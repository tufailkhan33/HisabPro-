package com.example.desktop.cloud

import com.example.shared.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/**
 * Typed Firestore REST API v1 codec & client targeting the exact shared Firebase project
 * and named Firestore database (`ai-studio-android-hisabpro-...`) used by HisabPro Android v1.3.
 */
object FirestoreRestCodec {

    fun stringVal(v: String): JSONObject = JSONObject().put("stringValue", v)
    fun intVal(v: Long): JSONObject = JSONObject().put("integerValue", v.toString())
    fun boolVal(v: Boolean): JSONObject = JSONObject().put("booleanValue", v)
    fun arrayStringsVal(items: List<String>): JSONObject = JSONObject().put(
        "arrayValue",
        JSONObject().put("values", JSONArray().apply { items.forEach { put(stringVal(it)) } })
    )

    fun extractString(fields: JSONObject?, key: String, default: String = ""): String =
        fields?.optJSONObject(key)?.optString("stringValue", default) ?: default

    fun extractLong(fields: JSONObject?, key: String, default: Long = 0L): Long {
        val obj = fields?.optJSONObject(key) ?: return default
        if (obj.has("integerValue")) return obj.optString("integerValue").toLongOrNull() ?: default
        if (obj.has("doubleValue")) return obj.optDouble("doubleValue").toLong()
        return default
    }

    fun extractBool(fields: JSONObject?, key: String, default: Boolean = false): Boolean =
        fields?.optJSONObject(key)?.optBoolean("booleanValue", default) ?: default

    fun extractTimestampMillis(fields: JSONObject?, key: String, default: Long = 0L): Long {
        val ts = fields?.optJSONObject(key)?.optString("timestampValue", "").orEmpty()
        if (ts.isBlank()) return default
        return try {
            Instant.parse(ts).toEpochMilli()
        } catch (_: Exception) {
            default
        }
    }

    fun extractStringList(fields: JSONObject?, key: String): List<String> {
        val values = fields?.optJSONObject(key)
            ?.optJSONObject("arrayValue")
            ?.optJSONArray("values") ?: return emptyList()
        return buildList {
            for (i in 0 until values.length()) {
                values.optJSONObject(i)?.optString("stringValue")?.let { add(it) }
            }
        }
    }

    fun docIdFromName(name: String): String = name.substringAfterLast('/')

    fun buildDocumentBody(fields: Map<String, JSONObject>): JSONObject =
        JSONObject().put("fields", JSONObject(fields))

    /**
     * Attaches `createdAt` / `updatedAt` using Firestore `commit` transform `REQUEST_TIME`
     * so Firestore rules validating `isValidTimestamp(data.createdAt)` succeed identically
     * to Android's `FieldValue.serverTimestamp()`.
     */
    fun buildServerTimestampCommitWrite(
        fullDocumentName: String,
        fields: Map<String, JSONObject>,
        includeCreatedAt: Boolean = true,
        includeUpdatedAt: Boolean = true
    ): JSONObject {
        val transforms = JSONArray()
        if (includeCreatedAt) {
            transforms.put(
                JSONObject()
                    .put("fieldPath", "createdAt")
                    .put("setToServerValue", "REQUEST_TIME")
            )
        }
        if (includeUpdatedAt) {
            transforms.put(
                JSONObject()
                    .put("fieldPath", "updatedAt")
                    .put("setToServerValue", "REQUEST_TIME")
            )
        }
        return JSONObject().apply {
            put(
                "writes",
                JSONArray().put(
                    JSONObject().apply {
                        put(
                            "update",
                            JSONObject().apply {
                                put("name", fullDocumentName)
                                put("fields", JSONObject(fields))
                            }
                        )
                        if (transforms.length() > 0) {
                            put("updateTransforms", transforms)
                        }
                    }
                )
            )
        }
    }

