package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.AuditLog
import com.example.domain.model.Booker
import com.example.domain.model.Business
import com.example.domain.model.BusinessSettings
import com.example.domain.model.BusinessUser
import com.example.domain.model.CreditTransaction
import com.example.domain.model.PendingSyncTransaction
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.Shop
import com.example.domain.model.StaffPermission
import com.example.domain.model.SyncHistoryRecord

@Entity(
    tableName = "cached_businesses",
    indices = [Index("businessCode")]
)
data class CachedBusinessEntity(
    @PrimaryKey val id: String,
    val businessName: String,
    val businessCode: String,
    val ownerUid: String,
    val ownerName: String,
    val ownerMobile: String,
    val ownerEmail: String,
    val currency: String,
    val timezone: String,
    val allowOverRecovery: Boolean,
    val status: String,
    val suspendedReason: String,
    val lastActivityMillis: Long
)

@Entity(
    tableName = "cached_business_settings"
)
data class CachedBusinessSettingsEntity(
    @PrimaryKey val businessId: String,
    val businessName: String,
    val businessAddress: String,
    val contactPhone: String,
    val allowOverRecovery: Boolean,
    val requireInvoiceNumber: Boolean,
    val updatedBy: String
)

@Entity(
    tableName = "cached_bookers",
    indices = [Index("businessId"), Index("username")]
)
data class CachedBookerEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val bookerCode: String,
    val fullName: String,
    val username: String,
    val mobileNumber: String,
    val address: String,
    val notes: String,
    val status: String,
    val mustChangePassword: Boolean,
    val sessionRevokedBeforeMillis: Long,
    val lastLoginMillis: Long
)

@Entity(
    tableName = "cached_staff_users",
    indices = [Index("businessId"), Index("username")]
)
data class CachedStaffUserEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val userCode: String,
    val fullName: String,
    val username: String,
    val mobileNumber: String,
    val email: String,
    val role: String,
    val status: String,
    val mustChangePassword: Boolean,
    val sessionRevokedBeforeMillis: Long,
    val lastLoginMillis: Long
)

@Entity(
    tableName = "cached_permissions",
    indices = [Index("businessId")]
)
data class CachedPermissionEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val userId: String,
    val viewDashboard: Boolean,
    val viewShops: Boolean,
    val addShops: Boolean,
    val editShops: Boolean,
    val addCredit: Boolean,
    val editCredit: Boolean,
    val recordRecovery: Boolean,
    val viewRecoveryHistory: Boolean,
    val viewBookers: Boolean,
    val manageBookers: Boolean,
    val viewReports: Boolean,
    val generatePdfs: Boolean,
    val searchShops: Boolean,
    val viewActivityLogs: Boolean
)

@Entity(
    tableName = "cached_shops",
    indices = [Index("businessId"), Index("bookerId"), Index("shopUid")]
)
data class CachedShopEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val shopUid: String,
    val shopName: String,
    val bookerId: String,
    val bookerName: String,
    val ownerName: String,
    val mobileNumber: String,
    val address: String,
    val city: String,
    val openingCreditPaisa: Long,
    val openingDateMillis: Long,
    val notes: String,
    val isArchived: Boolean,
    val createdByAccountId: String,
    val createdByName: String
)

@Entity(
    tableName = "cached_credit_transactions",
    indices = [Index("businessId"), Index("shopId"), Index("bookerId"), Index("txDateMillis"), Index("syncStatus")]
)
data class CachedCreditTxEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val txCode: String,
    val shopId: String,
    val shopUid: String,
    val shopName: String,
    val bookerId: String,
    val bookerName: String,
    val amountPaisa: Long,
    val transactionKind: String,
    val relatedTxId: String,
    val reason: String,
    val invoiceNumber: String,
    val description: String,
    val attachmentUri: String,
    val txDateMillis: Long,
    val txDateFormatted: String,
    val txTimeFormatted: String,
    val createdByAccountId: String,
    val createdByName: String,
    val createdByRole: String,
    val dueDateMillis: Long,
    val dueDateFormatted: String,
    val entryTimestampMillis: Long,
    val entryDateFormatted: String,
    val entryTimeFormatted: String,
    val syncStatus: String,
    val idempotencyToken: String,
    val deviceId: String
)

