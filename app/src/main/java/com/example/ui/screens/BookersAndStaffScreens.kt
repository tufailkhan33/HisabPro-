package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.util.PakistanTime
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AccountRole
import com.example.domain.model.ActiveWorkspaceSession
import com.example.domain.model.Booker
import com.example.domain.model.BookerBalanceSummary
import com.example.domain.model.BusinessUser
import com.example.domain.model.CreditTransaction
import com.example.domain.model.GeneratedCredentials
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.Shop
import com.example.domain.model.ShopBalanceSummary
import com.example.domain.model.StaffPermission
import com.example.ui.PdfExportMode
import com.example.ui.components.ActionMessageBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabNavyTopBar
import com.example.ui.components.OfflineBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.HisabBlackText
import com.example.ui.theme.HisabBorder
import com.example.ui.theme.HisabCreamBg
import com.example.ui.theme.HisabCreamBorder
import com.example.ui.theme.HisabErrorBg
import com.example.ui.theme.HisabErrorRed
import com.example.ui.theme.HisabKpiBlueBg
import com.example.ui.theme.HisabLightGray
import com.example.ui.theme.HisabNavy
import com.example.ui.theme.HisabSecondaryGray
import com.example.ui.theme.HisabSuccessBg
import com.example.ui.theme.HisabSuccessGreen
import com.example.ui.theme.HisabWhite
import com.example.util.CurrencyFormatter
import com.example.util.PasswordHasher

/**
 * Panel 4: Booker Management Screen
 */
