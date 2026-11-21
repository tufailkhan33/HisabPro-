package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Unarchive
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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AccountRole
import com.example.domain.model.ActiveWorkspaceSession
import com.example.domain.model.Booker
import com.example.domain.model.BusinessSettings
import com.example.domain.model.CreditTransaction
import com.example.domain.model.DateFilterPreset
import com.example.domain.model.FinancialEngine
import com.example.domain.model.PaymentMethod
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.Shop
import com.example.domain.model.ShopBalanceSummary
import com.example.domain.model.ShopFilterOption
import com.example.domain.model.ShopLedgerReport
import com.example.domain.model.StaffPermission
import com.example.domain.model.SyncStatus
import com.example.ui.PdfExportMode
import com.example.ui.components.ActionMessageBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabNavyTopBar
import com.example.ui.components.OfflineBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.HisabBlackText
import com.example.ui.theme.HisabBorder
import com.example.ui.theme.HisabErrorBg
import com.example.ui.theme.HisabErrorRed
import com.example.ui.theme.HisabKpiBlueBg
import com.example.ui.theme.HisabKpiBlueIcon
import com.example.ui.theme.HisabLightGray
import com.example.ui.theme.HisabNavy
import com.example.ui.theme.HisabNavyContainer
import com.example.ui.theme.HisabSecondaryGray
import com.example.ui.theme.HisabSuccessBg
import com.example.ui.theme.HisabSuccessGreen
import com.example.ui.theme.HisabWarningBg
import com.example.ui.theme.HisabWarningOrange
import com.example.ui.theme.HisabWhite
import com.example.util.CurrencyFormatter
import com.example.util.PakistanTime
import com.example.util.PasswordHasher

