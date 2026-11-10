package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.InventoryMovement
import com.konoz.market.data.entity.MovementType
import com.konoz.market.data.entity.StockBatch
import com.konoz.market.data.entity.UnitType
import com.konoz.market.data.repo.ProductInput
import com.konoz.market.data.repo.ProductRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StockAdjustmentServiceTest {

    private lateinit var db: KonozDatabase
    private lateinit var service: StockAdjustmentService
    private lateinit var repo: ProductRepository
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        service = StockAdjustmentService(db) { now }
        repo = ProductRepository(db) { now }
    }

    @After fun tearDown() = db.close()

    private fun addProduct(name: String = "P", cost: Long = 1000, unit: UnitType = UnitType.PIECE): Long = runBlocking {
        repo.save(ProductInput(name = name, costPrice = cost, salePrice = 1500, unit = unit), null)
    }

    private fun addBatch(productId: Long, qty: Long, cost: Long, expiry: Long?): Long = runBlocking {
        val id = db.stock().insertBatch(StockBatch(productId = productId, qtyMilli = qty, unitCost = cost, expiryDate = expiry, receivedAt = now))
        db.stock().insertMovement(
            InventoryMovement(productId = productId, batchId = id, type = MovementType.PURCHASE, qtyMilli = qty, unitCost = cost, idempotencyKey = "T:$id", createdAt = now)
        )
        db.products().adjustQty(productId, qty)
        id
    }

    private fun qty(productId: Long) = runBlocking { db.products().getById(productId)!!.currentQtyMilli }

    @Test fun a_positive_adjustment_adds_a_batch_at_the_current_cost() = runBlocking {
        val p = addProduct(cost = 1000)
        val r = service.adjust("a1", p, 3000, "found on shelf", null)

        assertEquals(3000L, r.costValue)
        val batch = db.stock().observeBatches(p).first().single()
        assertEquals(3000L, batch.qtyMilli)
        assertEquals(1000L, batch.unitCost)
        assertEquals(3000L, qty(p))
        assertEquals(3000L, db.stock().movementTotal(p))
        assertEquals(MovementType.ADJUSTMENT, db.stock().observeMovements(p).first().single().type)
    }

    @Test fun a_negative_adjustment_takes_stock_out_fefo_and_reports_the_cost() = runBlocking {
        val p = addProduct()
        val late = addBatch(p, 5000, 1200, now + 60 * day)
        val soon = addBatch(p, 2000, 1000, now + 10 * day)

        val r = service.adjust("a2", p, -3000, "count", null)

        assertEquals(3200L, r.costValue) // 2.000 x 10.00 + 1.000 x 12.00
        val batches = db.stock().observeBatches(p).first().associateBy { it.id }
        assertEquals(0L, batches.getValue(soon).qtyMilli)
        assertEquals(4000L, batches.getValue(late).qtyMilli)
        assertEquals(4000L, qty(p))
        assertEquals(4000L, db.stock().movementTotal(p))
        assertEquals(4000L, db.stock().batchTotal(p))
    }

    @Test fun an_adjustment_can_never_push_stock_below_zero() {
        val p = addProduct()
        addBatch(p, 2000, 1000, null)
        assertThrows(StockException.InsufficientStock::class.java) {
            runBlocking { service.adjust("a3", p, -2001, "oops", null) }
        }
        assertEquals(2000L, qty(p))
    }

    @Test fun a_reason_and_a_non_zero_quantity_are_required() {
        val p = addProduct()
        assertThrows(StockException.ReasonRequired::class.java) { runBlocking { service.adjust("a", p, 1000, "  ", null) } }
        assertThrows(StockException.InvalidQuantity::class.java) { runBlocking { service.adjust("b", p, 0, "x", null) } }
        assertThrows(StockException.InvalidQuantity::class.java) { runBlocking { service.adjust("c", p, 1500, "x", null) } } // piece: no fractions
    }

    @Test fun writing_off_expired_goods_takes_the_oldest_batch_first() = runBlocking {
        val p = addProduct()
        val expired = addBatch(p, 2000, 800, now - day)
        val fresh = addBatch(p, 5000, 1000, now + 30 * day)

        val r = service.writeOff("w1", p, 3000, MovementType.EXPIRED, "expired", null)

        assertEquals(2600L, r.costValue) // 2.000 x 8.00 + 1.000 x 10.00
        val batches = db.stock().observeBatches(p).first().associateBy { it.id }
        assertEquals(0L, batches.getValue(expired).qtyMilli)
        assertEquals(4000L, batches.getValue(fresh).qtyMilli)
        val written = db.stock().observeMovements(p).first().filter { it.type == MovementType.EXPIRED }
        assertEquals(-3000L, written.sumOf { it.qtyMilli })
        assertEquals(4000L, qty(p))
    }

    @Test fun a_write_off_larger_than_the_stock_or_of_a_wrong_type_is_rejected() {
        val p = addProduct()
        addBatch(p, 2000, 1000, null)
        assertThrows(StockException.InsufficientStock::class.java) {
            runBlocking { service.writeOff("w", p, 3000, MovementType.DAMAGED, null, null) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service.writeOff("w2", p, 1000, MovementType.SALE, null, null) }
        }
        assertEquals(2000L, qty(p))
    }

    @Test fun converting_cartons_to_pieces_moves_the_cost_and_keeps_the_expiry() = runBlocking {
        val carton = addProduct("Carton", cost = 12_000, unit = UnitType.CARTON)
        val pieces = addProduct("Piece", cost = 1000)
        addBatch(carton, 3000, 12_000, now + 30 * day)

        val r = service.convertUnits("c1", carton, 2000, pieces, 24_000, null)

        assertEquals(24_000L, r.costValue) // 2 cartons x 120.00 = 240.00
        val newBatch = db.stock().observeBatches(pieces).first().single()
        assertEquals(24_000L, newBatch.qtyMilli)
        assertEquals(1000L, newBatch.unitCost) // 240.00 / 24 pieces = 10.00
        assertEquals(now + 30 * day, newBatch.expiryDate)
        assertEquals(1000L, qty(carton))
        assertEquals(24_000L, qty(pieces))
        assertEquals(1000L, db.stock().movementTotal(carton))
        assertEquals(24_000L, db.stock().movementTotal(pieces))
        assertTrue(db.stock().observeMovements(pieces).first().all { it.type == MovementType.UNIT_CONVERSION })
    }

    @Test fun converting_a_product_into_itself_is_rejected() {
        val p = addProduct()
        addBatch(p, 5000, 1000, null)
        assertThrows(StockException.SameProduct::class.java) {
            runBlocking { service.convertUnits("c", p, 1000, p, 1000, null) }
        }
    }

    @Test fun retrying_the_same_uuid_applies_the_operation_once() = runBlocking {
        val p = addProduct()
        val first = service.adjust("same", p, 3000, "found", null)
        val second = service.adjust("same", p, 3000, "found", null)
        assertFalse(first.duplicate)
        assertTrue(second.duplicate)
        assertEquals(3000L, qty(p))
    }
}
