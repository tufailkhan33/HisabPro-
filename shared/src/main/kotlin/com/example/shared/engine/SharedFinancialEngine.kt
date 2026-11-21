package com.example.shared.engine

import com.example.shared.model.AccountRole
import com.example.shared.model.Booker
import com.example.shared.model.BookerBalanceSummary
import com.example.shared.model.BookerConsolidatedLedger
import com.example.shared.model.BookerDailyPerformance
import com.example.shared.model.BookerShopInvoiceRow
import com.example.shared.model.BookerShopPaymentRow
import com.example.shared.model.BookerShopRecoveryDetail
import com.example.shared.model.BusinessDashboardSummary
import com.example.shared.model.BusinessUser
import com.example.shared.model.CreditTransaction
import com.example.shared.model.DailyPerformanceReport
import com.example.shared.model.PeriodReportSummary
import com.example.shared.model.PeriodShopRow
import com.example.shared.model.RecoveryTransaction
import com.example.shared.model.Shop
import com.example.shared.model.ShopBalanceSummary
import com.example.shared.model.ShopLedgerEntry
import com.example.shared.model.ShopLedgerReport
import com.example.shared.model.SyncStatus
import com.example.shared.util.SharedPakistanTime
import java.math.BigDecimal

/**
 * Pure decimal-safe financial calculation engine shared by Android & Windows Desktop.
 * All monetary values are stored in integer Paisa (1 PKR = 100 Paisa) and summed via BigDecimal.
 */
object SharedFinancialEngine {

    fun netCreditPaisa(credits: List<CreditTransaction>): Long {
        var total = BigDecimal.ZERO
        for (tx in credits) {
            val amount = BigDecimal.valueOf(tx.amountPaisa)
            total = if (tx.transactionKind == "CREDIT_REVERSAL") {
                total.subtract(amount)
            } else {
                total.add(amount)
            }
        }
        return total.longValueExact()
    }

    fun netRecoveryPaisa(recoveries: List<RecoveryTransaction>): Long {
        var total = BigDecimal.ZERO
        for (tx in recoveries) {
            val amount = BigDecimal.valueOf(tx.amountPaisa)
            total = if (tx.transactionKind == "RECOVERY_REVERSAL") {
                total.subtract(amount)
            } else {
                total.add(amount)
            }
        }
        return total.longValueExact()
    }

    fun computeShopBalances(
        shops: List<Shop>,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>
    ): List<ShopBalanceSummary> {
        val creditsByShop = credits.groupBy { it.shopId }
        val recoveriesByShop = recoveries.groupBy { it.shopId }

        return shops.map { shop ->
            val shopCredits = creditsByShop[shop.id].orEmpty()
            val shopRecoveries = recoveriesByShop[shop.id].orEmpty()
            val totalCredit = netCreditPaisa(shopCredits)
            val totalRecovered = netRecoveryPaisa(shopRecoveries)
            val outstanding = BigDecimal.valueOf(totalCredit)
                .subtract(BigDecimal.valueOf(totalRecovered))
                .longValueExact()

            val firstCredit = shopCredits
                .filter { it.transactionKind == "CREDIT" && it.txDateMillis > 0L }
                .minOfOrNull { it.txDateMillis }

            val lastCredit = shopCredits.maxOfOrNull { it.txDateMillis }
            val lastRecovery = shopRecoveries.maxOfOrNull { it.txDateMillis }
            val lastTx = listOfNotNull(lastCredit, lastRecovery).maxOrNull()

            ShopBalanceSummary(
                shop = shop,
                totalCreditPaisa = totalCredit,
                totalRecoveredPaisa = totalRecovered,
                outstandingPaisa = outstanding,
                firstCreditDateMillis = firstCredit,
                lastTransactionDateMillis = lastTx,
                creditCount = shopCredits.size,
                recoveryCount = shopRecoveries.size
            )
        }
    }

