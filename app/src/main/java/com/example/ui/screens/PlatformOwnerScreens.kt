package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.CompanyIsolatedSnapshot
import com.example.data.firebase.OwnerAuthRequirement
import com.example.data.firebase.PlatformOwnerRepository
import com.example.domain.model.AccountRole
import com.example.domain.model.Business
import com.example.domain.model.PlatformBackupSnapshot
import com.example.ui.GeneratedOwnerTempPassword
import com.example.ui.PlatformOwnerViewModel
import com.example.ui.components.ActionMessageBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabNavyTopBar
import com.example.ui.components.OfflineBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.HisabBorder
import com.example.ui.theme.HisabErrorBg
import com.example.ui.theme.HisabErrorRed
import com.example.ui.theme.HisabGoldAccent
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
fun PlatformOwnerRootScreen(
    viewModel: PlatformOwnerViewModel,
    isGoogleSignedIn: Boolean,
    googleEmail: String?,
    isOnline: Boolean,
    onBackToMainLogin: () -> Unit,
    onTriggerGoogleSignIn: () -> Unit,
    onSwitchGoogleAccount: () -> Unit
) {
    val authReq by viewModel.authRequirement.collectAsStateWithLifecycle()
    val ownerSession by viewModel.ownerSession.collectAsStateWithLifecycle()
    val cloudState by viewModel.cloudState.collectAsStateWithLifecycle()
    val filteredCompanies by viewModel.filteredCompanies.collectAsStateWithLifecycle()
    val selectedCompany by viewModel.selectedCompany.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val tempPassGenerated by viewModel.lastGeneratedTempPassword.collectAsStateWithLifecycle()

    BackHandler {
        if (selectedCompany != null) {
            viewModel.selectCompany(null)
        } else {
            onBackToMainLogin()
        }
    }

    val isAuthorizedEmail = isGoogleSignedIn &&
        googleEmail?.trim().equals(PlatformOwnerRepository.OWNER_EMAIL, ignoreCase = true)

    if (!isAuthorizedEmail) {
        PlatformOwnerGoogleGateScreen(
            isGoogleSignedIn = isGoogleSignedIn,
            googleEmail = googleEmail,
            isOnline = isOnline,
            actionMessage = actionMessage,
            onDismissMessage = viewModel::clearMessage,
            onBack = onBackToMainLogin,
            onSignIn = onTriggerGoogleSignIn,
            onSwitchGoogle = onSwitchGoogleAccount
        )
        return
    }

    val currentSession = ownerSession
    if (currentSession == null) {
        PlatformOwnerAuthGateScreen(
            authRequirement = authReq,
            googleEmail = googleEmail.orEmpty(),
            isOnline = isOnline,
            isLoading = isLoading,
            actionMessage = actionMessage,
            onDismissMessage = viewModel::clearMessage,
            onBack = onBackToMainLogin,
            onRefresh = viewModel::refreshAuthRequirement,
            onSetupFirstTime = viewModel::setupFirstTimePassword,
            onLogin = viewModel::loginOwner,
            onResetPasswordWithGoogle = { user, p1, p2, done ->
                viewModel.resetOwnerPassword(user, p1, p2, done)
            }
        )
        return
    }

    val companyDetail = selectedCompany
    if (companyDetail != null) {
        IsolatedCompanyControlScreen(
            snapshot = companyDetail,
            isOnline = isOnline,
            isLoading = isLoading,
            actionMessage = actionMessage,
            onDismissMessage = viewModel::clearMessage,
            onBack = { viewModel.selectCompany(null) },
            onRefresh = viewModel::refreshCloudState,
            onSuspendOrActivateCompany = { biz, suspend, reason, done ->
                viewModel.suspendOrActivateCompany(biz, suspend, reason, done)
            },
            onToggleAccountStatus = { biz, id, uname, role, status ->
                viewModel.toggleAccountStatus(biz, id, uname, role, status)
            },
            onGenerateTempPassword = { biz, id, uname, role, forceChange ->
                viewModel.generateTempPassword(biz, id, uname, role, forceChange)
            },
            onResetAccountPassword = { biz, id, uname, role, newPass, forceChange, done ->
                viewModel.resetAccountPassword(biz, id, uname, role, newPass, forceChange, done)
            },
            onForcePasswordChange = { biz, id, uname, role ->
                viewModel.forcePasswordChange(biz, id, uname, role)
            },
            onRevokeSessions = { biz, id, uname, role ->
                viewModel.revokeUserSessions(biz, id, uname, role)
            }
        )
    } else {
        PlatformOwnerDashboardScreen(
            ownerUsername = currentSession.username,
            ownerEmail = currentSession.email,
            cloudState = cloudState,
            filteredCompanies = filteredCompanies,
            searchQuery = searchQuery,
            isOnline = isOnline,
            isLoading = isLoading,
            actionMessage = actionMessage,
            onDismissMessage = viewModel::clearMessage,
            onBack = onBackToMainLogin,
            onSearchChange = viewModel::updateSearchQuery,
            onRefresh = viewModel::refreshCloudState,
            onSelectCompany = { viewModel.selectCompany(it.business.id) },
            onCreateBackup = viewModel::createCloudBackup,
            onRestoreBackup = { bkpId, bizId -> viewModel.restoreBackup(bkpId, bizId) },
            onChangeOwnerPassword = { curr, newP, confP, done ->
                viewModel.changeOwnerPassword(curr, newP, confP, done)
            },
            onRevokeAllOwnerSessions = viewModel::revokeAllOwnerSessions,
            onLogoutOwner = viewModel::logoutOwner
        )
    }

    if (tempPassGenerated != null) {
        OwnerGeneratedTempPasswordDialog(
            info = tempPassGenerated!!,
            onDismiss = viewModel::clearGeneratedTempPassword
        )
    }
}

