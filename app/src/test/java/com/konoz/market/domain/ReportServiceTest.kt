package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.ExpenseCategory
import com.konoz.market.data.entity.InventoryMovement
import com.konoz.market.data.entity.InvoiceStatus
import com.konoz.market.data.entity.MovementType
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReportServiceTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: ReportService
    private var userId = 0L
    private var productId = 0L
    private val now = 1_700_000_000_000L // 2023-11-14T22:13:20Z
    private val hour = 3_600_000L
    private val day = 24 * hour
    private var movementKey = 0

    @Before fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = ReportService(db, { now }, TimeZone.getTimeZone("UTC"))
        userId = db.users().insert(User(name = "Admin", username = "admin", pinHash = "h", salt = "s", role = UserRole.ADMIN, createdAt = now))
        productId = db.products().insert(Product(name = "Milk", createdAt = now))
    }

    @After fun tearDown() = db.close()

    private suspend fun invoice(number: Long, total: Long, cost: Long, at: Long, itemDiscount: Long = 0, invoiceDiscount: Long = 0): Long =
        db.sales().insertInvoice(
            SalesInvoice(
                uuid = "inv-$number", number = number, userId = userId, status = InvoiceStatus.COMPLETED,
                itemDiscountTotal = itemDiscount, invoiceDiscount = invoiceDiscount,
                total = total, totalCost = cost, createdAt = at, completedAt = at,
            )
        )

    private suspend fun item(invoiceId: Long, qty: Long, price: Long, cost: Long, lineTotal: Long): Long =
        db.sales().insertItems(
            listOf(SalesItem(invoiceId = invoiceId, productId = productId, productName = "Milk", qtyMilli = qty, unitPrice = price, unitCostAtSale = cost, lineTotal = lineTotal))
        ).single()

    private suspend fun salesReturn(uuid: String, invoiceId: Long, salesItemId: Long, qty: Long, refund: Long, condition: ReturnCondition, at: Long) {
        val id = db.sales().insertReturn(SalesReturn(uuid = uuid, salesInvoiceId = invoiceId, userId = userId, refundAmount = refund, createdAt = at))
        db.sales().insertReturnItems(listOf(ReturnItem(returnId = id, salesItemId = salesItemId, productId = productId, qtyMilli = qty, refundAmount = refund, condition = condition)))
    }

    private suspend fun movement(type: MovementType, qty: Long, cost: Long, refType: String? = null) {
        db.stock().insertMovement(
            InventoryMovement(productId = productId, type = type, qtyMilli = qty, unitCost = cost, refType = refType, idempotencyKey = "m${movementKey++}", createdAt = now)
        )
    }

    /** Today: two invoices, a resellable and a damaged return, an expense and some write-offs. Three days ago: one more invoice. */
    private suspend fun seed() {
        val inv1 = invoice(1, total = 10_000, cost = 6_000, at = now - hour)
        val item1 = item(inv1, qty = 2_000, price = 5_000, cost = 3_000, lineTotal = 10_000)
        val inv2 = invoice(2, total = 4_000, cost = 2_500, at = now - 2 * hour, itemDiscount = 200, invoiceDiscount = 100)
        val item2 = item(inv2, qty = 1_000, price = 4_000, cost = 2_500, lineTotal = 4_000)
        invoice(3, total = 6_000, cost = 3_500, at = now - 3 * day)

        salesReturn("r1", inv1, item1, qty = 1_000, refund = 5_000, condition = ReturnCondition.RESELLABLE, at = now)
        salesReturn("r2", inv2, item2, qty = 500, refund = 2_000, condition = ReturnCondition.DAMAGED, at = now)

        service.let { }
        com.konoz.market.domain.ExpenseService(db, { now }, TimeZone.getTimeZone("UTC"))
            .add(ExpenseInput(1_000, ExpenseCategory.ELECTRICITY, "Electricity", at = now), userId)

        movement(MovementType.DAMAGED, -2_000, 1_500) // 3000 loss
        movement(MovementType.EXPIRED, -500, 2_000) // 1000 loss
        movement(MovementType.ADJUSTMENT, 1_000, 1_000) // a gain: ignored
        movement(MovementType.DAMAGED, -1_000, 3_000, refType = "SALES_RETURN") // from a customer return: ignored
        movement(MovementType.UNIT_CONVERSION, -1_000, 3_000) // not a loss
    }

    @Test fun daily_summary_separates_sales_returns_cost_expenses_and_losses() = runBlocking {
        seed()

        val s = service.summary(PeriodKind.DAY)

        assertEquals(14_000L, s.grossSales)
        assertEquals(300L, s.discounts)
        assertEquals(2, s.invoiceCount)
        assertEquals(7_000L, s.averageInvoice)
        assertEquals(7_000L, s.returnsTotal)
        assertEquals(7_000L, s.netSales)
        // sold cost 8500, minus 3000 for the one resellable unit that came back (the damaged return stays in COGS)
        assertEquals(5_500L, s.cogs)
        assertEquals(1_500L, s.grossProfit)
        assertEquals(1_000L, s.expenses)
        assertEquals(4_000L, s.inventoryLosses)
        assertEquals(-3_500L, s.estimatedNetProfit)
    }

    @Test fun an_empty_period_is_all_zeros() = runBlocking {
        val s = service.summary(PeriodKind.DAY)

        assertEquals(0, s.invoiceCount)
        assertEquals(0L, s.averageInvoice)
        assertEquals(0L, s.grossSales)
        assertEquals(0L, s.estimatedNetProfit)
    }

    @Test fun average_invoice_is_rounded_half_up() = runBlocking {
        invoice(1, total = 1_000, cost = 0, at = now - hour)
        invoice(2, total = 1_001, cost = 0, at = now - 2 * hour)
        invoice(3, total = 1_001, cost = 0, at = now - 3 * hour)

        // 3002 / 3 = 1000.67 -> 1001
        assertEquals(1_001L, service.summary(PeriodKind.DAY).averageInvoice)
    }

    @Test fun month_summary_includes_earlier_days_of_the_month() = runBlocking {
        seed()

        val s = service.summary(PeriodKind.MONTH)

        assertEquals(20_000L, s.grossSales)
        assertEquals(3, s.invoiceCount)
    }

    @Test fun daily_series_has_one_point_per_day_oldest_first() = runBlocking {
        seed()

        val series = service.dailySeries(days = 7)

        assertEquals(7, series.size)
        assertEquals(7_000L, series.last().netSales) // today: 14000 - 7000
        assertEquals(14_000L, series.last().grossSales)
        assertEquals(6_000L, series[3].netSales) // three days ago
        assertEquals(listOf(0L, 0L, 0L), series.take(3).map { it.netSales })
        assertEquals(series.map { it.dayStart }.sorted(), series.map { it.dayStart })
    }

    @Test fun comparison_uses_the_previous_period() = runBlocking {
        seed()

        val day = service.compare(PeriodKind.DAY)
        assertEquals(14_000L, day.current.grossSales)
        assertEquals(0L, day.previous.grossSales) // Nov 13 had no sales

        val month = service.compare(PeriodKind.MONTH)
        assertEquals(20_000L, month.current.grossSales)
        assertEquals(0L, month.previous.grossSales) // October had no sales
    }
}