@Entity(
    tableName = "cached_recovery_transactions",
    indices = [Index("businessId"), Index("shopId"), Index("bookerId"), Index("txDateMillis"), Index("syncStatus")]
)
data class CachedRecoveryTxEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val txCode: String,
    val shopId: String,
    val shopUid: String,
    val shopName: String,
    val bookerId: String,
    val bookerName: String,
    val amountPaisa: Long,
    val transactionKind: String,
    val relatedTxId: String,
    val reason: String,
    val paymentMethod: String,
    val referenceNumber: String,
    val notes: String,
    val txDateMillis: Long,
    val txDateFormatted: String,
    val txTimeFormatted: String,
    val createdByAccountId: String,
    val createdByName: String,
    val createdByRole: String,
    val correctionGroupId: String,
    val correctedFromTxId: String,
    val originalAmountPaisa: Long,
    val originalTxDateMillis: Long,
    val isBackdated: Boolean,
    val reversedAtMillis: Long,
    val reversedByAccountId: String,
    val reversedByName: String,
    val syncStatus: String,
    val idempotencyToken: String,
    val deviceId: String
)

@Entity(
    tableName = "cached_audit_logs",
    indices = [Index("businessId"), Index("timestampMillis")]
)
data class CachedAuditLogEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val activityCode: String,
    val userId: String,
    val username: String,
    val role: String,
    val action: String,
    val shopUid: String,
    val previousValue: String,
    val newValue: String,
    val dateFormatted: String,
    val timeFormatted: String,
    val timestampMillis: Long,
    val description: String
)

@Entity(
    tableName = "pending_sync_transactions",
    indices = [Index("businessId"), Index("syncStatus"), Index("localDeviceTimestamp")]
)
data class PendingSyncTxEntity(
    @PrimaryKey val localId: String,
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
    val txType: String,
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
    val errorMessage: String,
    val retryCount: Int,
    val serverSyncedAtMillis: Long
)

@Entity(
    tableName = "sync_history_records",
    indices = [Index("businessId"), Index("syncedAtMillis")]
)
data class SyncHistoryEntity(
    @PrimaryKey val id: String,
    val businessId: String,
    val localTxId: String,
    val shopUid: String,
    val txType: String,
    val amountPaisa: Long,
    val syncStatus: String,
    val message: String,
    val syncedAtMillis: Long
)

@Entity(
    tableName = "cached_offline_sessions",
    indices = [Index("businessCode"), Index("username")]
)
data class CachedSessionEntity(
    @PrimaryKey val ticketKey: String,
    val businessId: String,
    val businessCode: String,
    val businessName: String,
    val accountId: String,
    val username: String,
    val fullName: String,
    val role: String,
    val bookerId: String,
    val encryptedPasswordSalt: String,
    val encryptedPasswordHash: String,
    val mustChangePassword: Boolean,
    val lastSyncedMillis: Long
)

// Mapping Extensions
fun Business.toEntity() = CachedBusinessEntity(
    id = id,
    businessName = businessName,
    businessCode = businessCode,
    ownerUid = ownerUid,
    ownerName = ownerName,
    ownerMobile = ownerMobile,
    ownerEmail = ownerEmail,
    currency = currency,
    timezone = timezone,
    allowOverRecovery = allowOverRecovery,
    status = status,
    suspendedReason = suspendedReason,
    lastActivityMillis = lastActivityMillis
)

fun CachedBusinessEntity.toDomain() = Business(
    id = id,
    businessName = businessName,
    businessCode = businessCode,
    ownerUid = ownerUid,
    ownerName = ownerName,
    ownerMobile = ownerMobile,
    ownerEmail = ownerEmail,
    currency = currency,
    timezone = timezone,
    allowOverRecovery = allowOverRecovery,
    status = status,
    suspendedReason = suspendedReason,
    lastActivityMillis = lastActivityMillis
)

fun BusinessSettings.toEntity() = CachedBusinessSettingsEntity(
    businessId = businessId.ifBlank { id },
    businessName = businessName,
    businessAddress = businessAddress,
    contactPhone = contactPhone,
    allowOverRecovery = allowOverRecovery,
    requireInvoiceNumber = requireInvoiceNumber,
    updatedBy = updatedBy
)

