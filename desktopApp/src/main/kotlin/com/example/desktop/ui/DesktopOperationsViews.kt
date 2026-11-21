package com.example.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.example.desktop.export.DesktopPdfAndPrintEngine
import com.example.desktop.state.DesktopAppState
import com.example.desktop.state.DesktopNavSection
import com.example.desktop.state.DesktopUiState
import com.example.shared.engine.SharedFinancialEngine
import com.example.shared.model.*
import com.example.shared.util.SharedCurrencyFormatter
import com.example.shared.util.SharedPakistanTime
import java.io.File

@Composable
fun DesktopDashboardView(
    state: DesktopUiState,
    controller: DesktopAppState
) {
    val summary = state.dashboardSummary
    val unsyncedCount = state.pendingSync.count { it.syncStatus != SyncStatus.SYNCED.key }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Executive Dashboard", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
                Text(
                    "${state.business?.businessName ?: state.activeSession?.businessName.orEmpty()} (${state.activeSession?.businessCode.orEmpty()}) • Signed in as ${state.activeSession?.fullName.orEmpty()} (${state.activeSession?.role?.label.orEmpty()})",
                    fontSize = 13.sp,
                    color = DesktopColors.SecondaryGray
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { controller.selectSection(DesktopNavSection.CREDIT_RECOVERY) }) {
                    Icon(Icons.Default.Payments, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Credit / Recovery")
                }
                Button(
                    onClick = { controller.runSyncNow(quiet = false) },
                    colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Navy)
                ) {
                    Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.isSyncing) "Syncing..." else "Sync Now")
                }
            }
        }

        if (unsyncedCount > 0) {
            Surface(color = DesktopColors.WarningBg, shape = MaterialTheme.shapes.medium) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$unsyncedCount offline transaction(s) queued in Windows SQLite awaiting cloud synchronization.",
                        color = Color(0xFF92400E),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    TextButton(onClick = { controller.selectSection(DesktopNavSection.OFFLINE_SYNC) }) {
                        Text("Inspect Queue")
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DesktopKpiCard(
                title = "TOTAL RECEIVABLES",
                value = SharedCurrencyFormatter.formatPkr(summary?.totalOutstandingPaisa ?: 0L),
                subtitle = "${summary?.activeShops ?: 0} Active Shops",
                bgColor = DesktopColors.ErrorBg,
                accentColor = DesktopColors.ErrorRed,
                modifier = Modifier.weight(1f)
            )
            DesktopKpiCard(
                title = "TOTAL CREDIT ISSUED",
                value = SharedCurrencyFormatter.formatPkr(summary?.totalCreditPaisa ?: 0L),
                subtitle = "${state.credits.size} Credit Entries",
                bgColor = DesktopColors.KpiBlueBg,
                accentColor = DesktopColors.Navy,
                modifier = Modifier.weight(1f)
            )
            DesktopKpiCard(
                title = "TOTAL RECOVERED",
                value = SharedCurrencyFormatter.formatPkr(summary?.totalRecoveredPaisa ?: 0L),
                subtitle = "${state.recoveries.size} Recovery Entries",
                bgColor = DesktopColors.SuccessBg,
                accentColor = DesktopColors.SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            DesktopKpiCard(
                title = "TEAM & CATALOG",
                value = "${summary?.totalBookers ?: 0} Bookers",
                subtitle = "${summary?.totalStaff ?: 0} Staff • ${state.products.size} Products",
                bgColor = DesktopColors.KpiPurpleBg,
                accentColor = DesktopColors.KpiPurpleIcon,
                modifier = Modifier.weight(1f)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DesktopCard(modifier = Modifier.weight(1.3f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Top Outstanding Shops (Click for Supplier Ledger)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DesktopColors.Navy)
                    TextButton(onClick = { controller.selectSection(DesktopNavSection.SHOPS) }) { Text("View All") }
                }
                Spacer(Modifier.height(8.dp))
                state.shopBalances.filter { !it.shop.isArchived }.sortedByDescending { it.outstandingPaisa }.take(8).forEach { sb ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { controller.openShopLedger(sb.shop) }.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(sb.shop.shopName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${sb.shop.shopUid} • Booker: ${sb.shop.bookerName}", fontSize = 11.sp, color = DesktopColors.SecondaryGray)
                        }
                        Text(
                            SharedCurrencyFormatter.formatPkr(sb.outstandingPaisa),
                            fontWeight = FontWeight.Bold,
                            color = if (sb.outstandingPaisa > 0) DesktopColors.ErrorRed else DesktopColors.SuccessGreen
                        )
                    }
                    HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.5f))
                }
            }

            DesktopCard(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Booker Performance", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DesktopColors.Navy)
                    TextButton(onClick = { controller.selectSection(DesktopNavSection.BOOKERS) }) { Text("All Bookers") }
                }
                Spacer(Modifier.height(8.dp))
                state.bookerSummaries.take(8).forEach { bs ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(bs.booker.fullName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${bs.assignedShopsCount} Shops • Recovered ${SharedCurrencyFormatter.formatCompactPkr(bs.totalRecoveredPaisa)}", fontSize = 11.sp, color = DesktopColors.SecondaryGray)
                        }
                        Text(SharedCurrencyFormatter.formatPkr(bs.outstandingPaisa), fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
                    }
                    HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun DesktopBookersView(state: DesktopUiState, controller: DesktopAppState) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedBooker by remember { mutableStateOf<Booker?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Bookers & Consolidated All-Shops Ledger", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
                Text("Track field bookers, assigned shop balances, and export Booker-Wise All-Shops PDFs.", fontSize = 13.sp, color = DesktopColors.SecondaryGray)
            }
            if (state.activeSession?.role == AccountRole.SUPER_ADMIN || state.permission?.manageBookers == true) {
                Button(onClick = { showAddDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Navy)) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Booker")
                }
            }
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DesktopCard(modifier = Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(0.dp)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.bookerSummaries, key = { it.booker.id }) { bs ->
                        val active = selectedBooker?.id == bs.booker.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (active) DesktopColors.NavyContainer else Color.White)
                                .clickable { selectedBooker = bs.booker }
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(bs.booker.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${bs.booker.bookerCode} • @${bs.booker.username} • ${bs.assignedShopsCount} shops", fontSize = 12.sp, color = DesktopColors.SecondaryGray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(SharedCurrencyFormatter.formatPkr(bs.outstandingPaisa), fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
                                Text("Rec: ${SharedCurrencyFormatter.formatCompactPkr(bs.totalRecoveredPaisa)}", fontSize = 11.sp, color = DesktopColors.SuccessGreen)
                            }
                        }
                        HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.6f))
                    }
                }
            }

            DesktopCard(modifier = Modifier.weight(1.4f).fillMaxHeight()) {
                val target = selectedBooker ?: state.bookers.firstOrNull()
                if (target == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Select a booker to inspect consolidated shop recovery ledger.", color = DesktopColors.SecondaryGray)
                    }
                } else {
                    val ledger = remember(target, state.shops, state.credits, state.recoveries) {
                        SharedFinancialEngine.computeBookerConsolidatedLedger(target, state.shops, state.credits, state.recoveries)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("${target.fullName} — All Shops Recovery", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DesktopColors.Navy)
                            Text("Credit: ${SharedCurrencyFormatter.formatPkr(ledger.grandTotalCreditPaisa)} | Recovered: ${SharedCurrencyFormatter.formatPkr(ledger.grandTotalRecoveredPaisa)} | Due: ${SharedCurrencyFormatter.formatPkr(ledger.grandTotalOutstandingPaisa)}", fontSize = 12.sp, color = DesktopColors.SecondaryGray)
                        }
                        OutlinedButton(onClick = {
                            val file = File(File(System.getProperty("user.home"), "Downloads/HisabPro"), "Booker_${target.bookerCode}_Ledger.pdf")
                            DesktopPdfAndPrintEngine.generateBookerConsolidatedPdf(file, state.business?.businessName ?: "HisabPro", ledger)
                            DesktopPdfAndPrintEngine.openFileInDesktopViewer(file)
                        }) {
                            Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export PDF")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(ledger.shopDetails, key = { it.shop.id }) { sd ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { controller.openShopLedger(sd.shop) }.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${sd.shop.shopName} (${sd.shop.shopUid})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Status: ${sd.paymentStatus} • Invoices: ${sd.invoices.size} • Payments: ${sd.payments.size}", fontSize = 11.sp, color = DesktopColors.SecondaryGray)
                                }
                                Text(
                                    SharedCurrencyFormatter.formatPkr(sd.remainingOutstandingPaisa),
                                    fontWeight = FontWeight.Bold,
                                    color = if (sd.remainingOutstandingPaisa > 0) DesktopColors.ErrorRed else DesktopColors.SuccessGreen
                                )
                            }
                            HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var user by remember { mutableStateOf("") }
        var mob by remember { mutableStateOf("") }
        var addr by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Create Booker Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, singleLine = true)
                    OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(value = mob, onValueChange = { mob = it }, label = { Text("Mobile") }, singleLine = true)
                    OutlinedTextField(value = addr, onValueChange = { addr = it }, label = { Text("Route / Address") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank() && user.isNotBlank(), onClick = {
                    controller.addBooker(name, user, mob, addr)
                    showAddDialog = false
                }) { Text("Create Booker") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun DesktopProductsAndBookingsView(state: DesktopUiState, controller: DesktopAppState) {
    var sku by remember { mutableStateOf("") }
    var pName by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("Carton") }
    var price by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("50") }

    var selectedShopId by remember(state.shops) { mutableStateOf(state.shops.firstOrNull()?.id.orEmpty()) }
    var selectedProductId by remember(state.products) { mutableStateOf(state.products.firstOrNull()?.id.orEmpty()) }
    var qty by remember { mutableStateOf("1") }
    var bkNotes by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Inventory Catalog & Shop Bookings (Multi-Device Cloud + Offline SQLite)", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Left: Inventory Catalog
            DesktopCard(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text("Products & Stock", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DesktopColors.Navy)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = pName, onValueChange = { pName = it }, label = { Text("Product") }, modifier = Modifier.weight(1.4f), singleLine = true)
                    OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price PKR") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = stock, onValueChange = { stock = it }, label = { Text("Qty") }, modifier = Modifier.weight(0.7f), singleLine = true)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU (Optional)") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit") }, modifier = Modifier.weight(0.8f), singleLine = true)
                    Button(
                        enabled = pName.isNotBlank() && price.isNotBlank(),
                        onClick = {
                            controller.addProduct(sku, pName, unit, price, stock.toIntOrNull() ?: 0, 5)
                            pName = ""; sku = ""; price = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Navy)
                    ) { Text("Add") }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.products, key = { it.id }) { prod ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("${prod.name} (${prod.sku})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Unit: ${prod.unit} • Stock: ${prod.stockQty}", fontSize = 11.sp, color = if (prod.stockQty <= prod.reorderLevel) DesktopColors.WarningOrange else DesktopColors.SecondaryGray)
                            }
                            Text(SharedCurrencyFormatter.formatPkr(prod.unitPricePaisa), fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
                        }
                        HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.5f))
                    }
                }
            }

            // Right: Bookings
            DesktopCard(modifier = Modifier.weight(1.1f).fillMaxHeight()) {
                Text("Shop Bookings / Orders", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DesktopColors.Navy)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.shops.filter { !it.isArchived }.forEach { s ->
                        FilterChip(selected = selectedShopId == s.id, onClick = { selectedShopId = s.id }, label = { Text(s.shopName) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.products.forEach { p ->
                        FilterChip(selected = selectedProductId == p.id, onClick = { selectedProductId = p.id }, label = { Text("${p.name} (${SharedCurrencyFormatter.formatPkr(p.unitPricePaisa)})") })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Qty") }, modifier = Modifier.width(90.dp), singleLine = true)
                    OutlinedTextField(value = bkNotes, onValueChange = { bkNotes = it }, label = { Text("Booking Notes") }, modifier = Modifier.weight(1f), singleLine = true)
                    Button(
                        enabled = state.shops.any { it.id == selectedShopId } && state.products.any { it.id == selectedProductId },
                        onClick = {
                            val s = state.shops.first { it.id == selectedShopId }
                            val p = state.products.first { it.id == selectedProductId }
                            controller.createBooking(s, p, qty.toIntOrNull() ?: 1, bkNotes)
                            bkNotes = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.SuccessGreen)
                    ) { Text("Book Order") }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.bookings, key = { it.id }) { bk ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${bk.bookingCode} • ${bk.shopName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${bk.productName} x ${bk.quantity} • ${bk.bookingDateFormatted}", fontSize = 11.sp, color = DesktopColors.SecondaryGray)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                SyncStatusBadge(bk.syncStatus)
                                Text(SharedCurrencyFormatter.formatPkr(bk.totalAmountPaisa), fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
                            }
                        }
                        HorizontalDivider(color = DesktopColors.Border.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopReportsView(state: DesktopUiState, controller: DesktopAppState) {
    val dailyReport = remember(state.shops, state.bookers, state.credits, state.recoveries) {
        SharedFinancialEngine.computeDailyPerformanceReport(state.shops, state.bookers, state.credits, state.recoveries)
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Reports, Printing & Backup Exports", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DesktopColors.Navy)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val out = File(File(System.getProperty("user.home"), "Downloads/HisabPro"), "Daily_Report_${SharedPakistanTime.formatDashDate()}.pdf")
                DesktopPdfAndPrintEngine.generateDailyPerformancePdf(out, state.business?.businessName ?: "HisabPro", dailyReport)
                DesktopPdfAndPrintEngine.openFileInDesktopViewer(out)
            }, colors = ButtonDefaults.buttonColors(containerColor = DesktopColors.Navy)) {
                Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Daily Performance PDF")
            }
            OutlinedButton(onClick = {
                val out = File(File(System.getProperty("user.home"), "Downloads/HisabPro"), "All_Shops_${SharedPakistanTime.formatDashDate()}.pdf")
                DesktopPdfAndPrintEngine.generateAllShopsSummaryPdf(out, state.business?.businessName ?: "HisabPro", state.shopBalances)
                DesktopPdfAndPrintEngine.openFileInDesktopViewer(out)
            }) { Text("All Shops A4 PDF") }
            OutlinedButton(onClick = { controller.exportAllShopsExcel() }) { Text("All Shops Excel (.xlsx)") }
            OutlinedButton(onClick = { controller.exportJsonBackup() }) { Text("Full Workspace JSON Backup") }
        }

        DesktopCard {
            Text("Daily Performance Summary — ${dailyReport.dateFormatted} (PKT)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DesktopColors.Navy)
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DesktopKpiCard("TODAY CREDIT", SharedCurrencyFormatter.formatPkr(dailyReport.todayCreditPaisa), "${dailyReport.todayCreditCount} Invoices", DesktopColors.KpiBlueBg, DesktopColors.Navy, Modifier.weight(1f))
                DesktopKpiCard("TODAY RECOVERY", SharedCurrencyFormatter.formatPkr(dailyReport.todayRecoveryPaisa), "${dailyReport.todayRecoveryCount} Collections", DesktopColors.SuccessBg, DesktopColors.SuccessGreen, Modifier.weight(1f))
                DesktopKpiCard("NET RECEIVABLES", SharedCurrencyFormatter.formatPkr(dailyReport.totalOutstandingPaisa), "Total Unrecovered", DesktopColors.ErrorBg, DesktopColors.ErrorRed, Modifier.weight(1f))
            }
        }
    }
}
