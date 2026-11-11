package com.konoz.market.domain

import com.konoz.market.core.Money
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.AuditLog
import com.konoz.market.data.entity.CountScope
import com.konoz.market.data.entity.CountStatus
import com.konoz.market.data.entity.MovementType
import com.konoz.market.data.entity.StockCount
import com.konoz.market.data.entity.StockCountItem
import com.konoz.market.data.entity.UnitType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.abs

sealed class CountException(message: String) : Exception(message) {
    class NoProducts : CountException("لا توجد منتجات للجرد")
    class CategoryRequired : CountException("اختر التصنيف المراد جرده")
    class NotFound : CountException("الجرد غير موجود")
    class NotDraft : CountException("الجرد معتمد مسبقًا")
    class ItemNotInCount(val productId: Long) : CountException("المنتج $productId ليس ضمن هذا الجرد")
    class InvalidQuantity : CountException("كمية غير صحيحة")
}

/** One product row of a count with its computed difference (counted - system). diffMilli = null while not counted. */
data class CountLine(val item: StockCountItem, val productName: String, val unit: UnitType, val diffMilli: Long?)

data class CountSummary(
    /** Value (at cost) of what is missing: sum of |negative differences| x cost. */
    val shortageValue: Long,
    /** Value (at cost) of what is in excess. */
    val surplusValue: Long,
    val countedItems: Int,
    val uncountedItems: Int,
)

data class CountApproval(val shortageValue: Long, val surplusValue: Long, val adjustedProducts: Int)

object CountMath {
    fun diffMilli(item: StockCountItem): Long? = item.countedQtyMilli?.let { it - item.systemQtyMilli }

    fun summary(items: List<StockCountItem>): CountSummary {
        var shortage = 0L
        var surplus = 0L
        var counted = 0
        for (i in items) {
            val diff = diffMilli(i) ?: continue
            counted++
            val value = Money.costOf(i.unitCostAtCount, abs(diff))
            if (diff < 0L) shortage += value else if (diff > 0L) surplus += value
        }
        return CountSummary(shortage, surplus, counted, items.size - counted)
    }
}

/**
 * Stock counting (inventory). A count is a DRAFT until approved; the stock is changed ONLY on approval.
 *
 *  - start: snapshots every active product (all, or one category) with its system quantity and cost at that moment.
 *  - setCounted / incrementCounted: record the physical quantity (typed or by barcode scans). Not-counted products
 *    (null) are left alone on approval, which allows partial counts.
 *  - approve: for every counted product with a difference, applies the difference the count FOUND (counted - snapshot)
 *    to the CURRENT stock through COUNT_ADJUSTMENT movements (surplus = new batch, shortage = FEFO). Sales made while
 *    counting are therefore kept. A shortage never takes the stock below zero (it is limited to what is left).
 *    Who approved and when is stored, and an approved count can never be changed or approved again.
 */
class StockCountService(
    private val db: KonozDatabase,
    private val adjustments: StockAdjustmentService,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val counts get() = db.stockCounts()

    suspend fun start(scope: CountScope, categoryId: Long?, userId: Long, notes: String? = null): Long = db.tx {
        if (scope == CountScope.CATEGORY && categoryId == null) throw CountException.CategoryRequired()
        val products = db.products().getAllActive().filter { scope == CountScope.ALL || it.categoryId == categoryId }
        if (products.isEmpty()) throw CountException.NoProducts()
        val now = clock()
        val id = counts.insert(
            StockCount(
                status = CountStatus.DRAFT, scope = scope, categoryId = if (scope == CountScope.CATEGORY) categoryId else null,
                startedByUserId = userId, notes = notes?.trim()?.ifEmpty { null }, startedAt = now,
            )
        )
        counts.insertItems(
            products.map { StockCountItem(countId = id, productId = it.id, systemQtyMilli = it.currentQtyMilli, unitCostAtCount = it.costPrice) }
        )
        audit(userId, "STOCK_COUNT_STARTED", id, "scope=${scope.name} products=${products.size}", now)
        id
    }

    /** Sets the counted quantity (milli) of one product; null clears it. */
    suspend fun setCounted(countId: Long, productId: Long, countedMilli: Long?) {
        db.tx { write(countId, productId) { countedMilli } }
    }

    /** Adds [deltaMilli] to the counted quantity (null counts as 0): used for barcode scans while counting. */
    suspend fun incrementCounted(countId: Long, productId: Long, deltaMilli: Long) {
        db.tx { write(countId, productId) { current -> (current ?: 0L) + deltaMilli } }
    }

    private suspend fun write(countId: Long, productId: Long, newValue: (Long?) -> Long?) {
        val count = counts.getById(countId) ?: throw CountException.NotFound()
        if (count.status != CountStatus.DRAFT) throw CountException.NotDraft()
        val item = counts.getItem(countId, productId) ?: throw CountException.ItemNotInCount(productId)
        val value = newValue(item.countedQtyMilli)
        if (value != null) {
            if (value < 0L) throw CountException.InvalidQuantity()
            val product = db.products().getById(productId)
            if (product != null && !product.unit.isFractional && value % 1000L != 0L) throw CountException.InvalidQuantity()
        }
        counts.updateItem(item.copy(countedQtyMilli = value))
    }

    fun observeLines(countId: Long): Flow<List<CountLine>> =
        combine(counts.observeItems(countId), db.products().observeAll()) { items, products ->
            val byId = products.associateBy { it.id }
            items.map { CountLine(it, byId[it.productId]?.name.orEmpty(), byId[it.productId]?.unit ?: UnitType.PIECE, CountMath.diffMilli(it)) }
                .sortedBy { it.productName }
        }

    suspend fun summary(countId: Long): CountSummary = CountMath.summary(counts.getItems(countId))

    suspend fun approve(countId: Long, userId: Long): CountApproval = db.tx {
        val count = counts.getById(countId) ?: throw CountException.NotFound()
        if (count.status != CountStatus.DRAFT) throw CountException.NotDraft()
        val items = counts.getItems(countId)
        val summary = CountMath.summary(items)
        val now = clock()

        var adjusted = 0
        for (item in items) {
            val diff = CountMath.diffMilli(item) ?: continue
            if (diff == 0L) continue
            val product = db.products().getById(item.productId) ?: continue
            val applied = if (diff < 0L) -minOf(-diff, maxOf(product.currentQtyMilli, 0L)) else diff
            if (applied == 0L) continue
            adjustments.adjust("COUNT-$countId-${item.productId}", item.productId, applied, "جرد #$countId", userId, MovementType.COUNT_ADJUSTMENT)
            adjusted++
        }
        counts.update(count.copy(status = CountStatus.APPROVED, approvedByUserId = userId, approvedAt = now))
        audit(userId, "STOCK_COUNT_APPROVED", countId, "shortage=${summary.shortageValue} surplus=${summary.surplusValue} adjusted=$adjusted", now)
        CountApproval(summary.shortageValue, summary.surplusValue, adjusted)
    }

    suspend fun deleteDraft(countId: Long) {
        db.tx {
            val count = counts.getById(countId) ?: throw CountException.NotFound()
            if (count.status != CountStatus.DRAFT) throw CountException.NotDraft()
            counts.deleteItems(countId)
            counts.deleteDraft(countId)
        }
    }

    private suspend fun audit(userId: Long, action: String, countId: Long, details: String, now: Long) {
        db.audit().insert(AuditLog(userId = userId, action = action, entity = "stock_count", entityId = countId, details = details, createdAt = now))
    }
}
