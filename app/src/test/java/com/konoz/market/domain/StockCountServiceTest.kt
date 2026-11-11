package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.Category
import com.konoz.market.data.entity.CountScope
import com.konoz.market.data.entity.CountStatus
import com.konoz.market.data.entity.MovementType
import com.konoz.market.data.entity.UnitType
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import com.konoz.market.data.repo.ProductInput
import com.konoz.market.data.repo.ProductRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class StockCountServiceTest {

    private lateinit var db: KonozDatabase
    private lateinit var adjustments: StockAdjustmentService
    private lateinit var service: StockCountService
    private lateinit var repo: ProductRepository
    private val now = 1_700_000_000_000L
    private var userId = 0L

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        adjustments = StockAdjustmentService(db) { now }
        service = StockCountService(db, adjustments) { now }
        repo = ProductRepository(db) { now }
        userId = runBlocking {
            db.users().insert(User(name = "Admin", username = "admin", pinHash = "h", salt = "s", role = UserRole.ADMIN, createdAt = now))
        }
    }

    @After fun tearDown() = db.close()

    private fun product(
        name: String, cost: Long = 1000, stock: Long = 10_000, category: Long? = null,
        unit: UnitType = UnitType.PIECE, active: Boolean = true,
    ): Long = runBlocking {
        repo.save(
            ProductInput(name = name, costPrice = cost, salePrice = 1500, categoryId = category, unit = unit, isActive = active, openingQtyMilli = stock),
            null,
        )
    }

    private fun qty(id: Long) = runBlocking { db.products().getById(id)!!.currentQtyMilli }

    @Test fun a_count_snapshots_active_products_and_respects_the_category_scope() = runBlocking {
        val cat = db.categories().insert(Category(name = "c"))
        val a = product("A", stock = 10_000)
        product("B", cost = 500, stock = 5_000)
        product("Inactive", active = false)
        val c = product("C", category = cat)

        val all = service.start(CountScope.ALL, null, userId)
        val items = db.stockCounts().getItems(all)
        assertEquals(3, items.size) // inactive product is not counted
        val itemA = items.first { it.productId == a }
        assertEquals(10_000L, itemA.systemQtyMilli)
        assertEquals(1000L, itemA.unitCostAtCount)
        assertNull(itemA.countedQtyMilli)

        val byCategory = service.start(CountScope.CATEGORY, cat, userId)
        assertEquals(listOf(c), db.stockCounts().getItems(byCategory).map { it.productId })
    }

    @Test fun starting_needs_a_category_for_a_category_count_and_at_least_one_product() {
        assertThrows(CountException.CategoryRequired::class.java) { runBlocking { service.start(CountScope.CATEGORY, null, userId) } }
        val empty = runBlocking { db.categories().insert(Category(name = "empty")) }
        assertThrows(CountException.NoProducts::class.java) { runBlocking { service.start(CountScope.CATEGORY, empty, userId) } }
    }

    @Test fun counted_quantities_are_validated() = runBlocking {
        val a = product("A")
        val id = service.start(CountScope.ALL, null, userId)

        assertThrows(CountException.InvalidQuantity::class.java) { runBlocking { service.setCounted(id, a, -1000) } }
        assertThrows(CountException.InvalidQuantity::class.java) { runBlocking { service.setCounted(id, a, 1500) } } // piece: no fractions
        assertThrows(CountException.ItemNotInCount::class.java) { runBlocking { service.setCounted(id, 9999, 1000) } }

        service.setCounted(id, a, 8000)
        assertEquals(8000L, db.stockCounts().getItem(id, a)!!.countedQtyMilli)
        service.setCounted(id, a, null) // clearing is allowed
        assertNull(db.stockCounts().getItem(id, a)!!.countedQtyMilli)
    }

    @Test fun shortage_and_surplus_are_valued_at_cost() = runBlocking {
        val a = product("A", cost = 1000, stock = 10_000)
        val b = product("B", cost = 500, stock = 5_000)
        product("C")
        val id = service.start(CountScope.ALL, null, userId)
        service.setCounted(id, a, 8_000) // 2 missing x 10.00
        service.setCounted(id, b, 7_000) // 2 extra x 5.00

        val s = service.summary(id)

        assertEquals(2000L, s.shortageValue)
        assertEquals(1000L, s.surplusValue)
        assertEquals(2, s.countedItems)
        assertEquals(1, s.uncountedItems)
    }

    @Test fun stock_changes_only_when_the_count_is_approved() = runBlocking {
        val a = product("A", stock = 10_000)
        val b = product("B", cost = 500, stock = 5_000)
        val c = product("C", stock = 3_000)
        val id = service.start(CountScope.ALL, null, userId)
        service.setCounted(id, a, 8_000)
        service.setCounted(id, b, 7_000)

        assertEquals(10_000L, qty(a)) // still a draft: nothing moved
        assertEquals(5_000L, qty(b))

        val approval = service.approve(id, userId)

        assertEquals(8_000L, qty(a))
        assertEquals(7_000L, qty(b))
        assertEquals(3_000L, qty(c)) // never counted: untouched
        assertEquals(2, approval.adjustedProducts)
        assertEquals(2000L, approval.shortageValue)
        assertEquals(1000L, approval.surplusValue)
        assertEquals(8_000L, db.stock().movementTotal(a))
        assertEquals(8_000L, db.stock().batchTotal(a))
        assertTrue(db.stock().observeMovements(a).first().any { it.type == MovementType.COUNT_ADJUSTMENT })

        val count = db.stockCounts().getById(id)!!
        assertEquals(CountStatus.APPROVED, count.status)
        assertEquals(userId, count.approvedByUserId)
        assertNotNull(count.approvedAt)
    }

    @Test fun an_approved_count_cannot_be_approved_changed_or_deleted() = runBlocking {
        val a = product("A")
        val id = service.start(CountScope.ALL, null, userId)
        service.setCounted(id, a, 9_000)
        service.approve(id, userId)

        assertThrows(CountException.NotDraft::class.java) { runBlocking { service.approve(id, userId) } }
        assertThrows(CountException.NotDraft::class.java) { runBlocking { service.setCounted(id, a, 1_000) } }
        assertThrows(CountException.NotDraft::class.java) { runBlocking { service.deleteDraft(id) } }
        assertEquals(9_000L, qty(a)) // approving twice did not adjust twice
    }

    @Test fun sales_made_while_counting_are_preserved() = runBlocking {
        val a = product("A", stock = 10_000)
        val id = service.start(CountScope.ALL, null, userId) // snapshot: 10
        service.setCounted(id, a, 8_000) // the count found 2 missing
        adjustments.writeOff("w", a, 3_000, MovementType.DAMAGED, null, userId) // stock is now 7

        service.approve(id, userId)

        assertEquals(5_000L, qty(a)) // 7 - 2, not forced to 8
    }

    @Test fun a_shortage_never_takes_the_stock_below_zero() = runBlocking {
        val a = product("A", stock = 10_000)
        val id = service.start(CountScope.ALL, null, userId)
        service.setCounted(id, a, 0) // the count says 10 missing
        adjustments.writeOff("w", a, 9_000, MovementType.DAMAGED, null, userId) // only 1 left

        service.approve(id, userId)

        assertEquals(0L, qty(a))
        assertEquals(0L, db.stock().movementTotal(a))
    }

    @Test fun barcode_style_increments_accumulate_the_counted_quantity() = runBlocking {
        val a = product("A")
        val id = service.start(CountScope.ALL, null, userId)
        service.incrementCounted(id, a, 1_000)
        service.incrementCounted(id, a, 12_000) // a carton barcode of 12
        assertEquals(13_000L, db.stockCounts().getItem(id, a)!!.countedQtyMilli)
    }

    @Test fun a_draft_can_be_deleted_with_its_items() = runBlocking {
        product("A")
        val id = service.start(CountScope.ALL, null, userId)
        service.deleteDraft(id)
        assertNull(db.stockCounts().getById(id))
        assertTrue(db.stockCounts().getItems(id).isEmpty())
    }

    @Test fun the_lines_flow_gives_names_sorted_with_their_differences() = runBlocking {
        val b = product("Bread", stock = 5_000)
        val a = product("Apple", stock = 10_000)
        val id = service.start(CountScope.ALL, null, userId)
        service.setCounted(id, a, 8_000)

        val lines = service.observeLines(id).first()

        assertEquals(listOf("Apple", "Bread"), lines.map { it.productName })
        assertEquals(-2_000L, lines.first { it.item.productId == a }.diffMilli)
        assertNull(lines.first { it.item.productId == b }.diffMilli)
    }
}
