package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.Category
import com.konoz.market.data.entity.ExpenseCategory
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReportBreakdownTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: ReportService
    private val utc = TimeZone.getTimeZone("UTC")
    private var userId = 0L
    private var milk = 0L
    private var rice = 0L
    private var soap = 0L
    private val now = 1_700_000_000_000L // 2023-11-14T22:13:20Z, a Tuesday
    private val hour = 3_600_000L
    private val today get() = Periods.rangeOf(PeriodKind.DAY, now, utc)

    @Before fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = ReportService(db, { now }, utc)
        userId = db.users().insert(User(name = "Admin", username = "admin", pinHash = "h", salt = "s", role = UserRole.ADMIN, createdAt = now))
        val dairy = db.categories().insert(Category(name = "Dairy"))
        milk = db.products().insert(Product(name = "Milk", categoryId = dairy, createdAt = now))
        rice = db.products().insert(Product(name = "Rice", createdAt = now))
        soap = db.products().insert(Product(name = "Soap", currentQtyMilli = 5_000, createdAt = now)) // in stock, never sold
        db.products().insert(Product(name = "Tea", currentQtyMilli = 0, createdAt = now)) // no stock, never sold
    }

    @After fun tearDown() = db.close()

    private suspend fun invoice(number: Long, total: Long, at: Long): Long =
        db.sales().insertInvoice(
            SalesInvoice(uuid = "inv-$number", number = number, userId = userId, status = InvoiceStatus.COMPLETED, total = total, createdAt = at, completedAt = at)
        )

    private suspend fun item(invoiceId: Long, productId: Long, name: String, qty: Long, cost: Long, lineTotal: Long): Long =
        db.sales().insertItems(
            listOf(SalesItem(invoiceId = invoiceId, productId = productId, productName = name, qtyMilli = qty, unitPrice = 0, unitCostAtSale = cost, lineTotal = lineTotal))
        ).single()

    /**
     * Invoice 1 (21:13): milk 3 x 5.00 (cost 3.00) + rice 1 x 20.00 (cost 12.00), total 35.00.
     * Invoice 2 (20:13): milk 1 x 5.00, total 5.00. One resellable milk is returned for 5.00.
     */
    private suspend fun seed() {
        val inv1 = invoice(1, total = 35_000, at = now - hour)
        val milk1 = item(inv1, milk, "Milk", qty = 3_000, cost = 3_000, lineTotal = 15_000)
        item(inv1, rice, "Rice", qty = 1_000, cost = 12_000, lineTotal = 20_000)
        val inv2 = invoice(2, total = 5_000, at = now - 2 * hour)
        item(inv2, milk, "Milk", qty = 1_000, cost = 3_000, lineTotal = 5_000)

        val ret = db.sales().insertReturn(SalesReturn(uuid = "r1", salesInvoiceId = inv1, userId = userId, refundAmount = 5_000, createdAt = now))
        db.sales().insertReturnItems(
            listOf(ReturnItem(returnId = ret, salesItemId = milk1, productId = milk, qtyMilli = 1_000, refundAmount = 5_000, condition = ReturnCondition.RESELLABLE))
        )
    }

    @Test fun product_performance_is_net_of_returns() = runBlocking {
        seed()

        val byName = service.productPerformance(today).associateBy { it.name }

        val m = byName.getValue("Milk")
        assertEquals(3_000L, m.qtyMilli) // 4 sold - 1 returned
        assertEquals(15_000L, m.revenue) // 20.00 - 5.00 refund
        assertEquals(9_000L, m.cost) // 12.00 - 3.00 for the resellable unit
        assertEquals(6_000L, m.profit)
        val r = byName.getValue("Rice")
        assertEquals(1_000L, r.qtyMilli)
        assertEquals(8_000L, r.profit)
    }

    @Test fun rankings_by_quantity_profit_and_least_sold() = runBlocking {
        seed()

        assertEquals(listOf("Milk", "Rice"), service.topByQuantity(today).map { it.name })
        assertEquals(listOf("Rice", "Milk"), service.topByProfit(today).map { it.name })
        assertEquals(listOf("Rice"), service.leastSold(today, limit = 1).map { it.name })
    }

    @Test fun non_moving_lists_only_in_stock_products_without_sales() = runBlocking {
        seed()

        assertEquals(listOf("Soap"), service.nonMoving(today).map { it.name })
    }

    @Test fun sales_by_category_groups_uncategorized_products_together() = runBlocking {
        seed()

        val cats = service.salesByCategory(today)

        assertNull(cats[0].categoryName) // rice: 20.00 revenue
        assertEquals(20_000L, cats[0].revenue)
        assertEquals(8_000L, cats[0].profit)
        assertEquals("Dairy", cats[1].categoryName)
        assertEquals(15_000L, cats[1].revenue)
        assertEquals(6_000L, cats[1].profit)
    }

    @Test fun expenses_are_grouped_by_category_biggest_first() = runBlocking {
        val expenses = ExpenseService(db, { now }, utc)
        expenses.add(ExpenseInput(1_000, ExpenseCategory.ELECTRICITY, "a", at = now), userId)
        expenses.add(ExpenseInput(5_000, ExpenseCategory.RENT, "b", at = now), userId)
        expenses.add(ExpenseInput(500, ExpenseCategory.ELECTRICITY, "c", at = now), userId)

        val result = service.expensesByCategory(today)

        assertEquals(listOf(ExpenseByCategory(ExpenseCategory.RENT, 5_000), ExpenseByCategory(ExpenseCategory.ELECTRICITY, 1_500)), result)
    }

    @Test fun peak_hours_count_invoices_by_completion_hour() = runBlocking {
        seed()

        val hours = service.peakHours(today)

        assertEquals(24, hours.size)
        assertEquals(HourStat(21, 1, 35_000), hours[21])
        assertEquals(HourStat(20, 1, 5_000), hours[20])
        assertEquals(0, hours[3].invoiceCount)
    }

    @Test fun weekday_average_divides_by_the_weekdays_that_have_passed() = runBlocking {
        seed()
        val month = Periods.rangeOf(PeriodKind.MONTH, now, utc)

        val stats = service.weekdayComparison(month).associateBy { it.dayOfWeek }

        val tuesday = stats.getValue(Calendar.TUESDAY)
        assertEquals(2, tuesday.occurrences) // Nov 7 and Nov 14
        assertEquals(40_000L, tuesday.sales)
        assertEquals(20_000L, tuesday.averageSales)
        assertEquals(0L, stats.getValue(Calendar.WEDNESDAY).sales)
        assertEquals(7, stats.size)
    }
}
