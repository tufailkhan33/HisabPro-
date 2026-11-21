package com.example.desktop.local

import com.example.desktop.cloud.DesktopFirestoreClient
import com.example.desktop.cloud.FirestoreRestCodec
import com.example.shared.engine.SharedFinancialEngine
import com.example.shared.model.*
import com.example.shared.util.SharedPakistanTime
import com.example.shared.util.SharedPasswordHasher
import java.util.UUID

data class DesktopSyncRunResult(
    val syncedCount: Int = 0,
    val failedCount: Int = 0,
    val conflictCount: Int = 0,
    val skippedCount: Int = 0
)

/**
 * Coordinates offline SQLite queuing, idempotency token protection, duplicate prevention,
 * conflict detection, and replay to Firebase Firestore on Windows Desktop.
 */
class DesktopOfflineSyncManager(
    private val db: DesktopSqliteDatabase,
    private val firestore: DesktopFirestoreClient?,
    private val deviceIdProvider: () -> String = { System.getProperty("user.name", "win-pc") }
) {

    fun queueOfflineCredit(
        session: ActiveWorkspaceSession,
        shop: Shop,
        amountPaisa: Long,
        txDateMillis: Long,
        dueDateMillis: Long,
        invoiceNumber: String,
        description: String
    ): Result<CreditTransaction> = runCatching {
        require(amountPaisa > 0L) { "Credit amount must be greater than Rs. 0." }
        val now = SharedPakistanTime.nowMillis()
        val localId = "cr_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val idempotencyToken = "win_cr_${UUID.randomUUID().toString().replace("-", "")}"
        val txCode = SharedPasswordHasher.generateCode("CR")
        val devId = deviceIdProvider()

        val credit = CreditTransaction(
            id = localId,
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
            description = description.trim(),
            txDateMillis = txDateMillis,
            txDateFormatted = SharedPakistanTime.formatDate(txDateMillis),
            txTimeFormatted = SharedPakistanTime.formatTime(txDateMillis),
            createdByAccountId = session.accountId,
            createdByName = session.fullName,
            createdByRole = session.role.key,
            dueDateMillis = dueDateMillis,
            dueDateFormatted = if (dueDateMillis > 0L) SharedPakistanTime.formatDate(dueDateMillis) else "",
            entryTimestampMillis = now,
            entryDateFormatted = SharedPakistanTime.formatDate(now),
            entryTimeFormatted = SharedPakistanTime.formatTime(now),
            syncStatus = SyncStatus.PENDING_SYNC.key,
            idempotencyToken = idempotencyToken,
            deviceId = devId
        )

        db.upsertCredit(credit)
        val pending = PendingSyncTransaction(
            localId = localId,
            businessId = session.businessId,
            userId = session.accountId,
            username = session.username,
            userRole = session.role.key,
            deviceId = devId,
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
            descriptionOrNotes = description.trim(),
            localDeviceTimestamp = now,
            syncStatus = SyncStatus.PENDING_SYNC.key,
            idempotencyToken = idempotencyToken,
            auditMetadata = "txCode=$txCode"
        )
        db.insertPendingSyncIfAbsent(pending)
        credit
    }

    fun queueOfflineRecovery(
        session: ActiveWorkspaceSession,
        shop: Shop,
        amountPaisa: Long,
        txDateMillis: Long,
        paymentMethod: PaymentMethod,
        referenceNumber: String,
        notes: String,
        currentShopOutstandingPaisa: Long,
        allowOverRecovery: Boolean
    ): Result<RecoveryTransaction> = runCatching {
        require(amountPaisa > 0L) { "Recovery amount must be greater than Rs. 0." }
        SharedFinancialEngine.validateRecoveryDateForRole(session.role, txDateMillis)?.let { err ->
            throw IllegalArgumentException(err)
        }
        if (!allowOverRecovery && amountPaisa > currentShopOutstandingPaisa) {
            throw IllegalArgumentException("Recovery exceeds shop remaining balance and over-recovery is disabled.")
        }

        val now = SharedPakistanTime.nowMillis()
        val isBackdated = session.role == AccountRole.SUPER_ADMIN && SharedPakistanTime.isBackdatedPakistanDay(txDateMillis, now)
        val localId = "rc_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val idempotencyToken = "win_rc_${UUID.randomUUID().toString().replace("-", "")}"
        val txCode = SharedPasswordHasher.generateCode("RC")
        val devId = deviceIdProvider()

        val rec = RecoveryTransaction(
            id = localId,
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
            txDateFormatted = SharedPakistanTime.formatDate(txDateMillis),
            txTimeFormatted = SharedPakistanTime.formatTime(txDateMillis),
            createdByAccountId = session.accountId,
            createdByName = session.fullName,
            createdByRole = session.role.key,
            originalAmountPaisa = amountPaisa,
            originalTxDateMillis = txDateMillis,
            isBackdated = isBackdated,
            syncStatus = SyncStatus.PENDING_SYNC.key,
            idempotencyToken = idempotencyToken,
            deviceId = devId
        )

        db.upsertRecovery(rec)
        db.insertPendingSyncIfAbsent(
            PendingSyncTransaction(
                localId = localId,
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                userRole = session.role.key,
                deviceId = devId,
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
                descriptionOrNotes = notes.trim(),
                localDeviceTimestamp = now,
                syncStatus = SyncStatus.PENDING_SYNC.key,
                idempotencyToken = idempotencyToken,
                auditMetadata = "txCode=$txCode|backdated=$isBackdated"
            )
        )
        rec
    }

    fun queueOfflineBooking(
        session: ActiveWorkspaceSession,
        shop: Shop,
        product: ProductItem,
        quantity: Int,
        notes: String
    ): Result<BookingOrder> = runCatching {
        require(quantity > 0) { "Booking quantity must be at least 1." }
        val now = SharedPakistanTime.nowMillis()
        val localId = "bk_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val idempotencyToken = "win_bk_${UUID.randomUUID().toString().replace("-", "")}"
        val code = SharedPasswordHasher.generateCode("ORD")
        val devId = deviceIdProvider()
        val total = product.unitPricePaisa * quantity.toLong()

        val booking = BookingOrder(
            id = localId,
            businessId = session.businessId,
            bookingCode = code,
            shopId = shop.id,
            shopUid = shop.shopUid,
            shopName = shop.shopName,
            bookerId = shop.bookerId,
            bookerName = shop.bookerName,
            productId = product.id,
            productName = product.name,
            quantity = quantity,
            unitPricePaisa = product.unitPricePaisa,
            totalAmountPaisa = total,
            status = "BOOKED",
            notes = notes.trim(),
            bookingDateMillis = now,
            bookingDateFormatted = SharedPakistanTime.formatDate(now),
            createdByAccountId = session.accountId,
            createdByName = session.fullName,
            syncStatus = SyncStatus.PENDING_SYNC.key,
            idempotencyToken = idempotencyToken,
            deviceId = devId
        )
        db.upsertBooking(booking)
        db.insertPendingSyncIfAbsent(
            PendingSyncTransaction(
                localId = localId,
                businessId = session.businessId,
                userId = session.accountId,
                username = session.username,
                userRole = session.role.key,
                deviceId = devId,
                shopId = shop.id,
                shopUid = shop.shopUid,
                shopName = shop.shopName,
                bookerId = shop.bookerId,
                bookerName = shop.bookerName,
                txType = "BOOKING",
                amountPaisa = total,
                originalTxDateMillis = now,
                dueDateMillis = 0L,
                invoiceOrRefNumber = code,
                paymentMethod = product.id,
                descriptionOrNotes = "${product.name} x $quantity | ${notes.trim()}",
                localDeviceTimestamp = now,
                syncStatus = SyncStatus.PENDING_SYNC.key,
                idempotencyToken = idempotencyToken,
                auditMetadata = "qty=$quantity|unitPaisa=${product.unitPricePaisa}"
            )
        )
        booking
    }

    suspend fun syncPendingQueue(businessId: String): DesktopSyncRunResult {
        val fs = firestore ?: return DesktopSyncRunResult(skippedCount = db.countUnsynced(businessId))
        val pending = db.getPendingSyncItems(businessId)
            .filter { it.syncStatus != SyncStatus.SYNCED.key && it.syncStatus != SyncStatus.CONFLICT_REQUIRES_REVIEW.key }

        if (pending.isEmpty()) return DesktopSyncRunResult()

        var synced = 0
        var failed = 0
        var conflicts = 0

        for (item in pending) {
            db.updatePendingSyncState(item.localId, SyncStatus.SYNCING.key, "", item.retryCount)
            val outcome = syncSingleItem(fs, item)
            when {
                outcome.isSuccess -> {
                    val now = SharedPakistanTime.nowMillis()
                    db.updatePendingSyncState(item.localId, SyncStatus.SYNCED.key, "", item.retryCount, now)
                    db.markLocalTransactionSynced(item.txType, item.localId)
                    db.deleteSyncedPendingItem(item.localId)
                    db.insertSyncHistory(
                        SyncHistoryRecord(
                            id = "sh_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                            businessId = item.businessId,
                            localTxId = item.localId,
                            shopUid = item.shopUid,
                            txType = item.txType,
                            amountPaisa = item.amountPaisa,
                            syncStatus = SyncStatus.SYNCED.key,
                            message = "Synced to Firestore (${item.idempotencyToken.take(10)})",
                            syncedAtMillis = now
                        )
                    )
                    synced++
                }
                else -> {
                    val err = outcome.exceptionOrNull()?.message.orEmpty()
                    val isConflict = err.contains("ALREADY_EXISTS", ignoreCase = true) ||
                        err.contains("CONFLICT", ignoreCase = true) ||
                        err.contains("409") ||
                        err.contains("PERMISSION_DENIED", ignoreCase = true)
                    val newStatus = if (isConflict) SyncStatus.CONFLICT_REQUIRES_REVIEW.key else SyncStatus.SYNC_FAILED.key
                    db.updatePendingSyncState(
                        localId = item.localId,
                        syncStatus = newStatus,
                        errorMessage = err.take(280),
                        retryCount = item.retryCount + 1
                    )
                    db.insertSyncHistory(
                        SyncHistoryRecord(
                            id = "sh_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                            businessId = item.businessId,
                            localTxId = item.localId,
                            shopUid = item.shopUid,
                            txType = item.txType,
                            amountPaisa = item.amountPaisa,
                            syncStatus = newStatus,
                            message = err.take(200),
                            syncedAtMillis = SharedPakistanTime.nowMillis()
                        )
                    )
                    if (isConflict) conflicts++ else failed++
                }
            }
        }
        return DesktopSyncRunResult(syncedCount = synced, failedCount = failed, conflictCount = conflicts)
    }

    private suspend fun syncSingleItem(fs: DesktopFirestoreClient, item: PendingSyncTransaction): Result<Unit> = runCatching {
        when (item.txType) {
            "CREDIT" -> {
                val existingDoc = fs.getDocument("businesses/${item.businessId}/credit_transactions/${item.localId}").getOrNull()
                if (existingDoc != null) {
                    val remote = FirestoreRestCodec.toCreditTx(existingDoc)
                    if (remote.idempotencyToken == item.idempotencyToken && item.idempotencyToken.isNotBlank()) {
                        return@runCatching // Already safely synced (duplicate-safe idempotency)
                    }
                    if (remote.amountPaisa != item.amountPaisa) {
                        throw IllegalStateException("CONFLICT: Remote credit ${item.localId} differs in amount (remote=${remote.amountPaisa}, local=${item.amountPaisa}).")
                    }
                    return@runCatching
                }
                val credit = db.getCredits(item.businessId).firstOrNull { it.id == item.localId }
                    ?: throw IllegalStateException("Local credit ${item.localId} missing from SQLite.")
                fs.commitWriteWithServerTimestamp(
                    relativeDocPath = "businesses/${item.businessId}/credit_transactions/${item.localId}",
                    fields = FirestoreRestCodec.creditToFields(credit)
                ).getOrThrow()
            }

            "RECOVERY" -> {
                val existingDoc = fs.getDocument("businesses/${item.businessId}/recovery_transactions/${item.localId}").getOrNull()
                if (existingDoc != null) {
                    val remote = FirestoreRestCodec.toRecoveryTx(existingDoc)
                    if (remote.idempotencyToken == item.idempotencyToken && item.idempotencyToken.isNotBlank()) {
                        return@runCatching
                    }
                    if (remote.amountPaisa != item.amountPaisa) {
                        throw IllegalStateException("CONFLICT: Remote recovery ${item.localId} differs in amount.")
                    }
                    return@runCatching
                }
                val rec = db.getRecoveries(item.businessId).firstOrNull { it.id == item.localId }
                    ?: throw IllegalStateException("Local recovery ${item.localId} missing from SQLite.")
                fs.commitWriteWithServerTimestamp(
                    relativeDocPath = "businesses/${item.businessId}/recovery_transactions/${item.localId}",
                    fields = FirestoreRestCodec.recoveryToFields(rec)
                ).getOrThrow()
            }

            "BOOKING" -> {
                val existingDoc = fs.getDocument("businesses/${item.businessId}/bookings/${item.localId}").getOrNull()
                if (existingDoc != null) {
                    val remote = FirestoreRestCodec.toBooking(existingDoc)
                    if (remote.idempotencyToken == item.idempotencyToken && item.idempotencyToken.isNotBlank()) {
                        return@runCatching
                    }
                    throw IllegalStateException("CONFLICT: Remote booking ${item.localId} exists with a different idempotency token.")
                }
                val bk = db.getBookings(item.businessId).firstOrNull { it.id == item.localId }
                    ?: throw IllegalStateException("Local booking ${item.localId} missing from SQLite.")
                fs.commitWriteWithServerTimestamp(
                    relativeDocPath = "businesses/${item.businessId}/bookings/${bk.id}",
                    fields = mapOf(
                        "businessId" to FirestoreRestCodec.stringVal(bk.businessId),
                        "bookingCode" to FirestoreRestCodec.stringVal(bk.bookingCode),
                        "shopId" to FirestoreRestCodec.stringVal(bk.shopId),
                        "shopUid" to FirestoreRestCodec.stringVal(bk.shopUid),
                        "shopName" to FirestoreRestCodec.stringVal(bk.shopName),
                        "bookerId" to FirestoreRestCodec.stringVal(bk.bookerId),
                        "bookerName" to FirestoreRestCodec.stringVal(bk.bookerName),
                        "productId" to FirestoreRestCodec.stringVal(bk.productId),
                        "productName" to FirestoreRestCodec.stringVal(bk.productName),
                        "quantity" to FirestoreRestCodec.intVal(bk.quantity.toLong()),
                        "unitPricePaisa" to FirestoreRestCodec.intVal(bk.unitPricePaisa),
                        "totalAmountPaisa" to FirestoreRestCodec.intVal(bk.totalAmountPaisa),
                        "status" to FirestoreRestCodec.stringVal(bk.status),
                        "notes" to FirestoreRestCodec.stringVal(bk.notes),
                        "bookingDateMillis" to FirestoreRestCodec.intVal(bk.bookingDateMillis),
                        "bookingDateFormatted" to FirestoreRestCodec.stringVal(bk.bookingDateFormatted),
                        "createdByAccountId" to FirestoreRestCodec.stringVal(bk.createdByAccountId),
                        "createdByName" to FirestoreRestCodec.stringVal(bk.createdByName),
                        "idempotencyToken" to FirestoreRestCodec.stringVal(bk.idempotencyToken),
                        "deviceId" to FirestoreRestCodec.stringVal(bk.deviceId)
                    )
                ).getOrThrow()
            }

            else -> throw IllegalStateException("Unsupported pending sync type: ${item.txType}")
        }
    }

    fun resolveConflictDiscardLocal(item: PendingSyncTransaction) {
        db.discardConflictedLocalItem(item.txType, item.localId)
        db.insertSyncHistory(
            SyncHistoryRecord(
                id = "sh_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                businessId = item.businessId,
                localTxId = item.localId,
                shopUid = item.shopUid,
                txType = item.txType,
                amountPaisa = item.amountPaisa,
                syncStatus = SyncStatus.SYNCED.key,
                message = "Conflict resolved by keeping cloud server record",
                syncedAtMillis = SharedPakistanTime.nowMillis()
            )
        )
    }

    fun requeueFailedOrConflictedItem(localId: String) {
        db.updatePendingSyncState(
            localId = localId,
            syncStatus = SyncStatus.PENDING_SYNC.key,
            errorMessage = "",
            retryCount = 0
        )
    }
}
