package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.AccountRole
import com.example.domain.model.ActiveWorkspaceSession
import com.example.domain.model.PendingSyncTransaction
import com.example.domain.model.SyncHistoryRecord
import com.example.domain.model.SyncStatus
import com.example.ui.components.ActionMessageBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabNavyTopBar
import com.example.ui.components.OfflineBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.HisabBorder
import com.example.ui.theme.HisabErrorBg
import com.example.ui.theme.HisabErrorRed
import com.example.ui.theme.HisabKpiBlueBg
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
fun OfflineSyncScreen(
    session: ActiveWorkspaceSession,
    deviceId: String,
    isOnline: Boolean,
    isSyncing: Boolean,
    isSubmitting: Boolean,
    pendingItems: List<PendingSyncTransaction>,
    syncHistory: List<SyncHistoryRecord>,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onManualSyncNow: () -> Unit,
    onApproveConflict: (PendingSyncTransaction) -> Unit,
    onDiscardItem: (PendingSyncTransaction) -> Unit
) {
    val isSuperAdmin = session.role == AccountRole.SUPER_ADMIN
    val pendingCount = pendingItems.count { it.syncStatus == SyncStatus.PENDING_SYNC.key }
    val syncingCount = pendingItems.count { it.syncStatus == SyncStatus.SYNCING.key }
    val failedCount = pendingItems.count { it.syncStatus == SyncStatus.SYNC_FAILED.key }
    val conflictCount = pendingItems.count { it.syncStatus == SyncStatus.CONFLICT_REQUIRES_REVIEW.key }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Offline Sync Center",
                subtitle = "Queue, Conflicts & Idempotent Cloud Sync",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = onManualSyncNow,
                        enabled = isOnline && !isSyncing,
                        modifier = Modifier.testTag("topbar_sync_now_button")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Now")
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
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = when {
                                            isSyncing -> Icons.Default.CloudSync
                                            isOnline -> Icons.Default.CloudDone
                                            else -> Icons.Default.CloudOff
                                        },
                                        contentDescription = null,
                                        tint = if (isOnline) HisabSuccessGreen else HisabWarningOrange,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = when {
                                                isSyncing -> "Synchronizing with Cloud..."
                                                isOnline -> "Online • Auto-Sync Active"
                                                else -> "Offline • Saving Encrypted Local Outbox"
                                            },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = HisabNavy,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Device ID: $deviceId • Workspace: ${session.businessCode}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HisabSecondaryGray
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SyncStatChip("Pending", pendingCount, HisabKpiBlueBg, HisabNavy)
                                SyncStatChip("Syncing", syncingCount, HisabNavyContainer, HisabNavy)
                                SyncStatChip("Failed", failedCount, HisabWarningBg, HisabWarningOrange)
                                SyncStatChip("Conflicts", conflictCount, HisabErrorBg, HisabErrorRed)
                            }

                            Button(
                                onClick = onManualSyncNow,
                                enabled = isOnline && !isSyncing,
                                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("manual_sync_button")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        color = HisabWhite,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Syncing...")
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Manual Sync Now")
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Outbox Queue (${pendingItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = HisabNavy,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (pendingItems.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.CloudDone,
                            title = "Outbox Empty",
                            message = "All locally recorded credit and recovery transactions have been confirmed with cloud."
                        )
                    }
                } else {
                    items(pendingItems, key = { it.localId }) { item ->
                        val statusEnum = SyncStatus.fromKey(item.syncStatus)
                        val (pillBg, pillFg) = when (statusEnum) {
                            SyncStatus.SYNCED -> HisabSuccessBg to HisabSuccessGreen
                            SyncStatus.SYNCING -> HisabNavyContainer to HisabNavy
                            SyncStatus.PENDING_SYNC -> HisabKpiBlueBg to HisabNavy
                            SyncStatus.SYNC_FAILED -> HisabWarningBg to HisabWarningOrange
                            SyncStatus.CONFLICT_REQUIRES_REVIEW -> HisabErrorBg to HisabErrorRed
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = HisabWhite),
                            border = BorderStroke(1.dp, HisabBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sync_outbox_item_${item.localId}")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${item.txType} • ${item.shopName} (${item.shopUid})",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = HisabNavy,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "By @${item.username} (${item.userRole}) • Device: ${item.deviceId.take(10)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HisabSecondaryGray
                                        )
                                    }
                                    StatusPill(statusEnum.label, pillBg, pillFg)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = CurrencyFormatter.formatPkr(item.amountPaisa),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (item.txType == "CREDIT") HisabErrorRed else HisabSuccessGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Tx Date: ${PakistanTime.formatDate(item.originalTxDateMillis)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HisabNavy
                                        )
                                        Text(
                                            text = "Device Time: ${PakistanTime.formatDateTime(item.localDeviceTimestamp)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HisabSecondaryGray
                                        )
                                    }
                                }

                                Text(
                                    text = "Idempotency Key: ${item.idempotencyToken}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HisabSecondaryGray
                                )

                                if (item.errorMessage.isNotBlank()) {
                                    Surface(
                                        color = if (statusEnum == SyncStatus.CONFLICT_REQUIRES_REVIEW) HisabErrorBg else HisabWarningBg,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = if (statusEnum == SyncStatus.CONFLICT_REQUIRES_REVIEW) HisabErrorRed else HisabWarningOrange,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = item.errorMessage,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (statusEnum == SyncStatus.CONFLICT_REQUIRES_REVIEW) HisabErrorRed else HisabWarningOrange
                                            )
                                        }
                                    }
                                }

                                if (statusEnum == SyncStatus.CONFLICT_REQUIRES_REVIEW || statusEnum == SyncStatus.SYNC_FAILED) {
                                    HorizontalDivider(color = HisabBorder)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (isSuperAdmin && statusEnum == SyncStatus.CONFLICT_REQUIRES_REVIEW) {
                                            Button(
                                                onClick = { onApproveConflict(item) },
                                                enabled = isOnline && !isSubmitting,
                                                colors = ButtonDefaults.buttonColors(containerColor = HisabSuccessGreen),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("approve_conflict_${item.localId}")
                                            ) {
                                                Text("Approve as Admin")
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { onDiscardItem(item) },
                                            enabled = !isSubmitting,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("discard_sync_${item.localId}")
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = HisabErrorRed)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Discard", color = HisabErrorRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Recent Sync History (${syncHistory.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = HisabNavy,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (syncHistory.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.History,
                            title = "No Sync History Yet",
                            message = "Completed synchronization events and conflict audit results will appear here."
                        )
                    }
                } else {
                    items(syncHistory, key = { it.id }) { h ->
                        val st = SyncStatus.fromKey(h.syncStatus)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = HisabWhite),
                            border = BorderStroke(1.dp, HisabBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${h.txType} • ${h.shopUid}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = HisabNavy,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = PakistanTime.formatDateTime(h.syncedAtMillis),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HisabSecondaryGray
                                    )
                                }
                                Text(h.message, style = MaterialTheme.typography.bodySmall)
                                StatusPill(
                                    text = st.label,
                                    containerColor = if (st == SyncStatus.SYNCED) HisabSuccessBg else HisabWarningBg,
                                    contentColor = if (st == SyncStatus.SYNCED) HisabSuccessGreen else HisabWarningOrange
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
private fun SyncStatChip(
    label: String,
    count: Int,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color
) {
    Surface(color = bg, shape = RoundedCornerShape(10.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$count", style = MaterialTheme.typography.titleMedium, color = fg, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}
