package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.AccountRole
import com.example.domain.model.Booker
import com.example.domain.model.CreditTransaction
import com.example.domain.model.FinancialEngine
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.Shop
import com.example.domain.model.ShopBalanceSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets

@Implements(PdfDocument::class)
class TestShadowPdfDocument {
    private var closed = false
    private val finishedPages = mutableListOf<PdfDocument.PageInfo>()

    @Implementation
    fun __constructor__() {
        lastCreatedPages.clear()
        closed = false
    }

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        check(!closed) { "document is closed!" }
        val bmp = Bitmap.createBitmap(pageInfo.pageWidth.coerceAtLeast(1), pageInfo.pageHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        return ReflectionHelpers.callConstructor(
            PdfDocument.Page::class.java,
            ReflectionHelpers.ClassParameter.from(Canvas::class.java, canvas),
            ReflectionHelpers.ClassParameter.from(PdfDocument.PageInfo::class.java, pageInfo)
        )
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {
        check(!closed) { "document is closed!" }
        finishedPages.add(page.info)
        lastCreatedPages.add(page.info)
    }

    @Implementation
    fun getPages(): MutableList<PdfDocument.PageInfo> = finishedPages

    @Implementation
    fun writeTo(out: OutputStream) {
        check(!closed) { "document is closed!" }
        val payload = "%PDF-1.4\n% HisabPro QA Test Pages=${finishedPages.size}\n%%EOF\n"
        out.write(payload.toByteArray(StandardCharsets.US_ASCII))
    }

    @Implementation
    fun close() {
        closed = true
    }

    companion object {
        val lastCreatedPages = mutableListOf<PdfDocument.PageInfo>()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [TestShadowPdfDocument::class])
class PdfGeneratorTest {

    @Before
    fun resetPdfShadow() {
        TestShadowPdfDocument.lastCreatedPages.clear()
        runCatching {
            val cache = ReflectionHelpers.getStaticField<MutableMap<*, *>>(FileProvider::class.java, "sCache")
            synchronized(cache) {
                cache.clear()
            }
        }
    }

    private fun latestCachedPdf(context: Context, prefix: String): File {
        val dir = File(context.cacheDir, "reports")
        return dir.listFiles()
            ?.filter { it.name.startsWith(prefix) && it.name.endsWith(".pdf") }
            ?.maxByOrNull { it.lastModified() }
            ?: error("Expected PDF with prefix $prefix in ${dir.absolutePath}")
    }

    private fun assertValidPdfBytes(file: File) {
        assertTrue("PDF should exist: ${file.name}", file.exists())
        assertTrue("PDF should be non-empty", file.length() > 20L)
        val header = file.inputStream().use { ins ->
            val buf = ByteArray(5)
            ins.read(buf)
            String(buf)
        }
        assertEquals("%PDF-", header)
    }

    @Test
    fun generateAllShopsPdf_forAdminAndBookerAndMultiPage_writesValidPdfs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rows50 = (1..50).map { i ->
            val credit = i * 100_000L
            val recovered = i * 40_000L
            ShopBalanceSummary(
                shop = Shop(
                    id = "s_$i",
                    shopUid = "SHP-${100 + i}",
                    shopName = "Pak Traders Branch $i",
                    bookerId = if (i % 2 == 0) "bk1" else "bk2",
                    bookerName = if (i % 2 == 0) "Ali Raza" else "Imran Khan",
                    ownerName = "Owner $i",
                    mobileNumber = "03001234567",
                    city = "Lahore"
                ),
                totalCreditPaisa = credit,
                totalRecoveredPaisa = recovered,
                outstandingPaisa = credit - recovered,
                firstCreditDateMillis = 1_000_000L,
                lastTransactionDateMillis = 2_000_000L,
                creditCount = 2,
                recoveryCount = 1
            )
        }

        // Verify grand totals math matches expected sums across 50 shops
        val grandCredit = rows50.sumOf { it.totalCreditPaisa }
        val grandRecovered = rows50.sumOf { it.totalRecoveredPaisa }
        val grandOutstanding = rows50.sumOf { it.outstandingPaisa }
        assertEquals(grandCredit - grandRecovered, grandOutstanding)
        assertEquals(127_500_000L, grandCredit)

        // 1. Admin All-Business Multi-Page PDF (>45 shops guarantees >= 2 pages)
        PdfGenerator.generateAllShopsStatementPdf(
            context = context,
            businessName = "Al Noor Wholesale",
            role = AccountRole.SUPER_ADMIN,
            booker = null,
            rows = rows50,
            dateRangeLabel = "All Time",
            totalBookersCount = 2
        )
        assertTrue("Admin 50-shop PDF must span >= 2 pages", TestShadowPdfDocument.lastCreatedPages.size >= 2)
        val adminMultiPagePdf = latestCachedPdf(context, "HisabPro_All_Shops")
        assertValidPdfBytes(adminMultiPagePdf)

        // 2. Booker All-Shops PDF (filtered to Booker's assigned shops)
        val bookerRows = rows50.filter { it.shop.bookerId == "bk1" }
        val booker = Booker(id = "bk1", bookerCode = "BK-001", fullName = "Ali Raza")
        PdfGenerator.generateAllShopsStatementPdf(
            context = context,
            businessName = "Al Noor Wholesale",
            role = AccountRole.BOOKER,
            booker = booker,
            rows = bookerRows,
            dateRangeLabel = "This Month",
            totalBookersCount = 1
        )
        assertTrue("Booker PDF must render at least 1 page", TestShadowPdfDocument.lastCreatedPages.isNotEmpty())
        val bookerPdf = latestCachedPdf(context, "HisabPro_Booker_Shops")
        assertValidPdfBytes(bookerPdf)
    }

    @Test
    fun generateShopStatementPdf_multiPageAndDateRangeAndReceipt_writeValidPdfs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shop = Shop(
            id = "s_1",
            shopUid = "SHP001",
            shopName = "ABC Super Store",
            bookerId = "bk1",
            bookerName = "Ali Raza",
            ownerName = "Haji Akram",
            mobileNumber = "03001234567",
            city = "Karachi"
        )

        val now = PakistanTime.nowMillis()
        val credits = (1..30).map { idx ->
            CreditTransaction(
                id = "cr_$idx",
                txCode = "CR-$idx",
                shopId = "s_1",
                shopUid = "SHP001",
                shopName = "ABC Super Store",
                bookerId = "bk1",
                bookerName = "Ali Raza",
                amountPaisa = 500_000L,
                transactionKind = "CREDIT",
                invoiceNumber = "INV-$idx",
                description = "Stock Lot $idx",
                txDateMillis = now - (idx * 3_600_000L),
                txDateFormatted = PakistanTime.formatDate(now),
                txTimeFormatted = PakistanTime.formatTime(now)
            )
        }
        val recoveries = (1..25).map { idx ->
            RecoveryTransaction(
                id = "rc_$idx",
                txCode = "RC-$idx",
                shopId = "s_1",
                shopUid = "SHP001",
                shopName = "ABC Super Store",
                bookerId = "bk1",
                bookerName = "Ali Raza",
                amountPaisa = 250_000L,
                transactionKind = "RECOVERY",
                paymentMethod = "Easypaisa",
                referenceNumber = "EP-$idx",
                notes = "Installment $idx",
                txDateMillis = now - (idx * 3_600_000L),
                txDateFormatted = PakistanTime.formatDate(now),
                txTimeFormatted = PakistanTime.formatTime(now)
            )
        }

        // Daily, Monthly, and Custom range checks
        val (dayStart, dayEnd) = PakistanTime.dayRangeFor(now)
        val dailyReport = FinancialEngine.computePeriodReport(listOf(shop), credits, recoveries, dayStart, dayEnd)
        val (monthStart, monthEnd) = PakistanTime.monthRangeFor(now)
        val monthlyReport = FinancialEngine.computePeriodReport(listOf(shop), credits, recoveries, monthStart, monthEnd)
        assertTrue(monthlyReport.periodCreditPaisa >= dailyReport.periodCreditPaisa)

        // Multi-page Individual Shop PDF (55 total rows spans >= 2 pages)
        val summary = FinancialEngine.computeShopBalances(listOf(shop), credits, recoveries).first()
        assertEquals(30 * 500_000L - 25 * 250_000L, summary.outstandingPaisa)

        PdfGenerator.generateShopStatementPdf(
            context = context,
            businessName = "Al Noor Wholesale",
            summary = summary,
            credits = credits,
            recoveries = recoveries,
            dateRangeLabel = "Custom Range"
        )
        assertTrue("Individual Shop 55-line PDF must span >= 2 pages", TestShadowPdfDocument.lastCreatedPages.size >= 2)
        val shopPdf = latestCachedPdf(context, "HisabPro_Shop_SHP001")
        assertValidPdfBytes(shopPdf)

        // Payment Recovery Receipt PDF
        PdfGenerator.generateRecoveryReceiptPdf(
            context = context,
            businessName = "Al Noor Wholesale",
            tx = recoveries.first(),
            remainingOutstandingPaisa = summary.outstandingPaisa
        )
        assertEquals(1, TestShadowPdfDocument.lastCreatedPages.size)
        val receiptPdf = latestCachedPdf(context, "HisabPro_Receipt_RC-1")
        assertValidPdfBytes(receiptPdf)
    }

