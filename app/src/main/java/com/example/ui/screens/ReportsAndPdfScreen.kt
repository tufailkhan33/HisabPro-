package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AccountRole
import com.example.domain.model.ActiveWorkspaceSession
import com.example.domain.model.Booker
import com.example.domain.model.BookerBalanceSummary
import com.example.domain.model.CreditTransaction
import com.example.domain.model.DailyPerformanceReport
import com.example.domain.model.DateFilterPreset
import com.example.domain.model.FinancialEngine
import com.example.domain.model.PeriodReportSummary
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.ShopBalanceSummary
import com.example.ui.PdfExportMode
import com.example.ui.components.ActionMessageBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabNavyTopBar
import com.example.ui.components.OfflineBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.HisabBlackText
import com.example.ui.theme.HisabBorder
import com.example.ui.theme.HisabErrorRed
import com.example.ui.theme.HisabKpiBlueBg
import com.example.ui.theme.HisabKpiBlueIcon
import com.example.ui.theme.HisabKpiGreenBg
import com.example.ui.theme.HisabKpiRedBg
import com.example.ui.theme.HisabLightGray
import com.example.ui.theme.HisabNavy
import com.example.ui.theme.HisabNavyContainer
import com.example.ui.theme.HisabSecondaryGray
import com.example.ui.theme.HisabSoftBlueBtn
import com.example.ui.theme.HisabSuccessGreen
import com.example.ui.theme.HisabWhite
import com.example.util.CurrencyFormatter
import com.example.util.PakistanTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsAndPdfScreen(
    session: ActiveWorkspaceSession,
    initialTab: Int = 1,
    shops: List<com.example.domain.model.Shop>,
    shopBalances: List<ShopBalanceSummary>,
    bookers: List<Booker>,
    bookerSummaries: List<BookerBalanceSummary>,
    creditTransactions: List<CreditTransaction>,
    recoveryTransactions: List<RecoveryTransaction>,
    isOnline: Boolean,
    hasUnsyncedData: Boolean = false,
    actionMessage: Pair<Boolean, String>? = null,
    onDismissMessage: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    onShopClick: (String) -> Unit,
    onExportAllShopsPdf: (Context, List<ShopBalanceSummary>, String, PdfExportMode) -> Unit,
    onExportDailyPdf: (Context, DailyPerformanceReport, PdfExportMode) -> Unit = { _, _, _ -> },
    onExportPeriodPdf: (Context, PeriodReportSummary, String, PdfExportMode) -> Unit = { _, _, _, _ -> },
    onExportBookerPdf: (Context, Booker, List<ShopBalanceSummary>, List<CreditTransaction>, List<RecoveryTransaction>, PdfExportMode, Long?, Long?) -> Unit = { _, _, _, _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val isBooker = session.role == AccountRole.BOOKER
    val maxTabIndex = if (isBooker) 4 else 5

    var selectedTab by remember(initialTab) {
        mutableIntStateOf(initialTab.coerceIn(0, maxTabIndex))
    }

    // Selected anchor date (in PKT) for Daily / Weekly / Monthly (Panel 8 Select Date)
    var anchorDateMillis by remember { mutableLongStateOf(PakistanTime.nowMillis()) }
    var showSingleDatePicker by remember { mutableStateOf(false) }

    // Period filter state for All Shops PDF / Shop-Wise / Booker-Wise
    var selectedPreset by remember { mutableStateOf(DateFilterPreset.THIS_MONTH) }
    var customStartMillis by remember { mutableLongStateOf(PakistanTime.thisMonthRange().first) }
    var customEndMillis by remember { mutableLongStateOf(PakistanTime.todayRange().second) }
    var showCustomRangeDialog by remember { mutableStateOf(false) }

    // PDF View / Share quick modal for Daily/Weekly/Monthly "Generate PDF" button
    var showPeriodPdfChooser by remember { mutableStateOf(false) }

    val effectiveBounds: Pair<Long, Long> = remember(
        selectedTab,
        anchorDateMillis,
        selectedPreset,
        customStartMillis,
        customEndMillis
    ) {
        when (selectedTab) {
            1 -> PakistanTime.dayRangeFor(anchorDateMillis)
            2 -> PakistanTime.weekRangeFor(anchorDateMillis)
            3 -> PakistanTime.monthRangeFor(anchorDateMillis)
            else -> when (selectedPreset) {
                DateFilterPreset.ALL_TIME -> 0L to PakistanTime.endOfDay(PakistanTime.nowMillis())
                DateFilterPreset.TODAY -> PakistanTime.todayRange()
                DateFilterPreset.YESTERDAY -> PakistanTime.yesterdayRange()
                DateFilterPreset.LAST_7_DAYS -> PakistanTime.lastDaysRange(7)
                DateFilterPreset.LAST_30_DAYS -> PakistanTime.lastDaysRange(30)
                DateFilterPreset.THIS_MONTH -> PakistanTime.thisMonthRange()
                DateFilterPreset.PREVIOUS_MONTH -> PakistanTime.previousMonthRange()
                DateFilterPreset.CUSTOM -> PakistanTime.startOfDay(customStartMillis) to PakistanTime.endOfDay(customEndMillis)
            }
        }
    }

    val activeDateLabel = remember(selectedTab, anchorDateMillis, selectedPreset, effectiveBounds) {
        when (selectedTab) {
            1 -> "Daily • ${PakistanTime.formatNumericDate(anchorDateMillis)}"
            2 -> "Weekly (${PakistanTime.formatDate(effectiveBounds.first)} - ${PakistanTime.formatDate(effectiveBounds.second)})"
            3 -> "Monthly (${PakistanTime.formatDate(effectiveBounds.first)} - ${PakistanTime.formatDate(effectiveBounds.second)})"
            else -> if (selectedPreset == DateFilterPreset.ALL_TIME) {
                "All Time (Current Statement)"
            } else {
                "${PakistanTime.formatDate(effectiveBounds.first)} to ${PakistanTime.formatDate(effectiveBounds.second)}"
            }
        }
    }

    val periodReport = remember(shops, creditTransactions, recoveryTransactions, effectiveBounds) {
        FinancialEngine.computePeriodReport(
            shops = shops,
            credits = creditTransactions,
            recoveries = recoveryTransactions,
            startMillis = effectiveBounds.first,
            endMillis = effectiveBounds.second
        )
    }

    val periodShopSummaries = remember(periodReport) {
        periodReport.shopRows
            .filter { !it.shop.isArchived }
            .map { pr ->
                ShopBalanceSummary(
                    shop = pr.shop,
                    totalCreditPaisa = pr.periodCreditPaisa,
                    totalRecoveredPaisa = pr.periodRecoveredPaisa,
                    outstandingPaisa = pr.closingOutstandingAsOfEndDatePaisa,
                    firstCreditDateMillis = null,
                    lastTransactionDateMillis = null,
                    creditCount = 0,
                    recoveryCount = 0
                )
            }
    }

    // All-Shops PDF Builder State
    var pdfScopeMode by remember { mutableIntStateOf(0) } // 0 = All Shops, 1 = Selected Shops, 2 = Specific Booker
    var pdfBookerFilter by remember { mutableStateOf<Booker?>(null) }
    var pdfBookerDropdown by remember { mutableStateOf(false) }
    var pdfSelectedShopIds by remember { mutableStateOf(setOf<String>()) }
    var pdfUseCurrentBalances by remember { mutableStateOf(true) }

    val pdfTargetRows = remember(
        shopBalances,
        periodShopSummaries,
        pdfScopeMode,
        pdfBookerFilter,
        pdfSelectedShopIds,
        pdfUseCurrentBalances,
        isBooker
    ) {
        val baseRows: List<ShopBalanceSummary> = if (pdfUseCurrentBalances) {
            shopBalances.filter { !it.shop.isArchived }
        } else {
            periodShopSummaries
        }
        when {
            pdfScopeMode == 1 -> baseRows.filter { pdfSelectedShopIds.contains(it.shop.id) }
            !isBooker && pdfScopeMode == 2 && pdfBookerFilter != null ->
                baseRows.filter { it.shop.bookerId == pdfBookerFilter!!.id }
            else -> baseRows
        }
    }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Reports",
                onBack = onBack
            )
        },
        containerColor = HisabWhite
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OfflineBanner(isOnline = isOnline)
            ActionMessageBanner(messageState = actionMessage, onDismiss = onDismissMessage)

            // Top Segmented Pill Selector (Daily / Weekly / Monthly matching Panel 8) + Secondary Tools
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HisabWhite)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1 to "Daily", 2 to "Weekly", 3 to "Monthly").forEach { (tabIdx, label) ->
                        val selected = selectedTab == tabIdx
                        Surface(
                            color = if (selected) HisabNavy else HisabSoftBlueBtn,
                            shape = RoundedCornerShape(10.dp),
                            border = if (selected) null else BorderStroke(1.dp, HisabBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedTab = tabIdx }
                                .testTag("report_tab_$tabIdx")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) HisabWhite else HisabNavy,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            label = { Text("All Shops PDF") },
                            modifier = Modifier.testTag("report_tab_0")
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedTab == 4,
                            onClick = { selectedTab = 4 },
                            label = { Text("Shop-Wise") },
                            modifier = Modifier.testTag("report_tab_4")
                        )
                    }
                    if (!isBooker) {
                        item {
                            FilterChip(
                                selected = selectedTab == 5,
                                onClick = { selectedTab = 5 },
                                label = { Text("Booker-Wise") },
                                modifier = Modifier.testTag("report_tab_5")
                            )
                        }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    1, 2, 3 -> {
                        // Panel 8 Select Date field
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Select Date",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = HisabBlackText,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { showSingleDatePicker = true }
                                ) {
                                    OutlinedTextField(
                                        value = when (selectedTab) {
                                            1 -> PakistanTime.formatNumericDate(anchorDateMillis)
                                            else -> "${PakistanTime.formatNumericDate(effectiveBounds.first)} - ${PakistanTime.formatNumericDate(effectiveBounds.second)}"
                                        },
                                        onValueChange = {},
                                        readOnly = true,
                                        enabled = false,
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = "Choose Date",
                                                tint = HisabNavy
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            disabledTextColor = HisabBlackText,
                                            disabledBorderColor = HisabBorder,
                                            disabledContainerColor = HisabWhite,
                                            disabledTrailingIconColor = HisabNavy
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Stacked Pastel Summary Cards (Total Credit / Total Recovery / Outstanding matching Panel 8)
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ReportPastelRowCard(
                                    title = "Total Credit",
                                    value = CurrencyFormatter.formatPkr(periodReport.periodCreditPaisa),
                                    icon = Icons.Default.MonetizationOn,
                                    iconTint = HisabKpiBlueIcon,
                                    backgroundColor = HisabKpiBlueBg
                                )
                                ReportPastelRowCard(
                                    title = "Total Recovery",
                                    value = CurrencyFormatter.formatPkr(periodReport.periodRecoveredPaisa),
                                    icon = Icons.Default.CheckCircle,
                                    iconTint = HisabSuccessGreen,
                                    backgroundColor = HisabKpiGreenBg
                                )
                                ReportPastelRowCard(
                                    title = "Outstanding",
                                    value = CurrencyFormatter.formatPkr(periodReport.closingOutstandingAsOfEndDatePaisa),
                                    icon = Icons.Default.AccountBalanceWallet,
                                    iconTint = HisabErrorRed,
                                    backgroundColor = HisabKpiRedBg
                                )
                            }
                        }

                        // Full-width Navy Generate PDF CTA (matches Panel 8)
                        item {
                            Button(
                                onClick = { showPeriodPdfChooser = true },
                                enabled = periodShopSummaries.isNotEmpty() || shopBalances.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("generate_report_pdf_button")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Generate PDF", style = MaterialTheme.typography.titleMedium, color = HisabWhite)
                            }
                        }

                        // Shop-wise Report Header + "View All ->" (matches Panel 8)
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Shop-wise Report",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HisabBlackText,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { selectedTab = 4 }) {
                                    Text("View All →", color = HisabNavy, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        if (periodReport.shopRows.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.Storefront,
                                    title = "No Shop Activity",
                                    message = "No shops or ledger movements recorded for this date range."
                                )
                            }
                        } else {
                            items(periodReport.shopRows.take(12), key = { "p_shop_${it.shop.id}" }) { row ->
                                Card(
                                    onClick = { onShopClick(row.shop.id) },
                                    colors = CardDefaults.cardColors(containerColor = HisabWhite),
                                    border = BorderStroke(1.dp, HisabBorder),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(HisabKpiBlueBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Storefront,
                                                contentDescription = null,
                                                tint = HisabKpiBlueIcon,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = row.shop.shopName,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = HisabBlackText,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${CurrencyFormatter.formatPkr(row.periodCreditPaisa)} | ${CurrencyFormatter.formatPkr(row.periodRecoveredPaisa)} | ${CurrencyFormatter.formatPkr(row.closingOutstandingAsOfEndDatePaisa)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = HisabNavy,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "Open Shop",
                                            tint = HisabSecondaryGray
                                        )
                                    }
                                }
                            }
                        }

                        if (periodReport.filteredRecoveryTxs.isNotEmpty() || periodReport.filteredCreditTxs.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Transactions (${periodReport.creditTxCount} Credits • ${periodReport.recoveryTxCount} Recoveries)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = HisabNavy,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(periodReport.filteredRecoveryTxs.take(10), key = { "rep_rc_${it.id}" }) { tx ->
                                TransactionHistoryCard(
                                    shopName = tx.shopName,
                                    shopUid = tx.shopUid,
                                    bookerName = tx.bookerName,
                                    amountPaisa = tx.amountPaisa,
                                    txType = if (tx.transactionKind == "RECOVERY_REVERSAL") "Recovery Reversal" else "Recovery (${tx.paymentMethod})",
                                    date = tx.txDateFormatted,
                                    time = tx.txTimeFormatted,
                                    createdBy = tx.createdByName,
                                    isRecovery = true,
                                    isReversal = tx.transactionKind == "RECOVERY_REVERSAL",
                                    onClick = { onShopClick(tx.shopId) }
                                )
                            }
                            items(periodReport.filteredCreditTxs.take(10), key = { "rep_cr_${it.id}" }) { tx ->
                                TransactionHistoryCard(
                                    shopName = tx.shopName,
                                    shopUid = tx.shopUid,
                                    bookerName = tx.bookerName,
                                    amountPaisa = tx.amountPaisa,
                                    txType = if (tx.transactionKind == "CREDIT_REVERSAL") "Credit Reversal" else "Credit Added",
                                    date = tx.txDateFormatted,
                                    time = tx.txTimeFormatted,
                                    createdBy = tx.createdByName,
                                    isRecovery = false,
                                    isReversal = tx.transactionKind == "CREDIT_REVERSAL",
                                    onClick = { onShopClick(tx.shopId) }
                                )
                            }
                        }
                    }

                    0 -> {
                        // Consolidated All-Shops Credit Statement PDF Builder
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = HisabWhite),
                                border = BorderStroke(1.dp, HisabBorder),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Statement Mode", style = MaterialTheme.typography.titleSmall, color = HisabNavy, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (pdfUseCurrentBalances) "Current Live Outstanding Statement" else "Period Activity & Closing Balance ($activeDateLabel)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = HisabSecondaryGray
                                            )
                                        }
                                        Switch(
                                            checked = pdfUseCurrentBalances,
                                            onCheckedChange = { pdfUseCurrentBalances = it },
                                            colors = SwitchDefaults.colors(checkedTrackColor = HisabNavy)
                                        )
                                    }
                                    if (!pdfUseCurrentBalances) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(DateFilterPreset.entries) { preset ->
                                                FilterChip(
                                                    selected = selectedPreset == preset,
                                                    onClick = {
                                                        if (preset == DateFilterPreset.CUSTOM) showCustomRangeDialog = true
                                                        else selectedPreset = preset
                                                    },
                                                    label = { Text(preset.label) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = HisabWhite),
                                border = BorderStroke(1.dp, HisabBorder),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = if (isBooker) "Booker Assigned Shops Statement PDF" else "Consolidated All-Shops Credit Statement PDF",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = HisabNavy,
                                        fontWeight = FontWeight.Bold
                                    )

                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        item {
                                            FilterChip(
                                                selected = pdfScopeMode == 0,
                                                onClick = { pdfScopeMode = 0 },
                                                label = { Text(if (isBooker) "All Assigned Shops" else "All Shops") }
                                            )
                                        }
                                        item {
                                            FilterChip(
                                                selected = pdfScopeMode == 1,
                                                onClick = { pdfScopeMode = 1 },
                                                label = { Text("Selected Shops (${pdfSelectedShopIds.size})") }
                                            )
                                        }
                                        if (!isBooker) {
                                            item {
                                                FilterChip(
                                                    selected = pdfScopeMode == 2,
                                                    onClick = { pdfScopeMode = 2 },
                                                    label = { Text("Specific Booker") }
                                                )
                                            }
                                        }
                                    }

                                    if (!isBooker && pdfScopeMode == 2) {
                                        ExposedDropdownMenuBox(
                                            expanded = pdfBookerDropdown,
                                            onExpandedChange = { pdfBookerDropdown = !pdfBookerDropdown }
                                        ) {
                                            OutlinedTextField(
                                                value = pdfBookerFilter?.let { "${it.fullName} (${it.bookerCode})" } ?: "Select Booker",
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Filter by Booker") },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pdfBookerDropdown) },
                                                modifier = Modifier
                                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                    .fillMaxWidth()
                                            )
                                            ExposedDropdownMenu(
                                                expanded = pdfBookerDropdown,
                                                onDismissRequest = { pdfBookerDropdown = false }
                                            ) {
                                                bookers.forEach { b ->
                                                    DropdownMenuItem(
                                                        text = { Text("${b.fullName} (${b.bookerCode})") },
                                                        onClick = {
                                                            pdfBookerFilter = b
                                                            pdfBookerDropdown = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (pdfScopeMode == 1) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("Select Shops to Include:", style = MaterialTheme.typography.labelMedium, color = HisabNavy)
                                            shopBalances.filter { !it.shop.isArchived }.forEach { sb ->
                                                val checked = pdfSelectedShopIds.contains(sb.shop.id)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            pdfSelectedShopIds = if (checked) pdfSelectedShopIds - sb.shop.id else pdfSelectedShopIds + sb.shop.id
                                                        }
                                                        .padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                                        contentDescription = null,
                                                        tint = HisabNavy
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = "${sb.shop.shopName} (${sb.shop.shopUid})",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = CurrencyFormatter.formatPkr(sb.outstandingPaisa),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = HisabNavy
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = HisabBorder)

                                    val gCredit = pdfTargetRows.sumOf { it.totalCreditPaisa }
                                    val gRec = pdfTargetRows.sumOf { it.totalRecoveredPaisa }
                                    val gOut = pdfTargetRows.sumOf { it.outstandingPaisa }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        ReportMini("Shops", "${pdfTargetRows.size}", HisabNavy)
                                        ReportMini("Total Credit", CurrencyFormatter.formatPkr(gCredit), HisabNavy)
                                        ReportMini("Recovered", CurrencyFormatter.formatPkr(gRec), HisabSuccessGreen)
                                        ReportMini("Outstanding", CurrencyFormatter.formatPkr(gOut), HisabErrorRed)
                                    }

                                    val pdfRangeText = if (pdfUseCurrentBalances) "Current Live Statement (${PakistanTime.formatDate()})" else activeDateLabel
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onExportAllShopsPdf(context, pdfTargetRows, pdfRangeText, PdfExportMode.VIEW)
                                            },
                                            enabled = pdfTargetRows.isNotEmpty(),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("preview_all_shops_pdf_button")
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("View")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onExportAllShopsPdf(context, pdfTargetRows, pdfRangeText, PdfExportMode.DOWNLOAD)
                                            },
                                            enabled = pdfTargetRows.isNotEmpty(),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("download_all_shops_pdf_button")
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Download")
                                        }
                                        Button(
                                            onClick = {
                                                onExportAllShopsPdf(context, pdfTargetRows, pdfRangeText, PdfExportMode.SHARE)
                                            },
                                            enabled = pdfTargetRows.isNotEmpty(),
                                            colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("share_all_shops_pdf_button")
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Share")
                                        }
                                    }
                                }
                            }
                        }

                        items(pdfTargetRows, key = { it.shop.id }) { sb ->
                            ShopMiniSummaryCard(sb = sb, onClick = { onShopClick(sb.shop.id) })
                        }
                    }

                    4 -> {
                        // Shop-Wise Full Historical Report
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(DateFilterPreset.entries) { preset ->
                                    FilterChip(
                                        selected = selectedPreset == preset,
                                        onClick = {
                                            if (preset == DateFilterPreset.CUSTOM) showCustomRangeDialog = true
                                            else selectedPreset = preset
                                        },
                                        label = { Text(preset.label) }
                                    )
                                }
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ReportPastelRowCard(
                                    title = "Period Credit ($activeDateLabel)",
                                    value = CurrencyFormatter.formatPkr(periodReport.periodCreditPaisa),
                                    icon = Icons.Default.MonetizationOn,
                                    iconTint = HisabKpiBlueIcon,
                                    backgroundColor = HisabKpiBlueBg
                                )
                                ReportPastelRowCard(
                                    title = "Period Recovery",
                                    value = CurrencyFormatter.formatPkr(periodReport.periodRecoveredPaisa),
                                    icon = Icons.Default.CheckCircle,
                                    iconTint = HisabSuccessGreen,
                                    backgroundColor = HisabKpiGreenBg
                                )
                                ReportPastelRowCard(
                                    title = "Closing Balance (As Of End)",
                                    value = CurrencyFormatter.formatPkr(periodReport.closingOutstandingAsOfEndDatePaisa),
                                    icon = Icons.Default.AccountBalanceWallet,
                                    iconTint = HisabErrorRed,
                                    backgroundColor = HisabKpiRedBg
                                )
                            }
                        }
                        if (periodReport.shopRows.isEmpty()) {
                            item { EmptyStateCard(Icons.Default.Storefront, "No Shops", "No shop ledger records found.") }
                        } else {
                            items(periodReport.shopRows, key = { "sw_${it.shop.id}" }) { row ->
                                Card(
                                    onClick = { onShopClick(row.shop.id) },
                                    colors = CardDefaults.cardColors(containerColor = HisabWhite),
                                    border = BorderStroke(1.dp, HisabBorder),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(row.shop.shopName, fontWeight = FontWeight.Bold, color = HisabNavy)
                                            StatusPill(row.shop.shopUid, HisabNavyContainer, HisabNavy)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            ReportMini("Period Credit", CurrencyFormatter.formatPkr(row.periodCreditPaisa), HisabNavy)
                                            ReportMini("Period Rec.", CurrencyFormatter.formatPkr(row.periodRecoveredPaisa), HisabSuccessGreen)
                                            ReportMini("As-Of Closing", CurrencyFormatter.formatPkr(row.closingOutstandingAsOfEndDatePaisa), HisabErrorRed)
                                            ReportMini("Current Bal.", CurrencyFormatter.formatPkr(row.currentOutstandingPaisa), HisabNavy)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // Booker-Wise Report
                        if (bookerSummaries.isEmpty()) {
                            item { EmptyStateCard(Icons.Default.Badge, "No Bookers", "No booker performance data available.") }
                        } else {
                            items(bookerSummaries, key = { "bw_${it.booker.id}" }) { bs ->
                                val bShops = remember(shopBalances, bs.booker.id) {
                                    shopBalances.filter { it.shop.bookerId == bs.booker.id && !it.shop.isArchived }
                                }
                                val bCredits = remember(creditTransactions, bs.booker.id) {
                                    creditTransactions.filter { it.bookerId == bs.booker.id }
                                }
                                val bRecoveries = remember(recoveryTransactions, bs.booker.id) {
                                    recoveryTransactions.filter { it.bookerId == bs.booker.id }
                                }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = HisabWhite),
                                    border = BorderStroke(1.dp, HisabBorder),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${bs.booker.fullName} (${bs.booker.bookerCode})", fontWeight = FontWeight.Bold, color = HisabNavy)
                                            StatusPill("${bs.assignedShopsCount} Shops", HisabNavyContainer, HisabNavy)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            ReportMini("Total Credit", CurrencyFormatter.formatPkr(bs.totalCreditPaisa), HisabNavy)
                                            ReportMini("Recovered", CurrencyFormatter.formatPkr(bs.totalRecoveredPaisa), HisabSuccessGreen)
                                            ReportMini("Outstanding", CurrencyFormatter.formatPkr(bs.outstandingPaisa), if (bs.outstandingPaisa > 0) HisabErrorRed else HisabSuccessGreen)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    onExportBookerPdf(context, bs.booker, bShops, bCredits, bRecoveries, PdfExportMode.VIEW, null, null)
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f).height(48.dp)
                                            ) {
                                                Text("View Ledger")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    onExportBookerPdf(context, bs.booker, bShops, bCredits, bRecoveries, PdfExportMode.DOWNLOAD, null, null)
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f).height(48.dp)
                                            ) {
                                                Text("Download")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSingleDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = PakistanTime.toDatePickerUtcMillis(anchorDateMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showSingleDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            anchorDateMillis = PakistanTime.fromDatePickerUtcMillis(it)
                        }
                        showSingleDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showSingleDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showCustomRangeDialog) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = PakistanTime.toDatePickerUtcMillis(customStartMillis),
            initialSelectedEndDateMillis = PakistanTime.toDatePickerUtcMillis(customEndMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showCustomRangeDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = rangeState.selectedStartDateMillis?.let {
                            PakistanTime.fromDatePickerUtcMillis(it)
                        } ?: customStartMillis
                        val end = rangeState.selectedEndDateMillis?.let {
                            PakistanTime.fromDatePickerUtcMillis(it)
                        } ?: start
                        if (start <= end) {
                            customStartMillis = start
                            customEndMillis = end
                        } else {
                            customStartMillis = end
                            customEndMillis = start
                        }
                        selectedPreset = DateFilterPreset.CUSTOM
                        showCustomRangeDialog = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRangeDialog = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = rangeState, modifier = Modifier.height(420.dp))
        }
    }

    if (showPeriodPdfChooser) {
        val dailySnapshot = if (selectedTab == 1) {
            FinancialEngine.computeDailyPerformanceReport(
                shops = shops,
                bookers = bookers,
                credits = creditTransactions,
                recoveries = recoveryTransactions,
                dayMillis = anchorDateMillis
            ).let { rep ->
                if (hasUnsyncedData && !rep.hasUnsyncedData) rep.copy(hasUnsyncedData = true) else rep
            }
        } else null

        fun triggerExport(mode: PdfExportMode) {
            showPeriodPdfChooser = false
            if (dailySnapshot != null) {
                onExportDailyPdf(context, dailySnapshot, mode)
            } else {
                onExportPeriodPdf(context, periodReport, activeDateLabel, mode)
            }
        }

        AlertDialog(
            onDismissRequest = { showPeriodPdfChooser = false },
            containerColor = HisabWhite,
            title = { Text("Generate Statement PDF", color = HisabNavy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (dailySnapshot != null) {
                            "Export Daily Credit & Recovery report for ${dailySnapshot.dateFormatted} (${dailySnapshot.todayCreditCount} credits, ${dailySnapshot.todayRecoveryCount} recoveries)."
                        } else {
                            "Export ${periodReport.shopRows.size} shops for $activeDateLabel as a formatted A4 PDF report."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = HisabSecondaryGray
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { triggerExport(PdfExportMode.VIEW) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("View")
                        }
                        OutlinedButton(
                            onClick = { triggerExport(PdfExportMode.DOWNLOAD) },
                            modifier = Modifier.weight(1f).testTag("download_period_pdf_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Download")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { triggerExport(PdfExportMode.SHARE) },
                    colors = ButtonDefaults.buttonColors(containerColor = HisabNavy)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPeriodPdfChooser = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ReportPastelRowCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    backgroundColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabNavy,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = HisabBlackText,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ReportMini(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
        Text(value, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
    }
}
