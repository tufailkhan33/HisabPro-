package com.example.shared.model

enum class AccountRole(val key: String, val label: String) {
    SUPER_ADMIN("SUPER_ADMIN", "Super Admin"),
    STAFF("STAFF", "Authorized Staff"),
    BOOKER("BOOKER", "Booker");

    companion object {
        fun fromKey(key: String?): AccountRole =
            entries.firstOrNull { it.key == key } ?: BOOKER
    }
}

enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    BANK_TRANSFER("Bank Transfer"),
    EASYPAISA("Easypaisa"),
    JAZZCASH("JazzCash"),
    OTHER("Other");

    companion object {
        fun fromLabel(label: String): PaymentMethod =
            entries.firstOrNull { it.label == label } ?: CASH
    }
}

enum class DateFilterPreset(val label: String) {
    ALL_TIME("All Time"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    THIS_MONTH("This Month"),
    PREVIOUS_MONTH("Previous Month"),
    CUSTOM("Custom Range")
}

enum class ShopFilterOption(val label: String) {
    ALL("All Shops"),
    OUTSTANDING("Outstanding"),
    FULLY_RECOVERED("Fully Recovered"),
    RECENTLY_ADDED("Recently Added"),
    HIGHEST_OUTSTANDING("Highest Outstanding"),
    LOWEST_OUTSTANDING("Lowest Outstanding"),
    ARCHIVED("Archived")
}

enum class SyncStatus(val key: String, val label: String) {
    PENDING_SYNC("PENDING_SYNC", "Pending"),
    SYNCING("SYNCING", "Syncing"),
    SYNCED("SYNCED", "Synced"),
    SYNC_FAILED("SYNC_FAILED", "Failed"),
    CONFLICT_REQUIRES_REVIEW("CONFLICT_REQUIRES_REVIEW", "Conflict");

    companion object {
        fun fromKey(key: String?): SyncStatus = when (key) {
            "PENDING", "PENDING_SYNC" -> PENDING_SYNC
            "SYNCING" -> SYNCING
            "SYNCED" -> SYNCED
            "FAILED", "SYNC_FAILED" -> SYNC_FAILED
            "CONFLICT", "CONFLICT_REQUIRES_REVIEW" -> CONFLICT_REQUIRES_REVIEW
            else -> SYNCED
        }
    }
}

enum class BusinessStatus(val key: String, val label: String) {
    ACTIVE("ACTIVE", "Active"),
    SUSPENDED("SUSPENDED", "Suspended");

