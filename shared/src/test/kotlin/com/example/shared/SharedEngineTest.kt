package com.example.shared

import com.example.shared.engine.SharedFinancialEngine
import com.example.shared.model.AccountRole
import com.example.shared.model.Booker
import com.example.shared.model.CreditTransaction
import com.example.shared.model.RecoveryTransaction
import com.example.shared.model.Shop
import com.example.shared.util.SharedCurrencyFormatter
import com.example.shared.util.SharedPakistanTime
import com.example.shared.util.SharedPasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedEngineTest {

    @Test
    fun `paisa math preserves exact credit, reversal, and supplier ledger running balance`() {
        val shop = Shop(id = "s1", businessId = "b1", shopUid = "SHP-101", shopName = "Al-Noor Mart", bookerId = "bk1", bookerName = "Bilal")
        val t1 = 1_780_000_000_000L
        val t2 = t1 + 86_400_000L
        val t3 = t2 + 86_400_000L
        val t4 = t3 + 86_400_000L

        val credits = listOf(
            CreditTransaction(id = "c1", businessId = "b1", txCode = "CR-1", shopId = "s1", amountPaisa = 15_140_000L, txDateMillis = t1, invoiceNumber = "4133"),
            CreditTransaction(id = "c2", businessId = "b1", txCode = "CR-2", shopId = "s1", amountPaisa = 5_000_000L, txDateMillis = t2, transactionKind = "CREDIT_REVERSAL", relatedTxId = "c1", reason = "Returned cartons")
        )
        val recoveries = listOf(
            RecoveryTransaction(id = "r1", businessId = "b1", txCode = "RC-1", shopId = "s1", amountPaisa = 3_441_000L, txDateMillis = t3, paymentMethod = "Cash"),
            RecoveryTransaction(id = "r2", businessId = "b1", txCode = "RC-2", shopId = "s1", amountPaisa = 3_441_000L, txDateMillis = t4, transactionKind = "RECOVERY_REVERSAL", relatedTxId = "r1", reason = "Bounced cheque")
        )

        val balances = SharedFinancialEngine.computeShopBalances(listOf(shop), credits, recoveries)
        assertEquals(10_140_000L, balances.first().totalCreditPaisa)
        assertEquals(0L, balances.first().totalRecoveredPaisa)
        assertEquals(10_140_000L, balances.first().outstandingPaisa)

        val ledger = SharedFinancialEngine.computeShopLedgerReport(shop, credits, recoveries, 0L, Long.MAX_VALUE)
        assertEquals(4, ledger.entries.size)
        assertEquals(10_140_000L, ledger.closingBalancePaisa)
        assertTrue(ledger.entries.last().isReversal)
    }

    @Test
    fun `staff cannot record previous-day or future recovery while super admin can backdate`() {
        val now = SharedPakistanTime.nowMillis()
        val yesterday = now - 86_400_000L
        val tomorrow = now + 86_400_000L

        assertNull(SharedFinancialEngine.validateRecoveryDateForRole(AccountRole.STAFF, now, now))
        assertNotNull(SharedFinancialEngine.validateRecoveryDateForRole(AccountRole.STAFF, yesterday, now))
        assertNotNull(SharedFinancialEngine.validateRecoveryDateForRole(AccountRole.STAFF, tomorrow, now))
        assertNull(SharedFinancialEngine.validateRecoveryDateForRole(AccountRole.SUPER_ADMIN, yesterday, now))
        assertNotNull(SharedFinancialEngine.validateRecoveryDateForRole(AccountRole.SUPER_ADMIN, tomorrow, now))
    }

    @Test
    fun `currency formatter and password hasher behave identically`() {
        assertEquals("Rs. 151,400", SharedCurrencyFormatter.formatPkr(15_140_000L))
        assertEquals("151,400.0", SharedCurrencyFormatter.formatLedgerAmount(15_140_000L))
        assertEquals(15_140_000L, SharedCurrencyFormatter.parsePkrToPaisa("151,400"))

        val hashed = SharedPasswordHasher.hashPassword("HisabPro#2026")
        assertTrue(SharedPasswordHasher.verifyPassword("HisabPro#2026", hashed.saltBase64, hashed.hashBase64))
        assertFalse(SharedPasswordHasher.verifyPassword("WrongPass#1", hashed.saltBase64, hashed.hashBase64))
    }

    @Test
    fun `booker consolidated ledger totals match assigned shops`() {
        val booker = Booker(id = "bk1", businessId = "b1", bookerCode = "BK-1", fullName = "Bilal Ahmed")
        val shop = Shop(id = "s1", businessId = "b1", shopUid = "SHP-1", shopName = "Madina Store", bookerId = "bk1", bookerName = "Bilal Ahmed")
        val ledger = SharedFinancialEngine.computeBookerConsolidatedLedger(
            booker = booker,
            shops = listOf(shop),
            credits = listOf(CreditTransaction(id = "c1", shopId = "s1", amountPaisa = 250_000L, txDateMillis = 1_780_000_000_000L)),
            recoveries = listOf(RecoveryTransaction(id = "r1", shopId = "s1", amountPaisa = 100_000L, txDateMillis = 1_780_000_100_000L))
        )
        assertEquals(250_000L, ledger.grandTotalCreditPaisa)
        assertEquals(100_000L, ledger.grandTotalRecoveredPaisa)
        assertEquals(150_000L, ledger.grandTotalOutstandingPaisa)
    }
}
