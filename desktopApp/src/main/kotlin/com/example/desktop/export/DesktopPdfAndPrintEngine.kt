package com.example.desktop.export

import com.example.shared.engine.SharedFinancialEngine
import com.example.shared.model.*
import com.example.shared.util.SharedCurrencyFormatter
import com.example.shared.util.SharedPakistanTime
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.printing.PDFPageable
import java.awt.Color
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.print.PrinterJob
import java.io.File

/**
 * Windows Desktop A4 PDF Engine (Apache PDFBox 3) and native Print/Save-As integration.
 * Renders the 6-column SUPPLIER LEDGER (Voucher # | Dated | Description | Purchase | Payment / Recovery | Balance)
 * plus All Shops, Booker Consolidated, Daily Performance, and Recovery Receipt PDFs.
 */
object DesktopPdfAndPrintEngine {

    private val PAGE_W = PDRectangle.A4.width // 595.276f
    private val PAGE_H = PDRectangle.A4.height // 841.89f
    private const val MARGIN_X = 28f
    private const val CONTENT_W = 595.276f - 56f

    private val NAVY = Color(0x17, 0x2C, 0x46)
    private val NAVY_SOFT = Color(0xE8, 0xEE, 0xF5)
    private val BLACK = Color(0x11, 0x11, 0x11)
    private val GRAY = Color(0x4B, 0x55, 0x63)
    private val BORDER = Color(0x94, 0xA3, 0xB8)
    private val GREEN = Color(0x16, 0x83, 0x4A)
    private val RED = Color(0xC6, 0x28, 0x28)
    private val ALT_ROW = Color(0xF8, 0xFA, 0xFC)

