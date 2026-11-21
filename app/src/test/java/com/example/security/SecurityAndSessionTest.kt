package com.example.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.SessionPreferences
import com.example.domain.model.AccountRole
import com.example.domain.model.ActiveWorkspaceSession
import com.example.domain.model.AuditLog
import com.example.domain.model.Booker
import com.example.domain.model.Business
import com.example.domain.model.BusinessUser
import com.example.domain.model.CreditTransaction
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.Shop
import com.example.util.DataExporter
import com.example.util.PasswordHasher
import com.example.util.SecurityRateLimiter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityAndSessionTest {

    @Test
    fun passwordHasher_usesUniqueRandomSaltAndVerifiesCorrectly() {
        val hash1 = PasswordHasher.hashPassword("SecretPass#1")
        val hash2 = PasswordHasher.hashPassword("SecretPass#1")

        assertNotEquals(hash1.saltBase64, hash2.saltBase64)
        assertNotEquals(hash1.hashBase64, hash2.hashBase64)
        assertTrue(PasswordHasher.verifyPassword("SecretPass#1", hash1.saltBase64, hash1.hashBase64))
        assertFalse(PasswordHasher.verifyPassword("WrongPass", hash1.saltBase64, hash1.hashBase64))
    }

    @Test
    fun securityRateLimiter_locksOutAfterMaxFailuresAndResetsOnSuccess() {
        var now = 1_000_000L
        val limiter = SecurityRateLimiter(clockMillis = { now })
        val op = SecurityRateLimiter.Operation.LOGIN
        val key = "BIZ-101:admin"

        repeat(op.maxFailures - 1) {
            limiter.checkAllowed(op, key)
            limiter.recordFailure(op, key)
        }
        assertEquals(1, limiter.remainingAttempts(op, key))

        // 5th failure triggers lockout
        limiter.recordFailure(op, key)
        assertEquals(0, limiter.remainingAttempts(op, key))

        var lockedOut = false
        try {
            limiter.checkAllowed(op, key)
        } catch (e: IllegalStateException) {
            lockedOut = e.message?.contains("Too many failed login attempts") == true
        }
        assertTrue(lockedOut)

        // Advance clock past lockout window
        now += op.lockoutMillis + 1000L
        limiter.checkAllowed(op, key)
        limiter.recordSuccess(op, key)
        assertEquals(op.maxFailures, limiter.remainingAttempts(op, key))
    }

    @Test
    fun sessionPreferences_persistsUntilManualLogout() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SessionPreferences(context)
        prefs.clearSession()

        val sample = ActiveWorkspaceSession(
            businessId = "biz_1",
            businessCode = "HP-8811",
            businessName = "Lahore Wholesale",
            accountId = "usr_1",
            username = "admin",
            fullName = "Tariq Admin",
            role = AccountRole.SUPER_ADMIN,
            bookerId = "",
            mustChangePassword = false
        )

        val t0 = System.currentTimeMillis() - 60_000L
        prefs.saveSession("google_uid_1", sample, loginAtMillis = t0)
        val stored = prefs.observeSession("google_uid_1").first()
        assertNotNull(stored)
        assertEquals("HP-8811", stored?.businessCode)

        // Persistent session does not expire automatically after 12h or 7 days
        assertFalse(SessionPreferences.isSessionExpired(loginAtMillis = 1_000L, nowMillis = 1_000L + 11 * 3600_000L))
        assertFalse(SessionPreferences.isSessionExpired(loginAtMillis = 1_000L, nowMillis = 1_000L + 7 * 24 * 3600_000L))

        // Logout clears session cleanly
        prefs.clearSession()
        assertNull(prefs.observeSession("google_uid_1").first())
    }

    @Test
    fun dataExporter_buildsStructuredIsolatedBackupJson() {
        val json = DataExporter.buildExportJson(
            business = Business(id = "b1", businessName = "Karachi Traders", businessCode = "KT-101"),
            bookers = listOf(Booker(id = "bk1", bookerCode = "BKR-1", fullName = "Ali", username = "ali")),
            staffUsers = listOf(BusinessUser(id = "u1", userCode = "USR-1", fullName = "Usman", username = "usman")),
            shops = listOf(Shop(id = "s1", shopUid = "SHP001", shopName = "ABC Store", bookerName = "Ali")),
            credits = listOf(CreditTransaction(id = "c1", txCode = "CR-1", shopUid = "SHP001", amountPaisa = 500_000L)),
            recoveries = listOf(RecoveryTransaction(id = "r1", txCode = "RC-1", shopUid = "SHP001", amountPaisa = 200_000L)),
            auditLogs = listOf(AuditLog(id = "a1", action = "CREDIT_ADDED"))
        )

        assertEquals("1.0", json.getString("backupVersion"))
        assertEquals("business_scoped", json.getString("workspaceIsolation"))
        assertEquals("PKR", json.getString("currency"))
        assertEquals("Asia/Karachi", json.getString("timezone"))
        val summary = json.getJSONObject("summary")
        assertEquals(1, summary.getInt("totalBookers"))
        assertEquals(1, summary.getInt("totalShops"))
        assertEquals(1, summary.getInt("totalCreditEntries"))
        assertEquals(1, summary.getInt("totalRecoveryEntries"))
        assertEquals(1, summary.getInt("totalAuditLogs"))
    }
}