@Composable
fun BookersScreen(
    session: ActiveWorkspaceSession,
    permissions: StaffPermission,
    bookerSummaries: List<BookerBalanceSummary>,
    isOnline: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onBack: (() -> Unit)? = null,
    onBookerClick: (String) -> Unit,
    onAddBookerClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val canManage = session.role == AccountRole.SUPER_ADMIN ||
        (session.role == AccountRole.STAFF && permissions.manageBookers)

    val filtered = remember(bookerSummaries, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) bookerSummaries else bookerSummaries.filter {
            it.booker.fullName.lowercase().contains(q) ||
                it.booker.bookerCode.lowercase().contains(q) ||
                it.booker.username.lowercase().contains(q) ||
                it.booker.mobileNumber.lowercase().contains(q)
        }
    }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Booker Management",
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

            // Search bar + Full-width "+ Add Booker" CTA (Panel 4)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HisabWhite)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Booker...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = HisabSecondaryGray) },
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
                        .testTag("search_booker_input")
                )

                if (canManage) {
                    Button(
                        onClick = onAddBookerClick,
                        colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("add_booker_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add Booker", style = MaterialTheme.typography.titleSmall, color = HisabWhite)
                    }
                }
            }

            if (filtered.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.Default.Badge,
                        title = "No Bookers Found",
                        message = if (canManage) "Tap '+ Add Booker' above to register a field Booker and generate login credentials." else "No bookers registered in this workspace."
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.booker.id }) { summary ->
                        val b = summary.booker
                        val isActive = b.status == "ACTIVE"
                        Card(
                            onClick = { onBookerClick(b.id) },
                            colors = CardDefaults.cardColors(containerColor = HisabWhite),
                            border = BorderStroke(1.dp, HisabBorder),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("booker_card_${b.bookerCode}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(HisabKpiBlueBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = HisabNavy,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = b.fullName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = HisabBlackText,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${b.bookerCode} • @${b.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HisabSecondaryGray
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = if (isActive) HisabSuccessBg else HisabErrorBg,
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = if (isActive) HisabSuccessGreen else HisabErrorRed,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = if (isActive) "Active" else "Inactive",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isActive) HisabSuccessGreen else HisabErrorRed,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${summary.assignedShopsCount} Shops",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HisabSecondaryGray
                                    )
                                    Text(
                                        text = CurrencyFormatter.formatPkr(summary.outstandingPaisa),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = HisabBlackText,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Open Booker",
                                    tint = HisabSecondaryGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Panel 5: Dedicated Add Booker Screen with inline Account Credentials warm cream card
 */
@Composable
fun AddBookerScreen(
    availableShops: List<Shop>,
    generatedCredentials: GeneratedCredentials?,
    isOnline: Boolean,
    isSubmitting: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onClearCredentials: () -> Unit,
    onBack: () -> Unit,
    onGenerateBooker: (
        fullName: String,
        username: String,
        tempPassword: String,
        mobile: String,
        address: String,
        notes: String,
        assignedShopIds: List<String>,
        onDone: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var tempPassword by remember { mutableStateOf(PasswordHasher.generateTemporaryPassword()) }
    var mobile by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var shopsExpanded by remember { mutableStateOf(false) }
    var selectedShopIds by remember { mutableStateOf(setOf<String>()) }

    val activeShops = remember(availableShops) { availableShops.filter { !it.isArchived } }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Add Booker",
                onBack = {
                    onClearCredentials()
                    onBack()
                }
            )
        },
        containerColor = HisabWhite
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OfflineBanner(isOnline = isOnline)
            ActionMessageBanner(messageState = actionMessage, onDismiss = onDismissMessage)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name *") },
                    placeholder = { Text("Ali Raza") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("booker_fullname_input")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username *") },
                    placeholder = { Text("aliraza123") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("booker_username_input")
                )

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it },
                    label = { Text("Mobile Number *") },
                    placeholder = { Text("0300-1234567") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("booker_mobile_input")
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    placeholder = { Text("Lahore, Pakistan") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tempPassword,
                        onValueChange = { tempPassword = it },
                        label = { Text("Login Password (min 6 chars) *") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("booker_password_input")
                    )
                    IconButton(onClick = { tempPassword = PasswordHasher.generateTemporaryPassword() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate Password", tint = HisabNavy)
                    }
                }

                // Expandable Assigned Shops Selector (Panel 5 "Assigned Shops -> Select Shops")
                Surface(
                    color = HisabLightGray,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HisabBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { shopsExpanded = !shopsExpanded }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Assigned Shops", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                                Text(
                                    text = if (selectedShopIds.isEmpty()) "Select Shops (Optional)" else "${selectedShopIds.size} shops selected",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HisabNavy,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(
                                imageVector = if (shopsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle Shop Picker",
                                tint = HisabNavy
                            )
                        }
                        if (shopsExpanded) {
                            Spacer(Modifier.height(8.dp))
                            if (activeShops.isEmpty()) {
                                Text("No existing shops yet — you can assign shops when adding them.", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                            } else {
                                activeShops.forEach { shop ->
                                    val checked = selectedShopIds.contains(shop.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedShopIds = if (checked) selectedShopIds - shop.id else selectedShopIds + shop.id
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                            contentDescription = null,
                                            tint = HisabNavy
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("${shop.shopName} (${shop.shopUid})", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        onGenerateBooker(fullName, username, tempPassword, mobile, address, notes, selectedShopIds.toList()) {
                            tempPassword = PasswordHasher.generateTemporaryPassword()
                        }
                    },
                    enabled = !isSubmitting && fullName.isNotBlank() && username.isNotBlank() && tempPassword.length >= 6 && mobile.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_booker_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = HisabWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Generate Account", style = MaterialTheme.typography.titleMedium, color = HisabWhite)
                    }
                }

                // Inline Warm Cream Account Credentials Card (matches Panel 5)
                if (generatedCredentials != null) {
                    val shareText = buildString {
                        appendLine("HisabPro Booker Credentials")
                        appendLine("Name: ${generatedCredentials.fullName} (${generatedCredentials.accountCode})")
                        appendLine("Business Code: ${generatedCredentials.businessCode}")
                        appendLine("Username: ${generatedCredentials.username}")
                        appendLine("Password: ${generatedCredentials.temporaryPassword}")
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HisabCreamBg),
                        border = BorderStroke(1.dp, HisabCreamBorder),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = HisabNavy, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Account Credentials", style = MaterialTheme.typography.titleSmall, color = HisabNavy, fontWeight = FontWeight.Bold)
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("HisabPro Credentials", shareText))
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Credentials", tint = HisabNavy)
                                    }
                                    IconButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share Booker Credentials"))
                                        }
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share Credentials", tint = HisabNavy)
                                    }
                                }
                            }
                            Text("Booker ID: ${generatedCredentials.accountCode}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Business Code: ${generatedCredentials.businessCode}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Username: ${generatedCredentials.username}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Password: ${generatedCredentials.temporaryPassword}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = HisabNavy)
                            Text(
                                text = "Please share these credentials securely.",
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

@Composable
private fun StatMini(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
        Text(value, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookerDetailScreen(
    session: ActiveWorkspaceSession,
    permissions: StaffPermission,
    bookerSummary: BookerBalanceSummary?,
    assignedShops: List<ShopBalanceSummary>,
    bookerCredits: List<CreditTransaction>,
    bookerRecoveries: List<RecoveryTransaction>,
    isOnline: Boolean,
    isSubmitting: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onShopClick: (String) -> Unit,
    onUpdateBooker: (Booker, String, String, String, String, () -> Unit) -> Unit,
    onToggleBookerStatus: (Booker) -> Unit,
    onResetPassword: (Booker, String) -> Unit,
    onExportBookerPdf: (Context, Booker, List<ShopBalanceSummary>, List<CreditTransaction>, List<RecoveryTransaction>, PdfExportMode, Long?, Long?) -> Unit
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showResetPassDialog by remember { mutableStateOf(false) }
    var useCustomRange by remember { mutableStateOf(false) }
    var startMillis by remember { mutableLongStateOf(PakistanTime.thisMonthRange().first) }
    var endMillis by remember { mutableLongStateOf(PakistanTime.todayRange().second) }
    var showRangeDialog by remember { mutableStateOf(false) }
    val activeStart = if (useCustomRange) PakistanTime.startOfDay(startMillis) else null
    val activeEnd = if (useCustomRange) PakistanTime.endOfDay(endMillis) else null
    val canManage = session.role == AccountRole.SUPER_ADMIN ||
        (session.role == AccountRole.STAFF && permissions.manageBookers)

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = bookerSummary?.booker?.fullName ?: "Booker Profile",
                subtitle = bookerSummary?.booker?.let { "${it.bookerCode} • @${it.username}" },
                onBack = onBack
            )
        },
        containerColor = HisabLightGray
    ) { innerPadding ->
        if (bookerSummary == null) {
            Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                EmptyStateCard(Icons.Default.Badge, "Booker Not Found", "This booker profile could not be loaded.")
            }
            return@Scaffold
        }

        val b = bookerSummary.booker
        val isActive = b.status == "ACTIVE"

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
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(b.fullName, style = MaterialTheme.typography.titleLarge, color = HisabNavy)
                                StatusPill(
                                    text = b.status,
                                    containerColor = if (isActive) HisabSuccessBg else HisabErrorBg,
                                    contentColor = if (isActive) HisabSuccessGreen else HisabErrorRed
                                )
                            }
                            Text("Booker Code: ${b.bookerCode}  •  Username: @${b.username}", style = MaterialTheme.typography.bodyMedium)
                            Text("Mobile: ${b.mobileNumber}", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                            if (b.address.isNotBlank()) {
                                Text("Area: ${b.address}", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = HisabBorder)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                StatMini("Shops", "${bookerSummary.assignedShopsCount}", HisabNavy)
                                StatMini("Total Credit", CurrencyFormatter.formatPkr(bookerSummary.totalCreditPaisa), HisabNavy)
                                StatMini("Recovered", CurrencyFormatter.formatPkr(bookerSummary.totalRecoveredPaisa), HisabSuccessGreen)
                                StatMini("Outstanding", CurrencyFormatter.formatPkr(bookerSummary.outstandingPaisa), HisabErrorRed)
                            }
                            if (canManage) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showEditDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit")
                                    }
                                    OutlinedButton(
                                        onClick = { showResetPassDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Set Pass")
                                    }
                                    OutlinedButton(
                                        onClick = { onToggleBookerStatus(b) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(if (isActive) "Deactivate" else "Activate", color = if (isActive) HisabErrorRed else HisabSuccessGreen)
                                    }
                                }
                            }
                        }
                    }
                }

                // Combined All-Shops Credit PDF Card for this Booker
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HisabWhite),
                        border = BorderStroke(1.dp, HisabBorder),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "All-Shops Combined Credit PDF",
                                style = MaterialTheme.typography.titleSmall,
                                color = HisabNavy,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Generate a single combined recovery ledger PDF with ${assignedShops.size} assigned shops for ${b.fullName}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = HisabSecondaryGray
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { showRangeDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(44.dp)
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (useCustomRange) {
                                            "Period: ${PakistanTime.formatDate(startMillis)} - ${PakistanTime.formatDate(endMillis)}"
                                        } else {
                                            "Period: All Time (Filter)"
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (useCustomRange) {
                                    TextButton(onClick = { useCustomRange = false }) { Text("Clear") }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onExportBookerPdf(
                                            context, b, assignedShops, bookerCredits, bookerRecoveries, PdfExportMode.VIEW, activeStart, activeEnd
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("booker_pdf_view")
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("View")
                                }
                                OutlinedButton(
                                    onClick = {
                                        onExportBookerPdf(
                                            context, b, assignedShops, bookerCredits, bookerRecoveries, PdfExportMode.DOWNLOAD, activeStart, activeEnd
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("booker_pdf_download")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Download")
                                }
                                Button(
                                    onClick = {
                                        onExportBookerPdf(
                                            context, b, assignedShops, bookerCredits, bookerRecoveries, PdfExportMode.SHARE, activeStart, activeEnd
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("booker_pdf_share")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share")
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Assigned Shops (${assignedShops.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = HisabNavy,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (assignedShops.isEmpty()) {
                    item {
                        EmptyStateCard(Icons.Default.Badge, "No Assigned Shops", "No shops are currently assigned to ${b.fullName}.")
                    }
                } else {
                    items(assignedShops, key = { it.shop.id }) { sb ->
                        ShopMiniSummaryCard(sb = sb, onClick = { onShopClick(sb.shop.id) })
                    }
                }

                item {
                    Text(
                        text = "Recent Booker Transactions (${bookerCredits.size + bookerRecoveries.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = HisabNavy,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(bookerCredits.take(10), key = { "cr_${it.id}" }) { tx ->
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
                        onClick = { onShopClick(tx.shopId) }
                    )
                }

                items(bookerRecoveries.take(10), key = { "rc_${it.id}" }) { tx ->
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
                        onClick = { onShopClick(tx.shopId) }
                    )
                }
            }
        }
    }

    if (showEditDialog && bookerSummary != null) {
        val b = bookerSummary.booker
        var name by remember { mutableStateOf(b.fullName) }
        var mobile by remember { mutableStateOf(b.mobileNumber) }
        var addr by remember { mutableStateOf(b.address) }
        var nts by remember { mutableStateOf(b.notes) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = HisabWhite,
            title = { Text("Edit Booker Profile", color = HisabNavy) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobile *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text("Area / Address") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nts, onValueChange = { nts = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = { onUpdateBooker(b, name, mobile, addr, nts) { showEditDialog = false } },
                    enabled = !isSubmitting && name.isNotBlank() && mobile.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = HisabNavy)
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } }
        )
    }

    if (showResetPassDialog && bookerSummary != null) {
        val b = bookerSummary.booker
        DirectPasswordResetDialog(
            accountLabel = "${b.fullName} (@${b.username})",
            isSubmitting = isSubmitting,
            onDismiss = { showResetPassDialog = false },
            onConfirm = { newPass ->
                onResetPassword(b, newPass)
                showResetPassDialog = false
            }
        )
    }

    if (showRangeDialog) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = PakistanTime.toDatePickerUtcMillis(startMillis),
            initialSelectedEndDateMillis = PakistanTime.toDatePickerUtcMillis(endMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showRangeDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = pickerState.selectedStartDateMillis
                        val e = pickerState.selectedEndDateMillis ?: s
                        if (s != null && e != null) {
                            startMillis = PakistanTime.fromDatePickerUtcMillis(s, startMillis)
                            endMillis = PakistanTime.fromDatePickerUtcMillis(e, endMillis)
                            useCustomRange = true
                        }
                        showRangeDialog = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showRangeDialog = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
fun StaffManagementScreen(
    session: ActiveWorkspaceSession,
    staffUsers: List<BusinessUser>,
    allPermissions: List<StaffPermission>,
    isOnline: Boolean,
    isSubmitting: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onCreateStaff: (String, String, String, String, StaffPermission, () -> Unit) -> Unit,
    onUpdatePermissions: (BusinessUser, StaffPermission, () -> Unit) -> Unit,
    onToggleStaffStatus: (BusinessUser) -> Unit,
    onResetStaffPassword: (BusinessUser, String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingUserPair by remember { mutableStateOf<Pair<BusinessUser, StaffPermission>?>(null) }
    var resetPasswordUser by remember { mutableStateOf<BusinessUser?>(null) }

    val staffOnly = remember(staffUsers) { staffUsers.filter { it.role == AccountRole.STAFF.key } }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Staff & Permissions",
                subtitle = "${staffOnly.size} authorized staff accounts",
                onBack = onBack
            )
        },
        floatingActionButton = {
            if (session.role == AccountRole.SUPER_ADMIN) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = HisabNavy,
                    contentColor = HisabWhite,
                    modifier = Modifier.testTag("add_staff_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Staff User")
                }
            }
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

            if (staffOnly.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.Default.Group,
                        title = "No Staff Users Created",
                        message = "Tap + to create an Authorized Staff account with granular 14-permission access control."
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(staffOnly, key = { it.id }) { user ->
                        val perm = allPermissions.firstOrNull { it.userId == user.id }
                            ?: StaffPermission(businessId = session.businessId, userId = user.id)
                        val isActive = user.status == "ACTIVE"

                        Card(
                            colors = CardDefaults.cardColors(containerColor = HisabWhite),
                            border = BorderStroke(1.dp, HisabBorder),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(user.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${user.userCode} • @${user.username} • ${user.mobileNumber}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = HisabSecondaryGray
                                        )
                                    }
                                    StatusPill(
                                        text = user.status,
                                        containerColor = if (isActive) HisabSuccessBg else HisabErrorBg,
                                        contentColor = if (isActive) HisabSuccessGreen else HisabErrorRed
                                    )
                                }
                                HorizontalDivider(color = HisabBorder)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { editingUserPair = user to perm },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Permissions")
                                    }
                                    OutlinedButton(
                                        onClick = { resetPasswordUser = user },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Set Pass")
                                    }
                                    OutlinedButton(
                                        onClick = { onToggleStaffStatus(user) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = if (isActive) "Suspend" else "Activate",
                                            color = if (isActive) HisabErrorRed else HisabSuccessGreen
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

    if (showCreateDialog) {
        StaffCreateOrPermissionsDialog(
            existingUser = null,
            initialPermission = StaffPermission(businessId = session.businessId),
            isSubmitting = isSubmitting,
            onDismiss = { showCreateDialog = false },
            onSaveNew = { name, user, pass, mobile, perm ->
                onCreateStaff(name, user, pass, mobile, perm) { showCreateDialog = false }
            },
            onSavePermissions = { _ -> }
        )
    }

    if (editingUserPair != null) {
        val (user, perm) = editingUserPair!!
        StaffCreateOrPermissionsDialog(
            existingUser = user,
            initialPermission = perm,
            isSubmitting = isSubmitting,
            onDismiss = { editingUserPair = null },
            onSaveNew = { _, _, _, _, _ -> },
            onSavePermissions = { updatedPerm ->
                onUpdatePermissions(user, updatedPerm) { editingUserPair = null }
            }
        )
    }

    if (resetPasswordUser != null) {
        val target = resetPasswordUser!!
        DirectPasswordResetDialog(
            accountLabel = "${target.fullName} (@${target.username})",
            isSubmitting = isSubmitting,
            onDismiss = { resetPasswordUser = null },
            onConfirm = { newPass ->
                onResetStaffPassword(target, newPass)
                resetPasswordUser = null
            }
        )
    }
}

@Composable
private fun DirectPasswordResetDialog(
    accountLabel: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var passwordInput by remember { mutableStateOf(PasswordHasher.generateTemporaryPassword()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HisabWhite,
        title = { Text("Set Direct Login Password", color = HisabNavy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter a new login password for $accountLabel. They can log in directly with this password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HisabSecondaryGray
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password (min 6 chars) *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("direct_reset_password_input")
                    )
                    IconButton(onClick = { passwordInput = PasswordHasher.generateTemporaryPassword() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Generate Random Password", tint = HisabNavy)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(passwordInput.trim()) },
                enabled = !isSubmitting && passwordInput.trim().length >= 6,
                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                modifier = Modifier.testTag("confirm_direct_reset_password")
            ) {
                Text("Save Password")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StaffCreateOrPermissionsDialog(
    existingUser: BusinessUser?,
    initialPermission: StaffPermission,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSaveNew: (String, String, String, String, StaffPermission) -> Unit,
    onSavePermissions: (StaffPermission) -> Unit
) {
    var fullName by remember { mutableStateOf(existingUser?.fullName.orEmpty()) }
    var username by remember { mutableStateOf(existingUser?.username.orEmpty()) }
    var tempPassword by remember { mutableStateOf(PasswordHasher.generateTemporaryPassword()) }
    var mobile by remember { mutableStateOf(existingUser?.mobileNumber.orEmpty()) }
    var perm by remember { mutableStateOf(initialPermission) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HisabWhite,
        title = {
            Text(
                text = if (existingUser == null) "Create Authorized Staff" else "Permissions • ${existingUser.fullName}",
                color = HisabNavy
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (existingUser == null) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("staff_fullname_input")
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("staff_username_input")
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = tempPassword,
                            onValueChange = { tempPassword = it },
                            label = { Text("Login Password (min 6 chars) *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("staff_password_input")
                        )
                        IconButton(onClick = { tempPassword = PasswordHasher.generateTemporaryPassword() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Regenerate Password", tint = HisabNavy)
                        }
                    }
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("Mobile Number *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = HisabBorder)
                }

                Text("14 Configurable Staff Permissions", style = MaterialTheme.typography.labelLarge, color = HisabNavy)
                PermissionToggleRow("View Dashboard", perm.viewDashboard) { perm = perm.copy(viewDashboard = it) }
                PermissionToggleRow("View Shops", perm.viewShops) { perm = perm.copy(viewShops = it) }
                PermissionToggleRow("Add Shops", perm.addShops) { perm = perm.copy(addShops = it) }
                PermissionToggleRow("Edit Shops", perm.editShops) { perm = perm.copy(editShops = it) }
                PermissionToggleRow("Add Credit", perm.addCredit) { perm = perm.copy(addCredit = it) }
                PermissionToggleRow("Reverse / Correct Credit", perm.editCredit) { perm = perm.copy(editCredit = it) }
                PermissionToggleRow("Record Recovery (Today's Date PKT Only)", perm.recordRecovery) { perm = perm.copy(recordRecovery = it) }
                if (perm.recordRecovery) {
                    Text(
                        text = "Same-day PKT enforcement: Staff with recovery permission can only enter payments for today's Pakistan calendar date. Previous/backdated recovery dates are disabled (Super Admin only).",
                        style = MaterialTheme.typography.labelSmall,
                        color = HisabNavy,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                PermissionToggleRow("View Recovery History", perm.viewRecoveryHistory) { perm = perm.copy(viewRecoveryHistory = it) }
                PermissionToggleRow("View Bookers", perm.viewBookers) { perm = perm.copy(viewBookers = it) }
                PermissionToggleRow("Manage Bookers", perm.manageBookers) { perm = perm.copy(manageBookers = it) }
                PermissionToggleRow("View Reports", perm.viewReports) { perm = perm.copy(viewReports = it) }
                PermissionToggleRow("Generate PDFs", perm.generatePdfs) { perm = perm.copy(generatePdfs = it) }
                PermissionToggleRow("Search Shops", perm.searchShops) { perm = perm.copy(searchShops = it) }
                PermissionToggleRow("View Activity Logs", perm.viewActivityLogs) { perm = perm.copy(viewActivityLogs = it) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (existingUser == null) {
                        onSaveNew(fullName, username, tempPassword, mobile, perm)
                    } else {
                        onSavePermissions(perm)
                    }
                },
                enabled = !isSubmitting && (existingUser != null || (fullName.isNotBlank() && username.isNotBlank() && tempPassword.length >= 6 && mobile.isNotBlank())),
                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                modifier = Modifier.testTag("save_staff_button")
            ) {
                Text(if (existingUser == null) "Create Staff" else "Save Permissions")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PermissionToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = HisabNavy)
        )
    }
}
