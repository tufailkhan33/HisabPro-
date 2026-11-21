package com.example.shared.export

import com.example.shared.model.AuditLog
import com.example.shared.model.Booker
import com.example.shared.model.BookingOrder
import com.example.shared.model.Business
import com.example.shared.model.BusinessUser
import com.example.shared.model.CreditTransaction
import com.example.shared.model.ProductItem
import com.example.shared.model.RecoveryTransaction
import com.example.shared.model.Shop
import com.example.shared.util.SharedPakistanTime
import org.json.JSONArray
import org.json.JSONObject

object SharedBackupBuilder {

    fun buildExportJson(
        business: Business,
        bookers: List<Booker>,
        staffUsers: List<BusinessUser>,
        shops: List<Shop>,
        credits: List<CreditTransaction>,
        recoveries: List<RecoveryTransaction>,
        auditLogs: List<AuditLog>,
        products: List<ProductItem> = emptyList(),
        bookings: List<BookingOrder> = emptyList()
    ): JSONObject = JSONObject().apply {
        put("backupVersion", "1.3")
        put("workspaceIsolation", "business_scoped")
        put("exportedAt", SharedPakistanTime.formatDateTime())
        put("businessName", business.businessName)
        put("businessCode", business.businessCode)
        put("currency", business.currency)
        put("timezone", business.timezone)
        put("summary", JSONObject().apply {
            put("totalBookers", bookers.size)
            put("totalStaff", staffUsers.size)
            put("totalShops", shops.size)
            put("totalProducts", products.size)
            put("totalBookings", bookings.size)
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
        put("products", JSONArray().apply {
            products.forEach { p ->
                put(JSONObject().apply {
                    put("sku", p.sku)
                    put("name", p.name)
                    put("unit", p.unit)
                    put("unitPricePaisa", p.unitPricePaisa)
                    put("stockQty", p.stockQty)
                })
            }
        })
        put("bookings", JSONArray().apply {
            bookings.forEach { bk ->
                put(JSONObject().apply {
                    put("bookingCode", bk.bookingCode)
                    put("shopUid", bk.shopUid)
                    put("productName", bk.productName)
                    put("quantity", bk.quantity)
                    put("totalAmountPaisa", bk.totalAmountPaisa)
                    put("status", bk.status)
                    put("date", bk.bookingDateFormatted)
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
}