    fun toBusiness(doc: JSONObject): Business {
        val f = doc.optJSONObject("fields")
        return Business(
            id = docIdFromName(doc.optString("name")),
            businessName = extractString(f, "businessName"),
            businessCode = extractString(f, "businessCode"),
            ownerUid = extractString(f, "ownerUid"),
            ownerName = extractString(f, "ownerName"),
            ownerMobile = extractString(f, "ownerMobile"),
            ownerEmail = extractString(f, "ownerEmail"),
            currency = extractString(f, "currency", "PKR"),
            timezone = extractString(f, "timezone", "Asia/Karachi"),
            allowOverRecovery = extractBool(f, "allowOverRecovery"),
            status = extractString(f, "status", "ACTIVE"),
            suspendedReason = extractString(f, "suspendedReason"),
            lastActivityMillis = extractLong(f, "lastActivityMillis"),
            createdAtMillis = extractTimestampMillis(f, "createdAt"),
            updatedAtMillis = extractTimestampMillis(f, "updatedAt")
        )
    }

    fun toBusinessSettings(doc: JSONObject): BusinessSettings {
        val f = doc.optJSONObject("fields")
        return BusinessSettings(
            id = docIdFromName(doc.optString("name")),
            businessId = extractString(f, "businessId"),
            businessName = extractString(f, "businessName"),
            businessAddress = extractString(f, "businessAddress"),
            contactPhone = extractString(f, "contactPhone"),
            allowOverRecovery = extractBool(f, "allowOverRecovery"),
            requireInvoiceNumber = extractBool(f, "requireInvoiceNumber"),
            updatedBy = extractString(f, "updatedBy")
        )
    }

    fun toLoginTicket(doc: JSONObject): LoginTicket {
        val f = doc.optJSONObject("fields")
        return LoginTicket(
            id = docIdFromName(doc.optString("name")),
            businessId = extractString(f, "businessId"),
            businessName = extractString(f, "businessName"),
            businessCode = extractString(f, "businessCode"),
            accountId = extractString(f, "accountId"),
            role = extractString(f, "role", "BOOKER"),
            username = extractString(f, "username"),
            fullName = extractString(f, "fullName"),
            passwordSalt = extractString(f, "passwordSalt"),
            passwordHash = extractString(f, "passwordHash"),
            mustChangePassword = extractBool(f, "mustChangePassword"),
            status = extractString(f, "status", "ACTIVE"),
            boundGoogleUid = extractString(f, "boundGoogleUid"),
            ownerUid = extractString(f, "ownerUid"),
            sessionRevokedBeforeMillis = extractLong(f, "sessionRevokedBeforeMillis")
        )
    }

    fun toWorkspaceBookmark(doc: JSONObject): UserWorkspaceBookmark {
        val f = doc.optJSONObject("fields")
        return UserWorkspaceBookmark(
            id = docIdFromName(doc.optString("name")),
            businessId = extractString(f, "businessId"),
            businessName = extractString(f, "businessName"),
            businessCode = extractString(f, "businessCode"),
            lastUsername = extractString(f, "lastUsername"),
            lastRole = extractString(f, "lastRole", "SUPER_ADMIN"),
            updatedAtMillis = extractTimestampMillis(f, "updatedAt")
        )
    }

    fun toBusinessUser(doc: JSONObject): BusinessUser {
        val f = doc.optJSONObject("fields")
        return BusinessUser(
            id = docIdFromName(doc.optString("name")),
            businessId = extractString(f, "businessId"),
            userCode = extractString(f, "userCode"),
            fullName = extractString(f, "fullName"),
            username = extractString(f, "username"),
            mobileNumber = extractString(f, "mobileNumber"),
            email = extractString(f, "email"),
            role = extractString(f, "role", "STAFF"),
            status = extractString(f, "status", "ACTIVE"),
            mustChangePassword = extractBool(f, "mustChangePassword"),
            sessionRevokedBeforeMillis = extractLong(f, "sessionRevokedBeforeMillis"),
            lastLoginMillis = extractLong(f, "lastLoginMillis")
        )
    }

