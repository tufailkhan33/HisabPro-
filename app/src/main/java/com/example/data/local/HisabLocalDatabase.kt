package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CachedBusinessEntity::class,
        CachedBusinessSettingsEntity::class,
        CachedBookerEntity::class,
        CachedStaffUserEntity::class,
        CachedPermissionEntity::class,
        CachedShopEntity::class,
        CachedCreditTxEntity::class,
        CachedRecoveryTxEntity::class,
        CachedAuditLogEntity::class,
        PendingSyncTxEntity::class,
        SyncHistoryEntity::class,
        CachedSessionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class HisabLocalDatabase : RoomDatabase() {
    abstract fun hisabDao(): HisabLocalDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Non-destructive migration preserving cached ledger, encrypted sessions, and pending sync outbox.
            }
        }

        @Volatile
        private var INSTANCE: HisabLocalDatabase? = null

        fun getInstance(context: Context): HisabLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HisabLocalDatabase::class.java,
                    "hisabpro_offline_cache.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