    fun computeBookerSummaries(
        bookers: List<Booker>,
        shopBalances: List<ShopBalanceSummary>
    ): List<BookerBalanceSummary> {
        val shopsByBooker = shopBalances.filter { !it.shop.isArchived }.groupBy { it.shop.bookerId }
        return bookers.map { booker ->
            val assigned = shopsByBooker[booker.id].orEmpty()
            var credit = BigDecimal.ZERO
            var recovered = BigDecimal.ZERO
            assigned.forEach { sb ->
                credit = credit.add(BigDecimal.valueOf(sb.totalCreditPaisa))
                recovered = recovered.add(BigDecimal.valueOf(sb.totalRecoveredPaisa))
            }
            val outstanding = credit.subtract(recovered)
            BookerBalanceSummary(
                booker = booker,
                assignedShopsCount = assigned.size,
                totalCreditPaisa = credit.longValueExact(),
                totalRecoveredPaisa = recovered.longValueExact(),
                outstandingPaisa = outstanding.longValueExact()
            )
        }
    }

    fun computeDashboardSummary(
        shopBalances: List<ShopBalanceSummary>,
        bookers: List<Booker>,
        staffUsers: List<BusinessUser>
    ): BusinessDashboardSummary {
        var totalCredit = BigDecimal.ZERO
        var totalRecovered = BigDecimal.ZERO
        for (sb in shopBalances) {
            totalCredit = totalCredit.add(BigDecimal.valueOf(sb.totalCreditPaisa))
            totalRecovered = totalRecovered.add(BigDecimal.valueOf(sb.totalRecoveredPaisa))
        }
        val totalOutstanding = totalCredit.subtract(totalRecovered)
        return BusinessDashboardSummary(
            totalCreditPaisa = totalCredit.longValueExact(),
            totalRecoveredPaisa = totalRecovered.longValueExact(),
            totalOutstandingPaisa = totalOutstanding.longValueExact(),
            totalShops = shopBalances.size,
            activeShops = shopBalances.count { !it.shop.isArchived },
            totalBookers = bookers.size,
            totalStaff = staffUsers.count { it.role == AccountRole.STAFF.key }
        )
    }

    fun computePeriodReport(
        shops: List<Shop>,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        startMillis: Long,
        endMillis: Long
    ): PeriodReportSummary {
        val periodCredits = credits.filter { it.txDateMillis in startMillis..endMillis }
        val periodRecoveries = recoveries.filter { it.txDateMillis in startMillis..endMillis }

        val periodCreditPaisa = netCreditPaisa(periodCredits)
        val periodRecoveredPaisa = netRecoveryPaisa(periodRecoveries)
        val periodNetChange = BigDecimal.valueOf(periodCreditPaisa)
            .subtract(BigDecimal.valueOf(periodRecoveredPaisa))
            .longValueExact()

        val asOfEndCredits = credits.filter { it.txDateMillis <= endMillis }
        val asOfEndRecoveries = recoveries.filter { it.txDateMillis <= endMillis }
        val closingOutstanding = BigDecimal.valueOf(netCreditPaisa(asOfEndCredits))
            .subtract(BigDecimal.valueOf(netRecoveryPaisa(asOfEndRecoveries)))
            .longValueExact()

        val currentOutstanding = BigDecimal.valueOf(netCreditPaisa(credits))
            .subtract(BigDecimal.valueOf(netRecoveryPaisa(recoveries)))
            .longValueExact()

        val periodCreditsByShop = periodCredits.groupBy { it.shopId }
        val periodRecoveriesByShop = periodRecoveries.groupBy { it.shopId }
        val asOfEndCreditsByShop = asOfEndCredits.groupBy { it.shopId }
        val asOfEndRecoveriesByShop = asOfEndRecoveries.groupBy { it.shopId }
        val allCreditsByShop = credits.groupBy { it.shopId }
        val allRecoveriesByShop = recoveries.groupBy { it.shopId }

        val shopRows = shops.map { shop ->
            val pCr = netCreditPaisa(periodCreditsByShop[shop.id].orEmpty())
            val pRc = netRecoveryPaisa(periodRecoveriesByShop[shop.id].orEmpty())
            val endCr = netCreditPaisa(asOfEndCreditsByShop[shop.id].orEmpty())
            val endRc = netRecoveryPaisa(asOfEndRecoveriesByShop[shop.id].orEmpty())
            val curCr = netCreditPaisa(allCreditsByShop[shop.id].orEmpty())
            val curRc = netRecoveryPaisa(allRecoveriesByShop[shop.id].orEmpty())
            PeriodShopRow(
                shop = shop,
                periodCreditPaisa = pCr,
                periodRecoveredPaisa = pRc,
                closingOutstandingAsOfEndDatePaisa = endCr - endRc,
                currentOutstandingPaisa = curCr - curRc
            )
        }

        return PeriodReportSummary(
            startMillis = startMillis,
            endMillis = endMillis,
            periodCreditPaisa = periodCreditPaisa,
            periodRecoveredPaisa = periodRecoveredPaisa,
            periodNetChangePaisa = periodNetChange,
            closingOutstandingAsOfEndDatePaisa = closingOutstanding,
            currentOutstandingPaisa = currentOutstanding,
            creditTxCount = periodCredits.size,
            recoveryTxCount = periodRecoveries.size,
            shopRows = shopRows,
            filteredCreditTxs = periodCredits.sortedByDescending { it.txDateMillis },
            filteredRecoveryTxs = periodRecoveries.sortedByDescending { it.txDateMillis }
        )
    }

