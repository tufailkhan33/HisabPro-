package com.example.desktop.state

import com.example.desktop.cloud.DesktopFirebaseAuthSession
import com.example.desktop.export.DesktopExcelExporter
import com.example.desktop.export.DesktopPdfAndPrintEngine
import com.example.shared.engine.SharedFinancialEngine
import com.example.shared.model.*
import com.example.shared.util.SharedCurrencyFormatter
import com.example.shared.util.SharedPakistanTime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID

enum class DesktopNavSection(val label: String) {
    DASHBOARD("Dashboard"),
    SHOPS("Shops & Ledger"),
    CREDIT_RECOVERY("Credit & Recovery"),
    BOOKERS("Bookers"),
    PRODUCTS_BOOKINGS("Inventory & Bookings"),
    REPORTS("Reports & Exports"),
    STAFF("Staff & Permissions"),
    CHAT("Team Chat"),
    OFFLINE_SYNC("Sync & Conflicts"),
    AUDIT_NOTIFICATIONS("Activity & Alerts"),
    SETTINGS("Desktop Settings"),
    PLATFORM_OWNER("Super Admin Owner")
}

data class DesktopUiState(
    val isInitializing: Boolean = true,
    val isBusy: Boolean = false,
    val isOnline: Boolean = false,
    val isSyncing: Boolean = false,
    val firebaseSession: DesktopFirebaseAuthSession? = null,
    val activeSession: ActiveWorkspaceSession? = null,
    val ownerSession: PlatformOwnerSession? = null,
    val bookmarks: List<UserWorkspaceBookmark> = emptyList(),
    val business: Business? = null,
    val settings: BusinessSettings? = null,
    val permission: StaffPermission? = null,
    val allPermissions: List<StaffPermission> = emptyList(),
    val staffUsers: List<BusinessUser> = emptyList(),
    val bookers: List<Booker> = emptyList(),
    val shops: List<Shop> = emptyList(),
    val shopBalances: List<ShopBalanceSummary> = emptyList(),
    val bookerSummaries: List<BookerBalanceSummary> = emptyList(),
    val dashboardSummary: BusinessDashboardSummary? = null,
    val products: List<ProductItem> = emptyList(),
    val bookings: List<BookingOrder> = emptyList(),
    val credits: List<CreditTransaction> = emptyList(),
    val recoveries: List<RecoveryTransaction> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val chatGroups: List<ChatGroup> = emptyList(),
    val selectedChatGroupId: String? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    val pendingSync: List<PendingSyncTransaction> = emptyList(),
    val syncHistory: List<SyncHistoryRecord> = emptyList(),
    val lastSyncedAtMillis: Long = 0L,
    val sqliteIntegrityStatus: String = "Pending Check",
    val selectedSection: DesktopNavSection = DesktopNavSection.DASHBOARD,
    val selectedShopForLedger: Shop? = null,
    val ownerBusinesses: List<Business> = emptyList(),
    val statusBanner: String? = null,
    val errorBanner: String? = null,
    val lastGeneratedCredentials: GeneratedCredentials? = null,
    val autoSyncEnabled: Boolean = true
)

