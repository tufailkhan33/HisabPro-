package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.domain.model.AuditLog
import com.example.domain.model.Booker
import com.example.domain.model.Business
import com.example.domain.model.BusinessUser
import com.example.domain.model.CreditTransaction
import com.example.domain.model.RecoveryTransaction
import com.example.domain.model.Shop
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object DataExporter {

    fun buildExportJson(
        business: Business,
        bookers: List<Booker>,
        staffUsers: List<BusinessUser>,
        shops: List<Shop>,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        auditLogs: List<AuditLog>
    ): JSONObject = JSONObject().apply {
        put("backupVersion", "1.0")
        put("workspaceIsolation", "business_scoped")
        put("exportedAt", PakistanTime.formatDateTime())
        put("businessName", business.businessName)
        put("businessCode", business.businessCode)
        put("currency", business.currency)
        put("timezone", business.timezone)
        put("summary", JSONObject().apply {
            put("totalBookers", bookers.size)
            put("totalStaff", staffUsers.size)
            put("totalShops", shops.size)
            put("totalCreditEntries", credits.size)
            put("totalRecoveryEntries", recoveries.size)
            put("totalAuditLogs", auditLogs.size)
        })
        put("bookers", JSONArray().apply {
            bookers.forEach { b ->
                put(JSONObject().apply {
                    put("bookerCode", b.bookerCode)
                    put("fullName", b.fullName)
                    put("username", b.username)
                    put("mobileNumber", b.mobileNumber)
                    put("status", b.status)
                })
            }
        })
        put("staffUsers", JSONArray().apply {
            staffUsers.forEach { u ->
                put(JSONObject().apply {
                    put("userCode", u.userCode)
                    put("fullName", u.fullName)
                    put("username", u.username)
                    put("role", u.role)
                    put("status", u.status)
                })
            }
        })
        put("shops", JSONArray().apply {
            shops.forEach { s ->
                put(JSONObject().apply {
                    put("shopUid", s.shopUid)
                    put("shopName", s.shopName)
                    put("ownerName", s.ownerName)
                    put("bookerName", s.bookerName)
                    put("city", s.city)
                    put("isArchived", s.isArchived)
                })
            }
        })
        put("creditTransactions", JSONArray().apply {
            credits.forEach { c ->
                put(JSONObject().apply {
                    put("txCode", c.txCode)
                    put("shopUid", c.shopUid)
                    put("amountPaisa", c.amountPaisa)
                    put("kind", c.transactionKind)
                    put("date", "${c.txDateFormatted} ${c.txTimeFormatted}")
                    put("createdBy", c.createdByName)
                })
            }
        })
        put("recoveryTransactions", JSONArray().apply {
            recoveries.forEach { r ->
                put(JSONObject().apply {
                    put("txCode", r.txCode)
                    put("shopUid", r.shopUid)
                    put("amountPaisa", r.amountPaisa)
                    put("kind", r.transactionKind)
                    put("method", r.paymentMethod)
                    put("date", "${r.txDateFormatted} ${r.txTimeFormatted}")
                    put("createdBy", r.createdByName)
                })
            }
        })
        put("auditLogsCount", auditLogs.size)
    }

    fun exportBusinessJson(
        context: Context,
        business: Business,
        bookers: List<Booker>,
        staffUsers: List<BusinessUser>,
        shops: List<Shop>,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        auditLogs: List<AuditLog>
    ): File {
        val root = buildExportJson(
            business = business,
            bookers = bookers,
            staffUsers = staffUsers,
            shops = shops,
            credits = credits,
            recoveries = recoveries,
            auditLogs = auditLogs
        )

        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val file = File(dir, "HisabPro_Export_${business.businessCode}_${System.currentTimeMillis()}.json")
        file.writeText(root.toString(2))

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "HisabPro Business Export - ${business.businessName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Export Business Data").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        return file
    }
}