    fun reversedCreditMap(credits: List<CreditTransaction>): Map<String, CreditTransaction> {
        return credits
            .filter { it.transactionKind == "CREDIT_REVERSAL" && it.relatedTxId.isNotBlank() }
            .associateBy { it.relatedTxId }
    }

    fun reversedRecoveryMap(recoveries: List<RecoveryTransaction>): Map<String, RecoveryTransaction> {
        return recoveries
            .filter { it.transactionKind == "RECOVERY_REVERSAL" && it.relatedTxId.isNotBlank() }
            .associateBy { it.relatedTxId }
    }

    fun computeShopLedgerReport(
        shop: Shop,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        startMillis: Long,
        endMillis: Long
    ): ShopLedgerReport {
        val shopCredits = credits.filter { it.shopId == shop.id }
        val shopRecoveries = recoveries.filter { it.shopId == shop.id }
        val revCreditByOrigId = reversedCreditMap(shopCredits)
        val revRecByOrigId = reversedRecoveryMap(shopRecoveries)
        val recById = shopRecoveries.associateBy { it.id }

        val priorCredits = shopCredits.filter { it.txDateMillis < startMillis }
        val priorRecoveries = shopRecoveries.filter { it.txDateMillis < startMillis }
        val openingBalance = BigDecimal.valueOf(netCreditPaisa(priorCredits))
            .subtract(BigDecimal.valueOf(netRecoveryPaisa(priorRecoveries)))
            .longValueExact()

        val periodCredits = shopCredits.filter { it.txDateMillis in startMillis..endMillis }
        val periodRecoveries = shopRecoveries.filter { it.txDateMillis in startMillis..endMillis }

        data class RawLedgerRow(
            val id: String,
            val voucherNumber: String,
            val txDateMillis: Long,
            val txDateFormatted: String,
            val entryTimestampMillis: Long,
            val entryDateTimeFormatted: String,
            val description: String,
            val creditDeltaPaisa: Long,
            val recoveryDeltaPaisa: Long,
            val isReversal: Boolean,
            val isReversedOriginal: Boolean,
            val reason: String,
            val originalDateFormatted: String,
            val reversalDateFormatted: String,
            val syncStatus: String,
            val tieBreak: Int
        )

        val rawRows = mutableListOf<RawLedgerRow>()
        for (c in periodCredits) {
            val isRev = c.transactionKind == "CREDIT_REVERSAL"
            val isReversedOrig = !isRev && revCreditByOrigId.containsKey(c.id)
            val entryTs = if (c.entryTimestampMillis > 0L) c.entryTimestampMillis else c.txDateMillis
            val entryDtStr = "${SharedPakistanTime.formatDashDate(entryTs)} ${SharedPakistanTime.formatTime(entryTs)} PKT"
            val desc = buildString {
                if (isRev) {
                    append("CREDIT REVERSAL: ${c.description.ifBlank { "Reversed Credit" }}")
                    if (c.reason.isNotBlank()) append(", Reason: ${c.reason}")
                } else {
                    append("PURCHASE: ${c.description.ifBlank { "Credit Sale" }}")
                    if (c.invoiceNumber.isNotBlank()) append(", Invoice #: ${c.invoiceNumber}")
                    if (c.dueDateFormatted.isNotBlank()) append(", Due: ${c.dueDateFormatted}")
                    append(" (Entered: $entryDtStr)")
                }
            }
            rawRows.add(
                RawLedgerRow(
                    id = "cr_${c.id}",
                    voucherNumber = c.invoiceNumber.ifBlank { c.txCode },
                    txDateMillis = c.txDateMillis,
                    txDateFormatted = SharedPakistanTime.formatDashDate(c.txDateMillis),
                    entryTimestampMillis = entryTs,
                    entryDateTimeFormatted = "${SharedPakistanTime.formatDashDate(c.txDateMillis)} ${c.txTimeFormatted}".trim(),
                    description = desc,
                    creditDeltaPaisa = if (isRev) -c.amountPaisa else c.amountPaisa,
                    recoveryDeltaPaisa = 0L,
                    isReversal = isRev,
                    isReversedOriginal = isReversedOrig,
                    reason = c.reason,
                    originalDateFormatted = SharedPakistanTime.formatDashDate(c.txDateMillis),
                    reversalDateFormatted = if (isRev) SharedPakistanTime.formatDashDate(c.txDateMillis) else revCreditByOrigId[c.id]?.txDateFormatted.orEmpty(),
                    syncStatus = c.syncStatus,
                    tieBreak = if (isRev) 1 else 0
                )
            )
        }

        for (r in periodRecoveries) {
            val isRev = r.transactionKind == "RECOVERY_REVERSAL"
            val isReversedOrig = !isRev && revRecByOrigId.containsKey(r.id)
            val origTx = if (isRev && r.relatedTxId.isNotBlank()) recById[r.relatedTxId] else null
            val origDate = when {
                r.originalTxDateMillis > 0L -> SharedPakistanTime.formatDashDate(r.originalTxDateMillis)
                origTx != null -> SharedPakistanTime.formatDashDate(origTx.txDateMillis)
                else -> SharedPakistanTime.formatDashDate(r.txDateMillis)
            }
            val revDate = when {
                isRev && r.reversedAtMillis > 0L -> SharedPakistanTime.formatDashDate(r.reversedAtMillis)
                isRev -> SharedPakistanTime.formatDashDate(r.txDateMillis)
                else -> revRecByOrigId[r.id]?.txDateFormatted.orEmpty()
            }
            val desc = if (isRev) {
                "REVERSED PAYMENT: ${r.paymentMethod} > ${shop.shopName}"
            } else {
                buildString {
                    append("PAYMENT / RECOVERY: ${r.paymentMethod} > ${shop.shopName}")
                    if (r.referenceNumber.isNotBlank()) append(", REF #: ${r.referenceNumber}")
                    if (r.notes.isNotBlank()) append(", Comments: ${r.notes}")
                    if (r.correctedFromTxId.isNotBlank()) append(" [Corrected]")
                    if (r.isBackdated) append(" [Admin Backdated]")
                }
            }
            rawRows.add(
                RawLedgerRow(
                    id = "rc_${r.id}",
                    voucherNumber = r.txCode,
                    txDateMillis = r.txDateMillis,
                    txDateFormatted = SharedPakistanTime.formatDashDate(r.txDateMillis),
                    entryTimestampMillis = r.txDateMillis,
                    entryDateTimeFormatted = "${SharedPakistanTime.formatDashDate(r.txDateMillis)} ${r.txTimeFormatted}".trim(),
                    description = desc,
                    creditDeltaPaisa = 0L,
                    recoveryDeltaPaisa = if (isRev) -r.amountPaisa else r.amountPaisa,
                    isReversal = isRev,
                    isReversedOriginal = isReversedOrig,
                    reason = r.reason.ifBlank { revRecByOrigId[r.id]?.reason.orEmpty() },
                    originalDateFormatted = origDate,
                    reversalDateFormatted = revDate,
                    syncStatus = r.syncStatus,
                    tieBreak = if (isRev) 3 else 2
                )
            )
        }

        val sorted = rawRows.sortedWith(compareBy<RawLedgerRow> { it.txDateMillis }.thenBy { it.tieBreak })
        var running = BigDecimal.valueOf(openingBalance)
        val entries = sorted.map { row ->
            running = running
                .add(BigDecimal.valueOf(row.creditDeltaPaisa))
                .subtract(BigDecimal.valueOf(row.recoveryDeltaPaisa))
            ShopLedgerEntry(
                id = row.id,
                voucherNumber = row.voucherNumber,
                txDateMillis = row.txDateMillis,
                txDateFormatted = row.txDateFormatted,
                entryTimestampMillis = row.entryTimestampMillis,
                entryDateTimeFormatted = row.entryDateTimeFormatted,
                description = row.description,
                creditPaisa = row.creditDeltaPaisa,
                recoveryPaisa = row.recoveryDeltaPaisa,
                runningBalancePaisa = running.longValueExact(),
                isReversal = row.isReversal,
                isReversedOriginal = row.isReversedOriginal,
                reason = row.reason,
                originalPaymentDateFormatted = row.originalDateFormatted,
                reversalDateFormatted = row.reversalDateFormatted,
                syncStatus = row.syncStatus
            )
        }

        val periodCreditNet = netCreditPaisa(periodCredits)
        val periodRecoveryNet = netRecoveryPaisa(periodRecoveries)
        val reversedRecoveryTotal = periodRecoveries
            .filter { it.transactionKind == "RECOVERY_REVERSAL" }
            .sumOf { it.amountPaisa }

        val hasUnsynced = shopCredits.any { it.syncStatus != SyncStatus.SYNCED.key } ||
            shopRecoveries.any { it.syncStatus != SyncStatus.SYNCED.key }

        return ShopLedgerReport(
            shop = shop,
            startMillis = startMillis,
            endMillis = endMillis,
            openingBalancePaisa = openingBalance,
            entries = entries,
            totalCreditPaisa = periodCreditNet,
            totalRecoveredPaisa = periodRecoveryNet,
            reversedTotalPaisa = reversedRecoveryTotal,
            closingBalancePaisa = running.longValueExact(),
            hasUnsyncedData = hasUnsynced
        )
    }

