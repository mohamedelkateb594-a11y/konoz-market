package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.InvoiceStatus
import com.konoz.market.data.entity.PaymentMethod
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import com.konoz.market.data.repo.BarcodeInput
import com.konoz.market.data.repo.ProductInput
import com.konoz.market.data.repo.ProductRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CashierSessionTest {

    private lateinit var db: KonozDatabase
    private lateinit var repo: ProductRepository
    private lateinit var sales: SaleService
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L
    private var adminId = 0L
    private var cashierId = 0L
    private var counter = 0

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        repo = ProductRepository(db) { now }
        sales = SaleService(db) { now }
        runBlocking {
            adminId = db.users().insert(User(name = "a", username = "a", pinHash = "x", salt = "y", role = UserRole.ADMIN, createdAt = now))
            cashierId = db.users().insert(User(name = "c", username = "c", pinHash = "x", salt = "y", role = UserRole.CASHIER, createdAt = now))
        }
    }

    @After fun tearDown() = db.close()

    private fun newSession(userId: Long = adminId): CashierSession {
        // the first session created in a test gets cart-1, the next cart-2 ...
        return CashierSession(db, repo, sales, { userId }, { now }, { "cart-${++counter}" })
    }

    private fun addProduct(price: Long = 1500, stockMilli: Long = 10_000, expiry: Long? = null, barcodes: List<BarcodeInput> = listOf(BarcodeInput("111"), BarcodeInput("CARTON", 12))): Long =
        runBlocking {
            repo.save(ProductInput(name = "Milk", costPrice = 1000, salePrice = price, barcodes = barcodes, openingQtyMilli = stockMilli, expiryDate = expiry), null)
        }

    private suspend fun started(userId: Long = adminId): CashierSession = newSession(userId).also { it.start() }

    private fun cash(amount: Long) = listOf(PaymentInput(PaymentMethod.CASH, amount))

    @Test fun scanning_twice_increases_quantity_and_a_carton_barcode_adds_n() = runBlocking {
        addProduct()
        val s = started()
        s.scan("111"); s.scan("111")
        assertEquals(2000L, s.cart.value.items.single().qtyMilli)
        s.scan("CARTON")
        assertEquals(14000L, s.cart.value.items.single().qtyMilli)
    }

    @Test fun unknown_barcode_posts_a_message_and_leaves_the_cart_empty() = runBlocking {
        val s = started()
        s.scan(" 999 ")
        assertEquals(CashierMessage.UnknownBarcode("999"), s.message.value)
        assertTrue(s.cart.value.isEmpty)
    }

    @Test fun cart_is_autosaved_and_restored_after_a_restart() = runBlocking {
        addProduct()
        val first = started()
        first.scan("111"); first.scan("111")
        val held = db.sales().getByUuid("cart-1")
        assertNotNull(held)
        assertEquals(InvoiceStatus.HELD, held!!.status)

        // "app killed": a brand new session over the same database
        val second = started()
        assertEquals("cart-1", second.cart.value.uuid)
        assertEquals(2000L, second.cart.value.items.single().qtyMilli)
    }

    @Test fun emptying_the_cart_removes_its_autosaved_draft() = runBlocking {
        val id = addProduct()
        val s = started()
        s.scan("111")
        assertNotNull(db.sales().getByUuid("cart-1"))
        s.remove(id)
        assertNull(db.sales().getByUuid("cart-1"))
    }

    @Test fun checkout_completes_the_same_invoice_deducts_stock_and_starts_a_new_cart() = runBlocking {
        val productId = addProduct()
        val s = started()
        s.scan("111"); s.scan("111")
        val draftId = db.sales().getByUuid("cart-1")!!.id

        val outcome = s.checkout(cash(3000))

        assertTrue(outcome is CheckoutOutcome.Success)
        val result = (outcome as CheckoutOutcome.Success).result
        assertEquals(draftId, result.invoiceId)
        assertEquals(3000L, result.total)
        assertEquals(InvoiceStatus.COMPLETED, db.sales().getInvoice(draftId)!!.status)
        assertEquals(8000L, db.products().getById(productId)!!.currentQtyMilli)
        assertTrue(s.cart.value.isEmpty)
        assertEquals("cart-2", s.cart.value.uuid)
        assertTrue(db.sales().observeHeld().first().isEmpty())
    }

    @Test fun hold_parks_the_cart_and_resume_brings_it_back() = runBlocking {
        addProduct()
        val s = started()
        s.scan("111")
        val heldId = s.hold()!!
        assertTrue(s.cart.value.isEmpty)
        assertEquals(1, db.sales().observeHeld().first().size)

        assertTrue(s.resume(heldId))
        assertEquals("cart-1", s.cart.value.uuid)
        assertEquals(1000L, s.cart.value.items.single().qtyMilli)
    }

    @Test fun a_cashier_cannot_override_prices_or_exceed_the_discount_limit() = runBlocking {
        val id = addProduct(price = 1500)
        val s = started(cashierId)
        s.scan("111")

        s.setPrice(id, 100)
        assertEquals(CashierMessage.NotAllowed, s.message.value)
        assertEquals(1500L, s.cart.value.items.single().unitPrice)

        s.consumeMessage()
        s.setLineDiscount(id, 151) // limit is 10% of 15.00 = 1.50
        assertEquals(CashierMessage.NotAllowed, s.message.value)
        assertEquals(0L, s.cart.value.items.single().discount)

        s.consumeMessage()
        s.setLineDiscount(id, 150)
        assertEquals(1350L, s.cart.value.totals()!!.total)
    }

    @Test fun an_admin_can_override_the_price_and_it_is_audited() = runBlocking {
        val id = addProduct(price = 1500)
        val s = started(adminId)
        s.scan("111")
        s.setPrice(id, 1200)
        assertEquals(1200L, s.cart.value.items.single().unitPrice)
        val log = db.audit().observeRecent(10).first().first { it.action == "PRICE_OVERRIDE" }
        assertEquals("1500 -> 1200", log.details)
    }

    @Test fun expired_stock_warns_on_scan_and_needs_confirmation_to_sell() = runBlocking {
        addProduct(stockMilli = 5000, expiry = now - day)
        val s = started()
        s.scan("111")
        assertEquals(CashierMessage.ExpiredWarning("Milk"), s.message.value)
        s.consumeMessage()

        val blocked = s.checkout(cash(1500))
        assertEquals(CheckoutOutcome.Failed, blocked)
        assertEquals(CashierMessage.ConfirmExpiredSale, s.message.value)
        assertEquals(1, s.cart.value.items.size) // cart kept

        val confirmed = s.confirmExpiredSale()
        assertTrue(confirmed is CheckoutOutcome.Success)
        assertTrue(s.cart.value.isEmpty)
    }

    @Test fun piece_products_reject_fractional_quantities() = runBlocking {
        val id = addProduct()
        val s = started()
        s.scan("111")
        s.setQty(id, 1500)
        assertEquals(CashierMessage.InvalidInput, s.message.value)
        assertEquals(1000L, s.cart.value.items.single().qtyMilli)
    }

    @Test fun short_payment_keeps_the_cart_and_reports_the_amounts() = runBlocking {
        addProduct()
        val s = started()
        s.scan("111")
        val outcome = s.checkout(cash(1000))
        assertEquals(CheckoutOutcome.Failed, outcome)
        assertEquals(CashierMessage.PaymentShort(1500, 1000), s.message.value)
        assertEquals(1, s.cart.value.items.size)
    }
}
