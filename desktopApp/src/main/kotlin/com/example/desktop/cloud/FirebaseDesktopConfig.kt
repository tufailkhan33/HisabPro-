package com.example.desktop.cloud

import org.json.JSONObject
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Loads the exact shared Firebase project, named Firestore database, and OAuth client config
 * from the bundled google-services.json and firebase_applet_config.xml copied from :app
 * (with optional .env / system environment overrides for Desktop OAuth client credentials).
 */
data class FirebaseDesktopConfig(
    val projectId: String,
    val projectNumber: String,
    val databaseId: String,
    val apiKey: String,
    val webClientId: String,
    val oauthClientSecret: String
) {
    val firestoreDocumentsBaseUrl: String
        get() = "https://firestore.googleapis.com/v1/projects/$projectId/databases/$databaseId/documents"

    val firestoreRunQueryUrl: String
        get() = "https://firestore.googleapis.com/v1/projects/$projectId/databases/$databaseId/documents:runQuery"

    val isConfigured: Boolean
        get() = projectId.isNotBlank() && databaseId.isNotBlank() && apiKey.isNotBlank()

    companion object {
        fun load(): FirebaseDesktopConfig {
            val env = loadDotEnv()
            val gsJsonText = readResourceText("google-services.json")
                ?: File("app/google-services.json").takeIf { it.exists() }?.readText()
                ?: "{}"
            val gs = JSONObject(gsJsonText)
            val projectInfo = gs.optJSONObject("project_info") ?: JSONObject()
            val projectId = projectInfo.optString("project_id", "")
            val projectNumber = projectInfo.optString("project_number", "")

            val clients = gs.optJSONArray("client")
            val firstClient = if (clients != null && clients.length() > 0) clients.optJSONObject(0) else JSONObject()
            val apiKeyArr = firstClient.optJSONArray("api_key")
            val apiKey = if (apiKeyArr != null && apiKeyArr.length() > 0) {
                apiKeyArr.optJSONObject(0)?.optString("current_key", "").orEmpty()
            } else ""

            var webClientId = ""
            val oauthClients = firstClient.optJSONArray("oauth_client")
            if (oauthClients != null) {
                for (i in 0 until oauthClients.length()) {
                    val obj = oauthClients.optJSONObject(i) ?: continue
                    if (obj.optInt("client_type") == 3) {
                        webClientId = obj.optString("client_id", "")
                        break
                    }
                }
            }

            val xmlText = readResourceText("firebase_applet_config.xml")
                ?: File("app/src/main/res/values/firebase_applet_config.xml").takeIf { it.exists() }?.readText()
            val databaseId = parseDatabaseIdFromXml(xmlText).ifBlank { "(default)" }

            val envClientId = System.getenv("HISABPRO_DESKTOP_GOOGLE_CLIENT_ID")
                ?: env["HISABPRO_DESKTOP_GOOGLE_CLIENT_ID"]
                ?: ""
            val envClientSecret = System.getenv("HISABPRO_DESKTOP_GOOGLE_CLIENT_SECRET")
                ?: env["HISABPRO_DESKTOP_GOOGLE_CLIENT_SECRET"]
                ?: ""

            return FirebaseDesktopConfig(
                projectId = projectId,
                projectNumber = projectNumber,
                databaseId = databaseId,
                apiKey = apiKey,
                webClientId = envClientId.ifBlank { webClientId },
                oauthClientSecret = envClientSecret
            )
        }

        private fun readResourceText(name: String): String? {
            return FirebaseDesktopConfig::class.java.classLoader
                ?.getResourceAsStream(name)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
        }

        private fun parseDatabaseIdFromXml(xmlText: String?): String {
            if (xmlText.isNullOrBlank()) return ""
            return try {
                val doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xmlText.byteInputStream(Charsets.UTF_8))
                val nodes = doc.getElementsByTagName("string")
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i)
                    val nameAttr = node.attributes?.getNamedItem("name")?.nodeValue
                    if (nameAttr == "firestore_database_id") {
                        return node.textContent.orEmpty().trim()
                    }
                }
                ""
            } catch (_: Exception) {
                ""
            }
        }

        private fun loadDotEnv(): Map<String, String> {
            val f = File(".env").takeIf { it.exists() } ?: return emptyMap()
            return f.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
                .associate { line ->
                    val idx = line.indexOf('=')
                    line.substring(0, idx).trim() to line.substring(idx + 1).trim().removeSurrounding("\"")
                }
        }
    }
}