@Composable
private fun PlatformOwnerGoogleGateScreen(
    isGoogleSignedIn: Boolean,
    googleEmail: String?,
    isOnline: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSwitchGoogle: () -> Unit
) {
    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Platform Owner Control",
                subtitle = "Restricted Root Access",
                onBack = onBack
            )
        },
        containerColor = HisabLightGray
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            OfflineBanner(isOnline = isOnline)
            ActionMessageBanner(messageState = actionMessage, onDismiss = onDismissMessage)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = HisabWhite),
                    border = BorderStroke(1.dp, HisabBorder),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(HisabNavy),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = HisabGoldAccent,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Text(
                            text = "Platform Owner Identity Verification",
                            style = MaterialTheme.typography.titleLarge,
                            color = HisabNavy,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "This panel is above all registered companies and restricted exclusively to the verified Platform Owner Google account (${PlatformOwnerRepository.OWNER_EMAIL}).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HisabSecondaryGray
                        )
                        if (isGoogleSignedIn && !googleEmail.isNullOrBlank()) {
                            Surface(
                                color = HisabErrorBg,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Signed in as $googleEmail — not authorized for Platform Owner access.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HisabErrorRed,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Button(
                                onClick = onSwitchGoogle,
                                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Switch Google Account")
                            }
                        } else {
                            Button(
                                onClick = onSignIn,
                                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("owner_google_signin_btn")
                            ) {
                                Text("Verify with Google Sign-In")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformOwnerAuthGateScreen(
    authRequirement: OwnerAuthRequirement?,
    googleEmail: String,
    isOnline: Boolean,
    isLoading: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSetupFirstTime: (String, String, String) -> Unit,
    onLogin: (String, String) -> Unit,
    onResetPasswordWithGoogle: (String, String, String, () -> Unit) -> Unit
) {
    var username by remember { mutableStateOf(PlatformOwnerRepository.OWNER_USERNAME) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Platform Owner Login",
                subtitle = googleEmail,
                onBack = onBack,
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                .navigationBarsPadding()
        ) {
            OfflineBanner(isOnline = isOnline)
            ActionMessageBanner(messageState = actionMessage, onDismiss = onDismissMessage)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (authRequirement == null && isLoading) {
                    CircularProgressIndicator(color = HisabNavy)
                } else {
                    val isFirstTimeSetup = authRequirement is OwnerAuthRequirement.NeedsFirstTimePasswordSetup
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HisabWhite),
                        border = BorderStroke(1.dp, HisabBorder),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = HisabNavy, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isFirstTimeSetup) "First-Time Platform Owner Setup" else "Platform Owner Sign In",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = HisabNavy,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Verified Google Identity: $googleEmail",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HisabSuccessGreen
                                    )
                                }
                            }

                            Text(
                                text = if (isFirstTimeSetup) {
                                    "Create a strong password (min 8 chars, upper, lower, digit). Stored securely with PBKDF2-HMAC-SHA256 — never plaintext."
                                } else {
                                    "Sign in with your permanent Platform Owner username and salted PBKDF2 password."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = HisabSecondaryGray
                            )

                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Platform Owner Username") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(if (isFirstTimeSetup) "owner_setup_username" else "owner_login_username")
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text(if (isFirstTimeSetup) "Create Strong Password *" else "Password *") },
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle password"
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(if (isFirstTimeSetup) "owner_setup_password" else "owner_login_password")
                            )

                            if (isFirstTimeSetup) {
                                OutlinedTextField(
                                    value = confirmPassword,
                                    onValueChange = { confirmPassword = it },
                                    label = { Text("Confirm Strong Password *") },
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("owner_setup_confirm_password")
                                )
                            }

                            Button(
                                onClick = {
                                    if (isFirstTimeSetup) {
                                        onSetupFirstTime(username, password, confirmPassword)
                                    } else {
                                        onLogin(username, password)
                                    }
                                },
                                enabled = !isLoading && username.isNotBlank() && password.isNotBlank() && (!isFirstTimeSetup || confirmPassword.isNotBlank()),
                                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag(if (isFirstTimeSetup) "owner_setup_submit" else "owner_login_submit")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(color = HisabWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(if (isFirstTimeSetup) "Set Owner Password & Enter" else "Unlock Control Panel")
                                }
                            }

                            if (!isFirstTimeSetup) {
                                TextButton(
                                    onClick = { showForgotDialog = true },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Reset Owner Password (Verified Google)", color = HisabNavy)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForgotDialog) {
        var rUser by remember { mutableStateOf(PlatformOwnerRepository.OWNER_USERNAME) }
        var rPass by remember { mutableStateOf("") }
        var rConf by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            containerColor = HisabWhite,
            title = { Text("Reset Platform Owner Password", color = HisabNavy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Authorized via verified Google identity ($googleEmail). Previous Owner sessions will be revoked immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HisabSecondaryGray
                    )
                    OutlinedTextField(value = rUser, onValueChange = { rUser = it }, label = { Text("Owner Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = rPass, onValueChange = { rPass = it }, label = { Text("New Strong Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = rConf, onValueChange = { rConf = it }, label = { Text("Confirm Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetPasswordWithGoogle(rUser, rPass, rConf) { showForgotDialog = false }
                    },
                    enabled = !isLoading && rPass.isNotBlank() && rConf.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = HisabNavy)
                ) { Text("Reset Password") }
            },
            dismissButton = { TextButton(onClick = { showForgotDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PlatformOwnerDashboardScreen(
    ownerUsername: String,
    ownerEmail: String,
    cloudState: com.example.data.firebase.PlatformCloudState?,
    filteredCompanies: List<CompanyIsolatedSnapshot>,
    searchQuery: String,
    isOnline: Boolean,
    isLoading: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSelectCompany: (CompanyIsolatedSnapshot) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: (String, String?) -> Unit,
    onChangeOwnerPassword: (String, String, String, () -> Unit) -> Unit,
    onRevokeAllOwnerSessions: () -> Unit,
    onLogoutOwner: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showChangePassDialog by remember { mutableStateOf(false) }
    var restoreTargetBackup by remember { mutableStateOf<PlatformBackupSnapshot?>(null) }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Platform Owner Console",
                subtitle = "$ownerUsername • $ownerEmail",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onRefresh, modifier = Modifier.testTag("owner_refresh_btn")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Platform Metrics")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.testTag("owner_security_menu")) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Owner Security Menu")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Change Owner Password") },
                                leadingIcon = { Icon(Icons.Default.Password, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showChangePassDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Revoke All Owner Sessions") },
                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onRevokeAllOwnerSessions()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sign Out Owner Panel", color = HisabErrorRed) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = HisabErrorRed) },
                                onClick = {
                                    menuExpanded = false
                                    onLogoutOwner()
                                }
                            )
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

            // Security Access Disclosure Banner
            Surface(
                color = HisabNavyContainer.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = HisabNavy, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Owner Support & Security Access: PBKDF2-HMAC-SHA256 salted hashes only — plaintext passwords are never stored or visible.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HisabNavy
                    )
                }
            }

            // Global Search Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HisabWhite)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search company, Business Code/ID, Admin, Staff, Booker, Shop UID...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("owner_global_search_input")
                )
            }

            TabRow(selectedTabIndex = selectedTab, containerColor = HisabWhite, contentColor = HisabNavy) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Overview") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Companies (${filteredCompanies.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Activity") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Backups") })
            }

            if (cloudState == null && isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HisabNavy)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            val m = cloudState?.metrics
                            if (m != null) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = HisabWhite),
                                        border = BorderStroke(1.dp, HisabBorder),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text("Global Platform Metrics", style = MaterialTheme.typography.titleMedium, color = HisabNavy, fontWeight = FontWeight.Bold)
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                OwnerMiniStat("Companies", "${m.totalBusinesses}", HisabNavy)
                                                OwnerMiniStat("Active", "${m.activeBusinesses}", HisabSuccessGreen)
                                                OwnerMiniStat("Suspended", "${m.suspendedBusinesses}", HisabErrorRed)
                                                OwnerMiniStat("Admins", "${m.totalAdmins}", HisabNavy)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                OwnerMiniStat("Staff", "${m.totalStaff}", HisabNavy)
                                                OwnerMiniStat("Bookers", "${m.totalBookers}", HisabNavy)
                                                OwnerMiniStat("Shops", "${m.totalShops}", HisabNavy)
                                                OwnerMiniStat("Tx (Cr/Rc)", "${m.totalCreditTransactions}/${m.totalRecoveryTransactions}", HisabNavy)
                                            }
                                            HorizontalDivider(color = HisabBorder)
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                OwnerMiniStat("Platform Credit", CurrencyFormatter.formatPkr(m.totalCreditPaisa), HisabNavy)
                                                OwnerMiniStat("Recovered", CurrencyFormatter.formatPkr(m.totalRecoveredPaisa), HisabSuccessGreen)
                                                OwnerMiniStat("Outstanding", CurrencyFormatter.formatPkr(m.totalOutstandingPaisa), HisabErrorRed)
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Text("Recent Global Activity", style = MaterialTheme.typography.titleSmall, color = HisabNavy, fontWeight = FontWeight.Bold)
                            }
                            val recentAct = cloudState?.platformActivity.orEmpty().take(12)
                            if (recentAct.isEmpty()) {
                                item {
                                    EmptyStateCard(Icons.Default.History, "No Platform Logs", "Owner and platform-level actions appear here.")
                                }
                            } else {
                                items(recentAct, key = { it.id }) { log ->
                                    PlatformActivityCard(log)
                                }
                            }
                        }

                        1 -> {
                            if (filteredCompanies.isEmpty()) {
                                item {
                                    EmptyStateCard(Icons.Default.Business, "No Companies Found", "No registered businesses match '$searchQuery'.")
                                }
                            } else {
                                items(filteredCompanies, key = { it.business.id }) { snap ->
                                    val biz = snap.business
                                    val isActive = biz.status == "ACTIVE"
                                    val adminUser = snap.users.firstOrNull { it.role == AccountRole.SUPER_ADMIN.key }
                                    Card(
                                        onClick = { onSelectCompany(snap) },
                                        colors = CardDefaults.cardColors(containerColor = HisabWhite),
                                        border = BorderStroke(1.dp, HisabBorder),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("owner_company_card_${biz.businessCode}")
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(biz.businessName, style = MaterialTheme.typography.titleMedium, color = HisabNavy, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        text = "Code: ${biz.businessCode} • ID: ${biz.id}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = HisabSecondaryGray
                                                    )
                                                }
                                                StatusPill(
                                                    text = biz.status,
                                                    containerColor = if (isActive) HisabSuccessBg else HisabErrorBg,
                                                    contentColor = if (isActive) HisabSuccessGreen else HisabErrorRed
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open Isolated Company", tint = HisabSecondaryGray)
                                            }
                                            Text(
                                                text = "Admin: ${biz.ownerName}${adminUser?.let { " (@${it.username})" } ?: ""} • ${biz.ownerMobile}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = HisabSecondaryGray
                                            )
                                            Text(
                                                text = "Staff: ${snap.users.count { it.role == AccountRole.STAFF.key }} • Bookers: ${snap.bookers.size} • Shops: ${snap.shops.size} • Outstanding: ${CurrencyFormatter.formatPkr(snap.totalOutstandingPaisa)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = HisabNavy
                                            )
                                            if (biz.lastActivityMillis > 0L) {
                                                Text(
                                                    text = "Last Active: ${PakistanTime.formatDateTime(biz.lastActivityMillis)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = HisabSecondaryGray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            val act = cloudState?.platformActivity.orEmpty()
                            if (act.isEmpty()) {
                                item { EmptyStateCard(Icons.Default.History, "No Activity Logs", "Platform owner activity records will appear here.") }
                            } else {
                                items(act, key = { it.id }) { log ->
                                    PlatformActivityCard(log)
                                }
                            }
                        }

                        else -> {
                            item {
                                Button(
                                    onClick = onCreateBackup,
                                    enabled = isOnline && !isLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("owner_create_backup_button")
                                ) {
                                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Create Full Cloud Backup Snapshot")
                                }
                            }
                            val bkps = cloudState?.backups.orEmpty()
                            if (bkps.isEmpty()) {
                                item {
                                    EmptyStateCard(Icons.Default.CloudDownload, "No Cloud Backups", "Tap above to capture an isolated point-in-time backup of all companies.")
                                }
                            } else {
                                items(bkps, key = { it.id }) { bkp ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = HisabWhite),
                                        border = BorderStroke(1.dp, HisabBorder),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(bkp.id, style = MaterialTheme.typography.titleSmall, color = HisabNavy, fontWeight = FontWeight.Bold)
                                                Text(PakistanTime.formatDateTime(bkp.createdAtMillis), style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                                            }
                                            Text(
                                                text = "Businesses: ${bkp.totalBusinesses} • Users: ${bkp.totalUsers} • Bookers: ${bkp.totalBookers} • Shops: ${bkp.totalShops}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = HisabSecondaryGray
                                            )
                                            Text(
                                                text = "Credits: ${bkp.totalCreditTx} • Recoveries: ${bkp.totalRecoveryTx}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = HisabNavy
                                            )
                                            OutlinedButton(
                                                onClick = { restoreTargetBackup = bkp },
                                                enabled = isOnline && !isLoading,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth().height(48.dp)
                                            ) {
                                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Restore Snapshot...")
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

    if (showChangePassDialog) {
        var curr by remember { mutableStateOf("") }
        var newP by remember { mutableStateOf("") }
        var confP by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePassDialog = false },
            containerColor = HisabWhite,
            title = { Text("Change Platform Owner Password", color = HisabNavy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = curr, onValueChange = { curr = it }, label = { Text("Current Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newP, onValueChange = { newP = it }, label = { Text("New Strong Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = confP, onValueChange = { confP = it }, label = { Text("Confirm Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = { onChangeOwnerPassword(curr, newP, confP) { showChangePassDialog = false } },
                    enabled = !isLoading && curr.isNotBlank() && newP.isNotBlank() && confP.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = HisabNavy)
                ) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { showChangePassDialog = false }) { Text("Cancel") } }
        )
    }

    if (restoreTargetBackup != null) {
        val bkp = restoreTargetBackup!!
        var selectedBizId by remember { mutableStateOf<String?>(null) }
        val companies = cloudState?.companies.orEmpty()
        AlertDialog(
            onDismissRequest = { restoreTargetBackup = null },
            containerColor = HisabWhite,
            title = { Text("Restore Backup ${bkp.id}", color = HisabNavy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose an individual isolated company to restore, or restore all companies contained in this snapshot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HisabSecondaryGray
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(
                                selected = selectedBizId == null,
                                onClick = { selectedBizId = null },
                                label = { Text("All Companies") }
                            )
                        }
                        items(companies, key = { it.business.id }) { c ->
                            FilterChip(
                                selected = selectedBizId == c.business.id,
                                onClick = { selectedBizId = c.business.id },
                                label = { Text(c.business.businessCode) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreBackup(bkp.id, selectedBizId)
                        restoreTargetBackup = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HisabWarningOrange)
                ) { Text("Restore Now") }
            },
            dismissButton = { TextButton(onClick = { restoreTargetBackup = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun IsolatedCompanyControlScreen(
    snapshot: CompanyIsolatedSnapshot,
    isOnline: Boolean,
    isLoading: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSuspendOrActivateCompany: (Business, Boolean, String, () -> Unit) -> Unit,
    onToggleAccountStatus: (Business, String, String, AccountRole, String) -> Unit,
    onGenerateTempPassword: (Business, String, String, AccountRole, Boolean) -> Unit,
    onResetAccountPassword: (Business, String, String, AccountRole, String, Boolean, () -> Unit) -> Unit,
    onForcePasswordChange: (Business, String, String, AccountRole) -> Unit,
    onRevokeSessions: (Business, String, String, AccountRole) -> Unit
) {
    val biz = snapshot.business
    val isActive = biz.status == "ACTIVE"
    var detailTab by remember { mutableIntStateOf(0) }
    var showSuspendDialog by remember { mutableStateOf(false) }
    var resetTarget by remember { mutableStateOf<Triple<String, String, AccountRole>?>(null) }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = biz.businessName,
                subtitle = "Isolated Company • ${biz.businessCode} (${biz.id.take(10)})",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Company")
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

            TabRow(selectedTabIndex = detailTab, containerColor = HisabWhite, contentColor = HisabNavy) {
                Tab(selected = detailTab == 0, onClick = { detailTab = 0 }, text = { Text("Accounts (${snapshot.users.size + snapshot.bookers.size})") })
                Tab(selected = detailTab == 1, onClick = { detailTab = 1 }, text = { Text("Shops (${snapshot.shops.size})") })
                Tab(selected = detailTab == 2, onClick = { detailTab = 2 }, text = { Text("Tx & Logs") })
            }

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
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Strictly Isolated Workspace Data", style = MaterialTheme.typography.titleSmall, color = HisabNavy, fontWeight = FontWeight.Bold)
                                StatusPill(
                                    text = biz.status,
                                    containerColor = if (isActive) HisabSuccessBg else HisabErrorBg,
                                    contentColor = if (isActive) HisabSuccessGreen else HisabErrorRed
                                )
                            }
                            Text("Owner: ${biz.ownerName} • ${biz.ownerMobile}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "Credit: ${CurrencyFormatter.formatPkr(snapshot.totalCreditPaisa)} • Recovered: ${CurrencyFormatter.formatPkr(snapshot.totalRecoveredPaisa)} • Outstanding: ${CurrencyFormatter.formatPkr(snapshot.totalOutstandingPaisa)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = HisabNavy
                            )
                            if (biz.suspendedReason.isNotBlank()) {
                                Text("Suspension Reason: ${biz.suspendedReason}", style = MaterialTheme.typography.bodySmall, color = HisabErrorRed)
                            }
                            Button(
                                onClick = {
                                    if (isActive) showSuspendDialog = true
                                    else onSuspendOrActivateCompany(biz, false, "Reactivated by Platform Owner") {}
                                },
                                enabled = isOnline && !isLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = if (isActive) HisabErrorRed else HisabSuccessGreen),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("owner_toggle_company_status_button")
                            ) {
                                Text(if (isActive) "Suspend Company (Preserve Data)" else "Reactivate Company")
                            }
                        }
                    }
                }

                when (detailTab) {
                    0 -> {
                        item {
                            Text("Business Admins & Staff (${snapshot.users.size})", style = MaterialTheme.typography.titleSmall, color = HisabNavy, fontWeight = FontWeight.Bold)
                        }
                        items(snapshot.users, key = { "u_${it.id}" }) { u ->
                            val role = AccountRole.fromKey(u.role)
                            OwnerAccountControlCard(
                                name = u.fullName,
                                username = u.username,
                                code = u.userCode,
                                accountId = u.id,
                                role = role,
                                status = u.status,
                                mustChange = u.mustChangePassword,
                                enabled = isOnline && !isLoading,
                                onToggleStatus = { onToggleAccountStatus(biz, u.id, u.username, role, u.status) },
                                onGenerateTemp = { onGenerateTempPassword(biz, u.id, u.username, role, true) },
                                onSetPassword = { resetTarget = Triple(u.id, u.username, role) },
                                onForceChange = { onForcePasswordChange(biz, u.id, u.username, role) },
                                onRevokeSessions = { onRevokeSessions(biz, u.id, u.username, role) }
                            )
                        }
                        item {
                            Text("Bookers (${snapshot.bookers.size})", style = MaterialTheme.typography.titleSmall, color = HisabNavy, fontWeight = FontWeight.Bold)
                        }
                        items(snapshot.bookers, key = { "b_${it.id}" }) { b ->
                            OwnerAccountControlCard(
                                name = b.fullName,
                                username = b.username,
                                code = b.bookerCode,
                                accountId = b.id,
                                role = AccountRole.BOOKER,
                                status = b.status,
                                mustChange = b.mustChangePassword,
                                enabled = isOnline && !isLoading,
                                onToggleStatus = { onToggleAccountStatus(biz, b.id, b.username, AccountRole.BOOKER, b.status) },
                                onGenerateTemp = { onGenerateTempPassword(biz, b.id, b.username, AccountRole.BOOKER, true) },
                                onSetPassword = { resetTarget = Triple(b.id, b.username, AccountRole.BOOKER) },
                                onForceChange = { onForcePasswordChange(biz, b.id, b.username, AccountRole.BOOKER) },
                                onRevokeSessions = { onRevokeSessions(biz, b.id, b.username, AccountRole.BOOKER) }
                            )
                        }
                    }

                    1 -> {
                        if (snapshot.shops.isEmpty()) {
                            item { EmptyStateCard(Icons.Default.Business, "No Shops", "This company has not registered any shops yet.") }
                        } else {
                            items(snapshot.shops, key = { it.id }) { s ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = HisabWhite),
                                    border = BorderStroke(1.dp, HisabBorder),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("${s.shopName} (${s.shopUid})", fontWeight = FontWeight.Bold, color = HisabNavy)
                                        Text("Owner: ${s.ownerName} • Booker: ${s.bookerName} • ${s.city}", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        item {
                            Text(
                                text = "Credits: ${snapshot.credits.size} • Recoveries/Reversals: ${snapshot.recoveries.size} • Audit Logs: ${snapshot.auditLogs.size}",
                                style = MaterialTheme.typography.titleSmall,
                                color = HisabNavy,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(snapshot.auditLogs.take(40), key = { it.id }) { log ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = HisabWhite),
                                border = BorderStroke(1.dp, HisabBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        StatusPill(log.action, HisabNavyContainer, HisabNavy)
                                        Text("${log.dateFormatted} ${log.timeFormatted}", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                                    }
                                    Text(log.description, style = MaterialTheme.typography.bodySmall)
                                    Text("@${log.username} (${log.role})", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSuspendDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSuspendDialog = false },
            containerColor = HisabWhite,
            title = { Text("Suspend ${biz.businessName}?", color = HisabErrorRed) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Suspending blocks workspace access and sync while safely preserving all shops and financial ledgers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HisabSecondaryGray
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Suspension Reason *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSuspendOrActivateCompany(biz, true, reason) { showSuspendDialog = false }
                    },
                    enabled = reason.trim().length >= 3,
                    colors = ButtonDefaults.buttonColors(containerColor = HisabErrorRed)
                ) { Text("Suspend Company") }
            },
            dismissButton = { TextButton(onClick = { showSuspendDialog = false }) { Text("Cancel") } }
        )
    }

    if (resetTarget != null) {
        val (accId, uname, role) = resetTarget!!
        var newPass by remember { mutableStateOf("") }
        var forceChange by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { resetTarget = null },
            containerColor = HisabWhite,
            title = { Text("Reset Password for @$uname", color = HisabNavy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Passwords are hashed with PBKDF2-HMAC-SHA256. Existing passwords are never stored in plaintext.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HisabSecondaryGray
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New Password (min 6 chars)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = forceChange, onCheckedChange = { forceChange = it })
                        Text("Force password change on next login", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetAccountPassword(biz, accId, uname, role, newPass, forceChange) {
                            resetTarget = null
                        }
                    },
                    enabled = newPass.trim().length >= 6,
                    colors = ButtonDefaults.buttonColors(containerColor = HisabNavy)
                ) { Text("Set Password") }
            },
            dismissButton = { TextButton(onClick = { resetTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun OwnerAccountControlCard(
    name: String,
    username: String,
    code: String,
    accountId: String,
    role: AccountRole,
    status: String,
    mustChange: Boolean,
    enabled: Boolean,
    onToggleStatus: () -> Unit,
    onGenerateTemp: () -> Unit,
    onSetPassword: () -> Unit,
    onForceChange: () -> Unit,
    onRevokeSessions: () -> Unit
) {
    val isActive = status == "ACTIVE"
    Card(
        colors = CardDefaults.cardColors(containerColor = HisabWhite),
        border = BorderStroke(1.dp, HisabBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("$name (@$username)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = HisabNavy)
                    Text("${role.label} • $code • ID: $accountId", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                    if (mustChange) {
                        Text("Mandatory password change pending", style = MaterialTheme.typography.labelSmall, color = HisabWarningOrange)
                    }
                }
                StatusPill(
                    text = status,
                    containerColor = if (isActive) HisabSuccessBg else HisabErrorBg,
                    contentColor = if (isActive) HisabSuccessGreen else HisabErrorRed
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    OutlinedButton(onClick = onGenerateTemp, enabled = enabled, modifier = Modifier.height(48.dp)) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Gen Temp Pass")
                    }
                }
                item {
                    OutlinedButton(onClick = onSetPassword, enabled = enabled, modifier = Modifier.height(48.dp)) {
                        Text("Reset Pass")
                    }
                }
                item {
                    OutlinedButton(onClick = onForceChange, enabled = enabled, modifier = Modifier.height(48.dp)) {
                        Text("Force Change")
                    }
                }
                item {
                    OutlinedButton(onClick = onRevokeSessions, enabled = enabled, modifier = Modifier.height(48.dp)) {
                        Text("Revoke Sessions")
                    }
                }
                item {
                    OutlinedButton(onClick = onToggleStatus, enabled = enabled, modifier = Modifier.height(48.dp)) {
                        Text(if (isActive) "Suspend" else "Activate", color = if (isActive) HisabErrorRed else HisabSuccessGreen)
                    }
                }
            }
        }
    }
}

@Composable
private fun OwnerGeneratedTempPasswordDialog(
    info: GeneratedOwnerTempPassword,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val shareText = "HisabPro Temporary Password\nBusiness Code: ${info.businessCode}\nUsername: ${info.username}\nTemporary Password: ${info.tempPassword}"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HisabWhite,
        title = { Text("Temporary Password Generated", color = HisabNavy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Shown once only — not stored in plaintext on server or device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabWarningOrange
                )
                Surface(color = HisabLightGray, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Business Code: ${info.businessCode}", fontWeight = FontWeight.SemiBold)
                        Text("Username: @${info.username}", fontWeight = FontWeight.SemiBold)
                        Text("Temporary Password: ${info.tempPassword}", fontWeight = FontWeight.Bold, color = HisabNavy)
                        Text("Force Change on Next Login: ${if (info.forceChangeOnNextLogin) "Yes" else "No"}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Temporary Password"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Share")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Temp Password", shareText))
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun PlatformActivityCard(log: com.example.domain.model.PlatformActivityLog) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HisabWhite),
        border = BorderStroke(1.dp, HisabBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatusPill(log.action, HisabNavyContainer, HisabNavy)
                Text("${log.dateFormatted} ${log.timeFormatted}", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
            }
            Text(log.details, style = MaterialTheme.typography.bodySmall)
            if (log.targetBusinessName.isNotBlank()) {
                Text("Company: ${log.targetBusinessName}", style = MaterialTheme.typography.labelSmall, color = HisabNavy)
            }
        }
    }
}

@Composable
private fun OwnerMiniStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
        Text(value, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
