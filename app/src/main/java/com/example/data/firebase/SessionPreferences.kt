package com.example.data.firebase

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AccountRole
import com.example.domain.model.ActiveWorkspaceSession
import com.example.domain.model.PlatformOwnerSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.hisabSessionStore: DataStore<Preferences> by preferencesDataStore(name = "hisabpro_session")

data class StoredSessionInfo(
    val session: ActiveWorkspaceSession,
    val loginAtMillis: Long,
    val lastActiveMillis: Long,
    val expiresAtMillis: Long
)

class SessionPreferences(private val context: Context) {

    private val keyGoogleUid = stringPreferencesKey("google_uid")
    private val keyLastGoogleUid = stringPreferencesKey("last_verified_google_uid")
    private val keyLastGoogleEmail = stringPreferencesKey("last_verified_google_email")
    private val keyBusinessId = stringPreferencesKey("business_id")
    private val keyBusinessCode = stringPreferencesKey("business_code")
    private val keyBusinessName = stringPreferencesKey("business_name")
    private val keyAccountId = stringPreferencesKey("account_id")
    private val keyUsername = stringPreferencesKey("username")
    private val keyFullName = stringPreferencesKey("full_name")
    private val keyRole = stringPreferencesKey("role")
    private val keyBookerId = stringPreferencesKey("booker_id")
    private val keyMustChangePassword = booleanPreferencesKey("must_change_password")
    private val keyLoginAtMillis = longPreferencesKey("login_at_millis")
    private val keyLastActiveMillis = longPreferencesKey("last_active_millis")

    // Platform Owner session keys (preserved separately)
    private val keyOwnerActive = booleanPreferencesKey("platform_owner_active")
    private val keyOwnerUsername = stringPreferencesKey("platform_owner_username")
    private val keyOwnerEmail = stringPreferencesKey("platform_owner_email")
    private val keyOwnerGoogleUid = stringPreferencesKey("platform_owner_google_uid")
    private val keyOwnerLoginAt = longPreferencesKey("platform_owner_login_at")
    private val keyOwnerMustChange = booleanPreferencesKey("platform_owner_must_change")

    fun observeLastGoogleIdentity(): Flow<Pair<String, String>?> =
        context.hisabSessionStore.data.map { prefs ->
            val uid = prefs[keyLastGoogleUid].orEmpty()
            val email = prefs[keyLastGoogleEmail].orEmpty()
            if (uid.isBlank()) null else (uid to email)
        }

    suspend fun rememberGoogleIdentity(googleUid: String, email: String?) {
        if (googleUid.isBlank()) return
        context.hisabSessionStore.edit { prefs ->
            prefs[keyLastGoogleUid] = googleUid
            if (!email.isNullOrBlank()) {
                prefs[keyLastGoogleEmail] = email
            }
        }
    }

    fun observeSessionInfo(expectedGoogleUid: String): Flow<StoredSessionInfo?> =
        context.hisabSessionStore.data.map { prefs ->
            val storedUid = prefs[keyGoogleUid].orEmpty()
            val bizId = prefs[keyBusinessId].orEmpty()
            val accountId = prefs[keyAccountId].orEmpty()
            // Allow restored offline session even if Firebase currentUser hasn't rehydrated yet when expectedGoogleUid matches storedUid or cached
            val uidMatches = expectedGoogleUid.isNotBlank() && storedUid == expectedGoogleUid
            if (!uidMatches || bizId.isBlank() || accountId.isBlank()) {
                null
            } else {
                val loginAt = prefs[keyLoginAtMillis] ?: 0L
                val lastActive = prefs[keyLastActiveMillis] ?: loginAt
                val session = ActiveWorkspaceSession(
                    businessId = bizId,
                    businessCode = prefs[keyBusinessCode].orEmpty(),
                    businessName = prefs[keyBusinessName].orEmpty(),
                    accountId = accountId,
                    username = prefs[keyUsername].orEmpty(),
                    fullName = prefs[keyFullName].orEmpty(),
                    role = AccountRole.fromKey(prefs[keyRole]),
                    bookerId = prefs[keyBookerId].orEmpty(),
                    mustChangePassword = prefs[keyMustChangePassword] ?: false
                )
                StoredSessionInfo(
                    session = session,
                    loginAtMillis = loginAt,
                    lastActiveMillis = lastActive,
                    expiresAtMillis = 0L
                )
            }
        }

    fun observeSession(expectedGoogleUid: String): Flow<ActiveWorkspaceSession?> =
        observeSessionInfo(expectedGoogleUid).map { info -> info?.session }

    fun observePlatformOwnerSession(expectedGoogleUid: String, expectedEmail: String): Flow<PlatformOwnerSession?> =
        context.hisabSessionStore.data.map { prefs ->
            val active = prefs[keyOwnerActive] ?: false
            val storedUid = prefs[keyOwnerGoogleUid].orEmpty()
            val storedEmail = prefs[keyOwnerEmail].orEmpty()
            val username = prefs[keyOwnerUsername].orEmpty()
            if (!active || storedUid != expectedGoogleUid || !storedEmail.equals(expectedEmail, ignoreCase = true) || username.isBlank()) {
                null
            } else {
                PlatformOwnerSession(
                    username = username,
                    email = storedEmail,
                    googleUid = storedUid,
                    loginTimestampMillis = prefs[keyOwnerLoginAt] ?: 0L,
                    needsPasswordSetup = false,
                    mustChangePassword = prefs[keyOwnerMustChange] ?: false
                )
            }
        }

