package com.example.desktop.export

import com.example.shared.engine.SharedFinancialEngine
import com.example.shared.export.SharedBackupBuilder
import com.example.shared.model.*
import com.example.shared.util.SharedCurrencyFormatter
import com.example.shared.util.SharedPakistanTime
import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

/**
 * Windows Desktop Apache POI Excel (.xlsx), CSV, and JSON Backup Exporter.
 */
object DesktopExcelExporter {

    fun exportShopLedgerXlsx(
        outputFile: File,
        businessName: String,
        shop: Shop,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        startMillis: Long,
        endMillis: Long
    ): File {
        val report = SharedFinancialEngine.computeShopLedgerReport(shop, credits, recoveries, startMillis, endMillis)
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Supplier Ledger")

        val headerStyle = wb.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(wb.createFont().apply { bold = true; color = IndexedColors.WHITE.index })
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }
        val reversalStyle = wb.createCellStyle().apply {
            setFont(wb.createFont().apply { color = IndexedColors.DARK_RED.index; bold = true })
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }
        val cellStyle = wb.createCellStyle().apply {
            borderBottom = BorderStyle.THIN
            borderTop = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
        }

        val titleRow = sheet.createRow(0)
        titleRow.createCell(0).setCellValue("$businessName — SUPPLIER LEDGER (${shop.shopName} / ${shop.shopUid})")
        val metaRow = sheet.createRow(1)
        metaRow.createCell(0).setCellValue("Booker: ${shop.bookerName} | Generated: ${SharedPakistanTime.formatDateTime()}")

        val hdrRow = sheet.createRow(3)
        listOf("Voucher #", "Dated", "Description", "Purchase", "Payment / Recovery", "Balance").forEachIndexed { i, title ->
            hdrRow.createCell(i).apply {
                setCellValue(title)
                setCellStyle(headerStyle)
            }
        }

        val openingRow = sheet.createRow(4)
        openingRow.createCell(0).apply { setCellValue("0"); setCellStyle(cellStyle) }
        openingRow.createCell(1).apply { setCellValue(if (startMillis > 0L) SharedPakistanTime.formatDashDate(startMillis) else "Opening"); setCellStyle(cellStyle) }
        openingRow.createCell(2).apply { setCellValue("Opening Balance"); setCellStyle(cellStyle) }
        openingRow.createCell(3).apply { setCellValue(0.0); setCellStyle(cellStyle) }
        openingRow.createCell(4).apply { setCellValue(0.0); setCellStyle(cellStyle) }
        openingRow.createCell(5).apply { setCellValue(SharedCurrencyFormatter.paisaToBigDecimal(report.openingBalancePaisa).toDouble()); setCellStyle(cellStyle) }

        var rowIdx = 5
        report.entries.forEach { entry ->
            val r = sheet.createRow(rowIdx++)
            val style = if (entry.isReversal) reversalStyle else cellStyle
            val desc = if (entry.isReversal && entry.reason.isNotBlank()) "${entry.description} [Reason: ${entry.reason}]" else entry.description
            r.createCell(0).apply { setCellValue(entry.voucherNumber); setCellStyle(style) }
            r.createCell(1).apply { setCellValue(entry.txDateFormatted); setCellStyle(style) }
            r.createCell(2).apply { setCellValue(desc); setCellStyle(style) }
            r.createCell(3).apply { setCellValue(SharedCurrencyFormatter.paisaToBigDecimal(entry.creditPaisa).toDouble()); setCellStyle(style) }
            r.createCell(4).apply { setCellValue(SharedCurrencyFormatter.paisaToBigDecimal(entry.recoveryPaisa).toDouble()); setCellStyle(style) }
            r.createCell(5).apply { setCellValue(SharedCurrencyFormatter.paisaToBigDecimal(entry.runningBalancePaisa).toDouble()); setCellStyle(style) }
        }

