package com.example.domain.model

import com.example.util.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinancialEngineTest {

    @Test
    fun computeShopBalances_andReversals_exactPaisaMath() {
        val shop = Shop(
            id = "s1",
            businessId = "b1",
            shopUid = "SHP-1001",
            shopName = "Shop ABC",
            bookerId = "bk1",
            bookerName = "Ali"
        )
        val credits = listOf(
            CreditTransaction(id = "c1", shopId = "s1", amountPaisa = 5_000_000L, transactionKind = "CREDIT", txDateMillis = 1000L),
            CreditTransaction(id = "c2", shopId = "s1", amountPaisa = 3_000_000L, transactionKind = "CREDIT", txDateMillis = 2000L),
            CreditTransaction(id = "c3", shopId = "s1", amountPaisa = 1_000_000L, transactionKind = "CREDIT_REVERSAL", txDateMillis = 3000L)
        )
        val recoveries = listOf(
            RecoveryTransaction(id = "r1", shopId = "s1", amountPaisa = 2_000_000L, transactionKind = "RECOVERY", txDateMillis = 2500L),
            RecoveryTransaction(id = "r2", shopId = "s1", amountPaisa = 1_000_000L, transactionKind = "RECOVERY", txDateMillis = 3500L),
            RecoveryTransaction(id = "r3", shopId = "s1", amountPaisa = 500_000L, transactionKind = "RECOVERY_REVERSAL", txDateMillis = 4000L)
        )

        val summary = FinancialEngine.computeShopBalances(listOf(shop), credits, recoveries).first()
        // Net Credit = 50k + 30k - 10k = 70k (7_000_000)
        assertEquals(7_000_000L, summary.totalCreditPaisa)
        // Net Recovered = 20k + 10k - 5k = 25k (2_500_000)
        assertEquals(2_500_000L, summary.totalRecoveredPaisa)
        // Outstanding = 70k - 25k = 45k (4_500_000)
        assertEquals(4_500_000L, summary.outstandingPaisa)
    }

    @Test
    fun computePeriodReport_calculatesHistoricalClosingBalanceAsOfEndDate() {
        val shop = Shop(id = "s1", shopUid = "SHP-1", shopName = "Shop One", bookerId = "bk1", bookerName = "Ali")
        val credits = listOf(
            CreditTransaction(id = "c1", shopId = "s1", amountPaisa = 5_000_000L, transactionKind = "CREDIT", txDateMillis = 1000L),
            CreditTransaction(id = "c2", shopId = "s1", amountPaisa = 3_000_000L, transactionKind = "CREDIT", txDateMillis = 5000L)
        )
        val recoveries = listOf(
            RecoveryTransaction(id = "r1", shopId = "s1", amountPaisa = 2_000_000L, transactionKind = "RECOVERY", txDateMillis = 2000L),
            RecoveryTransaction(id = "r2", shopId = "s1", amountPaisa = 1_000_000L, transactionKind = "RECOVERY", txDateMillis = 6000L)
        )

        // Period [1500..3000]: only recovery r1 (20k) happened inside period,
        // while closing balance as of t=3000 is 50k - 20k = 30k (even though today's balance is 50k!)
        val report = FinancialEngine.computePeriodReport(listOf(shop), credits, recoveries, 1500L, 3000L)
        assertEquals(0L, report.periodCreditPaisa)
        assertEquals(2_000_000L, report.periodRecoveredPaisa)
        assertEquals(3_000_000L, report.closingOutstandingAsOfEndDatePaisa)
        assertEquals(5_000_000L, report.currentOutstandingPaisa)
    }

    @Test
    fun currencyFormatter_parsesAndFormatsPkrSafely() {
        assertEquals("Rs. 500,000", CurrencyFormatter.formatPkr(50_000_000L))
        assertEquals("Rs. 125.50", CurrencyFormatter.formatPkr(12_550L))
        assertEquals(50_000_000L, CurrencyFormatter.parsePkrToPaisa("500,000"))
        assertNull(CurrencyFormatter.parsePkrToPaisa("-100"))
    }

    @Test
    fun computeDashboardAndBookerSummaries_aggregatesGrandTotalsAccurately() {
        val bookers = listOf(
            Booker(id = "bk1", bookerCode = "BK-1", fullName = "Ali"),
            Booker(id = "bk2", bookerCode = "BK-2", fullName = "Imran")
        )
        val shops = listOf(
            Shop(id = "s1", shopUid = "SHP1", shopName = "A", bookerId = "bk1", bookerName = "Ali"),
            Shop(id = "s2", shopUid = "SHP2", shopName = "B", bookerId = "bk1", bookerName = "Ali"),
            Shop(id = "s3", shopUid = "SHP3", shopName = "C", bookerId = "bk2", bookerName = "Imran", isArchived = true)
        )
        val credits = listOf(
            CreditTransaction(id = "c1", shopId = "s1", amountPaisa = 1_000_000L, transactionKind = "CREDIT", txDateMillis = 100L),
            CreditTransaction(id = "c2", shopId = "s2", amountPaisa = 2_500_000L, transactionKind = "CREDIT", txDateMillis = 200L),
            CreditTransaction(id = "c3", shopId = "s3", amountPaisa = 500_000L, transactionKind = "CREDIT", txDateMillis = 300L)
        )
        val recoveries = listOf(
            RecoveryTransaction(id = "r1", shopId = "s1", amountPaisa = 400_000L, transactionKind = "RECOVERY", txDateMillis = 150L),
            RecoveryTransaction(id = "r2", shopId = "s2", amountPaisa = 500_000L, transactionKind = "RECOVERY", txDateMillis = 250L)
        )
        val staff = listOf(BusinessUser(id = "u1", fullName = "Staff One"))

        val shopBalances = FinancialEngine.computeShopBalances(shops, credits, recoveries)
        val bookerSums = FinancialEngine.computeBookerSummaries(bookers, shopBalances)
        val dashboard = FinancialEngine.computeDashboardSummary(shopBalances, bookers, staff)

        assertEquals(4_000_000L, dashboard.totalCreditPaisa)
        assertEquals(900_000L, dashboard.totalRecoveredPaisa)
        assertEquals(3_100_000L, dashboard.totalOutstandingPaisa)
        assertEquals(3, dashboard.totalShops)
        assertEquals(2, dashboard.activeShops)
        assertEquals(2, dashboard.totalBookers)
        assertEquals(1, dashboard.totalStaff)

        val bk1 = bookerSums.first { it.booker.id == "bk1" }
        assertEquals(2, bk1.assignedShopsCount)
        assertEquals(3_500_000L, bk1.totalCreditPaisa)
        assertEquals(900_000L, bk1.totalRecoveredPaisa)
        assertEquals(2_600_000L, bk1.outstandingPaisa)
    }
}