    fun toStaffPermission(doc: JSONObject): StaffPermission {
        val f = doc.optJSONObject("fields")
        val id = docIdFromName(doc.optString("name"))
        return StaffPermission(
            id = id,
            businessId = extractString(f, "businessId"),
            userId = extractString(f, "userId", id),
            viewDashboard = extractBool(f, "viewDashboard", true),
            viewShops = extractBool(f, "viewShops", true),
            addShops = extractBool(f, "addShops", false),
            editShops = extractBool(f, "editShops", false),
            addCredit = extractBool(f, "addCredit", false),
            editCredit = extractBool(f, "editCredit", false),
            recordRecovery = extractBool(f, "recordRecovery", false),
            viewRecoveryHistory = extractBool(f, "viewRecoveryHistory", true),
            viewBookers = extractBool(f, "viewBookers", true),
            manageBookers = extractBool(f, "manageBookers", false),
            viewReports = extractBool(f, "viewReports", true),
            generatePdfs = extractBool(f, "generatePdfs", true),
            searchShops = extractBool(f, "searchShops", true),
            viewActivityLogs = extractBool(f, "viewActivityLogs", false)
        )
    }

    fun toBooker(doc: JSONObject): Booker {
        val f = doc.optJSONObject("fields")
        return Booker(
            id = docIdFromName(doc.optString("name")),
            businessId = extractString(f, "businessId"),
            bookerCode = extractString(f, "bookerCode"),
            fullName = extractString(f, "fullName"),
            username = extractString(f, "username"),
            mobileNumber = extractString(f, "mobileNumber"),
            address = extractString(f, "address"),
            notes = extractString(f, "notes"),
            status = extractString(f, "status", "ACTIVE"),
            mustChangePassword = extractBool(f, "mustChangePassword"),
            sessionRevokedBeforeMillis = extractLong(f, "sessionRevokedBeforeMillis"),
            lastLoginMillis = extractLong(f, "lastLoginMillis")
        )
    }

    fun toShop(doc: JSONObject): Shop {
        val f = doc.optJSONObject("fields")
        return Shop(
            id = docIdFromName(doc.optString("name")),
            businessId = extractString(f, "businessId"),
            shopUid = extractString(f, "shopUid"),
            shopName = extractString(f, "shopName"),
            bookerId = extractString(f, "bookerId"),
            bookerName = extractString(f, "bookerName"),
            ownerName = extractString(f, "ownerName"),
            mobileNumber = extractString(f, "mobileNumber"),
            address = extractString(f, "address"),
            city = extractString(f, "city"),
            openingCreditPaisa = extractLong(f, "openingCreditPaisa"),
            openingDateMillis = extractLong(f, "openingDateMillis"),
            notes = extractString(f, "notes"),
            isArchived = extractBool(f, "isArchived"),
            createdByAccountId = extractString(f, "createdByAccountId"),
            createdByName = extractString(f, "createdByName")
        )
    }

    fun toProduct(doc: JSONObject): ProductItem {
        val f = doc.optJSONObject("fields")
        return ProductItem(
            id = docIdFromName(doc.optString("name")),
            businessId = extractString(f, "businessId"),
            sku = extractString(f, "sku"),
            name = extractString(f, "name"),
            unit = extractString(f, "unit", "Pack"),
            unitPricePaisa = extractLong(f, "unitPricePaisa"),
            stockQty = extractLong(f, "stockQty").toInt(),
            reorderLevel = extractLong(f, "reorderLevel", 5).toInt(),
            isArchived = extractBool(f, "isArchived"),
            updatedAtMillis = extractLong(f, "updatedAtMillis"),
            syncStatus = SyncStatus.SYNCED.key
        )
    }

