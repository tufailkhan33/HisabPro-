package com.example.desktop.state

import com.example.desktop.cloud.DesktopFirebaseAuthClient
import com.example.desktop.cloud.DesktopFirestoreClient
import com.example.desktop.cloud.FirestoreRestCodec
import com.example.desktop.local.DesktopOfflineSyncManager
import com.example.desktop.local.DesktopSqliteDatabase
import com.example.desktop.local.DesktopSyncRunResult
import com.example.shared.engine.SharedFinancialEngine
import com.example.shared.model.*
import com.example.shared.util.SharedCurrencyFormatter
import com.example.shared.util.SharedPakistanTime
import com.example.shared.util.SharedPasswordHasher
import com.example.shared.util.SharedSecurityRateLimiter
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * Unified Windows Desktop Repository coordinating SQLite offline cache/queue
 * and direct Firestore REST v1 multi-device synchronization with HisabPro Android v1.3.
 */
class DesktopWorkspaceRepository(
    val db: DesktopSqliteDatabase,
    val authClient: DesktopFirebaseAuthClient,
    val firestore: DesktopFirestoreClient,
    val syncManager: DesktopOfflineSyncManager,
    private val rateLimiter: SharedSecurityRateLimiter = SharedSecurityRateLimiter()
) {
    companion object {
        const val OWNER_USERNAME = "MrTufailadmin"
        const val OWNER_EMAIL = "muhammadtufailllc007@gmail.com"
        private const val ACTIVE_SESSION_SLOT = "active_workspace_session"
        private const val OWNER_SESSION_SLOT = "platform_owner_session"
    }

    fun isNetworkAvailable(): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("firestore.googleapis.com", 443), 2500)
            true
        }
    } catch (_: Exception) {
        false
    }

    fun restoreWorkspaceSession(): ActiveWorkspaceSession? {
        val raw = db.loadEncryptedSlot(ACTIVE_SESSION_SLOT) ?: return null
        return try {
            val o = JSONObject(raw)
            ActiveWorkspaceSession(
                businessId = o.optString("businessId"),
                businessCode = o.optString("businessCode"),
                businessName = o.optString("businessName"),
                accountId = o.optString("accountId"),
                username = o.optString("username"),
                fullName = o.optString("fullName"),
                role = AccountRole.fromKey(o.optString("role")),
                bookerId = o.optString("bookerId"),
                mustChangePassword = o.optBoolean("mustChangePassword", false)
            ).takeIf { it.businessId.isNotBlank() && it.accountId.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun persistWorkspaceSession(session: ActiveWorkspaceSession, loginAtMillis: Long = SharedPakistanTime.nowMillis()) {
        val o = JSONObject().apply {
            put("businessId", session.businessId)
            put("businessCode", session.businessCode)
            put("businessName", session.businessName)
            put("accountId", session.accountId)
            put("username", session.username)
            put("fullName", session.fullName)
            put("role", session.role.key)
            put("bookerId", session.bookerId)
            put("mustChangePassword", session.mustChangePassword)
            put("loginAtMillis", loginAtMillis)
        }
        db.saveEncryptedSlot(ACTIVE_SESSION_SLOT, o.toString())
        db.setPreference("workspace_login_at_millis", loginAtMillis.toString())
    }

    fun getWorkspaceLoginAtMillis(): Long =
        db.getPreference("workspace_login_at_millis", "0").toLongOrNull() ?: 0L

    fun clearWorkspaceSession() {
        db.clearEncryptedSlot(ACTIVE_SESSION_SLOT)
        db.setPreference("workspace_login_at_millis", "0")
    }

    fun restoreOwnerSession(): PlatformOwnerSession? {
        val raw = db.loadEncryptedSlot(OWNER_SESSION_SLOT) ?: return null
        return try {
            val o = JSONObject(raw)
            val email = o.optString("email")
            if (!email.equals(OWNER_EMAIL, ignoreCase = true)) return null
            PlatformOwnerSession(
                username = o.optString("username"),
                email = email,
                googleUid = o.optString("googleUid"),
                loginTimestampMillis = o.optLong("loginTimestampMillis"),
                needsPasswordSetup = o.optBoolean("needsPasswordSetup", false),
                mustChangePassword = o.optBoolean("mustChangePassword", false)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun persistOwnerSession(s: PlatformOwnerSession) {
        val o = JSONObject().apply {
            put("username", s.username)
            put("email", s.email)
            put("googleUid", s.googleUid)
            put("loginTimestampMillis", s.loginTimestampMillis)
            put("needsPasswordSetup", s.needsPasswordSetup)
            put("mustChangePassword", s.mustChangePassword)
        }
        db.saveEncryptedSlot(OWNER_SESSION_SLOT, o.toString())
    }

    fun clearOwnerSession() {
        db.clearEncryptedSlot(OWNER_SESSION_SLOT)
    }

    // --- Cloud Authentication & Workspace Sign-In ---

    suspend fun fetchUserBookmarks(googleUid: String): Result<List<UserWorkspaceBookmark>> = runCatching {
        val docs = firestore.listCollection("users/$googleUid/workspaces").getOrThrow()
        docs.map { FirestoreRestCodec.toWorkspaceBookmark(it) }
            .sortedByDescending { it.updatedAtMillis }
    }

    suspend fun loginWorkspace(
        businessCode: String,
        username: String,
        password: String
    ): Result<ActiveWorkspaceSession> = runCatching {
        val authSession = authClient.restoreSavedSession()
            ?: throw IllegalStateException("Please complete Google Sign-In first.")
        val cleanCode = businessCode.trim().uppercase()
        val cleanUser = username.trim()
        require(cleanCode.isNotBlank() && cleanUser.isNotBlank() && password.isNotBlank()) {
            "Business Code, Username, and Password are all required."
        }
        val rateKey = "$cleanCode:$cleanUser"
        rateLimiter.checkAllowed(SharedSecurityRateLimiter.Operation.LOGIN, rateKey)

        val ticketId = SharedPasswordHasher.buildTicketId(cleanCode, cleanUser)
        val ticketDoc = firestore.getDocument("login_tickets/$ticketId").getOrThrow()
            ?: run {
                rateLimiter.recordFailure(SharedSecurityRateLimiter.Operation.LOGIN, rateKey)
                throw IllegalArgumentException("Account not found for Business Code $cleanCode and username $cleanUser.")
            }
        val ticket = FirestoreRestCodec.toLoginTicket(ticketDoc)
        if (ticket.status != "ACTIVE") {
            throw IllegalStateException("Account status is ${ticket.status}. Contact your Business Admin.")
        }
        if (!SharedPasswordHasher.verifyPassword(password, ticket.passwordSalt, ticket.passwordHash)) {
            rateLimiter.recordFailure(SharedSecurityRateLimiter.Operation.LOGIN, rateKey)
            throw IllegalArgumentException("Incorrect password. Please try again.")
        }
        if (ticket.boundGoogleUid.isNotBlank() &&
            ticket.role == AccountRole.SUPER_ADMIN.key &&
            ticket.ownerUid != authSession.uid &&
            ticket.boundGoogleUid != authSession.uid
        ) {
            throw SecurityException("This Admin ticket is bound to another Google account.")
        }
        rateLimiter.recordSuccess(SharedSecurityRateLimiter.Operation.LOGIN, rateKey)

        // Bind Google UID on first claim while preserving createdAt
        if (ticket.boundGoogleUid.isBlank()) {
            firestore.patchFieldsWithServerTimestamp(
                relativeDocPath = "login_tickets/$ticketId",
                fields = mapOf("boundGoogleUid" to FirestoreRestCodec.stringVal(authSession.uid))
            ).getOrThrow()
        }

        // Ensure workspace member document exists for this Windows Google UID (preserve createdAt if updating)
        val loginAtMillis = SharedPakistanTime.nowMillis()
        val role = AccountRole.fromKey(ticket.role)
        val bookerId = if (role == AccountRole.BOOKER) ticket.accountId else ""
        val existingMember = firestore.getDocument("businesses/${ticket.businessId}/members/${authSession.uid}").getOrNull()
        val memberFields = mapOf(
            "businessId" to FirestoreRestCodec.stringVal(ticket.businessId),
            "googleUid" to FirestoreRestCodec.stringVal(authSession.uid),
            "accountId" to FirestoreRestCodec.stringVal(ticket.accountId),
            "username" to FirestoreRestCodec.stringVal(ticket.username),
            "fullName" to FirestoreRestCodec.stringVal(ticket.fullName),
            "role" to FirestoreRestCodec.stringVal(ticket.role),
            "bookerId" to FirestoreRestCodec.stringVal(bookerId),
            "status" to FirestoreRestCodec.stringVal("ACTIVE"),
            "claimedTicketId" to FirestoreRestCodec.stringVal(ticket.id),
            "sessionRevokedBeforeMillis" to FirestoreRestCodec.intVal(ticket.sessionRevokedBeforeMillis)
        )
        if (existingMember == null) {
            firestore.commitWriteWithServerTimestamp(
                relativeDocPath = "businesses/${ticket.businessId}/members/${authSession.uid}",
                fields = memberFields
            ).getOrThrow()
        } else {
            firestore.patchFieldsWithServerTimestamp(
                relativeDocPath = "businesses/${ticket.businessId}/members/${authSession.uid}",
                fields = memberFields
            ).getOrThrow()
        }

        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "users/${authSession.uid}/workspaces/${ticket.businessId}",
            fields = mapOf(
                "businessId" to FirestoreRestCodec.stringVal(ticket.businessId),
                "businessName" to FirestoreRestCodec.stringVal(ticket.businessName),
                "businessCode" to FirestoreRestCodec.stringVal(ticket.businessCode),
                "lastUsername" to FirestoreRestCodec.stringVal(ticket.username),
                "lastRole" to FirestoreRestCodec.stringVal(ticket.role)
            )
        )

        val session = ActiveWorkspaceSession(
            businessId = ticket.businessId,
            businessCode = ticket.businessCode,
            businessName = ticket.businessName,
            accountId = ticket.accountId,
            username = ticket.username,
            fullName = ticket.fullName,
            role = role,
            bookerId = bookerId,
            mustChangePassword = ticket.mustChangePassword
        )
        persistWorkspaceSession(session, loginAtMillis)
        pullAllBusinessData(session).getOrThrow()
        session
    }

    suspend fun createBusinessAndSuperAdmin(
        fullName: String,
        businessName: String,
        username: String,
        password: String,
        mobileNumber: String,
        email: String
    ): Result<ActiveWorkspaceSession> = runCatching {
        val authSession = authClient.restoreSavedSession()
            ?: throw IllegalStateException("Please complete Google Sign-In first.")
        SharedPasswordHasher.validateStrongPassword(password)?.let { throw IllegalArgumentException(it) }
        val cleanName = fullName.trim()
        val cleanBiz = businessName.trim()
        val cleanUser = username.trim()
        val cleanMobile = mobileNumber.trim()
        val cleanEmail = email.trim().ifBlank { authSession.email }
        require(cleanName.length >= 2 && cleanBiz.length >= 2 && cleanUser.length >= 2) {
            "Full Name, Business Name, and Username must each be at least 2 characters."
        }
        require(cleanMobile.length >= 7) { "Please enter a valid mobile number." }

        val businessId = "biz_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val adminId = "usr_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val bizCode = SharedPasswordHasher.generateBusinessCode(cleanBiz)
        val userCode = SharedPasswordHasher.generateCode("ADM")
        val ticketId = SharedPasswordHasher.buildTicketId(bizCode, cleanUser)
        val hashed = SharedPasswordHasher.hashPassword(password)
        val now = SharedPakistanTime.nowMillis()

        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "businesses/$businessId",
            fields = mapOf(
                "businessName" to FirestoreRestCodec.stringVal(cleanBiz),
                "businessCode" to FirestoreRestCodec.stringVal(bizCode),
                "ownerUid" to FirestoreRestCodec.stringVal(authSession.uid),
                "ownerName" to FirestoreRestCodec.stringVal(cleanName),
                "ownerMobile" to FirestoreRestCodec.stringVal(cleanMobile),
                "ownerEmail" to FirestoreRestCodec.stringVal(cleanEmail),
                "currency" to FirestoreRestCodec.stringVal("PKR"),
                "timezone" to FirestoreRestCodec.stringVal("Asia/Karachi"),
                "allowOverRecovery" to FirestoreRestCodec.boolVal(false),
                "status" to FirestoreRestCodec.stringVal("ACTIVE"),
                "suspendedReason" to FirestoreRestCodec.stringVal(""),
                "lastActivityMillis" to FirestoreRestCodec.intVal(now)
            )
        ).getOrThrow()

        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "businesses/$businessId/members/${authSession.uid}",
            fields = mapOf(
                "businessId" to FirestoreRestCodec.stringVal(businessId),
                "googleUid" to FirestoreRestCodec.stringVal(authSession.uid),
                "accountId" to FirestoreRestCodec.stringVal(adminId),
                "username" to FirestoreRestCodec.stringVal(cleanUser),
                "fullName" to FirestoreRestCodec.stringVal(cleanName),
                "role" to FirestoreRestCodec.stringVal(AccountRole.SUPER_ADMIN.key),
                "bookerId" to FirestoreRestCodec.stringVal(""),
                "status" to FirestoreRestCodec.stringVal("ACTIVE"),
                "claimedTicketId" to FirestoreRestCodec.stringVal(ticketId),
                "sessionRevokedBeforeMillis" to FirestoreRestCodec.intVal(0L)
            )
        ).getOrThrow()

        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "businesses/$businessId/users/$adminId",
            fields = mapOf(
                "businessId" to FirestoreRestCodec.stringVal(businessId),
                "userCode" to FirestoreRestCodec.stringVal(userCode),
                "fullName" to FirestoreRestCodec.stringVal(cleanName),
                "username" to FirestoreRestCodec.stringVal(cleanUser),
                "mobileNumber" to FirestoreRestCodec.stringVal(cleanMobile),
                "email" to FirestoreRestCodec.stringVal(cleanEmail),
                "role" to FirestoreRestCodec.stringVal(AccountRole.SUPER_ADMIN.key),
                "status" to FirestoreRestCodec.stringVal("ACTIVE"),
                "mustChangePassword" to FirestoreRestCodec.boolVal(false),
                "sessionRevokedBeforeMillis" to FirestoreRestCodec.intVal(0L),
                "lastLoginMillis" to FirestoreRestCodec.intVal(now)
            )
        ).getOrThrow()

        val fullPerms = StaffPermission.fullAdmin(businessId, adminId)
        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "businesses/$businessId/permissions/$adminId",
            fields = permissionToFields(fullPerms)
        ).getOrThrow()

        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "businesses/$businessId/business_settings/default",
            fields = mapOf(
                "businessId" to FirestoreRestCodec.stringVal(businessId),
                "businessName" to FirestoreRestCodec.stringVal(cleanBiz),
                "businessAddress" to FirestoreRestCodec.stringVal(""),
                "contactPhone" to FirestoreRestCodec.stringVal(cleanMobile),
                "allowOverRecovery" to FirestoreRestCodec.boolVal(false),
                "requireInvoiceNumber" to FirestoreRestCodec.boolVal(false),
                "updatedBy" to FirestoreRestCodec.stringVal(cleanName)
            )
        ).getOrThrow()

        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "login_tickets/$ticketId",
            fields = mapOf(
                "businessId" to FirestoreRestCodec.stringVal(businessId),
                "businessName" to FirestoreRestCodec.stringVal(cleanBiz),
                "businessCode" to FirestoreRestCodec.stringVal(bizCode),
                "accountId" to FirestoreRestCodec.stringVal(adminId),
                "role" to FirestoreRestCodec.stringVal(AccountRole.SUPER_ADMIN.key),
                "username" to FirestoreRestCodec.stringVal(cleanUser),
                "fullName" to FirestoreRestCodec.stringVal(cleanName),
                "passwordSalt" to FirestoreRestCodec.stringVal(hashed.saltBase64),
                "passwordHash" to FirestoreRestCodec.stringVal(hashed.hashBase64),
                "mustChangePassword" to FirestoreRestCodec.boolVal(false),
                "status" to FirestoreRestCodec.stringVal("ACTIVE"),
                "boundGoogleUid" to FirestoreRestCodec.stringVal(authSession.uid),
                "ownerUid" to FirestoreRestCodec.stringVal(authSession.uid),
                "sessionRevokedBeforeMillis" to FirestoreRestCodec.intVal(0L)
            )
        ).getOrThrow()

        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "users/${authSession.uid}/workspaces/$businessId",
            fields = mapOf(
                "businessId" to FirestoreRestCodec.stringVal(businessId),
                "businessName" to FirestoreRestCodec.stringVal(cleanBiz),
                "businessCode" to FirestoreRestCodec.stringVal(bizCode),
                "lastUsername" to FirestoreRestCodec.stringVal(cleanUser),
                "lastRole" to FirestoreRestCodec.stringVal(AccountRole.SUPER_ADMIN.key)
            )
        )

        val session = ActiveWorkspaceSession(
            businessId = businessId,
            businessCode = bizCode,
            businessName = cleanBiz,
            accountId = adminId,
            username = cleanUser,
            fullName = cleanName,
            role = AccountRole.SUPER_ADMIN,
            bookerId = "",
            mustChangePassword = false
        )
        persistWorkspaceSession(session)
        pullAllBusinessData(session)
        session
    }

    suspend fun loginPlatformOwner(enteredUsername: String, enteredPassword: String): Result<PlatformOwnerSession> = runCatching {
        val authSession = authClient.restoreSavedSession()
            ?: throw IllegalStateException("Google Sign-In required first.")
        if (!authSession.email.equals(OWNER_EMAIL, ignoreCase = true) || !authSession.emailVerified) {
            throw SecurityException("Platform Owner portal is restricted strictly to verified $OWNER_EMAIL.")
        }
        val cleanUser = enteredUsername.trim()
        val rateKey = "owner:$cleanUser"
        rateLimiter.checkAllowed(SharedSecurityRateLimiter.Operation.LOGIN, rateKey)
        if (cleanUser != OWNER_USERNAME) {
            rateLimiter.recordFailure(SharedSecurityRateLimiter.Operation.LOGIN, rateKey)
            throw IllegalArgumentException("Invalid Platform Owner username.")
        }
        val doc = firestore.getDocument("platform_owner/root").getOrThrow()
            ?: throw IllegalStateException("Platform Owner account not yet initialized on cloud. Setup password on first login or from Android.")
        val cfg = FirestoreRestCodec.toPlatformOwnerConfig(doc)
        if (!SharedPasswordHasher.verifyPassword(enteredPassword, cfg.passwordSalt, cfg.passwordHash)) {
            rateLimiter.recordFailure(SharedSecurityRateLimiter.Operation.LOGIN, rateKey)
            throw IllegalArgumentException("Incorrect Platform Owner password.")
        }
        rateLimiter.recordSuccess(SharedSecurityRateLimiter.Operation.LOGIN, rateKey)
        val now = SharedPakistanTime.nowMillis()
        val actId = "pact_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        runCatching {
            firestore.commitWriteWithServerTimestamp(
                relativeDocPath = "platform_activity/$actId",
                fields = mapOf(
                    "actorEmail" to FirestoreRestCodec.stringVal(OWNER_EMAIL),
                    "actorUsername" to FirestoreRestCodec.stringVal(OWNER_USERNAME),
                    "action" to FirestoreRestCodec.stringVal("OWNER_LOGIN"),
                    "targetBusinessId" to FirestoreRestCodec.stringVal(""),
                    "targetBusinessName" to FirestoreRestCodec.stringVal(""),
                    "targetAccountId" to FirestoreRestCodec.stringVal(""),
                    "targetUsername" to FirestoreRestCodec.stringVal(""),
                    "details" to FirestoreRestCodec.stringVal("Signed in from HisabPro Windows Desktop (${authSession.uid.take(8)})"),
                    "timestampMillis" to FirestoreRestCodec.intVal(now),
                    "dateFormatted" to FirestoreRestCodec.stringVal(SharedPakistanTime.formatDate(now)),
                    "timeFormatted" to FirestoreRestCodec.stringVal(SharedPakistanTime.formatTime(now))
                ),
                includeCreatedAt = true,
                includeUpdatedAt = false
            )
        }
        val session = PlatformOwnerSession(
            username = OWNER_USERNAME,
            email = authSession.email,
            googleUid = authSession.uid,
            loginTimestampMillis = now,
            needsPasswordSetup = false,
            mustChangePassword = cfg.mustChangePassword
        )
        persistOwnerSession(session)
        session
    }

    suspend fun loadAllPlatformBusinesses(): Result<List<Business>> = runCatching {
        val docs = firestore.listCollection("businesses").getOrThrow()
        docs.map { FirestoreRestCodec.toBusiness(it) }
            .sortedByDescending { it.lastActivityMillis }
    }

    suspend fun setBusinessSuspended(business: Business, suspend: Boolean, reason: String): Result<Unit> = runCatching {
        val newStatus = if (suspend) "SUSPENDED" else "ACTIVE"
        val now = SharedPakistanTime.nowMillis()
        firestore.patchFieldsWithServerTimestamp(
            relativeDocPath = "businesses/${business.id}",
            fields = mapOf(
                "status" to FirestoreRestCodec.stringVal(newStatus),
                "suspendedReason" to FirestoreRestCodec.stringVal(if (suspend) reason else ""),
                "lastActivityMillis" to FirestoreRestCodec.intVal(now)
            ),
            includeUpdatedAt = true
        ).getOrThrow()
    }

    // --- Real-Time Bidirectional Pull & Sync ---

    suspend fun pullAllBusinessData(session: ActiveWorkspaceSession): Result<DesktopSyncRunResult> = runCatching {
        // 0. Verify session has not been suspended or remotely revoked
        val loginAtMillis = getWorkspaceLoginAtMillis()
        val ticketId = SharedPasswordHasher.buildTicketId(session.businessCode, session.username)
        val ticketSnap = firestore.getDocument("login_tickets/$ticketId").getOrNull()
        if (ticketSnap != null) {
            val ticket = FirestoreRestCodec.toLoginTicket(ticketSnap)
            if (ticket.status != "ACTIVE") {
                clearWorkspaceSession()
                throw SecurityException("Your workspace access was set to ${ticket.status}. Signed out.")
            }
            if (loginAtMillis > 0L && ticket.sessionRevokedBeforeMillis > loginAtMillis) {
                clearWorkspaceSession()
                throw SecurityException("Your session was revoked remotely by Admin/Owner. Please sign in again.")
            }
        }
        val authUid = authClient.restoreSavedSession()?.uid
        if (!authUid.isNullOrBlank()) {
            val memberSnap = firestore.getDocument("businesses/${session.businessId}/members/$authUid").getOrNull()
            val memberFields = memberSnap?.optJSONObject("fields")
            val memberRevoked = FirestoreRestCodec.extractLong(memberFields, "sessionRevokedBeforeMillis")
            val memberStatus = FirestoreRestCodec.extractString(memberFields, "status", "ACTIVE")
            if (memberStatus != "ACTIVE" || (loginAtMillis > 0L && memberRevoked > loginAtMillis)) {
                clearWorkspaceSession()
                throw SecurityException("Workspace session has been suspended or revoked.")
            }
        }

        // 1. Replay offline queue first so pending transactions are uploaded before replacing synced caches
        val uploadResult = syncManager.syncPendingQueue(session.businessId)

        // 2. Pull shared Firestore collections into local SQLite database
        val bizDoc = firestore.getDocument("businesses/${session.businessId}").getOrThrow()
        bizDoc?.let { db.upsertBusiness(FirestoreRestCodec.toBusiness(it)) }

        firestore.getDocument("businesses/${session.businessId}/business_settings/default").getOrNull()?.let {
            db.upsertSettings(FirestoreRestCodec.toBusinessSettings(it))
        }

        if (session.role != AccountRole.BOOKER) {
            val usersDocs = firestore.listCollection("businesses/${session.businessId}/users").getOrThrow()
            db.replaceStaffUsers(session.businessId, usersDocs.map { FirestoreRestCodec.toBusinessUser(it) })

            val permDocs = firestore.listCollection("businesses/${session.businessId}/permissions").getOrThrow()
            db.replacePermissions(session.businessId, permDocs.map { FirestoreRestCodec.toStaffPermission(it) })

            val bookerDocs = firestore.listCollection("businesses/${session.businessId}/bookers").getOrThrow()
            db.replaceBookers(session.businessId, bookerDocs.map { FirestoreRestCodec.toBooker(it) })

            val shopDocs = firestore.listCollection("businesses/${session.businessId}/shops").getOrThrow()
            db.replaceShops(session.businessId, shopDocs.map { FirestoreRestCodec.toShop(it) })

            val crDocs = firestore.listCollection("businesses/${session.businessId}/credit_transactions").getOrThrow()
            db.replaceSyncedCredits(session.businessId, crDocs.map { FirestoreRestCodec.toCreditTx(it) })

            val rcDocs = firestore.listCollection("businesses/${session.businessId}/recovery_transactions").getOrThrow()
            db.replaceSyncedRecoveries(session.businessId, rcDocs.map { FirestoreRestCodec.toRecoveryTx(it) })

            val prodDocs = firestore.listCollection("businesses/${session.businessId}/products").getOrElse { emptyList() }
            db.replaceSyncedProducts(session.businessId, prodDocs.map { FirestoreRestCodec.toProduct(it) })

            val bkDocs = firestore.listCollection("businesses/${session.businessId}/bookings").getOrElse { emptyList() }
            db.replaceSyncedBookings(session.businessId, bkDocs.map { FirestoreRestCodec.toBooking(it) })

            val notifDocs = firestore.listCollection("businesses/${session.businessId}/notifications").getOrElse { emptyList() }
            db.replaceNotifications(session.businessId, notifDocs.map { FirestoreRestCodec.toNotification(it) })

            val auditDocs = firestore.listCollection("businesses/${session.businessId}/audit_logs").getOrElse { emptyList() }
            db.replaceAuditLogs(session.businessId, auditDocs.map { FirestoreRestCodec.toAuditLog(it) })
        } else {
            val bookerDoc = firestore.getDocument("businesses/${session.businessId}/bookers/${session.bookerId}").getOrNull()
            if (bookerDoc != null) db.replaceBookers(session.businessId, listOf(FirestoreRestCodec.toBooker(bookerDoc)))

            val shopDocs = firestore.runStructuredQuery("businesses/${session.businessId}", "shops", "bookerId", session.bookerId).getOrThrow()
            db.replaceShops(session.businessId, shopDocs.map { FirestoreRestCodec.toShop(it) })

            val crDocs = firestore.runStructuredQuery("businesses/${session.businessId}", "credit_transactions", "bookerId", session.bookerId).getOrThrow()
            db.replaceSyncedCredits(session.businessId, crDocs.map { FirestoreRestCodec.toCreditTx(it) })

            val rcDocs = firestore.runStructuredQuery("businesses/${session.businessId}", "recovery_transactions", "bookerId", session.bookerId).getOrThrow()
            db.replaceSyncedRecoveries(session.businessId, rcDocs.map { FirestoreRestCodec.toRecoveryTx(it) })

            val prodDocs = firestore.listCollection("businesses/${session.businessId}/products").getOrElse { emptyList() }
            db.replaceSyncedProducts(session.businessId, prodDocs.map { FirestoreRestCodec.toProduct(it) })

            val bkDocs = firestore.runStructuredQuery("businesses/${session.businessId}", "bookings", "bookerId", session.bookerId).getOrElse { emptyList() }
            db.replaceSyncedBookings(session.businessId, bkDocs.map { FirestoreRestCodec.toBooking(it) })
        }

        val chatGroups = if (session.role == AccountRole.SUPER_ADMIN) {
            firestore.listCollection("businesses/${session.businessId}/chat_groups").getOrElse { emptyList() }
        } else {
            firestore.runStructuredQuery("businesses/${session.businessId}", "chat_groups", "memberAccountIds", session.accountId, "ARRAY_CONTAINS").getOrElse { emptyList() }
        }
        db.replaceChatGroups(session.businessId, chatGroups.map { FirestoreRestCodec.toChatGroup(it) })

        uploadResult
    }

    // --- Business Mutation Operations ---

    suspend fun createShop(
        session: ActiveWorkspaceSession,
        shopName: String,
        ownerName: String,
        mobile: String,
        address: String,
        city: String,
        booker: Booker,
        openingCreditPaisa: Long,
        notes: String
    ): Result<Shop> = runCatching {
        val id = "shp_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val uid = SharedPasswordHasher.generateCode("SHP")
        val now = SharedPakistanTime.nowMillis()
        val shop = Shop(
            id = id,
            businessId = session.businessId,
            shopUid = uid,
            shopName = shopName.trim(),
            bookerId = booker.id,
            bookerName = booker.fullName,
            ownerName = ownerName.trim(),
            mobileNumber = mobile.trim(),
            address = address.trim(),
            city = city.trim(),
            openingCreditPaisa = openingCreditPaisa,
            openingDateMillis = now,
            notes = notes.trim(),
            isArchived = false,
            createdByAccountId = session.accountId,
            createdByName = session.fullName
        )
        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "businesses/${session.businessId}/shops/$id",
            fields = mapOf(
                "businessId" to FirestoreRestCodec.stringVal(session.businessId),
                "shopUid" to FirestoreRestCodec.stringVal(uid),
                "shopName" to FirestoreRestCodec.stringVal(shop.shopName),
                "bookerId" to FirestoreRestCodec.stringVal(shop.bookerId),
                "bookerName" to FirestoreRestCodec.stringVal(shop.bookerName),
                "ownerName" to FirestoreRestCodec.stringVal(shop.ownerName),
                "mobileNumber" to FirestoreRestCodec.stringVal(shop.mobileNumber),
                "address" to FirestoreRestCodec.stringVal(shop.address),
                "city" to FirestoreRestCodec.stringVal(shop.city),
                "openingCreditPaisa" to FirestoreRestCodec.intVal(openingCreditPaisa),
                "openingDateMillis" to FirestoreRestCodec.intVal(now),
                "notes" to FirestoreRestCodec.stringVal(shop.notes),
                "isArchived" to FirestoreRestCodec.boolVal(false),
                "createdByAccountId" to FirestoreRestCodec.stringVal(session.accountId),
                "createdByName" to FirestoreRestCodec.stringVal(session.fullName)
            )
        ).getOrThrow()
        db.upsertShop(shop)

        if (openingCreditPaisa > 0L) {
            syncManager.queueOfflineCredit(
                session = session,
                shop = shop,
                amountPaisa = openingCreditPaisa,
                txDateMillis = now,
                dueDateMillis = 0L,
                invoiceNumber = "OPENING",
                description = "Opening balance for ${shop.shopName}"
            )
            syncManager.syncPendingQueue(session.businessId)
        }
        writeAuditLog(session, "CREATE_SHOP", shop.shopUid, "Created shop ${shop.shopName} (${shop.shopUid})")
        shop
    }

    suspend fun createBooker(
        session: ActiveWorkspaceSession,
        fullName: String,
        username: String,
        mobile: String,
        address: String
    ): Result<GeneratedCredentials> = runCatching {
        val authSession = authClient.restoreSavedSession()
            ?: throw IllegalStateException("Google Sign-In required.")
        val id = "bkr_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val code = SharedPasswordHasher.generateCode("BKR")
        val tempPass = SharedPasswordHasher.generateTemporaryPassword()
        val hashed = SharedPasswordHasher.hashPassword(tempPass)
        val ticketId = SharedPasswordHasher.buildTicketId(session.businessCode, username)

        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "businesses/${session.businessId}/bookers/$id",
            fields = mapOf(
                "businessId" to FirestoreRestCodec.stringVal(session.businessId),
                "bookerCode" to FirestoreRestCodec.stringVal(code),
                "fullName" to FirestoreRestCodec.stringVal(fullName.trim()),
                "username" to FirestoreRestCodec.stringVal(username.trim()),
                "mobileNumber" to FirestoreRestCodec.stringVal(mobile.trim()),
                "address" to FirestoreRestCodec.stringVal(address.trim()),
                "notes" to FirestoreRestCodec.stringVal(""),
                "status" to FirestoreRestCodec.stringVal("ACTIVE"),
                "mustChangePassword" to FirestoreRestCodec.boolVal(true),
                "sessionRevokedBeforeMillis" to FirestoreRestCodec.intVal(0L),
                "lastLoginMillis" to FirestoreRestCodec.intVal(0L)
            )
        ).getOrThrow()

        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "login_tickets/$ticketId",
            fields = mapOf(
                "businessId" to FirestoreRestCodec.stringVal(session.businessId),
                "businessName" to FirestoreRestCodec.stringVal(session.businessName),
                "businessCode" to FirestoreRestCodec.stringVal(session.businessCode),
                "accountId" to FirestoreRestCodec.stringVal(id),
                "role" to FirestoreRestCodec.stringVal(AccountRole.BOOKER.key),
                "username" to FirestoreRestCodec.stringVal(username.trim()),
                "fullName" to FirestoreRestCodec.stringVal(fullName.trim()),
                "passwordSalt" to FirestoreRestCodec.stringVal(hashed.saltBase64),
                "passwordHash" to FirestoreRestCodec.stringVal(hashed.hashBase64),
                "mustChangePassword" to FirestoreRestCodec.boolVal(true),
                "status" to FirestoreRestCodec.stringVal("ACTIVE"),
                "boundGoogleUid" to FirestoreRestCodec.stringVal(""),
                "ownerUid" to FirestoreRestCodec.stringVal(authSession.uid),
                "sessionRevokedBeforeMillis" to FirestoreRestCodec.intVal(0L)
            )
        ).getOrThrow()

        writeAuditLog(session, "CREATE_BOOKER", "", "Created booker ${fullName.trim()} ($code)")
        GeneratedCredentials(
            fullName = fullName.trim(),
            accountCode = code,
            roleLabel = "Booker",
            businessCode = session.businessCode,
            username = username.trim(),
            temporaryPassword = tempPass
        )
    }

    suspend fun createStaffUser(
        session: ActiveWorkspaceSession,
        fullName: String,
        username: String,
        mobile: String,
        email: String,
        permissions: StaffPermission
    ): Result<GeneratedCredentials> = runCatching {
        val authSession = authClient.restoreSavedSession()
            ?: throw IllegalStateException("Google Sign-In required.")
        val id = "stf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val code = SharedPasswordHasher.generateCode("STF")
        val tempPass = SharedPasswordHasher.generateTemporaryPassword()
        val hashed = SharedPasswordHasher.hashPassword(tempPass)
        val ticketId = SharedPasswordHasher.buildTicketId(session.businessCode, username)

        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "businesses/${session.businessId}/users/$id",
            fields = mapOf(
                "businessId" to FirestoreRestCodec.stringVal(session.businessId),
                "userCode" to FirestoreRestCodec.stringVal(code),
                "fullName" to FirestoreRestCodec.stringVal(fullName.trim()),
                "username" to FirestoreRestCodec.stringVal(username.trim()),
                "mobileNumber" to FirestoreRestCodec.stringVal(mobile.trim()),
                "email" to FirestoreRestCodec.stringVal(email.trim()),
                "role" to FirestoreRestCodec.stringVal(AccountRole.STAFF.key),
                "status" to FirestoreRestCodec.stringVal("ACTIVE"),
                "mustChangePassword" to FirestoreRestCodec.boolVal(true),
                "sessionRevokedBeforeMillis" to FirestoreRestCodec.intVal(0L),
                "lastLoginMillis" to FirestoreRestCodec.intVal(0L)
            )
        ).getOrThrow()

        val finalPerm = permissions.copy(id = id, businessId = session.businessId, userId = id)
        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "businesses/${session.businessId}/permissions/$id",
            fields = permissionToFields(finalPerm)
        ).getOrThrow()

        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "login_tickets/$ticketId",
            fields = mapOf(
                "businessId" to FirestoreRestCodec.stringVal(session.businessId),
                "businessName" to FirestoreRestCodec.stringVal(session.businessName),
                "businessCode" to FirestoreRestCodec.stringVal(session.businessCode),
                "accountId" to FirestoreRestCodec.stringVal(id),
                "role" to FirestoreRestCodec.stringVal(AccountRole.STAFF.key),
                "username" to FirestoreRestCodec.stringVal(username.trim()),
                "fullName" to FirestoreRestCodec.stringVal(fullName.trim()),
                "passwordSalt" to FirestoreRestCodec.stringVal(hashed.saltBase64),
                "passwordHash" to FirestoreRestCodec.stringVal(hashed.hashBase64),
                "mustChangePassword" to FirestoreRestCodec.boolVal(true),
                "status" to FirestoreRestCodec.stringVal("ACTIVE"),
                "boundGoogleUid" to FirestoreRestCodec.stringVal(""),
                "ownerUid" to FirestoreRestCodec.stringVal(authSession.uid),
                "sessionRevokedBeforeMillis" to FirestoreRestCodec.intVal(0L)
            )
        ).getOrThrow()

        writeAuditLog(session, "CREATE_STAFF", "", "Created staff ${fullName.trim()} ($code)")
        GeneratedCredentials(
            fullName = fullName.trim(),
            accountCode = code,
            roleLabel = "Authorized Staff",
            businessCode = session.businessCode,
            username = username.trim(),
            temporaryPassword = tempPass
        )
    }

    suspend fun updateStaffPermission(session: ActiveWorkspaceSession, p: StaffPermission): Result<Unit> = runCatching {
        firestore.patchFieldsWithServerTimestamp(
            relativeDocPath = "businesses/${session.businessId}/permissions/${p.userId}",
            fields = permissionToFields(p),
            includeUpdatedAt = true
        ).getOrThrow()
    }

    suspend fun createOrUpdateProduct(
        session: ActiveWorkspaceSession,
        sku: String,
        name: String,
        unit: String,
        unitPricePaisa: Long,
        stockQty: Int,
        reorderLevel: Int
    ): Result<ProductItem> = runCatching {
        val now = SharedPakistanTime.nowMillis()
        val id = "prd_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val item = ProductItem(
            id = id,
            businessId = session.businessId,
            sku = sku.trim().ifBlank { SharedPasswordHasher.generateCode("SKU") },
            name = name.trim(),
            unit = unit.trim().ifBlank { "Pack" },
            unitPricePaisa = unitPricePaisa,
            stockQty = stockQty,
            reorderLevel = reorderLevel,
            isArchived = false,
            updatedAtMillis = now,
            syncStatus = SyncStatus.SYNCED.key
        )
        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "businesses/${session.businessId}/products/$id",
            fields = mapOf(
                "businessId" to FirestoreRestCodec.stringVal(session.businessId),
                "sku" to FirestoreRestCodec.stringVal(item.sku),
                "name" to FirestoreRestCodec.stringVal(item.name),
                "unit" to FirestoreRestCodec.stringVal(item.unit),
                "unitPricePaisa" to FirestoreRestCodec.intVal(item.unitPricePaisa),
                "stockQty" to FirestoreRestCodec.intVal(item.stockQty.toLong()),
                "reorderLevel" to FirestoreRestCodec.intVal(item.reorderLevel.toLong()),
                "isArchived" to FirestoreRestCodec.boolVal(false),
                "updatedAtMillis" to FirestoreRestCodec.intVal(now)
            )
        ).getOrThrow()
        db.upsertProduct(item)
        item
    }

    suspend fun reverseRecovery(
        session: ActiveWorkspaceSession,
        original: RecoveryTransaction,
        reason: String
    ): Result<Unit> = runCatching {
        require(session.role == AccountRole.SUPER_ADMIN) {
            "Only Super Admin can reverse a recovery entry."
        }
        require(reason.trim().length >= 3) { "Please provide a clear reversal reason." }
        val revId = "rcrev_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val now = SharedPakistanTime.nowMillis()
        val revTx = RecoveryTransaction(
            id = revId,
            businessId = session.businessId,
            txCode = "REV-${original.txCode}",
            shopId = original.shopId,
            shopUid = original.shopUid,
            shopName = original.shopName,
            bookerId = original.bookerId,
            bookerName = original.bookerName,
            amountPaisa = original.amountPaisa,
            transactionKind = "RECOVERY_REVERSAL",
            relatedTxId = original.id,
            reason = reason.trim(),
            paymentMethod = original.paymentMethod,
            referenceNumber = original.referenceNumber,
            notes = "Reversed original ${original.txCode}",
            txDateMillis = now,
            txDateFormatted = SharedPakistanTime.formatDate(now),
            txTimeFormatted = SharedPakistanTime.formatTime(now),
            createdByAccountId = session.accountId,
            createdByName = session.fullName,
            createdByRole = session.role.key,
            originalAmountPaisa = original.amountPaisa,
            originalTxDateMillis = original.txDateMillis,
            reversedAtMillis = now,
            reversedByAccountId = session.accountId,
            reversedByName = session.fullName,
            syncStatus = SyncStatus.SYNCED.key,
            idempotencyToken = "win_rev_${original.id}",
            deviceId = "windows-desktop"
        )
        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "businesses/${session.businessId}/recovery_transactions/$revId",
            fields = FirestoreRestCodec.recoveryToFields(revTx)
        ).getOrThrow()
        db.upsertRecovery(revTx)
        writeAuditLog(session, "REVERSE_RECOVERY", original.shopUid, "Reversed ${original.txCode} (${SharedCurrencyFormatter.formatPkr(original.amountPaisa)}): $reason")
    }

    suspend fun sendChatMessage(
        session: ActiveWorkspaceSession,
        group: ChatGroup,
        text: String
    ): Result<Unit> = runCatching {
        val clean = text.trim()
        require(clean.isNotEmpty()) { "Message cannot be empty." }
        val now = SharedPakistanTime.nowMillis()
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "businesses/${session.businessId}/chat_groups/${group.id}/messages/$msgId",
            fields = mapOf(
                "businessId" to FirestoreRestCodec.stringVal(session.businessId),
                "groupId" to FirestoreRestCodec.stringVal(group.id),
                "senderAccountId" to FirestoreRestCodec.stringVal(session.accountId),
                "senderName" to FirestoreRestCodec.stringVal(session.fullName),
                "senderRole" to FirestoreRestCodec.stringVal(session.role.label),
                "messageType" to FirestoreRestCodec.stringVal("TEXT"),
                "text" to FirestoreRestCodec.stringVal(clean),
                "mediaBase64" to FirestoreRestCodec.stringVal(""),
                "fileName" to FirestoreRestCodec.stringVal(""),
                "mimeType" to FirestoreRestCodec.stringVal(""),
                "durationSec" to FirestoreRestCodec.intVal(0L),
                "fileSizeBytes" to FirestoreRestCodec.intVal(0L),
                "sentAtMillis" to FirestoreRestCodec.intVal(now),
                "dateFormatted" to FirestoreRestCodec.stringVal(SharedPakistanTime.formatDate(now)),
                "timeFormatted" to FirestoreRestCodec.stringVal(SharedPakistanTime.formatTime(now))
            ),
            includeCreatedAt = true,
            includeUpdatedAt = false
        ).getOrThrow()
    }

    suspend fun fetchChatMessages(businessId: String, groupId: String): Result<List<ChatMessage>> = runCatching {
        val docs = firestore.listCollection("businesses/$businessId/chat_groups/$groupId/messages").getOrThrow()
        val list = docs.map { FirestoreRestCodec.toChatMessage(it) }.sortedBy { it.sentAtMillis }
        db.replaceChatMessages(groupId, list)
        list
    }

    suspend fun changeWorkspacePassword(
        session: ActiveWorkspaceSession,
        currentPassword: String,
        newPassword: String
    ): Result<ActiveWorkspaceSession> = runCatching {
        SharedPasswordHasher.validateStrongPassword(newPassword)?.let { throw IllegalArgumentException(it) }
        val ticketId = SharedPasswordHasher.buildTicketId(session.businessCode, session.username)
        val ticketDoc = firestore.getDocument("login_tickets/$ticketId").getOrThrow()
            ?: throw IllegalStateException("Login ticket not found on cloud.")
        val ticket = FirestoreRestCodec.toLoginTicket(ticketDoc)
        if (!SharedPasswordHasher.verifyPassword(currentPassword, ticket.passwordSalt, ticket.passwordHash)) {
            throw IllegalArgumentException("Current password is incorrect.")
        }
        val hashed = SharedPasswordHasher.hashPassword(newPassword)
        firestore.patchFieldsWithServerTimestamp(
            relativeDocPath = "login_tickets/$ticketId",
            fields = mapOf(
                "passwordSalt" to FirestoreRestCodec.stringVal(hashed.saltBase64),
                "passwordHash" to FirestoreRestCodec.stringVal(hashed.hashBase64),
                "mustChangePassword" to FirestoreRestCodec.boolVal(false)
            )
        ).getOrThrow()
        val accountDocPath = if (session.role == AccountRole.BOOKER) {
            "businesses/${session.businessId}/bookers/${session.accountId}"
        } else {
            "businesses/${session.businessId}/users/${session.accountId}"
        }
        firestore.patchFieldsWithServerTimestamp(
            relativeDocPath = accountDocPath,
            fields = mapOf("mustChangePassword" to FirestoreRestCodec.boolVal(false))
        )
        val updated = session.copy(mustChangePassword = false)
        persistWorkspaceSession(updated, getWorkspaceLoginAtMillis().takeIf { it > 0L } ?: SharedPakistanTime.nowMillis())
        writeAuditLog(session, "CHANGE_PASSWORD", "", "Updated workspace account password from Windows Desktop")
        updated
    }

    suspend fun reverseCredit(
        session: ActiveWorkspaceSession,
        original: CreditTransaction,
        reason: String
    ): Result<Unit> = runCatching {
        require(session.role == AccountRole.SUPER_ADMIN) {
            "Only Super Admin can reverse a credit entry."
        }
        require(reason.trim().length >= 3) { "Please provide a mandatory reversal reason." }
        val revId = "crrev_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val now = SharedPakistanTime.nowMillis()
        val revTx = CreditTransaction(
            id = revId,
            businessId = session.businessId,
            txCode = "REV-${original.txCode}",
            shopId = original.shopId,
            shopUid = original.shopUid,
            shopName = original.shopName,
            bookerId = original.bookerId,
            bookerName = original.bookerName,
            amountPaisa = original.amountPaisa,
            transactionKind = "CREDIT_REVERSAL",
            relatedTxId = original.id,
            reason = reason.trim(),
            invoiceNumber = original.invoiceNumber,
            description = "Reversed credit ${original.txCode}",
            txDateMillis = now,
            txDateFormatted = SharedPakistanTime.formatDate(now),
            txTimeFormatted = SharedPakistanTime.formatTime(now),
            createdByAccountId = session.accountId,
            createdByName = session.fullName,
            createdByRole = session.role.key,
            entryTimestampMillis = now,
            entryDateFormatted = SharedPakistanTime.formatDate(now),
            entryTimeFormatted = SharedPakistanTime.formatTime(now),
            syncStatus = SyncStatus.SYNCED.key,
            idempotencyToken = "win_crrev_${original.id}",
            deviceId = "windows-desktop"
        )
        firestore.commitWriteWithServerTimestamp(
            relativeDocPath = "businesses/${session.businessId}/credit_transactions/$revId",
            fields = FirestoreRestCodec.creditToFields(revTx)
        ).getOrThrow()
        db.upsertCredit(revTx)
        writeAuditLog(session, "REVERSE_CREDIT", original.shopUid, "Reversed credit ${original.txCode} (${SharedCurrencyFormatter.formatPkr(original.amountPaisa)}): $reason")
    }

    suspend fun updateBusinessSettings(
        session: ActiveWorkspaceSession,
        businessName: String,
        businessAddress: String,
        contactPhone: String,
        allowOverRecovery: Boolean,
        requireInvoiceNumber: Boolean
    ): Result<Unit> = runCatching {
        require(session.role == AccountRole.SUPER_ADMIN) { "Only Super Admin can update business settings." }
        firestore.patchFieldsWithServerTimestamp(
            relativeDocPath = "businesses/${session.businessId}/business_settings/default",
            fields = mapOf(
                "businessName" to FirestoreRestCodec.stringVal(businessName.trim()),
                "businessAddress" to FirestoreRestCodec.stringVal(businessAddress.trim()),
                "contactPhone" to FirestoreRestCodec.stringVal(contactPhone.trim()),
                "allowOverRecovery" to FirestoreRestCodec.boolVal(allowOverRecovery),
                "requireInvoiceNumber" to FirestoreRestCodec.boolVal(requireInvoiceNumber),
                "updatedBy" to FirestoreRestCodec.stringVal(session.fullName)
            )
        ).getOrThrow()
        firestore.patchFieldsWithServerTimestamp(
            relativeDocPath = "businesses/${session.businessId}",
            fields = mapOf(
                "businessName" to FirestoreRestCodec.stringVal(businessName.trim()),
                "allowOverRecovery" to FirestoreRestCodec.boolVal(allowOverRecovery),
                "lastActivityMillis" to FirestoreRestCodec.intVal(SharedPakistanTime.nowMillis())
            )
        ).getOrThrow()
        writeAuditLog(session, "UPDATE_SETTINGS", "", "Updated business settings (allowOverRecovery=$allowOverRecovery)")
    }

    suspend fun markNotificationRead(session: ActiveWorkspaceSession, notificationId: String): Result<Unit> = runCatching {
        firestore.patchFieldsWithServerTimestamp(
            relativeDocPath = "businesses/${session.businessId}/notifications/$notificationId",
            fields = mapOf("isRead" to FirestoreRestCodec.boolVal(true))
        ).getOrThrow()
    }

    private suspend fun writeAuditLog(
        session: ActiveWorkspaceSession,
        action: String,
        shopUid: String,
        description: String
    ) {
        val now = SharedPakistanTime.nowMillis()
        val logId = "aud_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        runCatching {
            firestore.commitWriteWithServerTimestamp(
                relativeDocPath = "businesses/${session.businessId}/audit_logs/$logId",
                fields = mapOf(
                    "businessId" to FirestoreRestCodec.stringVal(session.businessId),
                    "activityCode" to FirestoreRestCodec.stringVal(SharedPasswordHasher.generateCode("ACT")),
                    "userId" to FirestoreRestCodec.stringVal(session.accountId),
                    "username" to FirestoreRestCodec.stringVal(session.username),
                    "role" to FirestoreRestCodec.stringVal(session.role.key),
                    "action" to FirestoreRestCodec.stringVal(action),
                    "shopUid" to FirestoreRestCodec.stringVal(shopUid),
                    "previousValue" to FirestoreRestCodec.stringVal(""),
                    "newValue" to FirestoreRestCodec.stringVal(""),
                    "dateFormatted" to FirestoreRestCodec.stringVal(SharedPakistanTime.formatDate(now)),
                    "timeFormatted" to FirestoreRestCodec.stringVal(SharedPakistanTime.formatTime(now)),
                    "timestampMillis" to FirestoreRestCodec.intVal(now),
                    "description" to FirestoreRestCodec.stringVal(description)
                ),
                includeCreatedAt = true,
                includeUpdatedAt = false
            )
        }
    }

    private fun permissionToFields(p: StaffPermission): Map<String, JSONObject> = mapOf(
        "businessId" to FirestoreRestCodec.stringVal(p.businessId),
        "userId" to FirestoreRestCodec.stringVal(p.userId),
        "viewDashboard" to FirestoreRestCodec.boolVal(p.viewDashboard),
        "viewShops" to FirestoreRestCodec.boolVal(p.viewShops),
        "addShops" to FirestoreRestCodec.boolVal(p.addShops),
        "editShops" to FirestoreRestCodec.boolVal(p.editShops),
        "addCredit" to FirestoreRestCodec.boolVal(p.addCredit),
        "editCredit" to FirestoreRestCodec.boolVal(p.editCredit),
        "recordRecovery" to FirestoreRestCodec.boolVal(p.recordRecovery),
        "viewRecoveryHistory" to FirestoreRestCodec.boolVal(p.viewRecoveryHistory),
        "viewBookers" to FirestoreRestCodec.boolVal(p.viewBookers),
        "manageBookers" to FirestoreRestCodec.boolVal(p.manageBookers),
        "viewReports" to FirestoreRestCodec.boolVal(p.viewReports),
        "generatePdfs" to FirestoreRestCodec.boolVal(p.generatePdfs),
        "searchShops" to FirestoreRestCodec.boolVal(p.searchShops),
        "viewActivityLogs" to FirestoreRestCodec.boolVal(p.viewActivityLogs)
    )
}
