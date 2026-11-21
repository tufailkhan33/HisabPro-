package com.example.desktop.cloud

import com.example.desktop.local.DesktopSqliteDatabase
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

data class DesktopFirebaseAuthSession(
    val uid: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val idToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
) {
    fun isTokenExpiredOrExpiringSoon(nowMillis: Long = System.currentTimeMillis()): Boolean =
        idToken.isBlank() || nowMillis >= (expiresAtMillis - 90_000L)

    fun toJson(): JSONObject = JSONObject().apply {
        put("uid", uid)
        put("email", email)
        put("displayName", displayName)
        put("emailVerified", emailVerified)
        put("idToken", idToken)
        put("refreshToken", refreshToken)
        put("expiresAtMillis", expiresAtMillis)
    }

    companion object {
        fun fromJson(text: String): DesktopFirebaseAuthSession? = try {
            val o = JSONObject(text)
            DesktopFirebaseAuthSession(
                uid = o.optString("uid"),
                email = o.optString("email"),
                displayName = o.optString("displayName"),
                emailVerified = o.optBoolean("emailVerified", false),
                idToken = o.optString("idToken"),
                refreshToken = o.optString("refreshToken"),
                expiresAtMillis = o.optLong("expiresAtMillis", 0L)
            ).takeIf { it.uid.isNotBlank() && it.refreshToken.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Handles Google OAuth2 (PKCE loopback) + Firebase Identity Toolkit REST sign-in and
 * automatic encrypted refresh-token rotation against the shared Firebase Auth backend.
 */
class DesktopFirebaseAuthClient(
    private val config: FirebaseDesktopConfig,
    private val db: DesktopSqliteDatabase,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()
) {
    companion object {
        private const val AUTH_SLOT = "firebase_auth_session"
    }

    @Volatile
    private var cachedSession: DesktopFirebaseAuthSession? = null

    fun restoreSavedSession(): DesktopFirebaseAuthSession? {
        cachedSession?.let { return it }
        val raw = db.loadEncryptedSlot(AUTH_SLOT) ?: return null
        return DesktopFirebaseAuthSession.fromJson(raw)?.also { cachedSession = it }
    }

    fun clearSession() {
        cachedSession = null
        db.clearEncryptedSlot(AUTH_SLOT)
    }

    suspend fun getValidIdToken(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val current = restoreSavedSession()
                ?: throw IllegalStateException("Google Sign-In required before accessing Firebase cloud.")
            if (!current.isTokenExpiredOrExpiringSoon()) {
                return@runCatching current.idToken
            }
            val refreshed = refreshIdToken(current.refreshToken, current).getOrThrow()
            persistSession(refreshed)
            refreshed.idToken
        }
    }

    suspend fun refreshIdToken(
        refreshToken: String,
        previous: DesktopFirebaseAuthSession? = null
    ): Result<DesktopFirebaseAuthSession> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.apiKey.isNotBlank()) { "Firebase API key is missing in bundled google-services.json." }
            val body = "grant_type=refresh_token&refresh_token=${urlEncode(refreshToken)}"
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://securetoken.googleapis.com/v1/token?key=${config.apiKey}"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() !in 200..299) {
                throw IllegalStateException("Token refresh failed (${resp.statusCode()}): ${resp.body()}")
            }
            val json = JSONObject(resp.body())
            val newIdToken = json.optString("id_token")
            val newRefresh = json.optString("refresh_token", refreshToken)
            val uid = json.optString("user_id", previous?.uid.orEmpty())
            val expiresIn = json.optString("expires_in", "3600").toLongOrNull() ?: 3600L

            val lookup = lookupAccount(newIdToken).getOrNull()
            DesktopFirebaseAuthSession(
                uid = uid,
                email = lookup?.first ?: previous?.email.orEmpty(),
                displayName = previous?.displayName.orEmpty().ifBlank { lookup?.first.orEmpty() },
                emailVerified = lookup?.second ?: previous?.emailVerified ?: false,
                idToken = newIdToken,
                refreshToken = newRefresh,
                expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L
            )
        }
    }

    /**
     * Launches Google Sign-In in the Windows default browser via a local loopback callback,
     * exchanges the OAuth2 authorization code (PKCE), and signs into Firebase Identity Toolkit.
     */
    suspend fun signInWithGoogleBrowser(): Result<DesktopFirebaseAuthSession> = withContext(Dispatchers.IO) {
        runCatching {
            require(config.webClientId.isNotBlank()) {
                "Google OAuth Client ID is missing. Configure HISABPRO_DESKTOP_GOOGLE_CLIENT_ID in .env or google-services.json."
            }
            val verifierBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes)
            val challengeBytes = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
            val codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes)
            val state = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })

            val codeFuture = CompletableFuture<String>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val port = server.address.port
            val redirectUri = "http://127.0.0.1:$port/callback"

            server.createContext("/callback") { exchange ->
                val query = exchange.requestURI.rawQuery.orEmpty()
                val params = parseQueryParams(query)
                val returnedState = params["state"].orEmpty()
                val error = params["error"]
                val code = params["code"]

                val html = if (error != null || code.isNullOrBlank() || returnedState != state) {
                    "<html><body style='font-family:Segoe UI,sans-serif;text-align:center;padding:48px;'><h2>HisabPro Sign-In Cancelled</h2><p>${error ?: "Authorization incomplete."} You can close this browser tab.</p></body></html>"
                } else {
                    "<html><body style='font-family:Segoe UI,sans-serif;text-align:center;padding:48px;color:#172C46;'><h2>HisabPro Sign-In Successful</h2><p>You can close this browser tab and return to HisabPro Desktop.</p></body></html>"
                }
                val bytes = html.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }

                if (error != null) {
                    codeFuture.completeExceptionally(IllegalStateException("Google Sign-In failed: $error"))
                } else if (returnedState != state || code.isNullOrBlank()) {
                    codeFuture.completeExceptionally(IllegalStateException("Invalid OAuth callback state."))
                } else {
                    codeFuture.complete(code)
                }
            }
            server.start()

            try {
                val authUrl = buildString {
                    append("https://accounts.google.com/o/oauth2/v2/auth")
                    append("?client_id=").append(urlEncode(config.webClientId))
                    append("&redirect_uri=").append(urlEncode(redirectUri))
                    append("&response_type=code")
                    append("&scope=").append(urlEncode("openid email profile"))
                    append("&code_challenge=").append(urlEncode(codeChallenge))
                    append("&code_challenge_method=S256")
                    append("&state=").append(urlEncode(state))
                    append("&prompt=select_account")
                }
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw IllegalStateException("System browser cannot be opened automatically. Open URL manually or provide a Google ID Token.")
                }
                Desktop.getDesktop().browse(URI(authUrl))
                val authCode = codeFuture.get(3, TimeUnit.MINUTES)
                val googleIdToken = exchangeGoogleAuthCode(authCode, redirectUri, codeVerifier)
                signInWithGoogleIdToken(googleIdToken).getOrThrow()
            } finally {
                server.stop(1)
            }
        }
    }

    /**
     * Exchanges an external Google OAuth ID token against Firebase Identity Toolkit
     * (`accounts:signInWithIdp`) so Firebase Auth UID & verified email match Firestore rules.
     */
    suspend fun signInWithGoogleIdToken(googleIdToken: String): Result<DesktopFirebaseAuthSession> = withContext(Dispatchers.IO) {
        runCatching {
            require(googleIdToken.isNotBlank()) { "Google ID token cannot be empty." }
            require(config.apiKey.isNotBlank()) { "Firebase API key is missing in configuration." }
            val payload = JSONObject().apply {
                put("postBody", "id_token=$googleIdToken&providerId=google.com")
                put("requestUri", "http://localhost")
                put("returnIdpCredential", true)
                put("returnSecureToken", true)
            }
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=${config.apiKey}"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build()
            val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() !in 200..299) {
                throw IllegalStateException("Firebase Google Auth failed (${resp.statusCode()}): ${resp.body()}")
            }
            val json = JSONObject(resp.body())
            val expiresIn = json.optString("expiresIn", "3600").toLongOrNull() ?: 3600L
            val session = DesktopFirebaseAuthSession(
                uid = json.optString("localId"),
                email = json.optString("email"),
                displayName = json.optString("fullName").ifBlank { json.optString("displayName") },
                emailVerified = json.optBoolean("emailVerified", false),
                idToken = json.optString("idToken"),
                refreshToken = json.optString("refreshToken"),
                expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L
            )
            persistSession(session)
            session
        }
    }

    private fun exchangeGoogleAuthCode(code: String, redirectUri: String, codeVerifier: String): String {
        val params = buildString {
            append("code=").append(urlEncode(code))
            append("&client_id=").append(urlEncode(config.webClientId))
            if (config.oauthClientSecret.isNotBlank()) {
                append("&client_secret=").append(urlEncode(config.oauthClientSecret))
            }
            append("&redirect_uri=").append(urlEncode(redirectUri))
            append("&grant_type=authorization_code")
            append("&code_verifier=").append(urlEncode(codeVerifier))
        }
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://oauth2.googleapis.com/token"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(params))
            .build()
        val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            throw IllegalStateException("Google OAuth token exchange failed (${resp.statusCode()}): ${resp.body()}")
        }
        val json = JSONObject(resp.body())
        val idToken = json.optString("id_token")
        require(idToken.isNotBlank()) { "Google OAuth response did not include an id_token." }
        return idToken
    }

    private fun lookupAccount(idToken: String): Result<Pair<String, Boolean>> = runCatching {
        val body = JSONObject().put("idToken", idToken).toString()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${config.apiKey}"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val json = JSONObject(resp.body())
        val user = json.optJSONArray("users")?.optJSONObject(0) ?: JSONObject()
        user.optString("email") to user.optBoolean("emailVerified", false)
    }

    private fun persistSession(session: DesktopFirebaseAuthSession) {
        cachedSession = session
        db.saveEncryptedSlot(AUTH_SLOT, session.toJson().toString())
    }

    private fun parseQueryParams(query: String): Map<String, String> =
        query.split("&")
            .filter { it.contains("=") }
            .associate { pair ->
                val idx = pair.indexOf("=")
                URLDecoder.decode(pair.substring(0, idx), Charsets.UTF_8) to
                    URLDecoder.decode(pair.substring(idx + 1), Charsets.UTF_8)
            }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8)
}