    fun toBooking(doc: JSONObject): BookingOrder {
        val f = doc.optJSONObject("fields")
        return BookingOrder(
            id = docIdFromName(doc.optString("name")),
            businessId = extractString(f, "businessId"),
            bookingCode = extractString(f, "bookingCode"),
            shopId = extractString(f, "shopId"),
            shopUid = extractString(f, "shopUid"),
            shopName = extractString(f, "shopName"),
            bookerId = extractString(f, "bookerId"),
            bookerName = extractString(f, "bookerName"),
            productId = extractString(f, "productId"),
            productName = extractString(f, "productName"),
            quantity = extractLong(f, "quantity", 1).toInt(),
            unitPricePaisa = extractLong(f, "unitPricePaisa"),
            totalAmountPaisa = extractLong(f, "totalAmountPaisa"),
            status = extractString(f, "status", "BOOKED"),
            notes = extractString(f, "notes"),
            bookingDateMillis = extractLong(f, "bookingDateMillis"),
            bookingDateFormatted = extractString(f, "bookingDateFormatted"),
            createdByAccountId = extractString(f, "createdByAccountId"),
            createdByName = extractString(f, "createdByName"),
            syncStatus = SyncStatus.SYNCED.key,
            idempotencyToken = extractString(f, "idempotencyToken"),
            deviceId = extractString(f, "deviceId")
        )
    }

    fun toCreditTx(doc: JSONObject): CreditTransaction {
        val f = doc.optJSONObject("fields")
        return CreditTransaction(
            id = docIdFromName(doc.optString("name")),
            businessId = extractString(f, "businessId"),
            txCode = extractString(f, "txCode"),
            shopId = extractString(f, "shopId"),
            shopUid = extractString(f, "shopUid"),
            shopName = extractString(f, "shopName"),
            bookerId = extractString(f, "bookerId"),
            bookerName = extractString(f, "bookerName"),
            amountPaisa = extractLong(f, "amountPaisa"),
            transactionKind = extractString(f, "transactionKind", "CREDIT"),
            relatedTxId = extractString(f, "relatedTxId"),
            reason = extractString(f, "reason"),
            invoiceNumber = extractString(f, "invoiceNumber"),
            description = extractString(f, "description"),
            attachmentUri = extractString(f, "attachmentUri"),
            txDateMillis = extractLong(f, "txDateMillis"),
            txDateFormatted = extractString(f, "txDateFormatted"),
            txTimeFormatted = extractString(f, "txTimeFormatted"),
            createdByAccountId = extractString(f, "createdByAccountId"),
            createdByName = extractString(f, "createdByName"),
            createdByRole = extractString(f, "createdByRole"),
            dueDateMillis = extractLong(f, "dueDateMillis"),
            dueDateFormatted = extractString(f, "dueDateFormatted"),
            entryTimestampMillis = extractLong(f, "entryTimestampMillis"),
            entryDateFormatted = extractString(f, "entryDateFormatted"),
            entryTimeFormatted = extractString(f, "entryTimeFormatted"),
            syncStatus = extractString(f, "syncStatus", "SYNCED"),
            idempotencyToken = extractString(f, "idempotencyToken"),
            deviceId = extractString(f, "deviceId")
        )
    }

