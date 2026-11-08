package com.konoz.market.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.Category
import com.konoz.market.data.entity.MovementType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class ProductRepositoryTest {

    private lateinit var db: KonozDatabase
    private lateinit var repo: ProductRepository
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        repo = ProductRepository(db) { now }
    }

    @After fun tearDown() = db.close()

    @Test fun create_with_barcodes_and_opening_stock_writes_batch_movement_and_quantity() = runBlocking {
        val id = repo.save(
            ProductInput(
                name = "  Milk  ", costPrice = 1000, salePrice = 1500, internalCode = "M1",
                barcodes = listOf(BarcodeInput("111"), BarcodeInput("222", 12), BarcodeInput("111"), BarcodeInput("  ")),
                openingQtyMilli = 24000, expiryDate = now + 30 * day,
            ),
            userId = null,
        )

        val p = db.products().getById(id)!!
        assertEquals("Milk", p.name)
        assertEquals(24000L, p.currentQtyMilli)
        assertEquals(24000L, db.stock().movementTotal(id))
        assertEquals(24000L, db.stock().batchTotal(id))

        val batch = db.stock().observeBatches(id).first().single()
        assertEquals(now + 30 * day, batch.expiryDate)
        assertEquals(1000L, batch.unitCost)

        val barcodes = db.barcodes().getForProduct(id)
        assertEquals(listOf("111", "222"), barcodes.map { it.code }) // duplicate and blank dropped
        assertTrue(barcodes[0].isPrimary)
        assertFalse(barcodes[1].isPrimary)

        val movement = db.stock().observeMovements(id).first().single()
        assertEquals(MovementType.OPENING, movement.type)
    }

    @Test fun carton_barcode_lookup_returns_units_per_scan() = runBlocking {
        repo.save(ProductInput(name = "Juice", barcodes = listOf(BarcodeInput("A1"), BarcodeInput("CARTON1", 12))), null)
        val (product, units) = repo.lookupBarcode("CARTON1")!!
        assertEquals("Juice", product.name)
        assertEquals(12, units)
        assertEquals(1, repo.lookupBarcode("A1")!!.second)
        assertNull(repo.lookupBarcode("nope"))
    }

    @Test fun duplicate_barcode_is_rejected_and_nothing_is_saved() {
        runBlocking { repo.save(ProductInput(name = "A", barcodes = listOf(BarcodeInput("111"))), null) }

        val ex = assertThrows(ProductException.DuplicateBarcode::class.java) {
            runBlocking { repo.save(ProductInput(name = "B", barcodes = listOf(BarcodeInput("222"), BarcodeInput("111"))), null) }
        }
        assertEquals("111", ex.code)
        runBlocking {
            assertEquals(1, db.products().count())
            assertNull(db.barcodes().findByCode("222"))
        }
    }

    @Test fun duplicate_internal_code_is_rejected() {
        runBlocking { repo.save(ProductInput(name = "A", internalCode = "X1"), null) }
        assertThrows(ProductException.DuplicateInternalCode::class.java) {
            runBlocking { repo.save(ProductInput(name = "B", internalCode = "X1"), null) }
        }
    }

    @Test fun blank_name_and_invalid_values_are_rejected() {
        assertThrows(ProductException.BlankName::class.java) { runBlocking { repo.save(ProductInput(name = "  "), null) } }
        assertThrows(ProductException.InvalidPrice::class.java) { runBlocking { repo.save(ProductInput(name = "A", salePrice = -1), null) } }
        assertThrows(ProductException.InvalidCartonSize::class.java) { runBlocking { repo.save(ProductInput(name = "A", piecesPerCarton = 0), null) } }
        assertThrows(ProductException.InvalidQuantity::class.java) { runBlocking { repo.save(ProductInput(name = "A", openingQtyMilli = 1500), null) } } // piece can't be fractional
    }

    @Test fun update_syncs_barcodes_and_preserves_stock_and_creation_date() = runBlocking {
        val id = repo.save(
            ProductInput(name = "Old", barcodes = listOf(BarcodeInput("111"), BarcodeInput("222")), openingQtyMilli = 24000, salePrice = 1000),
            null,
        )
        val createdAt = db.products().getById(id)!!.createdAt

        repo.save(
            ProductInput(id = id, name = "New", salePrice = 1200, barcodes = listOf(BarcodeInput("333"), BarcodeInput("111", 6))),
            null,
        )

        val p = db.products().getById(id)!!
        assertEquals("New", p.name)
        assertEquals(24000L, p.currentQtyMilli) // stock only changes through movements
        assertEquals(createdAt, p.createdAt)
        val codes = db.barcodes().getForProduct(id)
        assertEquals(setOf("111", "333"), codes.map { it.code }.toSet())
        assertEquals(6, codes.first { it.code == "111" }.unitsPerScan)
        assertTrue(codes.first { it.code == "333" }.isPrimary)
        assertEquals(1, db.stock().observeMovements(id).first().size) // no extra movement on edit
    }

    @Test fun bulk_price_adjustment_applies_percentage_to_the_selected_category_only() = runBlocking {
        val cat = db.categories().insert(Category(name = "c"))
        val a = repo.save(ProductInput(name = "A", categoryId = cat, salePrice = 1000), null)
        val b = repo.save(ProductInput(name = "B", categoryId = cat, salePrice = 999), null)
        val other = repo.save(ProductInput(name = "C", salePrice = 1000), null)

        val matched = repo.bulkAdjustSalePrice(cat, 1000, null) // +10%

        assertEquals(2, matched)
        assertEquals(1100L, db.products().getById(a)!!.salePrice)
        assertEquals(1099L, db.products().getById(b)!!.salePrice) // 999 + 99.9 -> 100 (HALF_UP)
        assertEquals(1000L, db.products().getById(other)!!.salePrice)
    }

    @Test fun deactivate_keeps_product_in_management_search_but_hides_it_from_cashier_search() = runBlocking {
        val id = repo.save(ProductInput(name = "Tea"), null)
        repo.setActive(id, false, null)

        assertEquals(1, repo.observeProducts("Tea").first().size)
        assertEquals(0, db.products().search("Tea").first().size)
        assertNotNull(db.products().getById(id))
    }
}
