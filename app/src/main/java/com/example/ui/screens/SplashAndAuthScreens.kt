package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.BuildConfig
import com.example.R
import com.example.data.firebase.onGoogleSignInClicked
import com.example.domain.model.UserWorkspaceBookmark
import com.example.ui.components.ActionMessageBanner
import com.example.ui.components.HisabBottomWaveFooter
import com.example.ui.components.HisabNavyTopBar
import com.example.ui.components.HisabProLogoBadge
import com.example.ui.components.HisabTopWaveBanner
import com.example.ui.components.OfflineBanner
import com.example.ui.theme.HisabBorder
import com.example.ui.theme.HisabGoldAccent
import com.example.ui.theme.HisabLightGray
import com.example.ui.theme.HisabNavy
import com.example.ui.theme.HisabNavyContainer
import com.example.ui.theme.HisabSecondaryGray
import com.example.ui.theme.HisabSoftBlueBtn
import com.example.ui.theme.HisabSuccessBg
import com.example.ui.theme.HisabSuccessGreen
import com.example.ui.theme.HisabWhite

enum class AuthUiPage {
    MAIN_LOGIN,
    ADMINISTRATION_ACCESS,
    CREATE_ADMIN_FORM
}

@Composable
fun SplashScreen() {
    val scale = remember { Animatable(0.86f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(420))
        scale.animateTo(1f, animationSpec = tween(420))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HisabNavy)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
                .padding(32.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.hisabpro_logo_rounded),
                contentDescription = "HisabPro Splash Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(136.dp)
                    .clip(RoundedCornerShape(30.dp))
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "HisabPro",
                style = MaterialTheme.typography.displaySmall,
                color = HisabWhite,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Har Udhaar Ka Poora Hisab.",
                style = MaterialTheme.typography.bodyLarge,
                color = HisabGoldAccent,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            CircularProgressIndicator(
                color = HisabWhite,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun UnifiedAuthScreen(
    isGoogleSignedIn: Boolean,
    isOnline: Boolean,
    isSubmitting: Boolean,
    actionMessage: Pair<Boolean, String>?,
    savedWorkspaces: List<UserWorkspaceBookmark>,
    googleEmail: String?,
    onDismissMessage: () -> Unit,
    onLogin: (businessCode: String, username: String, password: String) -> Unit,
    onCreateAdmin: (
        fullName: String,
        businessName: String,
        username: String,
        password: String,
        confirmPassword: String,
        mobileNumber: String,
        email: String
    ) -> Unit,
    onResetAdminPassword: (
        businessCode: String,
        username: String,
        mobile: String,
        newPass: String,
        onDone: () -> Unit
    ) -> Unit,
    onSignOutGoogle: () -> Unit,
    canAttemptOfflineLogin: Boolean = false,
    onOpenPlatformOwner: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var currentPage by remember { mutableStateOf(AuthUiPage.MAIN_LOGIN) }

    BackHandler(enabled = currentPage != AuthUiPage.MAIN_LOGIN) {
        currentPage = when (currentPage) {
            AuthUiPage.CREATE_ADMIN_FORM -> AuthUiPage.ADMINISTRATION_ACCESS
            AuthUiPage.ADMINISTRATION_ACCESS -> AuthUiPage.MAIN_LOGIN
            AuthUiPage.MAIN_LOGIN -> AuthUiPage.MAIN_LOGIN
        }
    }
    var googleLoading by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    // Main Login state
    var loginBizCode by remember { mutableStateOf("") }
    var loginUsername by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var showLoginPassword by remember { mutableStateOf(false) }
    var showBizCodeDialog by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }

    // Create Admin form state
    var fullName by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var adminUsername by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var adminConfirmPassword by remember { mutableStateOf("") }
    var showAdminPassword by remember { mutableStateOf(false) }
    var mobileNumber by remember { mutableStateOf("") }
    var emailAddress by remember { mutableStateOf(googleEmail.orEmpty()) }

    // Pending action queued when user taps Login / Create Admin before Google Sign-In completes
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(isGoogleSignedIn) {
        if (isGoogleSignedIn && pendingAction != null) {
            val action = pendingAction
            pendingAction = null
            action?.invoke()
        }
        if (isGoogleSignedIn && emailAddress.isBlank() && !googleEmail.isNullOrBlank()) {
            emailAddress = googleEmail
        }
    }

    LaunchedEffect(savedWorkspaces) {
        if (loginBizCode.isBlank() && savedWorkspaces.isNotEmpty()) {
            loginBizCode = savedWorkspaces.first().businessCode
            if (loginUsername.isBlank()) {
                loginUsername = savedWorkspaces.first().lastUsername
            }
        }
    }

    fun triggerGoogleSignIn(afterSuccess: (() -> Unit)? = null) {
        if (afterSuccess != null) {
            pendingAction = afterSuccess
        }
        googleLoading = true
        localError = null
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                googleLoading = false
            },
            onAuthError = { msg ->
                googleLoading = false
                pendingAction = null
                localError = msg
            },
            scope = scope,
            onAuthCancelled = {
                googleLoading = false
                pendingAction = null
            }
        )
    }

    fun runAfterGoogleAuth(action: () -> Unit) {
        if (isGoogleSignedIn || (!isOnline && canAttemptOfflineLogin)) {
            action()
        } else {
            triggerGoogleSignIn(afterSuccess = action)
        }
    }

    val effectiveMessage: Pair<Boolean, String>? =
        localError?.let { true to it } ?: actionMessage

    when (currentPage) {
        AuthUiPage.MAIN_LOGIN -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HisabWhite)
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(HisabNavy)
                        .statusBarsPadding()
                )
                HisabTopWaveBanner(heightDp = 46)

                OfflineBanner(isOnline = isOnline)
                ActionMessageBanner(
                    messageState = effectiveMessage,
                    onDismiss = {
                        localError = null
                        onDismissMessage()
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(2.dp))

                    HisabProLogoBadge(sizeDp = 76)

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "HisabPro",
                        style = MaterialTheme.typography.headlineLarge,
                        color = HisabNavy,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Har Udhaar Ka Poora Hisab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HisabSecondaryGray
                    )

                    Spacer(Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .widthIn(max = 460.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Subtle workspace selector pill when saved or entered
                        if (loginBizCode.isNotBlank() || savedWorkspaces.isNotEmpty()) {
                            Surface(
                                color = HisabSoftBlueBtn,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showBizCodeDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Business,
                                            contentDescription = null,
                                            tint = HisabNavy,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (loginBizCode.isNotBlank()) "Business Code: $loginBizCode" else "Select Business Code",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = HisabNavy
                                        )
                                    }
                                    Text(
                                        text = "Change",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = HisabNavy,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = loginUsername,
                            onValueChange = { loginUsername = it },
                            placeholder = { Text("Username") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = HisabNavy)
                            },
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
                                .testTag("login_username")
                        )

                        OutlinedTextField(
                            value = loginPassword,
                            onValueChange = { loginPassword = it },
                            placeholder = { Text("Password") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = HisabNavy)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showLoginPassword = !showLoginPassword }) {
                                    Icon(
                                        imageVector = if (showLoginPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility",
                                        tint = HisabSecondaryGray
                                    )
                                }
                            },
                            visualTransformation = if (showLoginPassword) VisualTransformation.None else PasswordVisualTransformation(),
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
                                .testTag("login_password")
                        )

                        Spacer(Modifier.height(2.dp))

                        Button(
                            onClick = {
                                if (loginBizCode.isBlank()) {
                                    showBizCodeDialog = true
                                } else {
                                    runAfterGoogleAuth {
                                        onLogin(loginBizCode, loginUsername, loginPassword)
                                    }
                                }
                            },
                            enabled = !isSubmitting && !googleLoading && loginUsername.isNotBlank() && loginPassword.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("login_submit_button")
                        ) {
                            if (isSubmitting || googleLoading) {
                                CircularProgressIndicator(color = HisabWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Login", style = MaterialTheme.typography.titleMedium, color = HisabWhite)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(onClick = { showForgotDialog = true }) {
                                Text(
                                    text = "Forgot Password?",
                                    color = HisabNavy,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (loginBizCode.isBlank()) {
                                TextButton(onClick = { showBizCodeDialog = true }) {
                                    Text(
                                        text = "• Enter Business Code",
                                        color = HisabSecondaryGray
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = HisabBorder)
                            Text(
                                text = "  OR  ",
                                style = MaterialTheme.typography.labelMedium,
                                color = HisabSecondaryGray
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = HisabBorder)
                        }

                        // Administration Access & Platform Owner Login Row (Both Visible Without Scrolling)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = HisabSoftBlueBtn,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, HisabBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { currentPage = AuthUiPage.ADMINISTRATION_ACCESS }
                                    .testTag("administration_login_button")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = HisabNavy,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Admin Access",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = HisabNavy,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                color = HisabNavyContainer.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.2.dp, HisabNavy),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = onOpenPlatformOwner)
                                    .testTag("platform_owner_entry_button")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = HisabNavy,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Owner Login",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = HisabNavy,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Google Cloud Auth Sync Indicator / Interactive Fallback Button
                        if (!isGoogleSignedIn) {
                            OutlinedButton(
                                onClick = { triggerGoogleSignIn() },
                                enabled = !googleLoading,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, HisabBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("google_sign_in_button")
                            ) {
                                Icon(
                                    Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = HisabNavy,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Sign in with Google", color = HisabNavy)
                            }
                        } else {
                            Surface(
                                color = HisabSuccessBg,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = HisabSuccessGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Cloud Connected${googleEmail?.let { ": $it" } ?: ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = HisabSuccessGreen
                                        )
                                    }
                                    Text(
                                        text = "Switch",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HisabNavy,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { onSignOutGoogle() }
                                    )
                                }
                            }
                        }

                        if (!isOnline && canAttemptOfflineLogin) {
                            Surface(
                                color = HisabSoftBlueBtn,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, HisabBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Offline Login Ready — Sign in with your Business Code, username & password cached on this device.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HisabNavy,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "HisabPro • v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = HisabSecondaryGray.copy(alpha = 0.75f)
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }

        AuthUiPage.ADMINISTRATION_ACCESS -> {
            // Panel 2: Administration Access Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HisabWhite)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Back Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { currentPage = AuthUiPage.MAIN_LOGIN }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Login",
                            tint = HisabNavy
                        )
                    }
                    Text(
                        text = "Back to Login",
                        style = MaterialTheme.typography.labelLarge,
                        color = HisabNavy
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Shield with Lock mark
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(HisabNavy),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Administration Security Emblem",
                            tint = HisabWhite,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Administration Access",
                        style = MaterialTheme.typography.headlineMedium,
                        color = HisabNavy,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Get started with HisabPro",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HisabSecondaryGray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(30.dp))

                    // Card Button 1: Create Admin Account
                    Card(
                        onClick = { currentPage = AuthUiPage.CREATE_ADMIN_FORM },
                        colors = CardDefaults.cardColors(containerColor = HisabNavy),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .widthIn(max = 440.dp)
                            .fillMaxWidth()
                            .height(74.dp)
                            .testTag("btn_open_create_admin")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = HisabWhite,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "Create Admin Account",
                                style = MaterialTheme.typography.titleMedium,
                                color = HisabWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Card Button 2: Admin Login
                    Card(
                        onClick = {
                            val adminWs = savedWorkspaces.firstOrNull { it.lastRole == "SUPER_ADMIN" }
                            if (adminWs != null) {
                                loginBizCode = adminWs.businessCode
                                loginUsername = adminWs.lastUsername
                            }
                            currentPage = AuthUiPage.MAIN_LOGIN
                        },
                        colors = CardDefaults.cardColors(containerColor = HisabNavy),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .widthIn(max = 440.dp)
                            .fillMaxWidth()
                            .height(74.dp)
                            .testTag("btn_admin_login_redirect")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Login,
                                contentDescription = null,
                                tint = HisabWhite,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "Admin Login",
                                style = MaterialTheme.typography.titleMedium,
                                color = HisabWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Card Button 3: Platform Owner Control Panel
                    Card(
                        onClick = onOpenPlatformOwner,
                        colors = CardDefaults.cardColors(containerColor = HisabSoftBlueBtn),
                        border = BorderStroke(1.5.dp, HisabNavy),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .widthIn(max = 440.dp)
                            .fillMaxWidth()
                            .height(74.dp)
                            .testTag("btn_open_platform_owner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = HisabNavy,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Platform Owner Control",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HisabNavy,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Master Super Owner Governance Portal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = HisabSecondaryGray
                                )
                            }
                        }
                    }
                }

                // Bottom curved wave footer (matches Panel 2)
                HisabBottomWaveFooter {
                    Text(
                        text = "HisabPro",
                        style = MaterialTheme.typography.headlineSmall,
                        color = HisabWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Har Udhaar Ka Poora Hisab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HisabWhite.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        AuthUiPage.CREATE_ADMIN_FORM -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HisabLightGray)
                    .navigationBarsPadding()
            ) {
                HisabNavyTopBar(
                    title = "Create Admin Account",
                    subtitle = "First-Time Workspace Setup",
                    onBack = { currentPage = AuthUiPage.ADMINISTRATION_ACCESS }
                )

                OfflineBanner(isOnline = isOnline)
                ActionMessageBanner(
                    messageState = effectiveMessage,
                    onDismiss = {
                        localError = null
                        onDismissMessage()
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HisabWhite),
                        border = BorderStroke(1.dp, HisabBorder),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .widthIn(max = 540.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Launch Isolated Business Workspace",
                                style = MaterialTheme.typography.titleMedium,
                                color = HisabNavy,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Creates your business workspace in PKR (Asia/Karachi) and grants full Super Admin permissions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HisabSecondaryGray
                            )

                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Full Name *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("reg_fullname")
                            )
                            OutlinedTextField(
                                value = businessName,
                                onValueChange = { businessName = it },
                                label = { Text("Business Name *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("reg_business_name")
                            )
                            OutlinedTextField(
                                value = adminUsername,
                                onValueChange = { adminUsername = it },
                                label = { Text("Username *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("reg_username")
                            )
                            OutlinedTextField(
                                value = mobileNumber,
                                onValueChange = { mobileNumber = it },
                                label = { Text("Mobile Number (e.g. 03001234567) *") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("reg_mobile")
                            )
                            OutlinedTextField(
                                value = emailAddress,
                                onValueChange = { emailAddress = it },
                                label = { Text("Email Address (Optional)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("reg_email")
                            )
                            OutlinedTextField(
                                value = adminPassword,
                                onValueChange = { adminPassword = it },
                                label = { Text("Password (min 6 chars) *") },
                                trailingIcon = {
                                    IconButton(onClick = { showAdminPassword = !showAdminPassword }) {
                                        Icon(
                                            if (showAdminPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle password"
                                        )
                                    }
                                },
                                visualTransformation = if (showAdminPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("reg_password")
                            )
                            OutlinedTextField(
                                value = adminConfirmPassword,
                                onValueChange = { adminConfirmPassword = it },
                                label = { Text("Confirm Password *") },
                                visualTransformation = if (showAdminPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("reg_confirm_password")
                            )

                            Button(
                                onClick = {
                                    runAfterGoogleAuth {
                                        onCreateAdmin(
                                            fullName,
                                            businessName,
                                            adminUsername,
                                            adminPassword,
                                            adminConfirmPassword,
                                            mobileNumber,
                                            emailAddress
                                        )
                                    }
                                },
                                enabled = !isSubmitting && !googleLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("create_admin_account_button")
                            ) {
                                if (isSubmitting || googleLoading) {
                                    CircularProgressIndicator(color = HisabWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Create Account", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBizCodeDialog) {
        BusinessCodePickerDialog(
            initialCode = loginBizCode,
            savedWorkspaces = savedWorkspaces,
            onDismiss = { showBizCodeDialog = false },
            onConfirm = { enteredCode ->
                loginBizCode = enteredCode.trim().uppercase()
                showBizCodeDialog = false
                if (loginUsername.isNotBlank() && loginPassword.isNotBlank() && loginBizCode.isNotBlank()) {
                    runAfterGoogleAuth {
                        onLogin(loginBizCode, loginUsername, loginPassword)
                    }
                }
            }
        )
    }

    if (showForgotDialog) {
        ForgotPasswordDialog(
            initialBizCode = loginBizCode,
            initialUsername = loginUsername,
            isSubmitting = isSubmitting,
            onDismiss = { showForgotDialog = false },
            onResetAdmin = { code, user, mobile, newPass ->
                runAfterGoogleAuth {
                    onResetAdminPassword(code, user, mobile, newPass) {
                        showForgotDialog = false
                    }
                }
            }
        )
    }
}

@Composable
private fun BusinessCodePickerDialog(
    initialCode: String,
    savedWorkspaces: List<UserWorkspaceBookmark>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by remember { mutableStateOf(initialCode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HisabWhite,
        title = { Text("Business Workspace Code", color = HisabNavy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your Business Code (e.g. ALNO-482) provided by your Super Admin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabSecondaryGray
                )
                if (savedWorkspaces.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        savedWorkspaces.forEach { ws ->
                            Surface(
                                color = if (code.equals(ws.businessCode, true)) HisabNavyContainer else HisabLightGray,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, HisabBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { code = ws.businessCode }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(ws.businessName, style = MaterialTheme.typography.labelMedium, color = HisabNavy)
                                    Text(ws.businessCode, style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Business Code *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("login_business_code")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code) },
                enabled = code.trim().length >= 3,
                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy)
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ForgotPasswordDialog(
    initialBizCode: String,
    initialUsername: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onResetAdmin: (String, String, String, String) -> Unit
) {
    var code by remember { mutableStateOf(initialBizCode) }
    var username by remember { mutableStateOf(initialUsername) }
    var mobile by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HisabWhite,
        title = { Text("Password Recovery", color = HisabNavy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Super Admins can reset their password after verifying workspace ownership and registered mobile number. Staff & Bookers must ask their Super Admin to reset their password from User/Booker Management.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabSecondaryGray
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Business Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Admin Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Registered Mobile Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("New Password (min 6 chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onResetAdmin(code, username, mobile, newPass) },
                enabled = !isSubmitting && code.isNotBlank() && username.isNotBlank() && mobile.isNotBlank() && newPass.length >= 6,
                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy)
            ) {
                Text("Reset Admin Password")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ChangeTemporaryPasswordScreen(
    fullName: String,
    username: String,
    roleLabel: String,
    isSubmitting: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onSubmitNewPassword: (String, String) -> Unit,
    onLogout: () -> Unit
) {
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HisabLightGray)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Surface(color = HisabNavy, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HisabProLogoBadge(sizeDp = 40)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Security Update Required", color = HisabWhite, style = MaterialTheme.typography.titleMedium)
                        Text("$fullName • $roleLabel", color = HisabGoldAccent, style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = onLogout) {
                    Text("Logout", color = HisabWhite)
                }
            }
        }

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
                modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Change Temporary Password",
                        style = MaterialTheme.typography.titleLarge,
                        color = HisabNavy
                    )
                    Text(
                        text = "Welcome $username! For your account security, please replace your temporary password before opening your dashboard.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HisabSecondaryGray
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New Password (min 6 chars)") },
                        trailingIcon = {
                            IconButton(onClick = { showPass = !showPass }) {
                                Icon(
                                    if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("first_login_new_password")
                    )
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("first_login_confirm_password")
                    )
                    Button(
                        onClick = { onSubmitNewPassword(newPass, confirmPass) },
                        enabled = !isSubmitting && newPass.length >= 6 && confirmPass.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("update_temp_password_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = HisabWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save New Password & Continue")
                        }
                    }
                }
            }
        }
    }
}
