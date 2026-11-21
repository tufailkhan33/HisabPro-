package com.example.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.desktop.cloud.DesktopFirebaseAuthClient
import com.example.desktop.cloud.DesktopFirestoreClient
import com.example.desktop.cloud.FirebaseDesktopConfig
import com.example.desktop.local.DesktopOfflineSyncManager
import com.example.desktop.local.DesktopSqliteDatabase
import com.example.desktop.state.DesktopAppState
import com.example.desktop.state.DesktopNavSection
import com.example.desktop.state.DesktopWorkspaceRepository
import com.example.desktop.ui.*
import com.example.shared.model.AccountRole
import com.example.shared.util.SharedPakistanTime
import kotlinx.coroutines.delay
import java.awt.Dimension

fun main() = application {
    val scope = rememberCoroutineScope()
    val controller = remember {
        val config = FirebaseDesktopConfig.load()
        val sqlite = DesktopSqliteDatabase()
        val authClient = DesktopFirebaseAuthClient(config, sqlite)
        val firestore = DesktopFirestoreClient(config, authClient)
        val syncMgr = DesktopOfflineSyncManager(sqlite, firestore)
        val repo = DesktopWorkspaceRepository(sqlite, authClient, firestore, syncMgr)
        DesktopAppState(repo, scope)
    }

    val state by controller.ui.collectAsState()
    val windowState = rememberWindowState(size = DpSize(1360.dp, 860.dp))

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "HisabPro — Business Credit, Recovery & Supplier Ledger",
        onPreviewKeyEvent = { ev ->
            if (ev.type == KeyEventType.KeyDown && ev.isCtrlPressed && ev.key == Key.S) {
                controller.runSyncNow(quiet = false)
                true
            } else if (ev.type == KeyEventType.KeyDown && ev.key == Key.Escape) {
                controller.clearBanners()
                true
            } else false
        }
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(1080, 700)
            loadBundledLogoBitmap()?.let { img ->
                window.iconImage = img.toAwtImage()
            }
        }

        HisabProDesktopTheme {
            when {
                state.isInitializing -> DesktopSplashScreen()
                state.activeSession == null && state.ownerSession == null -> DesktopAuthGateway(state, controller)
                else -> DesktopMainShell(state = state, controller = controller)
            }
        }
    }
}

