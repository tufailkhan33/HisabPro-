package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.ActiveWorkspaceSession
import com.example.domain.model.Booker
import com.example.domain.model.Business
import com.example.domain.model.CreditTransaction
import com.example.domain.model.RecoveryTransaction
import com.example.ui.components.ActionMessageBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabNavyTopBar
import com.example.ui.components.OfflineBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.HisabBlackText
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
import com.example.util.PakistanTime

private data class BookerHistoryItem(
    val id: String,
    val shopId: String,
    val shopName: String,
    val shopUid: String,
    val bookerName: String,
    val amountPaisa: Long,
    val txType: String,
    val date: String,
    val time: String,
    val createdBy: String,
    val sortMillis: Long,
    val isRecovery: Boolean,
    val isReversal: Boolean
)

@Composable
fun BookerHistoryScreen(
    creditTransactions: List<CreditTransaction>,
    recoveryTransactions: List<RecoveryTransaction>,
    isOnline: Boolean,
    onShopClick: (String) -> Unit
) {
    var filterTab by remember { mutableIntStateOf(0) } // 0=All, 1=Credits, 2=Recoveries
    var query by remember { mutableStateOf("") }

    val itemsList = remember(creditTransactions, recoveryTransactions, filterTab, query) {
        val credits = if (filterTab == 2) emptyList() else creditTransactions.map { tx ->
            BookerHistoryItem(
                id = "cr_${tx.id}",
                shopId = tx.shopId,
                shopName = tx.shopName,
                shopUid = tx.shopUid,
                bookerName = tx.bookerName,
                amountPaisa = tx.amountPaisa,
                txType = if (tx.transactionKind == "CREDIT_REVERSAL") "Credit Reversal" else "Credit Added",
                date = tx.txDateFormatted,
                time = tx.txTimeFormatted,
                createdBy = tx.createdByName,
                sortMillis = tx.txDateMillis,
                isRecovery = false,
                isReversal = tx.transactionKind == "CREDIT_REVERSAL"
            )
        }
        val recs = if (filterTab == 1) emptyList() else recoveryTransactions.map { tx ->
            BookerHistoryItem(
                id = "rc_${tx.id}",
                shopId = tx.shopId,
                shopName = tx.shopName,
                shopUid = tx.shopUid,
                bookerName = tx.bookerName,
                amountPaisa = tx.amountPaisa,
                txType = if (tx.transactionKind == "RECOVERY_REVERSAL") "Recovery Reversal" else "Recovery (${tx.paymentMethod})",
                date = tx.txDateFormatted,
                time = tx.txTimeFormatted,
                createdBy = tx.createdByName,
                sortMillis = tx.txDateMillis,
                isRecovery = true,
                isReversal = tx.transactionKind == "RECOVERY_REVERSAL"
            )
        }
        val merged = (credits + recs).sortedByDescending { it.sortMillis }
        val q = query.trim().lowercase()
        if (q.isEmpty()) merged else merged.filter {
            it.shopName.lowercase().contains(q) ||
                it.shopUid.lowercase().contains(q) ||
                it.txType.lowercase().contains(q) ||
                it.createdBy.lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Assigned History",
                subtitle = "Read-Only Credit & Recovery Feed"
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

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search by shop name, UID, or method...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("booker_history_search")
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Credits", "Recoveries").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = filterTab == idx,
                            onClick = { filterTab = idx },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HisabNavy,
                                selectedLabelColor = HisabWhite
                            )
                        )
                    }
                }
            }

            if (itemsList.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        title = "No History Entries",
                        message = "Credit and recovery transactions for your assigned shops will sync here in real time."
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(itemsList, key = { it.id }) { item ->
                        TransactionHistoryCard(
                            shopName = item.shopName,
                            shopUid = item.shopUid,
                            bookerName = item.bookerName,
                            amountPaisa = item.amountPaisa,
                            txType = item.txType,
                            date = item.date,
                            time = item.time,
                            createdBy = item.createdBy,
                            isRecovery = item.isRecovery,
                            isReversal = item.isReversal,
                            onClick = { onShopClick(item.shopId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookerProfileScreen(
    session: ActiveWorkspaceSession,
    business: Business?,
    booker: Booker?,
    assignedShopsCount: Int,
    unreadNotifications: Int,
    sessionExpiresAtMillis: Long,
    isOnline: Boolean,
    isSubmitting: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onChangeOwnPassword: (String, String, String, () -> Unit) -> Unit,
    onLogoutWorkspace: () -> Unit
) {
    var showChangePassword by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Booker Profile",
                subtitle = "${session.businessName} • ${session.businessCode}"
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(HisabNavy),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = session.fullName.trim().firstOrNull()?.uppercase() ?: "B",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = HisabWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = booker?.fullName ?: session.fullName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = HisabBlackText
                                    )
                                    Text(
                                        text = "@${session.username}  •  ${booker?.bookerCode ?: "Booker"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HisabSecondaryGray
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    StatusPill("READ-ONLY ACCESS", HisabWarningBg, HisabWarningOrange)
                                }
                            }

                            HorizontalDivider(color = HisabBorder)

                            ProfileDetailLine("Business Workspace", "${business?.businessName ?: session.businessName} (${session.businessCode})")
                            ProfileDetailLine("Mobile Number", booker?.mobileNumber?.ifBlank { "N/A" } ?: "N/A")
                            ProfileDetailLine("Address", booker?.address?.ifBlank { "Pakistan" } ?: "Pakistan")
                            ProfileDetailLine("Assigned Shops", "$assignedShopsCount Active Shops")
                            ProfileDetailLine("Account Status", booker?.status ?: "ACTIVE")
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = HisabNavy)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Security & Actions",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HisabNavy,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Session: Active until manual logout",
                                style = MaterialTheme.typography.bodySmall,
                                color = HisabSecondaryGray
                            )
                            Text(
                                text = "For edits to shops, credits, or recoveries, please contact your Business Super Admin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HisabSecondaryGray
                            )

                            OutlinedButton(
                                onClick = onNavigateNotifications,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("booker_notifications_button")
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (unreadNotifications > 0) "Notifications ($unreadNotifications unread)" else "Notifications"
                                )
                            }

                            Button(
                                onClick = { showChangePassword = true },
                                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("booker_change_password_button")
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Change Own Password")
                            }

                            OutlinedButton(
                                onClick = onLogoutWorkspace,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, HisabErrorRed),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("booker_logout_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = HisabErrorRed)
                                Spacer(Modifier.width(8.dp))
                                Text("Logout", color = HisabErrorRed, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChangePassword) {
        ChangeOwnPasswordDialog(
            username = session.username,
            isSubmitting = isSubmitting,
            onDismiss = { showChangePassword = false },
            onSubmit = { currentPass, newPass, confirmPass ->
                onChangeOwnPassword(currentPass, newPass, confirmPass) {
                    showChangePassword = false
                }
            }
        )
    }
}

@Composable
private fun ProfileDetailLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = HisabBlackText)
    }
}

