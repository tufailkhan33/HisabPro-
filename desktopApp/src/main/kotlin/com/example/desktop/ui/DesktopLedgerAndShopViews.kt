package com.example.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.desktop.state.DesktopAppState
import com.example.desktop.state.DesktopUiState
import com.example.shared.engine.SharedFinancialEngine
import com.example.shared.model.*
import com.example.shared.util.SharedCurrencyFormatter
import com.example.shared.util.SharedPakistanTime

@Composable
fun DesktopShopsAndLedgerView(
    state: DesktopUiState,
    controller: DesktopAppState
) {
    val selectedShop = state.selectedShopForLedger
    if (selectedShop != null) {
        DesktopSupplierLedgerDetail(shop = selectedShop, state = state, controller = controller)
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ShopFilterOption.ALL) }
    var showAddShopDialog by remember { mutableStateOf(false) }

    val filtered = remember(state.shopBalances, searchQuery, filter) {
        state.shopBalances
            .filter { sb ->
                val matchesSearch = searchQuery.isBlank() ||
                    sb.shop.shopName.contains(searchQuery, true) ||
                    sb.shop.shopUid.contains(searchQuery, true) ||
                    sb.shop.ownerName.contains(searchQuery, true) ||
                    sb.shop.bookerName.contains(searchQuery, true)
                val matchesFilter = when (filter) {
                    ShopFilterOption.ALL -> !sb.shop.isArchived
                    ShopFilterOption.OUTSTANDING -> !sb.shop.isArchived && sb.outstandingPaisa > 0L
                    ShopFilterOption.FULLY_RECOVERED -> !sb.shop.isArchived && sb.outstandingPaisa <= 0L
                    ShopFilterOption.ARCHIVED -> sb.shop.isArchived
                    else -> !sb.shop.isArchived
                }
                matchesSearch && matchesFilter
            }
            .let { list ->
                when (filter) {
                    ShopFilterOption.HIGHEST_OUTSTANDING -> list.sortedByDescending { it.outstandingPaisa }
                    ShopFilterOption.LOWEST_OUTSTANDING -> list.sortedBy { it.outstandingPaisa }
                    ShopFilterOption.RECENTLY_ADDED -> list.sortedByDescending { it.shop.openingDateMillis }
                    else -> list.sortedByDescending { it.outstandingPaisa }
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Shops & Supplier Ledgers", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
                Text("Select any shop to view full Supplier Ledger, print A4 statements, or export Excel (.xlsx).", fontSize = 13.sp, color = DesktopColors.SecondaryGray)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { controller.exportAllShopsExcel() }) {
                    Icon(Icons.Default.TableView, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export All Excel")
                }
                if (state.permission?.addShops == true || state.activeSession?.role == AccountRole.SUPER_ADMIN) {
                    Button(
                        onClick = { showAddShopDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Navy)
                    ) {
                        Icon(Icons.Default.AddBusiness, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Shop")
                    }
                }
            }
        }

        DesktopCard(contentPadding = PaddingValues(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search shop name, ID, owner, or booker...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.width(340.dp),
                    singleLine = true
                )
                ShopFilterOption.entries.forEach { opt ->
                    FilterChip(
                        selected = filter == opt,
                        onClick = { filter = opt },
                        label = { Text(opt.label) }
                    )
                }
            }
        }

        DesktopCard(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            // Table Header
            Row(
                modifier = Modifier.fillMaxWidth().background(DesktopColors.NavyContainer).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Shop ID", modifier = Modifier.width(105.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DesktopColors.Navy)
                Text("Shop & Owner", modifier = Modifier.weight(1.6f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DesktopColors.Navy)
                Text("Assigned Booker", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DesktopColors.Navy)
                Text("Total Credit", modifier = Modifier.width(120.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DesktopColors.Navy, textAlign = TextAlign.End)
                Text("Recovered", modifier = Modifier.width(120.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DesktopColors.Navy, textAlign = TextAlign.End)
                Text("Outstanding", modifier = Modifier.width(130.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DesktopColors.Navy, textAlign = TextAlign.End)
                Text("Action", modifier = Modifier.width(135.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DesktopColors.Navy, textAlign = TextAlign.Center)
            }
            HorizontalDivider(color = DesktopColors.Border)

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No shops match the selected filter.", color = DesktopColors.SecondaryGray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.shop.id }) { sb ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { controller.openShopLedger(sb.shop) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(sb.shop.shopUid, modifier = Modifier.width(105.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DesktopColors.Navy)
                            Column(modifier = Modifier.weight(1.6f)) {
                                Text(sb.shop.shopName, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("${sb.shop.ownerName} • ${sb.shop.mobileNumber} • ${sb.shop.city}", fontSize = 11.sp, color = DesktopColors.SecondaryGray)
                            }
                            Text(sb.shop.bookerName, modifier = Modifier.weight(1f), fontSize = 12.sp)
                            Text(SharedCurrencyFormatter.formatPkr(sb.totalCreditPaisa), modifier = Modifier.width(120.dp), fontSize = 12.sp, textAlign = TextAlign.End)
                            Text(SharedCurrencyFormatter.formatPkr(sb.totalRecoveredPaisa), modifier = Modifier.width(120.dp), fontSize = 12.sp, color = DesktopColors.SuccessGreen, textAlign = TextAlign.End)
                            Text(
                                SharedCurrencyFormatter.formatPkr(sb.outstandingPaisa),
                                modifier = Modifier.width(130.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sb.outstandingPaisa > 0) DesktopColors.ErrorRed else DesktopColors.SuccessGreen,
                                textAlign = TextAlign.End
                            )
                            Box(modifier = Modifier.width(135.dp), contentAlignment = Alignment.Center) {
                                OutlinedButton(onClick = { controller.openShopLedger(sb.shop) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                    Text("Open Ledger", fontSize = 12.sp)
                                }
                            }
                        }
                        HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }

    if (showAddShopDialog) {
        AddShopModalDialog(
            bookers = state.bookers,
            onDismiss = { showAddShopDialog = false },
            onSave = { name, owner, mobile, address, city, booker, opening, notes ->
                controller.addShop(name, owner, mobile, address, city, booker, opening, notes)
                showAddShopDialog = false
            }
        )
    }
}

@Composable
private fun DesktopSupplierLedgerDetail(
    shop: Shop,
    state: DesktopUiState,
    controller: DesktopAppState
) {
    var preset by remember { mutableStateOf(DateFilterPreset.ALL_TIME) }
    var customStartStr by remember { mutableStateOf(SharedPakistanTime.formatDashDate()) }
    var customEndStr by remember { mutableStateOf(SharedPakistanTime.formatDashDate()) }

    val (startMillis, endMillis) = remember(preset, customStartStr, customEndStr) {
        when (preset) {
            DateFilterPreset.ALL_TIME -> 0L to Long.MAX_VALUE
            DateFilterPreset.TODAY -> SharedPakistanTime.todayRange()
            DateFilterPreset.YESTERDAY -> SharedPakistanTime.yesterdayRange()
            DateFilterPreset.LAST_7_DAYS -> SharedPakistanTime.lastDaysRange(7)
            DateFilterPreset.LAST_30_DAYS -> SharedPakistanTime.lastDaysRange(30)
            DateFilterPreset.THIS_MONTH -> SharedPakistanTime.thisMonthRange()
            DateFilterPreset.PREVIOUS_MONTH -> SharedPakistanTime.previousMonthRange()
            DateFilterPreset.CUSTOM -> {
                val s = SharedPakistanTime.parseDashDate(customStartStr)?.let { SharedPakistanTime.startOfDay(it) } ?: 0L
                val e = SharedPakistanTime.parseDashDate(customEndStr)?.let { SharedPakistanTime.endOfDay(it) } ?: Long.MAX_VALUE
                s to e
            }
        }
    }

    val report = remember(shop, state.credits, state.recoveries, startMillis, endMillis) {
        SharedFinancialEngine.computeShopLedgerReport(shop, state.credits, state.recoveries, startMillis, endMillis)
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header Action Bar
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { controller.openShopLedger(null) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Back to Shops")
                }
                Column {
                    Text("SUPPLIER LEDGER — ${shop.shopName}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
                    Text("ID: ${shop.shopUid} | Owner: ${shop.ownerName} | Ph: ${shop.mobileNumber} | Booker: ${shop.bookerName}", fontSize = 12.sp, color = DesktopColors.SecondaryGray)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { controller.exportShopLedgerCsv(shop, startMillis, endMillis) }) {
                    Text("CSV")
                }
                OutlinedButton(onClick = { controller.exportShopLedgerExcel(shop, startMillis, endMillis) }) {
                    Icon(Icons.Default.TableView, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Excel (.xlsx)")
                }
                OutlinedButton(onClick = { controller.exportShopLedgerPdf(shop, startMillis, endMillis, printNow = false) }) {
                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export A4 PDF")
                }
                Button(
                    onClick = { controller.exportShopLedgerPdf(shop, startMillis, endMillis, printNow = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Navy)
                ) {
                    Icon(Icons.Default.Print, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Print Ledger")
                }
            }
        }

        // Date Filter Strip
        DesktopCard(contentPadding = PaddingValues(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DateFilterPreset.entries.forEach { opt ->
                    FilterChip(selected = preset == opt, onClick = { preset = opt }, label = { Text(opt.label, fontSize = 12.sp) })
                }
                if (preset == DateFilterPreset.CUSTOM) {
                    OutlinedTextField(
                        value = customStartStr,
                        onValueChange = { customStartStr = it },
                        label = { Text("From dd-MM-yyyy") },
                        modifier = Modifier.width(150.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customEndStr,
                        onValueChange = { customEndStr = it },
                        label = { Text("To dd-MM-yyyy") },
                        modifier = Modifier.width(150.dp),
                        singleLine = true
                    )
                }
            }
        }

        // Exact 6-Column Bordered Supplier Ledger Table (matching attached picture reference)
        DesktopCard(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
            Text(
                text = "SUPPLIER LEDGER",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DesktopColors.SecondaryGray
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text((state.business?.businessName ?: state.activeSession?.businessName ?: "HISABPRO").uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Ph. ${state.settings?.contactPhone.orEmpty().ifBlank { "-" }}", fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(shop.shopName.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "${if (startMillis <= 0L) "ALL" else SharedPakistanTime.formatDashDate(startMillis)} TO ${if (endMillis > 9_000_000_000_000L) SharedPakistanTime.formatDashDate() else SharedPakistanTime.formatDashDate(endMillis)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            // 6-Column Header
            Row(
                modifier = Modifier.fillMaxWidth().border(1.dp, DesktopColors.BlackText).background(DesktopColors.NavyContainer)
            ) {
                LedgerCell("Voucher #", Modifier.width(95.dp), bold = true)
                LedgerCell("Dated", Modifier.width(95.dp), bold = true)
                LedgerCell("Description", Modifier.weight(1f), bold = true)
                LedgerCell("Purchase", Modifier.width(110.dp), bold = true, alignEnd = true)
                LedgerCell("Payment / Recovery", Modifier.width(130.dp), bold = true, alignEnd = true)
                LedgerCell("Balance", Modifier.width(115.dp), bold = true, alignEnd = true)
            }

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().border(0.5.dp, DesktopColors.Border)) {
                        LedgerCell("0", Modifier.width(95.dp))
                        LedgerCell(if (startMillis <= 0L) "01-01-0001" else SharedPakistanTime.formatDashDate(startMillis), Modifier.width(95.dp))
                        LedgerCell("Opening Balance", Modifier.weight(1f))
                        LedgerCell("0.0", Modifier.width(110.dp), alignEnd = true)
                        LedgerCell("0.0", Modifier.width(130.dp), alignEnd = true)
                        LedgerCell(SharedCurrencyFormatter.formatLedgerAmount(report.openingBalancePaisa), Modifier.width(115.dp), bold = true, alignEnd = true)
                    }
                }
                items(report.entries, key = { it.id }) { e ->
                    val rowColor = if (e.isReversal) DesktopColors.ErrorRed else DesktopColors.BlackText
                    val rowBg = if (e.isReversal) DesktopColors.ErrorBg.copy(alpha = 0.45f) else Color.White
                    val purchaseTxt = when {
                        e.creditPaisa > 0L -> SharedCurrencyFormatter.formatLedgerAmount(e.creditPaisa)
                        e.creditPaisa < 0L -> "-${SharedCurrencyFormatter.formatLedgerAmount(-e.creditPaisa)}"
                        else -> "0.0"
                    }
                    val recTxt = when {
                        e.recoveryPaisa > 0L -> SharedCurrencyFormatter.formatLedgerAmount(e.recoveryPaisa)
                        e.recoveryPaisa < 0L -> "-${SharedCurrencyFormatter.formatLedgerAmount(-e.recoveryPaisa)}"
                        else -> "0.0"
                    }
                    Row(modifier = Modifier.fillMaxWidth().background(rowBg).border(0.5.dp, DesktopColors.Border)) {
                        LedgerCell(e.voucherNumber, Modifier.width(95.dp), color = rowColor)
                        LedgerCell(e.txDateFormatted, Modifier.width(95.dp), color = rowColor)
                        LedgerCell(
                            buildString {
                                append(e.description)
                                if (e.isReversal) append(" [REVERSED${if (e.reason.isNotBlank()) ": ${e.reason}" else ""}]")
                                if (e.syncStatus != SyncStatus.SYNCED.key) append(" • ${SyncStatus.fromKey(e.syncStatus).label}")
                            },
                            Modifier.weight(1f),
                            color = rowColor,
                            bold = e.isReversal
                        )
                        LedgerCell(purchaseTxt, Modifier.width(110.dp), color = rowColor, alignEnd = true)
                        LedgerCell(recTxt, Modifier.width(130.dp), color = rowColor, alignEnd = true)
                        LedgerCell(SharedCurrencyFormatter.formatLedgerAmount(e.runningBalancePaisa), Modifier.width(115.dp), bold = true, color = DesktopColors.Navy, alignEnd = true)
                    }
                }
            }

            // GRAND TOTAL Row
            Row(
                modifier = Modifier.fillMaxWidth().border(1.dp, DesktopColors.BlackText).background(DesktopColors.NavyContainer)
            ) {
                LedgerCell("GRAND TOTAL", Modifier.weight(1f), bold = true, color = DesktopColors.Navy)
                LedgerCell(SharedCurrencyFormatter.formatLedgerAmount(report.totalCreditPaisa), Modifier.width(110.dp), bold = true, alignEnd = true)
                LedgerCell(SharedCurrencyFormatter.formatLedgerAmount(report.totalRecoveredPaisa), Modifier.width(130.dp), bold = true, color = DesktopColors.SuccessGreen, alignEnd = true)
                LedgerCell(SharedCurrencyFormatter.formatLedgerAmount(report.closingBalancePaisa), Modifier.width(115.dp), bold = true, color = DesktopColors.Navy, alignEnd = true)
            }
        }
    }
}

@Composable
private fun LedgerCell(
    text: String,
    modifier: Modifier,
    bold: Boolean = false,
    color: Color = DesktopColors.BlackText,
    alignEnd: Boolean = false
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 7.dp),
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
    )
}

@Composable
fun DesktopCreditAndRecoveryView(
    state: DesktopUiState,
    controller: DesktopAppState
) {
    var tab by remember { mutableStateOf(0) } // 0 = Add Credit, 1 = Add Recovery, 2 = Recovery History & Reversals
    var selectedShopId by remember(state.shops) { mutableStateOf(state.shops.firstOrNull()?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }
    var dateInput by remember { mutableStateOf(SharedPakistanTime.formatDashDate()) }
    var invoiceOrRef by remember { mutableStateOf("") }
    var notesOrDesc by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var reverseTarget by remember { mutableStateOf<RecoveryTransaction?>(null) }
    var reverseReason by remember { mutableStateOf("") }

    val selectedShop = state.shops.firstOrNull { it.id == selectedShopId } ?: state.shops.firstOrNull()
    val role = state.activeSession?.role ?: AccountRole.BOOKER
    val isAdmin = role == AccountRole.SUPER_ADMIN
    val canAddCredit = isAdmin || state.permission?.addCredit == true
    val canRecordRecovery = isAdmin || state.permission?.recordRecovery == true

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Credit & Recovery Transactions", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Add Credit / Purchase") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Record Payment / Recovery") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Recent Recoveries & Reversals") })
        }

        if (tab in 0..1) {
            DesktopCard(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                if (state.shops.isEmpty()) {
                    Text("No shops found. Add a shop first before recording credit or recovery.", color = DesktopColors.ErrorRed)
                } else {
                    Text("Select Shop", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.shopBalances.filter { !it.shop.isArchived }.forEach { sb ->
                            FilterChip(
                                selected = sb.shop.id == selectedShop?.id,
                                onClick = { selectedShopId = sb.shop.id },
                                label = { Text("${sb.shop.shopName} (${SharedCurrencyFormatter.formatPkr(sb.outstandingPaisa)})") }
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text("Amount (PKR)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = dateInput,
                            onValueChange = { if (tab == 0 || isAdmin) dateInput = it },
                            enabled = tab == 0 || isAdmin,
                            label = { Text(if (tab == 1 && !isAdmin) "Recovery Date (PKT Today Locked)" else "Transaction Date (dd-MM-yyyy)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    if (tab == 1 && !isAdmin) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Authorized Staff policy: recoveries can only be logged on today's PKT date (${SharedPakistanTime.formatDate()}). Previous-date entries require Super Admin.",
                            fontSize = 11.sp,
                            color = DesktopColors.WarningOrange
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (tab == 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaymentMethod.entries.forEach { m ->
                                FilterChip(selected = paymentMethod == m, onClick = { paymentMethod = m }, label = { Text(m.label) })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = invoiceOrRef,
                            onValueChange = { invoiceOrRef = it },
                            label = { Text(if (tab == 0) "Invoice Number" else "Slip / Reference Number") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = notesOrDesc,
                            onValueChange = { notesOrDesc = it },
                            label = { Text("Description / Comments") },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    val allowed = if (tab == 0) canAddCredit else canRecordRecovery
                    Button(
                        enabled = allowed && selectedShop != null && !state.isBusy,
                        onClick = {
                            val shop = selectedShop ?: return@Button
                            val parsedDate = SharedPakistanTime.parseDashDate(dateInput) ?: SharedPakistanTime.nowMillis()
                            if (tab == 0) {
                                controller.addCredit(shop, amount, parsedDate, 0L, invoiceOrRef, notesOrDesc)
                            } else {
                                val effectiveDate = if (isAdmin) parsedDate else SharedPakistanTime.nowMillis()
                                controller.addRecovery(shop, amount, effectiveDate, paymentMethod, invoiceOrRef, notesOrDesc)
                            }
                            amount = ""; invoiceOrRef = ""; notesOrDesc = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (tab == 0) DesktopColors.Navy else DesktopColors.SuccessGreen)
                    ) {
                        Text(
                            when {
                                !allowed -> "Permission Required from Business Admin"
                                tab == 0 -> "Save Credit Invoice"
                                else -> "Save Payment / Recovery"
                            }
                        )
                    }
                }
            }
        } else {
            DesktopCard(modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                val revMap = remember(state.recoveries) { SharedFinancialEngine.reversedRecoveryMap(state.recoveries) }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.recoveries.take(150), key = { it.id }) { rec ->
                        val isRev = rec.transactionKind == "RECOVERY_REVERSAL"
                        val alreadyReversed = !isRev && revMap.containsKey(rec.id)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${rec.txCode} • ${rec.shopName} (${rec.paymentMethod})",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRev) DesktopColors.ErrorRed else DesktopColors.BlackText
                                )
                                Text(
                                    "${rec.txDateFormatted} ${rec.txTimeFormatted} • By ${rec.createdByName}${if (rec.notes.isNotBlank()) " • ${rec.notes}" else ""}",
                                    fontSize = 12.sp,
                                    color = DesktopColors.SecondaryGray
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SyncStatusBadge(rec.syncStatus)
                                Text(
                                    "${if (isRev) "-" else ""}${SharedCurrencyFormatter.formatPkr(rec.amountPaisa)}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRev) DesktopColors.ErrorRed else DesktopColors.SuccessGreen
                                )
                                if (isAdmin && !isRev && !alreadyReversed) {
                                    OutlinedButton(onClick = { reverseTarget = rec }) {
                                        Text("Reverse", color = DesktopColors.ErrorRed, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    reverseTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { reverseTarget = null },
            title = { Text("Reverse Recovery ${target.txCode}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Amount: ${SharedCurrencyFormatter.formatPkr(target.amountPaisa)} for ${target.shopName}. This preserves audit history with a reversal entry.")
                    OutlinedTextField(value = reverseReason, onValueChange = { reverseReason = it }, label = { Text("Mandatory Reversal Reason") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        controller.reverseRecovery(target, reverseReason)
                        reverseTarget = null; reverseReason = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.ErrorRed)
                ) { Text("Confirm Reversal") }
            },
            dismissButton = { TextButton(onClick = { reverseTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddShopModalDialog(
    bookers: List<Booker>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Booker, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedBooker by remember { mutableStateOf(bookers.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Shop") },
        text = {
            Column(modifier = Modifier.widthIn(min = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shop Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = owner, onValueChange = { owner = it }, label = { Text("Owner Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobile Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = opening, onValueChange = { opening = it }, label = { Text("Opening Credit (PKR, optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Assign Booker", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    bookers.forEach { b ->
                        FilterChip(selected = selectedBooker?.id == b.id, onClick = { selectedBooker = b }, label = { Text(b.fullName) })
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && selectedBooker != null,
                onClick = { selectedBooker?.let { onSave(name, owner, mobile, address, city, it, opening, notes) } }
            ) { Text("Create Shop") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