    fun toRecoveryTx(doc: JSONObject): RecoveryTransaction {
        val f = doc.optJSONObject("fields")
        return RecoveryTransaction(
            id = docIdFromName(doc.optString("name")),
            businessId = extractString(f, "businessId"),
            txCode = extractString(f, "txCode"),
            shopId = extractString(f, "shopId"),
            shopUid = extractString(f, "shopUid"),
            shopName = extractString(f, "shopName"),
            bookerId = extractString(f, "bookerId"),
            bookerName = extractString(f, "bookerName"),
            amountPaisa = extractLong(f, "amountPaisa"),
            transactionKind = extractString(f, "transactionKind", "RECOVERY"),
            relatedTxId = extractString(f, "relatedTxId"),
            reason = extractString(f, "reason"),
            paymentMethod = extractString(f, "paymentMethod", "Cash"),
            referenceNumber = extractString(f, "referenceNumber"),
            notes = extractString(f, "notes"),
            txDateMillis = extractLong(f, "txDateMillis"),
            txDateFormatted = extractString(f, "txDateFormatted"),
            txTimeFormatted = extractString(f, "txTimeFormatted"),
            createdByAccountId = extractString(f, "createdByAccountId"),
            createdByName = extractString(f, "createdByName"),
            createdByRole = extractString(f, "createdByRole"),
            correctionGroupId = extractString(f, "correctionGroupId"),
            correctedFromTxId = extractString(f, "correctedFromTxId"),
            originalAmountPaisa = extractLong(f, "originalAmountPaisa"),
            originalTxDateMillis = extractLong(f, "originalTxDateMillis"),
            isBackdated = extractBool(f, "isBackdated"),
            reversedAtMillis = extractLong(f, "reversedAtMillis"),
            reversedByAccountId = extractString(f, "reversedByAccountId"),
            reversedByName = extractString(f, "reversedByName"),
            syncStatus = extractString(f, "syncStatus", "SYNCED"),
            idempotencyToken = extractString(f, "idempotencyToken"),
            deviceId = extractString(f, "deviceId")
        )
    }

    fun toAuditLog(doc: JSONObject): AuditLog {
        val f = doc.optJSONObject("fields")
        return AuditLog(
            id = docIdFromName(doc.optString("name")),
            businessId = extractString(f, "businessId"),
            activityCode = extractString(f, "activityCode"),
            userId = extractString(f, "userId"),
            username = extractString(f, "username"),
            role = extractString(f, "role"),
            action = extractString(f, "action"),
            shopUid = extractString(f, "shopUid"),
            previousValue = extractString(f, "previousValue"),
            newValue = extractString(f, "newValue"),
            dateFormatted = extractString(f, "dateFormatted"),
            timeFormatted = extractString(f, "timeFormatted"),
            timestampMillis = extractLong(f, "timestampMillis"),
            description = extractString(f, "description")
        )
    }

    fun toNotification(doc: JSONObject): NotificationItem {
        val f = doc.optJSONObject("fields")
        return NotificationItem(
            id = docIdFromName(doc.optString("name")),
            businessId = extractString(f, "businessId"),
            title = extractString(f, "title"),
            message = extractString(f, "message"),
            category = extractString(f, "category"),
            targetRole = extractString(f, "targetRole", "ADMIN_STAFF"),
            targetBookerId = extractString(f, "targetBookerId"),
            shopUid = extractString(f, "shopUid"),
            isRead = extractBool(f, "isRead"),
            createdAtMillis = extractTimestampMillis(f, "createdAt")
        )
    }

    fun toChatGroup(doc: JSONObject): ChatGroup {
        val f = doc.optJSONObject("fields")
        return ChatGroup(
            id = docIdFromName(doc.optString("name")),
            businessId = extractString(f, "businessId"),
            name = extractString(f, "name"),
            description = extractString(f, "description"),
            memberAccountIds = extractStringList(f, "memberAccountIds"),
            memberNames = extractStringList(f, "memberNames"),
            createdByAccountId = extractString(f, "createdByAccountId"),
            createdByName = extractString(f, "createdByName"),
            lastMessagePreview = extractString(f, "lastMessagePreview"),
            lastMessageAtMillis = extractLong(f, "lastMessageAtMillis"),
            isArchived = extractBool(f, "isArchived")
        )
    }

