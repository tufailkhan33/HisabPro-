package com.example.util

import com.example.shared.util.SharedCurrencyFormatter
import java.math.BigDecimal

object CurrencyFormatter {
    fun paisaToBigDecimal(paisa: Long): BigDecimal =
        SharedCurrencyFormatter.paisaToBigDecimal(paisa)

    fun formatPkr(paisa: Long, includePrefix: Boolean = true): String =
        SharedCurrencyFormatter.formatPkr(paisa, includePrefix)

    fun formatLedgerAmount(paisa: Long): String =
        SharedCurrencyFormatter.formatLedgerAmount(paisa)

    fun formatCompactPkr(paisa: Long): String =
        SharedCurrencyFormatter.formatCompactPkr(paisa)

    fun parsePkrToPaisa(input: String): Long? =
        SharedCurrencyFormatter.parsePkrToPaisa(input)
}