class DesktopAppState(
    private val repo: DesktopWorkspaceRepository,
    private val scope: CoroutineScope
) {
    private val _ui = MutableStateFlow(DesktopUiState())
    val ui: StateFlow<DesktopUiState> = _ui.asStateFlow()

    private var syncJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            val savedAuth = repo.authClient.restoreSavedSession()
            val savedWorkspace = repo.restoreWorkspaceSession()
            val savedOwner = repo.restoreOwnerSession()
            val integrity = runCatching { repo.db.checkIntegrity() }.getOrDefault("error")
            val autoSyncPref = repo.db.getPreference("auto_sync", "true") == "true"

            _ui.update {
                it.copy(
                    isInitializing = false,
                    firebaseSession = savedAuth,
                    activeSession = savedWorkspace,
                    ownerSession = savedOwner,
                    sqliteIntegrityStatus = integrity,
                    autoSyncEnabled = autoSyncPref
                )
            }
            if (savedWorkspace != null) {
                reloadFromSqlite(savedWorkspace)
            }
            startBackgroundSyncLoop()
        }
    }

    private fun startBackgroundSyncLoop() {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val online = repo.isNetworkAvailable()
                val session = _ui.value.activeSession
                val hasAuth = _ui.value.firebaseSession != null
                _ui.update { it.copy(isOnline = online) }

                if (online && hasAuth && session != null && _ui.value.autoSyncEnabled) {
                    runSyncNow(quiet = true)
                }
                delay(12_000L)
            }
        }
    }

    fun selectSection(section: DesktopNavSection) {
        _ui.update { it.copy(selectedSection = section, errorBanner = null) }
    }

    fun openShopLedger(shop: Shop?) {
        _ui.update {
            it.copy(
                selectedShopForLedger = shop,
                selectedSection = if (shop != null) DesktopNavSection.SHOPS else it.selectedSection
            )
        }
    }

    fun clearBanners() {
        _ui.update { it.copy(statusBanner = null, errorBanner = null, lastGeneratedCredentials = null) }
    }

    fun signInWithGoogleBrowser() = launchBusy {
        val auth = repo.authClient.signInWithGoogleBrowser().getOrThrow()
        val bookmarks = repo.fetchUserBookmarks(auth.uid).getOrDefault(emptyList())
        _ui.update {
            it.copy(
                firebaseSession = auth,
                bookmarks = bookmarks,
                isOnline = true,
                statusBanner = "Google Account Verified: ${auth.email}"
            )
        }
    }

    fun signInWithGoogleIdToken(idToken: String) = launchBusy {
        val auth = repo.authClient.signInWithGoogleIdToken(idToken.trim()).getOrThrow()
        val bookmarks = repo.fetchUserBookmarks(auth.uid).getOrDefault(emptyList())
        _ui.update {
            it.copy(
                firebaseSession = auth,
                bookmarks = bookmarks,
                isOnline = true,
                statusBanner = "Google Account Verified: ${auth.email}"
            )
        }
    }

    fun refreshBookmarks() = launchBusy {
        val auth = _ui.value.firebaseSession ?: return@launchBusy
        val bookmarks = repo.fetchUserBookmarks(auth.uid).getOrThrow()
        _ui.update { it.copy(bookmarks = bookmarks) }
    }

    fun loginWorkspace(businessCode: String, username: String, password: String) = launchBusy {
        val session = repo.loginWorkspace(businessCode, username, password).getOrThrow()
        reloadFromSqlite(session)
        _ui.update {
            it.copy(
                activeSession = session,
                lastSyncedAtMillis = SharedPakistanTime.nowMillis(),
                selectedSection = DesktopNavSection.DASHBOARD,
                statusBanner = "Signed in to ${session.businessName} (${session.role.label})"
            )
        }
    }

    fun createBusinessAdmin(
        fullName: String,
        businessName: String,
        username: String,
        password: String,
        mobile: String,
        email: String
    ) = launchBusy {
        val session = repo.createBusinessAndSuperAdmin(fullName, businessName, username, password, mobile, email).getOrThrow()
        reloadFromSqlite(session)
        _ui.update {
            it.copy(
                activeSession = session,
                lastSyncedAtMillis = SharedPakistanTime.nowMillis(),
                selectedSection = DesktopNavSection.DASHBOARD,
                statusBanner = "Business ${session.businessName} (${session.businessCode}) created & synced!"
            )
        }
    }

    fun loginPlatformOwner(username: String, password: String) = launchBusy {
        val owner = repo.loginPlatformOwner(username, password).getOrThrow()
        val list = repo.loadAllPlatformBusinesses().getOrThrow()
        _ui.update {
            it.copy(
                ownerSession = owner,
                ownerBusinesses = list,
                selectedSection = DesktopNavSection.PLATFORM_OWNER,
                statusBanner = "Platform Owner signed in (${owner.username})"
            )
        }
    }

    fun toggleBusinessStatusAsOwner(business: Business, suspend: Boolean, reason: String) = launchBusy {
        repo.setBusinessSuspended(business, suspend, reason).getOrThrow()
        val list = repo.loadAllPlatformBusinesses().getOrThrow()
        _ui.update {
            it.copy(
                ownerBusinesses = list,
                statusBanner = "${business.businessName} marked as ${if (suspend) "SUSPENDED" else "ACTIVE"}."
            )
        }
    }

    fun logoutWorkspace() {
        repo.clearWorkspaceSession()
        _ui.update {
            it.copy(
                activeSession = null,
                business = null,
                shops = emptyList(),
                shopBalances = emptyList(),
                credits = emptyList(),
                recoveries = emptyList(),
                selectedShopForLedger = null,
                statusBanner = "Workspace signed out."
            )
        }
    }

    fun logoutAll() {
        repo.clearWorkspaceSession()
        repo.clearOwnerSession()
        repo.authClient.clearSession()
        _ui.update {
            DesktopUiState(
                isInitializing = false,
                sqliteIntegrityStatus = it.sqliteIntegrityStatus,
                autoSyncEnabled = it.autoSyncEnabled,
                statusBanner = "Signed out completely."
            )
        }
    }

    fun runSyncNow(quiet: Boolean = false) {
        val session = _ui.value.activeSession ?: return
        if (_ui.value.isSyncing) return
        scope.launch(Dispatchers.IO) {
            _ui.update { it.copy(isSyncing = true) }
            val result = repo.pullAllBusinessData(session)
            val err = result.exceptionOrNull()
            if (err is SecurityException) {
                _ui.update {
                    it.copy(
                        isSyncing = false,
                        activeSession = null,
                        errorBanner = err.message ?: "Session revoked. Please sign in again."
                    )
                }
                return@launch
            }
            reloadFromSqlite(session)
            val integrity = runCatching { repo.db.checkIntegrity() }.getOrDefault("error")
            _ui.update { state ->
                if (result.isSuccess) {
                    val res = result.getOrThrow()
                    state.copy(
                        isSyncing = false,
                        isOnline = true,
                        sqliteIntegrityStatus = integrity,
                        lastSyncedAtMillis = SharedPakistanTime.nowMillis(),
                        statusBanner = if (!quiet) {
                            "Cloud sync complete: ${res.syncedCount} uploaded, ${res.conflictCount} conflicts, ${res.failedCount} failed."
                        } else state.statusBanner
                    )
                } else {
                    state.copy(
                        isSyncing = false,
                        sqliteIntegrityStatus = integrity,
                        errorBanner = if (!quiet) result.exceptionOrNull()?.message else state.errorBanner
                    )
                }
            }
        }
    }

    fun addCredit(
        shop: Shop,
        amountInput: String,
        txDateMillis: Long,
        dueDateMillis: Long,
        invoiceNumber: String,
        description: String
    ) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        val paisa = SharedCurrencyFormatter.parsePkrToPaisa(amountInput)
            ?: throw IllegalArgumentException("Please enter a valid PKR amount.")
        repo.syncManager.queueOfflineCredit(session, shop, paisa, txDateMillis, dueDateMillis, invoiceNumber, description).getOrThrow()
        if (_ui.value.isOnline) {
            repo.pullAllBusinessData(session)
        }
        reloadFromSqlite(session)
        _ui.update { it.copy(statusBanner = "Credit saved (${if (it.isOnline) "Synced" else "Saved Offline to SQLite"}). ") }
    }

    fun addRecovery(
        shop: Shop,
        amountInput: String,
        txDateMillis: Long,
        method: PaymentMethod,
        referenceNumber: String,
        notes: String
    ) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        val paisa = SharedCurrencyFormatter.parsePkrToPaisa(amountInput)
            ?: throw IllegalArgumentException("Please enter a valid PKR amount.")
        val currentOutstanding = _ui.value.shopBalances.firstOrNull { it.shop.id == shop.id }?.outstandingPaisa ?: 0L
        val allowOver = _ui.value.settings?.allowOverRecovery ?: _ui.value.business?.allowOverRecovery ?: false
        repo.syncManager.queueOfflineRecovery(
            session = session,
            shop = shop,
            amountPaisa = paisa,
            txDateMillis = txDateMillis,
            paymentMethod = method,
            referenceNumber = referenceNumber,
            notes = notes,
            currentShopOutstandingPaisa = currentOutstanding,
            allowOverRecovery = allowOver
        ).getOrThrow()
        if (_ui.value.isOnline) {
            repo.pullAllBusinessData(session)
        }
        reloadFromSqlite(session)
        _ui.update { it.copy(statusBanner = "Recovery saved (${if (it.isOnline) "Synced" else "Offline SQLite Queue"}). ") }
    }

    fun reverseRecovery(original: RecoveryTransaction, reason: String) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        repo.reverseRecovery(session, original, reason).getOrThrow()
        reloadFromSqlite(session)
        _ui.update { it.copy(statusBanner = "Recovery ${original.txCode} reversed safely with audit record.") }
    }

    fun reverseCredit(original: CreditTransaction, reason: String) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        repo.reverseCredit(session, original, reason).getOrThrow()
        reloadFromSqlite(session)
        _ui.update { it.copy(statusBanner = "Credit ${original.txCode} reversed safely with audit record.") }
    }

    fun resolveConflictKeepServer(item: PendingSyncTransaction) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        repo.syncManager.resolveConflictDiscardLocal(item)
        if (_ui.value.isOnline) repo.pullAllBusinessData(session)
        reloadFromSqlite(session)
        _ui.update { it.copy(statusBanner = "Conflict ${item.localId} resolved by keeping server record.") }
    }

    fun retryPendingItem(localId: String) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        repo.syncManager.requeueFailedOrConflictedItem(localId)
        if (_ui.value.isOnline) repo.pullAllBusinessData(session)
        reloadFromSqlite(session)
        _ui.update { it.copy(statusBanner = "Re-queued $localId for synchronization.") }
    }

    fun changePassword(currentPassword: String, newPassword: String) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        val updated = repo.changeWorkspacePassword(session, currentPassword, newPassword).getOrThrow()
        _ui.update {
            it.copy(
                activeSession = updated,
                statusBanner = "Password updated successfully across cloud and devices."
            )
        }
    }

    fun updateBusinessSettings(
        businessName: String,
        businessAddress: String,
        contactPhone: String,
        allowOverRecovery: Boolean,
        requireInvoiceNumber: Boolean
    ) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        repo.updateBusinessSettings(session, businessName, businessAddress, contactPhone, allowOverRecovery, requireInvoiceNumber).getOrThrow()
        repo.pullAllBusinessData(session)
        reloadFromSqlite(session)
        _ui.update { it.copy(statusBanner = "Business settings updated and synced.") }
    }

    fun markNotificationRead(notificationId: String) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        repo.markNotificationRead(session, notificationId).getOrThrow()
        repo.pullAllBusinessData(session)
        reloadFromSqlite(session)
    }

    fun addShop(
        shopName: String,
        ownerName: String,
        mobile: String,
        address: String,
        city: String,
        booker: Booker,
        openingCreditInput: String,
        notes: String
    ) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        val openingPaisa = if (openingCreditInput.isBlank()) 0L else {
            SharedCurrencyFormatter.parsePkrToPaisa(openingCreditInput)
                ?: throw IllegalArgumentException("Invalid opening balance amount.")
        }
        repo.createShop(session, shopName, ownerName, mobile, address, city, booker, openingPaisa, notes).getOrThrow()
        repo.pullAllBusinessData(session)
        reloadFromSqlite(session)
        _ui.update { it.copy(statusBanner = "Shop $shopName added and synced.") }
    }

    fun addBooker(fullName: String, username: String, mobile: String, address: String) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        val creds = repo.createBooker(session, fullName, username, mobile, address).getOrThrow()
        repo.pullAllBusinessData(session)
        reloadFromSqlite(session)
        _ui.update { it.copy(lastGeneratedCredentials = creds, statusBanner = "Booker ${creds.fullName} created.") }
    }

    fun addStaff(fullName: String, username: String, mobile: String, email: String, perms: StaffPermission) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        val creds = repo.createStaffUser(session, fullName, username, mobile, email, perms).getOrThrow()
        repo.pullAllBusinessData(session)
        reloadFromSqlite(session)
        _ui.update { it.copy(lastGeneratedCredentials = creds, statusBanner = "Staff ${creds.fullName} created.") }
    }

    fun updatePermission(permission: StaffPermission) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        repo.updateStaffPermission(session, permission).getOrThrow()
        repo.pullAllBusinessData(session)
        reloadFromSqlite(session)
        _ui.update { it.copy(statusBanner = "Staff permissions updated.") }
    }

    fun addProduct(sku: String, name: String, unit: String, priceInput: String, stockQty: Int, reorder: Int) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        val price = SharedCurrencyFormatter.parsePkrToPaisa(priceInput)
            ?: throw IllegalArgumentException("Invalid unit price.")
        repo.createOrUpdateProduct(session, sku, name, unit, price, stockQty, reorder).getOrThrow()
        reloadFromSqlite(session)
        _ui.update { it.copy(statusBanner = "Product $name saved to shared catalog.") }
    }

    fun createBooking(shop: Shop, product: ProductItem, quantity: Int, notes: String) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        repo.syncManager.queueOfflineBooking(session, shop, product, quantity, notes).getOrThrow()
        if (_ui.value.isOnline) repo.pullAllBusinessData(session)
        reloadFromSqlite(session)
        _ui.update { it.copy(statusBanner = "Booking created for ${shop.shopName} (${product.name} x $quantity).") }
    }

    fun openChatGroup(group: ChatGroup) = launchBusy {
        _ui.update { it.copy(selectedChatGroupId = group.id) }
        val msgs = if (_ui.value.isOnline) {
            repo.fetchChatMessages(group.businessId, group.id).getOrElse { repo.db.getChatMessages(group.id) }
        } else {
            repo.db.getChatMessages(group.id)
        }
        _ui.update { it.copy(chatMessages = msgs) }
    }

    fun sendChatMessage(group: ChatGroup, text: String) = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active session.")
        repo.sendChatMessage(session, group, text).getOrThrow()
        val msgs = repo.fetchChatMessages(session.businessId, group.id).getOrDefault(emptyList())
        _ui.update { it.copy(chatMessages = msgs) }
    }

    fun toggleAutoSync(enabled: Boolean) {
        repo.db.setPreference("auto_sync", enabled.toString())
        _ui.update { it.copy(autoSyncEnabled = enabled) }
    }

    fun exportShopLedgerPdf(shop: Shop, startMillis: Long, endMillis: Long, printNow: Boolean = false) = launchBusy {
        val bizName = _ui.value.business?.businessName ?: _ui.value.activeSession?.businessName ?: "HisabPro"
        val defaultName = "Supplier_Ledger_${shop.shopUid}_${SharedPakistanTime.formatDashDate()}.pdf"
        val outFile = DesktopPdfAndPrintEngine.chooseSaveTarget(defaultName)
            ?: File(File(System.getProperty("user.home"), "Downloads/HisabPro"), defaultName)
        DesktopPdfAndPrintEngine.generateShopSupplierLedgerPdf(
            outputFile = outFile,
            businessName = bizName,
            businessSettings = _ui.value.settings,
            shop = shop,
            credits = _ui.value.credits,
            recoveries = _ui.value.recoveries,
            startMillis = startMillis,
            endMillis = endMillis
        )
        if (printNow) {
            DesktopPdfAndPrintEngine.printPdfWithDialog(outFile).getOrThrow()
        } else {
            DesktopPdfAndPrintEngine.openFileInDesktopViewer(outFile)
        }
        _ui.update { it.copy(statusBanner = "PDF saved: ${outFile.absolutePath}") }
    }

    fun exportShopLedgerExcel(shop: Shop, startMillis: Long, endMillis: Long) = launchBusy {
        val bizName = _ui.value.business?.businessName ?: _ui.value.activeSession?.businessName ?: "HisabPro"
        val defaultName = "Supplier_Ledger_${shop.shopUid}_${SharedPakistanTime.formatDashDate()}.xlsx"
        val outFile = DesktopPdfAndPrintEngine.chooseSaveTarget(defaultName)
            ?: File(File(System.getProperty("user.home"), "Downloads/HisabPro"), defaultName)
        DesktopExcelExporter.exportShopLedgerXlsx(
            outputFile = outFile,
            businessName = bizName,
            shop = shop,
            credits = _ui.value.credits,
            recoveries = _ui.value.recoveries,
            startMillis = startMillis,
            endMillis = endMillis
        )
        DesktopPdfAndPrintEngine.openFileInDesktopViewer(outFile)
        _ui.update { it.copy(statusBanner = "Excel exported: ${outFile.absolutePath}") }
    }

    fun exportAllShopsExcel() = launchBusy {
        val bizName = _ui.value.business?.businessName ?: _ui.value.activeSession?.businessName ?: "HisabPro"
        val defaultName = "HisabPro_All_Shops_${SharedPakistanTime.formatDashDate()}.xlsx"
        val outFile = DesktopPdfAndPrintEngine.chooseSaveTarget(defaultName)
            ?: File(File(System.getProperty("user.home"), "Downloads/HisabPro"), defaultName)
        DesktopExcelExporter.exportAllShopsSummaryXlsx(outFile, bizName, _ui.value.shopBalances)
        DesktopPdfAndPrintEngine.openFileInDesktopViewer(outFile)
        _ui.update { it.copy(statusBanner = "All Shops Excel saved: ${outFile.absolutePath}") }
    }

    fun exportJsonBackup() = launchBusy {
        val biz = _ui.value.business ?: Business(
            id = _ui.value.activeSession?.businessId.orEmpty(),
            businessName = _ui.value.activeSession?.businessName ?: "HisabPro",
            businessCode = _ui.value.activeSession?.businessCode.orEmpty()
        )
        val defaultName = "HisabPro_Backup_${biz.businessCode}_${System.currentTimeMillis()}.json"
        val outFile = DesktopPdfAndPrintEngine.chooseSaveTarget(defaultName)
            ?: File(File(System.getProperty("user.home"), "Downloads/HisabPro"), defaultName)
        DesktopExcelExporter.exportWorkspaceBackupJson(
            outputFile = outFile,
            business = biz,
            bookers = _ui.value.bookers,
            staffUsers = _ui.value.staffUsers,
            shops = _ui.value.shops,
            credits = _ui.value.credits,
            recoveries = _ui.value.recoveries,
            auditLogs = _ui.value.auditLogs,
            products = _ui.value.products,
            bookings = _ui.value.bookings
        )
        _ui.update { it.copy(statusBanner = "JSON Backup created: ${outFile.absolutePath}") }
    }

    fun exportShopLedgerCsv(shop: Shop, startMillis: Long, endMillis: Long) = launchBusy {
        val defaultName = "Supplier_Ledger_${shop.shopUid}_${SharedPakistanTime.formatDashDate()}.csv"
        val outFile = DesktopPdfAndPrintEngine.chooseSaveTarget(defaultName)
            ?: File(File(System.getProperty("user.home"), "Downloads/HisabPro"), defaultName)
        DesktopExcelExporter.exportShopLedgerCsv(
            outputFile = outFile,
            shop = shop,
            credits = _ui.value.credits,
            recoveries = _ui.value.recoveries,
            startMillis = startMillis,
            endMillis = endMillis
        )
        DesktopPdfAndPrintEngine.openFileInDesktopViewer(outFile)
        _ui.update { it.copy(statusBanner = "CSV exported: ${outFile.absolutePath}") }
    }

    fun backupSqliteDatabase() = launchBusy {
        val defaultName = "hisabpro_sqlite_backup_${SharedPakistanTime.formatDashDate()}.db"
        val outFile = DesktopPdfAndPrintEngine.chooseSaveTarget(defaultName)
            ?: File(File(System.getProperty("user.home"), "Downloads/HisabPro"), defaultName)
        repo.db.backupSqliteDatabaseTo(outFile)
        _ui.update { it.copy(statusBanner = "SQLite database backed up to ${outFile.absolutePath}") }
    }

    fun restoreSqliteDatabase() = launchBusy {
        val selected = DesktopPdfAndPrintEngine.chooseOpenTarget("Select HisabPro SQLite Backup (.db)")
            ?: throw IllegalStateException("No backup file selected.")
        repo.db.restoreSqliteDatabaseFrom(selected).getOrThrow()
        val integrity = runCatching { repo.db.checkIntegrity() }.getOrDefault("error")
        _ui.value.activeSession?.let { reloadFromSqlite(it) }
        _ui.update {
            it.copy(
                sqliteIntegrityStatus = integrity,
                statusBanner = "SQLite database restored and verified ($integrity) from ${selected.name}."
            )
        }
    }

    fun clearLocalCacheSafely() = launchBusy {
        val session = _ui.value.activeSession ?: throw IllegalStateException("No active workspace.")
        repo.db.clearBusinessCacheOnlyIfSafe(session.businessId).getOrThrow()
        reloadFromSqlite(session)
        _ui.update { it.copy(statusBanner = "Local temporary cache cleared safely (no unsynced data was lost).") }
    }

    private fun reloadFromSqlite(session: ActiveWorkspaceSession) {
        val bizId = session.businessId
        val bookerFilter = if (session.role == AccountRole.BOOKER) session.bookerId else null

        val biz = repo.db.getBusiness(bizId)
        val settings = repo.db.getSettings(bizId)
        val users = repo.db.getStaffUsers(bizId)
        val allPerms = repo.db.getPermissions(bizId)
        val myPerm = when (session.role) {
            AccountRole.SUPER_ADMIN -> StaffPermission.fullAdmin(bizId, session.accountId)
            AccountRole.STAFF -> allPerms.firstOrNull { it.userId == session.accountId } ?: StaffPermission(userId = session.accountId, businessId = bizId)
            AccountRole.BOOKER -> StaffPermission.bookerReadOnly(bizId, session.bookerId)
        }
        val bookers = repo.db.getBookers(bizId)
        val shops = repo.db.getShops(bizId, bookerFilter)
        val credits = repo.db.getCredits(bizId, bookerFilter)
        val recoveries = repo.db.getRecoveries(bizId, bookerFilter)
        val products = repo.db.getProducts(bizId)
        val bookings = repo.db.getBookings(bizId, bookerFilter)
        val audits = repo.db.getAuditLogs(bizId)
        val notifs = repo.db.getNotifications(bizId)
        val groups = repo.db.getChatGroups(bizId)
        val pending = repo.db.getPendingSyncItems(bizId)
        val history = repo.db.getSyncHistory(bizId)

        val shopBalances = SharedFinancialEngine.computeShopBalances(shops, credits, recoveries)
        val bookerSummaries = SharedFinancialEngine.computeBookerSummaries(bookers, shopBalances)
        val dash = SharedFinancialEngine.computeDashboardSummary(shopBalances, bookers, users)

        _ui.update {
            it.copy(
                business = biz,
                settings = settings,
                permission = myPerm,
                allPermissions = allPerms,
                staffUsers = users,
                bookers = bookers,
                shops = shops,
                shopBalances = shopBalances,
                bookerSummaries = bookerSummaries,
                dashboardSummary = dash,
                products = products,
                bookings = bookings,
                credits = credits,
                recoveries = recoveries,
                auditLogs = audits,
                notifications = notifs,
                chatGroups = groups,
                pendingSync = pending,
                syncHistory = history
            )
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        scope.launch(Dispatchers.IO) {
            _ui.update { it.copy(isBusy = true, errorBanner = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(errorBanner = e.message ?: "Operation failed.") }
            } finally {
                _ui.update { it.copy(isBusy = false) }
            }
        }
    }
}