@Composable
fun CreditShopsScreen(
    session: ActiveWorkspaceSession,
    permissions: StaffPermission,
    shopBalances: List<ShopBalanceSummary>,
    bookers: List<Booker>,
    isOnline: Boolean,
    isSubmitting: Boolean,
    actionMessage: Pair<Boolean, String>?,
    initialOpenAddShop: Boolean = false,
    onDismissMessage: () -> Unit,
    onBack: (() -> Unit)? = null,
    onShopClick: (String) -> Unit,
    onCreateShop: (
        shopUid: String,
        shopName: String,
        booker: Booker,
        ownerName: String,
        mobile: String,
        address: String,
        city: String,
        openingCreditPaisa: Long,
        openingDateMillis: Long,
        notes: String,
        onCreated: (Shop) -> Unit
    ) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ShopFilterOption.ALL) }
    var datePreset by remember { mutableStateOf(DateFilterPreset.ALL_TIME) }
    var showAddDialog by remember { mutableStateOf(initialOpenAddShop) }

    val canAddShop = session.role == AccountRole.SUPER_ADMIN ||
        (session.role == AccountRole.STAFF && permissions.addShops)

    val exactUidMatch = remember(searchQuery, shopBalances) {
        val q = searchQuery.trim()
        if (q.length >= 3) {
            shopBalances.firstOrNull { it.shop.shopUid.equals(q, ignoreCase = true) }
        } else null
    }

    val dateBounds = remember(datePreset) {
        when (datePreset) {
            DateFilterPreset.TODAY -> PakistanTime.todayRange()
            DateFilterPreset.YESTERDAY -> PakistanTime.yesterdayRange()
            DateFilterPreset.LAST_7_DAYS -> PakistanTime.lastDaysRange(7)
            DateFilterPreset.LAST_30_DAYS -> PakistanTime.lastDaysRange(30)
            DateFilterPreset.THIS_MONTH -> PakistanTime.thisMonthRange()
            DateFilterPreset.PREVIOUS_MONTH -> PakistanTime.previousMonthRange()
            else -> null
        }
    }

    val filteredShops = remember(shopBalances, searchQuery, selectedFilter, dateBounds) {
        val q = searchQuery.trim().lowercase()
        val base = shopBalances.filter { sb ->
            val matchesQuery = if (q.isEmpty()) true else {
                sb.shop.shopName.lowercase().contains(q) ||
                    sb.shop.shopUid.lowercase().contains(q) ||
                    sb.shop.ownerName.lowercase().contains(q) ||
                    sb.shop.bookerName.lowercase().contains(q) ||
                    sb.shop.mobileNumber.lowercase().contains(q)
            }
            val matchesDate = dateBounds?.let { (start, end) ->
                sb.shop.openingDateMillis in start..end ||
                    (sb.lastTransactionDateMillis ?: 0L) in start..end
            } ?: true

            val matchesFilter = when (selectedFilter) {
                ShopFilterOption.ALL -> !sb.shop.isArchived
                ShopFilterOption.OUTSTANDING -> !sb.shop.isArchived && sb.outstandingPaisa > 0L
                ShopFilterOption.FULLY_RECOVERED -> !sb.shop.isArchived && sb.outstandingPaisa <= 0L
                ShopFilterOption.RECENTLY_ADDED -> !sb.shop.isArchived
                ShopFilterOption.HIGHEST_OUTSTANDING -> !sb.shop.isArchived
                ShopFilterOption.LOWEST_OUTSTANDING -> !sb.shop.isArchived
                ShopFilterOption.ARCHIVED -> sb.shop.isArchived
            }
            matchesQuery && matchesDate && matchesFilter
        }
        when (selectedFilter) {
            ShopFilterOption.HIGHEST_OUTSTANDING -> base.sortedByDescending { it.outstandingPaisa }
            ShopFilterOption.LOWEST_OUTSTANDING -> base.sortedBy { it.outstandingPaisa }
            ShopFilterOption.RECENTLY_ADDED -> base.sortedByDescending { it.shop.openingDateMillis }
            else -> base
        }
    }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = if (session.role == AccountRole.BOOKER) "Assigned Shops" else "Credit Shops",
                onBack = onBack
            )
        },
        containerColor = HisabLightGray
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OfflineBanner(isOnline = isOnline)
            ActionMessageBanner(messageState = actionMessage, onDismiss = onDismissMessage)

            // Search Shop... + right-aligned + Add Shop CTA (matches Panel 6)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HisabWhite)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Shop...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = HisabSecondaryGray) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = HisabLightGray,
                        unfocusedContainerColor = HisabLightGray,
                        focusedBorderColor = HisabNavy,
                        unfocusedBorderColor = HisabBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shops_search_input")
                )

                if (canAddShop) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredShops.size} shops",
                            style = MaterialTheme.typography.labelLarge,
                            color = HisabSecondaryGray
                        )
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("add_shop_fab")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Shop", color = HisabWhite)
                        }
                    }
                }

                if (exactUidMatch != null) {
                    Button(
                        onClick = { onShopClick(exactUidMatch.shop.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Exact Shop UID Match: ${exactUidMatch.shop.shopName} (${exactUidMatch.shop.shopUid})")
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ShopFilterOption.entries) { opt ->
                        if (opt != ShopFilterOption.ARCHIVED || session.role != AccountRole.BOOKER) {
                            FilterChip(
                                selected = selectedFilter == opt,
                                onClick = { selectedFilter = opt },
                                label = { Text(opt.label) }
                            )
                        }
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        listOf(
                            DateFilterPreset.ALL_TIME,
                            DateFilterPreset.TODAY,
                            DateFilterPreset.LAST_7_DAYS,
                            DateFilterPreset.THIS_MONTH,
                            DateFilterPreset.PREVIOUS_MONTH
                        )
                    ) { preset ->
                        FilterChip(
                            selected = datePreset == preset,
                            onClick = { datePreset = preset },
                            label = { Text(preset.label) }
                        )
                    }
                }
            }

            if (filteredShops.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.Default.Storefront,
                        title = "No Matching Shops",
                        message = if (canAddShop) "Tap + to add a new shop or clear your search filters." else "No assigned shops match the active filters."
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredShops, key = { it.shop.id }) { sb ->
                        ShopDirectoryCard(sb = sb, onClick = { onShopClick(sb.shop.id) })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddOrEditShopDialog(
            title = "Add New Shop",
            existingShop = null,
            bookers = bookers.filter { it.status == "ACTIVE" },
            isSubmitting = isSubmitting,
            onDismiss = { showAddDialog = false },
            onSave = { uid, name, booker, owner, mobile, address, city, openingPaisa, notes ->
                onCreateShop(
                    uid, name, booker, owner, mobile, address, city,
                    openingPaisa, PakistanTime.nowMillis(), notes
                ) {
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
fun ShopDirectoryCard(
    sb: ShopBalanceSummary,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = HisabWhite),
        border = BorderStroke(1.dp, HisabBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shop_card_${sb.shop.shopUid}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HisabKpiBlueBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    tint = HisabKpiBlueIcon,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sb.shop.shopName,
                        style = MaterialTheme.typography.titleMedium,
                        color = HisabBlackText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (sb.shop.isArchived) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill("ARCHIVED", HisabWarningBg, HisabWarningOrange)
                    }
                }
                Text(
                    text = "UID: ${sb.shop.shopUid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabSecondaryGray
                )
                Text(
                    text = "BK: ${sb.shop.bookerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabSecondaryGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.formatPkr(sb.totalCreditPaisa),
                    style = MaterialTheme.typography.titleSmall,
                    color = HisabSuccessGreen,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = CurrencyFormatter.formatPkr(sb.outstandingPaisa),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sb.outstandingPaisa > 0) HisabBlackText else HisabSecondaryGray,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open Shop",
                tint = HisabSecondaryGray
            )
        }
    }
}