    fun toChatMessage(doc: JSONObject): ChatMessage {
        val f = doc.optJSONObject("fields")
        return ChatMessage(
            id = docIdFromName(doc.optString("name")),
            businessId = extractString(f, "businessId"),
            groupId = extractString(f, "groupId"),
            senderAccountId = extractString(f, "senderAccountId"),
            senderName = extractString(f, "senderName"),
            senderRole = extractString(f, "senderRole"),
            messageType = extractString(f, "messageType", "TEXT"),
            text = extractString(f, "text"),
            mediaBase64 = extractString(f, "mediaBase64"),
            fileName = extractString(f, "fileName"),
            mimeType = extractString(f, "mimeType"),
            durationSec = extractLong(f, "durationSec").toInt(),
            fileSizeBytes = extractLong(f, "fileSizeBytes"),
            sentAtMillis = extractLong(f, "sentAtMillis"),
            dateFormatted = extractString(f, "dateFormatted"),
            timeFormatted = extractString(f, "timeFormatted")
        )
    }

    fun toPlatformOwnerConfig(doc: JSONObject): PlatformOwnerConfig {
        val f = doc.optJSONObject("fields")
        return PlatformOwnerConfig(
            id = docIdFromName(doc.optString("name")),
            username = extractString(f, "username", "MrTufailadmin"),
            authorizedEmail = extractString(f, "authorizedEmail", "muhammadtufailllc007@gmail.com"),
            googleUid = extractString(f, "googleUid"),
            passwordSalt = extractString(f, "passwordSalt"),
            passwordHash = extractString(f, "passwordHash"),
            mustChangePassword = extractBool(f, "mustChangePassword"),
            sessionRevokedBeforeMillis = extractLong(f, "sessionRevokedBeforeMillis"),
            lastLoginMillis = extractLong(f, "lastLoginMillis"),
            deviceCount = extractLong(f, "deviceCount").toInt()
        )
    }

    fun creditToFields(c: CreditTransaction): Map<String, JSONObject> = mapOf(
        "businessId" to stringVal(c.businessId),
        "txCode" to stringVal(c.txCode),
        "shopId" to stringVal(c.shopId),
        "shopUid" to stringVal(c.shopUid),
        "shopName" to stringVal(c.shopName),
        "bookerId" to stringVal(c.bookerId),
        "bookerName" to stringVal(c.bookerName),
        "amountPaisa" to intVal(c.amountPaisa),
        "transactionKind" to stringVal(c.transactionKind),
        "relatedTxId" to stringVal(c.relatedTxId),
        "reason" to stringVal(c.reason),
        "invoiceNumber" to stringVal(c.invoiceNumber),
        "description" to stringVal(c.description),
        "attachmentUri" to stringVal(c.attachmentUri),
        "txDateMillis" to intVal(c.txDateMillis),
        "txDateFormatted" to stringVal(c.txDateFormatted),
        "txTimeFormatted" to stringVal(c.txTimeFormatted),
        "createdByAccountId" to stringVal(c.createdByAccountId),
        "createdByName" to stringVal(c.createdByName),
        "createdByRole" to stringVal(c.createdByRole),
        "dueDateMillis" to intVal(c.dueDateMillis),
        "dueDateFormatted" to stringVal(c.dueDateFormatted),
        "entryTimestampMillis" to intVal(c.entryTimestampMillis),
        "entryDateFormatted" to stringVal(c.entryDateFormatted),
        "entryTimeFormatted" to stringVal(c.entryTimeFormatted),
        "syncStatus" to stringVal(SyncStatus.SYNCED.key),
        "idempotencyToken" to stringVal(c.idempotencyToken),
        "deviceId" to stringVal(c.deviceId)
    )