fun CachedBusinessSettingsEntity.toDomain() = BusinessSettings(
    id = businessId,
    businessId = businessId,
    businessName = businessName,
    businessAddress = businessAddress,
    contactPhone = contactPhone,
    allowOverRecovery = allowOverRecovery,
    requireInvoiceNumber = requireInvoiceNumber,
    updatedBy = updatedBy
)

fun Booker.toEntity() = CachedBookerEntity(
    id = id,
    businessId = businessId,
    bookerCode = bookerCode,
    fullName = fullName,
    username = username,
    mobileNumber = mobileNumber,
    address = address,
    notes = notes,
    status = status,
    mustChangePassword = mustChangePassword,
    sessionRevokedBeforeMillis = sessionRevokedBeforeMillis,
    lastLoginMillis = lastLoginMillis
)

fun CachedBookerEntity.toDomain() = Booker(
    id = id,
    businessId = businessId,
    bookerCode = bookerCode,
    fullName = fullName,
    username = username,
    mobileNumber = mobileNumber,
    address = address,
    notes = notes,
    status = status,
    mustChangePassword = mustChangePassword,
    sessionRevokedBeforeMillis = sessionRevokedBeforeMillis,
    lastLoginMillis = lastLoginMillis
)

fun BusinessUser.toEntity() = CachedStaffUserEntity(
    id = id,
    businessId = businessId,
    userCode = userCode,
    fullName = fullName,
    username = username,
    mobileNumber = mobileNumber,
    email = email,
    role = role,
    status = status,
    mustChangePassword = mustChangePassword,
    sessionRevokedBeforeMillis = sessionRevokedBeforeMillis,
    lastLoginMillis = lastLoginMillis
)

fun CachedStaffUserEntity.toDomain() = BusinessUser(
    id = id,
    businessId = businessId,
    userCode = userCode,
    fullName = fullName,
    username = username,
    mobileNumber = mobileNumber,
    email = email,
    role = role,
    status = status,
    mustChangePassword = mustChangePassword,
    sessionRevokedBeforeMillis = sessionRevokedBeforeMillis,
    lastLoginMillis = lastLoginMillis
)

fun StaffPermission.toEntity() = CachedPermissionEntity(
    id = id.ifBlank { "${businessId}_$userId" },
    businessId = businessId,
    userId = userId,
    viewDashboard = viewDashboard,
    viewShops = viewShops,
    addShops = addShops,
    editShops = editShops,
    addCredit = addCredit,
    editCredit = editCredit,
    recordRecovery = recordRecovery,
    viewRecoveryHistory = viewRecoveryHistory,
    viewBookers = viewBookers,
    manageBookers = manageBookers,
    viewReports = viewReports,
    generatePdfs = generatePdfs,
    searchShops = searchShops,
    viewActivityLogs = viewActivityLogs
)

fun CachedPermissionEntity.toDomain() = StaffPermission(
    id = id,
    businessId = businessId,
    userId = userId,
    viewDashboard = viewDashboard,
    viewShops = viewShops,
    addShops = addShops,
    editShops = editShops,
    addCredit = addCredit,
    editCredit = editCredit,
    recordRecovery = recordRecovery,
    viewRecoveryHistory = viewRecoveryHistory,
    viewBookers = viewBookers,
    manageBookers = manageBookers,
    viewReports = viewReports,
    generatePdfs = generatePdfs,
    searchShops = searchShops,
    viewActivityLogs = viewActivityLogs
)

fun Shop.toEntity() = CachedShopEntity(
    id = id,
    businessId = businessId,
    shopUid = shopUid,
    shopName = shopName,
    bookerId = bookerId,
    bookerName = bookerName,
    ownerName = ownerName,
    mobileNumber = mobileNumber,
    address = address,
    city = city,
    openingCreditPaisa = openingCreditPaisa,
    openingDateMillis = openingDateMillis,
    notes = notes,
    isArchived = isArchived,
    createdByAccountId = createdByAccountId,
    createdByName = createdByName
)

fun CachedShopEntity.toDomain() = Shop(
    id = id,
    businessId = businessId,
    shopUid = shopUid,
    shopName = shopName,
    bookerId = bookerId,
    bookerName = bookerName,
    ownerName = ownerName,
    mobileNumber = mobileNumber,
    address = address,
    city = city,
    openingCreditPaisa = openingCreditPaisa,
    openingDateMillis = openingDateMillis,
    notes = notes,
    isArchived = isArchived,
    createdByAccountId = createdByAccountId,
    createdByName = createdByName
)

