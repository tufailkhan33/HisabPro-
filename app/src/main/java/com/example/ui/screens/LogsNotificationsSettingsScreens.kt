package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.util.PakistanTime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.AccountRole
import com.example.domain.model.ActiveWorkspaceSession
import com.example.domain.model.AuditLog
import com.example.domain.model.Booker
import com.example.domain.model.Business
import com.example.domain.model.BusinessSettings
import com.example.domain.model.CreditTransaction
import com.example.domain.model.NotificationItem
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.ShopBalanceSummary
import com.example.ui.components.ActionMessageBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabNavyTopBar
import com.example.ui.components.OfflineBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.HisabBorder
import com.example.ui.theme.HisabErrorRed
import com.example.ui.theme.HisabLightGray
import com.example.ui.theme.HisabNavy
import com.example.ui.theme.HisabNavyContainer
import com.example.ui.theme.HisabSecondaryGray
import com.example.ui.theme.HisabSuccessBg
import com.example.ui.theme.HisabSuccessGreen
import com.example.ui.theme.HisabWarningBg
import com.example.ui.theme.HisabWarningOrange
import com.example.ui.theme.HisabWhite

@Composable
fun GlobalSearchScreen(
    session: ActiveWorkspaceSession,
    shopBalances: List<ShopBalanceSummary>,
    bookers: List<Booker>,
    creditTransactions: List<CreditTransaction>,
    recoveryTransactions: List<RecoveryTransaction>,
    isOnline: Boolean,
    onBack: () -> Unit,
    onShopClick: (String) -> Unit,
    onBookerClick: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") } // ALL, OUTSTANDING, RECOVERED
    val isBooker = session.role == AccountRole.BOOKER

    val q = query.trim().lowercase()
    val exactUidShop = remember(q, shopBalances) {
        if (q.length >= 3) shopBalances.firstOrNull { it.shop.shopUid.lowercase() == q } else null
    }

    val matchedShops = remember(q, filterMode, shopBalances) {
        shopBalances.filter { sb ->
            val matchesQ = q.isEmpty() ||
                sb.shop.shopName.lowercase().contains(q) ||
                sb.shop.shopUid.lowercase().contains(q) ||
                sb.shop.ownerName.lowercase().contains(q) ||
                sb.shop.bookerName.lowercase().contains(q) ||
                sb.shop.mobileNumber.lowercase().contains(q)
            val matchesBal = when (filterMode) {
                "OUTSTANDING" -> sb.outstandingPaisa > 0L
                "RECOVERED" -> sb.outstandingPaisa <= 0L
                else -> true
            }
            matchesQ && matchesBal
        }
    }

    val matchedBookers = remember(q, bookers, isBooker) {
        if (isBooker || q.isEmpty()) emptyList()
        else bookers.filter {
            it.fullName.lowercase().contains(q) ||
                it.bookerCode.lowercase().contains(q) ||
                it.mobileNumber.lowercase().contains(q)
        }
    }

    val matchedCredits = remember(q, creditTransactions) {
        if (q.isEmpty()) emptyList()
        else creditTransactions.filter {
            it.shopName.lowercase().contains(q) ||
                it.shopUid.lowercase().contains(q) ||
                it.txCode.lowercase().contains(q) ||
                it.invoiceNumber.lowercase().contains(q)
        }.take(10)
    }

    val matchedRecoveries = remember(q, recoveryTransactions) {
        if (q.isEmpty()) emptyList()
        else recoveryTransactions.filter {
            it.shopName.lowercase().contains(q) ||
                it.shopUid.lowercase().contains(q) ||
                it.txCode.lowercase().contains(q) ||
                it.referenceNumber.lowercase().contains(q)
        }.take(10)
    }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Search & Filter",
                subtitle = "Find Shop Name, UID, Booker, Phone or Tx Code",
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

            Surface(color = HisabWhite, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Enter Shop UID (SHP-...), Name, Booker, or Mobile") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("global_search_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = filterMode == "ALL", onClick = { filterMode = "ALL" }, label = { Text("All") })
                        FilterChip(selected = filterMode == "OUTSTANDING", onClick = { filterMode = "OUTSTANDING" }, label = { Text("Outstanding") })
                        FilterChip(selected = filterMode == "RECOVERED", onClick = { filterMode = "RECOVERED" }, label = { Text("Recovered") })
                    }
                    if (exactUidShop != null) {
                        Button(
                            onClick = { onShopClick(exactUidShop.shop.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Direct Match: ${exactUidShop.shop.shopName} (${exactUidShop.shop.shopUid})")
                        }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (matchedBookers.isNotEmpty()) {
                    item {
                        Text("Matching Bookers (${matchedBookers.size})", style = MaterialTheme.typography.titleSmall, color = HisabNavy, fontWeight = FontWeight.Bold)
                    }
                    items(matchedBookers, key = { "sb_${it.id}" }) { b ->
                        Card(
                            onClick = { onBookerClick(b.id) },
                            colors = CardDefaults.cardColors(containerColor = HisabWhite),
                            border = BorderStroke(1.dp, HisabBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(b.fullName, fontWeight = FontWeight.Bold)
                                    Text("${b.bookerCode} • ${b.mobileNumber}", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                                }
                                StatusPill("Booker", HisabWarningBg, HisabWarningOrange)
                            }
                        }
                    }
                }

                item {
                    Text("Shops (${matchedShops.size})", style = MaterialTheme.typography.titleSmall, color = HisabNavy, fontWeight = FontWeight.Bold)
                }

                if (matchedShops.isEmpty()) {
                    item {
                        EmptyStateCard(Icons.Default.Search, "No Shops Match", "Try searching by partial shop name, Shop UID, owner name, or mobile number.")
                    }
                } else {
                    items(matchedShops, key = { "ss_${it.shop.id}" }) { sb ->
                        ShopMiniSummaryCard(sb = sb, onClick = { onShopClick(sb.shop.id) })
                    }
                }

                if (matchedCredits.isNotEmpty() || matchedRecoveries.isNotEmpty()) {
                    item {
                        Text("Matching Transactions", style = MaterialTheme.typography.titleSmall, color = HisabNavy, fontWeight = FontWeight.Bold)
                    }
                    items(matchedCredits, key = { "sc_${it.id}" }) { tx ->
                        TransactionHistoryCard(
                            shopName = tx.shopName,
                            shopUid = tx.shopUid,
                            bookerName = tx.bookerName,
                            amountPaisa = tx.amountPaisa,
                            txType = tx.txCode,
                            date = tx.txDateFormatted,
                            time = tx.txTimeFormatted,
                            createdBy = tx.createdByName,
                            isRecovery = false,
                            isReversal = tx.transactionKind == "CREDIT_REVERSAL",
                            onClick = { onShopClick(tx.shopId) }
                        )
                    }
                    items(matchedRecoveries, key = { "sr_${it.id}" }) { tx ->
                        TransactionHistoryCard(
                            shopName = tx.shopName,
                            shopUid = tx.shopUid,
                            bookerName = tx.bookerName,
                            amountPaisa = tx.amountPaisa,
                            txType = "${tx.txCode} (${tx.paymentMethod})",
                            date = tx.txDateFormatted,
                            time = tx.txTimeFormatted,
                            createdBy = tx.createdByName,
                            isRecovery = true,
                            isReversal = tx.transactionKind == "RECOVERY_REVERSAL",
                            onClick = { onShopClick(tx.shopId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityLogsScreen(
    auditLogs: List<AuditLog>,
    isOnline: Boolean,
    onBack: () -> Unit
) {
    var category by remember { mutableStateOf("ALL") }
    val filtered = remember(auditLogs, category) {
        when (category) {
            "CREDIT" -> auditLogs.filter { it.action.contains("CREDIT", true) }
            "RECOVERY" -> auditLogs.filter { it.action.contains("RECOVERY", true) }
            "SHOP" -> auditLogs.filter { it.action.contains("SHOP", true) }
            "SECURITY" -> auditLogs.filter {
                it.action.contains("PASSWORD", true) ||
                    it.action.contains("STAFF", true) ||
                    it.action.contains("BOOKER", true) ||
                    it.action.contains("SETTINGS", true)
            }
            else -> auditLogs
        }
    }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Activity Audit Logs",
                subtitle = "${filtered.size} immutable audit events",
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

            Surface(color = HisabWhite, modifier = Modifier.fillMaxWidth()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chips = listOf("ALL" to "All", "CREDIT" to "Credit", "RECOVERY" to "Recovery", "SHOP" to "Shops", "SECURITY" to "Accounts & Security")
                    items(chips) { (key, label) ->
                        FilterChip(
                            selected = category == key,
                            onClick = { category = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(Icons.Default.History, "No Audit Logs", "Workspace activity logs will be recorded automatically here.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { log ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = HisabWhite),
                            border = BorderStroke(1.dp, HisabBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPill(log.action, HisabNavyContainer, HisabNavy)
                                    Text(
                                        text = "${log.dateFormatted} • ${log.timeFormatted}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HisabSecondaryGray
                                    )
                                }
                                Text(log.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                if (log.previousValue.isNotBlank() || log.newValue.isNotBlank()) {
                                    Text(
                                        text = "${log.previousValue.ifBlank { "-" }} → ${log.newValue.ifBlank { "-" }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HisabNavy
                                    )
                                }
                                Text(
                                    text = "${log.activityCode} • By ${log.username} (${log.role})${if (log.shopUid.isNotBlank()) " • ${log.shopUid}" else ""}",
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

@Composable
fun NotificationsScreen(
    notifications: List<NotificationItem>,
    isOnline: Boolean,
    actionMessage: Pair<Boolean, String>? = null,
    onDismissMessage: () -> Unit = {},
    onBack: () -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit
) {
    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Notifications",
                subtitle = "${notifications.count { !it.isRead }} unread alerts",
                onBack = onBack,
                actions = {
                    if (notifications.any { !it.isRead }) {
                        TextButton(
                            onClick = onMarkAllRead,
                            modifier = Modifier.testTag("mark_all_notifications_read_button")
                        ) {
                            Text("Mark All Read", color = HisabWhite)
                        }
                    }
                }
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

            if (notifications.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(Icons.Default.Notifications, "No Notifications", "New credits, recoveries, and security updates appear here.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        Card(
                            onClick = { if (!item.isRead) onMarkRead(item.id) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.isRead) HisabWhite else HisabNavyContainer.copy(alpha = 0.45f)
                            ),
                            border = BorderStroke(1.dp, if (item.isRead) HisabBorder else HisabNavy.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = HisabNavy)
                                    if (!item.isRead) {
                                        StatusPill("NEW", HisabSuccessBg, HisabSuccessGreen)
                                    }
                                }
                                Text(item.message, style = MaterialTheme.typography.bodyMedium)
                                if (item.shopUid.isNotBlank()) {
                                    Text("Shop UID: ${item.shopUid}", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BusinessSettingsScreen(
    session: ActiveWorkspaceSession,
    business: Business?,
    settings: BusinessSettings?,
    unreadNotifications: Int = 0,
    sessionExpiresAtMillis: Long = 0L,
    googleEmail: String? = null,
    isOnline: Boolean,
    isSubmitting: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onSaveSettings: (String, String, String, Boolean, Boolean) -> Unit,
    onChangeOwnPassword: (String, String, String, () -> Unit) -> Unit = { _, _, _, _ -> },
    onNavigateBookers: () -> Unit = {},
    onNavigateStaff: () -> Unit = {},
    onNavigateNotifications: () -> Unit = {},
    onNavigateActivityLogs: () -> Unit = {},
    onNavigateAllShopsPdf: () -> Unit = {},
    onExportJson: (Context) -> Unit,
    onOpenPlatformOwner: () -> Unit = {},
    onLogoutWorkspace: () -> Unit
) {
    val context = LocalContext.current
    var bizName by remember(business, settings) { mutableStateOf(settings?.businessName ?: business?.businessName ?: session.businessName) }
    var address by remember(settings) { mutableStateOf(settings?.businessAddress.orEmpty()) }
    var phone by remember(settings, business) { mutableStateOf(settings?.contactPhone ?: business?.ownerMobile.orEmpty()) }
    var allowOverRecovery by remember(settings, business) { mutableStateOf(settings?.allowOverRecovery ?: business?.allowOverRecovery ?: false) }
    var requireInvoice by remember(settings) { mutableStateOf(settings?.requireInvoiceNumber ?: false) }

    var showAdminProfileDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var expandBusinessProfile by remember { mutableStateOf(false) }
    var expandPdfSettings by remember { mutableStateOf(false) }

    val isSuperAdmin = session.role == AccountRole.SUPER_ADMIN

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Admin Settings",
                subtitle = "Workspace Code: ${session.businessCode}",
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

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Group 1: Account & Identity (1. Admin Profile, 2. Change Password)
                item {
                    SettingsSectionCard(title = "Account & Identity") {
                        SettingsActionRow(
                            icon = Icons.Default.Person,
                            title = "1. Admin Profile",
                            subtitle = "${session.fullName} (@${session.username}) • ${session.role.label}",
                            tag = "settings_admin_profile",
                            onClick = { showAdminProfileDialog = true }
                        )
                        HorizontalDivider(color = HisabBorder)
                        SettingsActionRow(
                            icon = Icons.Default.Lock,
                            title = "2. Change Password",
                            subtitle = "Update your login password with PBKDF2 hashing",
                            tag = "settings_change_password",
                            onClick = { showChangePasswordDialog = true }
                        )
                    }
                }

                // Group 2: Business & Team Controls (3. Business Profile, 4. Booker Management, 5. Staff Management, 6. Permission Management)
                item {
                    SettingsSectionCard(title = "Workspace & Team") {
                        SettingsActionRow(
                            icon = Icons.Default.Store,
                            title = "3. Business Profile",
                            subtitle = "$bizName • Currency: PKR • Asia/Karachi",
                            tag = "settings_business_profile",
                            onClick = { expandBusinessProfile = !expandBusinessProfile }
                        )

                        if (expandBusinessProfile) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = bizName,
                                    onValueChange = { bizName = it },
                                    enabled = isSuperAdmin,
                                    label = { Text("Business Name *") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = address,
                                    onValueChange = { address = it },
                                    enabled = isSuperAdmin,
                                    label = { Text("Business Address") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    enabled = isSuperAdmin,
                                    label = { Text("Contact Phone") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (isSuperAdmin) {
                                    Button(
                                        onClick = { onSaveSettings(bizName, address, phone, allowOverRecovery, requireInvoice) },
                                        enabled = !isSubmitting && bizName.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("save_settings_button")
                                    ) {
                                        Text("Save Business Profile")
                                    }
                                }
                            }
                        }

                        if (isSuperAdmin) {
                            HorizontalDivider(color = HisabBorder)
                            SettingsActionRow(
                                icon = Icons.Default.Badge,
                                title = "4. Booker Management",
                                subtitle = "Add Bookers, assign shops, reset Booker passwords",
                                tag = "settings_booker_management",
                                onClick = onNavigateBookers
                            )
                            HorizontalDivider(color = HisabBorder)
                            SettingsActionRow(
                                icon = Icons.Default.Group,
                                title = "5. Staff Management",
                                subtitle = "Create Staff users, activate/suspend, reset Staff passwords",
                                tag = "settings_staff_management",
                                onClick = onNavigateStaff
                            )
                            HorizontalDivider(color = HisabBorder)
                            SettingsActionRow(
                                icon = Icons.Default.ManageAccounts,
                                title = "6. Permission Management",
                                subtitle = "Configure 14 granular role-based Staff permissions",
                                tag = "settings_permission_management",
                                onClick = onNavigateStaff
                            )
                        }
                    }
                }

                // Group 3: Alerts, PDF, Logs & Backup (7. Notifications, 8. PDF Settings, 9. Activity Logs, 10. Data Export)
                item {
                    SettingsSectionCard(title = "Operations, PDF & Audit") {
                        SettingsActionRow(
                            icon = Icons.Default.Notifications,
                            title = "7. Notifications",
                            subtitle = if (unreadNotifications > 0) "$unreadNotifications unread alerts & security updates" else "View credit, recovery & password alerts",
                            tag = "settings_notifications",
                            trailingBadge = if (unreadNotifications > 0) "$unreadNotifications" else null,
                            onClick = onNavigateNotifications
                        )
                        HorizontalDivider(color = HisabBorder)
                        SettingsActionRow(
                            icon = Icons.Default.PictureAsPdf,
                            title = "8. PDF Settings",
                            subtitle = "Credit/recovery rules, invoice checks & statement generator",
                            tag = "settings_pdf_settings",
                            onClick = { expandPdfSettings = !expandPdfSettings }
                        )

                        if (expandPdfSettings) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Allow Over-Recovery", style = MaterialTheme.typography.titleSmall)
                                        Text("Permit recovery higher than shop remaining balance.", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                                    }
                                    Switch(
                                        checked = allowOverRecovery,
                                        onCheckedChange = { if (isSuperAdmin) allowOverRecovery = it },
                                        colors = SwitchDefaults.colors(checkedTrackColor = HisabNavy)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Require Invoice Number", style = MaterialTheme.typography.titleSmall)
                                        Text("Require bill/invoice number on every credit entry & PDF.", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                                    }
                                    Switch(
                                        checked = requireInvoice,
                                        onCheckedChange = { if (isSuperAdmin) requireInvoice = it },
                                        colors = SwitchDefaults.colors(checkedTrackColor = HisabNavy)
                                    )
                                }
                                if (isSuperAdmin) {
                                    Button(
                                        onClick = { onSaveSettings(bizName, address, phone, allowOverRecovery, requireInvoice) },
                                        enabled = !isSubmitting && bizName.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(46.dp)
                                    ) {
                                        Text("Save PDF & Ledger Rules")
                                    }
                                }
                                OutlinedButton(
                                    onClick = onNavigateAllShopsPdf,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(46.dp)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open Reports & PDF Statements")
                                }
                            }
                        }

                        if (isSuperAdmin) {
                            HorizontalDivider(color = HisabBorder)
                            SettingsActionRow(
                                icon = Icons.Default.History,
                                title = "9. Activity Logs",
                                subtitle = "Immutable audit trail of logins, credits, recoveries & resets",
                                tag = "settings_activity_logs",
                                onClick = onNavigateActivityLogs
                            )
                            HorizontalDivider(color = HisabBorder)
                            SettingsActionRow(
                                icon = Icons.Default.Download,
                                title = "10. Data Export",
                                subtitle = "Export full workspace JSON backup (Shops, Bookers, Ledger)",
                                tag = "export_json_button",
                                onClick = { onExportJson(context) }
                            )
                        }
                    }
                }

                // Group 4: Security & Logout (11. Account Security, 12. Logout)
                item {
                    SettingsSectionCard(title = "Security & Session") {
                        SettingsActionRow(
                            icon = Icons.Default.Security,
                            title = "11. Account Security",
                            subtitle = "PBKDF2 hashing, rate limiting & persistent login session",
                            tag = "settings_account_security",
                            onClick = { showSecurityDialog = true }
                        )
                        HorizontalDivider(color = HisabBorder)
                        SettingsActionRow(
                            icon = Icons.Default.AdminPanelSettings,
                            title = "12. Platform Owner Control Panel",
                            subtitle = "Switch to Master Platform Owner governance portal",
                            tag = "settings_platform_owner_panel",
                            onClick = onOpenPlatformOwner
                        )
                        HorizontalDivider(color = HisabBorder)
                        SettingsActionRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            title = "13. Logout",
                            subtitle = "End active workspace session securely",
                            tag = "settings_logout_button",
                            isDestructive = true,
                            onClick = onLogoutWorkspace
                        )
                    }
                }
            }
        }
    }

    if (showAdminProfileDialog) {
        AlertDialog(
            onDismissRequest = { showAdminProfileDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = HisabNavy)
                    Spacer(Modifier.width(8.dp))
                    Text("Admin Profile", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsKeyValueRow("Full Name", session.fullName)
                    SettingsKeyValueRow("Username", "@${session.username}")
                    SettingsKeyValueRow("Role", session.role.label)
                    SettingsKeyValueRow("Business", business?.businessName ?: session.businessName)
                    SettingsKeyValueRow("Workspace Code", session.businessCode)
                    SettingsKeyValueRow("Registered Mobile", business?.ownerMobile?.ifBlank { "N/A" } ?: "N/A")
                    SettingsKeyValueRow("Google Account", googleEmail ?: business?.ownerEmail?.ifBlank { "Linked" } ?: "Linked")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdminProfileDialog = false }) { Text("Close") }
            }
        )
    }

    if (showChangePasswordDialog) {
        ChangeOwnPasswordDialog(
            username = session.username,
            isSubmitting = isSubmitting,
            onDismiss = { showChangePasswordDialog = false },
            onSubmit = { curr, newPass, confirmPass ->
                onChangeOwnPassword(curr, newPass, confirmPass) {
                    showChangePasswordDialog = false
                }
            }
        )
    }

    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = HisabNavy)
                    Spacer(Modifier.width(8.dp))
                    Text("Account Security", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecurityStatusItem("Password Hashing", "PBKDF2WithHmacSHA256 (16-byte random salt, 10,000 iterations)")
                    SecurityStatusItem("Rate Limiting", "Active on login (5 attempts), password change (5), reset (4)")
                    SecurityStatusItem("Login Session", "Persistent session — remains signed in until you tap Logout")
                    SecurityStatusItem("Workspace Isolation", "Zero-trust Firestore Rules scoped to ${session.businessCode}")
                    SecurityStatusItem("Financial Ledger", "Append-only immutable credits & recoveries with reversal logs")
                }
            },
            confirmButton = {
                TextButton(onClick = { showSecurityDialog = false }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = HisabNavy,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = HisabWhite),
            border = BorderStroke(1.dp, HisabBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    trailingBadge: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = HisabWhite,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isDestructive) HisabErrorRed else HisabNavy,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDestructive) HisabErrorRed else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabSecondaryGray
                )
            }
            if (trailingBadge != null) {
                StatusPill(trailingBadge, HisabWarningBg, HisabWarningOrange)
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = HisabSecondaryGray
            )
        }
    }
}

@Composable
private fun SettingsKeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecurityStatusItem(title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = HisabSuccessGreen,
            modifier = Modifier.size(18.dp).padding(top = 2.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
        }
    }
}

