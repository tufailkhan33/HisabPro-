package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.local.CachedSessionEntity
import com.example.data.local.HisabLocalDao
import com.example.data.local.HisabLocalDatabase
import com.example.data.local.LocalCryptoHelper
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.domain.model.AccountRole
import com.example.domain.model.ActiveWorkspaceSession
import com.example.domain.model.AuditLog
import com.example.domain.model.Booker
import com.example.domain.model.Business
import com.example.domain.model.BusinessSettings
import com.example.domain.model.BusinessUser
import com.example.domain.model.ChatGroup
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatMessageType
import com.example.domain.model.CreditTransaction
import com.example.domain.model.FinancialEngine
import com.example.domain.model.GeneratedCredentials
import com.example.domain.model.LoginTicket
import com.example.domain.model.NotificationItem
import com.example.domain.model.PaymentMethod
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.Shop
import com.example.domain.model.StaffPermission
import com.example.domain.model.UserWorkspaceBookmark
import com.example.domain.model.WorkspaceMember
import com.example.util.CurrencyFormatter
import com.example.util.PakistanTime
import com.example.util.PasswordHasher
import com.example.util.SecurityRateLimiter
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class TicketSessionStatus(
    val isValid: Boolean,
    val invalidReason: String = "",
    val mustChangePassword: Boolean = false
)

class HisabProRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val rateLimiter: SecurityRateLimiter = SecurityRateLimiter(),
    private val dao: HisabLocalDao? = null
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth,
        SecurityRateLimiter(),
        HisabLocalDatabase.getInstance(context.applicationContext).hisabDao()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Google Sign-In required before accessing Firestore.")
    }

    private fun businessRef(businessId: String) = db.collection("businesses").document(businessId)

    // --- Real-Time Observation Streams ---

    fun observeUserWorkspaces(googleUid: String): Flow<List<UserWorkspaceBookmark>> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$googleUid/workspaces"
        emitAll(
            db.collection("users").document(googleUid).collection("workspaces")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        UserWorkspaceBookmark::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, googleUid)
                    if (!isPermissionDenied(error)) emit(emptyList()) else throw error
                }
        )
    }

    private fun isPermissionDenied(error: Throwable): Boolean {
        val fsErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        return fsErr?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }

    fun observeBusiness(businessId: String): Flow<Business?> = flow {
        val uid = auth.currentUser?.uid
        val path = "businesses/$businessId"
        if (uid == null) {
            if (dao != null) {
                emitAll(dao.observeBusiness(businessId).map { it?.toDomain() })
                return@flow
            }
            requireUserId()
        }
        emitAll(
            businessRef(businessId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(Business::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .onEach { biz -> if (biz != null) dao?.upsertBusiness(biz.toEntity()) }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid.orEmpty())
                    if (dao != null && !isPermissionDenied(error)) {
                        emitAll(dao.observeBusiness(businessId).map { it?.toDomain() })
                    } else {
                        throw error
                    }
                }
        )
    }

    fun observeBusinessSettings(businessId: String): Flow<BusinessSettings?> = flow {
        val uid = auth.currentUser?.uid
        val path = "businesses/$businessId/business_settings/default"
        if (uid == null && dao != null) {
            emitAll(dao.observeSettings(businessId).map { it?.toDomain() })
            return@flow
        }
        val activeUid = requireUserId()
        emitAll(
            businessRef(businessId).collection("business_settings").document("default")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(BusinessSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .onEach { st -> if (st != null) dao?.upsertSettings(st.toEntity()) }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, activeUid)
                    if (dao != null && !isPermissionDenied(error)) {
                        emitAll(dao.observeSettings(businessId).map { it?.toDomain() })
                    } else {
                        throw error
                    }
                }
        )
    }

    fun observeCurrentMember(businessId: String, googleUid: String): Flow<WorkspaceMember?> = flow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            emit(null)
            return@flow
        }
        val path = "businesses/$businessId/members/$googleUid"
        emitAll(
            businessRef(businessId).collection("members").document(googleUid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(WorkspaceMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, uid)
                    if (!isPermissionDenied(error)) emit(null) else throw error
                }
        )
    }

    fun observePermissionForUser(businessId: String, accountId: String): Flow<StaffPermission?> = flow {
        val uid = auth.currentUser?.uid
        val path = "businesses/$businessId/permissions/$accountId"
        if (uid == null && dao != null) {
            emitAll(dao.observePermission(businessId, accountId).map { it?.toDomain() })
            return@flow
        }
        val activeUid = requireUserId()
        emitAll(
            businessRef(businessId).collection("permissions").document(accountId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(StaffPermission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .onEach { perm -> if (perm != null) dao?.upsertPermission(perm.toEntity()) }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, activeUid)
                    if (dao != null && !isPermissionDenied(error)) {
                        emitAll(dao.observePermission(businessId, accountId).map { it?.toDomain() })
                    } else {
                        throw error
                    }
                }
        )
    }

    fun observeAllPermissions(businessId: String): Flow<List<StaffPermission>> = flow {
        val uid = requireUserId()
        val path = "businesses/$businessId/permissions"
        emitAll(
            businessRef(businessId).collection("permissions")
                .snapshots()
                .map { it.toObjects(StaffPermission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeStaffUsers(businessId: String): Flow<List<BusinessUser>> = flow {
        val uid = auth.currentUser?.uid
        val path = "businesses/$businessId/users"
        if (uid == null && dao != null) {
            emitAll(dao.observeStaffUsers(businessId).map { list -> list.map { it.toDomain() } })
            return@flow
        }
        val activeUid = requireUserId()
        emitAll(
            businessRef(businessId).collection("users")
                .snapshots()
                .map { snap ->
                    snap.toObjects(BusinessUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.fullName }
                }
                .onEach { list -> dao?.replaceStaffUsers(businessId, list.map { it.toEntity() }) }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, activeUid)
                    if (dao != null && !isPermissionDenied(error)) {
                        emitAll(dao.observeStaffUsers(businessId).map { list -> list.map { it.toDomain() } })
                    } else {
                        throw error
                    }
                }
        )
    }

    fun observeBookers(businessId: String): Flow<List<Booker>> = flow {
        val uid = auth.currentUser?.uid
        val path = "businesses/$businessId/bookers"
        if (uid == null && dao != null) {
            emitAll(dao.observeBookers(businessId).map { list -> list.map { it.toDomain() } })
            return@flow
        }
        val activeUid = requireUserId()
        emitAll(
            businessRef(businessId).collection("bookers")
                .snapshots()
                .map { snap ->
                    snap.toObjects(Booker::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.fullName }
                }
                .onEach { list -> dao?.replaceBookers(businessId, list.map { it.toEntity() }) }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, activeUid)
                    if (dao != null && !isPermissionDenied(error)) {
                        emitAll(dao.observeBookers(businessId).map { list -> list.map { it.toDomain() } })
                    } else {
                        throw error
                    }
                }
        )
    }

    fun observeSingleBooker(businessId: String, bookerId: String): Flow<Booker?> = flow {
        val uid = auth.currentUser?.uid
        val path = "businesses/$businessId/bookers/$bookerId"
        if (uid == null && dao != null) {
            emitAll(
                dao.observeBookers(businessId).map { list ->
                    list.firstOrNull { it.id == bookerId }?.toDomain()
                }
            )
            return@flow
        }
        val activeUid = requireUserId()
        emitAll(
            businessRef(businessId).collection("bookers").document(bookerId)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(Booker::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .onEach { booker ->
                    if (booker != null) {
                        dao?.replaceBookers(businessId, listOf(booker.toEntity()))
                    }
                }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, activeUid)
                    if (dao != null && !isPermissionDenied(error)) {
                        emitAll(
                            dao.observeBookers(businessId).map { list ->
                                list.firstOrNull { it.id == bookerId }?.toDomain()
                            }
                        )
                    } else {
                        throw error
                    }
                }
        )
    }

    fun observeShops(businessId: String, role: AccountRole, bookerId: String): Flow<List<Shop>> = flow {
        val uid = auth.currentUser?.uid
        val path = "businesses/$businessId/shops"
        if (uid == null) {
            if (dao != null) {
                val localFlow = if (role == AccountRole.BOOKER) {
                    dao.observeShopsForBooker(businessId, bookerId)
                } else {
                    dao.observeShops(businessId)
                }
                emitAll(localFlow.map { list -> list.map { it.toDomain() }.sortedByDescending { it.openingDateMillis } })
                return@flow
            }
            requireUserId()
        }
        val col = businessRef(businessId).collection("shops")
        val query = if (role == AccountRole.BOOKER) col.whereEqualTo("bookerId", bookerId) else col
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(Shop::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.openingDateMillis }
                }
                .onEach { list ->
                    if (role != AccountRole.BOOKER) {
                        dao?.replaceShops(businessId, list.map { it.toEntity() })
                    } else if (list.isNotEmpty()) {
                        dao?.upsertShops(list.map { it.toEntity() })
                    }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid.orEmpty())
                    if (dao != null && !isPermissionDenied(error)) {
                        val localFlow = if (role == AccountRole.BOOKER) {
                            dao.observeShopsForBooker(businessId, bookerId)
                        } else {
                            dao.observeShops(businessId)
                        }
                        emitAll(localFlow.map { list -> list.map { it.toDomain() }.sortedByDescending { it.openingDateMillis } })
                    } else {
                        throw error
                    }
                }
        )
    }

    fun observeCreditTransactions(
        businessId: String,
        role: AccountRole,
        bookerId: String
    ): Flow<List<CreditTransaction>> = flow {
        val uid = auth.currentUser?.uid
        val path = "businesses/$businessId/credit_transactions"
        val localCreditsFlow = if (dao != null) {
            (if (role == AccountRole.BOOKER) {
                dao.observeCreditTransactionsForBooker(businessId, bookerId)
            } else {
                dao.observeCreditTransactions(businessId)
            }).map { list -> list.map { it.toDomain() }.sortedByDescending { it.txDateMillis } }
        } else null

        if (uid == null) {
            if (localCreditsFlow != null) {
                emitAll(localCreditsFlow)
                return@flow
            }
            requireUserId()
        }

        val col = businessRef(businessId).collection("credit_transactions")
        val query = if (role == AccountRole.BOOKER) col.whereEqualTo("bookerId", bookerId) else col
        val remoteFlow = query.snapshots()
            .map { snap ->
                snap.toObjects(CreditTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.txDateMillis }
            }
            .onEach { list ->
                if (role != AccountRole.BOOKER) {
                    dao?.replaceSyncedCredits(businessId, list.map { it.toEntity() })
                } else if (list.isNotEmpty()) {
                    dao?.upsertCreditTransactions(list.map { it.toEntity() })
                }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid.orEmpty())
                if (localCreditsFlow != null && !isPermissionDenied(error)) {
                    emitAll(localCreditsFlow)
                } else {
                    throw error
                }
            }

        if (dao == null) {
            emitAll(remoteFlow)
        } else {
            val localPendingFlow = dao.observeCreditTransactions(businessId)
                .map { items -> items.filter { it.syncStatus != "SYNCED" }.map { it.toDomain() } }
            emitAll(
                combine(remoteFlow, localPendingFlow) { remote, pending ->
                    val existingIds = remote.mapTo(HashSet()) { it.idempotencyToken.ifBlank { it.id } }
                    val uniquePending = pending.filter { p ->
                        val key = p.idempotencyToken.ifBlank { p.id }
                        key !in existingIds && (role != AccountRole.BOOKER || p.bookerId == bookerId)
                    }
                    (uniquePending + remote).sortedByDescending { it.txDateMillis }
                }
            )
        }
    }

    fun observeRecoveryTransactions(
        businessId: String,
        role: AccountRole,
        bookerId: String
    ): Flow<List<RecoveryTransaction>> = flow {
        val uid = auth.currentUser?.uid
        val path = "businesses/$businessId/recovery_transactions"
        val localFlow = if (dao != null) {
            (if (role == AccountRole.BOOKER) {
                dao.observeRecoveryTransactionsForBooker(businessId, bookerId)
            } else {
                dao.observeRecoveryTransactions(businessId)
            }).map { list -> list.map { it.toDomain() }.sortedByDescending { it.txDateMillis } }
        } else null

        if (uid == null) {
            if (localFlow != null) {
                emitAll(localFlow)
                return@flow
            }
            requireUserId()
        }

        val col = businessRef(businessId).collection("recovery_transactions")
        val query = if (role == AccountRole.BOOKER) col.whereEqualTo("bookerId", bookerId) else col
        val remoteFlow = query.snapshots()
            .map { snap ->
                snap.toObjects(RecoveryTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.txDateMillis }
            }
            .onEach { list ->
                if (role != AccountRole.BOOKER) {
                    dao?.replaceSyncedRecoveries(businessId, list.map { it.toEntity() })
                } else if (list.isNotEmpty()) {
                    dao?.upsertRecoveryTransactions(list.map { it.toEntity() })
                }
            }
            .catch { error ->
                logStreamError(error, OperationType.LIST, path, uid.orEmpty())
                if (localFlow != null && !isPermissionDenied(error)) {
                    emitAll(localFlow)
                } else {
                    throw error
                }
            }

        if (dao == null) {
            emitAll(remoteFlow)
        } else {
            val localPendingFlow = dao.observeRecoveryTransactions(businessId)
                .map { items -> items.filter { it.syncStatus != "SYNCED" }.map { it.toDomain() } }
            emitAll(
                combine(remoteFlow, localPendingFlow) { remote, pending ->
                    val existingTokens = remote.mapTo(HashSet()) { it.idempotencyToken.ifBlank { it.id } }
                    val uniquePending = pending.filter { p ->
                        val key = p.idempotencyToken.ifBlank { p.id }
                        key !in existingTokens && (role != AccountRole.BOOKER || p.bookerId == bookerId)
                    }
                    (uniquePending + remote).sortedByDescending { it.txDateMillis }
                }
            )
        }
    }

    fun observeAuditLogs(businessId: String): Flow<List<AuditLog>> = flow {
        val uid = auth.currentUser?.uid
        val path = "businesses/$businessId/audit_logs"
        if (uid == null && dao != null) {
            emitAll(dao.observeAuditLogs(businessId).map { list -> list.map { it.toDomain() } })
            return@flow
        }
        val activeUid = requireUserId()
        emitAll(
            businessRef(businessId).collection("audit_logs")
                .snapshots()
                .map { snap ->
                    snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.timestampMillis }
                }
                .onEach { list -> dao?.replaceAuditLogs(businessId, list.map { it.toEntity() }) }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, activeUid)
                    if (dao != null && !isPermissionDenied(error)) {
                        emitAll(dao.observeAuditLogs(businessId).map { list -> list.map { it.toDomain() } })
                    } else {
                        throw error
                    }
                }
        )
    }

    fun observeNotifications(
        businessId: String,
        role: AccountRole,
        bookerId: String
    ): Flow<List<NotificationItem>> = flow {
        val uid = requireUserId()
        val col = businessRef(businessId).collection("notifications")
        val query = if (role == AccountRole.BOOKER) col.whereEqualTo("targetBookerId", bookerId) else col
        val path = "businesses/$businessId/notifications"
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeChatGroups(
        businessId: String,
        role: AccountRole,
        accountId: String
    ): Flow<List<ChatGroup>> = flow {
        val uid = requireUserId()
        val col = businessRef(businessId).collection("chat_groups")
        val query = if (role == AccountRole.SUPER_ADMIN) {
            col
        } else {
            col.whereArrayContains("memberAccountIds", accountId)
        }
        val path = "businesses/$businessId/chat_groups"
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(ChatGroup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { !it.isArchived }
                        .sortedByDescending { maxOf(it.lastMessageAtMillis, (it.createdAt?.seconds ?: 0L) * 1000L) }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeChatMessages(
        businessId: String,
        groupId: String
    ): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        val path = "businesses/$businessId/chat_groups/$groupId/messages"
        emitAll(
            businessRef(businessId).collection("chat_groups").document(groupId)
                .collection("messages")
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.sentAtMillis }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    // --- Authentication & Workspace Provisioning ---

    suspend fun createBusinessAndSuperAdmin(
        fullName: String,
        businessName: String,
        username: String,
        password: String,
        mobileNumber: String,
        email: String
    ): Result<ActiveWorkspaceSession> = runCatching {
        val googleUid = requireUserId()
        val cleanName = fullName.trim()
        val cleanBiz = businessName.trim()
        val cleanUser = username.trim()
        val cleanMobile = mobileNumber.trim()
        val cleanEmail = email.trim()

        require(cleanName.length >= 2) { "Full name must be at least 2 characters." }
        require(cleanBiz.length >= 2) { "Business name must be at least 2 characters." }
        require(cleanUser.length >= 2) { "Username must be at least 2 characters." }
        require(password.length >= 6) { "Password must be at least 6 characters." }
        require(cleanMobile.length >= 7) { "Enter a valid mobile number." }

        val businessId = "biz_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val businessCode = PasswordHasher.generateBusinessCode(cleanBiz)
        val adminAccountId = "adm_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val ticketId = PasswordHasher.buildTicketId(businessCode, cleanUser)
        val hashed = PasswordHasher.hashPassword(password)
        val nowMillis = PakistanTime.nowMillis()

        val batch = db.batch()

        val bizDoc = businessRef(businessId)
        batch.set(
            bizDoc,
            mapOf(
                "businessName" to cleanBiz,
                "businessCode" to businessCode,
                "ownerUid" to googleUid,
                "ownerName" to cleanName,
                "ownerMobile" to cleanMobile,
                "ownerEmail" to cleanEmail,
                "currency" to "PKR",
                "timezone" to "Asia/Karachi",
                "allowOverRecovery" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val userDoc = bizDoc.collection("users").document(adminAccountId)
        batch.set(
            userDoc,
            mapOf(
                "businessId" to businessId,
                "userCode" to "ADM-0001",
                "fullName" to cleanName,
                "username" to cleanUser,
                "mobileNumber" to cleanMobile,
                "email" to cleanEmail,
                "role" to AccountRole.SUPER_ADMIN.key,
                "status" to "ACTIVE",
                "mustChangePassword" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val permDoc = bizDoc.collection("permissions").document(adminAccountId)
        batch.set(permDoc, permissionPayload(businessId, adminAccountId, StaffPermission.fullAdmin(businessId, adminAccountId), isCreate = true))

        val ticketDoc = db.collection("login_tickets").document(ticketId)
        batch.set(
            ticketDoc,
            mapOf(
                "businessId" to businessId,
                "businessName" to cleanBiz,
                "businessCode" to businessCode,
                "accountId" to adminAccountId,
                "role" to AccountRole.SUPER_ADMIN.key,
                "username" to cleanUser,
                "fullName" to cleanName,
                "passwordSalt" to hashed.saltBase64,
                "passwordHash" to hashed.hashBase64,
                "mustChangePassword" to false,
                "status" to "ACTIVE",
                "boundGoogleUid" to googleUid,
                "ownerUid" to googleUid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val memberDoc = bizDoc.collection("members").document(googleUid)
        batch.set(
            memberDoc,
            mapOf(
                "businessId" to businessId,
                "googleUid" to googleUid,
                "accountId" to adminAccountId,
                "username" to cleanUser,
                "fullName" to cleanName,
                "role" to AccountRole.SUPER_ADMIN.key,
                "bookerId" to "",
                "status" to "ACTIVE",
                "claimedTicketId" to ticketId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val settingsDoc = bizDoc.collection("business_settings").document("default")
        batch.set(
            settingsDoc,
            mapOf(
                "businessId" to businessId,
                "businessName" to cleanBiz,
                "businessAddress" to "",
                "contactPhone" to cleanMobile,
                "allowOverRecovery" to false,
                "requireInvoiceNumber" to false,
                "updatedBy" to cleanName,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val bookmarkDoc = db.collection("users").document(googleUid).collection("workspaces").document(businessId)
        batch.set(
            bookmarkDoc,
            mapOf(
                "businessId" to businessId,
                "businessName" to cleanBiz,
                "businessCode" to businessCode,
                "lastUsername" to cleanUser,
                "lastRole" to AccountRole.SUPER_ADMIN.key,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            bizDoc.collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = businessId,
                userId = adminAccountId,
                username = cleanUser,
                role = AccountRole.SUPER_ADMIN.key,
                action = "WORKSPACE_CREATED",
                shopUid = "",
                previousValue = "",
                newValue = "$cleanBiz ($businessCode)",
                timestampMillis = nowMillis,
                description = "Created business workspace $cleanBiz with Super Admin $cleanName"
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, bizDoc.path, auth)
            throw e
        }

        ActiveWorkspaceSession(
            businessId = businessId,
            businessCode = businessCode,
            businessName = cleanBiz,
            accountId = adminAccountId,
            username = cleanUser,
            fullName = cleanName,
            role = AccountRole.SUPER_ADMIN,
            bookerId = "",
            mustChangePassword = false
        )
    }

    suspend fun loginWithCredentials(
        businessCode: String,
        username: String,
        password: String
    ): Result<ActiveWorkspaceSession> = runCatching {
        val googleUid = requireUserId()
        val cleanCode = businessCode.trim().uppercase()
        val cleanUser = username.trim()
        require(cleanCode.isNotBlank() && cleanUser.isNotBlank() && password.isNotBlank()) {
            "Business Code, Username, and Password are required."
        }

        val ticketId = PasswordHasher.buildTicketId(cleanCode, cleanUser)
        val rateKey = "$cleanCode:$cleanUser"
        rateLimiter.checkAllowed(SecurityRateLimiter.Operation.LOGIN, rateKey)

        val ticketRef = db.collection("login_tickets").document(ticketId)
        val snap = try {
            ticketRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, ticketRef.path, auth)
            throw e
        }

        if (!snap.exists()) {
            rateLimiter.recordFailure(SecurityRateLimiter.Operation.LOGIN, rateKey)
            throw IllegalArgumentException("Invalid Business Code or Username.")
        }

        val ticket = snap.toObject(LoginTicket::class.java)
            ?: throw IllegalStateException("Corrupted login record.")

        if (ticket.status != "ACTIVE") {
            throw IllegalStateException("This account is currently ${ticket.status.lowercase()}. Contact your Super Admin.")
        }

        if (!PasswordHasher.verifyPassword(password, ticket.passwordSalt, ticket.passwordHash)) {
            rateLimiter.recordFailure(SecurityRateLimiter.Operation.LOGIN, rateKey)
            throw IllegalArgumentException("Incorrect password.")
        }

        if (ticket.role == AccountRole.SUPER_ADMIN.key && ticket.ownerUid != googleUid) {
            rateLimiter.recordFailure(SecurityRateLimiter.Operation.LOGIN, rateKey)
            throw SecurityException("Super Admin login requires the workspace owner's Google account.")
        }
        rateLimiter.recordSuccess(SecurityRateLimiter.Operation.LOGIN, rateKey)

        val role = AccountRole.fromKey(ticket.role)
        val bookerId = if (role == AccountRole.BOOKER) ticket.accountId else ""

        val batch = db.batch()
        if (ticket.boundGoogleUid.isBlank()) {
            batch.update(
                ticketRef,
                mapOf(
                    "boundGoogleUid" to googleUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val memberRef = businessRef(ticket.businessId).collection("members").document(googleUid)
        val existingMember = try {
            memberRef.get().await()
        } catch (_: Exception) {
            null
        }
        val memberPayload = mutableMapOf<String, Any>(
            "businessId" to ticket.businessId,
            "googleUid" to googleUid,
            "accountId" to ticket.accountId,
            "username" to ticket.username,
            "fullName" to ticket.fullName,
            "role" to ticket.role,
            "bookerId" to bookerId,
            "status" to ticket.status,
            "claimedTicketId" to ticketId,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (existingMember == null || !existingMember.exists()) {
            memberPayload["createdAt"] = FieldValue.serverTimestamp()
        } else {
            memberPayload["createdAt"] = existingMember.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
        }
        batch.set(memberRef, memberPayload)

        val bookmarkRef = db.collection("users").document(googleUid)
            .collection("workspaces").document(ticket.businessId)
        val existingBookmark = try {
            bookmarkRef.get().await()
        } catch (_: Exception) {
            null
        }
        val bookmarkPayload = mutableMapOf<String, Any>(
            "businessId" to ticket.businessId,
            "businessName" to ticket.businessName,
            "businessCode" to ticket.businessCode,
            "lastUsername" to ticket.username,
            "lastRole" to ticket.role,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (existingBookmark == null || !existingBookmark.exists()) {
            bookmarkPayload["createdAt"] = FieldValue.serverTimestamp()
        } else {
            bookmarkPayload["createdAt"] = existingBookmark.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()
        }
        batch.set(bookmarkRef, bookmarkPayload)

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, memberRef.path, auth)
            throw e
        }

        val bizCheck = try {
            businessRef(ticket.businessId).get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val bizStatus = bizCheck?.getString("status") ?: "ACTIVE"
        if (bizStatus == "SUSPENDED") {
            val reason = bizCheck?.getString("suspendedReason").orEmpty()
            throw IllegalStateException(
                "This company workspace has been suspended by the Platform Owner.${if (reason.isNotBlank()) " Reason: $reason" else ""}"
            )
        }

        // Persist encrypted offline login verifier in Room so previously logged-in users can work offline
        dao?.upsertCachedSession(
            CachedSessionEntity(
                ticketKey = ticketId,
                businessId = ticket.businessId,
                businessCode = ticket.businessCode,
                businessName = ticket.businessName,
                accountId = ticket.accountId,
                username = ticket.username,
                fullName = ticket.fullName,
                role = role.key,
                bookerId = bookerId,
                encryptedPasswordSalt = LocalCryptoHelper.encrypt(ticket.passwordSalt),
                encryptedPasswordHash = LocalCryptoHelper.encrypt(ticket.passwordHash),
                mustChangePassword = ticket.mustChangePassword,
                lastSyncedMillis = PakistanTime.nowMillis()
            )
        )

        ActiveWorkspaceSession(
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
    }

    suspend fun loginOfflineWithCachedCredentials(
        businessCode: String,
        username: String,
        password: String
    ): Result<ActiveWorkspaceSession> = runCatching {
        val localDao = dao ?: throw IllegalStateException("Offline cache database unavailable.")
        val cleanCode = businessCode.trim().uppercase()
        val cleanUser = username.trim()
        require(cleanCode.isNotBlank() && cleanUser.isNotBlank() && password.isNotBlank()) {
            "Business Code, Username, and Password are required."
        }
        val ticketId = PasswordHasher.buildTicketId(cleanCode, cleanUser)
        val cached = localDao.getCachedSession(ticketId)
            ?: throw IllegalStateException("No offline cached login found for @$cleanUser in $cleanCode. Connect to internet for first login.")

        val salt = LocalCryptoHelper.decrypt(cached.encryptedPasswordSalt)
        val hash = LocalCryptoHelper.decrypt(cached.encryptedPasswordHash)
        if (!PasswordHasher.verifyPassword(password, salt, hash)) {
            throw IllegalArgumentException("Incorrect password for offline login.")
        }

        val biz = localDao.getBusiness(cached.businessId)
        if (biz != null && biz.status == "SUSPENDED") {
            throw IllegalStateException("This business workspace is suspended.")
        }

        ActiveWorkspaceSession(
            businessId = cached.businessId,
            businessCode = cached.businessCode,
            businessName = cached.businessName,
            accountId = cached.accountId,
            username = cached.username,
            fullName = cached.fullName,
            role = AccountRole.fromKey(cached.role),
            bookerId = cached.bookerId,
            mustChangePassword = cached.mustChangePassword
        )
    }

    suspend fun validateActiveTicketSession(
        session: ActiveWorkspaceSession,
        loginAtMillis: Long
    ): TicketSessionStatus {
        if (auth.currentUser == null) {
            return TicketSessionStatus(isValid = true, mustChangePassword = session.mustChangePassword)
        }
        return runCatching {
            val bizSnap = businessRef(session.businessId).get().await()
            if (bizSnap.exists() && bizSnap.getString("status") == "SUSPENDED") {
                val reason = bizSnap.getString("suspendedReason").orEmpty()
                return@runCatching TicketSessionStatus(
                    isValid = false,
                    invalidReason = "Workspace suspended by Platform Owner.${if (reason.isNotBlank()) " Reason: $reason" else ""}"
                )
            }
            val ticketId = PasswordHasher.buildTicketId(session.businessCode, session.username)
            val ticketSnap = db.collection("login_tickets").document(ticketId).get().await()
            if (!ticketSnap.exists()) {
                return@runCatching TicketSessionStatus(isValid = true, mustChangePassword = session.mustChangePassword)
            }
            val ticket = ticketSnap.toObject(LoginTicket::class.java)
                ?: return@runCatching TicketSessionStatus(isValid = true, mustChangePassword = session.mustChangePassword)
            if (ticket.status != "ACTIVE") {
                return@runCatching TicketSessionStatus(
                    isValid = false,
                    invalidReason = "Account is currently ${ticket.status.lowercase()}."
                )
            }
            if (loginAtMillis > 0L && ticket.sessionRevokedBeforeMillis > loginAtMillis) {
                return@runCatching TicketSessionStatus(
                    isValid = false,
                    invalidReason = "Your session was revoked by the administrator. Please sign in again."
                )
            }
            TicketSessionStatus(
                isValid = true,
                mustChangePassword = ticket.mustChangePassword
            )
        }.getOrElse {
            TicketSessionStatus(isValid = true, mustChangePassword = session.mustChangePassword)
        }
    }

    suspend fun completeFirstLoginPasswordChange(
        session: ActiveWorkspaceSession,
        newPassword: String
    ): Result<ActiveWorkspaceSession> = runCatching {
        requireUserId()
        require(newPassword.length >= 6) { "New password must be at least 6 characters." }

        val ticketId = PasswordHasher.buildTicketId(session.businessCode, session.username)
        val ticketRef = db.collection("login_tickets").document(ticketId)
        val hashed = PasswordHasher.hashPassword(newPassword)
        val nowMillis = PakistanTime.nowMillis()

        val batch = db.batch()
        batch.update(
            ticketRef,
            mapOf(
                "passwordSalt" to hashed.saltBase64,
                "passwordHash" to hashed.hashBase64,
                "mustChangePassword" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val accountRef = if (session.role == AccountRole.BOOKER) {
            businessRef(session.businessId).collection("bookers").document(session.accountId)
        } else {
            businessRef(session.businessId).collection("users").document(session.accountId)
        }
        batch.update(
            accountRef,
            mapOf(
                "mustChangePassword" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        if (session.role != AccountRole.BOOKER) {
            val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            batch.set(
                businessRef(session.businessId).collection("audit_logs").document(logId),
                auditLogPayload(
                    businessId = session.businessId,
                    userId = session.accountId,
                    username = session.username,
                    role = session.role.key,
                    action = "PASSWORD_CHANGED",
                    shopUid = "",
                    previousValue = "Temporary Password",
                    newValue = "Custom Password Set",
                    timestampMillis = nowMillis,
                    description = "${session.fullName} changed temporary password on login."
                )
            )
        }

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ticketRef.path, auth)
            throw e
        }

        dao?.getCachedSession(ticketId)?.let { cached ->
            dao.upsertCachedSession(
                cached.copy(
                    encryptedPasswordSalt = LocalCryptoHelper.encrypt(hashed.saltBase64),
                    encryptedPasswordHash = LocalCryptoHelper.encrypt(hashed.hashBase64),
                    mustChangePassword = false,
                    lastSyncedMillis = nowMillis
                )
            )
        }

        session.copy(mustChangePassword = false)
    }

    suspend fun changeOwnPassword(
        session: ActiveWorkspaceSession,
        currentPassword: String,
        newPassword: String
    ): Result<ActiveWorkspaceSession> = runCatching {
        requireUserId()
        require(currentPassword.isNotBlank()) { "Current password is required." }
        require(newPassword.length >= 6) { "New password must be at least 6 characters." }
        require(newPassword != currentPassword) { "New password must be different from your current password." }

        val rateKey = "${session.businessCode}:${session.username}"
        rateLimiter.checkAllowed(SecurityRateLimiter.Operation.PASSWORD_CHANGE, rateKey)

        val ticketId = PasswordHasher.buildTicketId(session.businessCode, session.username)
        val ticketRef = db.collection("login_tickets").document(ticketId)
        val ticketSnap = ticketRef.get().await()
        if (!ticketSnap.exists()) {
            throw IllegalStateException("Account login record not found.")
        }
        val ticket = ticketSnap.toObject(LoginTicket::class.java)
            ?: throw IllegalStateException("Invalid login record.")

        if (!PasswordHasher.verifyPassword(currentPassword, ticket.passwordSalt, ticket.passwordHash)) {
            rateLimiter.recordFailure(SecurityRateLimiter.Operation.PASSWORD_CHANGE, rateKey)
            throw IllegalArgumentException("Current password is incorrect.")
        }
        rateLimiter.recordSuccess(SecurityRateLimiter.Operation.PASSWORD_CHANGE, rateKey)

        val hashed = PasswordHasher.hashPassword(newPassword)
        val nowMillis = PakistanTime.nowMillis()

        val batch = db.batch()
        batch.update(
            ticketRef,
            mapOf(
                "passwordSalt" to hashed.saltBase64,
                "passwordHash" to hashed.hashBase64,
                "mustChangePassword" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val accountRef = if (session.role == AccountRole.BOOKER) {
            businessRef(session.businessId).collection("bookers").document(session.accountId)
        } else {
            businessRef(session.businessId).collection("users").document(session.accountId)
        }
        batch.update(
            accountRef,
            mapOf(
                "mustChangePassword" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        if (session.role != AccountRole.BOOKER) {
            val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            batch.set(
                businessRef(session.businessId).collection("audit_logs").document(logId),
                auditLogPayload(
                    businessId = session.businessId,
                    userId = session.accountId,
                    username = session.username,
                    role = session.role.key,
                    action = "PASSWORD_CHANGED",
                    shopUid = "",
                    previousValue = "Previous Password Verified",
                    newValue = "New Password Set",
                    timestampMillis = nowMillis,
                    description = "${session.fullName} changed their account password."
                )
            )
        }

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ticketRef.path, auth)
            throw e
        }

        dao?.getCachedSession(ticketId)?.let { cached ->
            dao.upsertCachedSession(
                cached.copy(
                    encryptedPasswordSalt = LocalCryptoHelper.encrypt(hashed.saltBase64),
                    encryptedPasswordHash = LocalCryptoHelper.encrypt(hashed.hashBase64),
                    mustChangePassword = false,
                    lastSyncedMillis = nowMillis
                )
            )
        }

        session.copy(mustChangePassword = false)
    }

    suspend fun resetOwnAdminPassword(
        businessCode: String,
        username: String,
        registeredMobile: String,
        newPassword: String
    ): Result<Unit> = runCatching {
        val googleUid = requireUserId()
        require(newPassword.length >= 6) { "New password must be at least 6 characters." }
        val cleanCode = businessCode.trim().uppercase()
        val cleanUser = username.trim()
        val rateKey = "$cleanCode:$cleanUser"
        rateLimiter.checkAllowed(SecurityRateLimiter.Operation.PASSWORD_RESET, rateKey)

        val ticketId = PasswordHasher.buildTicketId(cleanCode, cleanUser)
        val ticketRef = db.collection("login_tickets").document(ticketId)
        val ticketSnap = ticketRef.get().await()
        if (!ticketSnap.exists()) {
            rateLimiter.recordFailure(SecurityRateLimiter.Operation.PASSWORD_RESET, rateKey)
            throw IllegalArgumentException("Admin account not found for Business Code.")
        }
        val ticket = ticketSnap.toObject(LoginTicket::class.java)
            ?: throw IllegalStateException("Invalid login ticket.")
        if (ticket.role != AccountRole.SUPER_ADMIN.key) {
            throw IllegalStateException("Staff & Bookers must request a password reset from their Super Admin.")
        }
        if (ticket.ownerUid != googleUid) {
            rateLimiter.recordFailure(SecurityRateLimiter.Operation.PASSWORD_RESET, rateKey)
            throw SecurityException("Only the Google account that owns this workspace can reset the Super Admin password.")
        }
        val bizSnap = businessRef(ticket.businessId).get().await()
        val storedMobile = bizSnap.getString("ownerMobile").orEmpty()
        if (storedMobile != registeredMobile.trim()) {
            rateLimiter.recordFailure(SecurityRateLimiter.Operation.PASSWORD_RESET, rateKey)
            throw IllegalArgumentException("Registered mobile number does not match.")
        }
        rateLimiter.recordSuccess(SecurityRateLimiter.Operation.PASSWORD_RESET, rateKey)
        val hashed = PasswordHasher.hashPassword(newPassword)
        ticketRef.update(
            mapOf(
                "passwordSalt" to hashed.saltBase64,
                "passwordHash" to hashed.hashBase64,
                "mustChangePassword" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        dao?.getCachedSession(ticketId)?.let { cached ->
            dao.upsertCachedSession(
                cached.copy(
                    encryptedPasswordSalt = LocalCryptoHelper.encrypt(hashed.saltBase64),
                    encryptedPasswordHash = LocalCryptoHelper.encrypt(hashed.hashBase64),
                    mustChangePassword = false,
                    lastSyncedMillis = PakistanTime.nowMillis()
                )
            )
        }
    }

    // --- Booker Management (Phase 5) ---

    suspend fun createBooker(
        session: ActiveWorkspaceSession,
        businessCode: String,
        businessName: String,
        fullName: String,
        username: String,
        temporaryPassword: String,
        mobileNumber: String,
        address: String,
        notes: String,
        assignedShopIds: List<String>
    ): Result<GeneratedCredentials> = runCatching {
        requireUserId()
        val cleanName = fullName.trim()
        val cleanUser = username.trim()
        val cleanMobile = mobileNumber.trim()
        require(cleanName.length >= 2) { "Booker full name is required." }
        require(cleanUser.length >= 2) { "Username must be at least 2 characters." }
        require(temporaryPassword.length >= 6) { "Password must be at least 6 characters." }
        require(cleanMobile.length >= 7) { "Valid mobile number is required." }

        val ticketId = PasswordHasher.buildTicketId(businessCode, cleanUser)
        val ticketRef = db.collection("login_tickets").document(ticketId)
        val existingTicket = ticketRef.get().await()
        if (existingTicket.exists()) {
            throw IllegalArgumentException("Username '$cleanUser' is already taken in this business.")
        }

        val bizSnap = businessRef(session.businessId).get().await()
        val ownerUid = bizSnap.getString("ownerUid").orEmpty()

        val bookerId = "bkr_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val bookerCode = PasswordHasher.generateCode("BKR")
        val hashed = PasswordHasher.hashPassword(temporaryPassword)
        val nowMillis = PakistanTime.nowMillis()

        val batch = db.batch()
        val bookerRef = businessRef(session.businessId).collection("bookers").document(bookerId)
        batch.set(
            bookerRef,
            mapOf(
                "businessId" to session.businessId,
                "bookerCode" to bookerCode,
                "fullName" to cleanName,
                "username" to cleanUser,
                "mobileNumber" to cleanMobile,
                "address" to address.trim(),
                "notes" to notes.trim(),
                "status" to "ACTIVE",
                "mustChangePassword" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        batch.set(
            ticketRef,
            mapOf(
                "businessId" to session.businessId,
                "businessName" to businessName,
                "businessCode" to businessCode,
                "accountId" to bookerId,
                "role" to AccountRole.BOOKER.key,
                "username" to cleanUser,
                "fullName" to cleanName,
                "passwordSalt" to hashed.saltBase64,
                "passwordHash" to hashed.hashBase64,
                "mustChangePassword" to false,
                "status" to "ACTIVE",
                "boundGoogleUid" to "",
                "ownerUid" to ownerUid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        // Assign selected existing shops to this Booker
        assignedShopIds.forEach { shopId ->
            val shopRef = businessRef(session.businessId).collection("shops").document(shopId)
            batch.update(
                shopRef,
                mapOf(
                    "bookerId" to bookerId,
                    "bookerName" to cleanName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "BOOKER_CREATED",
                shopUid = "",
                previousValue = "",
                newValue = "$cleanName ($bookerCode)",
                timestampMillis = nowMillis,
                description = "Created Booker $cleanName ($bookerCode) with username $cleanUser"
            )
        )

        val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("notifications").document(notifId),
            notificationPayload(
                businessId = session.businessId,
                title = "New Booker Added",
                message = "$cleanName ($bookerCode) added by ${session.fullName}.",
                category = "BOOKER",
                targetRole = "ADMIN_STAFF",
                targetBookerId = "",
                shopUid = ""
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, bookerRef.path, auth)
            throw e
        }

        GeneratedCredentials(
            fullName = cleanName,
            accountCode = bookerCode,
            roleLabel = "Booker (Read-Only)",
            businessCode = businessCode,
            username = cleanUser,
            temporaryPassword = temporaryPassword
        )
    }

    suspend fun updateBookerProfile(
        session: ActiveWorkspaceSession,
        booker: Booker,
        newFullName: String,
        newMobile: String,
        newAddress: String,
        newNotes: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val bookerRef = businessRef(session.businessId).collection("bookers").document(booker.id)
        val ticketId = PasswordHasher.buildTicketId(session.businessCode, booker.username)
        val ticketRef = db.collection("login_tickets").document(ticketId)

        val batch = db.batch()
        batch.update(
            bookerRef,
            mapOf(
                "fullName" to newFullName.trim(),
                "mobileNumber" to newMobile.trim(),
                "address" to newAddress.trim(),
                "notes" to newNotes.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            ticketRef,
            mapOf(
                "fullName" to newFullName.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "BOOKER_UPDATED",
                shopUid = "",
                previousValue = booker.fullName,
                newValue = newFullName.trim(),
                timestampMillis = PakistanTime.nowMillis(),
                description = "Updated Booker profile for ${booker.bookerCode}"
            )
        )
        batch.commit().await()
    }

    suspend fun toggleBookerStatus(
        session: ActiveWorkspaceSession,
        booker: Booker
    ): Result<Unit> = runCatching {
        requireUserId()
        val nextStatus = if (booker.status == "ACTIVE") "INACTIVE" else "ACTIVE"
        val bookerRef = businessRef(session.businessId).collection("bookers").document(booker.id)
        val ticketRef = db.collection("login_tickets")
            .document(PasswordHasher.buildTicketId(session.businessCode, booker.username))

        val batch = db.batch()
        batch.update(bookerRef, mapOf("status" to nextStatus, "updatedAt" to FieldValue.serverTimestamp()))
        batch.update(ticketRef, mapOf("status" to nextStatus, "updatedAt" to FieldValue.serverTimestamp()))
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "BOOKER_STATUS_CHANGED",
                shopUid = "",
                previousValue = booker.status,
                newValue = nextStatus,
                timestampMillis = PakistanTime.nowMillis(),
                description = "Changed Booker ${booker.fullName} status to $nextStatus"
            )
        )
        batch.commit().await()
    }

    suspend fun resetAccountPassword(
        session: ActiveWorkspaceSession,
        accountId: String,
        accountCode: String,
        fullName: String,
        username: String,
        role: AccountRole,
        newTempPassword: String
    ): Result<GeneratedCredentials> = runCatching {
        requireUserId()
        val hashed = PasswordHasher.hashPassword(newTempPassword)
        val ticketId = PasswordHasher.buildTicketId(session.businessCode, username)
        val ticketRef = db.collection("login_tickets").document(ticketId)
        val accountRef = if (role == AccountRole.BOOKER) {
            businessRef(session.businessId).collection("bookers").document(accountId)
        } else {
            businessRef(session.businessId).collection("users").document(accountId)
        }

        val batch = db.batch()
        batch.update(
            ticketRef,
            mapOf(
                "passwordSalt" to hashed.saltBase64,
                "passwordHash" to hashed.hashBase64,
                "mustChangePassword" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            accountRef,
            mapOf(
                "mustChangePassword" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "PASSWORD_RESET",
                shopUid = "",
                previousValue = "",
                newValue = "New login password issued for $username",
                timestampMillis = PakistanTime.nowMillis(),
                description = "Super Admin reset password for $fullName ($username)"
            )
        )
        batch.commit().await()
        dao?.getCachedSession(ticketId)?.let { cached ->
            dao.upsertCachedSession(
                cached.copy(
                    encryptedPasswordSalt = LocalCryptoHelper.encrypt(hashed.saltBase64),
                    encryptedPasswordHash = LocalCryptoHelper.encrypt(hashed.hashBase64),
                    mustChangePassword = false,
                    lastSyncedMillis = PakistanTime.nowMillis()
                )
            )
        }

        GeneratedCredentials(
            fullName = fullName,
            accountCode = accountCode,
            roleLabel = role.label,
            businessCode = session.businessCode,
            username = username,
            temporaryPassword = newTempPassword
        )
    }

    // --- Staff / User Management (Phase 6) ---

    suspend fun createStaffUser(
        session: ActiveWorkspaceSession,
        fullName: String,
        username: String,
        temporaryPassword: String,
        mobileNumber: String,
        permissionTemplate: StaffPermission
    ): Result<GeneratedCredentials> = runCatching {
        requireUserId()
        val cleanName = fullName.trim()
        val cleanUser = username.trim()
        val cleanMobile = mobileNumber.trim()
        require(cleanName.length >= 2) { "Full name is required." }
        require(cleanUser.length >= 2) { "Username must be at least 2 characters." }
        require(temporaryPassword.length >= 6) { "Password must be at least 6 characters." }
        require(cleanMobile.length >= 7) { "Valid mobile number is required." }

        val ticketId = PasswordHasher.buildTicketId(session.businessCode, cleanUser)
        val ticketRef = db.collection("login_tickets").document(ticketId)
        if (ticketRef.get().await().exists()) {
            throw IllegalArgumentException("Username '$cleanUser' already exists in this workspace.")
        }

        val bizSnap = businessRef(session.businessId).get().await()
        val ownerUid = bizSnap.getString("ownerUid").orEmpty()

        val userId = "usr_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val userCode = PasswordHasher.generateCode("USR")
        val hashed = PasswordHasher.hashPassword(temporaryPassword)

        val batch = db.batch()
        batch.set(
            businessRef(session.businessId).collection("users").document(userId),
            mapOf(
                "businessId" to session.businessId,
                "userCode" to userCode,
                "fullName" to cleanName,
                "username" to cleanUser,
                "mobileNumber" to cleanMobile,
                "email" to "",
                "role" to AccountRole.STAFF.key,
                "status" to "ACTIVE",
                "mustChangePassword" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            businessRef(session.businessId).collection("permissions").document(userId),
            permissionPayload(session.businessId, userId, permissionTemplate, isCreate = true)
        )
        batch.set(
            ticketRef,
            mapOf(
                "businessId" to session.businessId,
                "businessName" to session.businessName,
                "businessCode" to session.businessCode,
                "accountId" to userId,
                "role" to AccountRole.STAFF.key,
                "username" to cleanUser,
                "fullName" to cleanName,
                "passwordSalt" to hashed.saltBase64,
                "passwordHash" to hashed.hashBase64,
                "mustChangePassword" to false,
                "status" to "ACTIVE",
                "boundGoogleUid" to "",
                "ownerUid" to ownerUid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "STAFF_CREATED",
                shopUid = "",
                previousValue = "",
                newValue = "$cleanName ($userCode)",
                timestampMillis = PakistanTime.nowMillis(),
                description = "Created Staff account $cleanName ($userCode)"
            )
        )
        val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("notifications").document(notifId),
            notificationPayload(
                businessId = session.businessId,
                title = "New Staff Account Created",
                message = "$cleanName ($userCode) added as Authorized Staff.",
                category = "STAFF",
                targetRole = "ADMIN_STAFF",
                targetBookerId = "",
                shopUid = ""
            )
        )

        batch.commit().await()

        GeneratedCredentials(
            fullName = cleanName,
            accountCode = userCode,
            roleLabel = "Authorized Staff",
            businessCode = session.businessCode,
            username = cleanUser,
            temporaryPassword = temporaryPassword
        )
    }

    suspend fun updateStaffPermissions(
        session: ActiveWorkspaceSession,
        user: BusinessUser,
        permissions: StaffPermission
    ): Result<Unit> = runCatching {
        requireUserId()
        val permRef = businessRef(session.businessId).collection("permissions").document(user.id)
        val batch = db.batch()
        batch.update(permRef, permissionPayload(session.businessId, user.id, permissions, isCreate = false))
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "PERMISSIONS_CHANGED",
                shopUid = "",
                previousValue = "Updated RBAC Matrix",
                newValue = user.username,
                timestampMillis = PakistanTime.nowMillis(),
                description = "Updated permissions for Staff ${user.fullName} (${user.userCode})"
            )
        )
        val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("notifications").document(notifId),
            notificationPayload(
                businessId = session.businessId,
                title = "Permissions Updated",
                message = "Account permissions updated for ${user.fullName}.",
                category = "PERMISSIONS",
                targetRole = "ADMIN_STAFF",
                targetBookerId = "",
                shopUid = ""
            )
        )
        batch.commit().await()
    }

    suspend fun toggleStaffStatus(
        session: ActiveWorkspaceSession,
        user: BusinessUser
    ): Result<Unit> = runCatching {
        requireUserId()
        require(user.role != AccountRole.SUPER_ADMIN.key) { "Cannot suspend Super Admin." }
        val nextStatus = if (user.status == "ACTIVE") "SUSPENDED" else "ACTIVE"
        val userRef = businessRef(session.businessId).collection("users").document(user.id)
        val ticketRef = db.collection("login_tickets")
            .document(PasswordHasher.buildTicketId(session.businessCode, user.username))

        val batch = db.batch()
        batch.update(userRef, mapOf("status" to nextStatus, "updatedAt" to FieldValue.serverTimestamp()))
        batch.update(ticketRef, mapOf("status" to nextStatus, "updatedAt" to FieldValue.serverTimestamp()))
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = if (nextStatus == "SUSPENDED") "ACCOUNT_SUSPENDED" else "ACCOUNT_ACTIVATED",
                shopUid = "",
                previousValue = user.status,
                newValue = nextStatus,
                timestampMillis = PakistanTime.nowMillis(),
                description = "Staff ${user.fullName} status changed to $nextStatus"
            )
        )
        batch.commit().await()
    }

    // --- Credit Shops Management (Phase 7) ---

    suspend fun createShop(
        session: ActiveWorkspaceSession,
        shopUid: String,
        shopName: String,
        booker: Booker,
        ownerName: String,
        mobileNumber: String,
        address: String,
        city: String,
        openingCreditPaisa: Long,
        openingDateMillis: Long,
        notes: String
    ): Result<Shop> = runCatching {
        requireUserId()
        val cleanUid = shopUid.trim().uppercase()
        val cleanName = shopName.trim()
        require(cleanUid.length >= 3) { "Shop UID must be at least 3 characters." }
        require(cleanName.length >= 2) { "Shop name is required." }
        require(ownerName.trim().length >= 2) { "Owner name is required." }
        require(mobileNumber.trim().length >= 7) { "Valid mobile number is required." }
        require(address.trim().isNotEmpty()) { "Shop address is required." }
        require(city.trim().isNotEmpty()) { "City is required." }
        require(openingCreditPaisa >= 0L) { "Opening credit cannot be negative." }

        // Prevent duplicate Shop UID
        val dupCheck = businessRef(session.businessId).collection("shops")
            .whereEqualTo("shopUid", cleanUid)
            .get()
            .await()
        if (!dupCheck.isEmpty) {
            throw IllegalArgumentException("Shop UID '$cleanUid' already exists. Duplicate Shop UIDs are not allowed.")
        }

        val shopId = "shp_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val shopRef = businessRef(session.businessId).collection("shops").document(shopId)
        val batch = db.batch()

        batch.set(
            shopRef,
            mapOf(
                "businessId" to session.businessId,
                "shopUid" to cleanUid,
                "shopName" to cleanName,
                "bookerId" to booker.id,
                "bookerName" to booker.fullName,
                "ownerName" to ownerName.trim(),
                "mobileNumber" to mobileNumber.trim(),
                "address" to address.trim(),
                "city" to city.trim(),
                "openingCreditPaisa" to openingCreditPaisa,
                "openingDateMillis" to openingDateMillis,
                "notes" to notes.trim(),
                "isArchived" to false,
                "createdByAccountId" to session.accountId,
                "createdByName" to session.fullName,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        if (openingCreditPaisa > 0L) {
            val crId = "cr_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val txCode = PasswordHasher.generateCode("CR")
            batch.set(
                businessRef(session.businessId).collection("credit_transactions").document(crId),
                mapOf(
                    "businessId" to session.businessId,
                    "txCode" to txCode,
                    "shopId" to shopId,
                    "shopUid" to cleanUid,
                    "shopName" to cleanName,
                    "bookerId" to booker.id,
                    "bookerName" to booker.fullName,
                    "amountPaisa" to openingCreditPaisa,
                    "transactionKind" to "CREDIT",
                    "relatedTxId" to "",
                    "reason" to "",
                    "invoiceNumber" to "OPENING",
                    "description" to "Opening Credit Balance",
                    "attachmentUri" to "",
                    "txDateMillis" to openingDateMillis,
                    "txDateFormatted" to PakistanTime.formatDate(openingDateMillis),
                    "txTimeFormatted" to PakistanTime.formatTime(openingDateMillis),
                    "createdByAccountId" to session.accountId,
                    "createdByName" to session.fullName,
                    "createdByRole" to session.role.label,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "SHOP_CREATED",
                shopUid = cleanUid,
                previousValue = "",
                newValue = "$cleanName ($cleanUid) • Booker: ${booker.fullName}",
                timestampMillis = PakistanTime.nowMillis(),
                description = "Created shop $cleanName ($cleanUid) assigned to ${booker.fullName}"
            )
        )

        val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("notifications").document(notifId),
            notificationPayload(
                businessId = session.businessId,
                title = "New Shop Created",
                message = "$cleanName ($cleanUid) assigned to ${booker.fullName}.",
                category = "SHOP",
                targetRole = "BOOKER",
                targetBookerId = booker.id,
                shopUid = cleanUid
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, shopRef.path, auth)
            throw e
        }

        Shop(
            id = shopId,
            businessId = session.businessId,
            shopUid = cleanUid,
            shopName = cleanName,
            bookerId = booker.id,
            bookerName = booker.fullName,
            ownerName = ownerName.trim(),
            mobileNumber = mobileNumber.trim(),
            address = address.trim(),
            city = city.trim(),
            openingCreditPaisa = openingCreditPaisa,
            openingDateMillis = openingDateMillis,
            notes = notes.trim(),
            isArchived = false,
            createdByAccountId = session.accountId,
            createdByName = session.fullName
        )
    }

    suspend fun updateShop(
        session: ActiveWorkspaceSession,
        existingShop: Shop,
        shopName: String,
        booker: Booker,
        ownerName: String,
        mobileNumber: String,
        address: String,
        city: String,
        notes: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val shopRef = businessRef(session.businessId).collection("shops").document(existingShop.id)
        val batch = db.batch()
        batch.update(
            shopRef,
            mapOf(
                "shopName" to shopName.trim(),
                "bookerId" to booker.id,
                "bookerName" to booker.fullName,
                "ownerName" to ownerName.trim(),
                "mobileNumber" to mobileNumber.trim(),
                "address" to address.trim(),
                "city" to city.trim(),
                "notes" to notes.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "SHOP_EDITED",
                shopUid = existingShop.shopUid,
                previousValue = "${existingShop.shopName} (${existingShop.bookerName})",
                newValue = "${shopName.trim()} (${booker.fullName})",
                timestampMillis = PakistanTime.nowMillis(),
                description = "Updated shop ${existingShop.shopUid} details"
            )
        )
        val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("notifications").document(notifId),
            notificationPayload(
                businessId = session.businessId,
                title = "Shop Information Updated",
                message = "${shopName.trim()} (${existingShop.shopUid}) profile was updated.",
                category = "SHOP",
                targetRole = "BOOKER",
                targetBookerId = booker.id,
                shopUid = existingShop.shopUid
            )
        )
        batch.commit().await()
    }

    suspend fun toggleArchiveShop(
        session: ActiveWorkspaceSession,
        shop: Shop
    ): Result<Unit> = runCatching {
        requireUserId()
        val nextArchived = !shop.isArchived
        val shopRef = businessRef(session.businessId).collection("shops").document(shop.id)
        val batch = db.batch()
        batch.update(
            shopRef,
            mapOf(
                "isArchived" to nextArchived,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = if (nextArchived) "SHOP_ARCHIVED" else "SHOP_RESTORED",
                shopUid = shop.shopUid,
                previousValue = if (shop.isArchived) "Archived" else "Active",
                newValue = if (nextArchived) "Archived" else "Active",
                timestampMillis = PakistanTime.nowMillis(),
                description = "${if (nextArchived) "Archived" else "Restored"} shop ${shop.shopName} (${shop.shopUid})"
            )
        )
        batch.commit().await()
    }

    // --- Credit & Recovery Transactions (Phases 8, 9, 14, 16) ---

    suspend fun addCreditTransaction(
        session: ActiveWorkspaceSession,
        shop: Shop,
        amountPaisa: Long,
        txDateMillis: Long,
        invoiceNumber: String,
        description: String,
        attachmentUri: String,
        dueDateMillis: Long = 0L,
        idempotencyToken: String = "",
        deviceId: String = ""
    ): Result<CreditTransaction> = runCatching {
        requireUserId()
        require(amountPaisa > 0L) { "Credit amount must be greater than Rs. 0." }
        require(!shop.isArchived) { "Cannot add credit to an archived shop." }

        val entryNowMillis = PakistanTime.nowMillis()
        if (PakistanTime.isFuturePakistanDay(txDateMillis, entryNowMillis)) {
            throw IllegalArgumentException("Future credit dates are not allowed.")
        }

        val safeToken = idempotencyToken.replace(Regex("[^a-zA-Z0-9_-]"), "").take(24)
        val txId = if (safeToken.isNotEmpty()) {
            "cr_$safeToken"
        } else {
            "cr_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        }
        val txRef = businessRef(session.businessId).collection("credit_transactions").document(txId)
        if (safeToken.isNotEmpty() && txRef.get().await().exists()) {
            throw IllegalStateException("Duplicate submission prevented: this credit entry was already recorded.")
        }

        val txCode = PasswordHasher.generateCode("CR")
        val dateFormatted = PakistanTime.formatDate(txDateMillis)
        val timeFormatted = PakistanTime.formatTime(txDateMillis)
        val dueFormatted = if (dueDateMillis > 0L) PakistanTime.formatDate(dueDateMillis) else ""
        val entryDateFormatted = PakistanTime.formatDate(entryNowMillis)
        val entryTimeFormatted = PakistanTime.formatTime(entryNowMillis)

        val batch = db.batch()
        batch.set(
            txRef,
            mapOf(
                "businessId" to session.businessId,
                "txCode" to txCode,
                "shopId" to shop.id,
                "shopUid" to shop.shopUid,
                "shopName" to shop.shopName,
                "bookerId" to shop.bookerId,
                "bookerName" to shop.bookerName,
                "amountPaisa" to amountPaisa,
                "transactionKind" to "CREDIT",
                "relatedTxId" to "",
                "reason" to "",
                "invoiceNumber" to invoiceNumber.trim(),
                "description" to description.trim().ifEmpty { "Credit entry" },
                "attachmentUri" to attachmentUri.trim(),
                "txDateMillis" to txDateMillis,
                "txDateFormatted" to dateFormatted,
                "txTimeFormatted" to timeFormatted,
                "createdByAccountId" to session.accountId,
                "createdByName" to session.fullName,
                "createdByRole" to session.role.label,
                "dueDateMillis" to dueDateMillis,
                "dueDateFormatted" to dueFormatted,
                "entryTimestampMillis" to entryNowMillis,
                "entryDateFormatted" to entryDateFormatted,
                "entryTimeFormatted" to entryTimeFormatted,
                "syncStatus" to "SYNCED",
                "idempotencyToken" to safeToken,
                "deviceId" to deviceId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val amountStr = CurrencyFormatter.formatPkr(amountPaisa)
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "CREDIT_ADDED",
                shopUid = shop.shopUid,
                previousValue = "",
                newValue = "$amountStr ($txCode)",
                timestampMillis = entryNowMillis,
                description = "Added credit $amountStr to ${shop.shopName} (${shop.shopUid})"
            )
        )

        val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("notifications").document(notifId),
            notificationPayload(
                businessId = session.businessId,
                title = "New Credit Added",
                message = "$amountStr credit recorded for ${shop.shopName} (${shop.shopUid}).",
                category = "CREDIT",
                targetRole = "BOOKER",
                targetBookerId = shop.bookerId,
                shopUid = shop.shopUid
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, txRef.path, auth)
            throw e
        }

        CreditTransaction(
            id = txId,
            businessId = session.businessId,
            txCode = txCode,
            shopId = shop.id,
            shopUid = shop.shopUid,
            shopName = shop.shopName,
            bookerId = shop.bookerId,
            bookerName = shop.bookerName,
            amountPaisa = amountPaisa,
            transactionKind = "CREDIT",
            invoiceNumber = invoiceNumber.trim(),
            description = description.trim().ifEmpty { "Credit entry" },
            attachmentUri = attachmentUri.trim(),
            txDateMillis = txDateMillis,
            txDateFormatted = dateFormatted,
            txTimeFormatted = timeFormatted,
            createdByAccountId = session.accountId,
            createdByName = session.fullName,
            createdByRole = session.role.label,
            dueDateMillis = dueDateMillis,
            dueDateFormatted = dueFormatted,
            entryTimestampMillis = entryNowMillis,
            entryDateFormatted = entryDateFormatted,
            entryTimeFormatted = entryTimeFormatted,
            syncStatus = "SYNCED",
            idempotencyToken = safeToken,
            deviceId = deviceId
        )
    }

    suspend fun reverseCreditTransaction(
        session: ActiveWorkspaceSession,
        original: CreditTransaction,
        reason: String
    ): Result<Unit> = runCatching {
        requireUserId()
        require(original.transactionKind == "CREDIT") { "Only original credit entries can be reversed." }
        require(reason.trim().length >= 3) { "Please provide a clear reason for this credit correction." }

        val revId = "crev_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val txCode = PasswordHasher.generateCode("CRV")
        val nowMillis = PakistanTime.nowMillis()

        val batch = db.batch()
        batch.set(
            businessRef(session.businessId).collection("credit_transactions").document(revId),
            mapOf(
                "businessId" to session.businessId,
                "txCode" to txCode,
                "shopId" to original.shopId,
                "shopUid" to original.shopUid,
                "shopName" to original.shopName,
                "bookerId" to original.bookerId,
                "bookerName" to original.bookerName,
                "amountPaisa" to original.amountPaisa,
                "transactionKind" to "CREDIT_REVERSAL",
                "relatedTxId" to original.id,
                "reason" to reason.trim(),
                "invoiceNumber" to original.invoiceNumber,
                "description" to "Reversal of ${original.txCode}: ${reason.trim()}",
                "attachmentUri" to "",
                "txDateMillis" to nowMillis,
                "txDateFormatted" to PakistanTime.formatDate(nowMillis),
                "txTimeFormatted" to PakistanTime.formatTime(nowMillis),
                "createdByAccountId" to session.accountId,
                "createdByName" to session.fullName,
                "createdByRole" to session.role.label,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val amountStr = CurrencyFormatter.formatPkr(original.amountPaisa)
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "CREDIT_REVERSED",
                shopUid = original.shopUid,
                previousValue = "${original.txCode}: $amountStr",
                newValue = "$txCode (Reversed)",
                timestampMillis = nowMillis,
                description = "Reversed credit ${original.txCode} ($amountStr) — Reason: ${reason.trim()}"
            )
        )
        batch.commit().await()
    }

    suspend fun recordRecoveryTransaction(
        session: ActiveWorkspaceSession,
        shop: Shop,
        amountPaisa: Long,
        txDateMillis: Long,
        paymentMethod: PaymentMethod,
        referenceNumber: String,
        notes: String,
        allowOverRecovery: Boolean,
        clientRequestToken: String,
        deviceId: String = "",
        expectedDeviceTimestampMillis: Long = 0L
    ): Result<RecoveryTransaction> = runCatching {
        requireUserId()
        require(amountPaisa > 0L) { "Recovery amount must be greater than Rs. 0." }
        val nowMillis = PakistanTime.nowMillis()
        if (PakistanTime.isFuturePakistanDay(txDateMillis, nowMillis)) {
            throw IllegalArgumentException("Future recovery transaction dates are not permitted.")
        }
        val staffReferenceNow = if (deviceId.isNotBlank() && expectedDeviceTimestampMillis > 0L) {
            expectedDeviceTimestampMillis
        } else {
            nowMillis
        }
        if (session.role == AccountRole.STAFF &&
            !PakistanTime.isSamePakistanDay(txDateMillis, staffReferenceNow)
        ) {
            throw IllegalArgumentException(
                "Staff accounts cannot backdate recoveries and must use the current Pakistan calendar date (${PakistanTime.formatDate(staffReferenceNow)})."
            )
        }
        val isBackdated = session.role == AccountRole.SUPER_ADMIN &&
            PakistanTime.isBackdatedPakistanDay(txDateMillis, nowMillis)

        // Server-side/live balance validation to prevent over-recovery & duplicate submissions
        val shopCredits = businessRef(session.businessId).collection("credit_transactions")
            .whereEqualTo("shopId", shop.id)
            .get().await()
            .toObjects(CreditTransaction::class.java)
        val shopRecoveries = businessRef(session.businessId).collection("recovery_transactions")
            .whereEqualTo("shopId", shop.id)
            .get().await()
            .toObjects(RecoveryTransaction::class.java)

        val currentOutstanding = FinancialEngine.netCreditPaisa(shopCredits) -
            FinancialEngine.netRecoveryPaisa(shopRecoveries)

        if (!allowOverRecovery && amountPaisa > currentOutstanding) {
            throw IllegalArgumentException(
                "Recovery (${CurrencyFormatter.formatPkr(amountPaisa)}) exceeds current outstanding balance (${CurrencyFormatter.formatPkr(currentOutstanding)})."
            )
        }

        // Idempotent document ID using clientRequestToken prevents duplicate submissions
        val safeToken = clientRequestToken.replace(Regex("[^a-zA-Z0-9_-]"), "").take(24)
            .ifEmpty { UUID.randomUUID().toString().replace("-", "").take(14) }
        val txId = "rc_$safeToken"
        val txRef = businessRef(session.businessId).collection("recovery_transactions").document(txId)
        if (txRef.get().await().exists()) {
            throw IllegalStateException("Duplicate submission prevented: this recovery payment was already recorded.")
        }

        val txCode = PasswordHasher.generateCode("RC")
        val dateFormatted = PakistanTime.formatDate(txDateMillis)
        val timeFormatted = PakistanTime.formatTime(txDateMillis)

        val batch = db.batch()
        batch.set(
            txRef,
            mapOf(
                "businessId" to session.businessId,
                "txCode" to txCode,
                "shopId" to shop.id,
                "shopUid" to shop.shopUid,
                "shopName" to shop.shopName,
                "bookerId" to shop.bookerId,
                "bookerName" to shop.bookerName,
                "amountPaisa" to amountPaisa,
                "transactionKind" to "RECOVERY",
                "relatedTxId" to "",
                "reason" to "",
                "paymentMethod" to paymentMethod.label,
                "referenceNumber" to referenceNumber.trim(),
                "notes" to notes.trim(),
                "txDateMillis" to txDateMillis,
                "txDateFormatted" to dateFormatted,
                "txTimeFormatted" to timeFormatted,
                "createdByAccountId" to session.accountId,
                "createdByName" to session.fullName,
                "createdByRole" to session.role.label,
                "correctionGroupId" to "",
                "correctedFromTxId" to "",
                "originalAmountPaisa" to amountPaisa,
                "originalTxDateMillis" to txDateMillis,
                "isBackdated" to isBackdated,
                "reversedAtMillis" to 0L,
                "reversedByAccountId" to "",
                "reversedByName" to "",
                "syncStatus" to "SYNCED",
                "idempotencyToken" to safeToken,
                "deviceId" to deviceId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val amountStr = CurrencyFormatter.formatPkr(amountPaisa)
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "RECOVERY_RECORDED",
                shopUid = shop.shopUid,
                previousValue = "Outstanding: ${CurrencyFormatter.formatPkr(currentOutstanding)}",
                newValue = "Recovered: $amountStr via ${paymentMethod.label} ($txCode)${if (isBackdated) " [Backdated: $dateFormatted]" else ""}",
                timestampMillis = nowMillis,
                description = "Recorded recovery $amountStr from ${shop.shopName} (${shop.shopUid}) via ${paymentMethod.label}"
            )
        )

        val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("notifications").document(notifId),
            notificationPayload(
                businessId = session.businessId,
                title = "Recovery Payment Recorded",
                message = "$amountStr collected from ${shop.shopName} (${shop.shopUid}) via ${paymentMethod.label}.",
                category = "RECOVERY",
                targetRole = "BOOKER",
                targetBookerId = shop.bookerId,
                shopUid = shop.shopUid
            )
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, txRef.path, auth)
            throw e
        }

        RecoveryTransaction(
            id = txId,
            businessId = session.businessId,
            txCode = txCode,
            shopId = shop.id,
            shopUid = shop.shopUid,
            shopName = shop.shopName,
            bookerId = shop.bookerId,
            bookerName = shop.bookerName,
            amountPaisa = amountPaisa,
            transactionKind = "RECOVERY",
            paymentMethod = paymentMethod.label,
            referenceNumber = referenceNumber.trim(),
            notes = notes.trim(),
            txDateMillis = txDateMillis,
            txDateFormatted = dateFormatted,
            txTimeFormatted = timeFormatted,
            createdByAccountId = session.accountId,
            createdByName = session.fullName,
            createdByRole = session.role.label,
            originalAmountPaisa = amountPaisa,
            originalTxDateMillis = txDateMillis,
            isBackdated = isBackdated,
            syncStatus = "SYNCED",
            idempotencyToken = safeToken,
            deviceId = deviceId
        )
    }

    suspend fun reverseRecoveryTransaction(
        session: ActiveWorkspaceSession,
        original: RecoveryTransaction,
        reason: String
    ): Result<Unit> = runCatching {
        requireUserId()
        require(session.role == AccountRole.SUPER_ADMIN) {
            "Only Business Super Admin can reverse recovery transactions."
        }
        require(original.transactionKind == "RECOVERY") { "Only original recovery entries can be reversed." }
        require(reason.trim().length >= 3) { "Please provide a clear reason for this reversal." }

        val existingReversals = businessRef(session.businessId).collection("recovery_transactions")
            .whereEqualTo("relatedTxId", original.id)
            .whereEqualTo("transactionKind", "RECOVERY_REVERSAL")
            .get().await()
        if (!existingReversals.isEmpty) {
            throw IllegalStateException("This recovery payment (${original.txCode}) has already been reversed.")
        }

        val revId = "rrev_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val txCode = PasswordHasher.generateCode("RCV")
        val nowMillis = PakistanTime.nowMillis()

        val batch = db.batch()
        batch.set(
            businessRef(session.businessId).collection("recovery_transactions").document(revId),
            mapOf(
                "businessId" to session.businessId,
                "txCode" to txCode,
                "shopId" to original.shopId,
                "shopUid" to original.shopUid,
                "shopName" to original.shopName,
                "bookerId" to original.bookerId,
                "bookerName" to original.bookerName,
                "amountPaisa" to original.amountPaisa,
                "transactionKind" to "RECOVERY_REVERSAL",
                "relatedTxId" to original.id,
                "reason" to reason.trim(),
                "paymentMethod" to original.paymentMethod,
                "referenceNumber" to original.referenceNumber,
                "notes" to "Reversal of ${original.txCode} (Original Date: ${original.txDateFormatted}): ${reason.trim()}",
                "txDateMillis" to nowMillis,
                "txDateFormatted" to PakistanTime.formatDate(nowMillis),
                "txTimeFormatted" to PakistanTime.formatTime(nowMillis),
                "createdByAccountId" to session.accountId,
                "createdByName" to session.fullName,
                "createdByRole" to session.role.label,
                "correctionGroupId" to "",
                "correctedFromTxId" to "",
                "originalAmountPaisa" to original.amountPaisa,
                "originalTxDateMillis" to original.txDateMillis,
                "isBackdated" to false,
                "reversedAtMillis" to nowMillis,
                "reversedByAccountId" to session.accountId,
                "reversedByName" to session.fullName,
                "syncStatus" to "SYNCED",
                "idempotencyToken" to revId,
                "deviceId" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val amountStr = CurrencyFormatter.formatPkr(original.amountPaisa)
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "RECOVERY_REVERSED",
                shopUid = original.shopUid,
                previousValue = "${original.txCode}: $amountStr (${original.txDateFormatted})",
                newValue = "$txCode (Reversed ${PakistanTime.formatDate(nowMillis)})",
                timestampMillis = nowMillis,
                description = "Admin reversed recovery ${original.txCode} ($amountStr) — Reason: ${reason.trim()}"
            )
        )
        batch.commit().await()
    }

    /**
     * Admin-only audit-preserving payment correction:
     * Never deletes or mutates the original recovery transaction; atomically records:
     * 1) RECOVERY_REVERSAL linked to original.id
     * 2) New RECOVERY linked via correctedFromTxId & correctionGroupId
     * 3) Immutable RECOVERY_CORRECTED audit log + Booker notification
     */
    suspend fun correctRecoveryTransaction(
        session: ActiveWorkspaceSession,
        original: RecoveryTransaction,
        newAmountPaisa: Long,
        newTxDateMillis: Long,
        newPaymentMethod: PaymentMethod,
        newReferenceNumber: String,
        newNotes: String,
        reason: String,
        allowOverRecovery: Boolean
    ): Result<RecoveryTransaction> = runCatching {
        requireUserId()
        require(session.role == AccountRole.SUPER_ADMIN) {
            "Only Business Super Admin can correct recovery transactions."
        }
        require(original.transactionKind == "RECOVERY") {
            "Only original recovery payments can be corrected."
        }
        require(newAmountPaisa > 0L) { "Corrected amount must be greater than Rs. 0." }
        val cleanReason = reason.trim()
        require(cleanReason.length >= 3) { "A clear reason is mandatory for every payment correction." }
        val nowMillis = PakistanTime.nowMillis()
        if (PakistanTime.isFuturePakistanDay(newTxDateMillis, nowMillis)) {
            throw IllegalArgumentException("Future payment dates are not permitted.")
        }

        val existingReversals = businessRef(session.businessId).collection("recovery_transactions")
            .whereEqualTo("relatedTxId", original.id)
            .whereEqualTo("transactionKind", "RECOVERY_REVERSAL")
            .get().await()
        if (!existingReversals.isEmpty) {
            throw IllegalStateException("This payment (${original.txCode}) was already reversed or corrected.")
        }

        // Balance check excluding original payment that is being reversed
        val shopCredits = businessRef(session.businessId).collection("credit_transactions")
            .whereEqualTo("shopId", original.shopId)
            .get().await()
            .toObjects(CreditTransaction::class.java)
        val shopRecoveries = businessRef(session.businessId).collection("recovery_transactions")
            .whereEqualTo("shopId", original.shopId)
            .get().await()
            .toObjects(RecoveryTransaction::class.java)
        val netRecWithoutOriginal = FinancialEngine.netRecoveryPaisa(shopRecoveries) - original.amountPaisa
        val outstandingBeforeCorrected = FinancialEngine.netCreditPaisa(shopCredits) - netRecWithoutOriginal
        if (!allowOverRecovery && newAmountPaisa > outstandingBeforeCorrected) {
            throw IllegalArgumentException(
                "Corrected amount (${CurrencyFormatter.formatPkr(newAmountPaisa)}) exceeds outstanding balance (${CurrencyFormatter.formatPkr(outstandingBeforeCorrected)})."
            )
        }

        val groupId = "cgrp_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val revId = "rrev_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val revCode = PasswordHasher.generateCode("RCV")
        val newTxId = "rc_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val newTxCode = PasswordHasher.generateCode("RC")
        val isBackdated = PakistanTime.isBackdatedPakistanDay(newTxDateMillis, nowMillis)
        val newDateFormatted = PakistanTime.formatDate(newTxDateMillis)
        val newTimeFormatted = PakistanTime.formatTime(newTxDateMillis)

        val batch = db.batch()
        batch.set(
            businessRef(session.businessId).collection("recovery_transactions").document(revId),
            mapOf(
                "businessId" to session.businessId,
                "txCode" to revCode,
                "shopId" to original.shopId,
                "shopUid" to original.shopUid,
                "shopName" to original.shopName,
                "bookerId" to original.bookerId,
                "bookerName" to original.bookerName,
                "amountPaisa" to original.amountPaisa,
                "transactionKind" to "RECOVERY_REVERSAL",
                "relatedTxId" to original.id,
                "reason" to "Corrected to $newTxCode: $cleanReason",
                "paymentMethod" to original.paymentMethod,
                "referenceNumber" to original.referenceNumber,
                "notes" to "Reversed by correction $newTxCode (Original ${original.txDateFormatted}): $cleanReason",
                "txDateMillis" to nowMillis,
                "txDateFormatted" to PakistanTime.formatDate(nowMillis),
                "txTimeFormatted" to PakistanTime.formatTime(nowMillis),
                "createdByAccountId" to session.accountId,
                "createdByName" to session.fullName,
                "createdByRole" to session.role.label,
                "correctionGroupId" to groupId,
                "correctedFromTxId" to original.id,
                "originalAmountPaisa" to original.amountPaisa,
                "originalTxDateMillis" to original.txDateMillis,
                "isBackdated" to false,
                "reversedAtMillis" to nowMillis,
                "reversedByAccountId" to session.accountId,
                "reversedByName" to session.fullName,
                "syncStatus" to "SYNCED",
                "idempotencyToken" to revId,
                "deviceId" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        batch.set(
            businessRef(session.businessId).collection("recovery_transactions").document(newTxId),
            mapOf(
                "businessId" to session.businessId,
                "txCode" to newTxCode,
                "shopId" to original.shopId,
                "shopUid" to original.shopUid,
                "shopName" to original.shopName,
                "bookerId" to original.bookerId,
                "bookerName" to original.bookerName,
                "amountPaisa" to newAmountPaisa,
                "transactionKind" to "RECOVERY",
                "relatedTxId" to revId,
                "reason" to cleanReason,
                "paymentMethod" to newPaymentMethod.label,
                "referenceNumber" to newReferenceNumber.trim(),
                "notes" to newNotes.trim().ifBlank { "Corrected entry replacing ${original.txCode}: $cleanReason" },
                "txDateMillis" to newTxDateMillis,
                "txDateFormatted" to newDateFormatted,
                "txTimeFormatted" to newTimeFormatted,
                "createdByAccountId" to session.accountId,
                "createdByName" to session.fullName,
                "createdByRole" to session.role.label,
                "correctionGroupId" to groupId,
                "correctedFromTxId" to original.id,
                "originalAmountPaisa" to original.amountPaisa,
                "originalTxDateMillis" to original.txDateMillis,
                "isBackdated" to isBackdated,
                "reversedAtMillis" to 0L,
                "reversedByAccountId" to "",
                "reversedByName" to "",
                "syncStatus" to "SYNCED",
                "idempotencyToken" to newTxId,
                "deviceId" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        val oldAmtStr = CurrencyFormatter.formatPkr(original.amountPaisa)
        val newAmtStr = CurrencyFormatter.formatPkr(newAmountPaisa)
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "RECOVERY_CORRECTED",
                shopUid = original.shopUid,
                previousValue = "${original.txCode}: $oldAmtStr (${original.txDateFormatted})",
                newValue = "$newTxCode: $newAmtStr ($newDateFormatted)",
                timestampMillis = nowMillis,
                description = "Admin corrected recovery ${original.txCode} -> $newTxCode ($oldAmtStr -> $newAmtStr) — Reason: $cleanReason"
            )
        )

        val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("notifications").document(notifId),
            notificationPayload(
                businessId = session.businessId,
                title = "Payment Corrected by Admin",
                message = "${original.shopName} (${original.shopUid}) recovery ${original.txCode} corrected from $oldAmtStr to $newAmtStr.",
                category = "RECOVERY",
                targetRole = "BOOKER",
                targetBookerId = original.bookerId,
                shopUid = original.shopUid
            )
        )

        batch.commit().await()

        RecoveryTransaction(
            id = newTxId,
            businessId = session.businessId,
            txCode = newTxCode,
            shopId = original.shopId,
            shopUid = original.shopUid,
            shopName = original.shopName,
            bookerId = original.bookerId,
            bookerName = original.bookerName,
            amountPaisa = newAmountPaisa,
            transactionKind = "RECOVERY",
            relatedTxId = revId,
            reason = cleanReason,
            paymentMethod = newPaymentMethod.label,
            referenceNumber = newReferenceNumber.trim(),
            notes = newNotes.trim(),
            txDateMillis = newTxDateMillis,
            txDateFormatted = newDateFormatted,
            txTimeFormatted = newTimeFormatted,
            createdByAccountId = session.accountId,
            createdByName = session.fullName,
            createdByRole = session.role.label,
            correctionGroupId = groupId,
            correctedFromTxId = original.id,
            originalAmountPaisa = original.amountPaisa,
            originalTxDateMillis = original.txDateMillis,
            isBackdated = isBackdated,
            syncStatus = "SYNCED"
        )
    }

    // --- Notifications & Business Settings ---

    suspend fun markNotificationRead(businessId: String, notificationId: String) {
        try {
            requireUserId()
            businessRef(businessId).collection("notifications").document(notificationId)
                .update(mapOf("isRead" to true, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "businesses/$businessId/notifications/$notificationId", auth)
        }
    }

    suspend fun markAllNotificationsRead(
        businessId: String,
        role: AccountRole,
        bookerId: String,
        unreadIds: List<String>
    ): Result<Unit> = runCatching {
        requireUserId()
        val col = businessRef(businessId).collection("notifications")
        val serverUnreadIds = try {
            val baseSnapshot = if (role == AccountRole.BOOKER) {
                col.whereEqualTo("targetBookerId", bookerId).get().await()
            } else {
                col.get().await()
            }
            baseSnapshot.documents
                .filter { doc -> doc.getBoolean("isRead") != true }
                .map { it.id }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            try {
                val query = if (role == AccountRole.BOOKER) {
                    col.whereEqualTo("targetBookerId", bookerId).whereEqualTo("isRead", false)
                } else {
                    col.whereEqualTo("isRead", false)
                }
                query.get().await().documents.map { it.id }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Exception) {
                emptyList()
            }
        }
        val allUnreadIds = (unreadIds + serverUnreadIds).filter { it.isNotBlank() }.distinct()
        if (allUnreadIds.isEmpty()) return@runCatching

        var lastError: Exception? = null
        allUnreadIds.chunked(400).forEach { chunk ->
            try {
                val batch = db.batch()
                chunk.forEach { id ->
                    batch.update(
                        col.document(id),
                        mapOf("isRead" to true, "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
                batch.commit().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall back to individual updates so every valid doc still gets updated
                chunk.forEach { id ->
                    try {
                        col.document(id)
                            .update(mapOf("isRead" to true, "updatedAt" to FieldValue.serverTimestamp()))
                            .await()
                    } catch (innerCancel: CancellationException) {
                        throw innerCancel
                    } catch (innerErr: Exception) {
                        lastError = innerErr
                    }
                }
            }
        }
        if (lastError != null && allUnreadIds.size == 1) {
            handleFirestoreError(lastError!!, OperationType.UPDATE, "businesses/$businessId/notifications", auth)
        }
    }

    // --- Admin Group Chat & Meetings ---

    suspend fun createChatGroup(
        session: ActiveWorkspaceSession,
        name: String,
        description: String,
        memberAccountIds: List<String>,
        memberNames: List<String>
    ): Result<ChatGroup> = runCatching {
        requireUserId()
        require(session.role == AccountRole.SUPER_ADMIN) { "Only Super Admin can create meeting groups." }
        val cleanName = name.trim()
        require(cleanName.length >= 2) { "Group name must be at least 2 characters." }

        val ids = (listOf(session.accountId) + memberAccountIds).distinct()
        val names = (listOf(session.fullName) + memberNames).distinct()
        val groupId = "grp_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val nowMillis = PakistanTime.nowMillis()

        val groupRef = businessRef(session.businessId).collection("chat_groups").document(groupId)
        val batch = db.batch()
        batch.set(
            groupRef,
            mapOf(
                "businessId" to session.businessId,
                "name" to cleanName,
                "description" to description.trim(),
                "memberAccountIds" to ids,
                "memberNames" to names,
                "createdByAccountId" to session.accountId,
                "createdByName" to session.fullName,
                "lastMessagePreview" to "Group created by ${session.fullName}",
                "lastMessageAtMillis" to nowMillis,
                "isArchived" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "CHAT_GROUP_CREATED",
                shopUid = "",
                previousValue = "",
                newValue = "$cleanName (${ids.size} members)",
                timestampMillis = nowMillis,
                description = "Created meeting group '$cleanName' with ${ids.size} participants"
            )
        )
        batch.commit().await()

        ChatGroup(
            id = groupId,
            businessId = session.businessId,
            name = cleanName,
            description = description.trim(),
            memberAccountIds = ids,
            memberNames = names,
            createdByAccountId = session.accountId,
            createdByName = session.fullName,
            lastMessagePreview = "Group created by ${session.fullName}",
            lastMessageAtMillis = nowMillis,
            isArchived = false
        )
    }

    suspend fun updateChatGroupMembers(
        session: ActiveWorkspaceSession,
        group: ChatGroup,
        memberAccountIds: List<String>,
        memberNames: List<String>
    ): Result<Unit> = runCatching {
        requireUserId()
        require(session.role == AccountRole.SUPER_ADMIN) { "Only Super Admin can update group members." }
        val ids = (listOf(session.accountId) + memberAccountIds).distinct()
        val names = (listOf(session.fullName) + memberNames).distinct()
        businessRef(session.businessId).collection("chat_groups").document(group.id)
            .update(
                mapOf(
                    "memberAccountIds" to ids,
                    "memberNames" to names,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
    }

    suspend fun sendChatMessage(
        session: ActiveWorkspaceSession,
        groupId: String,
        messageType: ChatMessageType,
        text: String,
        mediaBase64: String = "",
        fileName: String = "",
        mimeType: String = "",
        durationSec: Int = 0,
        fileSizeBytes: Long = 0L
    ): Result<ChatMessage> = runCatching {
        requireUserId()
        val cleanText = text.trim()
        if (messageType == ChatMessageType.TEXT) {
            require(cleanText.isNotEmpty()) { "Message cannot be empty." }
        } else {
            require(mediaBase64.isNotEmpty()) { "Media attachment data is empty." }
            require(mediaBase64.length <= 750_000) { "Attachment is too large. Please keep files under 450 KB." }
        }

        val nowMillis = PakistanTime.nowMillis()
        val dateFormatted = PakistanTime.formatDate(nowMillis)
        val timeFormatted = PakistanTime.formatTime(nowMillis)
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val groupRef = businessRef(session.businessId).collection("chat_groups").document(groupId)
        val msgRef = groupRef.collection("messages").document(msgId)

        val preview = when (messageType) {
            ChatMessageType.TEXT -> "${session.fullName}: ${cleanText.take(60)}"
            ChatMessageType.VOICE -> "${session.fullName}: Voice note (${durationSec}s)"
            ChatMessageType.IMAGE -> "${session.fullName}: Photo"
            ChatMessageType.FILE -> "${session.fullName}: ${fileName.ifBlank { "File" }}"
        }.take(180)

        val batch = db.batch()
        batch.set(
            msgRef,
            mapOf(
                "businessId" to session.businessId,
                "groupId" to groupId,
                "senderAccountId" to session.accountId,
                "senderName" to session.fullName,
                "senderRole" to session.role.label,
                "messageType" to messageType.key,
                "text" to cleanText.take(2000),
                "mediaBase64" to mediaBase64,
                "fileName" to fileName.take(200),
                "mimeType" to mimeType.take(120),
                "durationSec" to durationSec.coerceIn(0, 600),
                "fileSizeBytes" to fileSizeBytes.coerceAtLeast(0L),
                "sentAtMillis" to nowMillis,
                "dateFormatted" to dateFormatted,
                "timeFormatted" to timeFormatted,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            groupRef,
            mapOf(
                "lastMessagePreview" to preview,
                "lastMessageAtMillis" to nowMillis,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()

        ChatMessage(
            id = msgId,
            businessId = session.businessId,
            groupId = groupId,
            senderAccountId = session.accountId,
            senderName = session.fullName,
            senderRole = session.role.label,
            messageType = messageType.key,
            text = cleanText,
            mediaBase64 = mediaBase64,
            fileName = fileName,
            mimeType = mimeType,
            durationSec = durationSec,
            fileSizeBytes = fileSizeBytes,
            sentAtMillis = nowMillis,
            dateFormatted = dateFormatted,
            timeFormatted = timeFormatted
        )
    }

    suspend fun updateBusinessSettings(
        session: ActiveWorkspaceSession,
        business: Business,
        businessName: String,
        businessAddress: String,
        contactPhone: String,
        allowOverRecovery: Boolean,
        requireInvoiceNumber: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val cleanBizName = businessName.trim()
        require(cleanBizName.length >= 2) { "Business name is required." }

        val batch = db.batch()
        batch.update(
            businessRef(session.businessId),
            mapOf(
                "businessName" to cleanBizName,
                "allowOverRecovery" to allowOverRecovery,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            businessRef(session.businessId).collection("business_settings").document("default"),
            mapOf(
                "businessName" to cleanBizName,
                "businessAddress" to businessAddress.trim(),
                "contactPhone" to contactPhone.trim(),
                "allowOverRecovery" to allowOverRecovery,
                "requireInvoiceNumber" to requireInvoiceNumber,
                "updatedBy" to session.fullName,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        batch.set(
            businessRef(session.businessId).collection("audit_logs").document(logId),
            auditLogPayload(
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                role = session.role.key,
                action = "SETTINGS_UPDATED",
                shopUid = "",
                previousValue = business.businessName,
                newValue = "$cleanBizName (OverRecovery=$allowOverRecovery)",
                timestampMillis = PakistanTime.nowMillis(),
                description = "Updated business settings for $cleanBizName"
            )
        )
        batch.commit().await()
    }

    // --- Payload Builders ---

    private fun permissionPayload(
        businessId: String,
        userId: String,
        p: StaffPermission,
        isCreate: Boolean
    ): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "businessId" to businessId,
            "userId" to userId,
            "viewDashboard" to p.viewDashboard,
            "viewShops" to p.viewShops,
            "addShops" to p.addShops,
            "editShops" to p.editShops,
            "addCredit" to p.addCredit,
            "editCredit" to p.editCredit,
            "recordRecovery" to p.recordRecovery,
            "viewRecoveryHistory" to p.viewRecoveryHistory,
            "viewBookers" to p.viewBookers,
            "manageBookers" to p.manageBookers,
            "viewReports" to p.viewReports,
            "generatePdfs" to p.generatePdfs,
            "searchShops" to p.searchShops,
            "viewActivityLogs" to p.viewActivityLogs,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isCreate) {
            map["createdAt"] = FieldValue.serverTimestamp()
        }
        return map
    }

    suspend fun logWorkspaceEvent(
        session: ActiveWorkspaceSession,
        action: String,
        shopUid: String = "",
        previousValue: String = "",
        newValue: String = "",
        description: String
    ) {
        runCatching {
            if (auth.currentUser == null) return
            val nowMillis = PakistanTime.nowMillis()
            val logId = "act_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val batch = db.batch()
            batch.set(
                businessRef(session.businessId).collection("audit_logs").document(logId),
                auditLogPayload(
                    businessId = session.businessId,
                    userId = session.accountId,
                    username = session.username,
                    role = session.role.key,
                    action = action,
                    shopUid = shopUid,
                    previousValue = previousValue,
                    newValue = newValue,
                    timestampMillis = nowMillis,
                    description = description.take(490)
                )
            )
            if (session.role == AccountRole.SUPER_ADMIN) {
                batch.update(
                    businessRef(session.businessId),
                    mapOf(
                        "lastActivityMillis" to nowMillis,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
        }
    }

    private fun auditLogPayload(
        businessId: String,
        userId: String,
        username: String,
        role: String,
        action: String,
        shopUid: String,
        previousValue: String,
        newValue: String,
        timestampMillis: Long,
        description: String
    ): Map<String, Any> = mapOf(
        "businessId" to businessId,
        "activityCode" to PasswordHasher.generateCode("ACT"),
        "userId" to userId,
        "username" to username,
        "role" to role,
        "action" to action,
        "shopUid" to shopUid,
        "previousValue" to previousValue,
        "newValue" to newValue,
        "dateFormatted" to PakistanTime.formatDate(timestampMillis),
        "timeFormatted" to PakistanTime.formatTime(timestampMillis),
        "timestampMillis" to timestampMillis,
        "description" to description,
        "createdAt" to FieldValue.serverTimestamp()
    )

    private fun notificationPayload(
        businessId: String,
        title: String,
        message: String,
        category: String,
        targetRole: String,
        targetBookerId: String,
        shopUid: String
    ): Map<String, Any> = mapOf(
        "businessId" to businessId,
        "title" to title,
        "message" to message,
        "category" to category,
        "targetRole" to targetRole,
        "targetBookerId" to targetBookerId,
        "shopUid" to shopUid,
        "isRead" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun logStreamError(error: Throwable, op: OperationType, path: String, expectedUid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path, auth)
        }
    }
}
