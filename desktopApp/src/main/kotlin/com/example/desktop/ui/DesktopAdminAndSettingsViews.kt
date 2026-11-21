package com.example.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.desktop.local.DesktopCryptoHelper
import com.example.desktop.state.DesktopAppState
import com.example.desktop.state.DesktopUiState
import com.example.shared.model.*
import com.example.shared.util.SharedCurrencyFormatter
import com.example.shared.util.SharedPakistanTime

@Composable
fun DesktopStaffAndPermissionsView(state: DesktopUiState, controller: DesktopAppState) {
    var showCreate by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<BusinessUser?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Staff & Granular Permissions", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
                Text("Manage Authorized Staff accounts and 14 real-time access toggles synced across Android & Windows.", fontSize = 13.sp, color = DesktopColors.SecondaryGray)
            }
            if (state.activeSession?.role == AccountRole.SUPER_ADMIN) {
                Button(onClick = { showCreate = true }, colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Navy)) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Create Staff")
                }
            }
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DesktopCard(modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(0.dp)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.staffUsers, key = { it.id }) { u ->
                        val active = selectedUser?.id == u.id
                        Row(
                            modifier = Modifier.fillMaxWidth().background(if (active) DesktopColors.NavyContainer else Color.White).clickable { selectedUser = u }.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(u.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("@${u.username} • ${u.userCode} • ${u.role}", fontSize = 12.sp, color = DesktopColors.SecondaryGray)
                            }
                            Text(u.status, fontWeight = FontWeight.SemiBold, color = if (u.status == "ACTIVE") DesktopColors.SuccessGreen else DesktopColors.ErrorRed)
                        }
                        HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.5f))
                    }
                }
            }

            DesktopCard(modifier = Modifier.weight(1.3f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                val target = selectedUser ?: state.staffUsers.firstOrNull { it.role == AccountRole.STAFF.key }
                if (target == null) {
                    Text("Select a Staff user to inspect or update permissions.", color = DesktopColors.SecondaryGray)
                } else {
                    val perm = state.allPermissions.firstOrNull { it.userId == target.id } ?: StaffPermission(id = target.id, businessId = target.businessId, userId = target.id)
                    val isEditable = state.activeSession?.role == AccountRole.SUPER_ADMIN && target.role != AccountRole.SUPER_ADMIN.key
                    Text("Permissions for ${target.fullName}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DesktopColors.Navy)
                    Spacer(Modifier.height(10.dp))
                    PermRow("View Dashboard", perm.viewDashboard, isEditable) { controller.updatePermission(perm.copy(viewDashboard = it)) }
                    PermRow("View Shops", perm.viewShops, isEditable) { controller.updatePermission(perm.copy(viewShops = it)) }
                    PermRow("Add Shops", perm.addShops, isEditable) { controller.updatePermission(perm.copy(addShops = it)) }
                    PermRow("Edit Shops", perm.editShops, isEditable) { controller.updatePermission(perm.copy(editShops = it)) }
                    PermRow("Add Credit", perm.addCredit, isEditable) { controller.updatePermission(perm.copy(addCredit = it)) }
                    PermRow("Edit / Reverse Credit", perm.editCredit, isEditable) { controller.updatePermission(perm.copy(editCredit = it)) }
                    PermRow("Record Recovery (PKT Today Only)", perm.recordRecovery, isEditable) { controller.updatePermission(perm.copy(recordRecovery = it)) }
                    PermRow("View Recovery History", perm.viewRecoveryHistory, isEditable) { controller.updatePermission(perm.copy(viewRecoveryHistory = it)) }
                    PermRow("View Bookers", perm.viewBookers, isEditable) { controller.updatePermission(perm.copy(viewBookers = it)) }
                    PermRow("Manage Bookers", perm.manageBookers, isEditable) { controller.updatePermission(perm.copy(manageBookers = it)) }
                    PermRow("View Reports", perm.viewReports, isEditable) { controller.updatePermission(perm.copy(viewReports = it)) }
                    PermRow("Generate PDFs", perm.generatePdfs, isEditable) { controller.updatePermission(perm.copy(generatePdfs = it)) }
                    PermRow("Search Shops", perm.searchShops, isEditable) { controller.updatePermission(perm.copy(searchShops = it)) }
                    PermRow("View Activity Logs", perm.viewActivityLogs, isEditable) { controller.updatePermission(perm.copy(viewActivityLogs = it)) }
                }
            }
        }
    }

    if (showCreate) {
        var fullName by remember { mutableStateOf("") }
        var username by remember { mutableStateOf("") }
        var mobile by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create Authorized Staff Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, singleLine = true)
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobile") }, singleLine = true)
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email (Optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(enabled = fullName.isNotBlank() && username.isNotBlank(), onClick = {
                    controller.addStaff(fullName, username, mobile, email, StaffPermission(recordRecovery = true, addCredit = true))
                    showCreate = false
                }) { Text("Create Staff") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PermRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun DesktopChatView(state: DesktopUiState, controller: DesktopAppState) {
    val activeGroup = state.chatGroups.firstOrNull { it.id == state.selectedChatGroupId } ?: state.chatGroups.firstOrNull()
    var messageInput by remember { mutableStateOf("") }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        DesktopCard(modifier = Modifier.weight(0.9f).fillMaxHeight(), contentPadding = PaddingValues(0.dp)) {
            Text("Team Chat Groups", modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.chatGroups, key = { it.id }) { grp ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(if (grp.id == activeGroup?.id) DesktopColors.NavyContainer else Color.White).clickable { controller.openChatGroup(grp) }.padding(14.dp)
                    ) {
                        Column {
                            Text(grp.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(grp.lastMessagePreview.ifBlank { grp.description }, fontSize = 12.sp, color = DesktopColors.SecondaryGray, maxLines = 1)
                        }
                    }
                    HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.5f))
                }
            }
        }

        DesktopCard(modifier = Modifier.weight(2f).fillMaxHeight()) {
            if (activeGroup == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active chat groups found.", color = DesktopColors.SecondaryGray)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(activeGroup.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DesktopColors.Navy)
                    OutlinedButton(onClick = { controller.openChatGroup(activeGroup) }) { Text("Refresh") }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(state.chatMessages, key = { it.id }) { msg ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text("${msg.senderName} (${msg.senderRole}) • ${msg.timeFormatted}", fontSize = 11.sp, color = DesktopColors.Navy, fontWeight = FontWeight.SemiBold)
                            Text(msg.text.ifBlank { "[${msg.messageType}: ${msg.fileName}]" }, fontSize = 13.sp)
                        }
                        HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.35f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = messageInput, onValueChange = { messageInput = it }, placeholder = { Text("Type message to team...") }, modifier = Modifier.weight(1f), singleLine = true)
                    Button(
                        enabled = messageInput.isNotBlank() && state.isOnline,
                        onClick = {
                            controller.sendChatMessage(activeGroup, messageInput)
                            messageInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Navy)
                    ) { Text("Send") }
                }
            }
        }
    }
}