@Composable
private fun ShopStatCol(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopProfileScreen(
    session: ActiveWorkspaceSession,
    businessName: String = session.businessName,
    settings: BusinessSettings? = null,
    hasUnsyncedData: Boolean = false,
    permissions: StaffPermission,
    summary: ShopBalanceSummary?,
    credits: List<CreditTransaction>,
    recoveries: List<RecoveryTransaction>,
    bookers: List<Booker>,
    isOnline: Boolean,
    isSubmitting: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onAddCreditClick: (String) -> Unit,
    onRecordRecoveryClick: (String) -> Unit,
    onUpdateShop: (Shop, String, Booker, String, String, String, String, String, () -> Unit) -> Unit,
    onToggleArchive: (Shop) -> Unit,
    onReverseCredit: (CreditTransaction, String) -> Unit,
    onReverseRecovery: (RecoveryTransaction, String) -> Unit,
    onCorrectRecovery: (RecoveryTransaction, Long, Long, PaymentMethod, String, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onExportShopPdf: (
        Context,
        ShopBalanceSummary,
        List<CreditTransaction>,
        List<RecoveryTransaction>,
        String,
        PdfExportMode,
        Long?,
        Long?
    ) -> Unit = { ctx, s, c, r, label, mode, startMs, endMs ->
        val uri = com.example.util.PdfGenerator.generateShopStatementPdf(
            ctx, businessName, s, c, r, label, settings, startMs ?: 0L, endMs ?: Long.MAX_VALUE
        )
        when (mode) {
            PdfExportMode.VIEW -> com.example.util.PdfGenerator.viewPdf(ctx, uri)
            PdfExportMode.SHARE -> com.example.util.PdfGenerator.sharePdf(ctx, uri, "${s.shop.shopName} (${s.shop.shopUid}) PDF")
            PdfExportMode.DOWNLOAD -> com.example.util.PdfGenerator.downloadPdfToDevice(
                ctx, uri, "HisabPro_Shop_${s.shop.shopUid}_${System.currentTimeMillis()}.pdf"
            )
        }
    },
    onDownloadPdf: (Context, Uri, String) -> Unit = { ctx, uri, title ->
        com.example.util.PdfGenerator.downloadPdfToDevice(ctx, uri, title)
    }
) {
    val context = LocalContext.current
    var ledgerTab by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var reversalCreditTarget by remember { mutableStateOf<CreditTransaction?>(null) }
    var reversalRecoveryTarget by remember { mutableStateOf<RecoveryTransaction?>(null) }
    var correctRecoveryTarget by remember { mutableStateOf<RecoveryTransaction?>(null) }
    var useCustomPdfRange by remember { mutableStateOf(false) }
    var pdfStartMillis by remember { mutableLongStateOf(PakistanTime.thisMonthRange().first) }
    var pdfEndMillis by remember { mutableLongStateOf(PakistanTime.todayRange().second) }
    var showPdfRangeDialog by remember { mutableStateOf(false) }

    val pdfRangeLabel = if (useCustomPdfRange) {
        "${PakistanTime.formatDashDate(pdfStartMillis)} TO ${PakistanTime.formatDashDate(pdfEndMillis)}"
    } else {
        "All Time"
    }
    val activeStartMs = if (useCustomPdfRange) PakistanTime.startOfDay(pdfStartMillis) else null
    val activeEndMs = if (useCustomPdfRange) PakistanTime.endOfDay(pdfEndMillis) else null

    val ledgerReport = remember(summary?.shop, credits, recoveries, activeStartMs, activeEndMs) {
        summary?.shop?.let {
            FinancialEngine.computeShopLedgerReport(
                shop = it,
                credits = credits,
                recoveries = recoveries,
                startMillis = activeStartMs ?: 0L,
                endMillis = activeEndMs ?: Long.MAX_VALUE
            )
        }
    }

    val isBooker = session.role == AccountRole.BOOKER
    val isSuperAdmin = session.role == AccountRole.SUPER_ADMIN

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = summary?.shop?.shopName ?: "Shop Profile",
                subtitle = summary?.shop?.let { "${it.shopUid} • ${it.city}" },
                onBack = onBack,
                actions = {
                    if (summary != null) {
                        IconButton(
                            onClick = {
                                onExportShopPdf(
                                    context, summary, credits, recoveries, pdfRangeLabel,
                                    PdfExportMode.SHARE, activeStartMs, activeEndMs
                                )
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share Shop PDF")
                        }
                    }
                }
            )
        },
        containerColor = HisabLightGray
    ) { innerPadding ->
        if (summary == null) {
            Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                EmptyStateCard(Icons.Default.Storefront, "Shop Not Found", "This shop may have been removed or belongs to another Booker.")
            }
            return@Scaffold
        }

        val shop = summary.shop
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OfflineBanner(isOnline = isOnline)
            ActionMessageBanner(messageState = actionMessage, onDismiss = onDismissMessage)

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HisabWhite),
                        border = BorderStroke(1.dp, HisabBorder),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(shop.shopName, style = MaterialTheme.typography.titleLarge, color = HisabNavy)
                                StatusPill(
                                    text = if (shop.isArchived) "ARCHIVED" else shop.shopUid,
                                    containerColor = if (shop.isArchived) HisabWarningBg else HisabNavyContainer,
                                    contentColor = if (shop.isArchived) HisabWarningOrange else HisabNavy
                                )
                            }
                            Text("Owner: ${shop.ownerName} • ${shop.mobileNumber}", style = MaterialTheme.typography.bodyMedium)
                            Text("Assigned Booker: ${shop.bookerName}", style = MaterialTheme.typography.bodyMedium, color = HisabNavy)
                            Text("Address: ${shop.address}, ${shop.city}", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                            Text(
                                text = "First Credit: ${summary.firstCreditDateMillis?.let { PakistanTime.formatDate(it) } ?: "N/A"}  •  Last Tx: ${summary.lastTransactionDateMillis?.let { PakistanTime.formatDate(it) } ?: "N/A"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = HisabSecondaryGray
                            )
                            if (shop.notes.isNotBlank()) {
                                Text("Notes: ${shop.notes}", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = HisabBorder)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                ShopStatCol("Total Credit", CurrencyFormatter.formatPkr(summary.totalCreditPaisa), HisabNavy)
                                ShopStatCol("Total Recovered", CurrencyFormatter.formatPkr(summary.totalRecoveredPaisa), HisabSuccessGreen)
                                ShopStatCol("Remaining", CurrencyFormatter.formatPkr(summary.outstandingPaisa), if (summary.outstandingPaisa > 0) HisabErrorRed else HisabSuccessGreen)
                            }
                        }
                    }
                }

                // Action Buttons
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!isBooker) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if ((isSuperAdmin || permissions.addCredit) && !shop.isArchived) {
                                    Button(
                                        onClick = { onAddCreditClick(shop.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Add Credit")
                                    }
                                }
                                if ((isSuperAdmin || permissions.recordRecovery) && !shop.isArchived) {
                                    Button(
                                        onClick = { onRecordRecoveryClick(shop.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = HisabSuccessGreen),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Record Recovery")
                                    }
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isBooker && (isSuperAdmin || permissions.editShops)) {
                                OutlinedButton(
                                    onClick = { showEditDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit Shop")
                                }
                            }
                            if (isSuperAdmin) {
                                OutlinedButton(
                                    onClick = { showArchiveConfirm = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        if (shop.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (shop.isArchived) "Restore" else "Archive")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    onExportShopPdf(
                                        context, summary, credits, recoveries, pdfRangeLabel,
                                        PdfExportMode.VIEW, activeStartMs, activeEndMs
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Shop PDF")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showPdfRangeDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("shop_pdf_range_button")
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (useCustomPdfRange) pdfRangeLabel else "Date Range: All Time")
                            }
                            if (useCustomPdfRange) {
                                TextButton(onClick = { useCustomPdfRange = false }) {
                                    Text("All Time", color = HisabNavy)
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                onExportShopPdf(
                                    context, summary, credits, recoveries, pdfRangeLabel,
                                    PdfExportMode.DOWNLOAD, activeStartMs, activeEndMs
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("download_shop_pdf_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download Shop Statement PDF ($pdfRangeLabel)")
                        }
                    }
                }

                // Ledger / Credit / Recovery Tabs
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HisabWhite),
                        border = BorderStroke(1.dp, HisabBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        TabRow(selectedTabIndex = ledgerTab, containerColor = HisabWhite, contentColor = HisabNavy) {
                            Tab(
                                selected = ledgerTab == 0,
                                onClick = { ledgerTab = 0 },
                                text = { Text("Ledger") },
                                modifier = Modifier.testTag("shop_tab_ledger")
                            )
                            Tab(
                                selected = ledgerTab == 1,
                                onClick = { ledgerTab = 1 },
                                text = { Text("Credit (${credits.size})") },
                                modifier = Modifier.testTag("shop_tab_credit")
                            )
                            Tab(
                                selected = ledgerTab == 2,
                                onClick = { ledgerTab = 2 },
                                text = { Text("Recovery (${recoveries.size})") },
                                modifier = Modifier.testTag("shop_tab_recovery")
                            )
                        }
                    }
                }

                if (ledgerTab == 0 && ledgerReport != null) {
                    item {
                        SupplierLedgerStatementCard(
                            businessName = businessName,
                            settings = settings,
                            shop = shop,
                            ledger = ledgerReport,
                            useCustomRange = useCustomPdfRange,
                            startMillis = activeStartMs ?: 0L,
                            endMillis = activeEndMs ?: Long.MAX_VALUE
                        )
                    }
                } else if (ledgerTab == 1) {
                    if (credits.isEmpty()) {
                        item { EmptyStateCard(Icons.Default.AddCard, "No Credit Entries", "No credit transactions recorded for this shop yet.") }
                    } else {
                        items(credits, key = { it.id }) { tx ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = HisabWhite),
                                border = BorderStroke(1.dp, HisabBorder),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${tx.txCode} • ${tx.txDateFormatted} ${tx.txTimeFormatted}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HisabSecondaryGray
                                        )
                                        Text(
                                            text = CurrencyFormatter.formatPkr(tx.amountPaisa),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (tx.transactionKind == "CREDIT_REVERSAL") HisabWarningOrange else HisabErrorRed,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(tx.description, style = MaterialTheme.typography.bodyMedium)
                                    if (tx.invoiceNumber.isNotBlank()) {
                                        Text("Invoice: ${tx.invoiceNumber}", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("By ${tx.createdByName}", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                                        if (!isBooker && (isSuperAdmin || permissions.editCredit) && tx.transactionKind == "CREDIT") {
                                            TextButton(onClick = { reversalCreditTarget = tx }) {
                                                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Reverse / Correct")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (ledgerTab == 2) {
                    if (recoveries.isEmpty()) {
                        item { EmptyStateCard(Icons.Default.Payments, "No Recoveries", "No recovery payments collected for this shop yet.") }
                    } else {
                        val reversedPairIds = recoveries
                            .filter { it.transactionKind == "RECOVERY_REVERSAL" && it.relatedTxId.isNotBlank() }
                            .map { it.relatedTxId }
                            .toSet()
                        items(recoveries, key = { it.id }) { tx ->
                            val isReversal = tx.transactionKind == "RECOVERY_REVERSAL"
                            val displayAmount = if (isReversal) "- ${CurrencyFormatter.formatPkr(tx.amountPaisa)}" else CurrencyFormatter.formatPkr(tx.amountPaisa)
                            val isReversedOrig = tx.id in reversedPairIds

                            Card(
                                colors = CardDefaults.cardColors(containerColor = HisabWhite),
                                border = BorderStroke(1.dp, HisabBorder),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${tx.txCode} • ${tx.txDateFormatted} ${tx.txTimeFormatted}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HisabSecondaryGray,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = displayAmount,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isReversal) HisabErrorRed else HisabSuccessGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (tx.syncStatus != SyncStatus.SYNCED.key) {
                                            StatusPill(
                                                text = SyncStatus.fromKey(tx.syncStatus).label,
                                                containerColor = HisabWarningBg,
                                                contentColor = HisabWarningOrange
                                            )
                                        }
                                        if (isReversal) {
                                            StatusPill("REVERSED ENTRY", HisabErrorBg, HisabErrorRed)
                                        } else if (isReversedOrig) {
                                            StatusPill("CORRECTED / REVERSED", HisabWarningBg, HisabWarningOrange)
                                        }
                                    }
                                    Text(
                                        text = "Method: ${tx.paymentMethod}${if (tx.referenceNumber.isNotBlank()) " • Ref: ${tx.referenceNumber}" else ""}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (tx.reason.isNotBlank()) {
                                        Text(
                                            text = "Reason: ${tx.reason}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = HisabErrorRed
                                        )
                                    }
                                    if (tx.originalTxDateMillis > 0L && isReversal) {
                                        Text(
                                            text = "Original Date: ${PakistanTime.formatDate(tx.originalTxDateMillis)} • Reversal Date: ${tx.txDateFormatted}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HisabSecondaryGray
                                        )
                                    }
                                    if (tx.notes.isNotBlank()) {
                                        Text(tx.notes, style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("By ${tx.createdByName}", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(
                                                onClick = {
                                                    val uri = com.example.util.PdfGenerator.generateRecoveryReceiptPdf(
                                                        context, businessName, tx, summary.outstandingPaisa
                                                    )
                                                    com.example.util.PdfGenerator.sharePdf(context, uri, "Receipt ${tx.txCode}")
                                                }
                                            ) {
                                                Text("Receipt")
                                            }
                                            IconButton(
                                                onClick = {
                                                    val uri = com.example.util.PdfGenerator.generateRecoveryReceiptPdf(
                                                        context, businessName, tx, summary.outstandingPaisa
                                                    )
                                                    onDownloadPdf(context, uri, "HisabPro_Receipt_${tx.txCode}.pdf")
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = "Download Receipt PDF",
                                                    tint = HisabNavy,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            if (isSuperAdmin && tx.transactionKind == "RECOVERY" && !isReversedOrig) {
                                                TextButton(
                                                    onClick = { correctRecoveryTarget = tx },
                                                    modifier = Modifier.testTag("correct_recovery_${tx.txCode}")
                                                ) {
                                                    Text("Correct", color = HisabNavy)
                                                }
                                                TextButton(
                                                    onClick = { reversalRecoveryTarget = tx },
                                                    modifier = Modifier.testTag("reverse_recovery_${tx.txCode}")
                                                ) {
                                                    Text("Reverse", color = HisabErrorRed)
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
    }

    if (showEditDialog && summary != null) {
        AddOrEditShopDialog(
            title = "Edit Shop (${summary.shop.shopUid})",
            existingShop = summary.shop,
            bookers = bookers.filter { it.status == "ACTIVE" || it.id == summary.shop.bookerId },
            isSubmitting = isSubmitting,
            onDismiss = { showEditDialog = false },
            onSave = { _, name, booker, owner, mobile, address, city, _, notes ->
                onUpdateShop(summary.shop, name, booker, owner, mobile, address, city, notes) {
                    showEditDialog = false
                }
            }
        )
    }

    if (showArchiveConfirm && summary != null) {
        val shop = summary.shop
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            containerColor = HisabWhite,
            title = { Text(if (shop.isArchived) "Restore Shop?" else "Archive Shop?", color = HisabNavy) },
            text = {
                Text(
                    if (shop.isArchived) "Restoring ${shop.shopName} makes it active for new credit and recovery entries."
                    else "Archiving ${shop.shopName} preserves all historical credit and recovery records safely in your immutable audit trail."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleArchive(shop)
                        showArchiveConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HisabNavy)
                ) {
                    Text(if (shop.isArchived) "Restore" else "Archive")
                }
            },
            dismissButton = { TextButton(onClick = { showArchiveConfirm = false }) { Text("Cancel") } }
        )
    }

    if (reversalCreditTarget != null) {
        ReversalReasonDialog(
            title = "Reverse Credit ${reversalCreditTarget!!.txCode}",
            amountLabel = CurrencyFormatter.formatPkr(reversalCreditTarget!!.amountPaisa),
            onDismiss = { reversalCreditTarget = null },
            onConfirm = { reason ->
                onReverseCredit(reversalCreditTarget!!, reason)
                reversalCreditTarget = null
            }
        )
    }

    if (reversalRecoveryTarget != null) {
        ReversalReasonDialog(
            title = "Reverse Recovery ${reversalRecoveryTarget!!.txCode}",
            amountLabel = CurrencyFormatter.formatPkr(reversalRecoveryTarget!!.amountPaisa),
            onDismiss = { reversalRecoveryTarget = null },
            onConfirm = { reason ->
                onReverseRecovery(reversalRecoveryTarget!!, reason)
                reversalRecoveryTarget = null
            }
        )
    }

    if (correctRecoveryTarget != null) {
        CorrectRecoveryDialog(
            original = correctRecoveryTarget!!,
            isSubmitting = isSubmitting,
            onDismiss = { correctRecoveryTarget = null },
            onConfirm = { newAmountPaisa, newDateMillis, newMethod, newRef, newNotes, reason ->
                onCorrectRecovery(
                    correctRecoveryTarget!!,
                    newAmountPaisa,
                    newDateMillis,
                    newMethod,
                    newRef,
                    newNotes,
                    reason
                )
                correctRecoveryTarget = null
            }
        )
    }

    if (showPdfRangeDialog) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = PakistanTime.toDatePickerUtcMillis(pdfStartMillis),
            initialSelectedEndDateMillis = PakistanTime.toDatePickerUtcMillis(pdfEndMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showPdfRangeDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = rangeState.selectedStartDateMillis?.let { PakistanTime.fromDatePickerUtcMillis(it) } ?: pdfStartMillis
                        val e = rangeState.selectedEndDateMillis?.let { PakistanTime.fromDatePickerUtcMillis(it) } ?: s
                        if (s <= e) {
                            pdfStartMillis = s
                            pdfEndMillis = e
                        } else {
                            pdfStartMillis = e
                            pdfEndMillis = s
                        }
                        useCustomPdfRange = true
                        showPdfRangeDialog = false
                    }
                ) { Text("Apply Range") }
            },
            dismissButton = {
                TextButton(onClick = { showPdfRangeDialog = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = rangeState, modifier = Modifier.height(420.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CorrectRecoveryDialog(
    original: RecoveryTransaction,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long, PaymentMethod, String, String, String) -> Unit
) {
    var amountStr by remember {
        mutableStateOf(
            CurrencyFormatter.paisaToBigDecimal(original.amountPaisa).stripTrailingZeros().toPlainString()
        )
    }
    var txDateMillis by remember { mutableLongStateOf(original.txDateMillis) }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.fromLabel(original.paymentMethod)) }
    var ref by remember { mutableStateOf(original.referenceNumber) }
    var notes by remember { mutableStateOf(original.notes) }
    var reason by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf<String?>(null) }
    val parsedPaisa = CurrencyFormatter.parsePkrToPaisa(amountStr)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HisabWhite,
        title = { Text("Correct Payment ${original.txCode}", color = HisabNavy) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Preserves original payment (${CurrencyFormatter.formatPkr(original.amountPaisa)}), creates an audit-linked reversal, and posts the corrected recovery entry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabSecondaryGray
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Corrected Amount (PKR) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("correct_recovery_amount")
                )
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Date: ${PakistanTime.formatDate(txDateMillis)} PKT")
                }
                if (dateError != null) {
                    Text(dateError!!, style = MaterialTheme.typography.bodySmall, color = HisabErrorRed)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PaymentMethod.entries) { m ->
                        FilterChip(
                            selected = selectedMethod == m,
                            onClick = { selectedMethod = m },
                            label = { Text(m.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HisabNavy,
                                selectedLabelColor = HisabWhite
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text("Reference / Receipt ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Correction Reason * (min 3 chars)") },
                    modifier = Modifier.fillMaxWidth().testTag("correct_recovery_reason")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val paisa = parsedPaisa ?: return@Button
                    onConfirm(paisa, txDateMillis, selectedMethod, ref, notes, reason)
                },
                enabled = !isSubmitting && parsedPaisa != null && parsedPaisa > 0L && reason.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                modifier = Modifier.testTag("confirm_correct_recovery")
            ) {
                Text("Save Correction")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = PakistanTime.toDatePickerUtcMillis(txDateMillis))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { picked ->
                            val candidate = PakistanTime.fromDatePickerUtcMillis(picked, txDateMillis)
                            if (PakistanTime.isFuturePakistanDay(candidate)) {
                                dateError = "Future recovery dates are not permitted."
                            } else {
                                dateError = null
                                txDateMillis = candidate
                            }
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrEditShopDialog(
    title: String,
    existingShop: Shop?,
    bookers: List<Booker>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        shopUid: String,
        shopName: String,
        booker: Booker,
        ownerName: String,
        mobile: String,
        address: String,
        city: String,
        openingCreditPaisa: Long,
        notes: String
    ) -> Unit
) {
    val autoUid = remember { existingShop?.shopUid ?: PasswordHasher.generateCode("SHP") }
    var shopName by remember { mutableStateOf(existingShop?.shopName.orEmpty()) }
    var selectedBooker by remember {
        mutableStateOf(
            bookers.firstOrNull { it.id == existingShop?.bookerId } ?: bookers.firstOrNull()
        )
    }
    var bookerMenuExpanded by remember { mutableStateOf(false) }
    var ownerName by remember { mutableStateOf(existingShop?.ownerName.orEmpty()) }
    var mobile by remember { mutableStateOf(existingShop?.mobileNumber.orEmpty()) }
    var address by remember { mutableStateOf(existingShop?.address.orEmpty()) }
    var city by remember { mutableStateOf(existingShop?.city.orEmpty()) }
    var openingCreditStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(existingShop?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HisabWhite,
        title = { Text(title, color = HisabNavy) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = autoUid,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Unique Shop UID (Permanent)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = { Text("Shop Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("shop_name_input")
                )

                if (bookers.isEmpty()) {
                    Text(
                        text = "Please create at least one Active Booker before adding a shop.",
                        color = HisabErrorRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = bookerMenuExpanded,
                        onExpandedChange = { bookerMenuExpanded = !bookerMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedBooker?.let { "${it.fullName} (${it.bookerCode})" } ?: "Select Booker",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Assigned Booker *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bookerMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = bookerMenuExpanded,
                            onDismissRequest = { bookerMenuExpanded = false }
                        ) {
                            bookers.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text("${b.fullName} (${b.bookerCode})") },
                                    onClick = {
                                        selectedBooker = b
                                        bookerMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = { Text("Owner Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("shop_owner_input")
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Mobile Number *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("shop_mobile_input")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Shop Address *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("shop_address_input")
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("shop_city_input")
                )
                if (existingShop == null) {
                    OutlinedTextField(
                        value = openingCreditStr,
                        onValueChange = { openingCreditStr = it },
                        label = { Text("Opening Credit PKR (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Additional Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val booker = selectedBooker ?: return@Button
                    val openingPaisa = if (openingCreditStr.isBlank()) 0L else {
                        CurrencyFormatter.parsePkrToPaisa(openingCreditStr) ?: return@Button
                    }
                    onSave(autoUid, shopName, booker, ownerName, mobile, address, city, openingPaisa, notes)
                },
                enabled = !isSubmitting && selectedBooker != null && shopName.isNotBlank() &&
                    ownerName.isNotBlank() && mobile.isNotBlank() && address.isNotBlank() && city.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                modifier = Modifier.testTag("save_shop_button")
            ) {
                Text("Save Shop")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ReversalReasonDialog(
    title: String,
    amountLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HisabWhite,
        title = { Text(title, color = HisabNavy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Original transaction ($amountLabel) remains permanently in the audit trail. A reversing entry will be created.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabSecondaryGray
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Correction Reason *") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                enabled = reason.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = HisabWarningOrange)
            ) {
                Text("Create Reversal")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SupplierLedgerStatementCard(
    businessName: String,
    settings: BusinessSettings?,
    shop: Shop,
    ledger: ShopLedgerReport,
    useCustomRange: Boolean,
    startMillis: Long,
    endMillis: Long
) {
    val gridColor = HisabBlackText.copy(alpha = 0.78f)
    val startDateText = if (useCustomRange && startMillis > 0L) {
        PakistanTime.formatDashDate(startMillis)
    } else {
        ledger.entries.firstOrNull()?.txDateFormatted ?: PakistanTime.formatDashDate(PakistanTime.nowMillis())
    }
    val endDateText = if (useCustomRange && endMillis < Long.MAX_VALUE) {
        PakistanTime.formatDashDate(endMillis)
    } else {
        PakistanTime.formatDashDate(PakistanTime.nowMillis())
    }
    val displayBusinessName = settings?.businessName?.ifBlank { businessName } ?: businessName
    val displayPhone = settings?.contactPhone?.ifBlank { "" }.orEmpty()
    val displayAddress = settings?.businessAddress?.ifBlank { "" }.orEmpty()

    Card(
        colors = CardDefaults.cardColors(containerColor = HisabWhite),
        border = BorderStroke(1.dp, HisabBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("supplier_ledger_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "SUPPLIER LEDGER",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                ),
                color = HisabSecondaryGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = HisabBlackText, thickness = 1.5.dp)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = displayBusinessName.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = HisabBlackText
                    )
                    Text(
                        text = "Ph. $displayPhone",
                        style = MaterialTheme.typography.bodySmall,
                        color = HisabBlackText
                    )
                    if (displayAddress.isNotBlank()) {
                        Text(
                            text = displayAddress.uppercase(),
                            style = MaterialTheme.typography.bodySmall,
                            color = HisabBlackText
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = buildString {
                            append(shop.shopName.uppercase())
                            if (shop.shopUid.isNotBlank()) append(" (${shop.shopUid})")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = HisabBlackText
                    )
                    Text(
                        text = "Ph. ${shop.mobileNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = HisabBlackText
                    )
                    Text(
                        text = "$startDateText  TO  $endDateText",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = HisabBlackText
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            val wVoucher = 68.dp
            val wDated = 86.dp
            val wDesc = 236.dp
            val wPurchase = 84.dp
            val wRecovery = 88.dp
            val wBalance = 84.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.width(646.dp)) {
                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .background(HisabLightGray.copy(alpha = 0.55f))
                            .border(1.dp, gridColor)
                    ) {
                        LedgerHeaderCell("Voucher #", wVoucher, gridColor)
                        LedgerHeaderCell("Dated", wDated, gridColor)
                        LedgerHeaderCell("Description", wDesc, gridColor)
                        LedgerHeaderCell("Purchase", wPurchase, gridColor)
                        LedgerHeaderCell("Payment /\nRecovery", wRecovery, gridColor)
                        LedgerHeaderCell("Balance", wBalance, gridColor, drawRightDivider = false)
                    }

                    // Opening Balance row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .border(BorderStroke(0.8.dp, gridColor))
                    ) {
                        LedgerTextCell("0", wVoucher, gridColor, TextAlign.Center)
                        LedgerTextCell(startDateText, wDated, gridColor, TextAlign.Center)
                        LedgerTextCell("Opening Balance", wDesc, gridColor, TextAlign.Start)
                        LedgerTextCell("0.0", wPurchase, gridColor, TextAlign.End)
                        LedgerTextCell("0.0", wRecovery, gridColor, TextAlign.End)
                        LedgerTextCell(
                            CurrencyFormatter.formatLedgerAmount(ledger.openingBalancePaisa),
                            wBalance,
                            gridColor,
                            TextAlign.End,
                            drawRightDivider = false
                        )
                    }

                    // Chronological entries
                    ledger.entries.forEach { entry ->
                        val fullDesc = buildString {
                            append(entry.description)
                            if (entry.isReversal) {
                                append("\n[REVERSED] Orig: ${entry.originalPaymentDateFormatted} • Rev: ${entry.reversalDateFormatted}")
                                if (entry.reason.isNotBlank()) append(" • Reason: ${entry.reason}")
                            } else if (entry.isReversedOriginal && entry.reason.isNotBlank()) {
                                append("\n[CORRECTED/REVERSED: ${entry.reason}]")
                            }
                        }
                        val purchaseStr = if (entry.creditPaisa != 0L) {
                            CurrencyFormatter.formatLedgerAmount(entry.creditPaisa)
                        } else {
                            "0.0"
                        }
                        val recoveryStr = if (entry.recoveryPaisa != 0L) {
                            CurrencyFormatter.formatLedgerAmount(entry.recoveryPaisa)
                        } else {
                            "0.0"
                        }
                        val recoveryColor = when {
                            entry.isReversal -> HisabErrorRed
                            entry.recoveryPaisa > 0L -> HisabSuccessGreen
                            else -> HisabBlackText
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .background(
                                    if (entry.isReversal) HisabErrorBg.copy(alpha = 0.45f) else HisabWhite
                                )
                                .border(0.8.dp, gridColor)
                        ) {
                            LedgerTextCell(entry.voucherNumber, wVoucher, gridColor, TextAlign.Center)
                            LedgerTextCell(entry.txDateFormatted, wDated, gridColor, TextAlign.Center)
                            LedgerTextCell(
                                text = fullDesc,
                                width = wDesc,
                                dividerColor = gridColor,
                                textAlign = TextAlign.Start,
                                textColor = if (entry.isReversal) HisabErrorRed else HisabBlackText
                            )
                            LedgerTextCell(purchaseStr, wPurchase, gridColor, TextAlign.End)
                            LedgerTextCell(
                                text = recoveryStr,
                                width = wRecovery,
                                dividerColor = gridColor,
                                textAlign = TextAlign.End,
                                textColor = recoveryColor,
                                fontWeight = if (entry.isReversal || entry.recoveryPaisa > 0L) FontWeight.SemiBold else FontWeight.Normal
                            )
                            LedgerTextCell(
                                text = CurrencyFormatter.formatLedgerAmount(entry.runningBalancePaisa),
                                width = wBalance,
                                dividerColor = gridColor,
                                textAlign = TextAlign.End,
                                fontWeight = FontWeight.Medium,
                                drawRightDivider = false
                            )
                        }
                    }

                    // Attached GRAND TOTAL row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .background(HisabNavyContainer.copy(alpha = 0.35f))
                            .border(1.dp, gridColor)
                    ) {
                        LedgerTextCell(
                            text = "GRAND TOTAL",
                            width = wVoucher + wDated + wDesc,
                            dividerColor = gridColor,
                            textAlign = TextAlign.Start,
                            fontWeight = FontWeight.Bold
                        )
                        LedgerTextCell(
                            text = CurrencyFormatter.formatLedgerAmount(ledger.totalCreditPaisa),
                            width = wPurchase,
                            dividerColor = gridColor,
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.Bold
                        )
                        LedgerTextCell(
                            text = CurrencyFormatter.formatLedgerAmount(ledger.totalRecoveredPaisa),
                            width = wRecovery,
                            dividerColor = gridColor,
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.Bold
                        )
                        LedgerTextCell(
                            text = CurrencyFormatter.formatLedgerAmount(ledger.closingBalancePaisa),
                            width = wBalance,
                            dividerColor = gridColor,
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.Bold,
                            textColor = if (ledger.closingBalancePaisa > 0L) HisabErrorRed else HisabSuccessGreen,
                            drawRightDivider = false
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "Swipe horizontally on the ledger table if needed to inspect all 6 columns.",
                style = MaterialTheme.typography.labelSmall,
                color = HisabSecondaryGray
            )
        }
    }
}

@Composable
private fun LedgerHeaderCell(
    title: String,
    width: androidx.compose.ui.unit.Dp,
    dividerColor: androidx.compose.ui.graphics.Color,
    drawRightDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = HisabBlackText,
                textAlign = TextAlign.Center
            )
        }
        if (drawRightDivider) {
            VerticalDivider(color = dividerColor, thickness = 0.8.dp, modifier = Modifier.fillMaxHeight())
        }
    }
}

@Composable
private fun LedgerTextCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    dividerColor: androidx.compose.ui.graphics.Color,
    textAlign: TextAlign,
    textColor: androidx.compose.ui.graphics.Color = HisabBlackText,
    fontWeight: FontWeight = FontWeight.Normal,
    drawRightDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 7.dp),
            contentAlignment = when (textAlign) {
                TextAlign.Center -> Alignment.Center
                TextAlign.End -> Alignment.CenterEnd
                else -> Alignment.CenterStart
            }
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                color = textColor,
                fontWeight = fontWeight,
                textAlign = textAlign
            )
        }
        if (drawRightDivider) {
            VerticalDivider(color = dividerColor, thickness = 0.8.dp, modifier = Modifier.fillMaxHeight())
        }
    }
}

