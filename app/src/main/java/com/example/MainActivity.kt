package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.activity.compose.BackHandler
import com.example.data.firebase.HisabProRepository
import com.example.data.firebase.PlatformOwnerRepository
import com.example.data.firebase.SessionPreferences
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.onGoogleSignInClicked
import com.example.data.firebase.signOutGoogle
import com.example.data.local.HisabLocalDatabase
import com.example.data.local.LocalCryptoHelper
import com.example.data.local.OfflineSyncManager
import com.example.domain.model.AccountRole
import com.example.ui.AppViewModel
import com.example.ui.PlatformOwnerViewModel
import com.example.ui.components.GeneratedCredentialsDialog
import com.example.ui.components.HisabProLogoBadge
import com.example.ui.screens.ActivityLogsScreen
import com.example.ui.screens.AddBookerScreen
import com.example.ui.screens.AddCreditScreen
import com.example.ui.screens.AllBookerCreditScreen
import com.example.ui.screens.BookerDetailScreen
import com.example.ui.screens.BookerHistoryScreen
import com.example.ui.screens.BookerProfileScreen
import com.example.ui.screens.BookersScreen
import com.example.ui.screens.BusinessSettingsScreen
import com.example.ui.screens.ChangeTemporaryPasswordScreen
import com.example.ui.screens.ChatGroupsListScreen
import com.example.ui.screens.CreditShopsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.GlobalSearchScreen
import com.example.ui.screens.GroupChatDetailScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OfflineSyncScreen
import com.example.ui.screens.PlatformOwnerRootScreen
import com.example.ui.screens.RecordRecoveryScreen
import com.example.ui.screens.ReportsAndPdfScreen
import com.example.ui.screens.ShopProfileScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StaffManagementScreen
import com.example.ui.screens.UnifiedAuthScreen
import com.example.ui.theme.HisabLightGray
import com.example.ui.theme.HisabNavy
import com.example.ui.theme.HisabNavyContainer
import com.example.ui.theme.HisabProTheme
import com.example.ui.theme.HisabSecondaryGray
import com.example.ui.theme.HisabWhite
import com.example.util.NetworkMonitor
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabNavyTopBar

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_NO_ACCESS = "no_access"
const val ROUTE_SHOPS = "shops"
const val ROUTE_SHOP_DETAIL = "shop/{shopId}"
const val ROUTE_ADD_CREDIT = "add_credit"
const val ROUTE_RECORD_RECOVERY = "record_recovery"
const val ROUTE_BOOKERS = "bookers"
const val ROUTE_ADD_BOOKER = "add_booker"
const val ROUTE_BOOKER_DETAIL = "booker/{bookerId}"
const val ROUTE_BOOKER_HISTORY = "booker_history"
const val ROUTE_BOOKER_PROFILE = "booker_profile"
const val ROUTE_STAFF = "staff"
const val ROUTE_REPORTS = "reports"
const val ROUTE_SEARCH = "search"
const val ROUTE_AUDIT_LOGS = "audit_logs"
const val ROUTE_NOTIFICATIONS = "notifications"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_ALL_BOOKER_CREDIT = "all_booker_credit"
const val ROUTE_CHAT_GROUPS = "chat_groups"
const val ROUTE_CHAT_DETAIL = "chat_group/{groupId}"
const val ROUTE_SYNC_CENTER = "sync_center"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HisabProTheme {
                HisabProRootApp()
            }
        }
    }
}

