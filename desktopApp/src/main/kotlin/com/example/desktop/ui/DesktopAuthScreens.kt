package com.example.desktop.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.desktop.state.DesktopAppState
import com.example.desktop.state.DesktopUiState

@Composable
fun DesktopSplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(DesktopColors.NavyDark),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            HisabProLogo(sizeDp = 96)
            Text("HisabPro", color = DesktopColors.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(
                "Business Credit, Recovery & Supplier Ledger — Windows Desktop",
                color = DesktopColors.GoldAccent,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(color = DesktopColors.GoldAccent, modifier = Modifier.size(32.dp))
            Text("Initializing encrypted SQLite vault & Firebase sync...", color = Color(0xFFB8C4D4), fontSize = 12.sp)
        }
    }
}

@Composable
fun DesktopAuthGateway(
    state: DesktopUiState,
    controller: DesktopAppState
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Workspace Login, 1 = Create Business Admin, 2 = Platform Owner
    var bizCode by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var bizName by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(state.firebaseSession?.email.orEmpty()) }
    var showTokenFallback by remember { mutableStateOf(false) }
    var pastedIdToken by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize().background(DesktopColors.LightGray)) {
        // Left Branding & Status Column
        Box(
            modifier = Modifier.weight(0.42f).fillMaxHeight().background(DesktopColors.NavyDark).padding(40.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                HisabProLogo(sizeDp = 82)
                Text("HisabPro Desktop", color = DesktopColors.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Unified Android + Windows Business Ledger",
                    color = DesktopColors.GoldAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Real-time shared Firebase Firestore cloud backend with local Windows SQLite offline protection, Supplier Ledgers, A4 PDF printing, Excel (.xlsx) exports, Inventory & Bookings.",
                    color = Color(0xFFD0DCEB),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                HorizontalDivider(color = DesktopColors.NavyLight)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        imageVector = if (state.isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = if (state.isOnline) Color(0xFF4ADE80) else DesktopColors.WarningOrange
                    )
                    Text(
                        text = if (state.isOnline) "Connected to Shared Firebase Cloud" else "Offline Mode Ready (SQLite Active)",
                        color = DesktopColors.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    "SQLite Integrity: ${state.sqliteIntegrityStatus.uppercase()}",
                    color = Color(0xFFAEC0D6),
                    fontSize = 12.sp
                )
            }
        }

        // Right Auth Card Column
        Box(
            modifier = Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(36.dp),
            contentAlignment = Alignment.Center
        ) {
            DesktopCard(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                // Step 1: Google Sign-In Gate
                if (state.firebaseSession == null) {
                    Text("Step 1 — Verify Google Identity", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "HisabPro connects to your shared company Firestore database using Google Sign-In. Sign in once on this PC to load your workspaces.",
                        fontSize = 13.sp,
                        color = DesktopColors.SecondaryGray
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { controller.signInWithGoogleBrowser() },
                        enabled = !state.isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Navy),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text("Sign In with Google (Browser)", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { showTokenFallback = !showTokenFallback }) {
                        Text(if (showTokenFallback) "Hide Manual ID Token Sign-In" else "Have a Google OAuth ID Token? Paste manually")
                    }
                    if (showTokenFallback) {
                        OutlinedTextField(
                            value = pastedIdToken,
                            onValueChange = { pastedIdToken = it },
                            label = { Text("Google OAuth ID Token (JWT)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { controller.signInWithGoogleIdToken(pastedIdToken) },
                            enabled = !state.isBusy && pastedIdToken.isNotBlank()
                        ) {
                            Text("Verify Token & Continue")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Google Verified", fontSize = 11.sp, color = DesktopColors.SuccessGreen, fontWeight = FontWeight.Bold)
                            Text(state.firebaseSession.email.ifBlank { state.firebaseSession.uid }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = { controller.logoutAll() }) { Text("Switch Google") }
                    }
                    Spacer(Modifier.height(12.dp))

                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Workspace Sign In") })
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Create Company") })
                        Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Owner Portal") })
                    }
                    Spacer(Modifier.height(16.dp))

                    if (state.bookmarks.isNotEmpty() && selectedTab == 0) {
                        Text("Saved Workspaces (Click to Autofill)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DesktopColors.SecondaryGray)
                        Spacer(Modifier.height(6.dp))
                        state.bookmarks.take(4).forEach { bm ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                                    bizCode = bm.businessCode
                                    username = bm.lastUsername
                                },
                                color = DesktopColors.SoftBlueBtn,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, DesktopColors.Border)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${bm.businessName} (${bm.businessCode})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("${bm.lastUsername} • ${bm.lastRole}", fontSize = 12.sp, color = DesktopColors.SecondaryGray)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    when (selectedTab) {
                        0 -> {
                            OutlinedTextField(
                                value = bizCode,
                                onValueChange = { bizCode = it },
                                label = { Text("Business Code (e.g. AHSA-402)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Username") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                                    }
                                }
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { controller.loginWorkspace(bizCode, username, password) },
                                enabled = !state.isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Navy),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Sign In & Sync Workspace", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        1 -> {
                            OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Super Admin Full Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = bizName, onValueChange = { bizName = it }, label = { Text("Company / Business Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Admin Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobile Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Business Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Strong Password (8+ chars, Upper/Lower/Digit)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation()
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { controller.createBusinessAdmin(fullName, bizName, username, password, mobile, email) },
                                enabled = !state.isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.SuccessGreen),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Register New Business Workspace", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        2 -> {
                            Text(
                                "Platform Super Owner Access (Restricted to verified muhammadtufailllc007@gmail.com)",
                                fontSize = 12.sp,
                                color = DesktopColors.SecondaryGray
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = username.ifBlank { "MrTufailadmin" },
                                onValueChange = { username = it },
                                label = { Text("Platform Owner Username") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Owner Password") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation()
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { controller.loginPlatformOwner(username.ifBlank { "MrTufailadmin" }, password) },
                                enabled = !state.isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.NavyDark),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Open Platform Owner Portal", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                if (state.isBusy) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.errorBanner?.let { err ->
                    Spacer(Modifier.height(12.dp))
                    Surface(color = DesktopColors.ErrorBg, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, DesktopColors.ErrorRed)) {
                        Text(err, color = DesktopColors.ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}
