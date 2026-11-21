package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.HisabProRepository
import com.example.data.firebase.SessionPreferences
import com.example.data.local.OfflineSyncManager
import com.example.domain.model.AccountRole
import com.example.domain.model.ActiveWorkspaceSession
import com.example.domain.model.AuditLog
import com.example.domain.model.Booker
import com.example.domain.model.BookerBalanceSummary
import com.example.domain.model.Business
import com.example.domain.model.BusinessDashboardSummary
import com.example.domain.model.BusinessSettings
import com.example.domain.model.BusinessUser
import com.example.domain.model.ChatGroup
import com.example.domain.model.ChatMessage
import com.example.domain.model.ChatMessageType
import com.example.domain.model.CreditTransaction
import com.example.domain.model.DailyPerformanceReport
import com.example.domain.model.FinancialEngine
import com.example.domain.model.GeneratedCredentials
import com.example.domain.model.NotificationItem
import com.example.domain.model.PaymentMethod
import com.example.domain.model.PendingSyncTransaction
import com.example.domain.model.PeriodReportSummary
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.Shop
import com.example.domain.model.ShopBalanceSummary
import com.example.domain.model.StaffPermission
import com.example.domain.model.SyncHistoryRecord
import com.example.domain.model.UserWorkspaceBookmark
import com.example.domain.model.WorkspaceMember
import com.example.util.CurrencyFormatter
import com.example.util.DataExporter
import com.example.util.NetworkMonitor
import com.example.util.PakistanTime
import com.example.util.PdfGenerator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PdfExportMode {
    VIEW,
    SHARE,
    DOWNLOAD
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val repository: HisabProRepository,
    private val sessionPrefs: SessionPreferences,
    private val networkMonitor: NetworkMonitor,
    private val googleUid: String,
    private val offlineSyncManager: OfflineSyncManager? = null,
    private val deviceId: String = ""
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _actionMessage = MutableStateFlow<Pair<Boolean, String>?>(null)
    val actionMessage: StateFlow<Pair<Boolean, String>?> = _actionMessage.asStateFlow()

    private val _generatedCredentials = MutableStateFlow<GeneratedCredentials?>(null)
    val generatedCredentials: StateFlow<GeneratedCredentials?> = _generatedCredentials.asStateFlow()

    private val _latestReceipt = MutableStateFlow<Pair<RecoveryTransaction, Long>?>(null)
    val latestReceipt: StateFlow<Pair<RecoveryTransaction, Long>?> = _latestReceipt.asStateFlow()

    private val _streamError = MutableStateFlow<String?>(null)
    val streamError: StateFlow<String?> = _streamError.asStateFlow()

    private val _pakistanClockMillis = MutableStateFlow(PakistanTime.nowMillis())
    val pakistanClockMillis: StateFlow<Long> = _pakistanClockMillis.asStateFlow()

    private val _locallyReadNotificationIds = MutableStateFlow<Set<String>>(emptySet())

    val isOnline: StateFlow<Boolean> = networkMonitor.observeIsOnline()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), networkMonitor.isCurrentlyOnline())

    val savedWorkspaces: StateFlow<List<UserWorkspaceBookmark>> =
        repository.observeUserWorkspaces(googleUid)
            .safeCatch("workspaces") { emptyList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val sessionInfo = sessionPrefs.observeSessionInfo(googleUid)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeSession: StateFlow<ActiveWorkspaceSession?> = sessionInfo
        .map { it?.session }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sessionExpiresAtMillis: StateFlow<Long> = sessionInfo
        .map { it?.expiresAtMillis ?: 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0L)

    private val readySession: Flow<ActiveWorkspaceSession?> = activeSession

    val business: StateFlow<Business?> = readySession.flatMapLatest { sess ->
        if (sess == null) flowOf(null)
        else repository.observeBusiness(sess.businessId).safeCatch("business") { null }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val settings: StateFlow<BusinessSettings?> = readySession.flatMapLatest { sess ->
        if (sess == null) flowOf(null)
        else repository.observeBusinessSettings(sess.businessId).safeCatch("settings") { null }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val currentMember: StateFlow<WorkspaceMember?> = readySession.flatMapLatest { sess ->
        if (sess == null) flowOf(null)
        else repository.observeCurrentMember(sess.businessId, googleUid).safeCatch("member") { null }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val currentPermissions: StateFlow<StaffPermission> = readySession.flatMapLatest { sess ->
        when {
            sess == null -> flowOf(StaffPermission.bookerReadOnly("", ""))
            sess.role == AccountRole.SUPER_ADMIN ->
                flowOf(StaffPermission.fullAdmin(sess.businessId, sess.accountId))
            sess.role == AccountRole.BOOKER ->
                flowOf(StaffPermission.bookerReadOnly(sess.businessId, sess.bookerId))
            else -> repository.observePermissionForUser(sess.businessId, sess.accountId)
                .map { it ?: StaffPermission(businessId = sess.businessId, userId = sess.accountId) }
                .safeCatch("permissions") { StaffPermission(businessId = sess.businessId, userId = sess.accountId) }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        StaffPermission.bookerReadOnly("", "")
    )

    val bookers: StateFlow<List<Booker>> = readySession.flatMapLatest { sess ->
        when {
            sess == null -> flowOf(emptyList())
            sess.role == AccountRole.BOOKER ->
                repository.observeSingleBooker(sess.businessId, sess.bookerId)
                    .map { listOfNotNull(it) }
                    .safeCatch("booker") { emptyList() }
            else -> repository.observeBookers(sess.businessId).safeCatch("bookers") { emptyList() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val staffUsers: StateFlow<List<BusinessUser>> = readySession.flatMapLatest { sess ->
        if (sess == null || sess.role == AccountRole.BOOKER) flowOf(emptyList())
        else repository.observeStaffUsers(sess.businessId).safeCatch("users") { emptyList() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allPermissions: StateFlow<List<StaffPermission>> = readySession.flatMapLatest { sess ->
        if (sess == null || sess.role == AccountRole.BOOKER) flowOf(emptyList())
        else repository.observeAllPermissions(sess.businessId).safeCatch("all_permissions") { emptyList() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val shops: StateFlow<List<Shop>> = readySession.flatMapLatest { sess ->
        if (sess == null) flowOf(emptyList())
        else repository.observeShops(sess.businessId, sess.role, sess.bookerId).safeCatch("shops") { emptyList() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val creditTransactions: StateFlow<List<CreditTransaction>> = readySession.flatMapLatest { sess ->
        if (sess == null) flowOf(emptyList())
        else repository.observeCreditTransactions(sess.businessId, sess.role, sess.bookerId)
            .safeCatch("credits") { emptyList() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val recoveryTransactions: StateFlow<List<RecoveryTransaction>> = readySession.flatMapLatest { sess ->
        if (sess == null) flowOf(emptyList())
        else repository.observeRecoveryTransactions(sess.businessId, sess.role, sess.bookerId)
            .safeCatch("recoveries") { emptyList() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = combine(readySession, currentPermissions) { sess, perm ->
        sess to perm
    }.flatMapLatest { (sess, perm) ->
        if (sess == null || sess.role == AccountRole.BOOKER || (!perm.viewActivityLogs && sess.role != AccountRole.SUPER_ADMIN)) {
            flowOf(emptyList())
        } else {
            repository.observeAuditLogs(sess.businessId).safeCatch("audit_logs") { emptyList() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val persistedReadNotificationIds: Flow<Set<String>> = readySession.flatMapLatest { sess ->
        if (sess == null) flowOf(emptySet())
        else sessionPrefs.observeLocallyReadNotificationIds(sess.businessId, sess.accountId)
    }

    val notifications: StateFlow<List<NotificationItem>> = combine(
        readySession.flatMapLatest { sess ->
            if (sess == null) flowOf(emptyList())
            else repository.observeNotifications(sess.businessId, sess.role, sess.bookerId)
                .safeCatch("notifications") { emptyList() }
        },
        _locallyReadNotificationIds,
        persistedReadNotificationIds
    ) { list, memRead, diskRead ->
        val allRead = memRead + diskRead
        list.map { item ->
            if (item.isRead || item.id in allRead) item.copy(isRead = true) else item
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val unreadNotificationCount: StateFlow<Int> = notifications
        .map { list -> list.count { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val chatGroups: StateFlow<List<ChatGroup>> = readySession.flatMapLatest { sess ->
        if (sess == null) flowOf(emptyList())
        else repository.observeChatGroups(sess.businessId, sess.role, sess.accountId)
            .safeCatch("chat_groups") { emptyList() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun observeChatMessages(groupId: String): Flow<List<ChatMessage>> =
        readySession.flatMapLatest { sess ->
            if (sess == null || groupId.isBlank()) flowOf(emptyList())
            else repository.observeChatMessages(sess.businessId, groupId)
                .safeCatch("chat_messages") { emptyList() }
        }

    val shopBalances: StateFlow<List<ShopBalanceSummary>> = combine(
        shops,
        creditTransactions,
        recoveryTransactions
    ) { shopList, crList, rcList ->
        FinancialEngine.computeShopBalances(shopList, crList, rcList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val bookerSummaries: StateFlow<List<BookerBalanceSummary>> = combine(
        bookers,
        shopBalances
    ) { bList, sbList ->
        FinancialEngine.computeBookerSummaries(bList, sbList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val dashboardSummary: StateFlow<BusinessDashboardSummary> = combine(
        shopBalances,
        bookers,
        staffUsers
    ) { sbList, bList, sList ->
        FinancialEngine.computeDashboardSummary(sbList, bList, sList)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        BusinessDashboardSummary(0L, 0L, 0L, 0, 0, 0, 0)
    )

    val pendingSyncItems: StateFlow<List<PendingSyncTransaction>> = readySession.flatMapLatest { sess ->
        if (sess == null || offlineSyncManager == null) flowOf(emptyList())
        else offlineSyncManager.observeOutbox(sess.businessId).safeCatch("outbox") { emptyList() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val unsyncedCount: StateFlow<Int> = readySession.flatMapLatest { sess ->
        if (sess == null || offlineSyncManager == null) flowOf(0)
        else offlineSyncManager.observeUnsyncedCount(sess.businessId).safeCatch("unsynced_count") { 0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val syncHistory: StateFlow<List<SyncHistoryRecord>> = readySession.flatMapLatest { sess ->
        if (sess == null || offlineSyncManager == null) flowOf(emptyList())
        else offlineSyncManager.observeSyncHistory(sess.businessId).safeCatch("sync_history") { emptyList() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val hasUnsyncedData: StateFlow<Boolean> = unsyncedCount
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val dailyPerformanceReport: StateFlow<DailyPerformanceReport> = combine(
        pakistanClockMillis,
        shops,
        bookers,
        creditTransactions,
        recoveryTransactions,
        unsyncedCount
    ) { arr ->
        val clock = arr[0] as Long
        @Suppress("UNCHECKED_CAST")
        val sList = arr[1] as List<Shop>
        @Suppress("UNCHECKED_CAST")
        val bList = arr[2] as List<Booker>
        @Suppress("UNCHECKED_CAST")
        val crList = arr[3] as List<CreditTransaction>
        @Suppress("UNCHECKED_CAST")
        val rcList = arr[4] as List<RecoveryTransaction>
        val pendingCount = arr[5] as Int
        FinancialEngine.computeDailyPerformanceReport(
            shops = sList,
            bookers = bList,
            credits = crList,
            recoveries = rcList,
            dayMillis = clock
        ).let { rep ->
            if (pendingCount > 0 && !rep.hasUnsyncedData) rep.copy(hasUnsyncedData = true) else rep
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        DailyPerformanceReport(
            dateMillis = PakistanTime.nowMillis(),
            dateFormatted = PakistanTime.formatDate(),
            todayCreditPaisa = 0L,
            todayRecoveryPaisa = 0L,
            todayCreditCount = 0,
            todayRecoveryCount = 0,
            totalOutstandingPaisa = 0L,
            shopsReceivingCreditToday = emptyList(),
            shopsMakingRecoveryToday = emptyList(),
            bookerBreakdown = emptyList(),
            hasUnsyncedData = false
        )
    )

    init {
        viewModelScope.launch {
            while (true) {
                delay(15_000L)
                _pakistanClockMillis.value = PakistanTime.nowMillis()
            }
        }

        // Automatic background synchronization when network becomes available and pending items exist
        viewModelScope.launch {
            combine(isOnline, activeSession, unsyncedCount) { online, sess, pending ->
                Triple(online, sess, pending)
            }.distinctUntilChanged().collect { (online, sess, pending) ->
                if (online && sess != null && pending > 0 && offlineSyncManager != null && !_isSyncing.value) {
                    syncPendingInternal(sess, announceEmpty = false)
                }
            }
        }

        // Periodic verification of Owner revocation / company suspension / forced password change while online
        viewModelScope.launch {
            combine(isOnline, sessionInfo) { online, info -> online to info }
                .collect { (online, info) ->
                    if (online && info != null) {
                        val status = repository.validateActiveTicketSession(info.session, info.loginAtMillis)
                        if (!status.isValid) {
                            sessionPrefs.clearSession()
                            _actionMessage.value = true to status.invalidReason
                        } else if (status.mustChangePassword != info.session.mustChangePassword) {
                            sessionPrefs.saveSession(
                                googleUid,
                                info.session.copy(mustChangePassword = status.mustChangePassword),
                                loginAtMillis = info.loginAtMillis
                            )
                        }
                    }
                }
        }
    }

    // --- Helpers ---

    private fun <T> Flow<T>.safeCatch(tag: String, fallback: () -> T): Flow<T> = catch { error ->
        val fsError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsError == null && error is CancellationException) throw error
        val root = fsError ?: error
        Log.w(TAG, "Stream error on $tag", root)
        _streamError.value = root.localizedMessage ?: "Failed to sync $tag"
        emit(fallback())
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun clearCredentialsDialog() {
        _generatedCredentials.value = null
    }

    fun clearLatestReceipt() {
        _latestReceipt.value = null
    }

    fun notifyAction(isError: Boolean, message: String) {
        _actionMessage.value = isError to message
    }

    private fun requireOnlineForWrite(): Boolean {
        if (!isOnline.value) {
            _actionMessage.value = true to "No internet connection. Connect to the internet before saving financial changes."
            return false
        }
        return true
    }

    // --- Auth & Workspace Actions ---

    fun createAdministrationAccount(
        fullName: String,
        businessName: String,
        username: String,
        password: String,
        confirmPassword: String,
        mobileNumber: String,
        email: String
    ) {
        if (password != confirmPassword) {
            _actionMessage.value = true to "Password and Confirm Password do not match."
            return
        }
        if (!requireOnlineForWrite()) return

        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.createBusinessAndSuperAdmin(
                fullName = fullName,
                businessName = businessName,
                username = username,
                password = password,
                mobileNumber = mobileNumber,
                email = email
            )
            _isSubmitting.value = false
            result.onSuccess { session ->
                _generatedCredentials.value = null
                _latestReceipt.value = null
                _locallyReadNotificationIds.value = emptySet()
                sessionPrefs.saveSession(googleUid, session)
                repository.logWorkspaceEvent(
                    session = session,
                    action = "COMPANY_REGISTERED",
                    description = "Registered ${session.businessName} (${session.businessCode}) by @${session.username}"
                )
                _actionMessage.value = false to "Workspace created! Your Business Code is ${session.businessCode}"
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to create administration account.")
            }
        }
    }

    fun loginWorkspace(businessCode: String, username: String, password: String) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = if (isOnline.value) {
                repository.loginWithCredentials(businessCode, username, password)
            } else {
                repository.loginOfflineWithCachedCredentials(businessCode, username, password)
            }
            _isSubmitting.value = false
            result.onSuccess { session ->
                _generatedCredentials.value = null
                _latestReceipt.value = null
                _locallyReadNotificationIds.value = emptySet()
                sessionPrefs.saveSession(googleUid, session)
                if (isOnline.value) {
                    repository.logWorkspaceEvent(
                        session = session,
                        action = "LOGIN",
                        description = "Signed in (${session.role.key}) from device $deviceId"
                    )
                } else {
                    _actionMessage.value = false to "Signed in in Offline Mode using encrypted local credentials."
                }
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Login failed.")
            }
        }
    }

    fun completeFirstLoginPasswordChange(newPassword: String, confirmPassword: String) {
        val sess = activeSession.value ?: return
        if (newPassword != confirmPassword) {
            _actionMessage.value = true to "Passwords do not match."
            return
        }
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.completeFirstLoginPasswordChange(sess, newPassword)
            _isSubmitting.value = false
            result.onSuccess { updated ->
                sessionPrefs.saveSession(googleUid, updated)
                _actionMessage.value = false to "Password updated successfully."
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to update password.")
            }
        }
    }

    fun changeOwnPassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
        onDone: () -> Unit
    ) {
        val sess = activeSession.value ?: return
        if (newPassword != confirmPassword) {
            _actionMessage.value = true to "New password and confirm password do not match."
            return
        }
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.changeOwnPassword(sess, currentPassword, newPassword)
            _isSubmitting.value = false
            result.onSuccess { updated ->
                sessionPrefs.saveSession(googleUid, updated)
                _actionMessage.value = false to "Password updated securely."
                onDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to change password.")
            }
        }
    }

    fun resetOwnAdminPassword(
        businessCode: String,
        username: String,
        registeredMobile: String,
        newPassword: String,
        onSuccessDone: () -> Unit
    ) {
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.resetOwnAdminPassword(businessCode, username, registeredMobile, newPassword)
            _isSubmitting.value = false
            res.onSuccess {
                _actionMessage.value = false to "Admin password reset successfully. You can now log in."
                onSuccessDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to reset admin password.")
            }
        }
    }

    fun logoutWorkspace() {
        val sess = activeSession.value
        val online = isOnline.value
        viewModelScope.launch {
            if (sess != null && online) {
                repository.logWorkspaceEvent(
                    session = sess,
                    action = "LOGOUT",
                    description = "Signed out from device $deviceId"
                )
            }
            sessionPrefs.clearSession()
            _locallyReadNotificationIds.value = emptySet()
            _generatedCredentials.value = null
            _latestReceipt.value = null
            _isSubmitting.value = false
            _streamError.value = null
            _actionMessage.value = null
        }
    }

    // --- Booker Actions ---

    fun createBooker(
        fullName: String,
        username: String,
        temporaryPassword: String,
        mobileNumber: String,
        address: String,
        notes: String,
        assignedShopIds: List<String>,
        onSuccessDone: () -> Unit
    ) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.createBooker(
                session = sess,
                businessCode = sess.businessCode,
                businessName = business.value?.businessName ?: sess.businessName,
                fullName = fullName,
                username = username,
                temporaryPassword = temporaryPassword,
                mobileNumber = mobileNumber,
                address = address,
                notes = notes,
                assignedShopIds = assignedShopIds
            )
            _isSubmitting.value = false
            res.onSuccess { creds ->
                _generatedCredentials.value = creds
                onSuccessDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to create Booker.")
            }
        }
    }

    fun updateBookerProfile(
        booker: Booker,
        fullName: String,
        mobile: String,
        address: String,
        notes: String,
        onDone: () -> Unit
    ) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.updateBookerProfile(sess, booker, fullName, mobile, address, notes)
            _isSubmitting.value = false
            res.onSuccess {
                _actionMessage.value = false to "Booker profile updated."
                onDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Update failed.")
            }
        }
    }

    fun toggleBookerStatus(booker: Booker) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.toggleBookerStatus(sess, booker)
            _isSubmitting.value = false
            res.onSuccess {
                _actionMessage.value = false to "Booker status updated."
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to update status.")
            }
        }
    }

    fun resetBookerOrStaffPassword(
        accountId: String,
        accountCode: String,
        fullName: String,
        username: String,
        role: AccountRole,
        tempPassword: String
    ) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.resetAccountPassword(
                sess, accountId, accountCode, fullName, username, role, tempPassword
            )
            _isSubmitting.value = false
            res.onSuccess { creds ->
                _generatedCredentials.value = creds
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Password reset failed.")
            }
        }
    }

    // --- Staff Actions ---

    fun createStaffUser(
        fullName: String,
        username: String,
        temporaryPassword: String,
        mobileNumber: String,
        permissions: StaffPermission,
        onDone: () -> Unit
    ) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.createStaffUser(sess, fullName, username, temporaryPassword, mobileNumber, permissions)
            _isSubmitting.value = false
            res.onSuccess { creds ->
                _generatedCredentials.value = creds
                onDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to create Staff account.")
            }
        }
    }

    fun updateStaffPermissions(user: BusinessUser, permissions: StaffPermission, onDone: () -> Unit) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.updateStaffPermissions(sess, user, permissions)
            _isSubmitting.value = false
            res.onSuccess {
                _actionMessage.value = false to "Permissions saved for ${user.fullName}."
                onDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to update permissions.")
            }
        }
    }

    fun toggleStaffStatus(user: BusinessUser) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.toggleStaffStatus(sess, user)
            _isSubmitting.value = false
            res.onSuccess {
                _actionMessage.value = false to "Account status updated."
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Status update failed.")
            }
        }
    }

    // --- Shop Actions ---

    fun createShop(
        shopUid: String,
        shopName: String,
        booker: Booker,
        ownerName: String,
        mobileNumber: String,
        address: String,
        city: String,
        openingCreditPaisa: Long,
        openingDateMillis: Long,
        notes: String,
        onCreated: (Shop) -> Unit
    ) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.createShop(
                sess, shopUid, shopName, booker, ownerName, mobileNumber, address, city,
                openingCreditPaisa, openingDateMillis, notes
            )
            _isSubmitting.value = false
            res.onSuccess { created ->
                _actionMessage.value = false to "Shop ${created.shopName} (${created.shopUid}) saved."
                onCreated(created)
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to save shop.")
            }
        }
    }

    fun updateShop(
        shop: Shop,
        shopName: String,
        booker: Booker,
        ownerName: String,
        mobileNumber: String,
        address: String,
        city: String,
        notes: String,
        onDone: () -> Unit
    ) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.updateShop(sess, shop, shopName, booker, ownerName, mobileNumber, address, city, notes)
            _isSubmitting.value = false
            res.onSuccess {
                _actionMessage.value = false to "Shop ${shop.shopUid} updated."
                onDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to update shop.")
            }
        }
    }

    fun toggleArchiveShop(shop: Shop) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.toggleArchiveShop(sess, shop)
            _isSubmitting.value = false
            res.onSuccess {
                _actionMessage.value = false to if (shop.isArchived) "Shop restored." else "Shop archived."
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Operation failed.")
            }
        }
    }

    // --- Credit & Recovery Actions ---

    fun addCredit(
        shop: Shop,
        amountPaisa: Long,
        txDateMillis: Long,
        invoiceNumber: String,
        description: String,
        attachmentUri: String,
        dueDateMillis: Long = 0L,
        onDone: () -> Unit
    ) {
        val sess = activeSession.value ?: return
        viewModelScope.launch {
            _isSubmitting.value = true
            if (isOnline.value) {
                val res = repository.addCreditTransaction(
                    session = sess,
                    shop = shop,
                    amountPaisa = amountPaisa,
                    txDateMillis = txDateMillis,
                    invoiceNumber = invoiceNumber,
                    description = description,
                    attachmentUri = attachmentUri,
                    dueDateMillis = dueDateMillis,
                    deviceId = deviceId
                )
                _isSubmitting.value = false
                res.onSuccess { tx ->
                    _actionMessage.value = false to "Credit ${tx.txCode} synced to server."
                    onDone()
                }.onFailure { err ->
                    _actionMessage.value = true to (err.localizedMessage ?: "Failed to record credit.")
                }
            } else if (offlineSyncManager != null) {
                val res = offlineSyncManager.enqueueCredit(
                    session = sess,
                    shop = shop,
                    amountPaisa = amountPaisa,
                    txDateMillis = txDateMillis,
                    dueDateMillis = dueDateMillis,
                    invoiceNumber = invoiceNumber,
                    description = description
                )
                _isSubmitting.value = false
                res.onSuccess {
                    _actionMessage.value = false to "Credit (${CurrencyFormatter.formatPkr(amountPaisa)}) saved offline. Will auto-sync when online."
                    onDone()
                }.onFailure { err ->
                    _actionMessage.value = true to (err.localizedMessage ?: "Failed to queue offline credit.")
                }
            } else {
                _isSubmitting.value = false
                requireOnlineForWrite()
            }
        }
    }

    fun reverseCredit(original: CreditTransaction, reason: String, onDone: () -> Unit = {}) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.reverseCreditTransaction(sess, original, reason)
            _isSubmitting.value = false
            res.onSuccess {
                _actionMessage.value = false to "Reversal created for ${original.txCode}."
                onDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Reversal failed.")
            }
        }
    }

    fun recordRecovery(
        shop: Shop,
        amountPaisa: Long,
        txDateMillis: Long,
        paymentMethod: PaymentMethod,
        referenceNumber: String,
        notes: String,
        clientRequestToken: String,
        onDone: () -> Unit
    ) {
        val sess = activeSession.value ?: return
        val allowOver = settings.value?.allowOverRecovery ?: (business.value?.allowOverRecovery ?: false)
        val nowMillis = PakistanTime.nowMillis()
        if (sess.role == AccountRole.STAFF && !PakistanTime.isSamePakistanDay(txDateMillis, nowMillis)) {
            _actionMessage.value = true to "Staff accounts cannot backdate or future-date recoveries. Current Pakistan date (${PakistanTime.formatDate(nowMillis)}) is required."
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            if (isOnline.value) {
                val res = repository.recordRecoveryTransaction(
                    session = sess,
                    shop = shop,
                    amountPaisa = amountPaisa,
                    txDateMillis = txDateMillis,
                    paymentMethod = paymentMethod,
                    referenceNumber = referenceNumber,
                    notes = notes,
                    allowOverRecovery = allowOver,
                    clientRequestToken = clientRequestToken,
                    deviceId = deviceId,
                    expectedDeviceTimestampMillis = nowMillis
                )
                _isSubmitting.value = false
                res.onSuccess { tx ->
                    val currentSummary = shopBalances.value.firstOrNull { it.shop.id == shop.id }
                    val remainingAfter = ((currentSummary?.outstandingPaisa ?: amountPaisa) - amountPaisa).coerceAtLeast(0L)
                    _latestReceipt.value = tx to remainingAfter
                    _actionMessage.value = false to "Recovery ${tx.txCode} confirmed on server."
                    onDone()
                }.onFailure { err ->
                    _actionMessage.value = true to (err.localizedMessage ?: "Failed to record recovery.")
                }
            } else if (offlineSyncManager != null) {
                val cachedOutstanding = shopBalances.value.firstOrNull { it.shop.id == shop.id }?.outstandingPaisa ?: 0L
                val res = offlineSyncManager.enqueueRecovery(
                    session = sess,
                    shop = shop,
                    amountPaisa = amountPaisa,
                    txDateMillis = txDateMillis,
                    paymentMethod = paymentMethod,
                    referenceNumber = referenceNumber,
                    notes = notes,
                    allowOverRecovery = allowOver,
                    currentCachedOutstandingPaisa = cachedOutstanding
                )
                _isSubmitting.value = false
                res.onSuccess { pending ->
                    val offlineTx = RecoveryTransaction(
                        id = pending.localId,
                        businessId = sess.businessId,
                        txCode = "${pending.localId.take(10).uppercase()} (OFFLINE)",
                        shopId = shop.id,
                        shopUid = shop.shopUid,
                        shopName = shop.shopName,
                        bookerId = shop.bookerId,
                        bookerName = shop.bookerName,
                        amountPaisa = amountPaisa,
                        transactionKind = "RECOVERY",
                        paymentMethod = paymentMethod.label,
                        referenceNumber = referenceNumber,
                        notes = notes,
                        txDateMillis = txDateMillis,
                        txDateFormatted = PakistanTime.formatDate(txDateMillis),
                        txTimeFormatted = PakistanTime.formatTime(txDateMillis),
                        createdByAccountId = sess.accountId,
                        createdByName = sess.fullName,
                        createdByRole = sess.role.label,
                        syncStatus = pending.syncStatus,
                        idempotencyToken = pending.idempotencyToken,
                        deviceId = deviceId
                    )
                    val remainingAfter = (cachedOutstanding - amountPaisa).coerceAtLeast(0L)
                    _latestReceipt.value = offlineTx to remainingAfter
                    _actionMessage.value = false to "Recovery saved offline. Queued for automatic cloud sync."
                    onDone()
                }.onFailure { err ->
                    _actionMessage.value = true to (err.localizedMessage ?: "Failed to save offline recovery.")
                }
            } else {
                _isSubmitting.value = false
                requireOnlineForWrite()
            }
        }
    }

    fun reverseRecovery(original: RecoveryTransaction, reason: String, onDone: () -> Unit = {}) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.reverseRecoveryTransaction(sess, original, reason)
            _isSubmitting.value = false
            res.onSuccess {
                _actionMessage.value = false to "Reversal created for ${original.txCode}."
                onDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Reversal failed.")
            }
        }
    }

    fun correctRecovery(
        original: RecoveryTransaction,
        newAmountPaisa: Long,
        newTxDateMillis: Long,
        newPaymentMethod: PaymentMethod,
        newReferenceNumber: String,
        newNotes: String,
        reason: String,
        onDone: () -> Unit = {}
    ) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        val allowOver = settings.value?.allowOverRecovery ?: (business.value?.allowOverRecovery ?: false)
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.correctRecoveryTransaction(
                session = sess,
                original = original,
                newAmountPaisa = newAmountPaisa,
                newTxDateMillis = newTxDateMillis,
                newPaymentMethod = newPaymentMethod,
                newReferenceNumber = newReferenceNumber,
                newNotes = newNotes,
                reason = reason,
                allowOverRecovery = allowOver
            )
            _isSubmitting.value = false
            res.onSuccess { corrected ->
                _actionMessage.value = false to "Corrected ${original.txCode} -> ${corrected.txCode} (${CurrencyFormatter.formatPkr(corrected.amountPaisa)})."
                onDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Payment correction failed.")
            }
        }
    }

    // --- Offline Queue & Manual Cloud Sync ---

    fun manualSyncNow() {
        val sess = activeSession.value ?: return
        if (!isOnline.value) {
            _actionMessage.value = true to "Cannot sync while offline. Connect to internet and try again."
            return
        }
        viewModelScope.launch {
            syncPendingInternal(sess, announceEmpty = true)
        }
    }

    private suspend fun syncPendingInternal(sess: ActiveWorkspaceSession, announceEmpty: Boolean) {
        val syncMgr = offlineSyncManager ?: return
        if (_isSyncing.value) return
        _isSyncing.value = true
        val allowOver = settings.value?.allowOverRecovery ?: (business.value?.allowOverRecovery ?: false)
        val summary = runCatching {
            syncMgr.syncPendingForBusiness(sess, allowOver)
        }.getOrNull()
        _isSyncing.value = false
        if (summary != null) {
            when {
                summary.conflictCount > 0 ->
                    _actionMessage.value = true to "Sync finished: ${summary.syncedCount} synced, ${summary.conflictCount} conflict(s) require Admin review."
                summary.failedCount > 0 ->
                    _actionMessage.value = true to "Sync partial: ${summary.syncedCount} synced, ${summary.failedCount} failed (will retry)."
                summary.syncedCount > 0 ->
                    _actionMessage.value = false to "Cloud sync complete: ${summary.syncedCount} offline transaction(s) synced."
                announceEmpty ->
                    _actionMessage.value = false to "All local transactions are already synced with cloud."
            }
        }
    }

    fun approveSyncConflict(item: PendingSyncTransaction) {
        val sess = activeSession.value ?: return
        val syncMgr = offlineSyncManager ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = syncMgr.approveConflictAsAdmin(sess, item)
            _isSubmitting.value = false
            res.onSuccess {
                _actionMessage.value = false to "Conflict approved and posted for ${item.shopUid}."
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to approve conflict.")
            }
        }
    }

    fun discardSyncItem(item: PendingSyncTransaction) {
        val sess = activeSession.value ?: return
        val syncMgr = offlineSyncManager ?: return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = syncMgr.discardConflictOrFailedItem(sess, item)
            _isSubmitting.value = false
            res.onSuccess {
                _actionMessage.value = false to "Queued item discarded from device outbox."
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to discard item.")
            }
        }
    }

    // --- Notifications & Settings ---

    fun markNotificationRead(id: String) {
        val sess = activeSession.value ?: return
        _locallyReadNotificationIds.value = _locallyReadNotificationIds.value + id
        viewModelScope.launch {
            sessionPrefs.markNotificationsReadLocally(sess.businessId, sess.accountId, listOf(id))
            repository.markNotificationRead(sess.businessId, id)
        }
    }

    fun markAllNotificationsRead() {
        val sess = activeSession.value ?: return
        val unread = notifications.value.filter { !it.isRead }.map { it.id }
        if (unread.isNotEmpty()) {
            _locallyReadNotificationIds.value = _locallyReadNotificationIds.value + unread
        }
        viewModelScope.launch {
            sessionPrefs.markNotificationsReadLocally(sess.businessId, sess.accountId, unread)
            val result = repository.markAllNotificationsRead(
                businessId = sess.businessId,
                role = sess.role,
                bookerId = sess.bookerId,
                unreadIds = unread
            )
            result.onSuccess {
                _actionMessage.value = false to "All notifications marked as read."
            }.onFailure { err ->
                Log.w(TAG, "markAllNotificationsRead partial/offline failure", err)
                _actionMessage.value = false to "All notifications marked as read."
            }
        }
    }

    fun updateBusinessSettings(
        businessName: String,
        businessAddress: String,
        contactPhone: String,
        allowOverRecovery: Boolean,
        requireInvoiceNumber: Boolean
    ) {
        val sess = activeSession.value ?: return
        val biz = business.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.updateBusinessSettings(
                sess, biz, businessName, businessAddress, contactPhone, allowOverRecovery, requireInvoiceNumber
            )
            _isSubmitting.value = false
            res.onSuccess {
                _actionMessage.value = false to "Business settings updated."
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to update settings.")
            }
        }
    }

    // --- Team / Meeting Group Chat ---

    fun createChatGroup(
        name: String,
        description: String,
        memberAccountIds: List<String>,
        memberNames: List<String>,
        onDone: (ChatGroup) -> Unit
    ) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.createChatGroup(
                session = sess,
                name = name,
                description = description,
                memberAccountIds = memberAccountIds,
                memberNames = memberNames
            )
            _isSubmitting.value = false
            res.onSuccess { group ->
                _actionMessage.value = false to "Meeting group '${group.name}' created."
                onDone(group)
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to create chat group.")
            }
        }
    }

    fun updateChatGroupMembers(
        group: ChatGroup,
        memberAccountIds: List<String>,
        memberNames: List<String>,
        onDone: () -> Unit
    ) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.updateChatGroupMembers(sess, group, memberAccountIds, memberNames)
            _isSubmitting.value = false
            res.onSuccess {
                _actionMessage.value = false to "Group members updated."
                onDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to update group members.")
            }
        }
    }

    fun sendTextChatMessage(groupId: String, text: String, onDone: () -> Unit = {}) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.sendChatMessage(
                session = sess,
                groupId = groupId,
                messageType = ChatMessageType.TEXT,
                text = text
            )
            _isSubmitting.value = false
            res.onSuccess { onDone() }
                .onFailure { err ->
                    _actionMessage.value = true to (err.localizedMessage ?: "Failed to send message.")
                }
        }
    }

    fun sendMediaChatMessage(
        groupId: String,
        type: ChatMessageType,
        text: String,
        mediaBase64: String,
        fileName: String,
        mimeType: String,
        durationSec: Int = 0,
        fileSizeBytes: Long = 0L,
        onDone: () -> Unit = {}
    ) {
        val sess = activeSession.value ?: return
        if (!requireOnlineForWrite()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            val res = repository.sendChatMessage(
                session = sess,
                groupId = groupId,
                messageType = type,
                text = text,
                mediaBase64 = mediaBase64,
                fileName = fileName,
                mimeType = mimeType,
                durationSec = durationSec,
                fileSizeBytes = fileSizeBytes
            )
            _isSubmitting.value = false
            res.onSuccess { onDone() }
                .onFailure { err ->
                    _actionMessage.value = true to (err.localizedMessage ?: "Failed to send attachment.")
                }
        }
    }

    // --- PDF & Export Helpers ---

    fun downloadGeneratedPdf(context: Context, uri: Uri, fileName: String) {
        PdfGenerator.downloadPdfToDevice(context, uri, fileName)
            .onSuccess { savedPath ->
                _actionMessage.value = false to "PDF downloaded to $savedPath"
            }
            .onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to download PDF.")
            }
    }

    private fun logPdfGenerated(description: String, shopUid: String = "") {
        val sess = activeSession.value ?: return
        if (!isOnline.value) return
        viewModelScope.launch {
            repository.logWorkspaceEvent(
                session = sess,
                action = "PDF_GENERATED",
                shopUid = shopUid,
                description = description
            )
        }
    }

    fun handleAllShopsPdf(
        context: Context,
        rows: List<ShopBalanceSummary>,
        dateRangeLabel: String,
        mode: PdfExportMode
    ): Uri? {
        return runCatching {
            val bizName = settings.value?.businessName?.takeIf { it.isNotBlank() }
                ?: business.value?.businessName ?: activeSession.value?.businessName ?: "HisabPro"
            val sess = activeSession.value
            val role = sess?.role ?: AccountRole.BOOKER
            val bookerObj = if (role == AccountRole.BOOKER) bookers.value.firstOrNull { it.id == sess?.bookerId } else null
            val uri = PdfGenerator.generateAllShopsStatementPdf(
                context = context,
                businessName = bizName,
                role = role,
                booker = bookerObj,
                rows = rows,
                dateRangeLabel = dateRangeLabel,
                totalBookersCount = bookers.value.size,
                hasUnsyncedData = hasUnsyncedData.value
            )
            when (mode) {
                PdfExportMode.VIEW -> PdfGenerator.viewPdf(context, uri)
                PdfExportMode.SHARE -> PdfGenerator.sharePdf(context, uri, "HisabPro Credit Statement - $bizName")
                PdfExportMode.DOWNLOAD -> downloadGeneratedPdf(
                    context = context,
                    uri = uri,
                    fileName = "HisabPro_All_Shops_${PakistanTime.formatNumericDate().replace("/", "-")}.pdf"
                )
            }
            logPdfGenerated("Exported All Shops PDF ($dateRangeLabel, ${rows.size} shops)")
            uri
        }.onFailure { err ->
            _actionMessage.value = true to (err.localizedMessage ?: "Failed to export PDF statement.")
        }.getOrNull()
    }

    fun generateAndShareAllShopsPdf(
        context: Context,
        rows: List<ShopBalanceSummary>,
        dateRangeLabel: String,
        shareImmediately: Boolean
    ): Uri? = handleAllShopsPdf(
        context = context,
        rows = rows,
        dateRangeLabel = dateRangeLabel,
        mode = if (shareImmediately) PdfExportMode.SHARE else PdfExportMode.VIEW
    )

    fun handleBookerAllShopsCreditPdf(
        context: Context,
        booker: Booker,
        assignedShops: List<ShopBalanceSummary>,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        mode: PdfExportMode,
        startMillis: Long? = null,
        endMillis: Long? = null
    ): Uri? {
        return runCatching {
            val bizName = settings.value?.businessName?.takeIf { it.isNotBlank() }
                ?: business.value?.businessName ?: activeSession.value?.businessName ?: "HisabPro"
            val uri = PdfGenerator.generateBookerAllShopsCreditPdf(
                context = context,
                businessName = bizName,
                booker = booker,
                assignedShops = assignedShops,
                credits = credits,
                recoveries = recoveries,
                businessSettings = settings.value,
                hasUnsyncedData = hasUnsyncedData.value,
                startMillis = startMillis ?: 0L,
                endMillis = endMillis ?: Long.MAX_VALUE
            )
            when (mode) {
                PdfExportMode.VIEW -> PdfGenerator.viewPdf(context, uri)
                PdfExportMode.SHARE -> PdfGenerator.sharePdf(
                    context, uri, "${booker.fullName} (${booker.bookerCode}) Recovery Ledger PDF"
                )
                PdfExportMode.DOWNLOAD -> downloadGeneratedPdf(
                    context = context,
                    uri = uri,
                    fileName = "HisabPro_Booker_${booker.bookerCode}_AllShops_${System.currentTimeMillis()}.pdf"
                )
            }
            logPdfGenerated("Exported Booker-wise All Shops Recovery Ledger PDF for ${booker.fullName} (${booker.bookerCode})")
            uri
        }.onFailure { err ->
            _actionMessage.value = true to (err.localizedMessage ?: "Failed to export Booker PDF.")
        }.getOrNull()
    }

    fun generateAndShareShopPdf(
        context: Context,
        summary: ShopBalanceSummary,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        dateRangeLabel: String,
        mode: PdfExportMode = PdfExportMode.VIEW,
        startMillis: Long? = null,
        endMillis: Long? = null
    ): Uri? {
        return runCatching {
            val bizName = settings.value?.businessName?.takeIf { it.isNotBlank() }
                ?: business.value?.businessName ?: activeSession.value?.businessName ?: "HisabPro"
            val uri = PdfGenerator.generateShopStatementPdf(
                context = context,
                businessName = bizName,
                summary = summary,
                credits = credits,
                recoveries = recoveries,
                dateRangeLabel = dateRangeLabel,
                businessSettings = settings.value,
                startMillis = startMillis ?: 0L,
                endMillis = endMillis ?: Long.MAX_VALUE
            )
            when (mode) {
                PdfExportMode.SHARE -> PdfGenerator.sharePdf(context, uri, "${summary.shop.shopName} (${summary.shop.shopUid}) Ledger")
                PdfExportMode.DOWNLOAD -> downloadGeneratedPdf(
                    context, uri, "HisabPro_Shop_${summary.shop.shopUid}_${System.currentTimeMillis()}.pdf"
                )
                PdfExportMode.VIEW -> PdfGenerator.viewPdf(context, uri)
            }
            logPdfGenerated("Exported Shop Ledger PDF for ${summary.shop.shopName} ($dateRangeLabel)", summary.shop.shopUid)
            uri
        }.onFailure { err ->
            _actionMessage.value = true to (err.localizedMessage ?: "Failed to generate Shop PDF.")
        }.getOrNull()
    }

    fun handleDailyPerformancePdf(
        context: Context,
        report: DailyPerformanceReport,
        mode: PdfExportMode
    ): Uri? {
        return runCatching {
            val bizName = settings.value?.businessName?.takeIf { it.isNotBlank() }
                ?: business.value?.businessName ?: activeSession.value?.businessName ?: "HisabPro"
            val uri = PdfGenerator.generateDailyPerformancePdf(
                context = context,
                businessName = bizName,
                businessSettings = settings.value,
                report = report
            )
            when (mode) {
                PdfExportMode.VIEW -> PdfGenerator.viewPdf(context, uri)
                PdfExportMode.SHARE -> PdfGenerator.sharePdf(context, uri, "$bizName Daily Performance (${report.dateFormatted})")
                PdfExportMode.DOWNLOAD -> downloadGeneratedPdf(
                    context, uri, "HisabPro_Daily_${report.dateFormatted.replace(" ", "_")}.pdf"
                )
            }
            logPdfGenerated("Exported Daily Performance PDF (${report.dateFormatted})")
            uri
        }.onFailure { err ->
            _actionMessage.value = true to (err.localizedMessage ?: "Failed to export Daily PDF.")
        }.getOrNull()
    }

    fun handlePeriodReportPdf(
        context: Context,
        report: PeriodReportSummary,
        dateRangeLabel: String,
        mode: PdfExportMode
    ): Uri? {
        return runCatching {
            val bizName = settings.value?.businessName?.takeIf { it.isNotBlank() }
                ?: business.value?.businessName ?: activeSession.value?.businessName ?: "HisabPro"
            val uri = PdfGenerator.generatePeriodReportPdf(
                context = context,
                businessName = bizName,
                businessSettings = settings.value,
                report = report,
                dateRangeLabel = dateRangeLabel,
                hasUnsyncedData = hasUnsyncedData.value
            )
            when (mode) {
                PdfExportMode.VIEW -> PdfGenerator.viewPdf(context, uri)
                PdfExportMode.SHARE -> PdfGenerator.sharePdf(context, uri, "$bizName Period Report ($dateRangeLabel)")
                PdfExportMode.DOWNLOAD -> downloadGeneratedPdf(
                    context, uri, "HisabPro_Period_${System.currentTimeMillis()}.pdf"
                )
            }
            logPdfGenerated("Exported Period Report PDF ($dateRangeLabel)")
            uri
        }.onFailure { err ->
            _actionMessage.value = true to (err.localizedMessage ?: "Failed to export Period PDF.")
        }.getOrNull()
    }

    fun shareRecoveryReceiptPdf(context: Context, tx: RecoveryTransaction, remainingPaisa: Long, shareImmediately: Boolean) {
        runCatching {
            val bizName = settings.value?.businessName?.takeIf { it.isNotBlank() }
                ?: business.value?.businessName ?: activeSession.value?.businessName ?: "HisabPro"
            val uri = PdfGenerator.generateRecoveryReceiptPdf(context, bizName, tx, remainingPaisa)
            if (shareImmediately) {
                PdfGenerator.sharePdf(context, uri, "Payment Receipt ${tx.txCode}")
            } else {
                PdfGenerator.viewPdf(context, uri)
            }
            logPdfGenerated("Generated Receipt PDF for ${tx.txCode}", tx.shopUid)
        }.onFailure { err ->
            _actionMessage.value = true to (err.localizedMessage ?: "Failed to generate Receipt PDF.")
        }
    }

    fun exportAllBusinessData(context: Context) {
        val biz = business.value
        if (biz == null) {
            _actionMessage.value = true to "Workspace profile is still syncing. Try again in a moment."
            return
        }
        runCatching {
            DataExporter.exportBusinessJson(
                context = context,
                business = biz,
                bookers = bookers.value,
                staffUsers = staffUsers.value,
                shops = shops.value,
                credits = creditTransactions.value,
                recoveries = recoveryTransactions.value,
                auditLogs = auditLogs.value
            )
        }.onSuccess { file ->
            _actionMessage.value = false to "Workspace backup exported (${file.name})."
        }.onFailure { err ->
            _actionMessage.value = true to (err.localizedMessage ?: "Failed to export business data.")
        }
    }

    private companion object {
        const val TAG = "HisabProAppVM"
    }
}