private data class TopNavDest(
    val route: String,
    val baseRoute: String,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@Composable
fun HisabProRootApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val networkMonitor = remember { NetworkMonitor(appContext) }
    val sessionPrefs = remember { SessionPreferences(appContext) }
    val localDb = remember { HisabLocalDatabase.getInstance(appContext) }
    val deviceId = remember { LocalCryptoHelper.getOrCreateDeviceId(appContext) }

    var splashDone by remember { mutableStateOf(false) }
    var showPlatformOwnerPortal by remember { mutableStateOf(false) }
    val firebaseUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    val rootIsOnline by remember { networkMonitor.observeIsOnline() }
        .collectAsStateWithLifecycle(initialValue = networkMonitor.isCurrentlyOnline())
    val lastGoogleIdentity by remember { sessionPrefs.observeLastGoogleIdentity() }
        .collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(firebaseUser?.uid, firebaseUser?.email) {
        val u = firebaseUser
        if (u != null) {
            sessionPrefs.rememberGoogleIdentity(u.uid, u.email)
        }
    }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
        delay(850L)
        splashDone = true
    }

    if (!splashDone) {
        SplashScreen()
        return
    }

    val currentUser = firebaseUser
    val effectiveGoogleUid = currentUser?.uid ?: lastGoogleIdentity?.first
    val effectiveGoogleEmail = currentUser?.email ?: lastGoogleIdentity?.second

    if (showPlatformOwnerPortal) {
        val ownerVm: PlatformOwnerViewModel = key(effectiveGoogleUid.orEmpty()) {
            viewModel(
                key = "owner_vm_${effectiveGoogleUid.orEmpty()}",
                factory = viewModelFactory {
                    initializer {
                        PlatformOwnerViewModel(
                            repository = PlatformOwnerRepository(appContext),
                            sessionPrefs = sessionPrefs,
                            googleUid = effectiveGoogleUid.orEmpty(),
                            googleEmail = effectiveGoogleEmail.orEmpty()
                        )
                    }
                }
            )
        }
        PlatformOwnerRootScreen(
            viewModel = ownerVm,
            isGoogleSignedIn = currentUser != null,
            googleEmail = currentUser?.email,
            isOnline = rootIsOnline,
            onBackToMainLogin = { showPlatformOwnerPortal = false },
            onTriggerGoogleSignIn = {
                onGoogleSignInClicked(
                    context = context,
                    credentialManager = credentialManager,
                    onAuthSuccess = { ownerVm.refreshAuthRequirement() },
                    onAuthError = {},
                    scope = scope,
                    onAuthCancelled = {}
                )
            },
            onSwitchGoogleAccount = {
                scope.launch {
                    sessionPrefs.clearPlatformOwnerSession()
                    sessionPrefs.clearAllOnGoogleSignOut()
                }
                signOutGoogle(credentialManager, scope)
            }
        )
        return
    }

    val viewModel: AppViewModel? = if (!effectiveGoogleUid.isNullOrBlank()) {
        key(effectiveGoogleUid) {
            val repo = remember(appContext) { HisabProRepository(appContext) }
            val syncMgr = remember(repo, localDb, deviceId) {
                OfflineSyncManager(
                    dao = localDb.hisabDao(),
                    repository = repo,
                    deviceId = deviceId
                )
            }
            viewModel(
                key = "hisabpro_vm_$effectiveGoogleUid",
                factory = viewModelFactory {
                    initializer {
                        AppViewModel(
                            repository = repo,
                            sessionPrefs = sessionPrefs,
                            networkMonitor = networkMonitor,
                            googleUid = effectiveGoogleUid,
                            offlineSyncManager = syncMgr,
                            deviceId = deviceId
                        )
                    }
                }
            )
        }
    } else {
        null
    }

    val activeSession = viewModel?.activeSession?.collectAsStateWithLifecycle()?.value
    val isOnline = viewModel?.isOnline?.collectAsStateWithLifecycle()?.value ?: rootIsOnline
    val isSubmitting = viewModel?.isSubmitting?.collectAsStateWithLifecycle()?.value ?: false
    val actionMessage = viewModel?.actionMessage?.collectAsStateWithLifecycle()?.value
    val savedWorkspaces = viewModel?.savedWorkspaces?.collectAsStateWithLifecycle()?.value ?: emptyList()

    when {
        viewModel != null && activeSession != null && activeSession.mustChangePassword -> {
            ChangeTemporaryPasswordScreen(
                fullName = activeSession.fullName,
                username = activeSession.username,
                roleLabel = activeSession.role.label,
                isSubmitting = isSubmitting,
                actionMessage = actionMessage,
                onDismissMessage = { viewModel.clearMessage() },
                onSubmitNewPassword = viewModel::completeFirstLoginPasswordChange,
                onLogout = { viewModel.logoutWorkspace() }
            )
        }
        viewModel != null && activeSession != null -> {
            AuthenticatedWorkspaceHost(
                viewModel = viewModel,
                session = activeSession,
                deviceId = deviceId,
                onOpenPlatformOwner = { showPlatformOwnerPortal = true }
            )
        }
        else -> {
            UnifiedAuthScreen(
                isGoogleSignedIn = currentUser != null,
                isOnline = isOnline,
                isSubmitting = isSubmitting,
                actionMessage = actionMessage,
                savedWorkspaces = savedWorkspaces,
                googleEmail = effectiveGoogleEmail,
                onDismissMessage = { viewModel?.clearMessage() },
                onLogin = { bizCode, username, password ->
                    viewModel?.loginWorkspace(bizCode, username, password)
                },
                onCreateAdmin = { fullName, bizName, username, pass, confirmPass, mobile, email ->
                    viewModel?.createAdministrationAccount(
                        fullName, bizName, username, pass, confirmPass, mobile, email
                    )
                },
                onResetAdminPassword = { bizCode, username, mobile, newPass, onDone ->
                    viewModel?.resetOwnAdminPassword(bizCode, username, mobile, newPass, onDone)
                },
                onSignOutGoogle = {
                    viewModel?.logoutWorkspace()
                    scope.launch { sessionPrefs.clearAllOnGoogleSignOut() }
                    signOutGoogle(credentialManager, scope)
                },
                canAttemptOfflineLogin = lastGoogleIdentity != null,
                onOpenPlatformOwner = { showPlatformOwnerPortal = true }
            )
        }
    }
}