        val totalRow = sheet.createRow(rowIdx)
        totalRow.createCell(0).apply { setCellValue("GRAND TOTAL"); setCellStyle(headerStyle) }
        totalRow.createCell(1).apply { setCellValue(""); setCellStyle(headerStyle) }
        totalRow.createCell(2).apply { setCellValue("Entries: ${report.entries.size}"); setCellStyle(headerStyle) }
        totalRow.createCell(3).apply { setCellValue(SharedCurrencyFormatter.paisaToBigDecimal(report.totalCreditPaisa).toDouble()); setCellStyle(headerStyle) }
        totalRow.createCell(4).apply { setCellValue(SharedCurrencyFormatter.paisaToBigDecimal(report.totalRecoveredPaisa).toDouble()); setCellStyle(headerStyle) }
        totalRow.createCell(5).apply { setCellValue(SharedCurrencyFormatter.paisaToBigDecimal(report.closingBalancePaisa).toDouble()); setCellStyle(headerStyle) }

        listOf(18, 15, 58, 16, 18, 16).forEachIndexed { col, width -> sheet.setColumnWidth(col, width * 256) }

        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { wb.write(it) }
        wb.close()
        return outputFile
    }

    fun exportAllShopsSummaryXlsx(
        outputFile: File,
        businessName: String,
        summaries: List<ShopBalanceSummary>
    ): File {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("All Shops Receivables")
        val hdr = sheet.createRow(0)
        listOf("Shop ID", "Shop Name", "Owner", "Mobile", "Booker", "Total Credit (PKR)", "Recovered (PKR)", "Outstanding (PKR)")
            .forEachIndexed { i, title -> hdr.createCell(i).setCellValue(title) }

        summaries.forEachIndexed { idx, s ->
            val r = sheet.createRow(idx + 1)
            r.createCell(0).setCellValue(s.shop.shopUid)
            r.createCell(1).setCellValue(s.shop.shopName)
            r.createCell(2).setCellValue(s.shop.ownerName)
            r.createCell(3).setCellValue(s.shop.mobileNumber)
            r.createCell(4).setCellValue(s.shop.bookerName)
            r.createCell(5).setCellValue(SharedCurrencyFormatter.paisaToBigDecimal(s.totalCreditPaisa).toDouble())
            r.createCell(6).setCellValue(SharedCurrencyFormatter.paisaToBigDecimal(s.totalRecoveredPaisa).toDouble())
            r.createCell(7).setCellValue(SharedCurrencyFormatter.paisaToBigDecimal(s.outstandingPaisa).toDouble())
        }
        listOf(16, 28, 22, 16, 22, 18, 18, 18).forEachIndexed { i, w -> sheet.setColumnWidth(i, w * 256) }
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { wb.write(it) }
        wb.close()
        return outputFile
    }

    fun exportShopLedgerCsv(
        outputFile: File,
        shop: Shop,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        startMillis: Long,
        endMillis: Long
    ): File {
        val report = SharedFinancialEngine.computeShopLedgerReport(shop, credits, recoveries, startMillis, endMillis)
        val lines = buildList {
            add("Voucher,Dated,Description,Purchase,PaymentRecovery,Balance,Status")
            add("0,Opening,Opening Balance,0.0,0.0,${SharedCurrencyFormatter.paisaToBigDecimal(report.openingBalancePaisa)},SYNCED")
            report.entries.forEach { e ->
                val safeDesc = "\"${e.description.replace("\"", "\"\"")}\""
                add("${e.voucherNumber},${e.txDateFormatted},$safeDesc,${SharedCurrencyFormatter.paisaToBigDecimal(e.creditPaisa)},${SharedCurrencyFormatter.paisaToBigDecimal(e.recoveryPaisa)},${SharedCurrencyFormatter.paisaToBigDecimal(e.runningBalancePaisa)},${e.syncStatus}")
            }
        }
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(lines.joinToString("\n"))
        return outputFile
    }

    fun exportWorkspaceBackupJson(
        outputFile: File,
        business: Business,
        bookers: List<Booker>,
        staffUsers: List<BusinessUser>,
        shops: List<Shop>,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        auditLogs: List<AuditLog>,
        products: List<ProductItem>,
        bookings: List<BookingOrder>
    ): File {
        val json = SharedBackupBuilder.buildExportJson(
            business = business,
            bookers = bookers,
            staffUsers = staffUsers,
            shops = shops,
            credits = credits,
            recoveries = recoveries,
            auditLogs = auditLogs,
            products = products,
            bookings = bookings
        )
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(json.toString(2))
        return outputFile
    }
}
