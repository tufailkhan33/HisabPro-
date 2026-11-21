package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.domain.model.AccountRole
import com.example.domain.model.AuditLog
import com.example.domain.model.Booker
import com.example.domain.model.Business
import com.example.domain.model.BusinessUser
import com.example.domain.model.BusinessSettings
import com.example.domain.model.CreditTransaction
import com.example.domain.model.FinancialEngine
import com.example.domain.model.LoginTicket
import com.example.domain.model.PlatformActivityLog
import com.example.domain.model.PlatformBackupSnapshot
import com.example.domain.model.PlatformGlobalMetrics
import com.example.domain.model.PlatformOwnerConfig
import com.example.domain.model.PlatformOwnerSession
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.Shop
import com.example.domain.model.StaffPermission
import com.example.util.PakistanTime
import com.example.util.PasswordHasher
import com.example.util.SecurityRateLimiter
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

sealed interface OwnerAuthRequirement {
    data object UnauthorizedGoogleAccount : OwnerAuthRequirement
    data class NeedsFirstTimePasswordSetup(val email: String, val googleUid: String) : OwnerAuthRequirement
    data class ReadyForPasswordLogin(val email: String, val googleUid: String, val config: PlatformOwnerConfig) : OwnerAuthRequirement
}

data class CompanyIsolatedSnapshot(
    val business: Business,
    val users: List<BusinessUser>,
    val permissions: List<StaffPermission>,
    val bookers: List<Booker>,
    val shops: List<Shop>,
    val credits: List<CreditTransaction>,
    val recoveries: List<RecoveryTransaction>,
    val auditLogs: List<AuditLog>
) {
    val totalCreditPaisa: Long get() = FinancialEngine.netCreditPaisa(credits)
    val totalRecoveredPaisa: Long get() = FinancialEngine.netRecoveryPaisa(recoveries)
    val totalOutstandingPaisa: Long
        get() = BigDecimal.valueOf(totalCreditPaisa)
            .subtract(BigDecimal.valueOf(totalRecoveredPaisa))
            .longValueExact()
}

data class PlatformCloudState(
    val companies: List<CompanyIsolatedSnapshot>,
    val metrics: PlatformGlobalMetrics,
    val platformActivity: List<PlatformActivityLog>,
    val backups: List<PlatformBackupSnapshot>
)

class PlatformOwnerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val rateLimiter: SecurityRateLimiter = SecurityRateLimiter()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth,
        SecurityRateLimiter()
    )

    companion object {
        const val OWNER_USERNAME = "MrTufailadmin"
        const val OWNER_EMAIL = "muhammadtufailllc007@gmail.com"
        private const val OWNER_DOC_ID = "root"
    }

    fun isVerifiedAuthorizedOwnerGoogleUser(): Boolean {
        val user = auth.currentUser ?: return false
        val email = user.email.orEmpty().trim()
        return email.equals(OWNER_EMAIL, ignoreCase = true) && user.isEmailVerified
    }

    private fun requireAuthorizedOwner(): Pair<String, String> {
        val user = auth.currentUser
            ?: throw SecurityException("Google Sign-In is required for Platform Owner access.")
        val email = user.email.orEmpty().trim()
        if (!email.equals(OWNER_EMAIL, ignoreCase = true) || !user.isEmailVerified) {
            throw SecurityException(
                "Unauthorized Google account. Platform Owner access is restricted strictly to verified $OWNER_EMAIL."
            )
        }
        return user.uid to email
    }

    suspend fun inspectOwnerAuthRequirement(): Result<OwnerAuthRequirement> = runCatching {
        val user = auth.currentUser
        if (user == null || !user.email.orEmpty().trim().equals(OWNER_EMAIL, ignoreCase = true) || !user.isEmailVerified) {
            return@runCatching OwnerAuthRequirement.UnauthorizedGoogleAccount
        }
        val docRef = db.collection("platform_owner").document(OWNER_DOC_ID)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            OwnerAuthRequirement.NeedsFirstTimePasswordSetup(user.email.orEmpty(), user.uid)
        } else {
            val cfg = snap.toObject(PlatformOwnerConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            if (cfg == null || cfg.passwordHash.isBlank() || cfg.passwordSalt.isBlank()) {
                OwnerAuthRequirement.NeedsFirstTimePasswordSetup(user.email.orEmpty(), user.uid)
            } else {
                OwnerAuthRequirement.ReadyForPasswordLogin(user.email.orEmpty(), user.uid, cfg)
            }
        }
    }

    suspend fun setupFirstTimeOwnerPassword(
        enteredUsername: String,
        newPassword: String,
        confirmPassword: String
    ): Result<PlatformOwnerSession> = runCatching {
        val (googleUid, email) = requireAuthorizedOwner()
        require(enteredUsername.trim() == OWNER_USERNAME) {
            "Invalid Platform Owner username. Expected exact Platform Owner username."
        }
        val strengthError = PasswordHasher.validateStrongPassword(newPassword)
        if (strengthError != null) throw IllegalArgumentException(strengthError)
        require(newPassword == confirmPassword) { "Password and confirm password do not match." }

        val docRef = db.collection("platform_owner").document(OWNER_DOC_ID)
        val existingSnap = docRef.get().await()
        if (existingSnap.exists()) {
            val existingCfg = existingSnap.toObject(PlatformOwnerConfig::class.java)
            if (existingCfg != null && existingCfg.passwordHash.isNotBlank()) {
                throw IllegalStateException("Owner password is already configured. Please sign in or use Reset Password.")
            }
        }

        val now = PakistanTime.nowMillis()
        val hashed = PasswordHasher.hashPassword(newPassword)
        val batch = db.batch()
        batch.set(
            docRef,
            mapOf(
                "username" to OWNER_USERNAME,
                "authorizedEmail" to OWNER_EMAIL,
                "googleUid" to googleUid,
                "passwordSalt" to hashed.saltBase64,
                "passwordHash" to hashed.hashBase64,
                "mustChangePassword" to false,
                "sessionRevokedBeforeMillis" to 0L,
                "lastLoginMillis" to now,
                "deviceCount" to 1,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logId = "pact_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            db.collection("platform_activity").document(logId),
            platformActivityPayload(
                action = "OWNER_FIRST_PASSWORD_CREATED",
                details = "Initial PBKDF2 password configured for $OWNER_USERNAME ($OWNER_EMAIL).",
                nowMillis = now
            )
        )
        batch.commit().await()

        PlatformOwnerSession(
            username = OWNER_USERNAME,
            email = email,
            googleUid = googleUid,
            loginTimestampMillis = now,
            needsPasswordSetup = false,
            mustChangePassword = false
        )
    }

    suspend fun loginPlatformOwner(
        enteredUsername: String,
        password: String
    ): Result<PlatformOwnerSession> = runCatching {
        val (googleUid, email) = requireAuthorizedOwner()
        val cleanUser = enteredUsername.trim()
        require(cleanUser.isNotBlank() && password.isNotBlank()) {
            "Owner username and password are required."
        }
        val rateKey = "OWNER:$OWNER_USERNAME"
        rateLimiter.checkAllowed(SecurityRateLimiter.Operation.LOGIN, rateKey)

        if (cleanUser != OWNER_USERNAME) {
            rateLimiter.recordFailure(SecurityRateLimiter.Operation.LOGIN, rateKey)
            throw IllegalArgumentException("Invalid Platform Owner username.")
        }

        val docRef = db.collection("platform_owner").document(OWNER_DOC_ID)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            throw IllegalStateException("Platform Owner password has not been set up yet.")
        }
        val config = snap.toObject(PlatformOwnerConfig::class.java)
            ?: throw IllegalStateException("Corrupted Platform Owner configuration.")

        if (config.passwordHash.isBlank() || config.passwordSalt.isBlank()) {
            throw IllegalStateException("Platform Owner password has not been set up yet.")
        }

        if (!PasswordHasher.verifyPassword(password, config.passwordSalt, config.passwordHash)) {
            rateLimiter.recordFailure(SecurityRateLimiter.Operation.LOGIN, rateKey)
            throw IllegalArgumentException("Incorrect Platform Owner password.")
        }
        rateLimiter.recordSuccess(SecurityRateLimiter.Operation.LOGIN, rateKey)

        val now = PakistanTime.nowMillis()
        val batch = db.batch()
        batch.update(
            docRef,
            mapOf(
                "googleUid" to googleUid,
                "lastLoginMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logId = "pact_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            db.collection("platform_activity").document(logId),
            platformActivityPayload(
                action = "OWNER_LOGIN",
                details = "Platform Owner $OWNER_USERNAME signed in via verified Google identity.",
                nowMillis = now
            )
        )
        batch.commit().await()

        PlatformOwnerSession(
            username = OWNER_USERNAME,
            email = email,
            googleUid = googleUid,
            loginTimestampMillis = now,
            needsPasswordSetup = false,
            mustChangePassword = config.mustChangePassword
        )
    }

    suspend fun resetOwnerPasswordWithVerifiedGoogle(
        enteredUsername: String,
        newPassword: String,
        confirmPassword: String
    ): Result<PlatformOwnerSession> = runCatching {
        val (googleUid, email) = requireAuthorizedOwner()
        require(enteredUsername.trim() == OWNER_USERNAME) { "Owner username must match $OWNER_USERNAME." }
        val strengthError = PasswordHasher.validateStrongPassword(newPassword)
        if (strengthError != null) throw IllegalArgumentException(strengthError)
        require(newPassword == confirmPassword) { "New password and confirmation do not match." }

        val rateKey = "OWNER:$OWNER_USERNAME"
        rateLimiter.checkAllowed(SecurityRateLimiter.Operation.PASSWORD_RESET, rateKey)

        val now = PakistanTime.nowMillis()
        val hashed = PasswordHasher.hashPassword(newPassword)
        val docRef = db.collection("platform_owner").document(OWNER_DOC_ID)
        val existingSnap = docRef.get().await()

        val batch = db.batch()
        val payload = mutableMapOf<String, Any>(
            "username" to OWNER_USERNAME,
            "authorizedEmail" to OWNER_EMAIL,
            "googleUid" to googleUid,
            "passwordSalt" to hashed.saltBase64,
            "passwordHash" to hashed.hashBase64,
            "mustChangePassword" to false,
            "sessionRevokedBeforeMillis" to now,
            "lastLoginMillis" to now,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!existingSnap.exists()) {
            payload["deviceCount"] = 1
            payload["createdAt"] = FieldValue.serverTimestamp()
        } else {
            payload["createdAt"] = existingSnap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
        }
        batch.set(docRef, payload)

        val logId = "pact_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            db.collection("platform_activity").document(logId),
            platformActivityPayload(
                action = "OWNER_PASSWORD_RESET",
                details = "Platform Owner password reset via verified Google identity ($OWNER_EMAIL); prior owner sessions invalidated.",
                nowMillis = now
            )
        )
        batch.commit().await()
        rateLimiter.recordSuccess(SecurityRateLimiter.Operation.PASSWORD_RESET, rateKey)

        PlatformOwnerSession(
            username = OWNER_USERNAME,
            email = email,
            googleUid = googleUid,
            loginTimestampMillis = now,
            needsPasswordSetup = false,
            mustChangePassword = false
        )
    }

    suspend fun changeOwnerPassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): Result<PlatformOwnerSession> = runCatching {
        val (googleUid, email) = requireAuthorizedOwner()
        require(currentPassword.isNotBlank()) { "Current password is required." }
        val strengthError = PasswordHasher.validateStrongPassword(newPassword)
        if (strengthError != null) throw IllegalArgumentException(strengthError)
        require(newPassword == confirmPassword) { "New password and confirmation do not match." }
        require(newPassword != currentPassword) { "New password must differ from current password." }

        val rateKey = "OWNER:$OWNER_USERNAME"
        rateLimiter.checkAllowed(SecurityRateLimiter.Operation.PASSWORD_CHANGE, rateKey)

        val docRef = db.collection("platform_owner").document(OWNER_DOC_ID)
        val snap = docRef.get().await()
        val config = snap.toObject(PlatformOwnerConfig::class.java)
            ?: throw IllegalStateException("Platform Owner record not configured.")

        if (!PasswordHasher.verifyPassword(currentPassword, config.passwordSalt, config.passwordHash)) {
            rateLimiter.recordFailure(SecurityRateLimiter.Operation.PASSWORD_CHANGE, rateKey)
            throw IllegalArgumentException("Current Platform Owner password is incorrect.")
        }
        rateLimiter.recordSuccess(SecurityRateLimiter.Operation.PASSWORD_CHANGE, rateKey)

        val now = PakistanTime.nowMillis()
        val hashed = PasswordHasher.hashPassword(newPassword)
        val batch = db.batch()
        batch.update(
            docRef,
            mapOf(
                "googleUid" to googleUid,
                "passwordSalt" to hashed.saltBase64,
                "passwordHash" to hashed.hashBase64,
                "mustChangePassword" to false,
                "sessionRevokedBeforeMillis" to now,
                "lastLoginMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logId = "pact_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            db.collection("platform_activity").document(logId),
            platformActivityPayload(
                action = "OWNER_PASSWORD_CHANGED",
                details = "Platform Owner changed password and revoked prior sessions.",
                nowMillis = now
            )
        )
        batch.commit().await()

        PlatformOwnerSession(
            username = OWNER_USERNAME,
            email = email,
            googleUid = googleUid,
            loginTimestampMillis = now,
            needsPasswordSetup = false,
            mustChangePassword = false
        )
    }

    suspend fun revokeAllOwnerSessions(): Result<Unit> = runCatching {
        requireAuthorizedOwner()
        val now = PakistanTime.nowMillis()
        val docRef = db.collection("platform_owner").document(OWNER_DOC_ID)
        val batch = db.batch()
        batch.update(
            docRef,
            mapOf(
                "sessionRevokedBeforeMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logId = "pact_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            db.collection("platform_activity").document(logId),
            platformActivityPayload(
                action = "OWNER_SESSIONS_REVOKED",
                details = "Revoked all active Platform Owner sessions across devices.",
                nowMillis = now
            )
        )
        batch.commit().await()
    }

    suspend fun isOwnerSessionRevoked(loginTimestampMillis: Long): Boolean {
        return runCatching {
            if (!isVerifiedAuthorizedOwnerGoogleUser()) return@runCatching true
            val snap = db.collection("platform_owner").document(OWNER_DOC_ID).get().await()
            if (!snap.exists()) return@runCatching false
            val revokedBefore = snap.getLong("sessionRevokedBeforeMillis") ?: 0L
            loginTimestampMillis > 0L && revokedBefore > loginTimestampMillis
        }.getOrDefault(false)
    }

    suspend fun loadGlobalCloudState(): Result<PlatformCloudState> = runCatching {
        requireAuthorizedOwner()
        val bizSnap = db.collection("businesses").get().await()
        val businesses = bizSnap.toObjects(Business::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.businessName.lowercase() }

        val companySnapshots = businesses.map { biz ->
            loadIsolatedCompanySnapshot(biz).getOrThrow()
        }

        val activitySnap = db.collection("platform_activity").get().await()
        val activityLogs = activitySnap.toObjects(PlatformActivityLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.timestampMillis }

        val backupsSnap = db.collection("platform_backups").get().await()
        val backups = backupsSnap.toObjects(PlatformBackupSnapshot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAtMillis }

        val totalCredit = companySnapshots.fold(BigDecimal.ZERO) { acc, c -> acc.add(BigDecimal.valueOf(c.totalCreditPaisa)) }.longValueExact()
        val totalRecovered = companySnapshots.fold(BigDecimal.ZERO) { acc, c -> acc.add(BigDecimal.valueOf(c.totalRecoveredPaisa)) }.longValueExact()
        val totalOutstanding = totalCredit - totalRecovered

        val metrics = PlatformGlobalMetrics(
            totalBusinesses = businesses.size,
            activeBusinesses = businesses.count { it.status == "ACTIVE" },
            suspendedBusinesses = businesses.count { it.status == "SUSPENDED" },
            totalAdmins = companySnapshots.sumOf { snap -> snap.users.count { it.role == AccountRole.SUPER_ADMIN.key } },
            totalStaff = companySnapshots.sumOf { snap -> snap.users.count { it.role == AccountRole.STAFF.key } },
            totalBookers = companySnapshots.sumOf { it.bookers.size },
            totalShops = companySnapshots.sumOf { it.shops.size },
            totalCreditTransactions = companySnapshots.sumOf { it.credits.size },
            totalRecoveryTransactions = companySnapshots.sumOf { it.recoveries.size },
            totalCreditPaisa = totalCredit,
            totalRecoveredPaisa = totalRecovered,
            totalOutstandingPaisa = totalOutstanding
        )

        PlatformCloudState(
            companies = companySnapshots,
            metrics = metrics,
            platformActivity = activityLogs,
            backups = backups
        )
    }

    suspend fun loadIsolatedCompanySnapshot(business: Business): Result<CompanyIsolatedSnapshot> = runCatching {
        requireAuthorizedOwner()
        val bizRef = db.collection("businesses").document(business.id)
        val users = bizRef.collection("users").get().await()
            .toObjects(BusinessUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.fullName }
        val permissions = bizRef.collection("permissions").get().await()
            .toObjects(StaffPermission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        val bookers = bizRef.collection("bookers").get().await()
            .toObjects(Booker::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.fullName }
        val shops = bizRef.collection("shops").get().await()
            .toObjects(Shop::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.shopName }
        val credits = bizRef.collection("credit_transactions").get().await()
            .toObjects(CreditTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.txDateMillis }
        val recoveries = bizRef.collection("recovery_transactions").get().await()
            .toObjects(RecoveryTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.txDateMillis }
        val logs = bizRef.collection("audit_logs").get().await()
            .toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.timestampMillis }

        CompanyIsolatedSnapshot(
            business = business,
            users = users,
            permissions = permissions,
            bookers = bookers,
            shops = shops,
            credits = credits,
            recoveries = recoveries,
            auditLogs = logs
        )
    }

    suspend fun suspendOrActivateCompany(
        business: Business,
        suspend: Boolean,
        reason: String
    ): Result<Unit> = runCatching {
        requireAuthorizedOwner()
        val cleanReason = reason.trim()
        if (suspend) {
            require(cleanReason.length >= 3) { "Please provide a clear reason to suspend this company." }
        }
        val nextStatus = if (suspend) "SUSPENDED" else "ACTIVE"
        val now = PakistanTime.nowMillis()
        val bizRef = db.collection("businesses").document(business.id)

        val batch = db.batch()
        batch.update(
            bizRef,
            mapOf(
                "status" to nextStatus,
                "suspendedReason" to if (suspend) cleanReason else "",
                "lastActivityMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val bizLogId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            bizRef.collection("audit_logs").document(bizLogId),
            mapOf(
                "businessId" to business.id,
                "activityCode" to PasswordHasher.generateCode("OWN"),
                "userId" to "platform_owner",
                "username" to OWNER_USERNAME,
                "role" to "PLATFORM_OWNER",
                "action" to if (suspend) "COMPANY_SUSPENDED" else "COMPANY_ACTIVATED",
                "shopUid" to "",
                "previousValue" to business.status,
                "newValue" to nextStatus,
                "dateFormatted" to PakistanTime.formatDate(now),
                "timeFormatted" to PakistanTime.formatTime(now),
                "timestampMillis" to now,
                "description" to if (suspend) "Platform Owner suspended company: $cleanReason" else "Platform Owner reactivated company",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        val platformLogId = "pact_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            db.collection("platform_activity").document(platformLogId),
            platformActivityPayload(
                action = if (suspend) "COMPANY_SUSPENDED" else "COMPANY_REACTIVATED",
                targetBusinessId = business.id,
                targetBusinessName = business.businessName,
                details = if (suspend) "Suspended ${business.businessName} (${business.businessCode}): $cleanReason" else "Reactivated ${business.businessName} (${business.businessCode})",
                nowMillis = now
            )
        )
        batch.commit().await()
    }

    suspend fun toggleAccountStatusByOwner(
        business: Business,
        accountId: String,
        username: String,
        role: AccountRole,
        currentStatus: String
    ): Result<Unit> = runCatching {
        requireAuthorizedOwner()
        val nextStatus = if (currentStatus == "ACTIVE") "SUSPENDED" else "ACTIVE"
        val now = PakistanTime.nowMillis()
        val bizRef = db.collection("businesses").document(business.id)
        val accountRef = if (role == AccountRole.BOOKER) {
            bizRef.collection("bookers").document(accountId)
        } else {
            bizRef.collection("users").document(accountId)
        }
        val ticketId = PasswordHasher.buildTicketId(business.businessCode, username)
        val ticketRef = db.collection("login_tickets").document(ticketId)

        val batch = db.batch()
        val accUpdate = mutableMapOf<String, Any>(
            "status" to nextStatus,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val ticketUpdate = mutableMapOf<String, Any>(
            "status" to nextStatus,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (nextStatus == "SUSPENDED") {
            accUpdate["sessionRevokedBeforeMillis"] = now
            ticketUpdate["sessionRevokedBeforeMillis"] = now
        }
        batch.update(accountRef, accUpdate)
        batch.update(ticketRef, ticketUpdate)

        val logId = "pact_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            db.collection("platform_activity").document(logId),
            platformActivityPayload(
                action = if (nextStatus == "SUSPENDED") "OWNER_SUSPENDED_ACCOUNT" else "OWNER_ACTIVATED_ACCOUNT",
                targetBusinessId = business.id,
                targetBusinessName = business.businessName,
                targetAccountId = accountId,
                targetUsername = username,
                details = "Set $role account @$username in ${business.businessName} to $nextStatus",
                nowMillis = now
            )
        )
        batch.commit().await()
    }

    suspend fun resetAccountPasswordByOwner(
        business: Business,
        accountId: String,
        username: String,
        role: AccountRole,
        newPassword: String,
        forceChangeOnNextLogin: Boolean
    ): Result<Unit> = runCatching {
        requireAuthorizedOwner()
        require(newPassword.length >= 6) { "Password must be at least 6 characters." }
        val hashed = PasswordHasher.hashPassword(newPassword)
        val now = PakistanTime.nowMillis()
        val bizRef = db.collection("businesses").document(business.id)
        val accountRef = if (role == AccountRole.BOOKER) {
            bizRef.collection("bookers").document(accountId)
        } else {
            bizRef.collection("users").document(accountId)
        }
        val ticketId = PasswordHasher.buildTicketId(business.businessCode, username)
        val ticketRef = db.collection("login_tickets").document(ticketId)

        val batch = db.batch()
        batch.update(
            ticketRef,
            mapOf(
                "passwordSalt" to hashed.saltBase64,
                "passwordHash" to hashed.hashBase64,
                "mustChangePassword" to forceChangeOnNextLogin,
                "sessionRevokedBeforeMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            accountRef,
            mapOf(
                "mustChangePassword" to forceChangeOnNextLogin,
                "sessionRevokedBeforeMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val bizLogId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            bizRef.collection("audit_logs").document(bizLogId),
            mapOf(
                "businessId" to business.id,
                "activityCode" to PasswordHasher.generateCode("OWN"),
                "userId" to "platform_owner",
                "username" to OWNER_USERNAME,
                "role" to "PLATFORM_OWNER",
                "action" to "OWNER_PASSWORD_RESET",
                "shopUid" to "",
                "previousValue" to "@$username",
                "newValue" to "Reset (forceChange=$forceChangeOnNextLogin)",
                "dateFormatted" to PakistanTime.formatDate(now),
                "timeFormatted" to PakistanTime.formatTime(now),
                "timestampMillis" to now,
                "description" to "Platform Owner reset password for @$username and revoked active sessions",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        val platformLogId = "pact_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            db.collection("platform_activity").document(platformLogId),
            platformActivityPayload(
                action = "OWNER_RESET_USER_PASSWORD",
                targetBusinessId = business.id,
                targetBusinessName = business.businessName,
                targetAccountId = accountId,
                targetUsername = username,
                details = "Platform Owner reset password for @$username (forceChange=$forceChangeOnNextLogin) & revoked active sessions.",
                nowMillis = now
            )
        )
        batch.commit().await()
    }

    suspend fun revokeAccountSessionsByOwner(
        business: Business,
        accountId: String,
        username: String,
        role: AccountRole
    ): Result<Unit> = runCatching {
        requireAuthorizedOwner()
        val now = PakistanTime.nowMillis()
        val bizRef = db.collection("businesses").document(business.id)
        val accountRef = if (role == AccountRole.BOOKER) {
            bizRef.collection("bookers").document(accountId)
        } else {
            bizRef.collection("users").document(accountId)
        }
        val ticketId = PasswordHasher.buildTicketId(business.businessCode, username)
        val ticketRef = db.collection("login_tickets").document(ticketId)

        val batch = db.batch()
        batch.update(
            accountRef,
            mapOf(
                "sessionRevokedBeforeMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            ticketRef,
            mapOf(
                "sessionRevokedBeforeMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val platformLogId = "pact_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            db.collection("platform_activity").document(platformLogId),
            platformActivityPayload(
                action = "OWNER_REVOKED_SESSIONS",
                targetBusinessId = business.id,
                targetBusinessName = business.businessName,
                targetAccountId = accountId,
                targetUsername = username,
                details = "Revoked all active login sessions for @$username in ${business.businessName}",
                nowMillis = now
            )
        )
        batch.commit().await()
    }

    suspend fun generateAndResetAccountPasswordByOwner(
        business: Business,
        accountId: String,
        username: String,
        role: AccountRole,
        forceChangeOnNextLogin: Boolean = true
    ): Result<String> = runCatching {
        val tempPass = PasswordHasher.generateTemporaryPassword(10)
        resetAccountPasswordByOwner(
            business = business,
            accountId = accountId,
            username = username,
            role = role,
            newPassword = tempPass,
            forceChangeOnNextLogin = forceChangeOnNextLogin
        ).getOrThrow()
        tempPass
    }

    suspend fun forcePasswordChangeByOwner(
        business: Business,
        accountId: String,
        username: String,
        role: AccountRole
    ): Result<Unit> = runCatching {
        requireAuthorizedOwner()
        val now = PakistanTime.nowMillis()
        val bizRef = db.collection("businesses").document(business.id)
        val accountRef = if (role == AccountRole.BOOKER) {
            bizRef.collection("bookers").document(accountId)
        } else {
            bizRef.collection("users").document(accountId)
        }
        val ticketId = PasswordHasher.buildTicketId(business.businessCode, username)
        val ticketRef = db.collection("login_tickets").document(ticketId)

        val batch = db.batch()
        batch.update(
            ticketRef,
            mapOf(
                "mustChangePassword" to true,
                "sessionRevokedBeforeMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            accountRef,
            mapOf(
                "mustChangePassword" to true,
                "sessionRevokedBeforeMillis" to now,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logId = "pact_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            db.collection("platform_activity").document(logId),
            platformActivityPayload(
                action = "OWNER_FORCE_PASSWORD_CHANGE",
                targetBusinessId = business.id,
                targetBusinessName = business.businessName,
                targetAccountId = accountId,
                targetUsername = username,
                details = "Platform Owner flagged @$username for mandatory password change on next login & revoked active sessions.",
                nowMillis = now
            )
        )
        batch.commit().await()
    }

    fun globalSearch(state: PlatformCloudState, query: String): List<CompanyIsolatedSnapshot> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return state.companies
        return state.companies.filter { c ->
            c.business.businessName.lowercase().contains(q) ||
                c.business.businessCode.lowercase().contains(q) ||
                c.business.id.lowercase().contains(q) ||
                c.business.ownerName.lowercase().contains(q) ||
                c.users.any {
                    it.fullName.lowercase().contains(q) ||
                        it.username.lowercase().contains(q) ||
                        it.userCode.lowercase().contains(q)
                } ||
                c.bookers.any {
                    it.fullName.lowercase().contains(q) ||
                        it.username.lowercase().contains(q) ||
                        it.bookerCode.lowercase().contains(q)
                } ||
                c.shops.any {
                    it.shopUid.lowercase().contains(q) ||
                        it.shopName.lowercase().contains(q)
                }
        }
    }

    suspend fun createCloudBackupManifest(state: PlatformCloudState): Result<PlatformBackupSnapshot> = runCatching {
        val (_, email) = requireAuthorizedOwner()
        val now = PakistanTime.nowMillis()
        val backupId = "bkp_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val summaryCompact = state.companies.joinToString(
            prefix = "[",
            postfix = "]"
        ) { c ->
            "{\"id\":\"${c.business.id}\",\"code\":\"${c.business.businessCode}\",\"shops\":${c.shops.size},\"outstandingPaisa\":${c.totalOutstandingPaisa}}"
        }.take(4000)

        val backupDocRef = db.collection("platform_backups").document(backupId)
        val batch = db.batch()
        batch.set(
            backupDocRef,
            mapOf(
                "createdByEmail" to email,
                "totalBusinesses" to state.metrics.totalBusinesses,
                "totalUsers" to (state.metrics.totalAdmins + state.metrics.totalStaff),
                "totalBookers" to state.metrics.totalBookers,
                "totalShops" to state.metrics.totalShops,
                "totalCreditTx" to state.metrics.totalCreditTransactions,
                "totalRecoveryTx" to state.metrics.totalRecoveryTransactions,
                "summaryJson" to summaryCompact,
                "createdAtMillis" to now,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        // Write full per-company restoration snapshots
        state.companies.forEach { c ->
            val settingsObj = runCatching {
                db.collection("businesses").document(c.business.id)
                    .collection("business_settings").document("default")
                    .get().await()
                    .toObject(BusinessSettings::class.java)
            }.getOrNull()
            val payloadJson = serializeCompanySnapshot(c, settingsObj)
            if (payloadJson.length <= 900_000) {
                batch.set(
                    backupDocRef.collection("companies").document(c.business.id),
                    mapOf(
                        "businessId" to c.business.id,
                        "businessCode" to c.business.businessCode,
                        "businessName" to c.business.businessName,
                        "snapshotJson" to payloadJson,
                        "createdAtMillis" to now,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }

        val platformLogId = "pact_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            db.collection("platform_activity").document(platformLogId),
            platformActivityPayload(
                action = "CLOUD_BACKUP_SNAPSHOT",
                details = "Created full cloud backup snapshot ($backupId) covering ${state.metrics.totalBusinesses} companies and ${state.metrics.totalShops} shops.",
                nowMillis = now
            )
        )
        batch.commit().await()

        PlatformBackupSnapshot(
            id = backupId,
            createdByEmail = email,
            totalBusinesses = state.metrics.totalBusinesses,
            totalUsers = state.metrics.totalAdmins + state.metrics.totalStaff,
            totalBookers = state.metrics.totalBookers,
            totalShops = state.metrics.totalShops,
            totalCreditTx = state.metrics.totalCreditTransactions,
            totalRecoveryTx = state.metrics.totalRecoveryTransactions,
            summaryJson = summaryCompact,
            createdAtMillis = now
        )
    }

    suspend fun restoreCompanyFromBackup(
        backupId: String,
        targetBusinessId: String? = null
    ): Result<Int> = runCatching {
        requireAuthorizedOwner()
        val now = PakistanTime.nowMillis()
        val backupDocRef = db.collection("platform_backups").document(backupId)
        val companyDocs = if (!targetBusinessId.isNullOrBlank()) {
            listOf(backupDocRef.collection("companies").document(targetBusinessId).get().await())
        } else {
            backupDocRef.collection("companies").get().await().documents
        }.filter { it.exists() }

        require(companyDocs.isNotEmpty()) {
            "No restorable company snapshots found inside backup $backupId."
        }

        var restoredCompanies = 0
        companyDocs.forEach { snap ->
            val rawJson = snap.getString("snapshotJson") ?: return@forEach
            val root = JSONObject(rawJson)
            val bizObj = root.getJSONObject("business")
            val businessId = bizObj.getString("id")
            val bizRef = db.collection("businesses").document(businessId)

            val writes = mutableListOf<Triple<com.google.firebase.firestore.DocumentReference, Map<String, Any>, Boolean>>()
            writes += Triple(
                bizRef,
                mapOf(
                    "businessName" to bizObj.getString("businessName"),
                    "businessCode" to bizObj.getString("businessCode"),
                    "ownerUid" to bizObj.getString("ownerUid"),
                    "ownerName" to bizObj.getString("ownerName"),
                    "ownerMobile" to bizObj.getString("ownerMobile"),
                    "ownerEmail" to bizObj.optString("ownerEmail", ""),
                    "currency" to bizObj.optString("currency", "PKR"),
                    "timezone" to bizObj.optString("timezone", "Asia/Karachi"),
                    "allowOverRecovery" to bizObj.optBoolean("allowOverRecovery", false),
                    "status" to bizObj.optString("status", "ACTIVE"),
                    "suspendedReason" to bizObj.optString("suspendedReason", ""),
                    "lastActivityMillis" to now,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                true
            )

            root.optJSONObject("settings")?.let { st ->
                writes += Triple(
                    bizRef.collection("business_settings").document("default"),
                    mapOf(
                        "businessId" to businessId,
                        "businessName" to st.optString("businessName", bizObj.getString("businessName")),
                        "businessAddress" to st.optString("businessAddress", ""),
                        "contactPhone" to st.optString("contactPhone", ""),
                        "allowOverRecovery" to st.optBoolean("allowOverRecovery", false),
                        "requireInvoiceNumber" to st.optBoolean("requireInvoiceNumber", false),
                        "updatedBy" to OWNER_USERNAME,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    true
                )
            }

            val usersArr = root.optJSONArray("users") ?: JSONArray()
            for (i in 0 until usersArr.length()) {
                val u = usersArr.getJSONObject(i)
                writes += Triple(
                    bizRef.collection("users").document(u.getString("id")),
                    mapOf(
                        "businessId" to businessId,
                        "userCode" to u.getString("userCode"),
                        "fullName" to u.getString("fullName"),
                        "username" to u.getString("username"),
                        "mobileNumber" to u.getString("mobileNumber"),
                        "email" to u.optString("email", ""),
                        "role" to u.getString("role"),
                        "status" to u.optString("status", "ACTIVE"),
                        "mustChangePassword" to u.optBoolean("mustChangePassword", false),
                        "sessionRevokedBeforeMillis" to u.optLong("sessionRevokedBeforeMillis", 0L),
                        "lastLoginMillis" to u.optLong("lastLoginMillis", 0L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    true
                )
            }

            val permsArr = root.optJSONArray("permissions") ?: JSONArray()
            for (i in 0 until permsArr.length()) {
                val p = permsArr.getJSONObject(i)
                val userId = p.getString("userId")
                writes += Triple(
                    bizRef.collection("permissions").document(userId),
                    mapOf(
                        "businessId" to businessId,
                        "userId" to userId,
                        "viewDashboard" to p.optBoolean("viewDashboard", true),
                        "viewShops" to p.optBoolean("viewShops", true),
                        "addShops" to p.optBoolean("addShops", false),
                        "editShops" to p.optBoolean("editShops", false),
                        "addCredit" to p.optBoolean("addCredit", false),
                        "editCredit" to p.optBoolean("editCredit", false),
                        "recordRecovery" to p.optBoolean("recordRecovery", false),
                        "viewRecoveryHistory" to p.optBoolean("viewRecoveryHistory", true),
                        "viewBookers" to p.optBoolean("viewBookers", true),
                        "manageBookers" to p.optBoolean("manageBookers", false),
                        "viewReports" to p.optBoolean("viewReports", true),
                        "generatePdfs" to p.optBoolean("generatePdfs", true),
                        "searchShops" to p.optBoolean("searchShops", true),
                        "viewActivityLogs" to p.optBoolean("viewActivityLogs", false),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    true
                )
            }

            val bookersArr = root.optJSONArray("bookers") ?: JSONArray()
            for (i in 0 until bookersArr.length()) {
                val b = bookersArr.getJSONObject(i)
                writes += Triple(
                    bizRef.collection("bookers").document(b.getString("id")),
                    mapOf(
                        "businessId" to businessId,
                        "bookerCode" to b.getString("bookerCode"),
                        "fullName" to b.getString("fullName"),
                        "username" to b.getString("username"),
                        "mobileNumber" to b.getString("mobileNumber"),
                        "address" to b.optString("address", ""),
                        "notes" to b.optString("notes", ""),
                        "status" to b.optString("status", "ACTIVE"),
                        "mustChangePassword" to b.optBoolean("mustChangePassword", false),
                        "sessionRevokedBeforeMillis" to b.optLong("sessionRevokedBeforeMillis", 0L),
                        "lastLoginMillis" to b.optLong("lastLoginMillis", 0L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    true
                )
            }

            val shopsArr = root.optJSONArray("shops") ?: JSONArray()
            for (i in 0 until shopsArr.length()) {
                val s = shopsArr.getJSONObject(i)
                writes += Triple(
                    bizRef.collection("shops").document(s.getString("id")),
                    mapOf(
                        "businessId" to businessId,
                        "shopUid" to s.getString("shopUid"),
                        "shopName" to s.getString("shopName"),
                        "bookerId" to s.getString("bookerId"),
                        "bookerName" to s.getString("bookerName"),
                        "ownerName" to s.getString("ownerName"),
                        "mobileNumber" to s.getString("mobileNumber"),
                        "address" to s.getString("address"),
                        "city" to s.getString("city"),
                        "openingCreditPaisa" to s.optLong("openingCreditPaisa", 0L),
                        "openingDateMillis" to s.optLong("openingDateMillis", now),
                        "notes" to s.optString("notes", ""),
                        "isArchived" to s.optBoolean("isArchived", false),
                        "createdByAccountId" to s.getString("createdByAccountId"),
                        "createdByName" to s.getString("createdByName"),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    true
                )
            }

            // Financial ledger entries are strictly create-only if missing so existing immutable records are never touched
            val creditsArr = root.optJSONArray("credits") ?: JSONArray()
            val existingCreditIds = bizRef.collection("credit_transactions").get().await().documents.map { it.id }.toSet()
            for (i in 0 until creditsArr.length()) {
                val c = creditsArr.getJSONObject(i)
                val cid = c.getString("id")
                if (!existingCreditIds.contains(cid)) {
                    writes += Triple(
                        bizRef.collection("credit_transactions").document(cid),
                        mapOf(
                            "businessId" to businessId,
                            "txCode" to c.getString("txCode"),
                            "shopId" to c.getString("shopId"),
                            "shopUid" to c.getString("shopUid"),
                            "shopName" to c.getString("shopName"),
                            "bookerId" to c.getString("bookerId"),
                            "bookerName" to c.getString("bookerName"),
                            "amountPaisa" to c.getLong("amountPaisa"),
                            "transactionKind" to c.getString("transactionKind"),
                            "relatedTxId" to c.optString("relatedTxId", ""),
                            "reason" to c.optString("reason", ""),
                            "invoiceNumber" to c.optString("invoiceNumber", ""),
                            "description" to c.optString("description", ""),
                            "attachmentUri" to c.optString("attachmentUri", ""),
                            "txDateMillis" to c.getLong("txDateMillis"),
                            "txDateFormatted" to c.getString("txDateFormatted"),
                            "txTimeFormatted" to c.getString("txTimeFormatted"),
                            "createdByAccountId" to c.getString("createdByAccountId"),
                            "createdByName" to c.getString("createdByName"),
                            "createdByRole" to c.getString("createdByRole"),
                            "dueDateMillis" to c.optLong("dueDateMillis", 0L),
                            "dueDateFormatted" to c.optString("dueDateFormatted", ""),
                            "entryTimestampMillis" to c.optLong("entryTimestampMillis", c.getLong("txDateMillis")),
                            "entryDateFormatted" to c.optString("entryDateFormatted", c.getString("txDateFormatted")),
                            "entryTimeFormatted" to c.optString("entryTimeFormatted", c.getString("txTimeFormatted")),
                            "syncStatus" to "SYNCED",
                            "idempotencyToken" to c.optString("idempotencyToken", cid),
                            "deviceId" to c.optString("deviceId", ""),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        false
                    )
                }
            }

            val recArr = root.optJSONArray("recoveries") ?: JSONArray()
            val existingRecIds = bizRef.collection("recovery_transactions").get().await().documents.map { it.id }.toSet()
            for (i in 0 until recArr.length()) {
                val r = recArr.getJSONObject(i)
                val rid = r.getString("id")
                if (!existingRecIds.contains(rid)) {
                    writes += Triple(
                        bizRef.collection("recovery_transactions").document(rid),
                        mapOf(
                            "businessId" to businessId,
                            "txCode" to r.getString("txCode"),
                            "shopId" to r.getString("shopId"),
                            "shopUid" to r.getString("shopUid"),
                            "shopName" to r.getString("shopName"),
                            "bookerId" to r.getString("bookerId"),
                            "bookerName" to r.getString("bookerName"),
                            "amountPaisa" to r.getLong("amountPaisa"),
                            "transactionKind" to r.getString("transactionKind"),
                            "relatedTxId" to r.optString("relatedTxId", ""),
                            "reason" to r.optString("reason", ""),
                            "paymentMethod" to r.optString("paymentMethod", "Cash"),
                            "referenceNumber" to r.optString("referenceNumber", ""),
                            "notes" to r.optString("notes", ""),
                            "txDateMillis" to r.getLong("txDateMillis"),
                            "txDateFormatted" to r.getString("txDateFormatted"),
                            "txTimeFormatted" to r.getString("txTimeFormatted"),
                            "createdByAccountId" to r.getString("createdByAccountId"),
                            "createdByName" to r.getString("createdByName"),
                            "createdByRole" to r.getString("createdByRole"),
                            "correctionGroupId" to r.optString("correctionGroupId", ""),
                            "correctedFromTxId" to r.optString("correctedFromTxId", ""),
                            "originalAmountPaisa" to r.optLong("originalAmountPaisa", r.getLong("amountPaisa")),
                            "originalTxDateMillis" to r.optLong("originalTxDateMillis", r.getLong("txDateMillis")),
                            "isBackdated" to r.optBoolean("isBackdated", false),
                            "reversedAtMillis" to r.optLong("reversedAtMillis", 0L),
                            "reversedByAccountId" to r.optString("reversedByAccountId", ""),
                            "reversedByName" to r.optString("reversedByName", ""),
                            "syncStatus" to "SYNCED",
                            "idempotencyToken" to r.optString("idempotencyToken", rid),
                            "deviceId" to r.optString("deviceId", ""),
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        false
                    )
                }
            }

            writes.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (ref, mapData, merge) ->
                    if (merge) batch.set(ref, mapData, SetOptions.merge()) else batch.set(ref, mapData)
                }
                batch.commit().await()
            }
            restoredCompanies++
        }

        val logId = "pact_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        db.collection("platform_activity").document(logId)
            .set(
                platformActivityPayload(
                    action = "CLOUD_BACKUP_RESTORED",
                    targetBusinessId = targetBusinessId.orEmpty(),
                    details = "Restored $restoredCompanies company record(s) from backup $backupId without overwriting immutable ledgers.",
                    nowMillis = now
                )
            ).await()

        restoredCompanies
    }

    private fun serializeCompanySnapshot(
        c: CompanyIsolatedSnapshot,
        settings: BusinessSettings?
    ): String {
        val root = JSONObject()
        val b = c.business
        root.put(
            "business",
            JSONObject().apply {
                put("id", b.id)
                put("businessName", b.businessName)
                put("businessCode", b.businessCode)
                put("ownerUid", b.ownerUid)
                put("ownerName", b.ownerName)
                put("ownerMobile", b.ownerMobile)
                put("ownerEmail", b.ownerEmail)
                put("currency", b.currency)
                put("timezone", b.timezone)
                put("allowOverRecovery", b.allowOverRecovery)
                put("status", b.status)
                put("suspendedReason", b.suspendedReason)
            }
        )
        if (settings != null) {
            root.put(
                "settings",
                JSONObject().apply {
                    put("businessName", settings.businessName)
                    put("businessAddress", settings.businessAddress)
                    put("contactPhone", settings.contactPhone)
                    put("allowOverRecovery", settings.allowOverRecovery)
                    put("requireInvoiceNumber", settings.requireInvoiceNumber)
                }
            )
        }
        root.put(
            "users",
            JSONArray().apply {
                c.users.forEach { u ->
                    put(
                        JSONObject().apply {
                            put("id", u.id)
                            put("userCode", u.userCode)
                            put("fullName", u.fullName)
                            put("username", u.username)
                            put("mobileNumber", u.mobileNumber)
                            put("email", u.email)
                            put("role", u.role)
                            put("status", u.status)
                            put("mustChangePassword", u.mustChangePassword)
                            put("sessionRevokedBeforeMillis", u.sessionRevokedBeforeMillis)
                            put("lastLoginMillis", u.lastLoginMillis)
                        }
                    )
                }
            }
        )
        root.put(
            "permissions",
            JSONArray().apply {
                c.permissions.forEach { p ->
                    put(
                        JSONObject().apply {
                            put("userId", p.userId)
                            put("viewDashboard", p.viewDashboard)
                            put("viewShops", p.viewShops)
                            put("addShops", p.addShops)
                            put("editShops", p.editShops)
                            put("addCredit", p.addCredit)
                            put("editCredit", p.editCredit)
                            put("recordRecovery", p.recordRecovery)
                            put("viewRecoveryHistory", p.viewRecoveryHistory)
                            put("viewBookers", p.viewBookers)
                            put("manageBookers", p.manageBookers)
                            put("viewReports", p.viewReports)
                            put("generatePdfs", p.generatePdfs)
                            put("searchShops", p.searchShops)
                            put("viewActivityLogs", p.viewActivityLogs)
                        }
                    )
                }
            }
        )
        root.put(
            "bookers",
            JSONArray().apply {
                c.bookers.forEach { bk ->
                    put(
                        JSONObject().apply {
                            put("id", bk.id)
                            put("bookerCode", bk.bookerCode)
                            put("fullName", bk.fullName)
                            put("username", bk.username)
                            put("mobileNumber", bk.mobileNumber)
                            put("address", bk.address)
                            put("notes", bk.notes)
                            put("status", bk.status)
                            put("mustChangePassword", bk.mustChangePassword)
                            put("sessionRevokedBeforeMillis", bk.sessionRevokedBeforeMillis)
                            put("lastLoginMillis", bk.lastLoginMillis)
                        }
                    )
                }
            }
        )
        root.put(
            "shops",
            JSONArray().apply {
                c.shops.forEach { s ->
                    put(
                        JSONObject().apply {
                            put("id", s.id)
                            put("shopUid", s.shopUid)
                            put("shopName", s.shopName)
                            put("bookerId", s.bookerId)
                            put("bookerName", s.bookerName)
                            put("ownerName", s.ownerName)
                            put("mobileNumber", s.mobileNumber)
                            put("address", s.address)
                            put("city", s.city)
                            put("openingCreditPaisa", s.openingCreditPaisa)
                            put("openingDateMillis", s.openingDateMillis)
                            put("notes", s.notes)
                            put("isArchived", s.isArchived)
                            put("createdByAccountId", s.createdByAccountId)
                            put("createdByName", s.createdByName)
                        }
                    )
                }
            }
        )
        root.put(
            "credits",
            JSONArray().apply {
                c.credits.forEach { cr ->
                    put(
                        JSONObject().apply {
                            put("id", cr.id)
                            put("txCode", cr.txCode)
                            put("shopId", cr.shopId)
                            put("shopUid", cr.shopUid)
                            put("shopName", cr.shopName)
                            put("bookerId", cr.bookerId)
                            put("bookerName", cr.bookerName)
                            put("amountPaisa", cr.amountPaisa)
                            put("transactionKind", cr.transactionKind)
                            put("relatedTxId", cr.relatedTxId)
                            put("reason", cr.reason)
                            put("invoiceNumber", cr.invoiceNumber)
                            put("description", cr.description)
                            put("attachmentUri", cr.attachmentUri)
                            put("txDateMillis", cr.txDateMillis)
                            put("txDateFormatted", cr.txDateFormatted)
                            put("txTimeFormatted", cr.txTimeFormatted)
                            put("createdByAccountId", cr.createdByAccountId)
                            put("createdByName", cr.createdByName)
                            put("createdByRole", cr.createdByRole)
                            put("dueDateMillis", cr.dueDateMillis)
                            put("dueDateFormatted", cr.dueDateFormatted)
                            put("entryTimestampMillis", cr.entryTimestampMillis)
                            put("entryDateFormatted", cr.entryDateFormatted)
                            put("entryTimeFormatted", cr.entryTimeFormatted)
                            put("idempotencyToken", cr.idempotencyToken)
                            put("deviceId", cr.deviceId)
                        }
                    )
                }
            }
        )
        root.put(
            "recoveries",
            JSONArray().apply {
                c.recoveries.forEach { rc ->
                    put(
                        JSONObject().apply {
                            put("id", rc.id)
                            put("txCode", rc.txCode)
                            put("shopId", rc.shopId)
                            put("shopUid", rc.shopUid)
                            put("shopName", rc.shopName)
                            put("bookerId", rc.bookerId)
                            put("bookerName", rc.bookerName)
                            put("amountPaisa", rc.amountPaisa)
                            put("transactionKind", rc.transactionKind)
                            put("relatedTxId", rc.relatedTxId)
                            put("reason", rc.reason)
                            put("paymentMethod", rc.paymentMethod)
                            put("referenceNumber", rc.referenceNumber)
                            put("notes", rc.notes)
                            put("txDateMillis", rc.txDateMillis)
                            put("txDateFormatted", rc.txDateFormatted)
                            put("txTimeFormatted", rc.txTimeFormatted)
                            put("createdByAccountId", rc.createdByAccountId)
                            put("createdByName", rc.createdByName)
                            put("createdByRole", rc.createdByRole)
                            put("correctionGroupId", rc.correctionGroupId)
                            put("correctedFromTxId", rc.correctedFromTxId)
                            put("originalAmountPaisa", rc.originalAmountPaisa)
                            put("originalTxDateMillis", rc.originalTxDateMillis)
                            put("isBackdated", rc.isBackdated)
                            put("reversedAtMillis", rc.reversedAtMillis)
                            put("reversedByAccountId", rc.reversedByAccountId)
                            put("reversedByName", rc.reversedByName)
                            put("idempotencyToken", rc.idempotencyToken)
                            put("deviceId", rc.deviceId)
                        }
                    )
                }
            }
        )
        return root.toString()
    }

    suspend fun checkTicketSessionRevoked(
        businessCode: String,
        username: String,
        loginAtMillis: Long
    ): Boolean {
        return runCatching {
            val ticketId = PasswordHasher.buildTicketId(businessCode, username)
            val snap = db.collection("login_tickets").document(ticketId).get().await()
            if (!snap.exists()) return@runCatching false
            val ticket = snap.toObject(LoginTicket::class.java) ?: return@runCatching false
            if (ticket.status != "ACTIVE") return@runCatching true
            val bizSnap = db.collection("businesses").document(ticket.businessId).get().await()
            val bizStatus = bizSnap.getString("status") ?: "ACTIVE"
            if (bizStatus == "SUSPENDED") return@runCatching true
            loginAtMillis > 0L && ticket.sessionRevokedBeforeMillis > loginAtMillis
        }.getOrDefault(false)
    }

    private fun platformActivityPayload(
        action: String,
        targetBusinessId: String = "",
        targetBusinessName: String = "",
        targetAccountId: String = "",
        targetUsername: String = "",
        details: String,
        nowMillis: Long
    ): Map<String, Any> = mapOf(
        "actorEmail" to OWNER_EMAIL,
        "actorUsername" to OWNER_USERNAME,
        "action" to action,
        "targetBusinessId" to targetBusinessId,
        "targetBusinessName" to targetBusinessName,
        "targetAccountId" to targetAccountId,
        "targetUsername" to targetUsername,
        "details" to details.take(500),
        "timestampMillis" to nowMillis,
        "dateFormatted" to PakistanTime.formatDate(nowMillis),
        "timeFormatted" to PakistanTime.formatTime(nowMillis),
        "createdAt" to FieldValue.serverTimestamp()
    )
}