fun CreditTransaction.toEntity() = CachedCreditTxEntity(
    id = id,
    businessId = businessId,
    txCode = txCode,
    shopId = shopId,
    shopUid = shopUid,
    shopName = shopName,
    bookerId = bookerId,
    bookerName = bookerName,
    amountPaisa = amountPaisa,
    transactionKind = transactionKind,
    relatedTxId = relatedTxId,
    reason = reason,
    invoiceNumber = invoiceNumber,
    description = description,
    attachmentUri = attachmentUri,
    txDateMillis = txDateMillis,
    txDateFormatted = txDateFormatted,
    txTimeFormatted = txTimeFormatted,
    createdByAccountId = createdByAccountId,
    createdByName = createdByName,
    createdByRole = createdByRole,
    dueDateMillis = dueDateMillis,
    dueDateFormatted = dueDateFormatted,
    entryTimestampMillis = entryTimestampMillis,
    entryDateFormatted = entryDateFormatted,
    entryTimeFormatted = entryTimeFormatted,
    syncStatus = syncStatus,
    idempotencyToken = idempotencyToken,
    deviceId = deviceId
)

fun CachedCreditTxEntity.toDomain() = CreditTransaction(
    id = id,
    businessId = businessId,
    txCode = txCode,
    shopId = shopId,
    shopUid = shopUid,
    shopName = shopName,
    bookerId = bookerId,
    bookerName = bookerName,
    amountPaisa = amountPaisa,
    transactionKind = transactionKind,
    relatedTxId = relatedTxId,
    reason = reason,
    invoiceNumber = invoiceNumber,
    description = description,
    attachmentUri = attachmentUri,
    txDateMillis = txDateMillis,
    txDateFormatted = txDateFormatted,
    txTimeFormatted = txTimeFormatted,
    createdByAccountId = createdByAccountId,
    createdByName = createdByName,
    createdByRole = createdByRole,
    dueDateMillis = dueDateMillis,
    dueDateFormatted = dueDateFormatted,
    entryTimestampMillis = entryTimestampMillis,
    entryDateFormatted = entryDateFormatted,
    entryTimeFormatted = entryTimeFormatted,
    syncStatus = syncStatus,
    idempotencyToken = idempotencyToken,
    deviceId = deviceId
)

fun RecoveryTransaction.toEntity() = CachedRecoveryTxEntity(
    id = id,
    businessId = businessId,
    txCode = txCode,
    shopId = shopId,
    shopUid = shopUid,
    shopName = shopName,
    bookerId = bookerId,
    bookerName = bookerName,
    amountPaisa = amountPaisa,
    transactionKind = transactionKind,
    relatedTxId = relatedTxId,
    reason = reason,
    paymentMethod = paymentMethod,
    referenceNumber = referenceNumber,
    notes = notes,
    txDateMillis = txDateMillis,
    txDateFormatted = txDateFormatted,
    txTimeFormatted = txTimeFormatted,
    createdByAccountId = createdByAccountId,
    createdByName = createdByName,
    createdByRole = createdByRole,
    correctionGroupId = correctionGroupId,
    correctedFromTxId = correctedFromTxId,
    originalAmountPaisa = originalAmountPaisa,
    originalTxDateMillis = originalTxDateMillis,
    isBackdated = isBackdated,
    reversedAtMillis = reversedAtMillis,
    reversedByAccountId = reversedByAccountId,
    reversedByName = reversedByName,
    syncStatus = syncStatus,
    idempotencyToken = idempotencyToken,
    deviceId = deviceId
)