    fun recoveryToFields(r: RecoveryTransaction): Map<String, JSONObject> = mapOf(
        "businessId" to stringVal(r.businessId),
        "txCode" to stringVal(r.txCode),
        "shopId" to stringVal(r.shopId),
        "shopUid" to stringVal(r.shopUid),
        "shopName" to stringVal(r.shopName),
        "bookerId" to stringVal(r.bookerId),
        "bookerName" to stringVal(r.bookerName),
        "amountPaisa" to intVal(r.amountPaisa),
        "transactionKind" to stringVal(r.transactionKind),
        "relatedTxId" to stringVal(r.relatedTxId),
        "reason" to stringVal(r.reason),
        "paymentMethod" to stringVal(r.paymentMethod),
        "referenceNumber" to stringVal(r.referenceNumber),
        "notes" to stringVal(r.notes),
        "txDateMillis" to intVal(r.txDateMillis),
        "txDateFormatted" to stringVal(r.txDateFormatted),
        "txTimeFormatted" to stringVal(r.txTimeFormatted),
        "createdByAccountId" to stringVal(r.createdByAccountId),
        "createdByName" to stringVal(r.createdByName),
        "createdByRole" to stringVal(r.createdByRole),
        "correctionGroupId" to stringVal(r.correctionGroupId),
        "correctedFromTxId" to stringVal(r.correctedFromTxId),
        "originalAmountPaisa" to intVal(r.originalAmountPaisa),
        "originalTxDateMillis" to intVal(r.originalTxDateMillis),
        "isBackdated" to boolVal(r.isBackdated),
        "reversedAtMillis" to intVal(r.reversedAtMillis),
        "reversedByAccountId" to stringVal(r.reversedByAccountId),
        "reversedByName" to stringVal(r.reversedByName),
        "syncStatus" to stringVal(SyncStatus.SYNCED.key),
        "idempotencyToken" to stringVal(r.idempotencyToken),
        "deviceId" to stringVal(r.deviceId)
    )
}