    fun computeBookerConsolidatedLedger(
        booker: Booker,
        shops: List<Shop>,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        startMillis: Long = 0L,
        endMillis: Long = Long.MAX_VALUE,
        referenceNowMillis: Long = SharedPakistanTime.nowMillis()
    ): BookerConsolidatedLedger {
        val assignedShops = shops
            .filter { it.bookerId == booker.id }
            .sortedBy { it.shopName.lowercase() }

        val shopDetails = assignedShops.map { shop ->
            val ledger = computeShopLedgerReport(shop, credits, recoveries, startMillis, endMillis)
            val shopCredits = credits.filter { it.shopId == shop.id && it.txDateMillis in startMillis..endMillis }
                .sortedBy { it.txDateMillis }
            val shopRecoveries = recoveries.filter { it.shopId == shop.id && it.txDateMillis in startMillis..endMillis }
                .sortedBy { it.txDateMillis }
            val revRecMap = reversedRecoveryMap(shopRecoveries)
            val recById = shopRecoveries.associateBy { it.id }

            val invoiceRows = shopCredits.map { c ->
                val isRev = c.transactionKind == "CREDIT_REVERSAL"
                val entryStr = when {
                    c.entryDateFormatted.isNotBlank() -> "${c.entryDateFormatted} ${c.entryTimeFormatted}".trim()
                    else -> "${c.txDateFormatted} ${c.txTimeFormatted}".trim()
                }
                BookerShopInvoiceRow(
                    voucherNumber = c.invoiceNumber.ifBlank { c.txCode },
                    invoiceDateFormatted = c.txDateFormatted,
                    entryDateTimeFormatted = entryStr,
                    dueDateFormatted = c.dueDateFormatted.ifBlank { "-" },
                    amountPaisa = if (isRev) -c.amountPaisa else c.amountPaisa,
                    isReversal = isRev,
                    reason = c.reason,
                    syncStatus = c.syncStatus
                )
            }

            val balanceByEntryId = ledger.entries.associateBy { it.id }
            val paymentRows = shopRecoveries.map { r ->
                val isRev = r.transactionKind == "RECOVERY_REVERSAL"
                val isOrigReversed = !isRev && revRecMap.containsKey(r.id)
                val orig = if (isRev && r.relatedTxId.isNotBlank()) recById[r.relatedTxId] else null
                val entry = balanceByEntryId["rc_${r.id}"]
                BookerShopPaymentRow(
                    voucherNumber = r.txCode,
                    paymentDateFormatted = r.txDateFormatted,
                    method = r.paymentMethod,
                    amountPaisa = if (isRev) -r.amountPaisa else r.amountPaisa,
                    isReversal = isRev,
                    isReversedOriginal = isOrigReversed,
                    reason = r.reason.ifBlank { revRecMap[r.id]?.reason.orEmpty() },
                    originalDateFormatted = when {
                        r.originalTxDateMillis > 0L -> SharedPakistanTime.formatDate(r.originalTxDateMillis)
                        orig != null -> orig.txDateFormatted
                        else -> r.txDateFormatted
                    },
                    reversalDateFormatted = if (isRev) r.txDateFormatted else revRecMap[r.id]?.txDateFormatted.orEmpty(),
                    runningBalanceAfterPaisa = entry?.runningBalancePaisa ?: ledger.closingBalancePaisa,
                    syncStatus = r.syncStatus
                )
            }

            val hasOverdue = shopCredits.any {
                it.transactionKind == "CREDIT" &&
                    it.dueDateMillis in 1L until referenceNowMillis &&
                    ledger.closingBalancePaisa > 0L
            }
            val status = when {
                ledger.closingBalancePaisa <= 0L -> "Paid"
                hasOverdue -> "Overdue"
                ledger.totalRecoveredPaisa > 0L -> "Partial"
                else -> "Unpaid"
            }

            BookerShopRecoveryDetail(
                shop = shop,
                invoices = invoiceRows,
                payments = paymentRows,
                totalCreditPaisa = ledger.totalCreditPaisa,
                totalRecoveredPaisa = ledger.totalRecoveredPaisa,
                reversedTotalPaisa = ledger.reversedTotalPaisa,
                remainingOutstandingPaisa = ledger.closingBalancePaisa,
                paymentStatus = status,
                hasUnsyncedData = ledger.hasUnsyncedData
            )
        }

        val grandCredit = shopDetails.fold(BigDecimal.ZERO) { acc, d -> acc.add(BigDecimal.valueOf(d.totalCreditPaisa)) }.longValueExact()
        val grandRecovered = shopDetails.fold(BigDecimal.ZERO) { acc, d -> acc.add(BigDecimal.valueOf(d.totalRecoveredPaisa)) }.longValueExact()
        val grandOutstanding = shopDetails.fold(BigDecimal.ZERO) { acc, d -> acc.add(BigDecimal.valueOf(d.remainingOutstandingPaisa)) }.longValueExact()

        return BookerConsolidatedLedger(
            booker = booker,
            startMillis = startMillis,
            endMillis = endMillis,
            shopDetails = shopDetails,
            grandTotalCreditPaisa = grandCredit,
            grandTotalRecoveredPaisa = grandRecovered,
            grandTotalOutstandingPaisa = grandOutstanding,
            hasUnsyncedData = shopDetails.any { it.hasUnsyncedData }
        )
    }