    companion object {
        fun fromKey(key: String?): BusinessStatus =
            entries.firstOrNull { it.key == key } ?: ACTIVE
    }
}

data class Business(
    val id: String = "",
    val businessName: String = "",
    val businessCode: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val ownerMobile: String = "",
    val ownerEmail: String = "",
    val currency: String = "PKR",
    val timezone: String = "Asia/Karachi",
    val allowOverRecovery: Boolean = false,
    val status: String = "ACTIVE",
    val suspendedReason: String = "",
    val lastActivityMillis: Long = 0L,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class LoginTicket(
    val id: String = "",
    val businessId: String = "",
    val businessName: String = "",
    val businessCode: String = "",
    val accountId: String = "",
    val role: String = "BOOKER",
    val username: String = "",
    val fullName: String = "",
    val passwordSalt: String = "",
    val passwordHash: String = "",
    val mustChangePassword: Boolean = false,
    val status: String = "ACTIVE",
    val boundGoogleUid: String = "",
    val ownerUid: String = "",
    val sessionRevokedBeforeMillis: Long = 0L,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class UserWorkspaceBookmark(
    val id: String = "",
    val businessId: String = "",
    val businessName: String = "",
    val businessCode: String = "",
    val lastUsername: String = "",
    val lastRole: String = "SUPER_ADMIN",
    val updatedAtMillis: Long = 0L
)

data class WorkspaceMember(
    val id: String = "",
    val businessId: String = "",
    val googleUid: String = "",
    val accountId: String = "",
    val username: String = "",
    val fullName: String = "",
    val role: String = "BOOKER",
    val bookerId: String = "",
    val status: String = "ACTIVE",
    val claimedTicketId: String = "",
    val sessionRevokedBeforeMillis: Long = 0L
)

data class BusinessUser(
    val id: String = "",
    val businessId: String = "",
    val userCode: String = "",
    val fullName: String = "",
    val username: String = "",
    val mobileNumber: String = "",
    val email: String = "",
    val role: String = "STAFF",
    val status: String = "ACTIVE",
    val mustChangePassword: Boolean = false,
    val sessionRevokedBeforeMillis: Long = 0L,
    val lastLoginMillis: Long = 0L
)

data class StaffPermission(
    val id: String = "",
    val businessId: String = "",
    val userId: String = "",
    val viewDashboard: Boolean = true,
    val viewShops: Boolean = true,
    val addShops: Boolean = false,
    val editShops: Boolean = false,
    val addCredit: Boolean = false,
    val editCredit: Boolean = false,
    val recordRecovery: Boolean = false,
    val viewRecoveryHistory: Boolean = true,
    val viewBookers: Boolean = true,
    val manageBookers: Boolean = false,
    val viewReports: Boolean = true,
    val generatePdfs: Boolean = true,
    val searchShops: Boolean = true,
    val viewActivityLogs: Boolean = false
) {
    companion object {
        fun fullAdmin(businessId: String, userId: String) = StaffPermission(
            id = userId,
            businessId = businessId,
            userId = userId,
            viewDashboard = true,
            viewShops = true,
            addShops = true,
            editShops = true,
            addCredit = true,
            editCredit = true,
            recordRecovery = true,
            viewRecoveryHistory = true,
            viewBookers = true,
            manageBookers = true,
            viewReports = true,
            generatePdfs = true,
            searchShops = true,
            viewActivityLogs = true
        )

        fun bookerReadOnly(businessId: String, bookerId: String) = StaffPermission(
            id = bookerId,
            businessId = businessId,
            userId = bookerId,
            viewDashboard = true,
            viewShops = true,
            addShops = false,
            editShops = false,
            addCredit = false,
            editCredit = false,
            recordRecovery = false,
            viewRecoveryHistory = true,
            viewBookers = false,
            manageBookers = false,
            viewReports = true,
            generatePdfs = true,
            searchShops = true,
            viewActivityLogs = false
        )
    }
}

data class Booker(
    val id: String = "",
    val businessId: String = "",
    val bookerCode: String = "",
    val fullName: String = "",
    val username: String = "",
    val mobileNumber: String = "",
    val address: String = "",
    val notes: String = "",
    val status: String = "ACTIVE",
    val mustChangePassword: Boolean = false,
    val sessionRevokedBeforeMillis: Long = 0L,
    val lastLoginMillis: Long = 0L
)

data class Shop(
    val id: String = "",
    val businessId: String = "",
    val shopUid: String = "",
    val shopName: String = "",
    val bookerId: String = "",
    val bookerName: String = "",
    val ownerName: String = "",
    val mobileNumber: String = "",
    val address: String = "",
    val city: String = "",
    val openingCreditPaisa: Long = 0L,
    val openingDateMillis: Long = 0L,
    val notes: String = "",
    val isArchived: Boolean = false,
    val createdByAccountId: String = "",
    val createdByName: String = ""
)

data class ProductItem(
    val id: String = "",
    val businessId: String = "",
    val sku: String = "",
    val name: String = "",
    val unit: String = "Pack",
    val unitPricePaisa: Long = 0L,
    val stockQty: Int = 0,
    val reorderLevel: Int = 5,
    val isArchived: Boolean = false,
    val updatedAtMillis: Long = 0L,
    val syncStatus: String = SyncStatus.SYNCED.key
)

data class BookingOrder(
    val id: String = "",
    val businessId: String = "",
    val bookingCode: String = "",
    val shopId: String = "",
    val shopUid: String = "",
    val shopName: String = "",
    val bookerId: String = "",
    val bookerName: String = "",
    val productId: String = "",
    val productName: String = "",
    val quantity: Int = 1,
    val unitPricePaisa: Long = 0L,
    val totalAmountPaisa: Long = 0L,
    val status: String = "BOOKED", // BOOKED, DISPATCHED, CANCELLED
    val notes: String = "",
    val bookingDateMillis: Long = 0L,
    val bookingDateFormatted: String = "",
    val createdByAccountId: String = "",
    val createdByName: String = "",
    val syncStatus: String = SyncStatus.SYNCED.key,
    val idempotencyToken: String = "",
    val deviceId: String = ""
)

data class CreditTransaction(
    val id: String = "",
    val businessId: String = "",
    val txCode: String = "",
    val shopId: String = "",
    val shopUid: String = "",
    val shopName: String = "",
    val bookerId: String = "",
    val bookerName: String = "",
    val amountPaisa: Long = 0L,
    val transactionKind: String = "CREDIT",
    val relatedTxId: String = "",
    val reason: String = "",
    val invoiceNumber: String = "",
    val description: String = "",
    val attachmentUri: String = "",
    val txDateMillis: Long = 0L,
    val txDateFormatted: String = "",
    val txTimeFormatted: String = "",
    val createdByAccountId: String = "",
    val createdByName: String = "",
    val createdByRole: String = "",
    val dueDateMillis: Long = 0L,
    val dueDateFormatted: String = "",
    val entryTimestampMillis: Long = 0L,
    val entryDateFormatted: String = "",
    val entryTimeFormatted: String = "",
    val syncStatus: String = "SYNCED",
    val idempotencyToken: String = "",
    val deviceId: String = ""
)

data class RecoveryTransaction(
    val id: String = "",
    val businessId: String = "",
    val txCode: String = "",
    val shopId: String = "",
    val shopUid: String = "",
    val shopName: String = "",
    val bookerId: String = "",
    val bookerName: String = "",
    val amountPaisa: Long = 0L,
    val transactionKind: String = "RECOVERY",
    val relatedTxId: String = "",
    val reason: String = "",
    val paymentMethod: String = "Cash",
    val referenceNumber: String = "",
    val notes: String = "",
    val txDateMillis: Long = 0L,
    val txDateFormatted: String = "",
    val txTimeFormatted: String = "",
    val createdByAccountId: String = "",
    val createdByName: String = "",
    val createdByRole: String = "",
    val correctionGroupId: String = "",
    val correctedFromTxId: String = "",
    val originalAmountPaisa: Long = 0L,
    val originalTxDateMillis: Long = 0L,
    val isBackdated: Boolean = false,
    val reversedAtMillis: Long = 0L,
    val reversedByAccountId: String = "",
    val reversedByName: String = "",
    val syncStatus: String = "SYNCED",
    val idempotencyToken: String = "",
    val deviceId: String = ""
)

data class AuditLog(
    val id: String = "",
    val businessId: String = "",
    val activityCode: String = "",
    val userId: String = "",
    val username: String = "",
    val role: String = "",
    val action: String = "",
    val shopUid: String = "",
    val previousValue: String = "",
    val newValue: String = "",
    val dateFormatted: String = "",
    val timeFormatted: String = "",
    val timestampMillis: Long = 0L,
    val description: String = ""
)

data class NotificationItem(
    val id: String = "",
    val businessId: String = "",
    val title: String = "",
    val message: String = "",
    val category: String = "",
    val targetRole: String = "ADMIN_STAFF",
    val targetBookerId: String = "",
    val shopUid: String = "",
    val isRead: Boolean = false,
    val createdAtMillis: Long = 0L
)

data class BusinessSettings(
    val id: String = "",
    val businessId: String = "",
    val businessName: String = "",
    val businessAddress: String = "",
    val contactPhone: String = "",
    val allowOverRecovery: Boolean = false,
    val requireInvoiceNumber: Boolean = false,
    val updatedBy: String = ""
)

data class ShopBalanceSummary(
    val shop: Shop,
    val totalCreditPaisa: Long,
    val totalRecoveredPaisa: Long,
    val outstandingPaisa: Long,
    val firstCreditDateMillis: Long?,
    val lastTransactionDateMillis: Long?,
    val creditCount: Int,
    val recoveryCount: Int
)

data class BookerBalanceSummary(
    val booker: Booker,
    val assignedShopsCount: Int,
    val totalCreditPaisa: Long,
    val totalRecoveredPaisa: Long,
    val outstandingPaisa: Long
)

data class BusinessDashboardSummary(
    val totalCreditPaisa: Long,
    val totalRecoveredPaisa: Long,
    val totalOutstandingPaisa: Long,
    val totalShops: Int,
    val activeShops: Int,
    val totalBookers: Int,
    val totalStaff: Int
)

data class PeriodShopRow(
    val shop: Shop,
    val periodCreditPaisa: Long,
    val periodRecoveredPaisa: Long,
    val closingOutstandingAsOfEndDatePaisa: Long,
    val currentOutstandingPaisa: Long
)

data class PeriodReportSummary(
    val startMillis: Long,
    val endMillis: Long,
    val periodCreditPaisa: Long,
    val periodRecoveredPaisa: Long,
    val periodNetChangePaisa: Long,
    val closingOutstandingAsOfEndDatePaisa: Long,
    val currentOutstandingPaisa: Long,
    val creditTxCount: Int,
    val recoveryTxCount: Int,
    val shopRows: List<PeriodShopRow>,
    val filteredCreditTxs: List<CreditTransaction>,
    val filteredRecoveryTxs: List<RecoveryTransaction>
)

data class ActiveWorkspaceSession(
    val businessId: String,
    val businessCode: String,
    val businessName: String,
    val accountId: String,
    val username: String,
    val fullName: String,
    val role: AccountRole,
    val bookerId: String,
    val mustChangePassword: Boolean
)

data class GeneratedCredentials(
    val fullName: String,
    val accountCode: String,
    val roleLabel: String,
    val businessCode: String,
    val username: String,
    val temporaryPassword: String
)

enum class ChatMessageType(val key: String, val label: String) {
    TEXT("TEXT", "Text"),
    VOICE("VOICE", "Voice Note"),
    IMAGE("IMAGE", "Photo"),
    FILE("FILE", "File");

    companion object {
        fun fromKey(key: String?): ChatMessageType =
            entries.firstOrNull { it.key == key } ?: TEXT
    }
}

data class ChatGroup(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    val description: String = "",
    val memberAccountIds: List<String> = emptyList(),
    val memberNames: List<String> = emptyList(),
    val createdByAccountId: String = "",
    val createdByName: String = "",
    val lastMessagePreview: String = "",
    val lastMessageAtMillis: Long = 0L,
    val isArchived: Boolean = false
)

data class ChatMessage(
    val id: String = "",
    val businessId: String = "",
    val groupId: String = "",
    val senderAccountId: String = "",
    val senderName: String = "",
    val senderRole: String = "",
    val messageType: String = "TEXT",
    val text: String = "",
    val mediaBase64: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val durationSec: Int = 0,
    val fileSizeBytes: Long = 0L,
    val sentAtMillis: Long = 0L,
    val dateFormatted: String = "",
    val timeFormatted: String = ""
)

data class ShopLedgerEntry(
    val id: String,
    val voucherNumber: String,
    val txDateMillis: Long,
    val txDateFormatted: String,
    val entryTimestampMillis: Long,
    val entryDateTimeFormatted: String,
    val description: String,
    val creditPaisa: Long,
    val recoveryPaisa: Long,
    val runningBalancePaisa: Long,
    val isReversal: Boolean,
    val isReversedOriginal: Boolean,
    val reason: String,
    val originalPaymentDateFormatted: String = "",
    val reversalDateFormatted: String = "",
    val syncStatus: String = "SYNCED"
)

data class ShopLedgerReport(
    val shop: Shop,
    val startMillis: Long,
    val endMillis: Long,
    val openingBalancePaisa: Long,
    val entries: List<ShopLedgerEntry>,
    val totalCreditPaisa: Long,
    val totalRecoveredPaisa: Long,
    val reversedTotalPaisa: Long,
    val closingBalancePaisa: Long,
    val hasUnsyncedData: Boolean
)

data class BookerShopInvoiceRow(
    val voucherNumber: String,
    val invoiceDateFormatted: String,
    val entryDateTimeFormatted: String,
    val dueDateFormatted: String,
    val amountPaisa: Long,
    val isReversal: Boolean,
    val reason: String,
    val syncStatus: String
)

data class BookerShopPaymentRow(
    val voucherNumber: String,
    val paymentDateFormatted: String,
    val method: String,
    val amountPaisa: Long,
    val isReversal: Boolean,
    val isReversedOriginal: Boolean,
    val reason: String,
    val originalDateFormatted: String,
    val reversalDateFormatted: String,
    val runningBalanceAfterPaisa: Long,
    val syncStatus: String
)

data class BookerShopRecoveryDetail(
    val shop: Shop,
    val invoices: List<BookerShopInvoiceRow>,
    val payments: List<BookerShopPaymentRow>,
    val totalCreditPaisa: Long,
    val totalRecoveredPaisa: Long,
    val reversedTotalPaisa: Long,
    val remainingOutstandingPaisa: Long,
    val paymentStatus: String,
    val hasUnsyncedData: Boolean
)

data class BookerConsolidatedLedger(
    val booker: Booker,
    val startMillis: Long,
    val endMillis: Long,
    val shopDetails: List<BookerShopRecoveryDetail>,
    val grandTotalCreditPaisa: Long,
    val grandTotalRecoveredPaisa: Long,
    val grandTotalOutstandingPaisa: Long,
    val hasUnsyncedData: Boolean
)

data class BookerDailyPerformance(
    val booker: Booker,
    val shopsAssignedCount: Int,
    val creditShopsTodayCount: Int,
    val recoveryShopsTodayCount: Int,
    val todayCreditPaisa: Long,
    val todayRecoveryPaisa: Long,
    val totalOutstandingPaisa: Long
)

data class DailyPerformanceReport(
    val dateMillis: Long,
    val dateFormatted: String,
    val todayCreditPaisa: Long,
    val todayRecoveryPaisa: Long,
    val todayCreditCount: Int,
    val todayRecoveryCount: Int,
    val totalOutstandingPaisa: Long,
    val shopsReceivingCreditToday: List<Shop>,
    val shopsMakingRecoveryToday: List<Shop>,
    val bookerBreakdown: List<BookerDailyPerformance>,
    val hasUnsyncedData: Boolean
)

data class PlatformOwnerConfig(
    val id: String = "root",
    val username: String = "MrTufailadmin",
    val authorizedEmail: String = "muhammadtufailllc007@gmail.com",
    val googleUid: String = "",
    val passwordSalt: String = "",
    val passwordHash: String = "",
    val mustChangePassword: Boolean = false,
    val sessionRevokedBeforeMillis: Long = 0L,
    val lastLoginMillis: Long = 0L,
    val deviceCount: Int = 0
)

data class PlatformOwnerSession(
    val username: String,
    val email: String,
    val googleUid: String,
    val loginTimestampMillis: Long,
    val needsPasswordSetup: Boolean = false,
    val mustChangePassword: Boolean = false
)

data class PlatformActivityLog(
    val id: String = "",
    val actorEmail: String = "",
    val actorUsername: String = "",
    val action: String = "",
    val targetBusinessId: String = "",
    val targetBusinessName: String = "",
    val targetAccountId: String = "",
    val targetUsername: String = "",
    val details: String = "",
    val timestampMillis: Long = 0L,
    val dateFormatted: String = "",
    val timeFormatted: String = ""
)

data class PlatformBackupSnapshot(
    val id: String = "",
    val createdByEmail: String = "",
    val totalBusinesses: Int = 0,
    val totalUsers: Int = 0,
    val totalBookers: Int = 0,
    val totalShops: Int = 0,
    val totalCreditTx: Int = 0,
    val totalRecoveryTx: Int = 0,
    val summaryJson: String = "",
    val createdAtMillis: Long = 0L
)

data class PendingSyncTransaction(
    val localId: String,
    val businessId: String,
    val userId: String,
    val username: String,
    val userRole: String,
    val deviceId: String,
    val shopId: String,
    val shopUid: String,
    val shopName: String,
    val bookerId: String,
    val bookerName: String,
    val txType: String, // "CREDIT", "RECOVERY", "BOOKING", "PRODUCT", "SHOP_UPDATE"
    val amountPaisa: Long,
    val originalTxDateMillis: Long,
    val dueDateMillis: Long,
    val invoiceOrRefNumber: String,
    val paymentMethod: String,
    val descriptionOrNotes: String,
    val localDeviceTimestamp: Long,
    val syncStatus: String,
    val idempotencyToken: String,
    val auditMetadata: String,
    val errorMessage: String = "",
    val retryCount: Int = 0,
    val serverSyncedAtMillis: Long = 0L
)

data class SyncHistoryRecord(
    val id: String,
    val businessId: String,
    val localTxId: String,
    val shopUid: String,
    val txType: String,
    val amountPaisa: Long,
    val syncStatus: String,
    val message: String,
    val syncedAtMillis: Long
)

data class PlatformGlobalMetrics(
    val totalBusinesses: Int,
    val activeBusinesses: Int,
    val suspendedBusinesses: Int,
    val totalAdmins: Int,
    val totalStaff: Int,
    val totalBookers: Int,
    val totalShops: Int,
    val totalCreditTransactions: Int,
    val totalRecoveryTransactions: Int,
    val totalCreditPaisa: Long,
    val totalRecoveredPaisa: Long,
    val totalOutstandingPaisa: Long
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