@Composable
fun ChangeOwnPasswordDialog(
    username: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (currentPass: String, newPass: String, confirmPass: String) -> Unit
) {
    var currentPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var showCurrent by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = HisabNavy)
                Spacer(Modifier.width(8.dp))
                Text("Change Password", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Update login password for @$username",
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabSecondaryGray
                )
                OutlinedTextField(
                    value = currentPass,
                    onValueChange = { currentPass = it },
                    label = { Text("Current Password *") },
                    singleLine = true,
                    visualTransformation = if (showCurrent) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showCurrent = !showCurrent }) {
                            Icon(
                                if (showCurrent) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pass_current")
                )
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("New Password (min 6 chars) *") },
                    singleLine = true,
                    visualTransformation = if (showNew) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showNew = !showNew }) {
                            Icon(
                                if (showNew) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pass_new")
                )
                OutlinedTextField(
                    value = confirmPass,
                    onValueChange = { confirmPass = it },
                    label = { Text("Confirm New Password *") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pass_confirm")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(currentPass, newPass, confirmPass) },
                enabled = !isSubmitting && currentPass.isNotBlank() && newPass.length >= 6 && confirmPass == newPass,
                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                modifier = Modifier.testTag("change_pass_submit")
            ) {
                Text(if (isSubmitting) "Updating..." else "Update Password")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
