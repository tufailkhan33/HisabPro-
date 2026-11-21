package com.example.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.AccountRole
import com.example.domain.model.Booker
import com.example.domain.model.BusinessSettings
import com.example.domain.model.PaymentMethod
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.Shop
import com.example.domain.model.ShopBalanceSummary
import com.example.ui.components.ActionMessageBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HisabNavyTopBar
import com.example.ui.components.OfflineBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.HisabBlackText
import com.example.ui.theme.HisabBorder
import com.example.ui.theme.HisabErrorBg
import com.example.ui.theme.HisabErrorRed
import com.example.ui.theme.HisabKpiBlueBg
import com.example.ui.theme.HisabKpiBlueIcon
import com.example.ui.theme.HisabLightGray
import com.example.ui.theme.HisabNavy
import com.example.ui.theme.HisabNavyContainer
import com.example.ui.theme.HisabSecondaryGray
import com.example.ui.theme.HisabSoftBlueBtn
import com.example.ui.theme.HisabSuccessBg
import com.example.ui.theme.HisabSuccessGreen
import com.example.ui.theme.HisabWarningBg
import com.example.ui.theme.HisabWarningOrange
import com.example.ui.theme.HisabWhite
import com.example.util.CurrencyFormatter
import com.example.util.PakistanTime
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCreditScreen(
    preselectedShopId: String?,
    bookers: List<Booker>,
    shopBalances: List<ShopBalanceSummary>,
    settings: BusinessSettings?,
    isOnline: Boolean,
    isSubmitting: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onQuickCreateShop: (
        shopUid: String,
        shopName: String,
        booker: Booker,
        ownerName: String,
        mobile: String,
        address: String,
        city: String,
        openingCreditPaisa: Long,
        openingDateMillis: Long,
        notes: String,
        onCreated: (Shop) -> Unit
    ) -> Unit,
    onSaveCredit: (
        shop: Shop,
        amountPaisa: Long,
        txDateMillis: Long,
        invoiceNumber: String,
        description: String,
        attachmentUri: String,
        dueDateMillis: Long,
        onDone: () -> Unit
    ) -> Unit
) {
    val activeBookers = remember(bookers) { bookers.filter { it.status == "ACTIVE" } }
    val activeShops = remember(shopBalances) { shopBalances.filter { !it.shop.isArchived } }

    var selectedBooker by remember { mutableStateOf<Booker?>(null) }
    var selectedShopSummary by remember { mutableStateOf<ShopBalanceSummary?>(null) }
    var shopPickerExpanded by remember { mutableStateOf(preselectedShopId == null) }
    var shopSearchQuery by remember { mutableStateOf("") }
    var bookerMenuExpanded by remember { mutableStateOf(false) }

    var amountStr by remember { mutableStateOf("") }
    var txDateMillis by remember { mutableLongStateOf(PakistanTime.nowMillis()) }
    var dueDateMillis by remember { mutableLongStateOf(0L) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var dateValidationWarning by remember { mutableStateOf<String?>(null) }
    var invoiceNumber by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var attachmentUri by remember { mutableStateOf<Uri?>(null) }
    var showQuickAddShop by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            attachmentUri = uri
        }
    }

    LaunchedEffect(preselectedShopId, activeShops, activeBookers) {
        if (preselectedShopId != null && selectedShopSummary == null) {
            val found = activeShops.firstOrNull { it.shop.id == preselectedShopId }
            if (found != null) {
                selectedShopSummary = found
                selectedBooker = activeBookers.firstOrNull { it.id == found.shop.bookerId }
                shopPickerExpanded = false
            }
        }
    }

    val filteredShops = remember(activeShops, selectedBooker, shopSearchQuery) {
        val q = shopSearchQuery.trim().lowercase()
        activeShops.filter { sb ->
            val matchesBooker = selectedBooker == null || sb.shop.bookerId == selectedBooker!!.id
            val matchesQuery = q.isEmpty() ||
                sb.shop.shopName.lowercase().contains(q) ||
                sb.shop.shopUid.lowercase().contains(q) ||
                sb.shop.ownerName.lowercase().contains(q)
            matchesBooker && matchesQuery
        }
    }

    val requireInvoice = settings?.requireInvoiceNumber == true
    val parsedPaisa = CurrencyFormatter.parsePkrToPaisa(amountStr)
    val canSubmit = !isSubmitting &&
        selectedShopSummary != null &&
        parsedPaisa != null &&
        parsedPaisa > 0L &&
        (!requireInvoice || invoiceNumber.isNotBlank())

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Add Credit",
                onBack = onBack
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

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Panel 7 Top Shop Header Card (Tap to expand / switch shop or Booker)
                item {
                    Surface(
                        color = HisabKpiBlueBg,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, HisabBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { shopPickerExpanded = !shopPickerExpanded }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(HisabWhite),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = HisabNavy,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedShopSummary != null) {
                                        "Shop: ${selectedShopSummary!!.shop.shopName}"
                                    } else {
                                        "Select Credit Shop *"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = HisabBlackText,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (selectedShopSummary != null) {
                                        "UID: ${selectedShopSummary!!.shop.shopUid} • BK: ${selectedShopSummary!!.shop.bookerName}"
                                    } else {
                                        "Tap to choose Booker, Shop or Quick Add"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HisabSecondaryGray
                                )
                            }
                            Icon(
                                imageVector = if (shopPickerExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle Shop Selector",
                                tint = HisabNavy
                            )
                        }
                    }
                }

                // Expandable Booker + Shop Selector & Quick Add Shop
                if (shopPickerExpanded || selectedShopSummary == null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = HisabWhite),
                            border = BorderStroke(1.dp, HisabBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Pick Booker & Shop",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = HisabNavy,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(
                                        onClick = { showQuickAddShop = true },
                                        modifier = Modifier.testTag("quick_add_shop_button")
                                    ) {
                                        Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Quick Add Shop")
                                    }
                                }

                                ExposedDropdownMenuBox(
                                    expanded = bookerMenuExpanded,
                                    onExpandedChange = { bookerMenuExpanded = !bookerMenuExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = selectedBooker?.let { "${it.fullName} (${it.bookerCode})" }
                                            ?: "All Active Bookers",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Filter by Booker") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bookerMenuExpanded) },
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            .fillMaxWidth()
                                            .testTag("credit_booker_dropdown")
                                    )
                                    ExposedDropdownMenu(
                                        expanded = bookerMenuExpanded,
                                        onDismissRequest = { bookerMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("All Active Bookers") },
                                            onClick = {
                                                selectedBooker = null
                                                bookerMenuExpanded = false
                                            }
                                        )
                                        activeBookers.forEach { b ->
                                            DropdownMenuItem(
                                                text = { Text("${b.fullName} (${b.bookerCode})") },
                                                onClick = {
                                                    selectedBooker = b
                                                    if (selectedShopSummary?.shop?.bookerId != b.id) {
                                                        selectedShopSummary = null
                                                    }
                                                    bookerMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = shopSearchQuery,
                                    onValueChange = { shopSearchQuery = it },
                                    placeholder = { Text("Search shop name, UID, or owner") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (filteredShops.isEmpty()) {
                                    Text(
                                        text = "No matching active shops. Tap 'Quick Add Shop' above to register a new shop.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HisabSecondaryGray
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        filteredShops.take(8).forEach { sb ->
                                            val isSelected = selectedShopSummary?.shop?.id == sb.shop.id
                                            Surface(
                                                color = if (isSelected) HisabNavyContainer else HisabLightGray,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, if (isSelected) HisabNavy else HisabBorder),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        selectedShopSummary = sb
                                                        shopPickerExpanded = false
                                                    }
                                                    .testTag("select_credit_shop_${sb.shop.shopUid}")
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = sb.shop.shopName,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${sb.shop.shopUid} • ${sb.shop.bookerName}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = HisabSecondaryGray
                                                        )
                                                    }
                                                    Text(
                                                        text = CurrencyFormatter.formatPkr(sb.outstandingPaisa),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = if (sb.outstandingPaisa > 0) HisabErrorRed else HisabSuccessGreen,
                                                        fontWeight = FontWeight.Bold
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

                // Panel 7 Credit Fields Form
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Credit Amount *",
                            style = MaterialTheme.typography.labelLarge,
                            color = HisabBlackText,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it },
                            placeholder = { Text("50000") },
                            prefix = { Text("Rs. ", fontWeight = FontWeight.Bold, color = HisabNavy) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = HisabWhite,
                                unfocusedContainerColor = HisabWhite,
                                focusedBorderColor = HisabNavy,
                                unfocusedBorderColor = HisabBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("credit_amount_input")
                        )

                        Text(
                            text = "Credit Date",
                            style = MaterialTheme.typography.labelLarge,
                            color = HisabBlackText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showDatePicker = true }
                        ) {
                            OutlinedTextField(
                                value = "${PakistanTime.formatNumericDate(txDateMillis)} (${PakistanTime.formatTime(txDateMillis)} PKT)",
                                onValueChange = {},
                                readOnly = true,
                                enabled = false,
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = "Select Credit Date",
                                        tint = HisabNavy
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = HisabBlackText,
                                    disabledBorderColor = HisabBorder,
                                    disabledContainerColor = HisabWhite,
                                    disabledTrailingIconColor = HisabNavy
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (!dateValidationWarning.isNullOrBlank()) {
                            Text(
                                text = dateValidationWarning!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = HisabErrorRed
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showDueDatePicker = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("credit_due_date_button")
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (dueDateMillis > 0L) "Due: ${PakistanTime.formatDate(dueDateMillis)}" else "Due Date (Optional)"
                                )
                            }
                            if (dueDateMillis > 0L) {
                                TextButton(onClick = { dueDateMillis = 0L }) {
                                    Text("Clear", color = HisabErrorRed)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = invoiceNumber,
                            onValueChange = { invoiceNumber = it },
                            label = {
                                Text(if (requireInvoice) "Invoice Number *" else "Invoice Number (Optional)")
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("credit_invoice_input")
                        )

                        Text(
                            text = "Description",
                            style = MaterialTheme.typography.labelLarge,
                            color = HisabBlackText,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            placeholder = { Text("Monthly Stock") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("credit_description_input")
                        )

                        Text(
                            text = "Attachment (Optional)",
                            style = MaterialTheme.typography.labelLarge,
                            color = HisabBlackText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            color = HisabSoftBlueBtn,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HisabBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("attach_bill_photo_button")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (attachmentUri == null) "Choose File" else "Change Bill Photo",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = HisabBlackText,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(12.dp))
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Choose Bill Photo",
                                    tint = HisabNavy,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (attachmentUri != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AsyncImage(
                                    model = attachmentUri,
                                    contentDescription = "Attached Bill Image",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                TextButton(onClick = { attachmentUri = null }) {
                                    Text("Remove Attachment", color = HisabErrorRed)
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = {
                                val shop = selectedShopSummary?.shop ?: return@Button
                                val paisa = parsedPaisa ?: return@Button
                                onSaveCredit(
                                    shop,
                                    paisa,
                                    txDateMillis,
                                    invoiceNumber,
                                    description,
                                    attachmentUri?.toString().orEmpty(),
                                    dueDateMillis
                                ) {
                                    amountStr = ""
                                    invoiceNumber = ""
                                    description = ""
                                    attachmentUri = null
                                    dueDateMillis = 0L
                                }
                            },
                            enabled = canSubmit,
                            colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("save_credit_button")
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(color = HisabWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Save Credit", style = MaterialTheme.typography.titleMedium, color = HisabWhite)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = PakistanTime.toDatePickerUtcMillis(txDateMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { picked ->
                            val candidate = PakistanTime.fromDatePickerUtcMillis(picked, txDateMillis)
                            if (PakistanTime.isFuturePakistanDay(candidate)) {
                                dateValidationWarning = "Future credit dates are not allowed. Kept previous valid PKT date."
                            } else {
                                dateValidationWarning = null
                                txDateMillis = candidate
                            }
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDueDatePicker) {
        val duePickerState = rememberDatePickerState(
            initialSelectedDateMillis = PakistanTime.toDatePickerUtcMillis(if (dueDateMillis > 0L) dueDateMillis else txDateMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showDueDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        duePickerState.selectedDateMillis?.let { picked ->
                            dueDateMillis = PakistanTime.fromDatePickerUtcMillis(picked, txDateMillis)
                        }
                        showDueDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDueDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = duePickerState)
        }
    }

    if (showQuickAddShop) {
        AddOrEditShopDialog(
            title = "Quick Add Shop",
            existingShop = null,
            bookers = activeBookers,
            isSubmitting = isSubmitting,
            onDismiss = { showQuickAddShop = false },
            onSave = { uid, name, booker, owner, mobile, address, city, openingPaisa, notes ->
                onQuickCreateShop(
                    uid, name, booker, owner, mobile, address, city,
                    openingPaisa, PakistanTime.nowMillis(), notes
                ) { created ->
                    showQuickAddShop = false
                    selectedBooker = booker
                    selectedShopSummary = ShopBalanceSummary(
                        shop = created,
                        totalCreditPaisa = openingPaisa,
                        totalRecoveredPaisa = 0L,
                        outstandingPaisa = openingPaisa,
                        firstCreditDateMillis = if (openingPaisa > 0L) PakistanTime.nowMillis() else null,
                        lastTransactionDateMillis = if (openingPaisa > 0L) PakistanTime.nowMillis() else null,
                        creditCount = if (openingPaisa > 0L) 1 else 0,
                        recoveryCount = 0
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecordRecoveryScreen(
    preselectedShopId: String?,
    businessName: String = "HisabPro",
    userRole: AccountRole = AccountRole.SUPER_ADMIN,
    bookers: List<Booker>,
    shopBalances: List<ShopBalanceSummary>,
    settings: BusinessSettings?,
    latestReceipt: Pair<RecoveryTransaction, Long>?,
    isOnline: Boolean,
    isSubmitting: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onDismissReceipt: () -> Unit,
    onBack: (() -> Unit)? = null,
    onSaveRecovery: (
        shop: Shop,
        amountPaisa: Long,
        txDateMillis: Long,
        paymentMethod: PaymentMethod,
        referenceNumber: String,
        notes: String,
        clientRequestToken: String,
        onDone: () -> Unit
    ) -> Unit,
    onDownloadPdf: (Context, Uri, String) -> Unit = { ctx, uri, name ->
        com.example.util.PdfGenerator.downloadPdfToDevice(ctx, uri, name)
    }
) {
    val context = LocalContext.current
    val isStaffRole = userRole == AccountRole.STAFF
    val activeBookers = remember(bookers) { bookers.filter { it.status == "ACTIVE" } }
    val activeShops = remember(shopBalances) { shopBalances.filter { !it.shop.isArchived } }

    var selectedBooker by remember { mutableStateOf<Booker?>(null) }
    var selectedShopSummary by remember { mutableStateOf<ShopBalanceSummary?>(null) }
    var shopSearchQuery by remember { mutableStateOf("") }
    var bookerMenuExpanded by remember { mutableStateOf(false) }

    var amountStr by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var txDateMillis by remember { mutableLongStateOf(PakistanTime.nowMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var dateWarning by remember { mutableStateOf<String?>(null) }
    var referenceNumber by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var requestToken by remember { mutableStateOf(UUID.randomUUID().toString()) }

    LaunchedEffect(preselectedShopId, activeShops, activeBookers) {
        if (preselectedShopId != null && selectedShopSummary == null) {
            val found = activeShops.firstOrNull { it.shop.id == preselectedShopId }
            if (found != null) {
                selectedShopSummary = found
                selectedBooker = activeBookers.firstOrNull { it.id == found.shop.bookerId }
            }
        }
    }

    // Keep selectedShopSummary balance synced with live shopBalances stream
    val liveSelectedSummary = remember(selectedShopSummary?.shop?.id, activeShops) {
        selectedShopSummary?.let { sel -> activeShops.firstOrNull { it.shop.id == sel.shop.id } ?: sel }
    }

    val filteredShops = remember(activeShops, selectedBooker, shopSearchQuery) {
        val q = shopSearchQuery.trim().lowercase()
        activeShops.filter { sb ->
            val matchesBooker = selectedBooker == null || sb.shop.bookerId == selectedBooker!!.id
            val matchesQuery = q.isEmpty() ||
                sb.shop.shopName.lowercase().contains(q) ||
                sb.shop.shopUid.lowercase().contains(q) ||
                sb.shop.ownerName.lowercase().contains(q)
            matchesBooker && matchesQuery
        }.sortedByDescending { it.outstandingPaisa }
    }

    val allowOver = settings?.allowOverRecovery == true
    val parsedPaisa = CurrencyFormatter.parsePkrToPaisa(amountStr)
    val currentOutstanding = liveSelectedSummary?.outstandingPaisa ?: 0L
    val exceedsOutstanding = parsedPaisa != null && liveSelectedSummary != null && !allowOver && parsedPaisa > currentOutstanding

    val canSubmit = !isSubmitting &&
        liveSelectedSummary != null &&
        parsedPaisa != null &&
        parsedPaisa > 0L &&
        !exceedsOutstanding

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "Record Recovery",
                subtitle = "Collect Payment & Generate Receipt",
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
                // Step 1: Booker & Shop Selector
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
                            Text(
                                text = "Step 1 • Select Booker & Shop",
                                style = MaterialTheme.typography.titleMedium,
                                color = HisabNavy,
                                fontWeight = FontWeight.Bold
                            )

                            ExposedDropdownMenuBox(
                                expanded = bookerMenuExpanded,
                                onExpandedChange = { bookerMenuExpanded = !bookerMenuExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedBooker?.let { "${it.fullName} (${it.bookerCode})" }
                                        ?: "All Active Bookers",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Booker Filter") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bookerMenuExpanded) },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = bookerMenuExpanded,
                                    onDismissRequest = { bookerMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Active Bookers") },
                                        onClick = {
                                            selectedBooker = null
                                            bookerMenuExpanded = false
                                        }
                                    )
                                    activeBookers.forEach { b ->
                                        DropdownMenuItem(
                                            text = { Text("${b.fullName} (${b.bookerCode})") },
                                            onClick = {
                                                selectedBooker = b
                                                if (liveSelectedSummary?.shop?.bookerId != b.id) {
                                                    selectedShopSummary = null
                                                }
                                                bookerMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = shopSearchQuery,
                                onValueChange = { shopSearchQuery = it },
                                placeholder = { Text("Search shop name, UID, or owner") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (liveSelectedSummary != null) {
                                Surface(
                                    color = HisabSuccessBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, HisabSuccessGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = liveSelectedSummary.shop.shopName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = HisabNavy,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${liveSelectedSummary.shop.shopUid} • Booker: ${liveSelectedSummary.shop.bookerName}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = HisabSecondaryGray
                                                )
                                            }
                                            IconButton(onClick = { selectedShopSummary = null }) {
                                                Icon(Icons.Default.Close, contentDescription = "Change Shop", tint = HisabNavy)
                                            }
                                        }
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = HisabBorder)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("Total Credit", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                                                Text(CurrencyFormatter.formatPkr(liveSelectedSummary.totalCreditPaisa), fontWeight = FontWeight.Bold, color = HisabNavy)
                                            }
                                            Column {
                                                Text("Recovered", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                                                Text(CurrencyFormatter.formatPkr(liveSelectedSummary.totalRecoveredPaisa), fontWeight = FontWeight.Bold, color = HisabSuccessGreen)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Remaining Udhaar", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                                                Text(
                                                    text = CurrencyFormatter.formatPkr(liveSelectedSummary.outstandingPaisa),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (liveSelectedSummary.outstandingPaisa > 0) HisabErrorRed else HisabSuccessGreen
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (filteredShops.isEmpty()) {
                                EmptyStateCard(
                                    icon = Icons.Default.Payments,
                                    title = "No Shops Found",
                                    message = "Clear your filter or add a shop first before recording recovery."
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    filteredShops.take(8).forEach { sb ->
                                        Surface(
                                            color = HisabLightGray,
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, HisabBorder),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { selectedShopSummary = sb }
                                                .testTag("select_recovery_shop_${sb.shop.shopUid}")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(sb.shop.shopName, fontWeight = FontWeight.SemiBold)
                                                    Text(
                                                        text = "${sb.shop.shopUid} • Booker: ${sb.shop.bookerName}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = HisabSecondaryGray
                                                    )
                                                }
                                                StatusPill(
                                                    text = CurrencyFormatter.formatPkr(sb.outstandingPaisa),
                                                    containerColor = if (sb.outstandingPaisa > 0) HisabErrorBg else HisabSuccessBg,
                                                    contentColor = if (sb.outstandingPaisa > 0) HisabErrorRed else HisabSuccessGreen
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 2: Recovery Form
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
                            Text(
                                text = "Step 2 • Recovery Collection Details",
                                style = MaterialTheme.typography.titleMedium,
                                color = HisabNavy,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = amountStr,
                                onValueChange = { amountStr = it },
                                label = { Text("Recovery Amount (PKR) *") },
                                prefix = { Text("Rs. ", fontWeight = FontWeight.Bold, color = HisabSuccessGreen) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                isError = exceedsOutstanding,
                                supportingText = if (exceedsOutstanding) {
                                    {
                                        Text(
                                            "Amount exceeds remaining balance (${CurrencyFormatter.formatPkr(currentOutstanding)}). Enable Over-Recovery in Settings if intended.",
                                            color = HisabErrorRed
                                        )
                                    }
                                } else null,
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("recovery_amount_input")
                            )

                            if (liveSelectedSummary != null && liveSelectedSummary.outstandingPaisa > 0L) {
                                OutlinedButton(
                                    onClick = {
                                        amountStr = CurrencyFormatter.paisaToBigDecimal(liveSelectedSummary.outstandingPaisa).stripTrailingZeros().toPlainString()
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Fill Full Outstanding (${CurrencyFormatter.formatPkr(liveSelectedSummary.outstandingPaisa)})")
                                }
                            }

                            Text("Payment Method", style = MaterialTheme.typography.labelLarge, color = HisabNavy)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PaymentMethod.entries.forEach { method ->
                                    FilterChip(
                                        selected = selectedMethod == method,
                                        onClick = { selectedMethod = method },
                                        label = { Text(method.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = HisabNavy,
                                            selectedLabelColor = HisabWhite
                                        ),
                                        modifier = Modifier.testTag("payment_method_${method.name}")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val effectiveRecoveryMillis = if (isStaffRole) PakistanTime.nowMillis() else txDateMillis
                                OutlinedButton(
                                    onClick = { if (!isStaffRole) showDatePicker = true },
                                    enabled = !isStaffRole,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("recovery_date_button")
                                ) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Date: ${PakistanTime.formatDate(effectiveRecoveryMillis)}")
                                }
                                Surface(
                                    color = HisabLightGray,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, HisabBorder)
                                ) {
                                    Text(
                                        text = "${PakistanTime.formatTime(effectiveRecoveryMillis)} PKT",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = HisabNavy,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            }
                            if (isStaffRole) {
                                Surface(
                                    color = HisabWarningBg,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, HisabWarningOrange.copy(alpha = 0.45f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Same-Date Policy: Staff recovery entries are locked to today's Pakistan calendar date (${PakistanTime.formatDate()}). Previous dates are disabled (Admin only).",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HisabWarningOrange,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                }
                            } else if (!dateWarning.isNullOrBlank()) {
                                Text(
                                    text = dateWarning!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HisabErrorRed
                                )
                            }

                            OutlinedTextField(
                                value = referenceNumber,
                                onValueChange = { referenceNumber = it },
                                label = { Text("Reference / Transaction ID (Optional)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("recovery_reference_input")
                            )

                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Notes / Collected By Remarks") },
                                minLines = 2,
                                maxLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("recovery_notes_input")
                            )

                            Button(
                                onClick = {
                                    val shop = liveSelectedSummary?.shop ?: return@Button
                                    val paisa = parsedPaisa ?: return@Button
                                    val effectiveMillis = if (isStaffRole) PakistanTime.nowMillis() else txDateMillis
                                    onSaveRecovery(
                                        shop,
                                        paisa,
                                        effectiveMillis,
                                        selectedMethod,
                                        referenceNumber,
                                        notes,
                                        requestToken
                                    ) {
                                        amountStr = ""
                                        referenceNumber = ""
                                        notes = ""
                                        requestToken = UUID.randomUUID().toString()
                                    }
                                },
                                enabled = canSubmit,
                                colors = ButtonDefaults.buttonColors(containerColor = HisabSuccessGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("save_recovery_button")
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(color = HisabWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Confirm Recovery Payment", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker && !isStaffRole) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = PakistanTime.toDatePickerUtcMillis(txDateMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { picked ->
                            val candidate = PakistanTime.fromDatePickerUtcMillis(picked, txDateMillis)
                            if (PakistanTime.isFuturePakistanDay(candidate)) {
                                dateWarning = "Future recovery dates are prohibited by financial rules."
                            } else {
                                dateWarning = null
                                txDateMillis = candidate
                            }
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (latestReceipt != null) {
        val (tx, remainingPaisa) = latestReceipt
        val receiptBusinessName = settings?.businessName?.takeIf { it.isNotBlank() } ?: businessName.ifBlank { "HisabPro" }
        RecoveryReceiptDialog(
            tx = tx,
            remainingPaisa = remainingPaisa,
            onViewPdf = {
                val uri = com.example.util.PdfGenerator.generateRecoveryReceiptPdf(
                    context = context,
                    businessName = receiptBusinessName,
                    tx = tx,
                    remainingOutstandingPaisa = remainingPaisa
                )
                com.example.util.PdfGenerator.viewPdf(context, uri)
            },
            onSharePdf = {
                val uri = com.example.util.PdfGenerator.generateRecoveryReceiptPdf(
                    context = context,
                    businessName = receiptBusinessName,
                    tx = tx,
                    remainingOutstandingPaisa = remainingPaisa
                )
                com.example.util.PdfGenerator.sharePdf(context, uri, "Payment Receipt ${tx.txCode}")
            },
            onDownloadPdf = {
                val uri = com.example.util.PdfGenerator.generateRecoveryReceiptPdf(
                    context = context,
                    businessName = receiptBusinessName,
                    tx = tx,
                    remainingOutstandingPaisa = remainingPaisa
                )
                onDownloadPdf(context, uri, "HisabPro_Receipt_${tx.txCode}.pdf")
            },
            onDismiss = onDismissReceipt
        )
    }
}

@Composable
fun RecoveryReceiptDialog(
    tx: RecoveryTransaction,
    remainingPaisa: Long,
    onViewPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onDownloadPdf: () -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HisabWhite,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = HisabSuccessGreen)
                Spacer(Modifier.width(8.dp))
                Text("Payment Receipt Ready", color = HisabNavy)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = HisabSuccessBg,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Amount Recovered", style = MaterialTheme.typography.labelMedium, color = HisabSecondaryGray)
                        Text(
                            text = CurrencyFormatter.formatPkr(tx.amountPaisa),
                            style = MaterialTheme.typography.headlineSmall,
                            color = HisabSuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text("Receipt: ${tx.txCode}", style = MaterialTheme.typography.labelSmall, color = HisabNavy)
                    }
                }
                Text("Shop: ${tx.shopName} (${tx.shopUid})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Method: ${tx.paymentMethod}", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                Text("Date: ${tx.txDateFormatted} • ${tx.txTimeFormatted} PKT", style = MaterialTheme.typography.bodySmall, color = HisabSecondaryGray)
                Text(
                    text = "Remaining Balance: ${CurrencyFormatter.formatPkr(remainingPaisa)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (remainingPaisa > 0) HisabErrorRed else HisabSuccessGreen,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onViewPdf, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("View")
                    }
                    OutlinedButton(onClick = onDownloadPdf, modifier = Modifier.weight(1f).testTag("download_receipt_pdf_button")) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Download")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSharePdf,
                colors = ButtonDefaults.buttonColors(containerColor = HisabNavy)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