    fun computeDailyPerformanceReport(
        shops: List<Shop>,
        bookers: List<Booker>,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        dayMillis: Long = SharedPakistanTime.nowMillis()
    ): DailyPerformanceReport {
        val (dayStart, dayEnd) = SharedPakistanTime.dayRangeFor(dayMillis)
        val dayCredits = credits.filter { it.txDateMillis in dayStart..dayEnd }
        val dayRecoveries = recoveries.filter { it.txDateMillis in dayStart..dayEnd }

        val revRecoveryIds = reversedRecoveryMap(recoveries).keys
        val dayValidCreditCount = dayCredits.count { it.transactionKind != "CREDIT_REVERSAL" }
        val dayValidRecoveryCount = dayRecoveries.count {
            it.transactionKind != "RECOVERY_REVERSAL" && !revRecoveryIds.contains(it.id)
        }

        val todayCredit = netCreditPaisa(dayCredits)
        val todayRecovery = netRecoveryPaisa(dayRecoveries)
        val shopBalances = computeShopBalances(shops, credits, recoveries)
        val totalOutstanding = shopBalances.fold(BigDecimal.ZERO) { acc, s ->
            acc.add(BigDecimal.valueOf(s.outstandingPaisa))
        }.longValueExact()

        val creditShopIds = dayCredits
            .filter { it.transactionKind != "CREDIT_REVERSAL" }
            .map { it.shopId }
            .toSet()
        val recoveryShopIds = dayRecoveries
            .filter { it.transactionKind != "RECOVERY_REVERSAL" && !revRecoveryIds.contains(it.id) }
            .map { it.shopId }
            .toSet()

        val shopById = shops.associateBy { it.id }
        val creditShopsToday = creditShopIds.mapNotNull { shopById[it] }.sortedBy { it.shopName }
        val recoveryShopsToday = recoveryShopIds.mapNotNull { shopById[it] }.sortedBy { it.shopName }

        val bookerBreakdown = bookers.map { booker ->
            val bkShops = shops.filter { it.bookerId == booker.id && !it.isArchived }
            val bkBalances = shopBalances.filter { it.shop.bookerId == booker.id }
            val bkDayCredits = dayCredits.filter { it.bookerId == booker.id }
            val bkDayRecoveries = dayRecoveries.filter { it.bookerId == booker.id }
            BookerDailyPerformance(
                booker = booker,
                shopsAssignedCount = bkShops.size,
                creditShopsTodayCount = bkDayCredits.filter { it.transactionKind != "CREDIT_REVERSAL" }.map { it.shopId }.toSet().size,
                recoveryShopsTodayCount = bkDayRecoveries.filter {
                    it.transactionKind != "RECOVERY_REVERSAL" && !revRecoveryIds.contains(it.id)
                }.map { it.shopId }.toSet().size,
                todayCreditPaisa = netCreditPaisa(bkDayCredits),
                todayRecoveryPaisa = netRecoveryPaisa(bkDayRecoveries),
                totalOutstandingPaisa = bkBalances.fold(BigDecimal.ZERO) { acc, s -> acc.add(BigDecimal.valueOf(s.outstandingPaisa)) }.longValueExact()
            )
        }

        val hasUnsynced = credits.any { it.syncStatus != SyncStatus.SYNCED.key } ||
            recoveries.any { it.syncStatus != SyncStatus.SYNCED.key }

        return DailyPerformanceReport(
            dateMillis = dayStart,
            dateFormatted = SharedPakistanTime.formatDate(dayStart),
            todayCreditPaisa = todayCredit,
            todayRecoveryPaisa = todayRecovery,
            todayCreditCount = dayValidCreditCount,
            todayRecoveryCount = dayValidRecoveryCount,
            totalOutstandingPaisa = totalOutstanding,
            shopsReceivingCreditToday = creditShopsToday,
            shopsMakingRecoveryToday = recoveryShopsToday,
            bookerBreakdown = bookerBreakdown,
            hasUnsyncedData = hasUnsynced
        )
    }

    /**
     * Enforces HisabPro recovery date policy across Android & Windows Desktop:
     * - Future PKT dates are always blocked.
     * - STAFF accounts can only record recoveries on the current PKT calendar day.
     * - Only SUPER_ADMIN (or Platform Owner) can record backdated recoveries.
     */
    fun validateRecoveryDateForRole(
        role: AccountRole,
        txDateMillis: Long,
        referenceNowMillis: Long = SharedPakistanTime.nowMillis()
    ): String? {
        if (SharedPakistanTime.isFuturePakistanDay(txDateMillis, referenceNowMillis)) {
            return "Future-dated recoveries are not permitted."
        }
        if (role != AccountRole.SUPER_ADMIN && !SharedPakistanTime.isSamePakistanDay(txDateMillis, referenceNowMillis)) {
            return "Authorized Staff can only record same-day recoveries (${SharedPakistanTime.formatDate(referenceNowMillis)} PKT). Only Super Admin can add previous-date recoveries."
        }
        return null
    }
}
