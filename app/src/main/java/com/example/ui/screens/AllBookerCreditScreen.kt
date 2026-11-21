package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Booker
import com.example.domain.model.BookerBalanceSummary
import com.example.domain.model.CreditTransaction
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.ShopBalanceSummary
import com.example.ui.PdfExportMode
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
import com.example.ui.theme.HisabLightGray
import com.example.ui.theme.HisabNavy
import com.example.ui.theme.HisabNavyContainer
import com.example.ui.theme.HisabSecondaryGray
import com.example.ui.theme.HisabSuccessBg
import com.example.ui.theme.HisabSuccessGreen
import com.example.ui.theme.HisabWhite
import com.example.util.CurrencyFormatter
import com.example.util.PakistanTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllBookerCreditScreen(
    bookerSummaries: List<BookerBalanceSummary>,
    shopBalances: List<ShopBalanceSummary>,
    creditTransactions: List<CreditTransaction>,
    recoveryTransactions: List<RecoveryTransaction>,
    isOnline: Boolean,
    actionMessage: Pair<Boolean, String>?,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
    onOpenBookerRecord: (String) -> Unit,
    onExportBookerPdf: (
        Context,
        Booker,
        List<ShopBalanceSummary>,
        List<CreditTransaction>,
        List<RecoveryTransaction>,
        PdfExportMode,
        Long?,
        Long?
    ) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var useCustomRange by remember { mutableStateOf(false) }
    var startMillis by remember { mutableLongStateOf(PakistanTime.thisMonthRange().first) }
    var endMillis by remember { mutableLongStateOf(PakistanTime.todayRange().second) }
    var showRangeDialog by remember { mutableStateOf(false) }
    val activeStart = if (useCustomRange) PakistanTime.startOfDay(startMillis) else null
    val activeEnd = if (useCustomRange) PakistanTime.endOfDay(endMillis) else null

    val filtered = remember(bookerSummaries, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) bookerSummaries else bookerSummaries.filter {
            it.booker.fullName.lowercase().contains(q) ||
                it.booker.bookerCode.lowercase().contains(q) ||
                it.booker.username.lowercase().contains(q) ||
                it.booker.mobileNumber.lowercase().contains(q)
        }
    }

    val totalCredit = remember(filtered) { filtered.sumOf { it.totalCreditPaisa } }
    val totalRecovered = remember(filtered) { filtered.sumOf { it.totalRecoveredPaisa } }
    val totalOutstanding = remember(filtered) { filtered.sumOf { it.outstandingPaisa } }

    Scaffold(
        topBar = {
            HisabNavyTopBar(
                title = "All Booker Credit",
                subtitle = "${bookerSummaries.size} Bookers • Tap any Booker for full record & PDF",
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
                    placeholder = { Text("Search Booker name, code, or username...") },
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
                        .testTag("all_booker_credit_search")
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = HisabNavyContainer.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Bookers", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                            Text("${filtered.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = HisabNavy)
                        }
                        Column {
                            Text("Credit", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                            Text(CurrencyFormatter.formatPkr(totalCredit), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = HisabNavy)
                        }
                        Column {
                            Text("Recovered", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                            Text(CurrencyFormatter.formatPkr(totalRecovered), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = HisabSuccessGreen)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Outstanding", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                            Text(CurrencyFormatter.formatPkr(totalOutstanding), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = HisabErrorRed)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showRangeDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (useCustomRange) {
                                "PDF Range: ${PakistanTime.formatDate(startMillis)} - ${PakistanTime.formatDate(endMillis)}"
                            } else {
                                "PDF Period: All Time (Tap to filter)"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (useCustomRange) {
                        TextButton(onClick = { useCustomRange = false }) {
                            Text("All Time", color = HisabNavy)
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        icon = Icons.Default.Badge,
                        title = "No Bookers Found",
                        message = "Add a Booker first to track assigned shops credit records and generate combined PDFs."
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.booker.id }) { summary ->
                        val b = summary.booker
                        val isActive = b.status == "ACTIVE"
                        val bShops = remember(shopBalances, b.id) {
                            shopBalances.filter { it.shop.bookerId == b.id && !it.shop.isArchived }
                        }
                        val bCredits = remember(creditTransactions, b.id) {
                            creditTransactions.filter { it.bookerId == b.id }
                        }
                        val bRecoveries = remember(recoveryTransactions, b.id) {
                            recoveryTransactions.filter { it.bookerId == b.id }
                        }

                        Card(
                            onClick = { onOpenBookerRecord(b.id) },
                            colors = CardDefaults.cardColors(containerColor = HisabWhite),
                            border = BorderStroke(1.dp, HisabBorder),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("all_booker_card_${b.bookerCode}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(HisabKpiBlueBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = HisabNavy,
                                            modifier = Modifier.size(24.dp)
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
                                            text = "${b.bookerCode} • @${b.username} • ${summary.assignedShopsCount} Shops",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = HisabSecondaryGray
                                        )
                                    }
                                    StatusPill(
                                        text = if (isActive) "Active" else "Inactive",
                                        containerColor = if (isActive) HisabSuccessBg else HisabErrorBg,
                                        contentColor = if (isActive) HisabSuccessGreen else HisabErrorRed
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Open Booker Credit Record",
                                        tint = HisabSecondaryGray
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Total Credit", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                                        Text(CurrencyFormatter.formatPkr(summary.totalCreditPaisa), style = MaterialTheme.typography.labelLarge, color = HisabNavy, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Recovered", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                                        Text(CurrencyFormatter.formatPkr(summary.totalRecoveredPaisa), style = MaterialTheme.typography.labelLarge, color = HisabSuccessGreen, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Outstanding", style = MaterialTheme.typography.labelSmall, color = HisabSecondaryGray)
                                        Text(
                                            text = CurrencyFormatter.formatPkr(summary.outstandingPaisa),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (summary.outstandingPaisa > 0L) HisabErrorRed else HisabSuccessGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                HorizontalDivider(color = HisabBorder)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onExportBookerPdf(context, b, bShops, bCredits, bRecoveries, PdfExportMode.VIEW, activeStart, activeEnd)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("View PDF", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onExportBookerPdf(context, b, bShops, bCredits, bRecoveries, PdfExportMode.DOWNLOAD, activeStart, activeEnd)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Download", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                    }
                                    Button(
                                        onClick = {
                                            onExportBookerPdf(context, b, bShops, bCredits, bRecoveries, PdfExportMode.SHARE, activeStart, activeEnd)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = HisabNavy),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Share", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRangeDialog) {
        val rangeState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = PakistanTime.toDatePickerUtcMillis(startMillis),
            initialSelectedEndDateMillis = PakistanTime.toDatePickerUtcMillis(endMillis)
        )
        DatePickerDialog(
            onDismissRequest = { showRangeDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = rangeState.selectedStartDateMillis?.let { PakistanTime.fromDatePickerUtcMillis(it) } ?: startMillis
                        val e = rangeState.selectedEndDateMillis?.let { PakistanTime.fromDatePickerUtcMillis(it) } ?: s
                        if (s <= e) {
                            startMillis = s
                            endMillis = e
                        } else {
                            startMillis = e
                            endMillis = s
                        }
                        useCustomRange = true
                        showRangeDialog = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showRangeDialog = false }) { Text("Cancel") } }
        ) {
            DateRangePicker(state = rangeState, modifier = Modifier.height(420.dp))
        }
    }
}