@Composable
private fun DesktopMainShell(
    state: com.example.desktop.state.DesktopUiState,
    controller: DesktopAppState
) {
    var clockText by remember { mutableStateOf(SharedPakistanTime.formatHeaderClock()) }
    LaunchedEffect(Unit) {
        while (true) {
            clockText = SharedPakistanTime.formatHeaderClock()
            delay(1000L)
        }
    }

    val navItems = remember(state.activeSession, state.ownerSession) {
        buildList {
            if (state.activeSession != null) {
                add(DesktopNavSection.DASHBOARD to Icons.Default.Dashboard)
                add(DesktopNavSection.SHOPS to Icons.Default.Store)
                add(DesktopNavSection.CREDIT_RECOVERY to Icons.Default.Payments)
                add(DesktopNavSection.BOOKERS to Icons.Default.People)
                add(DesktopNavSection.PRODUCTS_BOOKINGS to Icons.Default.Inventory2)
                add(DesktopNavSection.REPORTS to Icons.Default.Assessment)
                if (state.activeSession.role != AccountRole.BOOKER) {
                    add(DesktopNavSection.STAFF to Icons.Default.AdminPanelSettings)
                }
                add(DesktopNavSection.CHAT to Icons.Default.Forum)
                add(DesktopNavSection.OFFLINE_SYNC to Icons.Default.Sync)
                add(DesktopNavSection.AUDIT_NOTIFICATIONS to Icons.Default.Notifications)
                add(DesktopNavSection.SETTINGS to Icons.Default.Settings)
            }
            if (state.ownerSession != null) {
                add(DesktopNavSection.PLATFORM_OWNER to Icons.Default.Security)
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(DesktopColors.LightGray)) {
        // Left Fixed Sidebar
        Column(
            modifier = Modifier.width(248.dp).fillMaxHeight().background(DesktopColors.NavyDark).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HisabProLogo(sizeDp = 40)
                Column {
                    Text("HisabPro", color = DesktopColors.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        state.activeSession?.businessCode ?: "OWNER",
                        color = DesktopColors.GoldAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            HorizontalDivider(color = DesktopColors.NavyLight, modifier = Modifier.padding(vertical = 6.dp))

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                navItems.forEach { (section, icon) ->
                    val selected = state.selectedSection == section
                    SidebarNavButton(
                        label = section.label,
                        icon = icon,
                        selected = selected,
                        onClick = { controller.selectSection(section) }
                    )
                }
            }

            HorizontalDivider(color = DesktopColors.NavyLight)
            Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    state.activeSession?.fullName ?: state.ownerSession?.username.orEmpty(),
                    color = DesktopColors.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${state.activeSession?.role?.label ?: "Platform Owner"} • Windows",
                    color = Color(0xFFAEC0D6),
                    fontSize = 11.sp
                )
            }
        }

        // Right Main Area
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Top Utility Status Header
            Surface(color = DesktopColors.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(clockText, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = DesktopColors.SecondaryGray)
                        Surface(
                            color = if (state.isOnline) DesktopColors.SuccessBg else DesktopColors.WarningBg,
                            shape = RoundedCornerShape(99.dp)
                        ) {
                            Text(
                                text = if (state.isOnline) "Cloud Online" else "Offline Mode",
                                color = if (state.isOnline) DesktopColors.SuccessGreen else DesktopColors.WarningOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.isBusy || state.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        OutlinedButton(onClick = { controller.runSyncNow(quiet = false) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                            Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sync (Ctrl+S)", fontSize = 12.sp)
                        }
                    }
                }
            }

            state.statusBanner?.let { banner ->
                Surface(color = DesktopColors.SuccessBg, border = BorderStroke(1.dp, DesktopColors.SuccessGreen.copy(alpha = 0.4f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(banner, color = DesktopColors.SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { controller.clearBanners() }) { Text("Dismiss") }
                    }
                }
            }
            state.errorBanner?.let { err ->
                Surface(color = DesktopColors.ErrorBg, border = BorderStroke(1.dp, DesktopColors.ErrorRed.copy(alpha = 0.4f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(err, color = DesktopColors.ErrorRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { controller.clearBanners() }) { Text("Dismiss") }
                    }
                }
            }
            if (state.activeSession?.mustChangePassword == true && state.selectedSection != DesktopNavSection.SETTINGS) {
                Surface(color = DesktopColors.WarningBg, border = BorderStroke(1.dp, DesktopColors.WarningOrange.copy(alpha = 0.5f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Security notice: You are using a temporary or reset password. Update it now.", color = DesktopColors.WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { controller.selectSection(DesktopNavSection.SETTINGS) }) { Text("Change Password") }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp)) {
                when (state.selectedSection) {
                    DesktopNavSection.DASHBOARD -> DesktopDashboardView(state, controller)
                    DesktopNavSection.SHOPS -> DesktopShopsAndLedgerView(state, controller)
                    DesktopNavSection.CREDIT_RECOVERY -> DesktopCreditAndRecoveryView(state, controller)
                    DesktopNavSection.BOOKERS -> DesktopBookersView(state, controller)
                    DesktopNavSection.PRODUCTS_BOOKINGS -> DesktopProductsAndBookingsView(state, controller)
                    DesktopNavSection.REPORTS -> DesktopReportsView(state, controller)
                    DesktopNavSection.STAFF -> DesktopStaffAndPermissionsView(state, controller)
                    DesktopNavSection.CHAT -> DesktopChatView(state, controller)
                    DesktopNavSection.OFFLINE_SYNC -> DesktopOfflineSyncView(state, controller)
                    DesktopNavSection.AUDIT_NOTIFICATIONS -> DesktopAuditAndNotificationsView(state, controller)
                    DesktopNavSection.SETTINGS -> DesktopSettingsView(state, controller)
                    DesktopNavSection.PLATFORM_OWNER -> DesktopPlatformOwnerView(state, controller)
                }
            }
        }
    }

    state.lastGeneratedCredentials?.let { creds ->
        AlertDialog(
            onDismissRequest = { controller.clearBanners() },
            title = { Text("Account Credentials Generated") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${creds.roleLabel}: ${creds.fullName} (${creds.accountCode})", fontWeight = FontWeight.Bold)
                    Text("Business Code: ${creds.businessCode}")
                    Text("Username: ${creds.username}")
                    Text("Temporary Password: ${creds.temporaryPassword}", color = DesktopColors.Navy, fontWeight = FontWeight.Bold)
                    Text("Share securely. Account works immediately on both Android & Windows Desktop.", fontSize = 12.sp, color = DesktopColors.SecondaryGray)
                }
            },
            confirmButton = { Button(onClick = { controller.clearBanners() }) { Text("Done") } }
        )
    }
}

@Composable
private fun SidebarNavButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) DesktopColors.NavyLight else Color.Transparent,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = label, tint = if (selected) DesktopColors.GoldAccent else Color(0xFFB8C4D4), modifier = Modifier.size(18.dp))
            Text(label, color = if (selected) DesktopColors.White else Color(0xFFD7E0EC), fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}
