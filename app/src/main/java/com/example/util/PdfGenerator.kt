package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.R
import com.example.domain.model.AccountRole
import com.example.domain.model.Booker
import com.example.domain.model.BookerConsolidatedLedger
import com.example.domain.model.BusinessSettings
import com.example.domain.model.CreditTransaction
import com.example.domain.model.DailyPerformanceReport
import com.example.domain.model.FinancialEngine
import com.example.domain.model.PeriodReportSummary
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.ShopBalanceSummary
import com.example.domain.model.ShopLedgerReport
import java.io.File
import java.io.FileOutputStream

object PdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_H = 28f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_H * 2)
    private const val FOOTER_TOP_Y = PAGE_HEIGHT - 38f

    private val NAVY = Color.parseColor("#172C46")
    private val NAVY_SOFT = Color.parseColor("#E8EEF5")
    private val LIGHT_GRAY = Color.parseColor("#F4F6F9")
    private val BORDER_GRAY = Color.parseColor("#CBD5E1")
    private val BLACK = Color.parseColor("#111111")
    private val SECONDARY = Color.parseColor("#4B5563")
    private val GREEN = Color.parseColor("#16834A")
    private val RED = Color.parseColor("#D94343")
    private val AMBER_BG = Color.parseColor("#FEF3C7")
    private val AMBER_TEXT = Color.parseColor("#92400E")

    private fun loadLogoBitmap(context: Context): Bitmap? {
        return try {
            BitmapFactory.decodeResource(context.resources, R.drawable.hisabpro_logo_rounded)
        } catch (_: Exception) {
            null
        }
    }

    private fun savePdfToCache(context: Context, pdfDocument: PdfDocument, fileName: String): Uri {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(reportsDir, safeName)
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun sharePdf(context: Context, uri: Uri, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun viewPdf(context: Context, uri: Uri) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(viewIntent)
        } catch (_: Exception) {
            sharePdf(context, uri, "Open PDF Report")
        }
    }

    fun downloadPdfToDevice(
        context: Context,
        cachedUri: Uri,
        suggestedFileName: String
    ): Result<String> = runCatching {
        val cleanBase = suggestedFileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val fileName = if (cleanBase.endsWith(".pdf", ignoreCase = true)) cleanBase else "$cleanBase.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/HisabPro")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val destUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create file in Downloads.")
            resolver.openOutputStream(destUri)?.use { out ->
                resolver.openInputStream(cachedUri)?.use { input ->
                    input.copyTo(out)
                } ?: throw IllegalStateException("Could not read generated PDF.")
            } ?: throw IllegalStateException("Could not open Downloads output stream.")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(destUri, values, null, null)
            "Downloads/HisabPro/$fileName"
        } else {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(publicDir, "HisabPro")
            val destFile = try {
                targetDir.mkdirs()
                File(targetDir, fileName).also { file ->
                    context.contentResolver.openInputStream(cachedUri)?.use { input ->
                        FileOutputStream(file).use { out -> input.copyTo(out) }
                    }
                }
            } catch (_: Exception) {
                val appDir = File(context.filesDir, "downloads/HisabPro").apply { mkdirs() }
                File(appDir, fileName).also { file ->
                    context.contentResolver.openInputStream(cachedUri)?.use { input ->
                        FileOutputStream(file).use { out -> input.copyTo(out) }
                    }
                }
            }
            if (destFile.absolutePath.contains(context.filesDir.absolutePath)) {
                "App Downloads/HisabPro/$fileName"
            } else {
                "Downloads/HisabPro/$fileName"
            }
        }
    }

    /**
     * Booker-Wise All Shops Consolidated Recovery Ledger PDF (Excel Ledger Reference Format)
     */
    fun generateBookerAllShopsCreditPdf(
        context: Context,
        businessName: String,
        booker: Booker,
        assignedShops: List<ShopBalanceSummary>,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        businessSettings: BusinessSettings? = null,
        hasUnsyncedData: Boolean = false,
        startMillis: Long = 0L,
        endMillis: Long = Long.MAX_VALUE
    ): Uri {
        val ledger: BookerConsolidatedLedger = FinancialEngine.computeBookerConsolidatedLedger(
            booker = booker,
            shops = assignedShops.map { it.shop },
            credits = credits,
            recoveries = recoveries,
            startMillis = startMillis,
            endMillis = endMillis
        )
        val effectiveUnsynced = hasUnsyncedData || ledger.hasUnsyncedData
        val bizAddress = businessSettings?.businessAddress.orEmpty()
        val bizContact = businessSettings?.contactPhone.orEmpty()
        val bizHeaderContact = listOfNotNull(
            bizContact.takeIf { it.isNotBlank() },
            bizAddress.takeIf { it.isNotBlank() }
        ).joinToString(" | ")

        val pdf = PdfDocument()
        val logo = loadLogoBitmap(context)
        val genDate = PakistanTime.formatDate()
        val genTime = PakistanTime.formatTime()

        val pages = mutableListOf<PdfDocument.Page>()
        var pageNum = 1
        var currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
        var canvas = currentPage.canvas
        pages.add(currentPage)

        drawStatementHeader(
            canvas = canvas,
            logo = logo,
            businessName = businessName,
            title = "BOOKER-WISE ALL SHOPS RECOVERY LEDGER",
            subtitle = "Booker: ${booker.fullName} (ID: ${booker.bookerCode}) | Mob: ${booker.mobileNumber}",
            contactLine = bizHeaderContact,
            genDate = genDate,
            genTime = genTime
        )

        var y = 122f
        if (effectiveUnsynced) {
            y = drawUnsyncedWarningBanner(canvas, y) + 8f
        }

        drawKpiSummaryBox(
            canvas = canvas,
            topY = y,
            items = listOf(
                "Assigned Shops" to "${ledger.shopDetails.size}",
                "Total Credit" to CurrencyFormatter.formatPkr(ledger.grandTotalCreditPaisa),
                "Total Recovered" to CurrencyFormatter.formatPkr(ledger.grandTotalRecoveredPaisa),
                "Total Receivables" to CurrencyFormatter.formatPkr(ledger.grandTotalOutstandingPaisa)
            )
        )
        y += 64f

        fun startNextPage(contTitle: String) {
            drawPageFooter(canvas, genDate, genTime, pageNum, 0)
            pdf.finishPage(currentPage)
            pageNum++
            currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
            canvas = currentPage.canvas
            pages.add(currentPage)
            drawCompactHeader(canvas, logo, businessName, contTitle)
            y = 74f
            if (effectiveUnsynced) {
                y = drawUnsyncedWarningBanner(canvas, y) + 6f
            }
        }

        fun ensureSpace(needed: Float, contTitle: String) {
            if (y + needed > FOOTER_TOP_Y) {
                startNextPage(contTitle)
            }
        }

        if (ledger.shopDetails.isEmpty()) {
            val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = SECONDARY
                textSize = 11f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("No shops currently assigned to ${booker.fullName}.", PAGE_WIDTH / 2f, y + 32f, emptyPaint)
        } else {
            val contTitle = "BOOKER RECOVERY LEDGER: ${booker.fullName} (${booker.bookerCode}) - CONT."
            ledger.shopDetails.forEachIndexed { shopIndex, detail ->
                val s = detail.shop
                ensureSpace(96f, contTitle)

                // Shop Identity Header Box (separately identifiable shop section)
                val shopBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY_SOFT }
                val shopBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = NAVY
                    style = Paint.Style.STROKE
                    strokeWidth = 0.8f
                }
                val shopRect = RectF(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 40f)
                canvas.drawRect(shopRect, shopBg)
                canvas.drawRect(shopRect, shopBorder)

                val boldShopP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = NAVY
                    textSize = 9.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val subShopP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = BLACK
                    textSize = 8f
                }
                val statusP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = when (detail.paymentStatus) {
                        "Paid" -> GREEN
                        "Overdue" -> RED
                        else -> NAVY
                    }
                    textSize = 8.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                }

                canvas.drawText("${shopIndex + 1}. ${s.shopName}  [UID: ${s.shopUid}]", MARGIN_H + 8f, y + 15f, boldShopP)
                canvas.drawText("STATUS: ${detail.paymentStatus.uppercase()}", PAGE_WIDTH - MARGIN_H - 8f, y + 15f, statusP)
                canvas.drawText(
                    "Customer: ${s.ownerName} | Mobile: ${s.mobileNumber} | ${s.address}, ${s.city}".take(86),
                    MARGIN_H + 8f,
                    y + 30f,
                    subShopP
                )
                y += 42f

                // Invoices / Bills Sub-table
                if (detail.invoices.isNotEmpty()) {
                    ensureSpace(34f, contTitle)
                    drawBookerInvoicesHeader(canvas, y)
                    y += 18f
                    detail.invoices.forEachIndexed { idxInv, inv ->
                        val rowH = if (inv.reason.isNotBlank()) 24f else 17f
                        ensureSpace(rowH, contTitle)
                        val rowBg = Paint().apply { color = if (idxInv % 2 == 0) Color.WHITE else LIGHT_GRAY }
                        canvas.drawRect(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + rowH, rowBg)
                        drawHorizontalRule(canvas, y + rowH)

                        val cP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLACK; textSize = 7.8f }
                        val amtP = Paint(cP).apply {
                            color = if (inv.isReversal) GREEN else BLACK
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            textAlign = Paint.Align.RIGHT
                        }
                        canvas.drawText(inv.voucherNumber.take(16), MARGIN_H + 6f, y + 11.5f, cP)
                        canvas.drawText(inv.invoiceDateFormatted.take(12), MARGIN_H + 92f, y + 11.5f, cP)
                        canvas.drawText(inv.entryDateTimeFormatted.take(20), MARGIN_H + 165f, y + 11.5f, cP)
                        canvas.drawText(inv.dueDateFormatted.take(12), MARGIN_H + 286f, y + 11.5f, cP)
                        val amtText = if (inv.isReversal) "-${CurrencyFormatter.formatPkr(-inv.amountPaisa)}" else CurrencyFormatter.formatPkr(inv.amountPaisa)
                        canvas.drawText(amtText, MARGIN_H + 412f, y + 11.5f, amtP)
                        canvas.drawText(if (inv.isReversal) "Reversed" else "Billed", PAGE_WIDTH - MARGIN_H - 6f, y + 11.5f, Paint(cP).apply { textAlign = Paint.Align.RIGHT })

                        if (inv.reason.isNotBlank()) {
                            val rP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; textSize = 7.2f }
                            canvas.drawText("Reason: ${inv.reason.take(75)}", MARGIN_H + 8f, y + 20.5f, rP)
                        }
                        y += rowH
                    }
                }

                // Recoveries / Payments Sub-table
                if (detail.payments.isNotEmpty()) {
                    ensureSpace(34f, contTitle)
                    drawBookerPaymentsHeader(canvas, y)
                    y += 18f
                    detail.payments.forEachIndexed { idxPay, pay ->
                        val isRedReversal = pay.isReversal || pay.isReversedOriginal
                        val extraInfo = buildString {
                            if (isRedReversal) {
                                append("Orig: ${pay.originalDateFormatted}")
                                if (pay.reversalDateFormatted.isNotBlank()) append(" | Rev: ${pay.reversalDateFormatted}")
                                if (pay.reason.isNotBlank()) append(" | Reason: ${pay.reason}")
                            }
                        }
                        val rowH = if (extraInfo.isNotBlank()) 26f else 17f
                        ensureSpace(rowH, contTitle)
                        val rowBg = Paint().apply { color = if (idxPay % 2 == 0) Color.WHITE else LIGHT_GRAY }
                        canvas.drawRect(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + rowH, rowBg)
                        drawHorizontalRule(canvas, y + rowH)

                        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = if (isRedReversal) RED else BLACK
                            textSize = 7.8f
                        }
                        val amtPaint = Paint(basePaint).apply {
                            color = if (isRedReversal) RED else GREEN
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            textAlign = Paint.Align.RIGHT
                        }
                        val balPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = BLACK
                            textSize = 7.8f
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            textAlign = Paint.Align.RIGHT
                        }

                        canvas.drawText(pay.voucherNumber.take(15), MARGIN_H + 6f, y + 11.5f, basePaint)
                        canvas.drawText(pay.paymentDateFormatted.take(14), MARGIN_H + 96f, y + 11.5f, basePaint)
                        canvas.drawText(if (pay.isReversal) "${pay.method} (REV)" else pay.method.take(14), MARGIN_H + 178f, y + 11.5f, basePaint)
                        val payAmtStr = if (pay.isReversal) {
                            "-${CurrencyFormatter.formatPkr(-pay.amountPaisa)}"
                        } else if (pay.isReversedOriginal) {
                            "-${CurrencyFormatter.formatPkr(pay.amountPaisa)} (REV)"
                        } else {
                            CurrencyFormatter.formatPkr(pay.amountPaisa)
                        }
                        canvas.drawText(payAmtStr, MARGIN_H + 372f, y + 11.5f, amtPaint)
                        canvas.drawText(CurrencyFormatter.formatPkr(pay.runningBalanceAfterPaisa), PAGE_WIDTH - MARGIN_H - 6f, y + 11.5f, balPaint)

                        if (extraInfo.isNotBlank()) {
                            val subRed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; textSize = 7.1f }
                            canvas.drawText(extraInfo.take(86), MARGIN_H + 8f, y + 22f, subRed)
                        }
                        y += rowH
                    }
                }

                // Shop Summary Subtotal Bar
                ensureSpace(24f, contTitle)
                val sumBg = Paint().apply { color = Color.parseColor("#E2E8F0") }
                canvas.drawRect(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 20f, sumBg)
                drawHorizontalRule(canvas, y + 20f)
                val sumLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = BLACK
                    textSize = 8f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val sumRem = Paint(sumLabel).apply {
                    color = if (detail.remainingOutstandingPaisa > 0L) RED else GREEN
                    textAlign = Paint.Align.RIGHT
                }
                canvas.drawText(
                    "Shop Totals — Billed: ${CurrencyFormatter.formatPkr(detail.totalCreditPaisa)}  |  Recovered: ${CurrencyFormatter.formatPkr(detail.totalRecoveredPaisa)}",
                    MARGIN_H + 8f,
                    y + 13f,
                    sumLabel
                )
                canvas.drawText(
                    "Remaining: ${CurrencyFormatter.formatPkr(detail.remainingOutstandingPaisa)}",
                    PAGE_WIDTH - MARGIN_H - 8f,
                    y + 13f,
                    sumRem
                )
                y += 28f
            }

            // Grand Totals across all shops
            ensureSpace(58f, contTitle)
            drawGrandTotalsBox(
                canvas = canvas,
                topY = y + 4f,
                totalShops = ledger.shopDetails.size,
                grandCredit = ledger.grandTotalCreditPaisa,
                grandRecovered = ledger.grandTotalRecoveredPaisa,
                grandOutstanding = ledger.grandTotalOutstandingPaisa
            )
        }

        drawPageFooter(canvas, genDate, genTime, pageNum, 0)
        pdf.finishPage(currentPage)

        val safeCode = booker.bookerCode.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return savePdfToCache(context, pdf, "HisabPro_Booker_${safeCode}_AllShops_${System.currentTimeMillis()}.pdf")
    }

    /**
     * Consolidated All-Shops Credit Statement PDF (Reports Tab & All Shops Export)
     */
    fun generateAllShopsStatementPdf(
        context: Context,
        businessName: String,
        role: AccountRole,
        booker: Booker?,
        rows: List<ShopBalanceSummary>,
        dateRangeLabel: String,
        totalBookersCount: Int,
        hasUnsyncedData: Boolean = false
    ): Uri {
        val pdf = PdfDocument()
        val logo = loadLogoBitmap(context)
        val genDate = PakistanTime.formatDate()
        val genTime = PakistanTime.formatTime()
        val isBooker = role == AccountRole.BOOKER

        val grandCredit = rows.sumOf { it.totalCreditPaisa }
        val grandRecovered = rows.sumOf { it.totalRecoveredPaisa }
        val grandOutstanding = rows.sumOf { it.outstandingPaisa }

        val rowHeight = 22f
        val page1StartY = if (hasUnsyncedData) 272f else 248f
        val contPageStartY = if (hasUnsyncedData) 130f else 108f
        val grandTotalBoxHeight = 54f

        val pages = mutableListOf<List<ShopBalanceSummary>>()
        var idx = 0
        var pageNum = 1
        while (idx < rows.size || pageNum == 1) {
            val startY = if (pageNum == 1) page1StartY else contPageStartY
            val available = FOOTER_TOP_Y - startY - 10f
            val maxRowsOnPage = maxOf(1, (available / rowHeight).toInt())
            val slice = if (idx < rows.size) {
                rows.subList(idx, minOf(idx + maxRowsOnPage, rows.size))
            } else {
                emptyList()
            }
            pages.add(slice)
            idx += slice.size
            pageNum++
            if (idx >= rows.size) break
        }

        val lastPageStartY = if (pages.size == 1) page1StartY else contPageStartY
        val lastRowsBottom = lastPageStartY + (pages.last().size * rowHeight) + 12f
        val needsExtraTotalPage = (lastRowsBottom + grandTotalBoxHeight) > FOOTER_TOP_Y
        val totalPages = pages.size + if (needsExtraTotalPage) 1 else 0

        var runningIndex = 1
        pages.forEachIndexed { pIdx, pageRows ->
            val currentPageNumber = pIdx + 1
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            if (currentPageNumber == 1) {
                drawStatementHeader(
                    canvas = canvas,
                    logo = logo,
                    businessName = businessName,
                    title = if (isBooker) "BOOKER ASSIGNED SHOPS STATEMENT" else "ALL SHOPS CREDIT STATEMENT",
                    subtitle = buildString {
                        append("Date Range: $dateRangeLabel")
                        if (isBooker && booker != null) {
                            append(" | Booker: ${booker.fullName} (${booker.bookerCode})")
                        }
                    },
                    contactLine = "",
                    genDate = genDate,
                    genTime = genTime
                )
                var topOffset = 124f
                if (hasUnsyncedData) {
                    topOffset = drawUnsyncedWarningBanner(canvas, topOffset) + 6f
                }
                drawKpiSummaryBox(
                    canvas = canvas,
                    topY = topOffset,
                    items = if (isBooker) {
                        listOf(
                            "Assigned Shops" to "${rows.size}",
                            "Total Credit" to CurrencyFormatter.formatPkr(grandCredit),
                            "Total Recovered" to CurrencyFormatter.formatPkr(grandRecovered),
                            "Outstanding" to CurrencyFormatter.formatPkr(grandOutstanding)
                        )
                    } else {
                        listOf(
                            "Bookers / Shops" to "$totalBookersCount / ${rows.size}",
                            "Total Credit" to CurrencyFormatter.formatPkr(grandCredit),
                            "Total Recovered" to CurrencyFormatter.formatPkr(grandRecovered),
                            "Outstanding" to CurrencyFormatter.formatPkr(grandOutstanding)
                        )
                    }
                )
                drawAllShopsTableHeader(canvas, page1StartY - 24f, isBooker)
            } else {
                drawCompactHeader(
                    canvas = canvas,
                    logo = logo,
                    businessName = businessName,
                    title = if (isBooker) "BOOKER SHOPS STATEMENT (CONT.)" else "ALL SHOPS CREDIT STATEMENT (CONT.)"
                )
                if (hasUnsyncedData) {
                    drawUnsyncedWarningBanner(canvas, 72f)
                }
                drawAllShopsTableHeader(canvas, contPageStartY - 24f, isBooker)
            }

            var y = if (currentPageNumber == 1) page1StartY else contPageStartY
            if (pageRows.isEmpty() && currentPageNumber == 1) {
                val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = SECONDARY
                    textSize = 11f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("No shops match the selected filter.", PAGE_WIDTH / 2f, y + 24f, emptyPaint)
            } else {
                pageRows.forEach { sb ->
                    drawAllShopsRow(canvas, y, rowHeight, runningIndex, sb, isBooker)
                    runningIndex++
                    y += rowHeight
                }
            }

            if (!needsExtraTotalPage && currentPageNumber == pages.size) {
                drawGrandTotalsBox(
                    canvas = canvas,
                    topY = y + 10f,
                    totalShops = rows.size,
                    grandCredit = grandCredit,
                    grandRecovered = grandRecovered,
                    grandOutstanding = grandOutstanding
                )
            }

            drawPageFooter(canvas, genDate, genTime, currentPageNumber, totalPages)
            pdf.finishPage(page)
        }

        if (needsExtraTotalPage) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, totalPages).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas
            drawCompactHeader(canvas, logo, businessName, "STATEMENT GRAND TOTALS")
            drawGrandTotalsBox(
                canvas = canvas,
                topY = 96f,
                totalShops = rows.size,
                grandCredit = grandCredit,
                grandRecovered = grandRecovered,
                grandOutstanding = grandOutstanding
            )
            drawPageFooter(canvas, genDate, genTime, totalPages, totalPages)
            pdf.finishPage(page)
        }

        val prefix = if (isBooker) "HisabPro_Booker_Shops" else "HisabPro_All_Shops"
        return savePdfToCache(context, pdf, "${prefix}_${System.currentTimeMillis()}.pdf")
    }

    /**
     * Individual Shop Supplier Ledger PDF matching Supplier Ledger reference table:
     * Title -> 2-Col Business & Shop Metadata -> Bordered 6-Col Grid (Voucher #, Dated, Description, Purchase, Payment/Recovery, Balance) + GRAND TOTAL row.
     */
    fun generateShopStatementPdf(
        context: Context,
        businessName: String,
        summary: ShopBalanceSummary,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        dateRangeLabel: String,
        businessSettings: BusinessSettings? = null,
        startMillis: Long = 0L,
        endMillis: Long = Long.MAX_VALUE
    ): Uri {
        val report: ShopLedgerReport = FinancialEngine.computeShopLedgerReport(
            shop = summary.shop,
            credits = credits,
            recoveries = recoveries,
            startMillis = startMillis,
            endMillis = endMillis
        )
        val bizAddress = businessSettings?.businessAddress.orEmpty()
        val bizContact = businessSettings?.contactPhone.orEmpty()
        val s = report.shop

        val pdf = PdfDocument()
        val logo = loadLogoBitmap(context)
        val genDate = PakistanTime.formatDashDate()
        val genTime = PakistanTime.formatTime()
        val formattedRange = if (startMillis > 0L && endMillis < Long.MAX_VALUE) {
            "${PakistanTime.formatDashDate(startMillis)} TO ${PakistanTime.formatDashDate(endMillis)}"
        } else {
            dateRangeLabel
        }

        val descTextWidth = (SHOP_COL_PURCHASE - SHOP_COL_DESC - 10f).toInt()
        val descPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLACK
            textSize = 7.7f
        }
        val redAuditPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED
            textSize = 7.0f
        }

        fun buildDescLayout(entry: com.example.domain.model.ShopLedgerEntry): Pair<StaticLayout, StaticLayout?> {
            val isRedRev = entry.isReversal || entry.isReversedOriginal
            val mainPaint = if (isRedRev) TextPaint(descPaint).apply { color = RED } else descPaint
            val mainLayout = StaticLayout.Builder
                .obtain(entry.description, 0, entry.description.length, mainPaint, descTextWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(1.5f, 1f)
                .build()

            val auditStr = if (isRedRev || entry.reason.isNotBlank()) {
                buildString {
                    if (entry.originalPaymentDateFormatted.isNotBlank()) append("Orig: ${entry.originalPaymentDateFormatted}")
                    if (entry.reversalDateFormatted.isNotBlank()) append(" | Rev: ${entry.reversalDateFormatted}")
                    if (entry.reason.isNotBlank()) {
                        if (isNotEmpty()) append(" | ")
                        append("Reason: ${entry.reason}")
                    }
                }
            } else ""

            val auditLayout = if (auditStr.isNotBlank()) {
                StaticLayout.Builder
                    .obtain(auditStr, 0, auditStr.length, redAuditPaint, descTextWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(1.2f, 1f)
                    .build()
            } else null

            return mainLayout to auditLayout
        }

        data class PreparedShopRow(
            val entry: com.example.domain.model.ShopLedgerEntry,
            val mainLayout: StaticLayout,
            val auditLayout: StaticLayout?,
            val rowHeight: Float
        )

        val prepared = report.entries.map { e ->
            val (mLayout, aLayout) = buildDescLayout(e)
            val totalTextH = mLayout.height + (aLayout?.height?.plus(3) ?: 0)
            val rowH = maxOf(26f, totalTextH + 10f)
            PreparedShopRow(e, mLayout, aLayout, rowH)
        }

        val page1HeaderBottom = if (report.hasUnsyncedData) 168f else 144f
        val page1TableHeaderY = page1HeaderBottom
        val page1StartY = page1TableHeaderY + 26f
        val contTableHeaderY = if (report.hasUnsyncedData) 96f else 74f
        val contStartY = contTableHeaderY + 26f
        val openingRowHeight = 22f
        val grandTotalRowHeight = 24f

        val pages = mutableListOf<List<PreparedShopRow>>()
        var curList = mutableListOf<PreparedShopRow>()
        var simY = page1StartY + openingRowHeight
        prepared.forEach { pr ->
            if (simY + pr.rowHeight > FOOTER_TOP_Y - 8f && curList.isNotEmpty()) {
                pages.add(curList)
                curList = mutableListOf()
                simY = contStartY
            }
            curList.add(pr)
            simY += pr.rowHeight
        }
        pages.add(curList)

        val needsExtraGrandTotalRowPage = (simY + grandTotalRowHeight) > FOOTER_TOP_Y
        val totalPages = pages.size + if (needsExtraGrandTotalRowPage) 1 else 0

        pages.forEachIndexed { pageIdx, pageRows ->
            val currentPage = pageIdx + 1
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPage).create())
            val canvas = page.canvas

            if (currentPage == 1) {
                drawSupplierLedgerHeader(
                    canvas = canvas,
                    logo = logo,
                    businessName = businessName,
                    bizPhone = bizContact,
                    bizAddress = bizAddress,
                    shopName = "${s.shopName} (${s.shopUid})",
                    shopPhone = s.mobileNumber,
                    shopOwner = s.ownerName,
                    dateRangeText = formattedRange
                )

                if (report.hasUnsyncedData) {
                    drawUnsyncedWarningBanner(canvas, 140f)
                }

                drawShopLedgerHeader(canvas, page1TableHeaderY)
                var y = page1StartY

                // Row 0: Opening Balance row inside the bordered table
                drawShopLedgerOpeningRow(
                    canvas = canvas,
                    topY = y,
                    rowHeight = openingRowHeight,
                    datedText = if (startMillis > 0L) PakistanTime.formatDashDate(startMillis) else "Start",
                    openingBalancePaisa = report.openingBalancePaisa
                )
                y += openingRowHeight

                if (pageRows.isEmpty()) {
                    val emptyBorder = Paint().apply {
                        color = TABLE_BORDER
                        style = Paint.Style.STROKE
                        strokeWidth = 0.8f
                    }
                    canvas.drawRect(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 28f, emptyBorder)
                    val emptyP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = SECONDARY
                        textSize = 9.5f
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("No transactions recorded in selected date range.", PAGE_WIDTH / 2f, y + 17f, emptyP)
                    y += 28f
                } else {
                    pageRows.forEach { pr ->
                        y = drawShopLedgerEntryRow(canvas, y, pr.rowHeight, pr.entry, pr.mainLayout, pr.auditLayout)
                    }
                }
                if (!needsExtraGrandTotalRowPage && currentPage == pages.size) {
                    drawShopLedgerGrandTotalRow(canvas, y, grandTotalRowHeight, report)
                }
            } else {
                drawCompactHeader(canvas, logo, businessName, "SUPPLIER LEDGER: ${s.shopName} (${s.shopUid}) - CONT.")
                if (report.hasUnsyncedData) {
                    drawUnsyncedWarningBanner(canvas, 68f)
                }
                drawShopLedgerHeader(canvas, contTableHeaderY)
                var y = contStartY
                pageRows.forEach { pr ->
                    y = drawShopLedgerEntryRow(canvas, y, pr.rowHeight, pr.entry, pr.mainLayout, pr.auditLayout)
                }
                if (!needsExtraGrandTotalRowPage && currentPage == pages.size) {
                    drawShopLedgerGrandTotalRow(canvas, y, grandTotalRowHeight, report)
                }
            }

            drawPageFooter(canvas, genDate, genTime, currentPage, totalPages)
            pdf.finishPage(page)
        }

        if (needsExtraGrandTotalRowPage) {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, totalPages).create())
            val canvas = page.canvas
            drawCompactHeader(canvas, logo, businessName, "SUPPLIER LEDGER GRAND TOTAL: ${s.shopName}")
            drawShopLedgerHeader(canvas, contTableHeaderY)
            drawShopLedgerGrandTotalRow(canvas, contStartY, grandTotalRowHeight, report)
            drawPageFooter(canvas, genDate, genTime, totalPages, totalPages)
            pdf.finishPage(page)
        }

        return savePdfToCache(context, pdf, "HisabPro_Shop_${s.shopUid}_Ledger_${System.currentTimeMillis()}.pdf")
    }

    /**
     * Daily Credit & Recovery Performance PDF matching screen metrics 1:1
     */
    fun generateDailyPerformancePdf(
        context: Context,
        businessName: String,
        businessSettings: BusinessSettings?,
        report: DailyPerformanceReport
    ): Uri {
        val pdf = PdfDocument()
        val logo = loadLogoBitmap(context)
        val genDate = PakistanTime.formatDate()
        val genTime = PakistanTime.formatTime()
        val contactLine = listOfNotNull(
            businessSettings?.contactPhone?.takeIf { it.isNotBlank() },
            businessSettings?.businessAddress?.takeIf { it.isNotBlank() }
        ).joinToString(" | ")

        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas

        drawStatementHeader(
            canvas = canvas,
            logo = logo,
            businessName = businessName,
            title = "DAILY CREDIT & RECOVERY REPORT",
            subtitle = "Pakistan Calendar Date: ${report.dateFormatted}",
            contactLine = contactLine,
            genDate = genDate,
            genTime = genTime
        )

        var y = 122f
        if (report.hasUnsyncedData) {
            y = drawUnsyncedWarningBanner(canvas, y) + 6f
        }

        drawKpiSummaryBox(
            canvas = canvas,
            topY = y,
            items = listOf(
                "Today Credit (${report.todayCreditCount})" to CurrencyFormatter.formatPkr(report.todayCreditPaisa),
                "Today Recovery (${report.todayRecoveryCount})" to CurrencyFormatter.formatPkr(report.todayRecoveryPaisa),
                "Credited / Rec. Shops" to "${report.shopsReceivingCreditToday.size} / ${report.shopsMakingRecoveryToday.size}",
                "Total Outstanding" to CurrencyFormatter.formatPkr(report.totalOutstandingPaisa)
            )
        )
        y += 70f

        // Booker Daily Performance Breakdown Table
        val secPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAVY
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("BOOKER-WISE DAILY BREAKDOWN", MARGIN_H, y, secPaint)
        y += 8f

        val hBg = Paint().apply { color = NAVY }
        canvas.drawRect(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 22f, hBg)
        val hP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val hR = Paint(hP).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Booker (Code)", MARGIN_H + 6f, y + 14f, hP)
        canvas.drawText("Shops", MARGIN_H + 170f, y + 14f, hR)
        canvas.drawText("Cr/Rc Shops", MARGIN_H + 240f, y + 14f, hR)
        canvas.drawText("Today Credit", MARGIN_H + 344f, y + 14f, hR)
        canvas.drawText("Today Rec.", MARGIN_H + 436f, y + 14f, hR)
        canvas.drawText("Outstanding", PAGE_WIDTH - MARGIN_H - 6f, y + 14f, hR)
        y += 22f

        val rP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLACK; textSize = 8f }
        val rRight = Paint(rP).apply { textAlign = Paint.Align.RIGHT }
        report.bookerBreakdown.forEachIndexed { idx, bk ->
            if (y + 20f <= FOOTER_TOP_Y - 110f) {
                val bg = Paint().apply { color = if (idx % 2 == 0) Color.WHITE else LIGHT_GRAY }
                canvas.drawRect(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + 20f, bg)
                drawHorizontalRule(canvas, y + 20f)
                canvas.drawText("${bk.booker.fullName} (${bk.booker.bookerCode})".take(26), MARGIN_H + 6f, y + 13f, rP)
                canvas.drawText("${bk.shopsAssignedCount}", MARGIN_H + 170f, y + 13f, rRight)
                canvas.drawText("${bk.creditShopsTodayCount} / ${bk.recoveryShopsTodayCount}", MARGIN_H + 240f, y + 13f, rRight)
                canvas.drawText(CurrencyFormatter.formatPkr(bk.todayCreditPaisa), MARGIN_H + 344f, y + 13f, rRight)
                canvas.drawText(CurrencyFormatter.formatPkr(bk.todayRecoveryPaisa), MARGIN_H + 436f, y + 13f, Paint(rRight).apply { color = GREEN })
                canvas.drawText(CurrencyFormatter.formatPkr(bk.totalOutstandingPaisa), PAGE_WIDTH - MARGIN_H - 6f, y + 13f, Paint(rRight).apply { color = RED })
                y += 20f
            }
        }

        y += 16f
        if (y + 50f <= FOOTER_TOP_Y) {
            canvas.drawText("SHOPS RECEIVING CREDIT TODAY (${report.shopsReceivingCreditToday.size})", MARGIN_H, y, secPaint)
            y += 14f
            val listText = report.shopsReceivingCreditToday.joinToString(", ") { "${it.shopName} (${it.shopUid})" }.ifBlank { "None recorded today" }
            canvas.drawText(listText.take(96), MARGIN_H, y, rP)
            y += 20f
        }
        if (y + 36f <= FOOTER_TOP_Y) {
            canvas.drawText("SHOPS MAKING RECOVERY TODAY (${report.shopsMakingRecoveryToday.size})", MARGIN_H, y, secPaint)
            y += 14f
            val listText = report.shopsMakingRecoveryToday.joinToString(", ") { "${it.shopName} (${it.shopUid})" }.ifBlank { "None recorded today" }
            canvas.drawText(listText.take(96), MARGIN_H, y, rP)
        }

        drawPageFooter(canvas, genDate, genTime, 1, 1)
        pdf.finishPage(page)
        return savePdfToCache(context, pdf, "HisabPro_Daily_Performance_${System.currentTimeMillis()}.pdf")
    }

    /**
     * Historical / Period Report PDF matching Weekly / Monthly / Custom Range Report screen 1:1
     */
    fun generatePeriodReportPdf(
        context: Context,
        businessName: String,
        businessSettings: BusinessSettings?,
        report: PeriodReportSummary,
        dateRangeLabel: String,
        hasUnsyncedData: Boolean = false
    ): Uri {
        val pdf = PdfDocument()
        val logo = loadLogoBitmap(context)
        val genDate = PakistanTime.formatDate()
        val genTime = PakistanTime.formatTime()
        val contactLine = listOfNotNull(
            businessSettings?.contactPhone?.takeIf { it.isNotBlank() },
            businessSettings?.businessAddress?.takeIf { it.isNotBlank() }
        ).joinToString(" | ")

        val rowHeight = 20f
        val page1StartY = if (hasUnsyncedData) 246f else 222f
        val contStartY = if (hasUnsyncedData) 126f else 104f
        val pages = mutableListOf<List<Int>>()
        var idx = 0
        var p = 1
        while (idx < report.shopRows.size || p == 1) {
            val startY = if (p == 1) page1StartY else contStartY
            val maxRows = maxOf(1, ((FOOTER_TOP_Y - startY - 12f) / rowHeight).toInt())
            val slice = if (idx < report.shopRows.size) {
                (idx until minOf(idx + maxRows, report.shopRows.size)).toList()
            } else emptyList()
            pages.add(slice)
            idx += slice.size
            p++
            if (idx >= report.shopRows.size) break
        }
        val totalPages = pages.size

        pages.forEachIndexed { pIdx, rowIndices ->
            val currentPage = pIdx + 1
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPage).create())
            val canvas = page.canvas

            if (currentPage == 1) {
                drawStatementHeader(
                    canvas = canvas,
                    logo = logo,
                    businessName = businessName,
                    title = "HISTORICAL PERIOD LEDGER REPORT",
                    subtitle = "$dateRangeLabel  •  ${report.creditTxCount} Credits / ${report.recoveryTxCount} Recoveries",
                    contactLine = contactLine,
                    genDate = genDate,
                    genTime = genTime
                )
                var topY = 122f
                if (hasUnsyncedData) {
                    topY = drawUnsyncedWarningBanner(canvas, topY) + 6f
                }
                drawKpiSummaryBox(
                    canvas = canvas,
                    topY = topY,
                    items = listOf(
                        "Period Credit" to CurrencyFormatter.formatPkr(report.periodCreditPaisa),
                        "Period Recovery" to CurrencyFormatter.formatPkr(report.periodRecoveredPaisa),
                        "As-Of Closing" to CurrencyFormatter.formatPkr(report.closingOutstandingAsOfEndDatePaisa),
                        "Current Live Bal." to CurrencyFormatter.formatPkr(report.currentOutstandingPaisa)
                    )
                )
                drawPeriodTableHeader(canvas, page1StartY - 22f)
            } else {
                drawCompactHeader(canvas, logo, businessName, "HISTORICAL REPORT ($dateRangeLabel) - CONT.")
                if (hasUnsyncedData) drawUnsyncedWarningBanner(canvas, 70f)
                drawPeriodTableHeader(canvas, contStartY - 22f)
            }

            var y = if (currentPage == 1) page1StartY else contStartY
            if (rowIndices.isEmpty() && currentPage == 1) {
                val emptyP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = SECONDARY
                    textSize = 11f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("No shops or movements in selected date range.", PAGE_WIDTH / 2f, y + 24f, emptyP)
            } else {
                rowIndices.forEachIndexed { localIdx, rIdx ->
                    val r = report.shopRows[rIdx]
                    val bg = Paint().apply { color = if (localIdx % 2 == 0) Color.WHITE else LIGHT_GRAY }
                    canvas.drawRect(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y + rowHeight, bg)
                    drawHorizontalRule(canvas, y + rowHeight)
                    val tP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLACK; textSize = 8f }
                    val rP = Paint(tP).apply { textAlign = Paint.Align.RIGHT }
                    canvas.drawText("${r.shop.shopName} (${r.shop.shopUid})".take(30), MARGIN_H + 6f, y + 13f, tP)
                    canvas.drawText(CurrencyFormatter.formatPkr(r.periodCreditPaisa), MARGIN_H + 258f, y + 13f, rP)
                    canvas.drawText(CurrencyFormatter.formatPkr(r.periodRecoveredPaisa), MARGIN_H + 350f, y + 13f, Paint(rP).apply { color = GREEN })
                    canvas.drawText(CurrencyFormatter.formatPkr(r.closingOutstandingAsOfEndDatePaisa), MARGIN_H + 446f, y + 13f, rP)
                    canvas.drawText(CurrencyFormatter.formatPkr(r.currentOutstandingPaisa), PAGE_WIDTH - MARGIN_H - 6f, y + 13f, Paint(rP).apply { color = RED })
                    y += rowHeight
                }
            }

            drawPageFooter(canvas, genDate, genTime, currentPage, totalPages)
            pdf.finishPage(page)
        }

        return savePdfToCache(context, pdf, "HisabPro_Period_Report_${System.currentTimeMillis()}.pdf")
    }

    /**
     * Payment Receipt PDF
     */
    fun generateRecoveryReceiptPdf(
        context: Context,
        businessName: String,
        tx: RecoveryTransaction,
        remainingOutstandingPaisa: Long
    ): Uri {
        val pdf = PdfDocument()
        val logo = loadLogoBitmap(context)
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, 520, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val isRev = tx.transactionKind == "RECOVERY_REVERSAL"
        drawStatementHeader(
            canvas = canvas,
            logo = logo,
            businessName = businessName,
            title = if (isRev) "REVERSAL AUDIT RECEIPT" else "PAYMENT RECOVERY RECEIPT",
            subtitle = "Receipt No: ${tx.txCode}",
            contactLine = "",
            genDate = tx.txDateFormatted,
            genTime = tx.txTimeFormatted
        )

        val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIGHT_GRAY }
        canvas.drawRoundRect(RectF(MARGIN_H, 132f, PAGE_WIDTH - MARGIN_H, 444f), 10f, 10f, cardBg)

        val amountLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SECONDARY
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        val amountVal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isRev) RED else GREEN
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(if (isRev) "REVERSED RECOVERY AMOUNT" else "AMOUNT RECOVERED", PAGE_WIDTH / 2f, 162f, amountLabel)
        canvas.drawText(
            if (isRev) "-${CurrencyFormatter.formatPkr(tx.amountPaisa)}" else CurrencyFormatter.formatPkr(tx.amountPaisa),
            PAGE_WIDTH / 2f,
            192f,
            amountVal
        )

        val lineP = Paint().apply { color = BORDER_GRAY; strokeWidth = 1f }
        canvas.drawLine(MARGIN_H + 16f, 210f, PAGE_WIDTH - MARGIN_H - 16f, 210f, lineP)

        val rows = listOf(
            "Shop Name" to "${tx.shopName} (${tx.shopUid})",
            "Assigned Booker" to tx.bookerName,
            "Payment Method" to tx.paymentMethod,
            "Reference No" to tx.referenceNumber.ifBlank { "N/A" },
            "Date & Time" to "${tx.txDateFormatted} • ${tx.txTimeFormatted}",
            "Recorded By" to "${tx.createdByName} (${tx.createdByRole})",
            "Remaining Balance" to CurrencyFormatter.formatPkr(remainingOutstandingPaisa),
            "Notes / Reason" to tx.reason.ifBlank { tx.notes.ifBlank { "None" } }
        )

        val kP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SECONDARY; textSize = 10f }
        val vP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLACK
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        var y = 234f
        rows.forEach { (k, v) ->
            canvas.drawText(k, MARGIN_H + 18f, y, kP)
            canvas.drawText(v.take(44), PAGE_WIDTH - MARGIN_H - 18f, y, vP)
            y += 23f
        }

        val footP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SECONDARY
            textSize = 9f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("HisabPro • Har Udhaar Ka Poora Hisab • Verified Digital Receipt", PAGE_WIDTH / 2f, 488f, footP)

        pdf.finishPage(page)
        return savePdfToCache(context, pdf, "HisabPro_Receipt_${tx.txCode}.pdf")
    }

    // --- Drawing Helpers ---

    private fun drawStatementHeader(
        canvas: Canvas,
        logo: Bitmap?,
        businessName: String,
        title: String,
        subtitle: String,
        contactLine: String,
        genDate: String,
        genTime: String
    ) {
        val navyPaint = Paint().apply { color = NAVY }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 114f, navyPaint)

        if (logo != null) {
            val dest = RectF(MARGIN_H, 20f, MARGIN_H + 54f, 74f)
            canvas.drawBitmap(logo, Rect(0, 0, logo.width, logo.height), dest, null)
        }

        val leftX = if (logo != null) MARGIN_H + 66f else MARGIN_H
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 16.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E5E7EB")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 8.5f
        }
        val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 8f
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawText("HisabPro  |  $businessName".take(42), leftX, 34f, brandPaint)
        canvas.drawText(title, leftX, 52f, titlePaint)
        canvas.drawText(subtitle.take(78), leftX, 70f, subPaint)
        val line4 = contactLine.ifBlank { "Har Udhaar Ka Poora Hisab" }
        canvas.drawText(line4.take(78), leftX, 88f, subPaint)

        canvas.drawText("Generated: $genDate", PAGE_WIDTH - MARGIN_H, 34f, rightPaint)
        canvas.drawText("Time: $genTime PKT", PAGE_WIDTH - MARGIN_H, 48f, rightPaint)
    }

    private fun drawCompactHeader(canvas: Canvas, logo: Bitmap?, businessName: String, title: String) {
        val navyPaint = Paint().apply { color = NAVY }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 62f, navyPaint)
        if (logo != null) {
            canvas.drawBitmap(logo, Rect(0, 0, logo.width, logo.height), RectF(MARGIN_H, 12f, MARGIN_H + 38f, 50f), null)
        }
        val leftX = if (logo != null) MARGIN_H + 48f else MARGIN_H
        val p1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val p2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 9f
        }
        canvas.drawText("HisabPro • $businessName".take(48), leftX, 27f, p1)
        canvas.drawText(title.take(72), leftX, 45f, p2)
    }

    private fun drawUnsyncedWarningBanner(canvas: Canvas, topY: Float): Float {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AMBER_BG }
        val rect = RectF(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + 18f)
        canvas.drawRoundRect(rect, 4f, 4f, bg)
        val txtP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AMBER_TEXT
            textSize = 7.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "OFFLINE NOTICE: Includes local offline entries pending cloud synchronization.",
            PAGE_WIDTH / 2f,
            topY + 12f,
            txtP
        )
        return topY + 18f
    }

    private fun drawKpiSummaryBox(canvas: Canvas, topY: Float, items: List<Pair<String, String>>) {
        val boxHeight = 52f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIGHT_GRAY }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BORDER_GRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val rect = RectF(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + boxHeight)
        canvas.drawRoundRect(rect, 6f, 6f, bg)
        canvas.drawRoundRect(rect, 6f, 6f, border)

        val colW = CONTENT_WIDTH / items.size
        val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SECONDARY; textSize = 8f }
        val valP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLACK
            textSize = 10.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        items.forEachIndexed { i, (label, value) ->
            val x = MARGIN_H + (i * colW) + 8f
            canvas.drawText(label, x, topY + 19f, labelP)
            canvas.drawText(value.take(18), x, topY + 38f, valP)
        }
    }

    private fun drawBookerInvoicesHeader(canvas: Canvas, topY: Float) {
        val hPaint = Paint().apply { color = NAVY }
        canvas.drawRect(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + 18f, hPaint)
        val tP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 7.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rP = Paint(tP).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Invoice / Voucher", MARGIN_H + 6f, topY + 12f, tP)
        canvas.drawText("Bill Date", MARGIN_H + 92f, topY + 12f, tP)
        canvas.drawText("App Entry Date & Time", MARGIN_H + 165f, topY + 12f, tP)
        canvas.drawText("Due Date", MARGIN_H + 286f, topY + 12f, tP)
        canvas.drawText("Bill Amount", MARGIN_H + 412f, topY + 12f, rP)
        canvas.drawText("Entry", PAGE_WIDTH - MARGIN_H - 6f, topY + 12f, rP)
    }

    private fun drawBookerPaymentsHeader(canvas: Canvas, topY: Float) {
        val hPaint = Paint().apply { color = Color.parseColor("#243B55") }
        canvas.drawRect(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + 18f, hPaint)
        val tP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 7.6f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rP = Paint(tP).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Recovery Voucher", MARGIN_H + 6f, topY + 12f, tP)
        canvas.drawText("Recovery Date", MARGIN_H + 96f, topY + 12f, tP)
        canvas.drawText("Method", MARGIN_H + 178f, topY + 12f, tP)
        canvas.drawText("Recovered Amount", MARGIN_H + 372f, topY + 12f, rP)
        canvas.drawText("Running Bal.", PAGE_WIDTH - MARGIN_H - 6f, topY + 12f, rP)
    }

    // Supplier Ledger exact 6-column boundaries spanning MARGIN_H (28f) .. PAGE_WIDTH - MARGIN_H (567f)
    private val SHOP_COL_VOUCHER = MARGIN_H // 28f
    private val SHOP_COL_DATED = 82f
    private val SHOP_COL_DESC = 148f
    private val SHOP_COL_PURCHASE = 365f
    private val SHOP_COL_PAYMENT = 433f
    private val SHOP_COL_BALANCE = 502f
    private val SHOP_COL_END = PAGE_WIDTH - MARGIN_H // 567f
    private val TABLE_BORDER = Color.parseColor("#334155")

    private fun drawSupplierLedgerHeader(
        canvas: Canvas,
        logo: Bitmap?,
        businessName: String,
        bizPhone: String,
        bizAddress: String,
        shopName: String,
        shopPhone: String,
        shopOwner: String,
        dateRangeText: String
    ) {
        // Top navy accent rule
        val accentPaint = Paint().apply { color = NAVY }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 5f, accentPaint)

        if (logo != null) {
            canvas.drawBitmap(logo, Rect(0, 0, logo.width, logo.height), RectF(MARGIN_H, 12f, MARGIN_H + 28f, 40f), null)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NAVY
            textSize = 17.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SUPPLIER LEDGER", PAGE_WIDTH / 2f, 32f, titlePaint)

        val brandTagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SECONDARY
            textSize = 7.5f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("HisabPro • PKR", PAGE_WIDTH - MARGIN_H, 28f, brandTagPaint)

        val rulePaint = Paint().apply { color = BLACK; strokeWidth = 1.1f }
        canvas.drawLine(MARGIN_H, 46f, PAGE_WIDTH - MARGIN_H, 46f, rulePaint)

        val colRightX = 284f
        val boldHeader = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLACK
            textSize = 8.4f
        }

        // Left column: Business Info
        canvas.drawText(businessName.uppercase().take(34), MARGIN_H, 64f, boldHeader)
        canvas.drawText("Ph. ${bizPhone.ifBlank { "N/A" }}", MARGIN_H, 80f, detailPaint)
        val addrText = bizAddress.ifBlank { "Address not set" }.uppercase()
        val addrLine1 = addrText.take(40)
        val addrLine2 = if (addrText.length > 40) addrText.drop(40).take(40) else ""
        canvas.drawText(addrLine1, MARGIN_H, 95f, detailPaint)
        if (addrLine2.isNotBlank()) {
            canvas.drawText(addrLine2, MARGIN_H, 108f, detailPaint)
        }

        // Right column: Shop / Customer Info & Date Range
        canvas.drawText(shopName.uppercase().take(36), colRightX, 64f, boldHeader)
        canvas.drawText("Ph. ${shopPhone.ifBlank { "N/A" }}  •  ${shopOwner.take(22)}", colRightX, 80f, detailPaint)
        canvas.drawText(dateRangeText.uppercase(), colRightX, 95f, detailPaint)
    }

    private fun drawShopLedgerGridLines(canvas: Canvas, topY: Float, bottomY: Float) {
        val gridPaint = Paint().apply {
            color = TABLE_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        canvas.drawRect(SHOP_COL_VOUCHER, topY, SHOP_COL_END, bottomY, gridPaint)
        listOf(SHOP_COL_DATED, SHOP_COL_DESC, SHOP_COL_PURCHASE, SHOP_COL_PAYMENT, SHOP_COL_BALANCE).forEach { x ->
            canvas.drawLine(x, topY, x, bottomY, gridPaint)
        }
    }

    private fun drawShopLedgerHeader(canvas: Canvas, topY: Float) {
        val headerH = 26f
        val bg = Paint().apply { color = NAVY_SOFT }
        canvas.drawRect(SHOP_COL_VOUCHER, topY, SHOP_COL_END, topY + headerH, bg)
        drawShopLedgerGridLines(canvas, topY, topY + headerH)

        val centerP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLACK
            textSize = 8.4f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Voucher #", (SHOP_COL_VOUCHER + SHOP_COL_DATED) / 2f, topY + 16f, centerP)
        canvas.drawText("Dated", (SHOP_COL_DATED + SHOP_COL_DESC) / 2f, topY + 16f, centerP)
        canvas.drawText("Description", (SHOP_COL_DESC + SHOP_COL_PURCHASE) / 2f, topY + 16f, centerP)
        canvas.drawText("Purchase", (SHOP_COL_PURCHASE + SHOP_COL_PAYMENT) / 2f, topY + 16f, centerP)
        canvas.drawText("Payment /", (SHOP_COL_PAYMENT + SHOP_COL_BALANCE) / 2f, topY + 11f, centerP)
        canvas.drawText("Recovery", (SHOP_COL_PAYMENT + SHOP_COL_BALANCE) / 2f, topY + 21f, centerP)
        canvas.drawText("Balance", (SHOP_COL_BALANCE + SHOP_COL_END) / 2f, topY + 16f, centerP)
    }

    private fun drawShopLedgerOpeningRow(
        canvas: Canvas,
        topY: Float,
        rowHeight: Float,
        datedText: String,
        openingBalancePaisa: Long
    ) {
        val bg = Paint().apply { color = Color.WHITE }
        canvas.drawRect(SHOP_COL_VOUCHER, topY, SHOP_COL_END, topY + rowHeight, bg)
        drawShopLedgerGridLines(canvas, topY, topY + rowHeight)

        val centerP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLACK
            textSize = 8f
            textAlign = Paint.Align.CENTER
        }
        val leftP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLACK; textSize = 8f }
        val rightP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLACK
            textSize = 8f
            textAlign = Paint.Align.RIGHT
        }
        val textY = topY + 14.5f
        canvas.drawText("0", (SHOP_COL_VOUCHER + SHOP_COL_DATED) / 2f, textY, centerP)
        canvas.drawText(datedText, (SHOP_COL_DATED + SHOP_COL_DESC) / 2f, textY, centerP)
        canvas.drawText("Opening Balance", SHOP_COL_DESC + 5f, textY, leftP)
        canvas.drawText("0.0", SHOP_COL_PAYMENT - 5f, textY, rightP)
        canvas.drawText("0.0", SHOP_COL_BALANCE - 5f, textY, rightP)
        canvas.drawText(CurrencyFormatter.formatLedgerAmount(openingBalancePaisa), SHOP_COL_END - 5f, textY, rightP)
    }

    private fun drawShopLedgerEntryRow(
        canvas: Canvas,
        topY: Float,
        rowHeight: Float,
        entry: com.example.domain.model.ShopLedgerEntry,
        mainLayout: StaticLayout,
        auditLayout: StaticLayout?
    ): Float {
        val isRedRev = entry.isReversal || entry.isReversedOriginal
        val bg = Paint().apply { color = Color.WHITE }
        canvas.drawRect(SHOP_COL_VOUCHER, topY, SHOP_COL_END, topY + rowHeight, bg)
        drawShopLedgerGridLines(canvas, topY, topY + rowHeight)

        val centerP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isRedRev) RED else BLACK
            textSize = 7.7f
            textAlign = Paint.Align.CENTER
        }
        val timeSubP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isRedRev) RED else SECONDARY
            textSize = 6.7f
            textAlign = Paint.Align.CENTER
        }
        val rPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLACK
            textSize = 7.9f
            textAlign = Paint.Align.RIGHT
        }

        canvas.drawText(entry.voucherNumber.take(10), (SHOP_COL_VOUCHER + SHOP_COL_DATED) / 2f, topY + 14f, centerP)
        canvas.drawText(entry.txDateFormatted.take(10), (SHOP_COL_DATED + SHOP_COL_DESC) / 2f, topY + 13f, centerP)
        val timeOnly = entry.entryDateTimeFormatted.removePrefix(entry.txDateFormatted).trim()
        if (timeOnly.isNotBlank()) {
            canvas.drawText(timeOnly.take(10), (SHOP_COL_DATED + SHOP_COL_DESC) / 2f, topY + 22f, timeSubP)
        }

        // Wrapped Description + Red audit detail inside cell
        canvas.save()
        canvas.translate(SHOP_COL_DESC + 5f, topY + 5f)
        mainLayout.draw(canvas)
        if (auditLayout != null) {
            canvas.translate(0f, mainLayout.height + 2f)
            auditLayout.draw(canvas)
        }
        canvas.restore()

        val purText = if (entry.creditPaisa != 0L) CurrencyFormatter.formatLedgerAmount(entry.creditPaisa) else "0.0"
        val payText = if (entry.recoveryPaisa != 0L) CurrencyFormatter.formatLedgerAmount(entry.recoveryPaisa) else "0.0"

        canvas.drawText(
            purText,
            SHOP_COL_PAYMENT - 5f,
            topY + 14f,
            Paint(rPaint).apply { color = if (entry.creditPaisa < 0L) GREEN else BLACK }
        )
        canvas.drawText(
            payText,
            SHOP_COL_BALANCE - 5f,
            topY + 14f,
            Paint(rPaint).apply {
                color = when {
                    isRedRev -> RED
                    entry.recoveryPaisa > 0L -> BLACK
                    else -> BLACK
                }
                if (isRedRev) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )
        canvas.drawText(
            CurrencyFormatter.formatLedgerAmount(entry.runningBalancePaisa),
            SHOP_COL_END - 5f,
            topY + 14f,
            rPaint
        )

        return topY + rowHeight
    }

    private fun drawShopLedgerGrandTotalRow(
        canvas: Canvas,
        topY: Float,
        rowHeight: Float,
        report: ShopLedgerReport
    ) {
        val bg = Paint().apply { color = NAVY_SOFT }
        canvas.drawRect(SHOP_COL_VOUCHER, topY, SHOP_COL_END, topY + rowHeight, bg)
        val gridPaint = Paint().apply {
            color = BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }
        // Merged left cells (Voucher + Dated + Description) + Purchase + Payment/Recovery + Balance
        canvas.drawRect(SHOP_COL_VOUCHER, topY, SHOP_COL_END, topY + rowHeight, gridPaint)
        listOf(SHOP_COL_PURCHASE, SHOP_COL_PAYMENT, SHOP_COL_BALANCE).forEach { x ->
            canvas.drawLine(x, topY, x, topY + rowHeight, gridPaint)
        }

        val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLACK
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valP = Paint(labelP).apply { textAlign = Paint.Align.RIGHT; textSize = 8.4f }
        val textY = topY + 15.5f
        canvas.drawText("GRAND TOTAL", SHOP_COL_VOUCHER + 6f, textY, labelP)
        canvas.drawText(CurrencyFormatter.formatLedgerAmount(report.totalCreditPaisa), SHOP_COL_PAYMENT - 5f, textY, valP)
        canvas.drawText(CurrencyFormatter.formatLedgerAmount(report.totalRecoveredPaisa), SHOP_COL_BALANCE - 5f, textY, valP)
        canvas.drawText(CurrencyFormatter.formatLedgerAmount(report.closingBalancePaisa), SHOP_COL_END - 5f, textY, valP)
    }

    private fun drawAllShopsTableHeader(canvas: Canvas, topY: Float, isBooker: Boolean) {
        val hPaint = Paint().apply { color = NAVY }
        canvas.drawRect(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + 22f, hPaint)
        val tP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rP = Paint(tP).apply { textAlign = Paint.Align.RIGHT }

        if (isBooker) {
            canvas.drawText("No.", MARGIN_H + 6f, topY + 14f, tP)
            canvas.drawText("Shop Name", MARGIN_H + 34f, topY + 14f, tP)
            canvas.drawText("Shop UID", MARGIN_H + 195f, topY + 14f, tP)
            canvas.drawText("Total Credit", MARGIN_H + 340f, topY + 14f, rP)
            canvas.drawText("Recovered", MARGIN_H + 432f, topY + 14f, rP)
            canvas.drawText("Remaining", PAGE_WIDTH - MARGIN_H - 6f, topY + 14f, rP)
        } else {
            canvas.drawText("No.", MARGIN_H + 6f, topY + 14f, tP)
            canvas.drawText("Booker", MARGIN_H + 30f, topY + 14f, tP)
            canvas.drawText("Shop Name", MARGIN_H + 114f, topY + 14f, tP)
            canvas.drawText("UID", MARGIN_H + 232f, topY + 14f, tP)
            canvas.drawText("Credit", MARGIN_H + 356f, topY + 14f, rP)
            canvas.drawText("Recovered", MARGIN_H + 440f, topY + 14f, rP)
            canvas.drawText("Remaining", PAGE_WIDTH - MARGIN_H - 6f, topY + 14f, rP)
        }
    }

    private fun drawAllShopsRow(
        canvas: Canvas,
        topY: Float,
        rowHeight: Float,
        index: Int,
        sb: ShopBalanceSummary,
        isBooker: Boolean
    ) {
        val bg = Paint().apply { color = if (index % 2 == 1) Color.WHITE else LIGHT_GRAY }
        canvas.drawRect(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + rowHeight, bg)
        drawHorizontalRule(canvas, topY + rowHeight)

        val tP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLACK; textSize = 8f }
        val rP = Paint(tP).apply { textAlign = Paint.Align.RIGHT }
        val remP = Paint(rP).apply {
            color = if (sb.outstandingPaisa > 0) RED else GREEN
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        if (isBooker) {
            canvas.drawText("$index", MARGIN_H + 6f, topY + 14f, tP)
            canvas.drawText(sb.shop.shopName.take(26), MARGIN_H + 34f, topY + 14f, tP)
            canvas.drawText(sb.shop.shopUid.take(14), MARGIN_H + 195f, topY + 14f, tP)
            canvas.drawText(CurrencyFormatter.formatPkr(sb.totalCreditPaisa), MARGIN_H + 340f, topY + 14f, rP)
            canvas.drawText(CurrencyFormatter.formatPkr(sb.totalRecoveredPaisa), MARGIN_H + 432f, topY + 14f, rP)
            canvas.drawText(CurrencyFormatter.formatPkr(sb.outstandingPaisa), PAGE_WIDTH - MARGIN_H - 6f, topY + 14f, remP)
        } else {
            canvas.drawText("$index", MARGIN_H + 6f, topY + 14f, tP)
            canvas.drawText(sb.shop.bookerName.take(14), MARGIN_H + 30f, topY + 14f, tP)
            canvas.drawText(sb.shop.shopName.take(20), MARGIN_H + 114f, topY + 14f, tP)
            canvas.drawText(sb.shop.shopUid.take(12), MARGIN_H + 232f, topY + 14f, tP)
            canvas.drawText(CurrencyFormatter.formatPkr(sb.totalCreditPaisa), MARGIN_H + 356f, topY + 14f, rP)
            canvas.drawText(CurrencyFormatter.formatPkr(sb.totalRecoveredPaisa), MARGIN_H + 440f, topY + 14f, rP)
            canvas.drawText(CurrencyFormatter.formatPkr(sb.outstandingPaisa), PAGE_WIDTH - MARGIN_H - 6f, topY + 14f, remP)
        }
    }

    private fun drawPeriodTableHeader(canvas: Canvas, topY: Float) {
        val hPaint = Paint().apply { color = NAVY }
        canvas.drawRect(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + 20f, hPaint)
        val tP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 7.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rP = Paint(tP).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Shop (UID)", MARGIN_H + 6f, topY + 13f, tP)
        canvas.drawText("Period Credit", MARGIN_H + 258f, topY + 13f, rP)
        canvas.drawText("Period Rec.", MARGIN_H + 350f, topY + 13f, rP)
        canvas.drawText("As-Of Closing", MARGIN_H + 446f, topY + 13f, rP)
        canvas.drawText("Current Bal.", PAGE_WIDTH - MARGIN_H - 6f, topY + 13f, rP)
    }

    private fun drawGrandTotalsBox(
        canvas: Canvas,
        topY: Float,
        totalShops: Int,
        grandCredit: Long,
        grandRecovered: Long,
        grandOutstanding: Long
    ) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NAVY }
        canvas.drawRoundRect(RectF(MARGIN_H, topY, PAGE_WIDTH - MARGIN_H, topY + 46f), 6f, 6f, bg)
        val lblP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CBD5E1"); textSize = 7.8f }
        val valP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val items = listOf(
            "TOTAL SHOPS" to "$totalShops",
            "GRAND CREDIT" to CurrencyFormatter.formatPkr(grandCredit),
            "GRAND RECOVERED" to CurrencyFormatter.formatPkr(grandRecovered),
            "GRAND RECEIVABLE" to CurrencyFormatter.formatPkr(grandOutstanding)
        )
        val colW = CONTENT_WIDTH / items.size
        items.forEachIndexed { idx, (l, v) ->
            val x = MARGIN_H + (idx * colW) + 8f
            canvas.drawText(l, x, topY + 17f, lblP)
            canvas.drawText(v, x, topY + 34f, valP)
        }
    }

    private fun drawHorizontalRule(canvas: Canvas, y: Float) {
        val lineP = Paint().apply { color = BORDER_GRAY; strokeWidth = 0.5f }
        canvas.drawLine(MARGIN_H, y, PAGE_WIDTH - MARGIN_H, y, lineP)
    }

    private fun drawPageFooter(
        canvas: Canvas,
        genDate: String,
        genTime: String,
        pageNumber: Int,
        totalPages: Int
    ) {
        val lineP = Paint().apply { color = BORDER_GRAY; strokeWidth = 0.8f }
        val yLine = PAGE_HEIGHT - 32f
        canvas.drawLine(MARGIN_H, yLine, PAGE_WIDTH - MARGIN_H, yLine, lineP)

        val footP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SECONDARY; textSize = 8f }
        val rightP = Paint(footP).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("HisabPro • Har Udhaar Ka Poora Hisab  |  $genDate $genTime PKT", MARGIN_H, PAGE_HEIGHT - 16f, footP)
        val pageLabel = if (totalPages > 0) "Page $pageNumber of $totalPages" else "Page $pageNumber"
        canvas.drawText(pageLabel, PAGE_WIDTH - MARGIN_H, PAGE_HEIGHT - 16f, rightP)
    }
}
