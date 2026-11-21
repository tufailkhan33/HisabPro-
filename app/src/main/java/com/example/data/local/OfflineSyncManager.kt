package com.example.data.local

import com.example.data.firebase.HisabProRepository
import com.example.domain.model.AccountRole
import com.example.domain.model.ActiveWorkspaceSession
import com.example.domain.model.CreditTransaction
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PendingSyncTransaction
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.Shop
import com.example.domain.model.SyncHistoryRecord
import com.example.domain.model.SyncStatus
import com.example.util.CurrencyFormatter
import com.example.util.PakistanTime
import com.example.util.PasswordHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class SyncBatchSummary(
    val syncedCount: Int,
    val conflictCount: Int,
    val failedCount: Int
)

/**
 * Coordinates durable offline outbox persistence in Room SQLite and automatic/manual
 * synchronization with Firestore using idempotency tokens and conflict-safe validation.
 */
class OfflineSyncManager(
    private val dao: HisabLocalDao,
    private val repository: HisabProRepository,
    private val deviceId: String
) {
    private val syncMutex = Mutex()

    fun observeOutbox(businessId: String): Flow<List<PendingSyncTransaction>> =
        dao.observePendingSync(businessId).map { list -> list.map { it.toDomain() } }

    fun observeUnsyncedCount(businessId: String): Flow<Int> =
        dao.observeUnsyncedCount(businessId)

    fun observeSyncHistory(businessId: String): Flow<List<SyncHistoryRecord>> =
        dao.observeSyncHistory(businessId).map { list -> list.map { it.toDomain() } }

    suspend fun enqueueCredit(
        session: ActiveWorkspaceSession,
        shop: Shop,
        amountPaisa: Long,
        txDateMillis: Long,
        dueDateMillis: Long,
        invoiceNumber: String,
        description: String
    ): Result<PendingSyncTransaction> = runCatching {
        require(session.role == AccountRole.SUPER_ADMIN || session.role == AccountRole.STAFF) {
            "Read-only accounts cannot add credit."
        }
        require(amountPaisa > 0L) { "Credit amount must be greater than Rs. 0." }
        require(!shop.isArchived) { "Cannot add credit to an archived shop." }
        val now = PakistanTime.nowMillis()
        require(!PakistanTime.isFuturePakistanDay(txDateMillis, now)) {
            "Future credit dates are not permitted."
        }

        val token = "offcr_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val localId = token
        val txCode = PasswordHasher.generateCode("CR")
        val item = PendingSyncTransaction(
            localId = localId,
            businessId = session.businessId,
            userId = session.accountId,
            username = session.username,
            userRole = session.role.key,
            deviceId = deviceId,
            shopId = shop.id,
            shopUid = shop.shopUid,
            shopName = shop.shopName,
            bookerId = shop.bookerId,
            bookerName = shop.bookerName,
            txType = "CREDIT",
            amountPaisa = amountPaisa,
            originalTxDateMillis = txDateMillis,
            dueDateMillis = dueDateMillis,
            invoiceOrRefNumber = invoiceNumber.trim(),
            paymentMethod = "",
            descriptionOrNotes = description.trim().ifBlank { "Offline credit entry" },
            localDeviceTimestamp = now,
            syncStatus = SyncStatus.PENDING_SYNC.key,
            idempotencyToken = token,
            auditMetadata = "Queued offline by @${session.username} (${session.role.key}) on device $deviceId"
        )

        val cachedCredit = CreditTransaction(
            id = localId,
            businessId = session.businessId,
            txCode = "$txCode (OFFLINE)",
            shopId = shop.id,
            shopUid = shop.shopUid,
            shopName = shop.shopName,
            bookerId = shop.bookerId,
            bookerName = shop.bookerName,
            amountPaisa = amountPaisa,
            transactionKind = "CREDIT",
            invoiceNumber = invoiceNumber.trim(),
            description = item.descriptionOrNotes,
            txDateMillis = txDateMillis,
            txDateFormatted = PakistanTime.formatDate(txDateMillis),
            txTimeFormatted = PakistanTime.formatTime(txDateMillis),
            createdByAccountId = session.accountId,
            createdByName = session.fullName,
            createdByRole = session.role.label,
            dueDateMillis = dueDateMillis,
            dueDateFormatted = if (dueDateMillis > 0L) PakistanTime.formatDate(dueDateMillis) else "",
            entryTimestampMillis = now,
            entryDateFormatted = PakistanTime.formatDate(now),
            entryTimeFormatted = PakistanTime.formatTime(now),
            syncStatus = SyncStatus.PENDING_SYNC.key,
            idempotencyToken = token,
            deviceId = deviceId
        )

        dao.upsertPendingSync(item.toEntity())
        dao.upsertCreditTransactions(listOf(cachedCredit.toEntity()))
        item
    }

    suspend fun enqueueRecovery(
        session: ActiveWorkspaceSession,
        shop: Shop,
        amountPaisa: Long,
        txDateMillis: Long,
        paymentMethod: PaymentMethod,
        referenceNumber: String,
        notes: String,
        allowOverRecovery: Boolean,
        currentCachedOutstandingPaisa: Long
    ): Result<PendingSyncTransaction> = runCatching {
        require(session.role == AccountRole.SUPER_ADMIN || session.role == AccountRole.STAFF) {
            "Read-only accounts cannot record recovery."
        }
        require(amountPaisa > 0L) { "Recovery amount must be greater than Rs. 0." }
        val now = PakistanTime.nowMillis()
        require(!PakistanTime.isFuturePakistanDay(txDateMillis, now)) {
            "Future recovery dates are not permitted."
        }
        if (session.role == AccountRole.STAFF) {
            require(PakistanTime.isSamePakistanDay(txDateMillis, now)) {
                "Staff recovery entries must use the current Pakistan calendar date (${PakistanTime.formatDate(now)})."
            }
        }
        if (!allowOverRecovery && amountPaisa > currentCachedOutstandingPaisa) {
            throw IllegalArgumentException(
                "Offline recovery (${CurrencyFormatter.formatPkr(amountPaisa)}) exceeds cached outstanding (${CurrencyFormatter.formatPkr(currentCachedOutstandingPaisa)})."
            )
        }

        val token = "offrc_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val localId = token
        val txCode = PasswordHasher.generateCode("RC")
        val isBackdated = PakistanTime.isBackdatedPakistanDay(txDateMillis, now)
        val item = PendingSyncTransaction(
            localId = localId,
            businessId = session.businessId,
            userId = session.accountId,
            username = session.username,
            userRole = session.role.key,
            deviceId = deviceId,
            shopId = shop.id,
            shopUid = shop.shopUid,
            shopName = shop.shopName,
            bookerId = shop.bookerId,
            bookerName = shop.bookerName,
            txType = "RECOVERY",
            amountPaisa = amountPaisa,
            originalTxDateMillis = txDateMillis,
            dueDateMillis = 0L,
            invoiceOrRefNumber = referenceNumber.trim(),
            paymentMethod = paymentMethod.label,
            descriptionOrNotes = notes.trim().ifBlank { "Offline recovery entry" },
            localDeviceTimestamp = now,
            syncStatus = SyncStatus.PENDING_SYNC.key,
            idempotencyToken = token,
            auditMetadata = "Queued offline by @${session.username} (${session.role.key}) on device $deviceId"
        )

        val cachedRec = RecoveryTransaction(
            id = localId,
            businessId = session.businessId,
            txCode = "$txCode (OFFLINE)",
            shopId = shop.id,
            shopUid = shop.shopUid,
            shopName = shop.shopName,
            bookerId = shop.bookerId,
            bookerName = shop.bookerName,
            amountPaisa = amountPaisa,
            transactionKind = "RECOVERY",
            paymentMethod = paymentMethod.label,
            referenceNumber = referenceNumber.trim(),
            notes = item.descriptionOrNotes,
            txDateMillis = txDateMillis,
            txDateFormatted = PakistanTime.formatDate(txDateMillis),
            txTimeFormatted = PakistanTime.formatTime(txDateMillis),
            createdByAccountId = session.accountId,
            createdByName = session.fullName,
            createdByRole = session.role.label,
            originalAmountPaisa = amountPaisa,
            originalTxDateMillis = txDateMillis,
            isBackdated = isBackdated,
            syncStatus = SyncStatus.PENDING_SYNC.key,
            idempotencyToken = token,
            deviceId = deviceId
        )

        dao.upsertPendingSync(item.toEntity())
        dao.upsertRecoveryTransactions(listOf(cachedRec.toEntity()))
        item
    }

    suspend fun syncPendingForBusiness(
        session: ActiveWorkspaceSession,
        allowOverRecoverySetting: Boolean
    ): SyncBatchSummary = syncMutex.withLock {
        val pending = dao.getSyncableOutbox(session.businessId)
        var synced = 0
        var conflicts = 0
        var failed = 0

        for (entity in pending) {
            val item = entity.toDomain()
            dao.upsertPendingSync(entity.copy(syncStatus = SyncStatus.SYNCING.key))
            if (item.txType == "CREDIT") {
                dao.updateCachedCreditSyncStatus(item.localId, SyncStatus.SYNCING.key)
            } else {
                dao.updateCachedRecoverySyncStatus(item.localId, SyncStatus.SYNCING.key)
            }

            val outcome = pushSinglePendingItem(
                session = session,
                item = item,
                allowOverRecoverySetting = allowOverRecoverySetting
            )
            when (outcome) {
                is PushOutcome.Synced -> {
                    synced++
                    val now = PakistanTime.nowMillis()
                    dao.upsertPendingSync(
                        entity.copy(
                            syncStatus = SyncStatus.SYNCED.key,
                            errorMessage = "",
                            serverSyncedAtMillis = now
                        )
                    )
                    if (item.txType == "CREDIT") {
                        dao.deleteCachedCreditById(item.localId)
                    } else {
                        dao.deleteCachedRecoveryById(item.localId)
                    }
                    dao.insertSyncHistory(
                        SyncHistoryEntity(
                            id = "hist_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                            businessId = session.businessId,
                            localTxId = item.localId,
                            shopUid = item.shopUid,
                            txType = item.txType,
                            amountPaisa = item.amountPaisa,
                            syncStatus = SyncStatus.SYNCED.key,
                            message = "Synced ${item.txType} (${CurrencyFormatter.formatPkr(item.amountPaisa)}) for ${item.shopUid}",
                            syncedAtMillis = now
                        )
                    )
                }

                is PushOutcome.Conflict -> {
                    conflicts++
                    val now = PakistanTime.nowMillis()
                    dao.upsertPendingSync(
                        entity.copy(
                            syncStatus = SyncStatus.CONFLICT_REQUIRES_REVIEW.key,
                            errorMessage = outcome.reason,
                            retryCount = entity.retryCount + 1
                        )
                    )
                    if (item.txType == "CREDIT") {
                        dao.updateCachedCreditSyncStatus(item.localId, SyncStatus.CONFLICT_REQUIRES_REVIEW.key)
                    } else {
                        dao.updateCachedRecoverySyncStatus(item.localId, SyncStatus.CONFLICT_REQUIRES_REVIEW.key)
                    }
                    dao.insertSyncHistory(
                        SyncHistoryEntity(
                            id = "hist_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                            businessId = session.businessId,
                            localTxId = item.localId,
                            shopUid = item.shopUid,
                            txType = item.txType,
                            amountPaisa = item.amountPaisa,
                            syncStatus = SyncStatus.CONFLICT_REQUIRES_REVIEW.key,
                            message = outcome.reason,
                            syncedAtMillis = now
                        )
                    )
                }

                is PushOutcome.Failed -> {
                    failed++
                    val now = PakistanTime.nowMillis()
                    dao.upsertPendingSync(
                        entity.copy(
                            syncStatus = SyncStatus.SYNC_FAILED.key,
                            errorMessage = outcome.reason,
                            retryCount = entity.retryCount + 1
                        )
                    )
                    if (item.txType == "CREDIT") {
                        dao.updateCachedCreditSyncStatus(item.localId, SyncStatus.SYNC_FAILED.key)
                    } else {
                        dao.updateCachedRecoverySyncStatus(item.localId, SyncStatus.SYNC_FAILED.key)
                    }
                    dao.insertSyncHistory(
                        SyncHistoryEntity(
                            id = "hist_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                            businessId = session.businessId,
                            localTxId = item.localId,
                            shopUid = item.shopUid,
                            txType = item.txType,
                            amountPaisa = item.amountPaisa,
                            syncStatus = SyncStatus.SYNC_FAILED.key,
                            message = outcome.reason,
                            syncedAtMillis = now
                        )
                    )
                }
            }
        }

        SyncBatchSummary(synced, conflicts, failed)
    }

    suspend fun approveConflictAsAdmin(
        session: ActiveWorkspaceSession,
        item: PendingSyncTransaction
    ): Result<Unit> = runCatching {
        require(session.role == AccountRole.SUPER_ADMIN) {
            "Only Super Admin can approve sync conflicts."
        }
        val shop = Shop(
            id = item.shopId,
            businessId = item.businessId,
            shopUid = item.shopUid,
            shopName = item.shopName,
            bookerId = item.bookerId,
            bookerName = item.bookerName
        )
        if (item.txType == "CREDIT") {
            repository.addCreditTransaction(
                session = session,
                shop = shop,
                amountPaisa = item.amountPaisa,
                txDateMillis = item.originalTxDateMillis,
                invoiceNumber = item.invoiceOrRefNumber,
                description = item.descriptionOrNotes,
                attachmentUri = "",
                dueDateMillis = item.dueDateMillis,
                idempotencyToken = item.idempotencyToken,
                deviceId = item.deviceId
            ).getOrThrow()
            dao.deleteCachedCreditById(item.localId)
        } else {
            repository.recordRecoveryTransaction(
                session = session,
                shop = shop,
                amountPaisa = item.amountPaisa,
                txDateMillis = item.originalTxDateMillis,
                paymentMethod = PaymentMethod.fromLabel(item.paymentMethod),
                referenceNumber = item.invoiceOrRefNumber,
                notes = "${item.descriptionOrNotes} [Admin conflict approved]",
                allowOverRecovery = true,
                clientRequestToken = item.idempotencyToken,
                deviceId = item.deviceId,
                expectedDeviceTimestampMillis = item.localDeviceTimestamp
            ).getOrThrow()
            dao.deleteCachedRecoveryById(item.localId)
        }
        val now = PakistanTime.nowMillis()
        dao.upsertPendingSync(
            item.copy(
                syncStatus = SyncStatus.SYNCED.key,
                errorMessage = "Resolved by Admin",
                serverSyncedAtMillis = now
            ).toEntity()
        )
        dao.insertSyncHistory(
            SyncHistoryEntity(
                id = "hist_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                businessId = session.businessId,
                localTxId = item.localId,
                shopUid = item.shopUid,
                txType = item.txType,
                amountPaisa = item.amountPaisa,
                syncStatus = SyncStatus.SYNCED.key,
                message = "Admin approved conflict for ${item.shopUid}",
                syncedAtMillis = now
            )
        )
    }

    suspend fun discardConflictOrFailedItem(
        session: ActiveWorkspaceSession,
        item: PendingSyncTransaction
    ): Result<Unit> = runCatching {
        require(session.role == AccountRole.SUPER_ADMIN || item.userId == session.accountId) {
            "Insufficient permission to discard queued item."
        }
        dao.deletePendingSync(item.localId)
        if (item.txType == "CREDIT") {
            dao.deleteCachedCreditById(item.localId)
        } else {
            dao.deleteCachedRecoveryById(item.localId)
        }
    }

    private sealed interface PushOutcome {
        data object Synced : PushOutcome
        data class Conflict(val reason: String) : PushOutcome
        data class Failed(val reason: String) : PushOutcome
    }

    private suspend fun pushSinglePendingItem(
        session: ActiveWorkspaceSession,
        item: PendingSyncTransaction,
        allowOverRecoverySetting: Boolean
    ): PushOutcome {
        val cachedShop = dao.getShopById(item.shopId)
        if (cachedShop != null && cachedShop.isArchived) {
            return PushOutcome.Conflict("Shop ${item.shopUid} is archived. Admin conflict review required before posting.")
        }
        val shop = cachedShop?.toDomain() ?: Shop(
            id = item.shopId,
            businessId = item.businessId,
            shopUid = item.shopUid,
            shopName = item.shopName,
            bookerId = item.bookerId,
            bookerName = item.bookerName
        )
        val result = if (item.txType == "CREDIT") {
            repository.addCreditTransaction(
                session = session,
                shop = shop,
                amountPaisa = item.amountPaisa,
                txDateMillis = item.originalTxDateMillis,
                invoiceNumber = item.invoiceOrRefNumber,
                description = item.descriptionOrNotes,
                attachmentUri = "",
                dueDateMillis = item.dueDateMillis,
                idempotencyToken = item.idempotencyToken,
                deviceId = item.deviceId
            )
        } else {
            repository.recordRecoveryTransaction(
                session = session,
                shop = shop,
                amountPaisa = item.amountPaisa,
                txDateMillis = item.originalTxDateMillis,
                paymentMethod = PaymentMethod.fromLabel(item.paymentMethod),
                referenceNumber = item.invoiceOrRefNumber,
                notes = item.descriptionOrNotes,
                allowOverRecovery = allowOverRecoverySetting,
                clientRequestToken = item.idempotencyToken,
                deviceId = item.deviceId,
                expectedDeviceTimestampMillis = item.localDeviceTimestamp
            )
        }

        return if (result.isSuccess) {
            PushOutcome.Synced
        } else {
            val err = result.exceptionOrNull()
            val msg = err?.message.orEmpty()
            when {
                msg.contains("Duplicate submission prevented", ignoreCase = true) -> PushOutcome.Synced
                msg.contains("exceeds current outstanding balance", ignoreCase = true) ||
                    msg.contains("current Pakistan calendar date", ignoreCase = true) ||
                    msg.contains("PERMISSION_DENIED", ignoreCase = true) ||
                    msg.contains("archived", ignoreCase = true) ||
                    msg.contains("suspended", ignoreCase = true) ->
                    PushOutcome.Conflict(msg.ifBlank { "Requires Admin conflict review before posting." })
                else -> PushOutcome.Failed(msg.ifBlank { "Network or sync error. Will retry automatically." })
            }
        }
    }
}