class DesktopFirestoreClient(
    private val config: FirebaseDesktopConfig,
    private val authClient: DesktopFirebaseAuthClient,
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()
) {
    private fun docResourceName(relativePath: String): String {
        val clean = relativePath.trimStart('/')
        return "projects/${config.projectId}/databases/${config.databaseId}/documents/$clean"
    }

    suspend fun getDocument(relativePath: String): Result<JSONObject?> = runCatching {
        val token = authClient.getValidIdToken().getOrThrow()
        val clean = relativePath.trimStart('/')
        val req = HttpRequest.newBuilder()
            .uri(URI.create("${config.firestoreDocumentsBaseUrl}/$clean"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()
        val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
        when (resp.statusCode()) {
            200 -> JSONObject(resp.body())
            404 -> null
            else -> throw IllegalStateException("Firestore GET $clean failed (${resp.statusCode()}): ${resp.body()}")
        }
    }

    suspend fun listCollection(relativeCollectionPath: String, pageSize: Int = 500): Result<List<JSONObject>> = runCatching {
        val token = authClient.getValidIdToken().getOrThrow()
        val clean = relativeCollectionPath.trimStart('/')
        val all = mutableListOf<JSONObject>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("${config.firestoreDocumentsBaseUrl}/$clean?pageSize=$pageSize")
                if (!pageToken.isNullOrBlank()) {
                    append("&pageToken=").append(URLEncoder.encode(pageToken, Charsets.UTF_8))
                }
            }
            val req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer $token")
                .GET()
                .build()
            val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() !in 200..299) {
                throw IllegalStateException("Firestore LIST $clean failed (${resp.statusCode()}): ${resp.body()}")
            }
            val json = JSONObject(resp.body())
            val docs = json.optJSONArray("documents")
            if (docs != null) {
                for (i in 0 until docs.length()) {
                    docs.optJSONObject(i)?.let { all.add(it) }
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        all
    }

    suspend fun runStructuredQuery(
        parentRelativeDoc: String,
        collectionId: String,
        fieldPath: String,
        value: String,
        op: String = "EQUAL"
    ): Result<List<JSONObject>> = runCatching {
        val token = authClient.getValidIdToken().getOrThrow()
        val parent = if (parentRelativeDoc.isBlank()) {
            "projects/${config.projectId}/databases/${config.databaseId}/documents"
        } else {
            docResourceName(parentRelativeDoc)
        }
        val queryUrl = "https://firestore.googleapis.com/v1/$parent:runQuery"
        val body = JSONObject().put(
            "structuredQuery",
            JSONObject().apply {
                put("from", JSONArray().put(JSONObject().put("collectionId", collectionId)))
                put(
                    "where",
                    JSONObject().put(
                        "fieldFilter",
                        JSONObject().apply {
                            put("field", JSONObject().put("fieldPath", fieldPath))
                            put("op", op)
                            put(
                                "value",
                                if (op == "ARRAY_CONTAINS") JSONObject().put("stringValue", value)
                                else JSONObject().put("stringValue", value)
                            )
                        }
                    )
                )
            }
        )
        val req = HttpRequest.newBuilder()
            .uri(URI.create(queryUrl))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            throw IllegalStateException("Firestore query $collectionId failed (${resp.statusCode()}): ${resp.body()}")
        }
        val arr = JSONArray(resp.body())
        buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optJSONObject("document")?.let { add(it) }
            }
        }
    }

    suspend fun commitWriteWithServerTimestamp(
        relativeDocPath: String,
        fields: Map<String, JSONObject>,
        includeCreatedAt: Boolean = true,
        includeUpdatedAt: Boolean = true
    ): Result<Unit> = runCatching {
        val token = authClient.getValidIdToken().getOrThrow()
        val commitUrl = "https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/${config.databaseId}/documents:commit"
        val body = FirestoreRestCodec.buildServerTimestampCommitWrite(
            fullDocumentName = docResourceName(relativeDocPath),
            fields = fields,
            includeCreatedAt = includeCreatedAt,
            includeUpdatedAt = includeUpdatedAt
        )
        val req = HttpRequest.newBuilder()
            .uri(URI.create(commitUrl))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            throw IllegalStateException("Firestore write $relativeDocPath failed (${resp.statusCode()}): ${resp.body()}")
        }
    }

    suspend fun patchFieldsWithServerTimestamp(
        relativeDocPath: String,
        fields: Map<String, JSONObject>,
        includeUpdatedAt: Boolean = true
    ): Result<Unit> = runCatching {
        val token = authClient.getValidIdToken().getOrThrow()
        val commitUrl = "https://firestore.googleapis.com/v1/projects/${config.projectId}/databases/${config.databaseId}/documents:commit"
        val maskPaths = JSONArray().apply {
            fields.keys.forEach { put(it) }
        }
        val transforms = JSONArray().apply {
            if (includeUpdatedAt) {
                put(
                    JSONObject()
                        .put("fieldPath", "updatedAt")
                        .put("setToServerValue", "REQUEST_TIME")
                )
            }
        }
        val writeObj = JSONObject().apply {
            put(
                "update",
                JSONObject().apply {
                    put("name", docResourceName(relativeDocPath))
                    put("fields", JSONObject(fields))
                }
            )
            put("updateMask", JSONObject().put("fieldPaths", maskPaths))
            if (transforms.length() > 0) {
                put("updateTransforms", transforms)
            }
        }
        val body = JSONObject().put("writes", JSONArray().put(writeObj))
        val req = HttpRequest.newBuilder()
            .uri(URI.create(commitUrl))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            throw IllegalStateException("Firestore patch $relativeDocPath failed (${resp.statusCode()}): ${resp.body()}")
        }
    }

    suspend fun deleteDocument(relativeDocPath: String): Result<Unit> = runCatching {
        val token = authClient.getValidIdToken().getOrThrow()
        val clean = relativeDocPath.trimStart('/')
        val req = HttpRequest.newBuilder()
            .uri(URI.create("${config.firestoreDocumentsBaseUrl}/$clean"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer $token")
            .DELETE()
            .build()
        val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299 && resp.statusCode() != 404) {
            throw IllegalStateException("Firestore DELETE $clean failed (${resp.statusCode()}): ${resp.body()}")
        }
    }
}