    @Test
    fun generateBookerAllShopsCreditPdf_writesMultiPageValidPdf() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val booker = Booker(
            id = "bk1",
            bookerCode = "BKR-901",
            fullName = "Ali Raza",
            mobileNumber = "03001112233"
        )
        val assigned = (1..35).map { i ->
            ShopBalanceSummary(
                shop = Shop(
                    id = "s_$i",
                    shopUid = "SHP-${200 + i}",
                    shopName = "Wholesale Mart $i",
                    bookerId = "bk1",
                    bookerName = "Ali Raza",
                    ownerName = "Owner $i",
                    mobileNumber = "03001234567",
                    city = "Lahore"
                ),
                totalCreditPaisa = 500_000L,
                totalRecoveredPaisa = 200_000L,
                outstandingPaisa = 300_000L,
                firstCreditDateMillis = 1_000_000L,
                lastTransactionDateMillis = 2_000_000L,
                creditCount = 1,
                recoveryCount = 1
            )
        }
        val credits = listOf(
            CreditTransaction(
                id = "c1",
                txCode = "CR-1",
                shopId = "s_1",
                shopUid = "SHP-201",
                shopName = "Wholesale Mart 1",
                bookerId = "bk1",
                bookerName = "Ali Raza",
                amountPaisa = 500_000L,
                transactionKind = "CREDIT",
                txDateMillis = 1_000_000L,
                txDateFormatted = "16 Nov 2026"
            )
        )
        val recoveries = listOf(
            RecoveryTransaction(
                id = "r1",
                txCode = "RC-1",
                shopId = "s_1",
                shopUid = "SHP-201",
                shopName = "Wholesale Mart 1",
                bookerId = "bk1",
                bookerName = "Ali Raza",
                amountPaisa = 200_000L,
                transactionKind = "RECOVERY",
                paymentMethod = "Cash",
                txDateMillis = 1_100_000L,
                txDateFormatted = "16 Nov 2026"
            )
        )

        PdfGenerator.generateBookerAllShopsCreditPdf(
            context = context,
            businessName = "Al Noor Wholesale",
            booker = booker,
            assignedShops = assigned,
            credits = credits,
            recoveries = recoveries
        )

        assertTrue("Booker combined PDF with 35 shops + ledger must span >= 2 pages", TestShadowPdfDocument.lastCreatedPages.size >= 2)
        val pdfFile = latestCachedPdf(context, "HisabPro_Booker_BKR-901_AllShops")
        assertValidPdfBytes(pdfFile)
    }
}