@Composable
fun DesktopOfflineSyncView(state: DesktopUiState, controller: DesktopAppState) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Offline SQLite Queue & Cloud Synchronization", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
                Text("Unique idempotency tokens prevent duplicate records across retries and multi-device conflicts.", fontSize = 13.sp, color = DesktopColors.SecondaryGray)
            }
            Button(
                enabled = !state.isSyncing,
                onClick = { controller.runSyncNow(quiet = false) },
                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Navy)
            ) {
                Icon(Icons.Default.Sync, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.isSyncing) "Synchronizing..." else "Sync Pending Queue Now")
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val pendingCount = state.pendingSync.count { it.syncStatus == SyncStatus.PENDING_SYNC.key }
            val failedCount = state.pendingSync.count { it.syncStatus == SyncStatus.SYNC_FAILED.key }
            val conflictCount = state.pendingSync.count { it.syncStatus == SyncStatus.CONFLICT_REQUIRES_REVIEW.key }
            DesktopKpiCard("PENDING QUEUE", "$pendingCount", "Waiting to upload", DesktopColors.WarningBg, DesktopColors.WarningOrange, Modifier.weight(1f))
            DesktopKpiCard("SYNC FAILED", "$failedCount", "Will retry on connection", DesktopColors.ErrorBg, DesktopColors.ErrorRed, Modifier.weight(1f))
            DesktopKpiCard("CONFLICT REVIEW", "$conflictCount", "Protected from overwrite", DesktopColors.ErrorBg, DesktopColors.ErrorRed, Modifier.weight(1f))
            DesktopKpiCard("SYNC HISTORY", "${state.syncHistory.size}", "Last Sync: ${if (state.lastSyncedAtMillis > 0) SharedPakistanTime.formatTime(state.lastSyncedAtMillis) else "Pending"}", DesktopColors.SuccessBg, DesktopColors.SuccessGreen, Modifier.weight(1f))
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DesktopCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text("Pending / Conflict Transactions (${state.pendingSync.size})", fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.pendingSync, key = { it.localId }) { p ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${p.txType} • ${p.shopName} (${SharedCurrencyFormatter.formatPkr(p.amountPaisa)})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("ID: ${p.localId} • Retries: ${p.retryCount}${if (p.errorMessage.isNotBlank()) " • ${p.errorMessage}" else ""}", fontSize = 11.sp, color = DesktopColors.SecondaryGray)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                SyncStatusBadge(p.syncStatus)
                                if (p.syncStatus == SyncStatus.CONFLICT_REQUIRES_REVIEW.key || p.syncStatus == SyncStatus.SYNC_FAILED.key) {
                                    TextButton(onClick = { controller.retryPendingItem(p.localId) }) { Text("Retry", fontSize = 11.sp) }
                                    TextButton(onClick = { controller.resolveConflictKeepServer(p) }) {
                                        Text("Keep Server", fontSize = 11.sp, color = DesktopColors.ErrorRed)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.5f))
                    }
                }
            }

            DesktopCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text("Recent Sync Activity Log", fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.syncHistory, key = { it.id }) { h ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${h.txType} • ${h.shopUid} • ${SharedCurrencyFormatter.formatPkr(h.amountPaisa)}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text("${SharedPakistanTime.formatShortDateTime(h.syncedAtMillis)} — ${h.message}", fontSize = 11.sp, color = DesktopColors.SecondaryGray)
                            }
                            SyncStatusBadge(h.syncStatus)
                        }
                        HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopAuditAndNotificationsView(state: DesktopUiState, controller: DesktopAppState) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        DesktopCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text("Business Notifications (${state.notifications.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DesktopColors.Navy)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.notifications, key = { it.id }) { n ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(n.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(n.message, fontSize = 12.sp, color = DesktopColors.SecondaryGray)
                        }
                        if (!n.isRead && state.isOnline) {
                            TextButton(onClick = { controller.markNotificationRead(n.id) }) { Text("Mark Read", fontSize = 11.sp) }
                        }
                    }
                    HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.5f))
                }
            }
        }
        DesktopCard(modifier = Modifier.weight(1.2f).fillMaxHeight()) {
            Text("Immutable Audit Trail (${state.auditLogs.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DesktopColors.Navy)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.auditLogs, key = { it.id }) { log ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text("${log.action} • ${log.username} (${log.role})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DesktopColors.Navy)
                        Text(log.description, fontSize = 12.sp)
                        Text("${log.dateFormatted} ${log.timeFormatted} PKT", fontSize = 11.sp, color = DesktopColors.SecondaryGray)
                    }
                    HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun DesktopSettingsView(state: DesktopUiState, controller: DesktopAppState) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Desktop Settings, Diagnostics & Vault", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DesktopColors.Navy)

        DesktopCard {
            Text("Application & Database Diagnostics", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DesktopColors.Navy)
            Spacer(Modifier.height(10.dp))
            Text("Application Version: HisabPro Windows Desktop v1.3.0 (Unified with Android v1.3)", fontSize = 13.sp)
            Text("Local Data Folder: ${DesktopCryptoHelper.resolveAppDataDir().absolutePath}", fontSize = 13.sp)
            Text("SQLite Integrity Check: ${state.sqliteIntegrityStatus.uppercase()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DesktopColors.SuccessGreen)
            Text("Cloud Connectivity: ${if (state.isOnline) "Connected to Shared Firebase Project" else "Offline (Working from Local SQLite)"}", fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Automatic Background Cloud Sync", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Automatically uploads queued offline SQLite transactions and pulls live updates every 12 seconds.", fontSize = 12.sp, color = DesktopColors.SecondaryGray)
                }
                Switch(checked = state.autoSyncEnabled, onCheckedChange = { controller.toggleAutoSync(it) })
            }
        }

        DesktopCard {
            Text("Backup, Restore & Cache Safety", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DesktopColors.Navy)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { controller.backupSqliteDatabase() }, colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Navy)) {
                    Text("Backup SQLite (.db)")
                }
                OutlinedButton(onClick = { controller.restoreSqliteDatabase() }) {
                    Text("Restore SQLite (.db)")
                }
                OutlinedButton(onClick = { controller.exportJsonBackup() }) {
                    Text("Export JSON Snapshot")
                }
                OutlinedButton(onClick = { controller.clearLocalCacheSafely() }) {
                    Text("Clear Local Cache (If Synced)", color = DesktopColors.ErrorRed)
                }
            }
        }

        if (state.activeSession?.role == AccountRole.SUPER_ADMIN) {
            var bizNameInput by remember(state.settings, state.business) { mutableStateOf(state.settings?.businessName ?: state.business?.businessName.orEmpty()) }
            var addressInput by remember(state.settings) { mutableStateOf(state.settings?.businessAddress.orEmpty()) }
            var phoneInput by remember(state.settings) { mutableStateOf(state.settings?.contactPhone.orEmpty()) }
            var allowOver by remember(state.settings, state.business) { mutableStateOf(state.settings?.allowOverRecovery ?: state.business?.allowOverRecovery ?: false) }
            var reqInvoice by remember(state.settings) { mutableStateOf(state.settings?.requireInvoiceNumber ?: false) }
            DesktopCard {
                Text("Company Rules & Ledger Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DesktopColors.Navy)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = bizNameInput, onValueChange = { bizNameInput = it }, label = { Text("Business Name") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = phoneInput, onValueChange = { phoneInput = it }, label = { Text("Contact Phone") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = addressInput, onValueChange = { addressInput = it }, label = { Text("Business Address") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow Over-Recovery Beyond Outstanding Balance", fontSize = 13.sp)
                    Switch(checked = allowOver, onCheckedChange = { allowOver = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Require Invoice Number on Credit Entries", fontSize = 13.sp)
                    Switch(checked = reqInvoice, onCheckedChange = { reqInvoice = it })
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = state.isOnline && bizNameInput.isNotBlank(),
                    onClick = { controller.updateBusinessSettings(bizNameInput, addressInput, phoneInput, allowOver, reqInvoice) },
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Navy)
                ) { Text("Save Company Settings") }
            }
        }

        if (state.activeSession != null) {
            var curPass by remember { mutableStateOf("") }
            var newPass by remember { mutableStateOf("") }
            DesktopCard {
                Text(
                    if (state.activeSession.mustChangePassword) "Security Action Required — Change Temporary Password" else "Change Account Password",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (state.activeSession.mustChangePassword) DesktopColors.ErrorRed else DesktopColors.Navy
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = curPass, onValueChange = { curPass = it }, label = { Text("Current Password") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = newPass, onValueChange = { newPass = it }, label = { Text("New Strong Password") }, modifier = Modifier.weight(1f), singleLine = true)
                    Button(
                        enabled = state.isOnline && curPass.isNotBlank() && newPass.isNotBlank(),
                        onClick = {
                            controller.changePassword(curPass, newPass)
                            curPass = ""; newPass = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Navy)
                    ) { Text("Update Password") }
                }
            }
        }

        DesktopCard {
            Text("Session & Account Controls", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DesktopColors.Navy)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { controller.logoutWorkspace() }) { Text("Switch Workspace / User") }
                Button(onClick = { controller.logoutAll() }, colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.ErrorRed)) {
                    Text("Sign Out Google & Clear Session")
                }
            }
        }
    }
}

