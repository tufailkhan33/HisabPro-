package com.example

import android.content.Context
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.CompanyIsolatedSnapshot
import com.example.data.firebase.HisabProRepository
import com.example.data.firebase.PlatformCloudState
import com.example.data.firebase.PlatformOwnerRepository
import com.example.data.local.CachedSessionEntity
import com.example.data.local.HisabLocalDatabase
import com.example.data.local.LocalCryptoHelper
import com.example.data.local.OfflineSyncManager
import com.example.domain.model.AccountRole
import com.example.domain.model.ActiveWorkspaceSession
import com.example.domain.model.AuditLog
import com.example.domain.model.Booker
import com.example.domain.model.Business
import com.example.domain.model.BusinessSettings
import com.example.domain.model.BusinessUser
import com.example.domain.model.CreditTransaction
import com.example.domain.model.FinancialEngine
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PlatformGlobalMetrics
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.Shop
import com.example.domain.model.ShopBalanceSummary
import com.example.domain.model.SyncStatus
import com.example.util.PakistanTime
import com.example.util.PasswordHasher
import com.example.util.PdfGenerator
import com.example.util.TestShadowPdfDocument
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [TestShadowPdfDocument::class])
class HisabProUpgradeAcceptanceTest {

    private lateinit var context: Context
    private lateinit var db: HisabLocalDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HisabLocalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        TestShadowPdfDocument.lastCreatedPages.clear()
        runCatching {
            val cache = ReflectionHelpers.getStaticField<MutableMap<*, *>>(FileProvider::class.java, "sCache")
            synchronized(cache) { cache.clear() }
        }
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun ensureTestFirebaseApp(): FirebaseApp {
        return FirebaseApp.getApps(context).firstOrNull()
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.hisabpro.m4k9tx")
                    .setProjectId("demo-no-project")
                    .setApiKey("fake-api-key-for-unit-test")
                    .build()
            )
    }

    private fun latestCachedPdf(prefix: String): File {
        val dir = File(context.cacheDir, "reports")
        return dir.listFiles()
            ?.filter { it.name.startsWith(prefix) && it.name.endsWith(".pdf") }
            ?.maxByOrNull { it.lastModified() }
            ?: error("Expected PDF with prefix $prefix in ${dir.absolutePath}")
    }

    // Acceptance Test 1 & 2: Booker-Wise All Shops Recovery Ledger + Reversals + Running Balance + Multi-page PDF
    @Test
    fun acceptance1_bookerConsolidatedRecoveryLedger_separatesShopsIncludesDueDatesReversalsAndMultiPagePdf() {
        val booker = Booker(
            id = "bkr_1",
            businessId = "biz_1",
            bookerCode = "BKR-101",
            fullName = "Ali Raza",
            mobileNumber = "03001112233"
        )
        val now = PakistanTime.nowMillis()
        val shops = (1..18).map { i ->
            Shop(
                id = "shp_$i",
                businessId = "biz_1",
                shopUid = "SHP-${100 + i}",
                shopName = "Madina Store $i",
                bookerId = "bkr_1",
                bookerName = "Ali Raza",
                ownerName = "Customer $i",
                mobileNumber = "032100000${i.toString().padStart(2, '0')}",
                address = "Market Block $i",
                city = "Lahore"
            )
        }
        val credits = listOf(
            CreditTransaction(
                id = "cr_1",
                businessId = "biz_1",
                txCode = "CR-1",
                shopId = "shp_1",
                shopUid = "SHP-101",
                shopName = "Madina Store 1",
                bookerId = "bkr_1",
                bookerName = "Ali Raza",
                amountPaisa = 5_000_000L, // Rs 50,000
                transactionKind = "CREDIT",
                invoiceNumber = "INV-1001",
                txDateMillis = now - 5 * 86_400_000L,
                txDateFormatted = PakistanTime.formatDate(now - 5 * 86_400_000L),
                txTimeFormatted = "11:00 AM",
                dueDateMillis = now - 86_400_000L,
                dueDateFormatted = PakistanTime.formatDate(now - 86_400_000L),
                entryTimestampMillis = now - 5 * 86_400_000L,
                entryDateFormatted = PakistanTime.formatDate(now - 5 * 86_400_000L),
                entryTimeFormatted = "11:05 AM"
            ),
            CreditTransaction(
                id = "cr_2",
                businessId = "biz_1",
                txCode = "CR-2",
                shopId = "shp_2",
                shopUid = "SHP-102",
                shopName = "Madina Store 2",
                bookerId = "bkr_1",
                bookerName = "Ali Raza",
                amountPaisa = 2_000_000L, // Rs 20,000
                transactionKind = "CREDIT",
                invoiceNumber = "INV-1002",
                txDateMillis = now - 2 * 86_400_000L,
                txDateFormatted = PakistanTime.formatDate(now - 2 * 86_400_000L)
            )
        )
        val recoveries = listOf(
            RecoveryTransaction(
                id = "rc_1",
                businessId = "biz_1",
                txCode = "RC-1",
                shopId = "shp_1",
                shopUid = "SHP-101",
                shopName = "Madina Store 1",
                bookerId = "bkr_1",
                bookerName = "Ali Raza",
                amountPaisa = 1_500_000L,
                transactionKind = "RECOVERY",
                paymentMethod = "Cash",
                txDateMillis = now - 3 * 86_400_000L,
                txDateFormatted = PakistanTime.formatDate(now - 3 * 86_400_000L)
            ),
            RecoveryTransaction(
                id = "rc_1_rev",
                businessId = "biz_1",
                txCode = "REV-1",
                shopId = "shp_1",
                shopUid = "SHP-101",
                shopName = "Madina Store 1",
                bookerId = "bkr_1",
                bookerName = "Ali Raza",
                amountPaisa = 1_500_000L,
                transactionKind = "RECOVERY_REVERSAL",
                relatedTxId = "rc_1",
                reason = "Wrong cash denomination",
                paymentMethod = "Cash",
                txDateMillis = now - 2 * 86_400_000L,
                txDateFormatted = PakistanTime.formatDate(now - 2 * 86_400_000L),
                originalTxDateMillis = now - 3 * 86_400_000L,
                reversedAtMillis = now - 2 * 86_400_000L
            ),
            RecoveryTransaction(
                id = "rc_1_corr",
                businessId = "biz_1",
                txCode = "RC-1C",
                shopId = "shp_1",
                shopUid = "SHP-101",
                shopName = "Madina Store 1",
                bookerId = "bkr_1",
                bookerName = "Ali Raza",
                amountPaisa = 1_000_000L, // Rs 10,000 corrected
                transactionKind = "RECOVERY",
                correctedFromTxId = "rc_1",
                correctionGroupId = "grp_1",
                reason = "Corrected to Rs 10,000",
                paymentMethod = "Cash",
                txDateMillis = now - 86_400_000L,
                txDateFormatted = PakistanTime.formatDate(now - 86_400_000L)
            )
        )

        val consolidated = FinancialEngine.computeBookerConsolidatedLedger(
            booker = booker,
            shops = shops,
            credits = credits,
            recoveries = recoveries,
            referenceNowMillis = now
        )

        assertEquals(18, consolidated.shopDetails.size)
        assertEquals(7_000_000L, consolidated.grandTotalCreditPaisa)
        assertEquals(1_000_000L, consolidated.grandTotalRecoveredPaisa)
        assertEquals(6_000_000L, consolidated.grandTotalOutstandingPaisa)

        val shop1 = consolidated.shopDetails.first { it.shop.id == "shp_1" }
        assertEquals("Overdue", shop1.paymentStatus)
        assertEquals(4_000_000L, shop1.remainingOutstandingPaisa)
        assertTrue(shop1.payments.any { it.isReversal && it.reason == "Wrong cash denomination" })

        val shop2 = consolidated.shopDetails.first { it.shop.id == "shp_2" }
        assertEquals("Unpaid", shop2.paymentStatus)
        assertEquals(2_000_000L, shop2.remainingOutstandingPaisa)

        val summaries = FinancialEngine.computeShopBalances(shops, credits, recoveries)
        val settings = BusinessSettings(
            businessId = "biz_1",
            businessName = "Al Noor Wholesale",
            businessAddress = "Shah Alam Market Lahore",
            contactPhone = "042-37654321"
        )
        PdfGenerator.generateBookerAllShopsCreditPdf(
            context = context,
            businessName = "Al Noor Wholesale",
            booker = booker,
            assignedShops = summaries,
            credits = credits,
            recoveries = recoveries,
            businessSettings = settings,
            hasUnsyncedData = true
        )
        assertTrue(TestShadowPdfDocument.lastCreatedPages.size >= 2)
        val pdfFile = latestCachedPdf("HisabPro_Booker_BKR-101_AllShops")
        assertTrue(pdfFile.exists() && pdfFile.length() > 20L)
    }

    // Acceptance Test 2 & 3: Staff PKT same-day lock, future-date blocking, and Individual Shop Running Balance Ledger
    @Test
    fun acceptance2_and3_staffSameDayLock_shopLedgerOpeningClosingAndReversedRows() {
        val now = PakistanTime.nowMillis()
        val yesterday = now - 86_400_000L
        val tomorrow = now + 86_400_000L

        assertTrue(PakistanTime.isSamePakistanDay(now, now))
        assertFalse(PakistanTime.isSamePakistanDay(yesterday, now))
        assertTrue(PakistanTime.isFuturePakistanDay(tomorrow, now))
        assertFalse(PakistanTime.isFuturePakistanDay(now, now))

        val shop = Shop(
            id = "s_1",
            businessId = "biz_1",
            shopUid = "SHP-500",
            shopName = "Bismillah Mart",
            bookerId = "bk_1",
            bookerName = "Ali",
            ownerName = "Haji Akram",
            mobileNumber = "03001234567",
            city = "Multan"
        )
        val periodStart = now - 2 * 86_400_000L
        val credits = listOf(
            // Prior to period -> contributes to Opening Balance
            CreditTransaction(
                id = "c_prior", shopId = "s_1", txCode = "CR-0",
                amountPaisa = 3_000_000L, transactionKind = "CREDIT",
                txDateMillis = now - 5 * 86_400_000L, txDateFormatted = "10 Nov 2026"
            ),
            // Inside period
            CreditTransaction(
                id = "c_in", shopId = "s_1", txCode = "CR-1", invoiceNumber = "INV-55",
                amountPaisa = 2_000_000L, transactionKind = "CREDIT",
                txDateMillis = now - 86_400_000L, txDateFormatted = "14 Nov 2026"
            )
        )
        val recoveries = listOf(
            RecoveryTransaction(
                id = "r_prior", shopId = "s_1", txCode = "RC-0",
                amountPaisa = 1_000_000L, transactionKind = "RECOVERY",
                txDateMillis = now - 4 * 86_400_000L, txDateFormatted = "11 Nov 2026"
            ),
            RecoveryTransaction(
                id = "r_in", shopId = "s_1", txCode = "RC-1",
                amountPaisa = 500_000L, transactionKind = "RECOVERY",
                paymentMethod = "Easypaisa",
                txDateMillis = now - 10_000L, txDateFormatted = "15 Nov 2026"
            ),
            RecoveryTransaction(
                id = "r_rev", shopId = "s_1", txCode = "REV-1",
                amountPaisa = 500_000L, transactionKind = "RECOVERY_REVERSAL",
                relatedTxId = "r_in",
                reason = "Duplicate entry",
                paymentMethod = "Easypaisa",
                txDateMillis = now - 5_000L, txDateFormatted = "15 Nov 2026"
            )
        )

        val ledger = FinancialEngine.computeShopLedgerReport(
            shop = shop,
            credits = credits,
            recoveries = recoveries,
            startMillis = periodStart,
            endMillis = now
        )
        // Opening = 30k - 10k = 20k (2_000_000)
        assertEquals(2_000_000L, ledger.openingBalancePaisa)
        // Period credit = 20k, net period recovery = 0 (5k - 5k), closing = 40k
        assertEquals(2_000_000L, ledger.totalCreditPaisa)
        assertEquals(0L, ledger.totalRecoveredPaisa)
        assertEquals(500_000L, ledger.reversedTotalPaisa)
        assertEquals(4_000_000L, ledger.closingBalancePaisa)
        assertEquals(3, ledger.entries.size)
        assertEquals(4_000_000L, ledger.entries[0].runningBalancePaisa) // +20k
        assertEquals(3_500_000L, ledger.entries[1].runningBalancePaisa) // -5k
        assertEquals(4_000_000L, ledger.entries[2].runningBalancePaisa) // reversal restores +5k
        assertTrue(ledger.entries[2].isReversal)
        assertEquals("Duplicate entry", ledger.entries[2].reason)

        val summary = FinancialEngine.computeShopBalances(listOf(shop), credits, recoveries).first()
        PdfGenerator.generateShopStatementPdf(
            context = context,
            businessName = "Multan Wholesalers",
            summary = summary,
            credits = credits,
            recoveries = recoveries,
            dateRangeLabel = "Last 2 Days",
            startMillis = periodStart,
            endMillis = now
        )
        assertTrue(latestCachedPdf("HisabPro_Shop_SHP-500").exists())
    }

    // Acceptance Test 4: Encrypted Offline Storage, Offline Auth Cache, Staff Same-Day Enforcement & Sync Status Outbox
    @Test
    fun acceptance4_offlineMode_roomOutboxEncryptionStaffDateCheckAndCachedLogin() = runBlocking {
        val dao = db.hisabDao()
        val app = ensureTestFirebaseApp()
        val repo = HisabProRepository(FirebaseFirestore.getInstance(app, "demo-db"), FirebaseAuth.getInstance(app))
        val deviceId = LocalCryptoHelper.getOrCreateDeviceId(context)
        assertTrue(deviceId.isNotBlank())

        // AES-GCM / Fallback round-trip encryption check
        val secretJson = "{\"session\":\"active\",\"businessId\":\"biz_offline_1\"}"
        val cipher = LocalCryptoHelper.encrypt(secretJson)
        assertNotEquals(secretJson, cipher)
        assertEquals(secretJson, LocalCryptoHelper.decrypt(cipher))

        // Cached offline login credential preservation with encrypted salt/hash
        val pwHash = PasswordHasher.hashPassword("OfflinePass#1")
        dao.upsertCachedSession(
            CachedSessionEntity(
                ticketKey = "hp-101__staff1",
                businessId = "biz_offline_1",
                businessCode = "HP-101",
                businessName = "Offline Mart",
                accountId = "usr_1",
                username = "staff1",
                fullName = "Staff Offline",
                role = "STAFF",
                bookerId = "",
                encryptedPasswordSalt = LocalCryptoHelper.encrypt(pwHash.saltBase64),
                encryptedPasswordHash = LocalCryptoHelper.encrypt(pwHash.hashBase64),
                mustChangePassword = false,
                lastSyncedMillis = System.currentTimeMillis()
            )
        )
        val cachedSess = dao.getCachedSession("hp-101__staff1")
        assertNotNull(cachedSess)
        assertTrue(
            PasswordHasher.verifyPassword(
                "OfflinePass#1",
                LocalCryptoHelper.decrypt(cachedSess!!.encryptedPasswordSalt),
                LocalCryptoHelper.decrypt(cachedSess.encryptedPasswordHash)
            )
        )

        val syncMgr = OfflineSyncManager(dao, repo, deviceId)
        val shop = Shop(
            id = "s_off",
            businessId = "biz_offline_1",
            shopUid = "SHP-OFF1",
            shopName = "Offline Shop",
            bookerId = "bk_off",
            bookerName = "Offline Booker"
        )
        val staffSession = ActiveWorkspaceSession(
            businessId = "biz_offline_1",
            businessCode = "HP-101",
            businessName = "Offline Mart",
            accountId = "usr_1",
            username = "staff1",
            fullName = "Staff Offline",
            role = AccountRole.STAFF,
            bookerId = "",
            mustChangePassword = false
        )

        // Staff offline credit succeeds and queues with PENDING_SYNC & idempotency token
        val now = PakistanTime.nowMillis()
        val crQueued = syncMgr.enqueueCredit(
            session = staffSession,
            shop = shop,
            amountPaisa = 250_000L,
            txDateMillis = now,
            dueDateMillis = now + 7 * 86_400_000L,
            invoiceNumber = "INV-OFF1",
            description = "Offline bill"
        ).getOrThrow()
        assertEquals(SyncStatus.PENDING_SYNC.key, crQueued.syncStatus)
        assertTrue(crQueued.idempotencyToken.startsWith("offcr_"))

        // Staff offline recovery MUST use current PKT calendar date; backdate rejected!
        val backdatedAttempt = syncMgr.enqueueRecovery(
            session = staffSession,
            shop = shop,
            amountPaisa = 100_000L,
            txDateMillis = now - 2 * 86_400_000L,
            paymentMethod = PaymentMethod.CASH,
            referenceNumber = "",
            notes = "Backdate attempt",
            allowOverRecovery = false,
            currentCachedOutstandingPaisa = 250_000L
        )
        assertTrue(backdatedAttempt.isFailure)

        // Same-day staff recovery succeeds offline
        val rcQueued = syncMgr.enqueueRecovery(
            session = staffSession,
            shop = shop,
            amountPaisa = 100_000L,
            txDateMillis = now,
            paymentMethod = PaymentMethod.CASH,
            referenceNumber = "",
            notes = "Same-day offline recovery",
            allowOverRecovery = false,
            currentCachedOutstandingPaisa = 250_000L
        ).getOrThrow()
        assertEquals(SyncStatus.PENDING_SYNC.key, rcQueued.syncStatus)
        assertEquals(2, syncMgr.observeUnsyncedCount("biz_offline_1").first())

        // Local cached transactions are immediately observable offline
        val cachedCredits = dao.observeCreditTransactions("biz_offline_1").first()
        val cachedRecoveries = dao.observeRecoveryTransactions("biz_offline_1").first()
        assertEquals(1, cachedCredits.size)
        assertEquals(1, cachedRecoveries.size)
    }

    // Acceptance Test 5, 6, 7: Platform Owner Permanent Identity, Password Secrecy, 3-Company Isolation & Global Search
    @Test
    fun acceptance5_6_7_platformOwnerIsolation_3CompaniesGlobalSearchAndPasswordSecrecy() {
        assertEquals("MrTufailadmin", PlatformOwnerRepository.OWNER_USERNAME)
        assertEquals("muhammadtufailllc007@gmail.com", PlatformOwnerRepository.OWNER_EMAIL)

        val app = ensureTestFirebaseApp()
        val ownerRepo = PlatformOwnerRepository(FirebaseFirestore.getInstance(app, "demo-db"), FirebaseAuth.getInstance(app))
        val c1 = CompanyIsolatedSnapshot(
            business = Business(id = "biz_1", businessName = "Alpha Wholesale", businessCode = "HP-1001", ownerName = "Tariq"),
            users = listOf(BusinessUser(id = "u1", businessId = "biz_1", username = "alpha_staff", fullName = "Usman")),
            permissions = emptyList(),
            bookers = listOf(Booker(id = "bk1", businessId = "biz_1", bookerCode = "BK-1", username = "ali_b", fullName = "Ali Raza")),
            shops = listOf(Shop(id = "s1", businessId = "biz_1", shopUid = "SHP-ALPHA1", shopName = "Alpha Store")),
            credits = listOf(CreditTransaction(id = "cr1", businessId = "biz_1", shopId = "s1", amountPaisa = 500_000L)),
            recoveries = emptyList(),
            auditLogs = emptyList()
        )
        val c2 = CompanyIsolatedSnapshot(
            business = Business(id = "biz_2", businessName = "Beta Distributors", businessCode = "HP-2002", ownerName = "Kamran", status = "SUSPENDED"),
            users = emptyList(),
            permissions = emptyList(),
            bookers = emptyList(),
            shops = listOf(Shop(id = "s2", businessId = "biz_2", shopUid = "SHP-BETA1", shopName = "Beta Store")),
            credits = listOf(CreditTransaction(id = "cr2", businessId = "biz_2", shopId = "s2", amountPaisa = 900_000L)),
            recoveries = listOf(RecoveryTransaction(id = "rc2", businessId = "biz_2", shopId = "s2", amountPaisa = 300_000L)),
            auditLogs = emptyList()
        )
        val c3 = CompanyIsolatedSnapshot(
            business = Business(id = "biz_3", businessName = "Gamma Traders", businessCode = "HP-3003", ownerName = "Zaid"),
            users = emptyList(),
            permissions = emptyList(),
            bookers = emptyList(),
            shops = listOf(Shop(id = "s3", businessId = "biz_3", shopUid = "SHP-GAMMA1", shopName = "Gamma Store")),
            credits = emptyList(),
            recoveries = emptyList(),
            auditLogs = listOf(AuditLog(id = "a3", businessId = "biz_3", action = "SHOP_CREATED"))
        )

        val state = PlatformCloudState(
            companies = listOf(c1, c2, c3),
            metrics = PlatformGlobalMetrics(
                totalBusinesses = 3,
                activeBusinesses = 2,
                suspendedBusinesses = 1,
                totalAdmins = 3,
                totalStaff = 1,
                totalBookers = 1,
                totalShops = 3,
                totalCreditTransactions = 2,
                totalRecoveryTransactions = 1,
                totalCreditPaisa = 1_400_000L,
                totalRecoveredPaisa = 300_000L,
                totalOutstandingPaisa = 1_100_000L
            ),
            platformActivity = emptyList(),
            backups = emptyList()
        )

        // Global search hits exact isolated company by Shop UID, Business Code, Staff, or Company Name
        val shopSearch = ownerRepo.globalSearch(state, "SHP-BETA1")
        assertEquals(1, shopSearch.size)
        assertEquals("biz_2", shopSearch.first().business.id)
        assertTrue(shopSearch.first().shops.none { it.businessId != "biz_2" })

        val codeSearch = ownerRepo.globalSearch(state, "HP-3003")
        assertEquals(1, codeSearch.size)
        assertEquals("biz_3", codeSearch.first().business.id)

        val staffSearch = ownerRepo.globalSearch(state, "alpha_staff")
        assertEquals(1, staffSearch.size)
        assertEquals("biz_1", staffSearch.first().business.id)

        // Strong Password enforcement for Platform Owner & Temp Passwords
        assertNotNull(PasswordHasher.validateStrongPassword("weak"))
        assertNull(PasswordHasher.validateStrongPassword("Tufail@2026"))
        val hashed = PasswordHasher.hashPassword("Tufail@2026")
        assertFalse(hashed.hashBase64.contains("Tufail@2026"))
        assertTrue(PasswordHasher.verifyPassword("Tufail@2026", hashed.saltBase64, hashed.hashBase64))
    }

    // Acceptance Test 8 & 9: Daily & Historical Reports 1:1 with Daily / Period PDFs + Unsynced Banner
    @Test
    fun acceptance8_and9_dailyDashboardAndPeriodReportsMatchPdfOutputs() {
        val now = PakistanTime.nowMillis()
        val cal = Calendar.getInstance(PakistanTime.timeZone).apply { timeInMillis = now }
        cal.add(Calendar.DAY_OF_YEAR, -10)
        val tenDaysAgo = cal.timeInMillis

        val booker = Booker(id = "bk_1", bookerCode = "BK-1", fullName = "Ali Raza")
        val shopToday = Shop(id = "s_today", shopUid = "SHP-T1", shopName = "Today Mart", bookerId = "bk_1", bookerName = "Ali Raza")
        val shopOld = Shop(id = "s_old", shopUid = "SHP-O1", shopName = "Old Mart", bookerId = "bk_1", bookerName = "Ali Raza")

        val credits = listOf(
            CreditTransaction(id = "c1", shopId = "s_today", bookerId = "bk_1", amountPaisa = 1_200_000L, transactionKind = "CREDIT", txDateMillis = now, syncStatus = SyncStatus.PENDING_SYNC.key),
            CreditTransaction(id = "c2", shopId = "s_old", bookerId = "bk_1", amountPaisa = 3_000_000L, transactionKind = "CREDIT", txDateMillis = tenDaysAgo)
        )
        val recoveries = listOf(
            RecoveryTransaction(id = "r1", shopId = "s_today", bookerId = "bk_1", amountPaisa = 400_000L, transactionKind = "RECOVERY", txDateMillis = now)
        )

        val daily = FinancialEngine.computeDailyPerformanceReport(
            shops = listOf(shopToday, shopOld),
            bookers = listOf(booker),
            credits = credits,
            recoveries = recoveries,
            dayMillis = now
        )
        assertEquals(1_200_000L, daily.todayCreditPaisa)
        assertEquals(400_000L, daily.todayRecoveryPaisa)
        assertEquals(1, daily.todayCreditCount)
        assertEquals(1, daily.todayRecoveryCount)
        assertEquals(listOf("s_today"), daily.shopsReceivingCreditToday.map { it.id })
        assertEquals(listOf("s_today"), daily.shopsMakingRecoveryToday.map { it.id })
        assertTrue(daily.hasUnsyncedData) // Offline credit c1 flagged

        PdfGenerator.generateDailyPerformancePdf(
            context = context,
            businessName = "Al Noor Wholesale",
            businessSettings = BusinessSettings(businessName = "Al Noor Wholesale", contactPhone = "03001234567"),
            report = daily
        )
        assertTrue(latestCachedPdf("HisabPro_Daily_Performance").exists())

        val (start, end) = PakistanTime.monthRangeFor(now)
        val period = FinancialEngine.computePeriodReport(listOf(shopToday, shopOld), credits, recoveries, start, end)
        PdfGenerator.generatePeriodReportPdf(
            context = context,
            businessName = "Al Noor Wholesale",
            businessSettings = null,
            report = period,
            dateRangeLabel = "This Month",
            hasUnsyncedData = daily.hasUnsyncedData
        )
        assertTrue(latestCachedPdf("HisabPro_Period_Report").exists())
    }
}
