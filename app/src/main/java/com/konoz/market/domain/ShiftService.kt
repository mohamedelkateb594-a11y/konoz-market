package com.konoz.market.domain

import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.AuditLog
import com.konoz.market.data.entity.PaymentMethod
import com.konoz.market.data.entity.Shift
import com.konoz.market.data.entity.ShiftStatus
import kotlinx.coroutines.flow.Flow

sealed class ShiftException(message: String) : Exception(message) {
    class InvalidAmount : ShiftException("المبلغ لا يمكن أن يكون سالبًا")
    class AlreadyOpen(val shiftId: Long) : ShiftException("توجد وردية مفتوحة بالفعل")
    class NotFound(val id: Long) : ShiftException("الوردية غير موجودة")
    class AlreadyClosed(val id: Long) : ShiftException("الوردية مغلقة بالفعل")
}

/**
 * Cash-drawer figures of one shift (all piastres).
 * expectedCash = openingCash + cashSales - cashRefunds - cashExpenses.
 * Payments are stored NET of change, so cashSales is exactly the cash that stayed in the drawer.
 * [actualCash] / [difference] are null until the shift is closed; difference = actual - expected (negative = shortage).
 */
data class ShiftSummary(
    val shift: Shift,
    val openingCash: Long,
    val cashSales: Long,
    val cardSales: Long,
    val walletSales: Long,
    val cashExpenses: Long,
    val cashRefunds: Long,
    val expectedCash: Long,
    val actualCash: Long?,
    val difference: Long?,
) {
    /** Sales paid by means other than cash. */
    val otherSales: Long get() = cardSales + walletSales
    val totalSales: Long get() = cashSales + otherSales
}

object ShiftMath {
    fun expectedCash(openingCash: Long, cashSales: Long, cashRefunds: Long, cashExpenses: Long): Long =
        openingCash + cashSales - cashRefunds - cashExpenses

    fun difference(actualCash: Long, expectedCash: Long): Long = actualCash - expectedCash
}

/**
 * Opening and closing of cashier shifts. Every change runs in one transaction together with its audit row.
 * Only one shift can be open at a time. Sales, returns and expenses made while a shift is open are linked to it
 * through their `shiftId` (set by the callers), which is what [summary] reads.
 */
class ShiftService(
    private val db: KonozDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val shifts get() = db.shifts()

    fun observeOpenShift(): Flow<Shift?> = shifts.observeOpenShift()
    fun observeAll(): Flow<List<Shift>> = shifts.observeAll()
    suspend fun getOpenShift(): Shift? = shifts.getOpenShift()

    /** Opens a shift with the drawer's opening cash. Fails if another shift is still open. Returns the new shift id. */
    suspend fun open(openingCash: Long, userId: Long): Long = db.tx {
        if (openingCash < 0L) throw ShiftException.InvalidAmount()
        shifts.getOpenShift()?.let { throw ShiftException.AlreadyOpen(it.id) }
        val now = clock()
        val id = shifts.insert(Shift(userId = userId, openedAt = now, openingCash = openingCash))
        audit(userId, "SHIFT_OPENED", id, "openingCash=$openingCash", now)
        id
    }

    /** Live figures of a shift (open or closed). Components are recomputed from the ledger; closed shifts also show the saved actual/difference. */
    suspend fun summary(shiftId: Long): ShiftSummary {
        val shift = shifts.getById(shiftId) ?: throw ShiftException.NotFound(shiftId)
        return summaryOf(shift)
    }

    /**
     * Closes a shift: stores expected cash, the counted cash and their difference. Closing twice is rejected,
     * so a retry can never overwrite the first count. Returns the final summary.
     */
    suspend fun close(shiftId: Long, actualCash: Long, userId: Long): ShiftSummary = db.tx {
        if (actualCash < 0L) throw ShiftException.InvalidAmount()
        val shift = shifts.getById(shiftId) ?: throw ShiftException.NotFound(shiftId)
        if (shift.status != ShiftStatus.OPEN) throw ShiftException.AlreadyClosed(shiftId)
        val now = clock()
        val live = summaryOf(shift)
        val diff = ShiftMath.difference(actualCash, live.expectedCash)
        val closed = shift.copy(
            closedAt = now,
            expectedCash = live.expectedCash,
            actualCash = actualCash,
            difference = diff,
            status = ShiftStatus.CLOSED,
        )
        shifts.update(closed)
        audit(userId, "SHIFT_CLOSED", shiftId, "expected=${live.expectedCash} actual=$actualCash difference=$diff", now)
        live.copy(shift = closed, actualCash = actualCash, difference = diff)
    }

    // ---------- internals ----------
    private suspend fun summaryOf(shift: Shift): ShiftSummary {
        val cashSales = db.sales().paymentsTotalForShift(shift.id, PaymentMethod.CASH.name)
        val cardSales = db.sales().paymentsTotalForShift(shift.id, PaymentMethod.CARD.name)
        val walletSales = db.sales().paymentsTotalForShift(shift.id, PaymentMethod.WALLET.name)
        val cashRefunds = db.sales().cashRefundsForShift(shift.id)
        val cashExpenses = db.expenses().cashExpensesForShift(shift.id)
        return ShiftSummary(
            shift = shift,
            openingCash = shift.openingCash,
            cashSales = cashSales,
            cardSales = cardSales,
            walletSales = walletSales,
            cashExpenses = cashExpenses,
            cashRefunds = cashRefunds,
            expectedCash = ShiftMath.expectedCash(shift.openingCash, cashSales, cashRefunds, cashExpenses),
            actualCash = shift.actualCash,
            difference = shift.difference,
        )
    }

    private suspend fun audit(userId: Long, action: String, shiftId: Long, details: String, now: Long) {
        db.audit().insert(AuditLog(userId = userId, action = action, entity = "shift", entityId = shiftId, details = details, createdAt = now))
    }
}