@Composable
fun DesktopPlatformOwnerView(state: DesktopUiState, controller: DesktopAppState) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Platform Super Owner Console (${state.ownerSession?.username ?: "MrTufailadmin"})", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
        Text("Verified Owner Identity: ${state.ownerSession?.email.orEmpty()} | Registered Companies: ${state.ownerBusinesses.size}", fontSize = 13.sp, color = DesktopColors.SecondaryGray)

        DesktopCard(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.ownerBusinesses, key = { it.id }) { biz ->
                    val suspended = biz.status == "SUSPENDED"
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${biz.businessName} (${biz.businessCode})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Owner: ${biz.ownerName} • Mob: ${biz.ownerMobile} • Status: ${biz.status}", fontSize = 12.sp, color = if (suspended) DesktopColors.ErrorRed else DesktopColors.SuccessGreen)
                        }
                        Button(
                            onClick = { controller.toggleBusinessStatusAsOwner(biz, !suspended, if (!suspended) "Suspended by Platform Owner" else "") },
                            colors = ButtonDefaults.buttonColors(containerColor = if (suspended) DesktopColors.SuccessGreen else DesktopColors.ErrorRed)
                        ) {
                            Text(if (suspended) "Reactivate" else "Suspend")
                        }
                    }
                    HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.5f))
                }
            }
        }
    }
}
