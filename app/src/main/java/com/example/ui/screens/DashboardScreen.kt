package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import com.example.domain.model.AccountRole
import com.example.domain.model.ActiveWorkspaceSession
import com.example.domain.model.AuditLog
import com.example.domain.model.Business
import com.example.domain.model.BusinessDashboardSummary
import com.example.domain.model.CreditTransaction
import com.example.domain.model.DailyPerformanceReport
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.ShopBalanceSummary
import com.example.domain.model.StaffPermission
import com.example.ui.PdfExportMode
import com.example.ui.components.ActionMessageBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabProLogoBadge
import com.example.ui.components.OfflineBanner
import com.example.ui.components.PastelKpiCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.HisabBlackText
import com.example.ui.theme.HisabBorder
import com.example.ui.theme.HisabErrorBg
import com.example.ui.theme.HisabErrorRed
import com.example.ui.theme.HisabKpiBlueBg
import com.example.ui.theme.HisabKpiBlueIcon
import com.example.ui.theme.HisabKpiGreenBg
import com.example.ui.theme.HisabKpiPurpleBg
import com.example.ui.theme.HisabKpiPurpleIcon
import com.example.ui.theme.HisabKpiRedBg
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

@Composable
fun DashboardScreen(
    session: ActiveWorkspaceSession,
    business: Business?,
    permissions: StaffPermission,
    summary: BusinessDashboardSummary,
    shopBalances: List<ShopBalanceSummary>,
    creditTransactions: List<CreditTransaction>,
    recoveryTransactions: List<RecoveryTransaction>,
    auditLogs: List<AuditLog>,
    unreadNotifications: Int,
    pakistanClockMillis: Long,
    isOnline: Boolean,
    unsyncedCount: Int = 0,
    isSyncing: Boolean = false,
    dailyPerformanceReport: DailyPerformanceReport? = null,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateSyncCenter: () -> Unit = {},
    onExportDailyPdf: (android.content.Context, PdfExportMode) -> Unit = { _, _ -> },
    onNavigateAddCredit: () -> Unit,
    onNavigateAddBooker: () -> Unit,
    onNavigateAddStaff: () -> Unit,
    onNavigateAddShop: () -> Unit,
    onNavigateRecordRecovery: () -> Unit,
    onNavigateShops: () -> Unit,
    onNavigateReports: () -> Unit,
    onNavigateAllShopsPdf: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateShopDetail: (String) -> Unit,
    onNavigateAllBookerCredit: () -> Unit,
    onNavigateGroupChat: () -> Unit,
    onNavigateActivityLogs: () -> Unit,
    onNavigateSettings: () -> Unit,
    onOpenPlatformOwner: () -> Unit = {},
    onLogoutWorkspace: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var historyTab by remember { mutableIntStateOf(0) }
    val isBooker = session.role == AccountRole.BOOKER
    val isSuperAdmin = session.role == AccountRole.SUPER_ADMIN
    val avatarLetter = session.fullName.trim().firstOrNull()?.uppercase() ?: "H"

    val canViewShops = isBooker || isSuperAdmin || permissions.viewShops
    val canViewReports = isBooker || isSuperAdmin || permissions.viewReports || permissions.generatePdfs
    val canViewRecoveryHistory = isBooker || isSuperAdmin || permissions.viewRecoveryHistory
    val canSearchShops = isBooker || isSuperAdmin || permissions.searchShops || permissions.viewShops

    val historyTabs = remember(isBooker, isSuperAdmin, permissions, canViewRecoveryHistory, canViewShops) {
        buildList {
            add("RECENT" to "All Recent")
            add("CREDITS" to "Credits")
            if (canViewRecoveryHistory) {
                add("RECOVERIES" to "Recoveries")
            }
            if (canViewShops) {
                add("OUTSTANDING" to "Outstanding")
            }
            if (!isBooker && (isSuperAdmin || permissions.viewActivityLogs)) {
                add("ACTIVITIES" to "Activities")
            }
        }
    }
    val activeHistoryTab = historyTab.coerceIn(0, (historyTabs.size - 1).coerceAtLeast(0))

    val combinedRecent = remember(creditTransactions, recoveryTransactions, canViewRecoveryHistory) {
        val credits = creditTransactions.map { tx ->
            UnifiedRecentTx(
                id = "cr_${tx.id}",
                shopId = tx.shopId,
                title = if (tx.transactionKind == "CREDIT_REVERSAL") "Credit Reversal" else "Credit Added",
                subtitle = tx.shopName,
                amountPaisa = tx.amountPaisa,
                isPositiveSign = tx.transactionKind == "CREDIT",
                date = tx.txDateFormatted,
                time = tx.txTimeFormatted,
                sortMillis = tx.txDateMillis,
                isRecovery = false,
                isReversal = tx.transactionKind == "CREDIT_REVERSAL"
            )
        }
        val recs = if (canViewRecoveryHistory) {
            recoveryTransactions.map { tx ->
                UnifiedRecentTx(
                    id = "rc_${tx.id}",
                    shopId = tx.shopId,
                    title = if (tx.transactionKind == "RECOVERY_REVERSAL") "Recovery Reversal" else "Payment Received",
                    subtitle = tx.shopName,
                    amountPaisa = tx.amountPaisa,
                    isPositiveSign = false,
                    date = tx.txDateFormatted,
                    time = tx.txTimeFormatted,
                    sortMillis = tx.txDateMillis,
                    isRecovery = true,
                    isReversal = tx.transactionKind == "RECOVERY_REVERSAL"
                )
            }
        } else {
            emptyList()
        }
        (credits + recs).sortedByDescending { it.sortMillis }.take(20)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HisabLightGray)
    ) {
        // Navy Top Bar matching Panel 3 (Wallet mark + HisabPro + Bell + Circular Avatar)
        Surface(
            color = HisabNavy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    HisabProLogoBadge(sizeDp = 38)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "HisabPro",
                            style = MaterialTheme.typography.titleLarge,
                            color = HisabWhite,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${business?.businessName ?: session.businessName} • ${session.businessCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = HisabWhite.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canSearchShops) {
                        IconButton(
                            onClick = onNavigateSearch,
                            modifier = Modifier.testTag("dashboard_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Global Search",
                                tint = HisabWhite
                            )
                        }
                    }

                    IconButton(
                        onClick = onNavigateGroupChat,
                        modifier = Modifier.testTag("dashboard_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = "Team Group Chat",
                            tint = HisabWhite
                        )
                    }

                    IconButton(
                        onClick = onNavigateNotifications,
                        modifier = Modifier.testTag("dashboard_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifications > 0) {
                                    Badge(containerColor = HisabErrorRed) {
                                        Text(
                                            text = if (unreadNotifications > 99) "99+" else "$unreadNotifications",
                                            color = HisabWhite
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = HisabWhite
                            )
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    Box {
                        Surface(
                            color = HisabWhite.copy(alpha = 0.18f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, HisabWhite.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { menuExpanded = true }
                                .testTag("dashboard_menu_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = avatarLetter,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HisabWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Code: ${session.businessCode}") },
                                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                                onClick = { menuExpanded = false }
                            )
                            if (!isBooker && (isSuperAdmin || permissions.addCredit)) {
                                DropdownMenuItem(
                                    text = { Text("Add Credit") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateAddCredit()
                                    }
                                )
                            }
                            if (isSuperAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Add User & Permissions") },
                                    leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateAddStaff()
                                    }
                                )
                            }
                            if (!isBooker && (isSuperAdmin || permissions.manageBookers)) {
                                DropdownMenuItem(
                                    text = { Text("Add Booker") },
                                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateAddBooker()
                                    }
                                )
                            }
                            if (!isBooker && (isSuperAdmin || permissions.addShops)) {
                                DropdownMenuItem(
                                    text = { Text("Add Shop") },
                                    leadingIcon = { Icon(Icons.Default.AddBusiness, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateAddShop()
                                    }
                                )
                            }
                            if (!isBooker && (isSuperAdmin || permissions.viewBookers || permissions.manageBookers)) {
                                DropdownMenuItem(
                                    text = { Text("All Booker Credit") },
                                    leadingIcon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateAllBookerCredit()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Team Group Chat") },
                                leadingIcon = { Icon(Icons.Default.Forum, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateGroupChat()
                                }
                            )
                            if (isSuperAdmin || permissions.generatePdfs) {
                                DropdownMenuItem(
                                    text = { Text("All Shops PDF") },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateAllShopsPdf()
                                    }
                                )
                            }
                            if (!isBooker && (isSuperAdmin || permissions.viewActivityLogs)) {
                                DropdownMenuItem(
                                    text = { Text("Activity Logs") },
                                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateActivityLogs()
                                    }
                                )
                            }
                            if (isSuperAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateSettings()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Offline Sync Center ($unsyncedCount)") },
                                leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onNavigateSyncCenter()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Owner Control Portal") },
                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenPlatformOwner()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Logout Account", color = HisabErrorRed) },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        tint = HisabErrorRed
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onLogoutWorkspace()
                                }
                            )
                        }
                    }
                }
            }
        }

        OfflineBanner(isOnline = isOnline, unsyncedCount = unsyncedCount)
        ActionMessageBanner(messageState = actionMessage, onDismiss = onDismissMessage)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Welcome Header Row + PKT Date/Time Box (matches Panel 3)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Welcome ${session.fullName},",
                            style = MaterialTheme.typography.titleMedium,
                            color = HisabBlackText,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = session.role.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HisabNavy,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Surface(
                        color = HisabWhite,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HisabBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = PakistanTime.formatDate(pakistanClockMillis),
                                style = MaterialTheme.typography.labelMedium,
                                color = HisabNavy,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${PakistanTime.formatTime(pakistanClockMillis)} PKT",
                                style = MaterialTheme.typography.labelSmall,
                                color = HisabSecondaryGray
                            )
                        }
                    }
                }
            }

            // 2x2 Pastel KPI Grid (matches Panel 3)
            item {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isWide = maxWidth >= 700.dp
                    val cards = listOf(
                        PastelKpiData(
                            title = "Total Credit",
                            value = CurrencyFormatter.formatPkr(summary.totalCreditPaisa),
                            icon = Icons.Default.MonetizationOn,
                            tint = HisabKpiBlueIcon,
                            bg = HisabKpiBlueBg,
                            onClick = if (canViewReports) onNavigateReports else ({})
                        ),
                        PastelKpiData(
                            title = "Total Recovered",
                            value = CurrencyFormatter.formatPkr(summary.totalRecoveredPaisa),
                            icon = Icons.Default.CheckCircle,
                            tint = HisabSuccessGreen,
                            bg = HisabKpiGreenBg,
                            onClick = if (canViewReports) onNavigateReports else ({})
                        ),
                        PastelKpiData(
                            title = "Outstanding",
                            value = CurrencyFormatter.formatPkr(summary.totalOutstandingPaisa),
                            icon = Icons.Default.AccountBalanceWallet,
                            tint = HisabErrorRed,
                            bg = HisabKpiRedBg,
                            onClick = if (canViewShops) onNavigateShops else ({})
                        ),
                        PastelKpiData(
                            title = if (isBooker) "Assigned Shops" else "Total Shops",
                            value = "${summary.activeShops}",
                            icon = Icons.Default.Storefront,
                            tint = HisabKpiPurpleIcon,
                            bg = HisabKpiPurpleBg,
                            onClick = if (canViewShops) onNavigateShops else ({})
                        )
                    )

                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            cards.forEach { card ->
                                PastelKpiCard(
                                    title = card.title,
                                    value = card.value,
                                    icon = card.icon,
                                    iconTint = card.tint,
                                    backgroundColor = card.bg,
                                    onClick = card.onClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            cards.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    pair.forEach { card ->
                                        PastelKpiCard(
                                            title = card.title,
                                            value = card.value,
                                            icon = card.icon,
                                            iconTint = card.tint,
                                            backgroundColor = card.bg,
                                            onClick = card.onClick,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Today's Daily Credit & Recovery Summary Card
            if (dailyPerformanceReport != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HisabWhite),
                        border = BorderStroke(1.dp, HisabBorder),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_daily_performance_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Today's Credit & Recovery",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = HisabNavy,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${dailyPerformanceReport.dateFormatted} PKT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HisabSecondaryGray
                                    )
                                }
                                if (canViewReports) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = { onExportDailyPdf(context, PdfExportMode.VIEW) },
                                            modifier = Modifier.testTag("dashboard_daily_pdf_view")
                                        ) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Daily PDF")
                                        }
                                        IconButton(
                                            onClick = { onExportDailyPdf(context, PdfExportMode.DOWNLOAD) },
                                            modifier = Modifier.testTag("dashboard_daily_pdf_download")
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = "Download Daily PDF", tint = HisabNavy)
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Today's Credit", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                                    Text(
                                        text = CurrencyFormatter.formatPkr(dailyPerformanceReport.todayCreditPaisa),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = HisabNavy,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${dailyPerformanceReport.todayCreditCount} entries • ${dailyPerformanceReport.shopsReceivingCreditToday.size} shops",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HisabSecondaryGray
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Today's Recovery", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                                    Text(
                                        text = CurrencyFormatter.formatPkr(dailyPerformanceReport.todayRecoveryPaisa),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = HisabSuccessGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${dailyPerformanceReport.todayRecoveryCount} entries • ${dailyPerformanceReport.shopsMakingRecoveryToday.size} shops",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HisabSecondaryGray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Primary Navy CTA Button (+ Add Credit for Admin/Staff, All Shops PDF for Booker/Permitted Staff)
            if ((!isBooker && (isSuperAdmin || permissions.addCredit)) || isBooker || isSuperAdmin || permissions.generatePdfs) {
                item {
                    if (!isBooker && (isSuperAdmin || permissions.addCredit)) {
                        Button(
                            onClick = onNavigateAddCredit,
                            colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("action_add_credit")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add Credit", style = MaterialTheme.typography.titleMedium, color = HisabWhite)
                        }
                    } else {
                        Button(
                            onClick = onNavigateAllShopsPdf,
                            colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("action_all_shops_pdf")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("All Shops PDF Statement", style = MaterialTheme.typography.titleMedium, color = HisabWhite)
                        }
                    }
                }
            }

            // Quick Actions Strip (compact rounded pills preserving every role-scoped action)
            item {
                val quickPills = buildList {
                    if (!isBooker && (isSuperAdmin || permissions.recordRecovery)) {
                        add(QuickPill("Record Recovery", Icons.Default.Payments, HisabSuccessGreen, "action_record_recovery", onNavigateRecordRecovery))
                    }
                    if (!isBooker && (isSuperAdmin || permissions.addShops)) {
                        add(QuickPill("Add Shop", Icons.Default.AddBusiness, HisabNavy, "action_add_shop", onNavigateAddShop))
                    }
                    if (!isBooker && (isSuperAdmin || permissions.manageBookers)) {
                        add(QuickPill("Add Booker", Icons.Default.Badge, HisabWarningOrange, "action_add_booker", onNavigateAddBooker))
                    }
                    if (!isBooker && (isSuperAdmin || permissions.viewBookers || permissions.manageBookers)) {
                        add(QuickPill("All Booker Credit", Icons.Default.Assessment, HisabNavy, "action_all_booker_credit", onNavigateAllBookerCredit))
                    }
                    add(
                        QuickPill(
                            label = if (isSyncing) "Syncing..." else if (unsyncedCount > 0) "Sync ($unsyncedCount)" else "Sync Center",
                            icon = Icons.Default.CloudSync,
                            tint = if (unsyncedCount > 0) HisabWarningOrange else HisabNavy,
                            tag = "action_sync_center",
                            onClick = onNavigateSyncCenter
                        )
                    )
                    add(QuickPill("Group Chat", Icons.Default.Forum, HisabNavy, "action_group_chat", onNavigateGroupChat))
                    if (isSuperAdmin) {
                        add(QuickPill("Add User (${summary.totalStaff})", Icons.Default.Group, HisabNavy, "action_staff", onNavigateAddStaff))
                    }
                    if (isSuperAdmin || permissions.generatePdfs) {
                        add(QuickPill("All Shops PDF", Icons.Default.PictureAsPdf, HisabErrorRed, "action_all_shops_pdf", onNavigateAllShopsPdf))
                    }
                    if (isSuperAdmin) {
                        add(QuickPill("Settings", Icons.Default.Settings, HisabNavy, "action_settings", onNavigateSettings))
                    }
                    if (canViewShops) {
                        add(QuickPill("Credit Shops", Icons.Default.Storefront, HisabNavy, "action_credit_shops", onNavigateShops))
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickPills, key = { it.tag }) { pill ->
                        Surface(
                            color = HisabWhite,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HisabBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { pill.onClick() }
                                .testTag(pill.tag)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = pill.icon,
                                    contentDescription = pill.label,
                                    tint = pill.tint,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = pill.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = HisabNavy
                                )
                            }
                        }
                    }
                }
            }

            // Recent Transactions Header + "View All ->" (matches Panel 3)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium,
                        color = HisabBlackText,
                        fontWeight = FontWeight.Bold
                    )
                    if (canViewReports) {
                        TextButton(onClick = onNavigateReports) {
                            Text("View All →", color = HisabNavy, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // History Filter Tabs
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = HisabWhite),
                    border = BorderStroke(1.dp, HisabBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = activeHistoryTab,
                        containerColor = HisabWhite,
                        contentColor = HisabNavy,
                        edgePadding = 6.dp
                    ) {
                        historyTabs.forEachIndexed { idx, (_, label) ->
                            Tab(
                                selected = activeHistoryTab == idx,
                                onClick = { historyTab = idx },
                                text = { Text(label, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }
                }
            }

            when (historyTabs.getOrNull(activeHistoryTab)?.first) {
                "RECENT" -> {
                    if (combinedRecent.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                title = "No Transactions Yet",
                                message = "Credit added and recovery payments will appear here automatically."
                            )
                        }
                    } else {
                        items(combinedRecent, key = { it.id }) { item ->
                            MockupRecentTxCard(
                                item = item,
                                onClick = { if (canViewShops) onNavigateShopDetail(item.shopId) }
                            )
                        }
                    }
                }
                "CREDITS" -> {
                    val recentCredits = creditTransactions.take(15)
                    if (recentCredits.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                title = "No Credit Entries",
                                message = "Credit entries added for shops will appear here."
                            )
                        }
                    } else {
                        items(recentCredits, key = { it.id }) { tx ->
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
                                onClick = { if (canViewShops) onNavigateShopDetail(tx.shopId) }
                            )
                        }
                    }
                }
                "RECOVERIES" -> {
                    val recentRecoveries = recoveryTransactions.take(15)
                    if (recentRecoveries.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Payments,
                                title = "No Recovery Entries",
                                message = "Recorded recovery collections will appear here."
                            )
                        }
                    } else {
                        items(recentRecoveries, key = { it.id }) { tx ->
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
                                onClick = { if (canViewShops) onNavigateShopDetail(tx.shopId) }
                            )
                        }
                    }
                }
                "OUTSTANDING" -> {
                    val outstandingShops = shopBalances
                        .filter { !it.shop.isArchived && it.outstandingPaisa > 0L }
                        .sortedByDescending { it.outstandingPaisa }
                        .take(15)
                    if (outstandingShops.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.CheckCircle,
                                title = "All Clear",
                                message = "All active shops are fully recovered."
                            )
                        }
                    } else {
                        items(outstandingShops, key = { it.shop.id }) { sb ->
                            ShopMiniSummaryCard(sb = sb, onClick = { if (canViewShops) onNavigateShopDetail(sb.shop.id) })
                        }
                    }
                }
                else -> {
                    val recentLogs = auditLogs.take(20)
                    if (recentLogs.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.History,
                                title = "No Account Activities",
                                message = "Workspace audit events will appear here permanently."
                            )
                        }
                    } else {
                        items(recentLogs, key = { it.id }) { log ->
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
                                        StatusPill(log.action, HisabNavyContainer, HisabNavy)
                                        Text(
                                            text = "${log.dateFormatted} • ${log.timeFormatted}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = HisabSecondaryGray
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(log.description, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "By ${log.username} (${log.role})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HisabSecondaryGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class PastelKpiData(
    val title: String,
    val value: String,
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
    val onClick: () -> Unit
)

private data class QuickPill(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val tag: String,
    val onClick: () -> Unit
)

private data class UnifiedRecentTx(
    val id: String,
    val shopId: String,
    val title: String,
    val subtitle: String,
    val amountPaisa: Long,
    val isPositiveSign: Boolean,
    val date: String,
    val time: String,
    val sortMillis: Long,
    val isRecovery: Boolean,
    val isReversal: Boolean
)

@Composable
private fun MockupRecentTxCard(
    item: UnifiedRecentTx,
    onClick: () -> Unit
) {
    val amountColor = when {
        item.isReversal -> HisabWarningOrange
        item.isRecovery -> HisabWarningOrange
        else -> HisabSuccessGreen
    }
    val prefix = if (item.isRecovery) "- " else "+ "

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = HisabWhite),
        border = BorderStroke(1.dp, HisabBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
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
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = HisabBlackText,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = HisabSecondaryGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$prefix${CurrencyFormatter.formatPkr(item.amountPaisa)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = amountColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
                Text(
                    text = "${item.date}, ${item.time}",
                    style = MaterialTheme.typography.labelSmall,
                    color = HisabSecondaryGray
                )
            }
        }
    }
}

@Composable
fun TransactionHistoryCard(
    shopName: String,
    shopUid: String,
    bookerName: String,
    amountPaisa: Long,
    txType: String,
    date: String,
    time: String,
    createdBy: String,
    isRecovery: Boolean,
    isReversal: Boolean,
    onClick: () -> Unit
) {
    val badgeBg = when {
        isReversal -> HisabWarningBg
        isRecovery -> HisabSuccessBg
        else -> HisabErrorBg
    }
    val badgeColor = when {
        isReversal -> HisabWarningOrange
        isRecovery -> HisabSuccessGreen
        else -> HisabErrorRed
    }

    Card(
        onClick = onClick,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shopName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$shopUid  •  Booker: $bookerName",
                        style = MaterialTheme.typography.bodySmall,
                        color = HisabSecondaryGray
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = CurrencyFormatter.formatPkr(amountPaisa),
                    style = MaterialTheme.typography.titleMedium,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(text = txType, containerColor = badgeBg, contentColor = badgeColor)
                Text(
                    text = "$date • $time",
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabSecondaryGray
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Created by: $createdBy",
                style = MaterialTheme.typography.bodySmall,
                color = HisabSecondaryGray
            )
        }
    }
}

@Composable
fun ShopMiniSummaryCard(
    sb: ShopBalanceSummary,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = HisabWhite),
        border = BorderStroke(1.dp, HisabBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(sb.shop.shopName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "${sb.shop.shopUid} • ${sb.shop.ownerName} • Booker: ${sb.shop.bookerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabSecondaryGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.formatPkr(sb.outstandingPaisa),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (sb.outstandingPaisa > 0) HisabErrorRed else HisabSuccessGreen,
                    fontWeight = FontWeight.Bold
                )
                Text("Outstanding", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
            }
        }
    }
}
