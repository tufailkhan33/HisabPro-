package com.example.shared.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object SharedCurrencyFormatter {
    private val symbols = DecimalFormatSymbols(Locale.ENGLISH).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }
    private val wholeFormat = DecimalFormat("#,##0", symbols)
    private val decimalFormat = DecimalFormat("#,##0.00", symbols)
    private val hundred = BigDecimal(100)

    fun paisaToBigDecimal(paisa: Long): BigDecimal {
        return BigDecimal(paisa).divide(hundred, 2, RoundingMode.HALF_UP)
    }

    fun formatPkr(paisa: Long, includePrefix: Boolean = true): String {
        val prefix = if (includePrefix) "Rs. " else ""
        val absPaisa = kotlin.math.abs(paisa)
        val sign = if (paisa < 0) "-" else ""
        val bd = paisaToBigDecimal(absPaisa)
        val formatted = if (absPaisa % 100L == 0L) {
            wholeFormat.format(bd.setScale(0, RoundingMode.HALF_UP))
        } else {
            decimalFormat.format(bd)
        }
        return "$sign$prefix$formatted"
    }

    fun formatLedgerAmount(paisa: Long): String {
        val absPaisa = kotlin.math.abs(paisa)
        val sign = if (paisa < 0) "-" else ""
        val bd = paisaToBigDecimal(absPaisa)
        val pattern = if (absPaisa % 100L == 0L) {
            DecimalFormat("#,##0.0", symbols)
        } else {
            decimalFormat
        }
        return "$sign${pattern.format(bd)}"
    }

    fun formatCompactPkr(paisa: Long): String {
        val rs = paisaToBigDecimal(paisa).toDouble()
        val abs = kotlin.math.abs(rs)
        val sign = if (rs < 0) "-" else ""
        return when {
            abs >= 10_000_000 -> "${sign}Rs. ${String.format(Locale.ENGLISH, "%.2f", abs / 10_000_000)} Cr"
            abs >= 100_000 -> "${sign}Rs. ${String.format(Locale.ENGLISH, "%.2f", abs / 100_000)} Lac"
            else -> formatPkr(paisa)
        }
    }

    fun parsePkrToPaisa(input: String): Long? {
        val cleaned = input.trim().replace(",", "").replace("Rs.", "", ignoreCase = true).trim()
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP)
            if (bd < BigDecimal.ZERO) return null
            bd.multiply(hundred).longValueExact()
        } catch (_: Exception) {
            null
        }
    }
}