@Composable
private fun AuthenticatedWorkspaceHost(
    viewModel: AppViewModel,
    session: com.example.domain.model.ActiveWorkspaceSession,
    deviceId: String,
    onOpenPlatformOwner: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val business by viewModel.business.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val permissions by viewModel.currentPermissions.collectAsStateWithLifecycle()
    val bookers by viewModel.bookers.collectAsStateWithLifecycle()
    val staffUsers by viewModel.staffUsers.collectAsStateWithLifecycle()
    val allPermissions by viewModel.allPermissions.collectAsStateWithLifecycle()
    val shops by viewModel.shops.collectAsStateWithLifecycle()
    val creditTransactions by viewModel.creditTransactions.collectAsStateWithLifecycle()
    val recoveryTransactions by viewModel.recoveryTransactions.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val unreadNotifications by viewModel.unreadNotificationCount.collectAsStateWithLifecycle()
    val chatGroups by viewModel.chatGroups.collectAsStateWithLifecycle()
    val shopBalances by viewModel.shopBalances.collectAsStateWithLifecycle()
    val bookerSummaries by viewModel.bookerSummaries.collectAsStateWithLifecycle()
    val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val dailyPerformanceReport by viewModel.dailyPerformanceReport.collectAsStateWithLifecycle()
    val pendingSyncItems by viewModel.pendingSyncItems.collectAsStateWithLifecycle()
    val unsyncedCount by viewModel.unsyncedCount.collectAsStateWithLifecycle()
    val hasUnsyncedData by viewModel.hasUnsyncedData.collectAsStateWithLifecycle()
    val syncHistory by viewModel.syncHistory.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val generatedCredentials by viewModel.generatedCredentials.collectAsStateWithLifecycle()
    val latestReceipt by viewModel.latestReceipt.collectAsStateWithLifecycle()
    val pakistanClockMillis by viewModel.pakistanClockMillis.collectAsStateWithLifecycle()
    val sessionExpiresAtMillis by viewModel.sessionExpiresAtMillis.collectAsStateWithLifecycle()
    val googleEmail = remember { Firebase.auth.currentUser?.email }

    val isBooker = session.role == AccountRole.BOOKER
    val isSuperAdmin = session.role == AccountRole.SUPER_ADMIN

    // Role-based Bottom Navigation (Phase 19):
    // - Admin: Dashboard, Bookers, Credit Shops, Recovery, Reports
    // - Staff: Only sections for which the Staff account has permissions
    // - Booker (Read-Only): Dashboard, My Shops, History, Reports, Profile
    val topDestinations = remember(isBooker, isSuperAdmin, permissions) {
        buildList {
            if (isBooker) {
                add(TopNavDest(ROUTE_DASHBOARD, ROUTE_DASHBOARD, "Dashboard", Icons.Default.Home, "nav_dashboard"))
                add(TopNavDest("$ROUTE_SHOPS?add=false", "$ROUTE_SHOPS?add={add}", "My Shops", Icons.Default.Storefront, "nav_shops"))
                add(TopNavDest(ROUTE_BOOKER_HISTORY, ROUTE_BOOKER_HISTORY, "History", Icons.Default.History, "nav_booker_history"))
                add(TopNavDest("$ROUTE_REPORTS?tab=1", "$ROUTE_REPORTS?tab={tab}", "Reports", Icons.Default.Assessment, "nav_reports"))
                add(TopNavDest(ROUTE_BOOKER_PROFILE, ROUTE_BOOKER_PROFILE, "Profile", Icons.Default.Person, "nav_booker_profile"))
            } else {
                if (isSuperAdmin || permissions.viewDashboard) {
                    add(TopNavDest(ROUTE_DASHBOARD, ROUTE_DASHBOARD, "Dashboard", Icons.Default.Home, "nav_dashboard"))
                }
                if (isSuperAdmin || permissions.viewBookers || permissions.manageBookers) {
                    add(TopNavDest(ROUTE_BOOKERS, ROUTE_BOOKERS, "Bookers", Icons.Default.Group, "nav_bookers"))
                }
                if (isSuperAdmin || permissions.viewShops) {
                    add(TopNavDest("$ROUTE_SHOPS?add=false", "$ROUTE_SHOPS?add={add}", "Credit Shops", Icons.Default.Storefront, "nav_shops"))
                }
                if (isSuperAdmin || permissions.recordRecovery) {
                    add(TopNavDest(ROUTE_RECORD_RECOVERY, "$ROUTE_RECORD_RECOVERY?shopId={shopId}", "Recovery", Icons.Default.Payments, "nav_recovery"))
                }
                if (isSuperAdmin || permissions.viewReports || permissions.generatePdfs) {
                    add(TopNavDest("$ROUTE_REPORTS?tab=1", "$ROUTE_REPORTS?tab={tab}", "Reports", Icons.Default.Assessment, "nav_reports"))
                }
                if (!isSuperAdmin && size < 5 && permissions.searchShops) {
                    add(TopNavDest(ROUTE_SEARCH, ROUTE_SEARCH, "Search", Icons.Default.Search, "nav_search"))
                }
            }
        }
    }

    val isTopLevelRoute = topDestinations.any { it.baseRoute == currentRoute }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen && isTopLevelRoute) {
                NavigationRail(
                    containerColor = HisabWhite,
                    contentColor = HisabNavy,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(Modifier.padding(top = 16.dp))
                    HisabProLogoBadge(sizeDp = 38)
                    Spacer(Modifier.padding(top = 16.dp))
                    topDestinations.forEach { dest ->
                        val selected = currentRoute == dest.baseRoute
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = {
                                Text(
                                    text = dest.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = HisabNavy,
                                selectedTextColor = HisabNavy,
                                indicatorColor = HisabNavyContainer,
                                unselectedIconColor = HisabSecondaryGray,
                                unselectedTextColor = HisabSecondaryGray
                            ),
                            modifier = Modifier.testTag(dest.tag)
                        )
                    }
                }
            }

            Scaffold(
                containerColor = HisabLightGray,
                contentWindowInsets = if (isTopLevelRoute) WindowInsets.navigationBars else WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!isWideScreen && isTopLevelRoute) {
                        NavigationBar(
                            containerColor = HisabWhite,
                            tonalElevation = 4.dp
                        ) {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.baseRoute
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    alwaysShowLabel = true,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = HisabNavy,
                                        selectedTextColor = HisabNavy,
                                        indicatorColor = HisabNavyContainer,
                                        unselectedIconColor = HisabSecondaryGray,
                                        unselectedTextColor = HisabSecondaryGray
                                    ),
                                    modifier = Modifier.testTag(dest.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isTopLevelRoute) innerPadding else PaddingValues(0.dp))
                ) {
                    val startRoute = remember(session.businessId, session.accountId) {
                        topDestinations.firstOrNull()?.baseRoute ?: ROUTE_NO_ACCESS
                    }
                    NavHost(
                        navController = navController,
                        startDestination = startRoute
                    ) {
                        composable(ROUTE_NO_ACCESS) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(HisabLightGray)
                            ) {
                                HisabNavyTopBar(
                                    title = business?.businessName ?: session.businessName,
                                    subtitle = "${session.fullName} • Restricted Staff Account"
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    EmptyStateCard(
                                        icon = Icons.Default.Lock,
                                        title = "Limited Permissions",
                                        message = "No navigation tabs are currently enabled for this Staff account. Ask your Super Admin to grant permissions."
                                    )
                                    Button(
                                        onClick = viewModel::logoutWorkspace,
                                        colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Logout Workspace", color = HisabWhite)
                                    }
                                }
                            }
                        }

                        composable(ROUTE_DASHBOARD) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(HisabNavy)
                                    .statusBarsPadding()
                            ) {
                                DashboardScreen(
                                    session = session,
                                    business = business,
                                    permissions = permissions,
                                    summary = dashboardSummary,
                                    shopBalances = shopBalances,
                                    creditTransactions = creditTransactions,
                                    recoveryTransactions = recoveryTransactions,
                                    auditLogs = auditLogs,
                                    unreadNotifications = unreadNotifications,
                                    pakistanClockMillis = pakistanClockMillis,
                                    isOnline = isOnline,
                                    unsyncedCount = unsyncedCount,
                                    isSyncing = isSyncing,
                                    dailyPerformanceReport = dailyPerformanceReport,
                                    actionMessage = actionMessage,
                                    onDismissMessage = viewModel::clearMessage,
                                    onNavigateNotifications = { navController.navigate(ROUTE_NOTIFICATIONS) },
                                    onNavigateSyncCenter = { navController.navigate(ROUTE_SYNC_CENTER) },
                                    onExportDailyPdf = { ctx, mode ->
                                        viewModel.handleDailyPerformancePdf(ctx, dailyPerformanceReport, mode)
                                    },
                                    onNavigateAddCredit = { navController.navigate(ROUTE_ADD_CREDIT) },
                                    onNavigateAddBooker = { navController.navigate(ROUTE_ADD_BOOKER) },
                                    onNavigateAddStaff = { navController.navigate(ROUTE_STAFF) },
                                    onNavigateAddShop = { navController.navigate("$ROUTE_SHOPS?add=true") },
                                    onNavigateRecordRecovery = { navController.navigate(ROUTE_RECORD_RECOVERY) },
                                    onNavigateShops = { navController.navigate("$ROUTE_SHOPS?add=false") },
                                    onNavigateReports = { navController.navigate("$ROUTE_REPORTS?tab=1") },
                                    onNavigateAllShopsPdf = { navController.navigate("$ROUTE_REPORTS?tab=0") },
                                    onNavigateSearch = { navController.navigate(ROUTE_SEARCH) },
                                    onNavigateShopDetail = { shopId -> navController.navigate("shop/$shopId") },
                                    onNavigateAllBookerCredit = { navController.navigate(ROUTE_ALL_BOOKER_CREDIT) },
                                    onNavigateGroupChat = { navController.navigate(ROUTE_CHAT_GROUPS) },
                                    onNavigateActivityLogs = { navController.navigate(ROUTE_AUDIT_LOGS) },
                                    onNavigateSettings = { navController.navigate(ROUTE_SETTINGS) },
                                    onOpenPlatformOwner = onOpenPlatformOwner,
                                    onLogoutWorkspace = viewModel::logoutWorkspace
                                )
                            }
                        }

                        composable(
                            route = "$ROUTE_SHOPS?add={add}",
                            arguments = listOf(navArgument("add") { type = NavType.BoolType; defaultValue = false })
                        ) { entry ->
                            val openAdd = entry.arguments?.getBoolean("add") ?: false
                            CreditShopsScreen(
                                session = session,
                                permissions = permissions,
                                shopBalances = shopBalances,
                                bookers = bookers,
                                isOnline = isOnline,
                                isSubmitting = isSubmitting,
                                actionMessage = actionMessage,
                                initialOpenAddShop = openAdd,
                                onDismissMessage = viewModel::clearMessage,
                                onBack = if (openAdd) ({ navController.popBackStack() }) else null,
                                onShopClick = { shopId -> navController.navigate("shop/$shopId") },
                                onCreateShop = viewModel::createShop
                            )
                        }

                        composable(
                            route = ROUTE_SHOP_DETAIL,
                            arguments = listOf(navArgument("shopId") { type = NavType.StringType })
                        ) { entry ->
                            val shopId = entry.arguments?.getString("shopId").orEmpty()
                            val summary = shopBalances.firstOrNull { it.shop.id == shopId }
                            val shopCredits = creditTransactions.filter { it.shopId == shopId }
                            val shopRecoveries = recoveryTransactions.filter { it.shopId == shopId }

                            ShopProfileScreen(
                                session = session,
                                businessName = business?.businessName ?: session.businessName,
                                settings = settings,
                                hasUnsyncedData = hasUnsyncedData,
                                permissions = permissions,
                                summary = summary,
                                credits = shopCredits,
                                recoveries = shopRecoveries,
                                bookers = bookers,
                                isOnline = isOnline,
                                isSubmitting = isSubmitting,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onBack = { navController.popBackStack() },
                                onAddCreditClick = { sId -> navController.navigate("$ROUTE_ADD_CREDIT?shopId=$sId") },
                                onRecordRecoveryClick = { sId -> navController.navigate("$ROUTE_RECORD_RECOVERY?shopId=$sId") },
                                onUpdateShop = viewModel::updateShop,
                                onToggleArchive = viewModel::toggleArchiveShop,
                                onReverseCredit = { tx, reason -> viewModel.reverseCredit(tx, reason) },
                                onReverseRecovery = { tx, reason -> viewModel.reverseRecovery(tx, reason) },
                                onCorrectRecovery = { tx, amt, dt, mth, ref, nts, rsn ->
                                    viewModel.correctRecovery(tx, amt, dt, mth, ref, nts, rsn)
                                },
                                onExportShopPdf = { ctx, s, c, r, label, mode, st, en ->
                                    viewModel.generateAndShareShopPdf(ctx, s, c, r, label, mode, st, en)
                                },
                                onDownloadPdf = viewModel::downloadGeneratedPdf
                            )
                        }

                        composable(
                            route = "$ROUTE_ADD_CREDIT?shopId={shopId}",
                            arguments = listOf(navArgument("shopId") { type = NavType.StringType; nullable = true; defaultValue = null })
                        ) { entry ->
                            val preselected = entry.arguments?.getString("shopId")
                            AddCreditScreen(
                                preselectedShopId = preselected,
                                bookers = bookers,
                                shopBalances = shopBalances,
                                settings = settings,
                                isOnline = isOnline,
                                isSubmitting = isSubmitting,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onBack = { navController.popBackStack() },
                                onQuickCreateShop = viewModel::createShop,
                                onSaveCredit = viewModel::addCredit
                            )
                        }

                        composable(
                            route = "$ROUTE_RECORD_RECOVERY?shopId={shopId}",
                            arguments = listOf(navArgument("shopId") { type = NavType.StringType; nullable = true; defaultValue = null })
                        ) { entry ->
                            val preselected = entry.arguments?.getString("shopId")
                            RecordRecoveryScreen(
                                preselectedShopId = preselected,
                                businessName = business?.businessName ?: session.businessName,
                                userRole = session.role,
                                bookers = bookers,
                                shopBalances = shopBalances,
                                settings = settings,
                                latestReceipt = latestReceipt,
                                isOnline = isOnline,
                                isSubmitting = isSubmitting,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onDismissReceipt = viewModel::clearLatestReceipt,
                                onBack = if (preselected != null) ({ navController.popBackStack() }) else null,
                                onSaveRecovery = viewModel::recordRecovery,
                                onDownloadPdf = viewModel::downloadGeneratedPdf
                            )
                        }

                        composable(ROUTE_BOOKERS) {
                            BookersScreen(
                                session = session,
                                permissions = permissions,
                                bookerSummaries = bookerSummaries,
                                isOnline = isOnline,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onBack = null,
                                onBookerClick = { bId -> navController.navigate("booker/$bId") },
                                onAddBookerClick = { navController.navigate(ROUTE_ADD_BOOKER) }
                            )
                        }

                        composable(ROUTE_ADD_BOOKER) {
                            AddBookerScreen(
                                availableShops = shops,
                                generatedCredentials = generatedCredentials,
                                isOnline = isOnline,
                                isSubmitting = isSubmitting,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onClearCredentials = viewModel::clearCredentialsDialog,
                                onBack = { navController.popBackStack() },
                                onGenerateBooker = viewModel::createBooker
                            )
                        }

                        composable(
                            route = ROUTE_BOOKER_DETAIL,
                            arguments = listOf(navArgument("bookerId") { type = NavType.StringType })
                        ) { entry ->
                            val bookerId = entry.arguments?.getString("bookerId").orEmpty()
                            val bSummary = bookerSummaries.firstOrNull { it.booker.id == bookerId }
                            val bShops = shopBalances.filter { it.shop.bookerId == bookerId && !it.shop.isArchived }
                            val bCredits = creditTransactions.filter { it.bookerId == bookerId }
                            val bRecoveries = recoveryTransactions.filter { it.bookerId == bookerId }

                            BookerDetailScreen(
                                session = session,
                                permissions = permissions,
                                bookerSummary = bSummary,
                                assignedShops = bShops,
                                bookerCredits = bCredits,
                                bookerRecoveries = bRecoveries,
                                isOnline = isOnline,
                                isSubmitting = isSubmitting,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onBack = { navController.popBackStack() },
                                onShopClick = { sId -> navController.navigate("shop/$sId") },
                                onUpdateBooker = viewModel::updateBookerProfile,
                                onToggleBookerStatus = viewModel::toggleBookerStatus,
                                onResetPassword = { booker, newTemp ->
                                    viewModel.resetBookerOrStaffPassword(
                                        accountId = booker.id,
                                        accountCode = booker.bookerCode,
                                        fullName = booker.fullName,
                                        username = booker.username,
                                        role = AccountRole.BOOKER,
                                        tempPassword = newTemp
                                    )
                                },
                                onExportBookerPdf = viewModel::handleBookerAllShopsCreditPdf
                            )
                        }

                        composable(ROUTE_STAFF) {
                            StaffManagementScreen(
                                session = session,
                                staffUsers = staffUsers,
                                allPermissions = allPermissions,
                                isOnline = isOnline,
                                isSubmitting = isSubmitting,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onBack = { navController.popBackStack() },
                                onCreateStaff = viewModel::createStaffUser,
                                onUpdatePermissions = viewModel::updateStaffPermissions,
                                onToggleStaffStatus = viewModel::toggleStaffStatus,
                                onResetStaffPassword = { user, newTemp ->
                                    viewModel.resetBookerOrStaffPassword(
                                        accountId = user.id,
                                        accountCode = user.userCode,
                                        fullName = user.fullName,
                                        username = user.username,
                                        role = AccountRole.STAFF,
                                        tempPassword = newTemp
                                    )
                                }
                            )
                        }

                        composable(
                            route = "$ROUTE_REPORTS?tab={tab}",
                            arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 1 })
                        ) { entry ->
                            val tab = entry.arguments?.getInt("tab") ?: 1
                            ReportsAndPdfScreen(
                                session = session,
                                initialTab = tab,
                                shops = shops,
                                shopBalances = shopBalances,
                                bookers = bookers,
                                bookerSummaries = bookerSummaries,
                                creditTransactions = creditTransactions,
                                recoveryTransactions = recoveryTransactions,
                                isOnline = isOnline,
                                hasUnsyncedData = hasUnsyncedData,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onBack = if (navController.previousBackStackEntry != null && !isTopLevelRoute) ({ navController.popBackStack() }) else null,
                                onShopClick = { sId -> navController.navigate("shop/$sId") },
                                onExportAllShopsPdf = { ctx, rows, label, mode ->
                                    viewModel.handleAllShopsPdf(ctx, rows, label, mode)
                                },
                                onExportDailyPdf = { ctx, report, mode ->
                                    viewModel.handleDailyPerformancePdf(ctx, report, mode)
                                },
                                onExportPeriodPdf = { ctx, report, label, mode ->
                                    viewModel.handlePeriodReportPdf(ctx, report, label, mode)
                                },
                                onExportBookerPdf = viewModel::handleBookerAllShopsCreditPdf
                            )
                        }

                        composable(ROUTE_SEARCH) {
                            GlobalSearchScreen(
                                session = session,
                                shopBalances = shopBalances,
                                bookers = bookers,
                                creditTransactions = creditTransactions,
                                recoveryTransactions = recoveryTransactions,
                                isOnline = isOnline,
                                onBack = { navController.popBackStack() },
                                onShopClick = { sId -> navController.navigate("shop/$sId") },
                                onBookerClick = { bId -> navController.navigate("booker/$bId") }
                            )
                        }

                        composable(ROUTE_AUDIT_LOGS) {
                            ActivityLogsScreen(
                                auditLogs = auditLogs,
                                isOnline = isOnline,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(ROUTE_NOTIFICATIONS) {
                            NotificationsScreen(
                                notifications = notifications,
                                isOnline = isOnline,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onBack = { navController.popBackStack() },
                                onMarkRead = viewModel::markNotificationRead,
                                onMarkAllRead = viewModel::markAllNotificationsRead
                            )
                        }

                        composable(ROUTE_BOOKER_HISTORY) {
                            BookerHistoryScreen(
                                creditTransactions = creditTransactions,
                                recoveryTransactions = recoveryTransactions,
                                isOnline = isOnline,
                                onShopClick = { sId -> navController.navigate("shop/$sId") }
                            )
                        }

                        composable(ROUTE_BOOKER_PROFILE) {
                            BookerProfileScreen(
                                session = session,
                                business = business,
                                booker = bookers.firstOrNull { it.id == session.bookerId },
                                assignedShopsCount = shops.count { !it.isArchived },
                                unreadNotifications = unreadNotifications,
                                sessionExpiresAtMillis = sessionExpiresAtMillis,
                                isOnline = isOnline,
                                isSubmitting = isSubmitting,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onNavigateNotifications = { navController.navigate(ROUTE_NOTIFICATIONS) },
                                onChangeOwnPassword = viewModel::changeOwnPassword,
                                onLogoutWorkspace = viewModel::logoutWorkspace
                            )
                        }

                        composable(ROUTE_SETTINGS) {
                            BusinessSettingsScreen(
                                session = session,
                                business = business,
                                settings = settings,
                                unreadNotifications = unreadNotifications,
                                sessionExpiresAtMillis = sessionExpiresAtMillis,
                                googleEmail = googleEmail,
                                isOnline = isOnline,
                                isSubmitting = isSubmitting,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onBack = { navController.popBackStack() },
                                onSaveSettings = viewModel::updateBusinessSettings,
                                onChangeOwnPassword = viewModel::changeOwnPassword,
                                onNavigateBookers = { navController.navigate(ROUTE_BOOKERS) },
                                onNavigateStaff = { navController.navigate(ROUTE_STAFF) },
                                onNavigateNotifications = { navController.navigate(ROUTE_NOTIFICATIONS) },
                                onNavigateActivityLogs = { navController.navigate(ROUTE_AUDIT_LOGS) },
                                onNavigateAllShopsPdf = { navController.navigate("$ROUTE_REPORTS?tab=0") },
                                onExportJson = viewModel::exportAllBusinessData,
                                onOpenPlatformOwner = onOpenPlatformOwner,
                                onLogoutWorkspace = viewModel::logoutWorkspace
                            )
                        }

                        composable(ROUTE_ALL_BOOKER_CREDIT) {
                            AllBookerCreditScreen(
                                bookerSummaries = bookerSummaries,
                                shopBalances = shopBalances,
                                creditTransactions = creditTransactions,
                                recoveryTransactions = recoveryTransactions,
                                isOnline = isOnline,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onBack = { navController.popBackStack() },
                                onOpenBookerRecord = { bId -> navController.navigate("booker/$bId") },
                                onExportBookerPdf = viewModel::handleBookerAllShopsCreditPdf
                            )
                        }

                        composable(ROUTE_SYNC_CENTER) {
                            OfflineSyncScreen(
                                session = session,
                                deviceId = deviceId,
                                isOnline = isOnline,
                                isSyncing = isSyncing,
                                isSubmitting = isSubmitting,
                                pendingItems = pendingSyncItems,
                                syncHistory = syncHistory,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onBack = { navController.popBackStack() },
                                onManualSyncNow = viewModel::manualSyncNow,
                                onApproveConflict = viewModel::approveSyncConflict,
                                onDiscardItem = viewModel::discardSyncItem
                            )
                        }

                        composable(ROUTE_CHAT_GROUPS) {
                            ChatGroupsListScreen(
                                session = session,
                                chatGroups = chatGroups,
                                bookers = bookers,
                                staffUsers = staffUsers,
                                isOnline = isOnline,
                                isSubmitting = isSubmitting,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onBack = { navController.popBackStack() },
                                onOpenGroup = { groupId -> navController.navigate("chat_group/$groupId") },
                                onCreateGroup = viewModel::createChatGroup
                            )
                        }

                        composable(
                            route = ROUTE_CHAT_DETAIL,
                            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                        ) { entry ->
                            val groupId = entry.arguments?.getString("groupId").orEmpty()
                            val group = chatGroups.firstOrNull { it.id == groupId }
                            val messages by remember(groupId) {
                                viewModel.observeChatMessages(groupId)
                            }.collectAsStateWithLifecycle(initialValue = emptyList())

                            GroupChatDetailScreen(
                                session = session,
                                group = group,
                                messages = messages,
                                bookers = bookers,
                                staffUsers = staffUsers,
                                isOnline = isOnline,
                                isSubmitting = isSubmitting,
                                actionMessage = actionMessage,
                                onDismissMessage = viewModel::clearMessage,
                                onNotifyAction = viewModel::notifyAction,
                                onBack = { navController.popBackStack() },
                                onUpdateMembers = viewModel::updateChatGroupMembers,
                                onSendText = { text, onDone -> viewModel.sendTextChatMessage(groupId, text, onDone) },
                                onSendMedia = { type, text, b64, fName, mime, dur, size ->
                                    viewModel.sendMediaChatMessage(
                                        groupId = groupId,
                                        type = type,
                                        text = text,
                                        mediaBase64 = b64,
                                        fileName = fName,
                                        mimeType = mime,
                                        durationSec = dur,
                                        fileSizeBytes = size
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Show modal credentials dialog only when not on AddBookerScreen (which renders credentials inline)
    if (generatedCredentials != null && currentRoute != ROUTE_ADD_BOOKER) {
        GeneratedCredentialsDialog(
            credentials = generatedCredentials!!,
            onDismiss = viewModel::clearCredentialsDialog
        )
    }
}
