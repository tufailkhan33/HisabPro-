package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HisabLocalDao {

    // Businesses & Settings
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBusiness(business: CachedBusinessEntity)

    @Query("SELECT * FROM cached_businesses WHERE id = :businessId LIMIT 1")
    fun observeBusiness(businessId: String): Flow<CachedBusinessEntity?>

    @Query("SELECT * FROM cached_businesses WHERE id = :businessId LIMIT 1")
    suspend fun getBusiness(businessId: String): CachedBusinessEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: CachedBusinessSettingsEntity)

    @Query("SELECT * FROM cached_business_settings WHERE businessId = :businessId LIMIT 1")
    fun observeSettings(businessId: String): Flow<CachedBusinessSettingsEntity?>

    // Bookers
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookers(items: List<CachedBookerEntity>)

    @Query("DELETE FROM cached_bookers WHERE businessId = :businessId")
    suspend fun clearBookersForBusiness(businessId: String)

    @Transaction
    suspend fun replaceBookers(businessId: String, items: List<CachedBookerEntity>) {
        clearBookersForBusiness(businessId)
        if (items.isNotEmpty()) upsertBookers(items)
    }

    @Query("SELECT * FROM cached_bookers WHERE businessId = :businessId ORDER BY fullName ASC")
    fun observeBookers(businessId: String): Flow<List<CachedBookerEntity>>

    // Staff Users & Permissions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStaffUsers(items: List<CachedStaffUserEntity>)

    @Query("DELETE FROM cached_staff_users WHERE businessId = :businessId")
    suspend fun clearStaffForBusiness(businessId: String)

    @Transaction
    suspend fun replaceStaffUsers(businessId: String, items: List<CachedStaffUserEntity>) {
        clearStaffForBusiness(businessId)
        if (items.isNotEmpty()) upsertStaffUsers(items)
    }

    @Query("SELECT * FROM cached_staff_users WHERE businessId = :businessId ORDER BY fullName ASC")
    fun observeStaffUsers(businessId: String): Flow<List<CachedStaffUserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPermission(permission: CachedPermissionEntity)

    @Query("SELECT * FROM cached_permissions WHERE businessId = :businessId AND userId = :userId LIMIT 1")
    fun observePermission(businessId: String, userId: String): Flow<CachedPermissionEntity?>

    @Query("SELECT * FROM cached_permissions WHERE businessId = :businessId AND userId = :userId LIMIT 1")
    suspend fun getPermission(businessId: String, userId: String): CachedPermissionEntity?

    // Shops
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShops(items: List<CachedShopEntity>)

    @Query("DELETE FROM cached_shops WHERE businessId = :businessId")
    suspend fun clearShopsForBusiness(businessId: String)

    @Transaction
    suspend fun replaceShops(businessId: String, items: List<CachedShopEntity>) {
        clearShopsForBusiness(businessId)
        if (items.isNotEmpty()) upsertShops(items)
    }

    @Query("SELECT * FROM cached_shops WHERE businessId = :businessId ORDER BY shopName ASC")
    fun observeShops(businessId: String): Flow<List<CachedShopEntity>>

    @Query("SELECT * FROM cached_shops WHERE businessId = :businessId AND bookerId = :bookerId ORDER BY shopName ASC")
    fun observeShopsForBooker(businessId: String, bookerId: String): Flow<List<CachedShopEntity>>

    @Query("SELECT * FROM cached_shops WHERE id = :shopId LIMIT 1")
    suspend fun getShopById(shopId: String): CachedShopEntity?

    // Credit Transactions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCreditTransactions(items: List<CachedCreditTxEntity>)

    @Query("DELETE FROM cached_credit_transactions WHERE businessId = :businessId AND syncStatus = 'SYNCED'")
    suspend fun clearSyncedCreditsForBusiness(businessId: String)

    @Transaction
    suspend fun replaceSyncedCredits(businessId: String, items: List<CachedCreditTxEntity>) {
        clearSyncedCreditsForBusiness(businessId)
        if (items.isNotEmpty()) upsertCreditTransactions(items)
    }

    @Query("SELECT * FROM cached_credit_transactions WHERE businessId = :businessId ORDER BY txDateMillis DESC")
    fun observeCreditTransactions(businessId: String): Flow<List<CachedCreditTxEntity>>

    @Query("SELECT * FROM cached_credit_transactions WHERE businessId = :businessId AND bookerId = :bookerId ORDER BY txDateMillis DESC")
    fun observeCreditTransactionsForBooker(businessId: String, bookerId: String): Flow<List<CachedCreditTxEntity>>

    @Query("UPDATE cached_credit_transactions SET syncStatus = :status WHERE id = :id")
    suspend fun updateCachedCreditSyncStatus(id: String, status: String)

    @Query("DELETE FROM cached_credit_transactions WHERE id = :id")
    suspend fun deleteCachedCreditById(id: String)

    // Recovery Transactions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecoveryTransactions(items: List<CachedRecoveryTxEntity>)

    @Query("DELETE FROM cached_recovery_transactions WHERE businessId = :businessId AND syncStatus = 'SYNCED'")
    suspend fun clearSyncedRecoveriesForBusiness(businessId: String)

    @Transaction
    suspend fun replaceSyncedRecoveries(businessId: String, items: List<CachedRecoveryTxEntity>) {
        clearSyncedRecoveriesForBusiness(businessId)
        if (items.isNotEmpty()) upsertRecoveryTransactions(items)
    }

    @Query("SELECT * FROM cached_recovery_transactions WHERE businessId = :businessId ORDER BY txDateMillis DESC")
    fun observeRecoveryTransactions(businessId: String): Flow<List<CachedRecoveryTxEntity>>

    @Query("SELECT * FROM cached_recovery_transactions WHERE businessId = :businessId AND bookerId = :bookerId ORDER BY txDateMillis DESC")
    fun observeRecoveryTransactionsForBooker(businessId: String, bookerId: String): Flow<List<CachedRecoveryTxEntity>>

    @Query("UPDATE cached_recovery_transactions SET syncStatus = :status WHERE id = :id")
    suspend fun updateCachedRecoverySyncStatus(id: String, status: String)

    @Query("DELETE FROM cached_recovery_transactions WHERE id = :id")
    suspend fun deleteCachedRecoveryById(id: String)

    // Audit Logs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAuditLogs(items: List<CachedAuditLogEntity>)

    @Query("DELETE FROM cached_audit_logs WHERE businessId = :businessId")
    suspend fun clearAuditLogsForBusiness(businessId: String)

    @Transaction
    suspend fun replaceAuditLogs(businessId: String, items: List<CachedAuditLogEntity>) {
        clearAuditLogsForBusiness(businessId)
        if (items.isNotEmpty()) upsertAuditLogs(items)
    }

    @Query("SELECT * FROM cached_audit_logs WHERE businessId = :businessId ORDER BY timestampMillis DESC")
    fun observeAuditLogs(businessId: String): Flow<List<CachedAuditLogEntity>>

    // Offline Outbox Queue
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPendingSync(item: PendingSyncTxEntity)

    @Query("SELECT * FROM pending_sync_transactions WHERE businessId = :businessId ORDER BY localDeviceTimestamp DESC")
    fun observePendingSync(businessId: String): Flow<List<PendingSyncTxEntity>>

    @Query(
        "SELECT * FROM pending_sync_transactions " +
            "WHERE businessId = :businessId AND syncStatus IN ('PENDING_SYNC', 'SYNC_FAILED') " +
            "ORDER BY localDeviceTimestamp ASC"
    )
    suspend fun getSyncableOutbox(businessId: String): List<PendingSyncTxEntity>

    @Query(
        "SELECT * FROM pending_sync_transactions " +
            "WHERE syncStatus IN ('PENDING_SYNC', 'SYNC_FAILED') " +
            "ORDER BY localDeviceTimestamp ASC"
    )
    suspend fun getAllSyncableOutbox(): List<PendingSyncTxEntity>

    @Query("SELECT * FROM pending_sync_transactions WHERE localId = :localId LIMIT 1")
    suspend fun getPendingSyncById(localId: String): PendingSyncTxEntity?

    @Query("DELETE FROM pending_sync_transactions WHERE localId = :localId")
    suspend fun deletePendingSync(localId: String)

    @Query("SELECT COUNT(*) FROM pending_sync_transactions WHERE businessId = :businessId AND syncStatus != 'SYNCED'")
    fun observeUnsyncedCount(businessId: String): Flow<Int>

    // Sync History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncHistory(item: SyncHistoryEntity)

    @Query("SELECT * FROM sync_history_records WHERE businessId = :businessId ORDER BY syncedAtMillis DESC LIMIT 100")
    fun observeSyncHistory(businessId: String): Flow<List<SyncHistoryEntity>>

    // Offline Encrypted Login Cache
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedSession(session: CachedSessionEntity)

    @Query("SELECT * FROM cached_offline_sessions WHERE ticketKey = :ticketKey LIMIT 1")
    suspend fun getCachedSession(ticketKey: String): CachedSessionEntity?

    @Query("DELETE FROM cached_offline_sessions WHERE businessId = :businessId AND accountId = :accountId")
    suspend fun clearCachedSessionForAccount(businessId: String, accountId: String)
}