    private val fontRegular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val fontBold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)

    private fun loadLogo(doc: PDDocument): PDImageXObject? = try {
        val bytes = DesktopPdfAndPrintEngine::class.java.classLoader
            ?.getResourceAsStream("hisabpro_logo.png")
            ?.use { it.readBytes() }
        if (bytes != null) PDImageXObject.createFromByteArray(doc, bytes, "hisabpro_logo.png") else null
    } catch (_: Exception) {
        null
    }

    private fun sanitize(text: String): String =
        text.replace("\r", " ").replace("\n", " ").map { ch -> if (ch.code in 32..126) ch else ' ' }.joinToString("")

    private fun wrapText(text: String, font: PDType1Font, size: Float, maxWidth: Float): List<String> {
        val clean = sanitize(text)
        if (clean.isBlank()) return listOf("")
        val words = clean.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = ""
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            val width = font.getStringWidth(candidate) / 1000f * size
            if (width <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = w
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines.ifEmpty { listOf("") }
    }

    private fun drawText(
        cs: PDPageContentStream,
        text: String,
        x: Float,
        y: Float,
        font: PDType1Font = fontRegular,
        size: Float = 9f,
        color: Color = BLACK,
        alignRight: Boolean = false
    ) {
        val clean = sanitize(text)
        val drawX = if (alignRight) {
            val w = font.getStringWidth(clean) / 1000f * size
            x - w
        } else x
        cs.beginText()
        cs.setFont(font, size)
        cs.setNonStrokingColor(color)
        cs.newLineAtOffset(drawX, y)
        cs.showText(clean)
        cs.endText()
    }

    private fun drawBorderedCell(
        cs: PDPageContentStream,
        x: Float,
        topY: Float,
        w: Float,
        h: Float,
        fillColor: Color? = null
    ) {
        val bottomY = topY - h
        if (fillColor != null) {
            cs.setNonStrokingColor(fillColor)
            cs.addRect(x, bottomY, w, h)
            cs.fill()
        }
        cs.setStrokingColor(BORDER)
        cs.setLineWidth(0.6f)
        cs.addRect(x, bottomY, w, h)
        cs.stroke()
    }

    fun generateShopSupplierLedgerPdf(
        outputFile: File,
        businessName: String,
        businessSettings: BusinessSettings?,
        shop: Shop,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        startMillis: Long,
        endMillis: Long
    ): File {
        val report = SharedFinancialEngine.computeShopLedgerReport(shop, credits, recoveries, startMillis, endMillis)
        val doc = PDDocument()
        val logo = loadLogo(doc)

        val colWidths = floatArrayOf(58f, 64f, 227f, 64f, 64f, 62f) // sum = 539 ~= CONTENT_W
        val headers = listOf("Voucher #", "Dated", "Description", "Purchase", "Payment / Recovery", "Balance")

        var page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        var cs = PDPageContentStream(doc, page)
        var pageNum = 1

        fun drawHeaderBlock(): Float {
            var y = PAGE_H - 34f
            if (logo != null) {
                cs.drawImage(logo, MARGIN_X, y - 30f, 32f, 32f)
            }
            drawText(cs, "SUPPLIER LEDGER", MARGIN_X + (CONTENT_W / 2f) - 55f, y - 8f, fontBold, 14f, GRAY)
            y -= 44f
            drawText(cs, businessName.uppercase(), MARGIN_X, y, fontBold, 11f, NAVY)
            drawText(cs, shop.shopName.uppercase(), MARGIN_X + 230f, y, fontBold, 11f, BLACK)
            y -= 13f
            val phone = businessSettings?.contactPhone?.takeIf { it.isNotBlank() } ?: "-"
            drawText(cs, "Ph. $phone", MARGIN_X, y, fontRegular, 8.5f, GRAY)
            drawText(cs, "Ph. ${shop.mobileNumber.ifBlank { "-" }}  |  Shop ID: ${shop.shopUid}", MARGIN_X + 230f, y, fontRegular, 8.5f, GRAY)
            y -= 12f
            val addr = businessSettings?.businessAddress?.takeIf { it.isNotBlank() } ?: "Booker: ${shop.bookerName}"
            drawText(cs, addr.take(55), MARGIN_X, y, fontRegular, 8.5f, GRAY)
            val fromStr = if (startMillis <= 0L) "START" else SharedPakistanTime.formatDashDate(startMillis)
            val toStr = if (endMillis >= Long.MAX_VALUE / 2) SharedPakistanTime.formatDashDate() else SharedPakistanTime.formatDashDate(endMillis)
            drawText(cs, "$fromStr TO $toStr", MARGIN_X + 230f, y, fontBold, 8.5f, NAVY)
            y -= 14f
            return y
        }

        fun drawTableHeader(topY: Float): Float {
            val h = 24f
            var x = MARGIN_X
            headers.forEachIndexed { i, title ->
                drawBorderedCell(cs, x, topY, colWidths[i], h, NAVY_SOFT)
                val lines = wrapText(title, fontBold, 8f, colWidths[i] - 6f)
                lines.take(2).forEachIndexed { li, line ->
                    drawText(cs, line, x + 4f, topY - 10f - (li * 9f), fontBold, 8f, NAVY)
                }
                x += colWidths[i]
            }
            return topY - h
        }

        var y = drawTableHeader(drawHeaderBlock())

        fun nextPage() {
            drawText(cs, "Generated by HisabPro Windows | ${SharedPakistanTime.formatDateTime()} | Page $pageNum", MARGIN_X, 20f, fontRegular, 7.5f, GRAY)
            cs.close()
            page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            cs = PDPageContentStream(doc, page)
            pageNum++
            drawText(cs, "$businessName — SUPPLIER LEDGER (${shop.shopName}) [Cont.]", MARGIN_X, PAGE_H - 28f, fontBold, 10f, NAVY)
            y = drawTableHeader(PAGE_H - 42f)
        }

        // Opening Balance Row
        run {
            val h = 18f
            val vals = listOf("0", if (startMillis > 0L) SharedPakistanTime.formatDashDate(startMillis) else "Opening", "Opening Balance", "0.0", "0.0", SharedCurrencyFormatter.formatLedgerAmount(report.openingBalancePaisa))
            var x = MARGIN_X
            vals.forEachIndexed { i, v ->
                drawBorderedCell(cs, x, y, colWidths[i], h, Color.WHITE)
                val isNum = i >= 3
                drawText(cs, v, if (isNum) x + colWidths[i] - 4f else x + 4f, y - 12f, fontRegular, 8f, BLACK, alignRight = isNum)
                x += colWidths[i]
            }
            y -= h
        }

        report.entries.forEachIndexed { idx, entry ->
            val descText = buildString {
                append(entry.description)
                if (entry.isReversal) append(" [REVERSED${if (entry.reason.isNotBlank()) " - ${entry.reason}" else ""}]")
            }
            val descLines = wrapText(descText, fontRegular, 7.8f, colWidths[2] - 8f)
            val rowH = (descLines.size.coerceAtLeast(1) * 10.5f + 8f).coerceAtLeast(18f)
            if (y - rowH < 58f) nextPage()

            val bg = when {
                entry.isReversal -> Color(0xFE, 0xF2, 0xF2)
                idx % 2 == 1 -> ALT_ROW
                else -> Color.WHITE
            }
            val rowColor = if (entry.isReversal) RED else BLACK
            val purchaseStr = when {
                entry.creditPaisa > 0L -> SharedCurrencyFormatter.formatLedgerAmount(entry.creditPaisa)
                entry.creditPaisa < 0L -> "-${SharedCurrencyFormatter.formatLedgerAmount(-entry.creditPaisa)}"
                else -> "0.0"
            }
            val recStr = when {
                entry.recoveryPaisa > 0L -> SharedCurrencyFormatter.formatLedgerAmount(entry.recoveryPaisa)
                entry.recoveryPaisa < 0L -> "-${SharedCurrencyFormatter.formatLedgerAmount(-entry.recoveryPaisa)}"
                else -> "0.0"
            }
            val balStr = SharedCurrencyFormatter.formatLedgerAmount(entry.runningBalancePaisa)

            var x = MARGIN_X
            colWidths.forEach { w -> drawBorderedCell(cs, x, y, w, rowH, bg); x += w }
            x = MARGIN_X
            drawText(cs, entry.voucherNumber.take(11), x + 4f, y - 12f, fontRegular, 7.8f, rowColor)
            x += colWidths[0]
            drawText(cs, entry.txDateFormatted, x + 4f, y - 12f, fontRegular, 7.8f, rowColor)
            x += colWidths[1]
            descLines.forEachIndexed { li, line ->
                drawText(cs, line, x + 4f, y - 11f - (li * 10.5f), if (entry.isReversal) fontBold else fontRegular, 7.8f, rowColor)
            }
            x += colWidths[2]
            drawText(cs, purchaseStr, x + colWidths[3] - 4f, y - 12f, fontRegular, 7.8f, rowColor, alignRight = true)
            x += colWidths[3]
            drawText(cs, recStr, x + colWidths[4] - 4f, y - 12f, fontRegular, 7.8f, rowColor, alignRight = true)
            x += colWidths[4]
            drawText(cs, balStr, x + colWidths[5] - 4f, y - 12f, fontBold, 7.8f, NAVY, alignRight = true)

            y -= rowH
        }

        if (y - 26f < 42f) nextPage()
        val totalH = 22f
        val grandW = colWidths[0] + colWidths[1] + colWidths[2]
        drawBorderedCell(cs, MARGIN_X, y, grandW, totalH, NAVY_SOFT)
        drawText(cs, "GRAND TOTAL", MARGIN_X + 6f, y - 14f, fontBold, 9f, NAVY)
        var gx = MARGIN_X + grandW
        drawBorderedCell(cs, gx, y, colWidths[3], totalH, NAVY_SOFT)
        drawText(cs, SharedCurrencyFormatter.formatLedgerAmount(report.totalCreditPaisa), gx + colWidths[3] - 4f, y - 14f, fontBold, 8.5f, BLACK, alignRight = true)
        gx += colWidths[3]
        drawBorderedCell(cs, gx, y, colWidths[4], totalH, NAVY_SOFT)
        drawText(cs, SharedCurrencyFormatter.formatLedgerAmount(report.totalRecoveredPaisa), gx + colWidths[4] - 4f, y - 14f, fontBold, 8.5f, GREEN, alignRight = true)
        gx += colWidths[4]
        drawBorderedCell(cs, gx, y, colWidths[5], totalH, NAVY_SOFT)
        drawText(cs, SharedCurrencyFormatter.formatLedgerAmount(report.closingBalancePaisa), gx + colWidths[5] - 4f, y - 14f, fontBold, 8.5f, NAVY, alignRight = true)

        drawText(cs, "Generated by HisabPro Windows | ${SharedPakistanTime.formatDateTime()} | Page $pageNum", MARGIN_X, 20f, fontRegular, 7.5f, GRAY)
        cs.close()
        outputFile.parentFile?.mkdirs()
        doc.save(outputFile)
        doc.close()
        return outputFile
    }

    fun generateAllShopsSummaryPdf(
        outputFile: File,
        businessName: String,
        summaries: List<ShopBalanceSummary>
    ): File {
        val doc = PDDocument()
        var page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        var cs = PDPageContentStream(doc, page)
        val colW = floatArrayOf(65f, 150f, 110f, 70f, 70f, 74f)
        var y = PAGE_H - 42f
        drawText(cs, "$businessName — ALL SHOPS SUMMARY", MARGIN_X, y, fontBold, 13f, NAVY)
        y -= 16f
        drawText(cs, "Generated: ${SharedPakistanTime.formatDateTime()} | Total Shops: ${summaries.size}", MARGIN_X, y, fontRegular, 8.5f, GRAY)
        y -= 18f

        fun drawHdr(topY: Float): Float {
            val h = 20f
            var x = MARGIN_X
            listOf("Shop ID", "Shop Name", "Booker", "Total Credit", "Recovered", "Outstanding").forEachIndexed { i, t ->
                drawBorderedCell(cs, x, topY, colW[i], h, NAVY_SOFT)
                drawText(cs, t, x + 4f, topY - 13f, fontBold, 8f, NAVY)
                x += colW[i]
            }
            return topY - h
        }
        y = drawHdr(y)
        summaries.forEachIndexed { idx, sb ->
            val h = 18f
            if (y - h < 45f) {
                cs.close()
                page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                cs = PDPageContentStream(doc, page)
                y = drawHdr(PAGE_H - 42f)
            }
            val bg = if (idx % 2 == 1) ALT_ROW else Color.WHITE
            var x = MARGIN_X
            colW.forEach { w -> drawBorderedCell(cs, x, y, w, h, bg); x += w }
            x = MARGIN_X
            drawText(cs, sb.shop.shopUid.take(12), x + 4f, y - 12f, fontRegular, 7.8f)
            x += colW[0]
            drawText(cs, sb.shop.shopName.take(28), x + 4f, y - 12f, fontBold, 7.8f)
            x += colW[1]
            drawText(cs, sb.shop.bookerName.take(20), x + 4f, y - 12f, fontRegular, 7.8f)
            x += colW[2]
            drawText(cs, SharedCurrencyFormatter.formatLedgerAmount(sb.totalCreditPaisa), x + colW[3] - 4f, y - 12f, fontRegular, 7.8f, BLACK, true)
            x += colW[3]
            drawText(cs, SharedCurrencyFormatter.formatLedgerAmount(sb.totalRecoveredPaisa), x + colW[4] - 4f, y - 12f, fontRegular, 7.8f, GREEN, true)
            x += colW[4]
            drawText(cs, SharedCurrencyFormatter.formatLedgerAmount(sb.outstandingPaisa), x + colW[5] - 4f, y - 12f, fontBold, 7.8f, if (sb.outstandingPaisa > 0) RED else NAVY, true)
            y -= h
        }
        cs.close()
        outputFile.parentFile?.mkdirs()
        doc.save(outputFile)
        doc.close()
        return outputFile
    }

    fun generateDailyPerformancePdf(
        outputFile: File,
        businessName: String,
        report: DailyPerformanceReport
    ): File {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val cs = PDPageContentStream(doc, page)
        var y = PAGE_H - 42f
        drawText(cs, "$businessName — DAILY PERFORMANCE REPORT", MARGIN_X, y, fontBold, 13f, NAVY)
        y -= 16f
        drawText(cs, "Date: ${report.dateFormatted} (PKT) | Generated: ${SharedPakistanTime.formatDateTime()}", MARGIN_X, y, fontRegular, 9f, GRAY)
        y -= 24f

        drawText(cs, "Today's Credit: ${SharedCurrencyFormatter.formatPkr(report.todayCreditPaisa)} (${report.todayCreditCount} entries)", MARGIN_X, y, fontBold, 10f, NAVY)
        y -= 16f
        drawText(cs, "Today's Recovery: ${SharedCurrencyFormatter.formatPkr(report.todayRecoveryPaisa)} (${report.todayRecoveryCount} entries)", MARGIN_X, y, fontBold, 10f, GREEN)
        y -= 16f
        drawText(cs, "Total Business Receivables: ${SharedCurrencyFormatter.formatPkr(report.totalOutstandingPaisa)}", MARGIN_X, y, fontBold, 10f, RED)
        y -= 24f

        val colW = floatArrayOf(150f, 65f, 105f, 105f, 114f)
        var x = MARGIN_X
        listOf("Booker", "Shops", "Today Credit", "Today Recovery", "Total Outstanding").forEachIndexed { i, t ->
            drawBorderedCell(cs, x, y, colW[i], 20f, NAVY_SOFT)
            drawText(cs, t, x + 4f, y - 13f, fontBold, 8.2f, NAVY)
            x += colW[i]
        }
        y -= 20f
        report.bookerBreakdown.forEach { b ->
            x = MARGIN_X
            colW.forEach { w -> drawBorderedCell(cs, x, y, w, 18f, Color.WHITE); x += w }
            x = MARGIN_X
            drawText(cs, b.booker.fullName.take(28), x + 4f, y - 12f, fontBold, 8f)
            x += colW[0]
            drawText(cs, "${b.shopsAssignedCount}", x + colW[1] - 4f, y - 12f, fontRegular, 8f, BLACK, true)
            x += colW[1]
            drawText(cs, SharedCurrencyFormatter.formatLedgerAmount(b.todayCreditPaisa), x + colW[2] - 4f, y - 12f, fontRegular, 8f, BLACK, true)
            x += colW[2]
            drawText(cs, SharedCurrencyFormatter.formatLedgerAmount(b.todayRecoveryPaisa), x + colW[3] - 4f, y - 12f, fontRegular, 8f, GREEN, true)
            x += colW[3]
            drawText(cs, SharedCurrencyFormatter.formatLedgerAmount(b.totalOutstandingPaisa), x + colW[4] - 4f, y - 12f, fontBold, 8f, NAVY, true)
            y -= 18f
        }
        cs.close()
        outputFile.parentFile?.mkdirs()
        doc.save(outputFile)
        doc.close()
        return outputFile
    }

    fun generateBookerConsolidatedPdf(
        outputFile: File,
        businessName: String,
        ledger: BookerConsolidatedLedger
    ): File {
        val doc = PDDocument()
        var page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        var cs = PDPageContentStream(doc, page)
        var y = PAGE_H - 42f
        drawText(cs, "$businessName — BOOKER ALL-SHOPS LEDGER", MARGIN_X, y, fontBold, 13f, NAVY)
        y -= 16f
        drawText(cs, "Booker: ${ledger.booker.fullName} (${ledger.booker.bookerCode}) | Total Outstanding: ${SharedCurrencyFormatter.formatPkr(ledger.grandTotalOutstandingPaisa)}", MARGIN_X, y, fontRegular, 9f, GRAY)
        y -= 20f

        val colW = floatArrayOf(75f, 175f, 95f, 95f, 99f)
        var x = MARGIN_X
        listOf("Shop ID", "Shop Name", "Total Credit", "Total Recovered", "Balance").forEachIndexed { i, t ->
            drawBorderedCell(cs, x, y, colW[i], 20f, NAVY_SOFT)
            drawText(cs, t, x + 4f, y - 13f, fontBold, 8f, NAVY)
            x += colW[i]
        }
        y -= 20f
        ledger.shopDetails.forEach { d ->
            if (y - 18f < 45f) {
                cs.close()
                page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                cs = PDPageContentStream(doc, page)
                y = PAGE_H - 42f
            }
            x = MARGIN_X
            colW.forEach { w -> drawBorderedCell(cs, x, y, w, 18f, Color.WHITE); x += w }
            x = MARGIN_X
            drawText(cs, d.shop.shopUid.take(14), x + 4f, y - 12f, fontRegular, 8f)
            x += colW[0]
            drawText(cs, d.shop.shopName.take(32), x + 4f, y - 12f, fontBold, 8f)
            x += colW[1]
            drawText(cs, SharedCurrencyFormatter.formatLedgerAmount(d.totalCreditPaisa), x + colW[2] - 4f, y - 12f, fontRegular, 8f, BLACK, true)
            x += colW[2]
            drawText(cs, SharedCurrencyFormatter.formatLedgerAmount(d.totalRecoveredPaisa), x + colW[3] - 4f, y - 12f, fontRegular, 8f, GREEN, true)
            x += colW[3]
            drawText(cs, SharedCurrencyFormatter.formatLedgerAmount(d.remainingOutstandingPaisa), x + colW[4] - 4f, y - 12f, fontBold, 8f, NAVY, true)
            y -= 18f
        }
        cs.close()
        outputFile.parentFile?.mkdirs()
        doc.save(outputFile)
        doc.close()
        return outputFile
    }

    fun openFileInDesktopViewer(file: File): Result<Unit> = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(file)
        }
    }

    fun printPdfWithDialog(pdfFile: File): Result<Unit> = runCatching {
        Loader.loadPDF(pdfFile).use { document ->
            val job = PrinterJob.getPrinterJob()
            job.jobName = "HisabPro - ${pdfFile.nameWithoutExtension}"
            job.setPageable(PDFPageable(document))
            if (job.printDialog()) {
                job.print()
            }
        }
    }

    fun chooseSaveTarget(defaultFileName: String): File? {
        val dialog = FileDialog(null as Frame?, "Save Report", FileDialog.SAVE).apply {
            file = defaultFileName
            isVisible = true
        }
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return File(dir, name)
    }

    fun chooseOpenTarget(dialogTitle: String = "Select Backup File (.db)"): File? {
        val dialog = FileDialog(null as Frame?, dialogTitle, FileDialog.LOAD).apply {
            isVisible = true
        }
        val dir = dialog.directory ?: return null
        val name = dialog.file ?: return null
        return File(dir, name)
    }
}
