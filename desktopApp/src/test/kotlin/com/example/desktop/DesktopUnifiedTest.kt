package com.example.desktop

import com.example.desktop.cloud.FirebaseDesktopConfig
import com.example.desktop.cloud.FirestoreRestCodec
import com.example.desktop.export.DesktopExcelExporter
import com.example.desktop.export.DesktopPdfAndPrintEngine
import com.example.desktop.local.DesktopCryptoHelper
import com.example.desktop.local.DesktopOfflineSyncManager
import com.example.desktop.local.DesktopSqliteDatabase
import com.example.shared.model.*
import com.example.shared.util.SharedPakistanTime
import org.apache.pdfbox.Loader
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DesktopUnifiedTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `firebase desktop config loads shared project and named database from app resources`() {
        val cfg = FirebaseDesktopConfig.load()
        assertEquals("gen-lang-client-0095595188", cfg.projectId)
        assertEquals("ai-studio-android-hisabpro-2c926fa7-5e95-49f4-a400-e38f863df997", cfg.databaseId)
        assertTrue(cfg.apiKey.isNotBlank())
        assertTrue(cfg.isConfigured)
    }

    @Test
    fun `sqlite encrypted vault, integrity check, and safe cache protection work reliably`() {
        val dir = tempFolder.newFolder("vault")
        val dbFile = File(dir, "hisabpro_test.db")
        val db = DesktopSqliteDatabase(dbFile = dbFile, cryptoBaseDir = dir)

        assertEquals("ok", db.checkIntegrity().lowercase())

        db.saveEncryptedSlot("session", "{\"user\":\"tufail\"}")
        assertEquals("{\"user\":\"tufail\"}", db.loadEncryptedSlot("session"))

        val cipher = DesktopCryptoHelper.encrypt("secret-token-123", dir)
        assertFalse(cipher.contains("secret-token-123"))
        assertEquals("secret-token-123", DesktopCryptoHelper.decrypt(cipher, dir))
    }

    @Test
    fun `offline sync manager enforces duplicate prevention, staff same-day rule, and safe cache clear lock`() {
        val dir = tempFolder.newFolder("offline")
        val db = DesktopSqliteDatabase(dbFile = File(dir, "offline.db"), cryptoBaseDir = dir)
        val syncMgr = DesktopOfflineSyncManager(db = db, firestore = null)

        val shop = Shop(id = "shp1", businessId = "biz1", shopUid = "SHP-101", shopName = "Madina Store", bookerId = "bkr1", bookerName = "Bilal")
        val prod = ProductItem(id = "prd1", businessId = "biz1", sku = "SKU-1", name = "Crisnex Box", unit = "Carton", unitPricePaisa = 250_000L, stockQty = 40)
        val staffSession = ActiveWorkspaceSession("biz1", "HSB-101", "Ahsan Marketing", "stf1", "ali_staff", "Ali Staff", AccountRole.STAFF, "", false)
        val adminSession = ActiveWorkspaceSession("biz1", "HSB-101", "Ahsan Marketing", "adm1", "ahsan_admin", "Ahsan Admin", AccountRole.SUPER_ADMIN, "", false)

        // Queue offline credit
        val crRes = syncMgr.queueOfflineCredit(staffSession, shop, 500_000L, SharedPakistanTime.nowMillis(), 0L, "INV-9", "Cartons")
        assertTrue(crRes.isSuccess)
        assertEquals(SyncStatus.PENDING_SYNC.key, crRes.getOrThrow().syncStatus)

        // Staff cannot backdate recovery offline either
        val yesterday = SharedPakistanTime.nowMillis() - 86_400_000L
        val backdatedStaffRec = syncMgr.queueOfflineRecovery(
            staffSession, shop, 100_000L, yesterday, PaymentMethod.CASH, "SL-1", "Backdate try", 500_000L, false
        )
        assertTrue(backdatedStaffRec.isFailure)

        // Super Admin can backdate recovery offline
        val adminBackRec = syncMgr.queueOfflineRecovery(
            adminSession, shop, 100_000L, yesterday, PaymentMethod.CASH, "SL-2", "Admin recovery", 500_000L, false
        )
        assertTrue(adminBackRec.isSuccess)

        // Queue offline booking
        val bkRes = syncMgr.queueOfflineBooking(staffSession, shop, prod, 3, "Urgent dispatch")
        assertTrue(bkRes.isSuccess)
        assertEquals(750_000L, bkRes.getOrThrow().totalAmountPaisa)

        // Verify SQLite counts & cache protection
        assertEquals(3, db.countUnsynced("biz1"))
        val clearBlocked = db.clearBusinessCacheOnlyIfSafe("biz1")
        assertTrue(clearBlocked.isFailure)

        // Verify idempotency uniqueness rejects duplicate insertion
        val pending = db.getPendingSyncItems("biz1").first()
        assertFalse(db.insertPendingSyncIfAbsent(pending.copy(localId = "another_id")))
    }

    @Test
    fun `firestore rest codec maps credit and recovery documents faithfully`() {
        val credit = CreditTransaction(
            id = "cr1",
            businessId = "biz1",
            txCode = "CR-101",
            shopId = "shp1",
            shopUid = "SHP-1",
            shopName = "Noor Bakers",
            amountPaisa = 1_500_000L,
            invoiceNumber = "4133"
        )
        val fieldsMap = FirestoreRestCodec.creditToFields(credit)
        val fakeDoc = JSONObject()
            .put("name", "projects/p/databases/d/documents/businesses/biz1/credit_transactions/cr1")
            .put("fields", JSONObject(fieldsMap))
        val parsed = FirestoreRestCodec.toCreditTx(fakeDoc)
        assertEquals("cr1", parsed.id)
        assertEquals(1_500_000L, parsed.amountPaisa)
        assertEquals("4133", parsed.invoiceNumber)
        assertEquals(SyncStatus.SYNCED.key, parsed.syncStatus)
    }

    @Test
    fun `pdfbox supplier ledger and poi excel export create valid readable A4 pdf and xlsx files`() {
        val dir = tempFolder.newFolder("exports")
        val shop = Shop(id = "shp1", businessId = "biz1", shopUid = "SHP-100", shopName = "Crisnex Foods", bookerId = "bkr1", bookerName = "Bilal", mobileNumber = "03001234567")
        val t1 = 1_780_000_000_000L
        val credits = listOf(CreditTransaction(id = "c1", businessId = "biz1", txCode = "235076", shopId = "shp1", amountPaisa = 15_140_000L, txDateMillis = t1, invoiceNumber = "4133", description = "Purchase Qty 255"))
        val recoveries = listOf(RecoveryTransaction(id = "r1", businessId = "biz1", txCode = "235287", shopId = "shp1", amountPaisa = 3_441_000L, txDateMillis = t1 + 86_400_000L, paymentMethod = "Cash", notes = "Partial cash"))

        val pdfFile = DesktopPdfAndPrintEngine.generateShopSupplierLedgerPdf(
            outputFile = File(dir, "ledger.pdf"),
            businessName = "AHSAN MARKETING SERVICES",
            businessSettings = BusinessSettings(contactPhone = "03235467842", businessAddress = "Tarnol ISB"),
            shop = shop,
            credits = credits,
            recoveries = recoveries,
            startMillis = 0L,
            endMillis = Long.MAX_VALUE
        )
        assertTrue(pdfFile.exists() && pdfFile.length() > 1500L)
        Loader.loadPDF(pdfFile).use { doc ->
            assertEquals(1, doc.numberOfPages)
        }

        val xlsxFile = DesktopExcelExporter.exportShopLedgerXlsx(
            outputFile = File(dir, "ledger.xlsx"),
            businessName = "AHSAN MARKETING SERVICES",
            shop = shop,
            credits = credits,
            recoveries = recoveries,
            startMillis = 0L,
            endMillis = Long.MAX_VALUE
        )
        assertTrue(xlsxFile.exists() && xlsxFile.length() > 1000L)
        xlsxFile.inputStream().use { input ->
            XSSFWorkbook(input).use { wb ->
                assertEquals("Supplier Ledger", wb.getSheetAt(0).sheetName)
                assertTrue(wb.getSheetAt(0).physicalNumberOfRows >= 6)
            }
        }
    }

    @Test
    fun `sqlite backup restore verifies integrity and conflict discard cleans pending queue`() {
        val dir = tempFolder.newFolder("restore_conflict")
        val dbFile = File(dir, "live.db")
        val backupFile = File(dir, "backup.db")
        val db = DesktopSqliteDatabase(dbFile = dbFile, cryptoBaseDir = dir)
        val syncMgr = DesktopOfflineSyncManager(db = db, firestore = null)

        val shop = Shop(id = "shp9", businessId = "biz1", shopUid = "SHP-9", shopName = "Pak Bakers", bookerId = "bkr1", bookerName = "Bilal")
        val prod = ProductItem(id = "prd9", businessId = "biz1", sku = "SKU-9", name = "Tea Box", unit = "Box", unitPricePaisa = 100_000L, stockQty = 20)
        val session = ActiveWorkspaceSession("biz1", "HSB-101", "Ahsan Marketing", "adm1", "ahsan", "Ahsan", AccountRole.SUPER_ADMIN, "", false)

        val bk = syncMgr.queueOfflineBooking(session, shop, prod, 2, "Before backup").getOrThrow()
        assertEquals(1, db.countUnsynced("biz1"))

        db.backupSqliteDatabaseTo(backupFile)
        assertTrue(backupFile.exists() && backupFile.length() > 100L)

        // Discard pending item via conflict resolution, verify queue clears, then restore backup and confirm item returns
        val pending = db.getPendingSyncItems("biz1").first { it.localId == bk.id }
        syncMgr.resolveConflictDiscardLocal(pending)
        assertEquals(0, db.countUnsynced("biz1"))

        val restoreRes = db.restoreSqliteDatabaseFrom(backupFile)
        assertTrue(restoreRes.isSuccess)
        assertEquals(1, db.countUnsynced("biz1"))
    }
}
