package com.konoz.market.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import com.konoz.market.domain.PurchaseDraft
import com.konoz.market.domain.PurchaseLine
import com.konoz.market.domain.PurchaseService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogRepositoryTest {

    private lateinit var db: KonozDatabase
    private lateinit var catalog: CatalogRepository
    private val now = 1_700_000_000_000L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        catalog = CatalogRepository(db) { now }
    }

    @After fun tearDown() = db.close()

    @Test fun create_then_update_keeps_the_id_and_trims_and_blanks_optional_fields() = runBlocking {
        val id = catalog.saveSupplier(0, "  Acme  ", " 0100 ", "", null)
        val created = catalog.getSupplier(id)!!
        assertEquals("Acme", created.name)
        assertEquals("0100", created.phone)
        assertNull(created.address)

        val same = catalog.saveSupplier(id, "Acme Co", "", "Cairo", "note")
        assertEquals(id, same)
        val updated = catalog.getSupplier(id)!!
        assertEquals("Acme Co", updated.name)
        assertNull(updated.phone)
        assertEquals("Cairo", updated.address)
        assertEquals(created.createdAt, updated.createdAt)
    }

    @Test fun a_blank_name_is_rejected() {
        assertThrows(ProductException.BlankName::class.java) { runBlocking { catalog.saveSupplier(0, "   ", null, null, null) } }
    }

    @Test fun a_supplier_without_invoices_can_be_deleted_but_one_with_invoices_cannot() = runBlocking {
        val free = catalog.addSupplier("Free", null)
        assertTrue(catalog.deleteSupplier(free))
        assertNull(catalog.getSupplier(free))

        val busy = catalog.addSupplier("Busy", null)
        val userId = db.users().insert(User(name = "t", username = "t", pinHash = "x", salt = "y", role = UserRole.ADMIN, createdAt = now))
        val product = ProductRepository(db) { now }.save(ProductInput(name = "P"), null)
        PurchaseService(db) { now }.approve(PurchaseDraft("u", busy, userId, listOf(PurchaseLine(product, 1000, 500))))

        assertFalse(catalog.deleteSupplier(busy))
        assertEquals("Busy", catalog.getSupplier(busy)!!.name)
    }
}