    suspend fun savePlatformOwnerSession(ownerSession: PlatformOwnerSession) {
        context.hisabSessionStore.edit { prefs ->
            prefs[keyOwnerActive] = true
            prefs[keyOwnerUsername] = ownerSession.username
            prefs[keyOwnerEmail] = ownerSession.email
            prefs[keyOwnerGoogleUid] = ownerSession.googleUid
            prefs[keyOwnerLoginAt] = ownerSession.loginTimestampMillis
            prefs[keyOwnerMustChange] = ownerSession.mustChangePassword
        }
    }

    suspend fun clearPlatformOwnerSession() {
        context.hisabSessionStore.edit { prefs ->
            prefs.remove(keyOwnerActive)
            prefs.remove(keyOwnerUsername)
            prefs.remove(keyOwnerEmail)
            prefs.remove(keyOwnerGoogleUid)
            prefs.remove(keyOwnerLoginAt)
            prefs.remove(keyOwnerMustChange)
        }
    }

    suspend fun saveSession(
        googleUid: String,
        session: ActiveWorkspaceSession,
        loginAtMillis: Long = System.currentTimeMillis()
    ) {
        val now = System.currentTimeMillis()
        context.hisabSessionStore.edit { prefs ->
            val preservedLoginAt = if (
                prefs[keyGoogleUid] == googleUid &&
                prefs[keyBusinessId] == session.businessId &&
                prefs[keyAccountId] == session.accountId &&
                (prefs[keyLoginAtMillis] ?: 0L) > 0L
            ) {
                prefs[keyLoginAtMillis] ?: loginAtMillis
            } else {
                loginAtMillis
            }
            prefs[keyGoogleUid] = googleUid
            prefs[keyLastGoogleUid] = googleUid
            prefs[keyBusinessId] = session.businessId
            prefs[keyBusinessCode] = session.businessCode
            prefs[keyBusinessName] = session.businessName
            prefs[keyAccountId] = session.accountId
            prefs[keyUsername] = session.username
            prefs[keyFullName] = session.fullName
            prefs[keyRole] = session.role.key
            prefs[keyBookerId] = session.bookerId
            prefs[keyMustChangePassword] = session.mustChangePassword
            prefs[keyLoginAtMillis] = preservedLoginAt
            prefs[keyLastActiveMillis] = now
        }
    }

    suspend fun touchActivity(nowMillis: Long = System.currentTimeMillis()) {
        context.hisabSessionStore.edit { prefs ->
            if (!prefs[keyBusinessId].isNullOrBlank()) {
                prefs[keyLastActiveMillis] = nowMillis
            }
        }
    }

    private fun readNotifKey(businessId: String, accountId: String) =
        stringSetPreferencesKey("read_notif_ids_${businessId}_${accountId}")

    fun observeLocallyReadNotificationIds(businessId: String, accountId: String): Flow<Set<String>> {
        if (businessId.isBlank() || accountId.isBlank()) return kotlinx.coroutines.flow.flowOf(emptySet())
        val key = readNotifKey(businessId, accountId)
        return context.hisabSessionStore.data.map { prefs -> prefs[key].orEmpty() }
    }

    suspend fun markNotificationsReadLocally(businessId: String, accountId: String, ids: Collection<String>) {
        val clean = ids.filter { it.isNotBlank() }
        if (businessId.isBlank() || accountId.isBlank() || clean.isEmpty()) return
        val key = readNotifKey(businessId, accountId)
        context.hisabSessionStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            val merged = (current + clean).toList().takeLast(500).toSet()
            prefs[key] = merged
        }
    }

    suspend fun clearSession() {
        context.hisabSessionStore.edit { prefs ->
            prefs.remove(keyGoogleUid)
            prefs.remove(keyBusinessId)
            prefs.remove(keyBusinessCode)
            prefs.remove(keyBusinessName)
            prefs.remove(keyAccountId)
            prefs.remove(keyUsername)
            prefs.remove(keyFullName)
            prefs.remove(keyRole)
            prefs.remove(keyBookerId)
            prefs.remove(keyMustChangePassword)
            prefs.remove(keyLoginAtMillis)
            prefs.remove(keyLastActiveMillis)
        }
    }

    suspend fun clearAllOnGoogleSignOut() {
        context.hisabSessionStore.edit { prefs ->
            prefs.clear()
        }
    }

    companion object {
        const val SESSION_MAX_AGE_MS = 0L // Persistent until manual logout

        fun isSessionExpired(
            loginAtMillis: Long,
            nowMillis: Long = System.currentTimeMillis(),
            maxAgeMillis: Long = SESSION_MAX_AGE_MS
        ): Boolean {
            if (maxAgeMillis <= 0L || loginAtMillis <= 0L) return false
            return (nowMillis - loginAtMillis) >= maxAgeMillis
        }
    }
}
