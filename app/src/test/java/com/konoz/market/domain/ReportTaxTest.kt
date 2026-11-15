package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.InvoiceStatus
import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.ReturnCondition
import com.konoz.market.data.entity.ReturnItem
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.data.entity.SalesItem
import com.konoz.market.data.entity.SalesReturn
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

/** Tax is collected for the state, so it must never be counted as sales revenue or profit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReportTaxTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: ReportService
    private val utc = TimeZone.getTimeZone("UTC")
    private val now = 1_700_000_000_000L // 2023-11-14T22:13:20Z
    private val hour = 3_600_000L
    private val today get() = Periods.rangeOf(PeriodKind.DAY, now, utc)

    @Before fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = ReportService(db, { now }, utc)
        val userId = db.users().insert(User(name = "Admin", username = "admin", pinHash = "h", salt = "s", role = UserRole.ADMIN, createdAt = now))
        val product = db.products().insert(Product(name = "Milk", createdAt = now))

        // 2 x 50.00 = 100.00 + 14% tax = 114.00; cost 2 x 30.00 = 60.00
        val invoice = db.sales().insertInvoice(
            SalesInvoice(
                uuid = "inv-1", number = 1, userId = userId, status = InvoiceStatus.COMPLETED, subtotal = 10_000,
                taxBasisPoints = 1_400, taxAmount = 1_400, total = 11_400, totalCost = 6_000, createdAt = now - hour, completedAt = now - hour,
            )
        )
        val item = db.sales().insertItems(
            listOf(SalesItem(invoiceId = invoice, productId = product, productName = "Milk", qtyMilli = 2_000, unitPrice = 5_000, unitCostAtSale = 3_000, lineTotal = 10_000))
        ).single()
        // one unit comes back and is resellable: the customer gets 57.00 back (half of 114.00), of which 7.00 is tax
        val ret = db.sales().insertReturn(SalesReturn(uuid = "r1", salesInvoiceId = invoice, userId = userId, refundAmount = 5_700, createdAt = now))
        db.sales().insertReturnItems(
            listOf(ReturnItem(returnId = ret, salesItemId = item, productId = product, qtyMilli = 1_000, refundAmount = 5_700, condition = ReturnCondition.RESELLABLE))
        )
    }

    @After fun tearDown() = db.close()

    @Test fun summary_takes_tax_out_of_sales_and_profit() = runBlocking {
        val s = service.summary(PeriodKind.DAY)

        assertEquals(11_400L, s.grossSales) // what customers paid
        assertEquals(1_400L, s.taxCollected)
        assertEquals(5_700L, s.returnsTotal)
        assertEquals(700L, s.returnsTax)
        assertEquals(5_000L, s.netSales) // (114.00 - 14.00) - (57.00 - 7.00)
        assertEquals(3_000L, s.cogs) // 60.00 - 30.00 resellable
        assertEquals(2_000L, s.grossProfit)
        assertEquals(2_000L, s.estimatedNetProfit)
    }

    @Test fun product_performance_agrees_with_the_summary() = runBlocking {
        val p = service.productPerformance(today).single()

        assertEquals(1_000L, p.qtyMilli)
        assertEquals(5_000L, p.revenue)
        assertEquals(3_000L, p.cost)
        assertEquals(2_000L, p.profit)
    }

    @Test fun daily_series_net_sales_excludes_tax() = runBlocking {
        val today = service.dailySeries(days = 1).single()

        assertEquals(11_400L, today.grossSales)
        assertEquals(5_000L, today.netSales)
    }
}
