package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.CompanyIsolatedSnapshot
import com.example.data.firebase.OwnerAuthRequirement
import com.example.data.firebase.PlatformCloudState
import com.example.data.firebase.PlatformOwnerRepository
import com.example.data.firebase.SessionPreferences
import com.example.domain.model.AccountRole
import com.example.domain.model.Business
import com.example.domain.model.PlatformOwnerSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GeneratedOwnerTempPassword(
    val username: String,
    val businessCode: String,
    val tempPassword: String,
    val forceChangeOnNextLogin: Boolean
)

class PlatformOwnerViewModel(
    private val repository: PlatformOwnerRepository,
    private val sessionPrefs: SessionPreferences,
    private val googleUid: String,
    private val googleEmail: String
) : ViewModel() {

    private val _authRequirement = MutableStateFlow<OwnerAuthRequirement?>(null)
    val authRequirement: StateFlow<OwnerAuthRequirement?> = _authRequirement.asStateFlow()

    val ownerSession: StateFlow<PlatformOwnerSession?> =
        sessionPrefs.observePlatformOwnerSession(googleUid, googleEmail)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _cloudState = MutableStateFlow<PlatformCloudState?>(null)
    val cloudState: StateFlow<PlatformCloudState?> = _cloudState.asStateFlow()

    private val _selectedCompanyId = MutableStateFlow<String?>(null)
    val selectedCompanyId: StateFlow<String?> = _selectedCompanyId.asStateFlow()

    private val _isolatedCompanyDetail = MutableStateFlow<CompanyIsolatedSnapshot?>(null)
    val isolatedCompanyDetail: StateFlow<CompanyIsolatedSnapshot?> = _isolatedCompanyDetail.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredCompanies: StateFlow<List<CompanyIsolatedSnapshot>> = combine(
        _cloudState,
        _searchQuery
    ) { state, q ->
        if (state == null) emptyList()
        else repository.globalSearch(state, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val selectedCompany: StateFlow<CompanyIsolatedSnapshot?> = combine(
        _isolatedCompanyDetail,
        _cloudState,
        _selectedCompanyId
    ) { isolated, state, selectedId ->
        when {
            selectedId.isNullOrBlank() -> null
            isolated != null && isolated.business.id == selectedId -> isolated
            else -> state?.companies?.firstOrNull { it.business.id == selectedId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    private val _lastGeneratedTempPassword = MutableStateFlow<GeneratedOwnerTempPassword?>(null)
    val lastGeneratedTempPassword: StateFlow<GeneratedOwnerTempPassword?> = _lastGeneratedTempPassword.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _actionMessage = MutableStateFlow<Pair<Boolean, String>?>(null)
    val actionMessage: StateFlow<Pair<Boolean, String>?> = _actionMessage.asStateFlow()

    init {
        refreshAuthRequirement()
        viewModelScope.launch {
            ownerSession.collect { sess ->
                if (sess != null) {
                    val revoked = repository.isOwnerSessionRevoked(sess.loginTimestampMillis)
                    if (revoked) {
                        sessionPrefs.clearPlatformOwnerSession()
                        _cloudState.value = null
                        _selectedCompanyId.value = null
                        _isolatedCompanyDetail.value = null
                        _actionMessage.value = true to "Platform Owner session was revoked or reset. Please sign in again."
                    } else {
                        refreshCloudState()
                    }
                } else {
                    _cloudState.value = null
                    _selectedCompanyId.value = null
                    _isolatedCompanyDetail.value = null
                }
            }
        }
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun clearGeneratedTempPassword() {
        _lastGeneratedTempPassword.value = null
    }

    fun refreshAuthRequirement() {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.inspectOwnerAuthRequirement()
            _isLoading.value = false
            res.onSuccess { req ->
                _authRequirement.value = req
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to verify Platform Owner status.")
            }
        }
    }

    fun setupFirstTimePassword(
        username: String,
        newPassword: String,
        confirmPassword: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.setupFirstTimeOwnerPassword(username, newPassword, confirmPassword)
            _isLoading.value = false
            res.onSuccess { sess ->
                sessionPrefs.savePlatformOwnerSession(sess)
                _actionMessage.value = false to "Platform Owner security configured. Welcome ${sess.username}."
                refreshAuthRequirement()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to set up Owner password.")
            }
        }
    }

    fun loginOwner(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.loginPlatformOwner(username, password)
            _isLoading.value = false
            res.onSuccess { sess ->
                sessionPrefs.savePlatformOwnerSession(sess)
                _actionMessage.value = false to "Signed in as Platform Owner (${sess.username})."
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Platform Owner login failed.")
            }
        }
    }

    fun resetOwnerPassword(
        username: String,
        newPassword: String,
        confirmPassword: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.resetOwnerPasswordWithVerifiedGoogle(username, newPassword, confirmPassword)
            _isLoading.value = false
            res.onSuccess { sess ->
                sessionPrefs.savePlatformOwnerSession(sess)
                _actionMessage.value = false to "Platform Owner password reset and active sessions refreshed."
                refreshAuthRequirement()
                onDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to reset Owner password.")
            }
        }
    }

    fun changeOwnerPassword(
        currentPassword: String,
        newPassword: String,
        confirmPassword: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.changeOwnerPassword(currentPassword, newPassword, confirmPassword)
            _isLoading.value = false
            res.onSuccess { sess ->
                sessionPrefs.savePlatformOwnerSession(sess)
                _actionMessage.value = false to "Platform Owner password changed securely."
                onDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to change Owner password.")
            }
        }
    }

    fun revokeAllOwnerSessions() {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.revokeAllOwnerSessions()
            _isLoading.value = false
            res.onSuccess {
                sessionPrefs.clearPlatformOwnerSession()
                _cloudState.value = null
                _selectedCompanyId.value = null
                _isolatedCompanyDetail.value = null
                _actionMessage.value = false to "All Platform Owner sessions revoked across devices."
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to revoke sessions.")
            }
        }
    }

    fun logoutOwner() {
        viewModelScope.launch {
            sessionPrefs.clearPlatformOwnerSession()
            _cloudState.value = null
            _selectedCompanyId.value = null
            _isolatedCompanyDetail.value = null
            _lastGeneratedTempPassword.value = null
        }
    }

    fun refreshCloudState() {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.loadGlobalCloudState()
            _isLoading.value = false
            res.onSuccess { state ->
                _cloudState.value = state
                val selId = _selectedCompanyId.value
                if (!selId.isNullOrBlank()) {
                    val match = state.companies.firstOrNull { it.business.id == selId }
                    if (match != null) {
                        _isolatedCompanyDetail.value = match
                    } else {
                        _selectedCompanyId.value = null
                        _isolatedCompanyDetail.value = null
                    }
                }
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to load global platform data.")
            }
        }
    }

    fun selectCompany(businessId: String?) {
        if (businessId.isNullOrBlank()) {
            _selectedCompanyId.value = null
            _isolatedCompanyDetail.value = null
            return
        }
        _selectedCompanyId.value = businessId
        val biz = _cloudState.value?.companies?.firstOrNull { it.business.id == businessId }?.business ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.loadIsolatedCompanySnapshot(biz)
            _isLoading.value = false
            res.onSuccess { isolated ->
                _isolatedCompanyDetail.value = isolated
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to load isolated company data.")
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun suspendOrActivateCompany(business: Business, suspend: Boolean, reason: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.suspendOrActivateCompany(business, suspend, reason)
            _isLoading.value = false
            res.onSuccess {
                _actionMessage.value = false to if (suspend) {
                    "Company ${business.businessName} suspended safely (data preserved)."
                } else {
                    "Company ${business.businessName} reactivated."
                }
                refreshCloudState()
                onDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Company status update failed.")
            }
        }
    }

    fun toggleAccountStatus(
        business: Business,
        accountId: String,
        username: String,
        role: AccountRole,
        currentStatus: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.toggleAccountStatusByOwner(business, accountId, username, role, currentStatus)
            _isLoading.value = false
            res.onSuccess {
                val next = if (currentStatus == "ACTIVE") "SUSPENDED" else "ACTIVE"
                _actionMessage.value = false to "Account @$username set to $next."
                refreshCloudState()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to update account status.")
            }
        }
    }

    fun generateTempPassword(
        business: Business,
        accountId: String,
        username: String,
        role: AccountRole,
        forceChangeOnNextLogin: Boolean = true
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.generateAndResetAccountPasswordByOwner(
                business = business,
                accountId = accountId,
                username = username,
                role = role,
                forceChangeOnNextLogin = forceChangeOnNextLogin
            )
            _isLoading.value = false
            res.onSuccess { tempPass ->
                _lastGeneratedTempPassword.value = GeneratedOwnerTempPassword(
                    username = username,
                    businessCode = business.businessCode,
                    tempPassword = tempPass,
                    forceChangeOnNextLogin = forceChangeOnNextLogin
                )
                refreshCloudState()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to generate temporary password.")
            }
        }
    }

    fun resetAccountPassword(
        business: Business,
        accountId: String,
        username: String,
        role: AccountRole,
        newPassword: String,
        forceChangeOnNextLogin: Boolean,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.resetAccountPasswordByOwner(
                business = business,
                accountId = accountId,
                username = username,
                role = role,
                newPassword = newPassword,
                forceChangeOnNextLogin = forceChangeOnNextLogin
            )
            _isLoading.value = false
            res.onSuccess {
                _actionMessage.value = false to "Password updated for @$username and prior sessions revoked."
                refreshCloudState()
                onDone()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to reset account password.")
            }
        }
    }

    fun forcePasswordChange(
        business: Business,
        accountId: String,
        username: String,
        role: AccountRole
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.forcePasswordChangeByOwner(business, accountId, username, role)
            _isLoading.value = false
            res.onSuccess {
                _actionMessage.value = false to "Forced password change enabled for @$username and active sessions revoked."
                refreshCloudState()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to force password change.")
            }
        }
    }

    fun revokeUserSessions(
        business: Business,
        accountId: String,
        username: String,
        role: AccountRole
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.revokeAccountSessionsByOwner(business, accountId, username, role)
            _isLoading.value = false
            res.onSuccess {
                _actionMessage.value = false to "Revoked active sessions for @$username."
                refreshCloudState()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Failed to revoke sessions.")
            }
        }
    }

    fun createCloudBackup() {
        val state = _cloudState.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.createCloudBackupManifest(state)
            _isLoading.value = false
            res.onSuccess { bkp ->
                _actionMessage.value = false to "Cloud backup snapshot ${bkp.id} created (${bkp.totalBusinesses} companies)."
                refreshCloudState()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Backup snapshot failed.")
            }
        }
    }

    fun restoreBackup(backupId: String, targetBusinessId: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = repository.restoreCompanyFromBackup(backupId, targetBusinessId)
            _isLoading.value = false
            res.onSuccess { count ->
                _actionMessage.value = false to "Restored $count company snapshot(s) from backup $backupId."
                refreshCloudState()
            }.onFailure { err ->
                _actionMessage.value = true to (err.localizedMessage ?: "Restore failed.")
            }
        }
    }
}
