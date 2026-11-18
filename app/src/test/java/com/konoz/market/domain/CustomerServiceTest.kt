package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.InvoiceStatus
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomerServiceTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: CustomerService
    private var userId = 0L
    private var invoiceId = 0L
    private var heldId = 0L
    private var clock = 1_700_000_000_000L

    @Before fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = CustomerService(db) { clock }
        userId = db.users().insert(User(name = "Sara", username = "sara", pinHash = "h", salt = "s", role = UserRole.CASHIER, createdAt = clock))
        invoiceId = db.sales().insertInvoice(
            SalesInvoice(uuid = "u1", number = 1, userId = userId, status = InvoiceStatus.COMPLETED, total = 1_000, createdAt = clock - 1_000, completedAt = clock)
        )
        heldId = db.sales().insertInvoice(SalesInvoice(uuid = "u2", userId = userId, status = InvoiceStatus.HELD, createdAt = clock))
    }

    @After fun tearDown() = db.close()

    private suspend fun errorOf(block: suspend () -> Unit): CustomerError? =
        try {
            block()
            null
        } catch (e: CustomerException) {
            e.error
        }

    @Test fun sending_an_invoice_saves_the_customer_with_the_normalized_phone_and_records_the_name_on_the_invoice() = runBlocking {
        val saved = service.saveForInvoice(invoiceId, "  Ahmed   Ali ", "01012345678")

        assertEquals("Ahmed Ali", saved!!.name)
        assertEquals("201012345678", saved.phone)
        assertEquals(listOf("201012345678"), db.customers().observeAll().first().map { it.phone })
        val invoice = db.sales().getInvoice(invoiceId)!!
        assertEquals("Ahmed Ali", invoice.customerName)
        assertEquals("201012345678", invoice.customerPhone)
    }

    @Test fun the_same_phone_typed_another_way_updates_the_name_instead_of_adding_a_second_customer() = runBlocking {
        service.saveForInvoice(invoiceId, "Ahmed", "01012345678")
        clock += 5_000
        service.saveForInvoice(invoiceId, "Ahmed Ali", "+20 101 234 5678")

        val all = db.customers().observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Ahmed Ali", all.single().name)
        assertEquals(1_700_000_000_000L, all.single().createdAt)
        assertEquals(1_700_000_005_000L, all.single().updatedAt)
    }

    @Test fun a_phone_without_a_name_is_refused_and_nothing_is_saved() = runBlocking {
        assertEquals(CustomerError.NAME_REQUIRED, errorOf { service.saveForInvoice(invoiceId, "   ", "01012345678") })

        assertEquals(0, db.customers().observeAll().first().size)
        assertNull(db.sales().getInvoice(invoiceId)!!.customerName)
    }

    @Test fun an_invalid_phone_is_refused_and_nothing_is_saved() = runBlocking {
        assertEquals(CustomerError.INVALID_PHONE, errorOf { service.saveForInvoice(invoiceId, "Ahmed", "123") })

        assertEquals(0, db.customers().observeAll().first().size)
        assertNull(db.sales().getInvoice(invoiceId)!!.customerName)
    }

    @Test fun a_name_alone_is_recorded_on_the_invoice_but_does_not_add_to_the_list() = runBlocking {
        val saved = service.saveForInvoice(invoiceId, "Ahmed", "")

        assertNull(saved)
        assertEquals(0, db.customers().observeAll().first().size)
        val invoice = db.sales().getInvoice(invoiceId)!!
        assertEquals("Ahmed", invoice.customerName)
        assertNull(invoice.customerPhone)
    }

    @Test fun nothing_typed_changes_nothing() = runBlocking {
        service.saveForInvoice(invoiceId, "Ahmed", "01012345678")

        assertNull(service.saveForInvoice(invoiceId, "", ""))

        assertEquals("Ahmed", db.sales().getInvoice(invoiceId)!!.customerName)
    }

    @Test fun a_held_or_unknown_invoice_is_refused_and_the_customer_is_not_saved() = runBlocking {
        assertEquals(CustomerError.NOT_FOUND, errorOf { service.saveForInvoice(heldId, "Ahmed", "01012345678") })
        assertEquals(CustomerError.NOT_FOUND, errorOf { service.saveForInvoice(999, "Ahmed", "01012345678") })

        assertNull(db.customers().getByPhone("201012345678")) // rolled back with the failed invoice update
    }

    @Test fun adding_a_phone_that_is_already_saved_is_refused() = runBlocking {
        service.add("Ahmed", "01012345678")

        assertEquals(CustomerError.PHONE_TAKEN, errorOf { service.add("Other", "+201012345678") })
        assertEquals(CustomerError.NAME_REQUIRED, errorOf { service.add(" ", "01112345678") })
        assertEquals(CustomerError.INVALID_PHONE, errorOf { service.add("Mona", "abc") })
        assertEquals(1, db.customers().observeAll().first().size)
    }

    @Test fun editing_a_customer_can_change_the_name_and_phone_but_not_take_another_customers_phone() = runBlocking {
        val a = service.add("Ahmed", "01012345678")
        service.add("Mona", "01112345678")

        assertEquals(CustomerError.PHONE_TAKEN, errorOf { service.update(a.id, "Ahmed", "01112345678") })

        val edited = service.update(a.id, "Ahmed Ali", "01212345678")
        assertEquals("Ahmed Ali", edited.name)
        assertEquals("201212345678", db.customers().getById(a.id)!!.phone)
        assertEquals(CustomerError.NOT_FOUND, errorOf { service.update(999, "X", "01012345678") })
    }

    @Test fun editing_or_deleting_a_customer_never_changes_an_invoice_already_sent() = runBlocking {
        val saved = service.saveForInvoice(invoiceId, "Ahmed", "01012345678")!!

        service.update(saved.id, "Ahmed Ali", "01012345678")
        service.delete(saved.id)

        assertNull(db.customers().getById(saved.id))
        assertEquals("Ahmed", db.sales().getInvoice(invoiceId)!!.customerName)
    }

    @Test fun findByPhone_matches_any_way_of_typing_the_number() = runBlocking {
        service.add("Ahmed", "01012345678")

        assertNotNull(service.findByPhone("+20 101 234 5678"))
        assertNull(service.findByPhone("01112345678"))
        assertNull(service.findByPhone("010"))
    }

    @Test fun a_loaded_invoice_carries_the_customer_name() = runBlocking {
        service.saveForInvoice(invoiceId, "Ahmed", "01012345678")

        val data = InvoiceLoader(db).load(invoiceId)!!

        assertEquals("Ahmed", data.customerName)
        assertEquals("201012345678", data.customerPhone)
    }

    @Test fun displayPhone_writes_egyptian_numbers_locally_and_others_with_a_plus_and_round_trips() {
        assertEquals("01012345678", CustomerService.displayPhone("201012345678"))
        assertEquals("+966501234567", CustomerService.displayPhone("966501234567"))
        assertEquals("201012345678", PhoneNumbers.toWhatsApp(CustomerService.displayPhone("201012345678")))
        assertEquals("966501234567", PhoneNumbers.toWhatsApp(CustomerService.displayPhone("966501234567")))
    }
}
