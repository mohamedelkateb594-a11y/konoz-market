package com.konoz.market.domain

import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.AuditLog
import com.konoz.market.data.entity.PaymentMethod
import com.konoz.market.data.entity.Shift
import com.konoz.market.data.entity.ShiftStatus

sealed class ShiftException(message: String) : Exception(message) {
    class AlreadyOpen : ShiftException("يوجد وردية مفتوحة بالفعل")
    class NoOpenShift : ShiftException("لا توجد وردية مفتوحة")
    class InvalidAmount : ShiftException("المبلغ غير صحيح")
    class NotFound(val id: Long) : ShiftException("الوردية غير موجودة")
}

/**
 * Cash drawer report of one shift. All money in piastres.
 *
 *  expectedCash = openingCash + cashSales - cashRefunds - cashExpenses
 *  difference   = actualCash - expectedCash   (negative = shortage, positive = overage)
 *
 * Payments are stored net of change, so [cashSales] is the cash that really stayed in the drawer.
 * For a closed shift [expectedCash], [actualCash] and [difference] are the values fixed at closing.
 */
data class ShiftReport(
    val shift: Shift,
    val cashSales: Long,
    val cardSales: Long,
    val walletSales: Long,
    val cashRefunds: Long,
    val cashExpenses: Long,
    val expectedCash: Long,
    val actualCash: Long?,
    val difference: Long?,
) {
    val otherPayments: Long get() = cardSales + walletSales
    val isOpen: Boolean get() = shift.status == ShiftStatus.OPEN
}

/** Opening and closing the cash drawer. Every change runs in one transaction together with its audit row. */
class ShiftService(
    private val db: KonozDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val shifts get() = db.shifts()

    /** Opens a shift with the cash already in the drawer. Only one shift can be open at a time. */
    suspend fun open(openingCash: Long, userId: Long): Long = db.tx {
        if (openingCash < 0L) throw ShiftException.InvalidAmount()
        if (shifts.getOpenShift() != null) throw ShiftException.AlreadyOpen()
        val now = clock()
        val id = shifts.insert(Shift(userId = userId, openedAt = now, openingCash = openingCash))
        audit(userId, "SHIFT_OPENED", id, "openingCash=$openingCash", now)
        id
    }

    /** Closes the open shift with the counted cash and returns its final report. */
    suspend fun close(actualCash: Long, userId: Long): ShiftReport = db.tx {
        if (actualCash < 0L) throw ShiftException.InvalidAmount()
        val shift = shifts.getOpenShift() ?: throw ShiftException.NoOpenShift()
        val now = clock()
        val live = reportIn(shift)
        val closed = shift.copy(
            closedAt = now,
            expectedCash = live.expectedCash,
            actualCash = actualCash,
            difference = actualCash - live.expectedCash,
            status = ShiftStatus.CLOSED,
        )
        shifts.update(closed)
        audit(userId, "SHIFT_CLOSED", shift.id, "expected=${live.expectedCash} actual=$actualCash difference=${actualCash - live.expectedCash}", now)
        reportIn(closed)
    }

    /** Live report of the open shift, or null when none is open. */
    suspend fun currentReport(): ShiftReport? = db.tx { shifts.getOpenShift()?.let { reportIn(it) } }

    suspend fun report(shiftId: Long): ShiftReport = db.tx {
        reportIn(shifts.getById(shiftId) ?: throw ShiftException.NotFound(shiftId))
    }

    // ---------- internals ----------
    private suspend fun reportIn(shift: Shift): ShiftReport {
        val cash = db.sales().paymentsTotalForShift(shift.id, PaymentMethod.CASH.name)
        val card = db.sales().paymentsTotalForShift(shift.id, PaymentMethod.CARD.name)
        val wallet = db.sales().paymentsTotalForShift(shift.id, PaymentMethod.WALLET.name)
        val refunds = db.sales().cashRefundsForShift(shift.id)
        val expenses = db.expenses().cashExpensesForShift(shift.id)
        val computed = shift.openingCash + cash - refunds - expenses
        return ShiftReport(
            shift = shift,
            cashSales = cash,
            cardSales = card,
            walletSales = wallet,
            cashRefunds = refunds,
            cashExpenses = expenses,
            expectedCash = shift.expectedCash ?: computed,
            actualCash = shift.actualCash,
            difference = shift.difference,
        )
    }

    private suspend fun audit(userId: Long, action: String, shiftId: Long, details: String, now: Long) {
        db.audit().insert(AuditLog(userId = userId, action = action, entity = "shift", entityId = shiftId, details = details, createdAt = now))
    }
}
