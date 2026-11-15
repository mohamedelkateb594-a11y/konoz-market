package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.InvoiceStatus
import com.konoz.market.data.entity.Payment
import com.konoz.market.data.entity.PaymentMethod
import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.data.entity.SalesItem
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvoiceLoaderTest {

    private lateinit var db: KonozDatabase
    private lateinit var loader: InvoiceLoader
    private var userId = 0L
    private var productId = 0L
    private val now = 1_700_000_000_000L

    @Before fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        loader = InvoiceLoader(db)
        userId = db.users().insert(User(name = "Sara", username = "sara", pinHash = "h", salt = "s", role = UserRole.CASHIER, createdAt = now))
        productId = db.products().insert(Product(name = "Milk", createdAt = now))
    }

    @After fun tearDown() = db.close()

    private suspend fun invoice(status: InvoiceStatus, number: Long?, total: Long, changeDue: Long = 0, invoiceDiscount: Long = 0, notes: String? = null): Long =
        db.sales().insertInvoice(
            SalesInvoice(
                uuid = "u-${number ?: "held"}", number = number, userId = userId, status = status, total = total, changeDue = changeDue,
                invoiceDiscount = invoiceDiscount, notes = notes, createdAt = now - 5_000, completedAt = if (number != null) now else null,
            )
        )

    private suspend fun item(invoiceId: Long, name: String, qty: Long, price: Long, lineTotal: Long, discount: Long = 0) =
        db.sales().insertItems(
            listOf(SalesItem(invoiceId = invoiceId, productId = productId, productName = name, qtyMilli = qty, unitPrice = price, unitCostAtSale = 0, discount = discount, lineTotal = lineTotal))
        )

    @Test fun a_completed_invoice_is_loaded_with_its_lines_totals_and_cashier() = runBlocking {
        val id = invoice(InvoiceStatus.COMPLETED, number = 7, total = 9_000, invoiceDiscount = 500, notes = "  gift  ")
        item(id, "Milk", qty = 2_000, price = 3_000, lineTotal = 5_500, discount = 500)
        item(id, "Rice", qty = 1_000, price = 4_000, lineTotal = 4_000)
        db.sales().insertPayments(listOf(Payment(invoiceId = id, method = PaymentMethod.CARD, amount = 9_000, createdAt = now)))

        val d = loader.load(id)!!

        assertEquals(7L, d.number)
        assertEquals(now, d.dateMillis)
        assertEquals("Sara", d.cashier)
        assertEquals(listOf("Milk", "Rice"), d.lines.map { it.name })
        assertEquals(500L, d.lines[0].discount)
        assertEquals(9_500L, d.subtotal) // sum of the line totals
        assertEquals(500L, d.invoiceDiscount)
        assertEquals(9_000L, d.total)
        assertEquals("gift", d.notes)
        assertEquals(9_000L, d.paid)
        assertEquals(0L, d.changeDue)
    }

    @Test fun the_cash_line_shows_what_was_handed_over_so_the_lines_add_up_to_the_amount_paid() = runBlocking {
        val id = invoice(InvoiceStatus.COMPLETED, number = 8, total = 6_000, changeDue = 4_000)
        item(id, "Milk", 2_000, 3_000, 6_000)
        // stored net of change: 60.00 of the 100.00 handed over stayed in the drawer
        db.sales().insertPayments(listOf(Payment(invoiceId = id, method = PaymentMethod.CASH, amount = 6_000, createdAt = now)))

        val d = loader.load(id)!!

        assertEquals(10_000L, d.payments.single().amount)
        assertEquals(10_000L, d.paid)
        assertEquals(4_000L, d.changeDue)
    }

    @Test fun with_mixed_payments_only_the_first_cash_line_takes_the_change() = runBlocking {
        val id = invoice(InvoiceStatus.COMPLETED, number = 9, total = 10_000, changeDue = 1_000)
        item(id, "Milk", 1_000, 10_000, 10_000)
        db.sales().insertPayments(
            listOf(
                Payment(invoiceId = id, method = PaymentMethod.CARD, amount = 4_000, createdAt = now),
                Payment(invoiceId = id, method = PaymentMethod.CASH, amount = 6_000, createdAt = now),
            )
        )

        val d = loader.load(id)!!

        assertEquals(listOf(4_000L, 7_000L), d.payments.map { it.amount })
        assertEquals(11_000L, d.paid)
    }

    @Test fun held_and_unknown_invoices_give_null() = runBlocking {
        val held = invoice(InvoiceStatus.HELD, number = null, total = 1_000)

        assertNull(loader.load(held))
        assertNull(loader.load(999))
    }
}