fun CachedRecoveryTxEntity.toDomain() = RecoveryTransaction(
    id = id,
    businessId = businessId,
    txCode = txCode,
    shopId = shopId,
    shopUid = shopUid,
    shopName = shopName,
    bookerId = bookerId,
    bookerName = bookerName,
    amountPaisa = amountPaisa,
    transactionKind = transactionKind,
    relatedTxId = relatedTxId,
    reason = reason,
    paymentMethod = paymentMethod,
    referenceNumber = referenceNumber,
    notes = notes,
    txDateMillis = txDateMillis,
    txDateFormatted = txDateFormatted,
    txTimeFormatted = txTimeFormatted,
    createdByAccountId = createdByAccountId,
    createdByName = createdByName,
    createdByRole = createdByRole,
    correctionGroupId = correctionGroupId,
    correctedFromTxId = correctedFromTxId,
    originalAmountPaisa = originalAmountPaisa,
    originalTxDateMillis = originalTxDateMillis,
    isBackdated = isBackdated,
    reversedAtMillis = reversedAtMillis,
    reversedByAccountId = reversedByAccountId,
    reversedByName = reversedByName,
    syncStatus = syncStatus,
    idempotencyToken = idempotencyToken,
    deviceId = deviceId
)

fun AuditLog.toEntity() = CachedAuditLogEntity(
    id = id,
    businessId = businessId,
    activityCode = activityCode,
    userId = userId,
    username = username,
    role = role,
    action = action,
    shopUid = shopUid,
    previousValue = previousValue,
    newValue = newValue,
    dateFormatted = dateFormatted,
    timeFormatted = timeFormatted,
    timestampMillis = timestampMillis,
    description = description
)

fun CachedAuditLogEntity.toDomain() = AuditLog(
    id = id,
    businessId = businessId,
    activityCode = activityCode,
    userId = userId,
    username = username,
    role = role,
    action = action,
    shopUid = shopUid,
    previousValue = previousValue,
    newValue = newValue,
    dateFormatted = dateFormatted,
    timeFormatted = timeFormatted,
    timestampMillis = timestampMillis,
    description = description
)

fun PendingSyncTransaction.toEntity() = PendingSyncTxEntity(
    localId = localId,
    businessId = businessId,
    userId = userId,
    username = username,
    userRole = userRole,
    deviceId = deviceId,
    shopId = shopId,
    shopUid = shopUid,
    shopName = shopName,
    bookerId = bookerId,
    bookerName = bookerName,
    txType = txType,
    amountPaisa = amountPaisa,
    originalTxDateMillis = originalTxDateMillis,
    dueDateMillis = dueDateMillis,
    invoiceOrRefNumber = invoiceOrRefNumber,
    paymentMethod = paymentMethod,
    descriptionOrNotes = descriptionOrNotes,
    localDeviceTimestamp = localDeviceTimestamp,
    syncStatus = syncStatus,
    idempotencyToken = idempotencyToken,
    auditMetadata = auditMetadata,
    errorMessage = errorMessage,
    retryCount = retryCount,
    serverSyncedAtMillis = serverSyncedAtMillis
)

fun PendingSyncTxEntity.toDomain() = PendingSyncTransaction(
    localId = localId,
    businessId = businessId,
    userId = userId,
    username = username,
    userRole = userRole,
    deviceId = deviceId,
    shopId = shopId,
    shopUid = shopUid,
    shopName = shopName,
    bookerId = bookerId,
    bookerName = bookerName,
    txType = txType,
    amountPaisa = amountPaisa,
    originalTxDateMillis = originalTxDateMillis,
    dueDateMillis = dueDateMillis,
    invoiceOrRefNumber = invoiceOrRefNumber,
    paymentMethod = paymentMethod,
    descriptionOrNotes = descriptionOrNotes,
    localDeviceTimestamp = localDeviceTimestamp,
    syncStatus = syncStatus,
    idempotencyToken = idempotencyToken,
    auditMetadata = auditMetadata,
    errorMessage = errorMessage,
    retryCount = retryCount,
    serverSyncedAtMillis = serverSyncedAtMillis
)

fun SyncHistoryRecord.toEntity() = SyncHistoryEntity(
    id = id,
    businessId = businessId,
    localTxId = localTxId,
    shopUid = shopUid,
    txType = txType,
    amountPaisa = amountPaisa,
    syncStatus = syncStatus,
    message = message,
    syncedAtMillis = syncedAtMillis
)

fun SyncHistoryEntity.toDomain() = SyncHistoryRecord(
    id = id,
    businessId = businessId,
    localTxId = localTxId,
    shopUid = shopUid,
    txType = txType,
    amountPaisa = amountPaisa,
    syncStatus = syncStatus,
    message = message,
    syncedAtMillis = syncedAtMillis
)
