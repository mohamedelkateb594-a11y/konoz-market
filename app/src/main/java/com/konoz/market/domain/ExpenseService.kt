package com.konoz.market.domain

import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.AuditLog
import com.konoz.market.data.entity.Expense
import com.konoz.market.data.entity.ExpenseCategory
import kotlinx.coroutines.flow.Flow
import java.util.TimeZone

sealed class ExpenseException(message: String) : Exception(message) {
    class InvalidAmount : ExpenseException("قيمة المصروف يجب أن تكون أكبر من صفر")
    class DescriptionRequired : ExpenseException("وصف المصروف مطلوب")
    class NotFound(val id: Long) : ExpenseException("المصروف غير موجود")
}

/** [amount] in piastres. [at] = null means "now" when adding, and "keep the stored time" when editing. */
data class ExpenseInput(
    val amount: Long,
    val category: ExpenseCategory,
    val description: String,
    val notes: String? = null,
    val receiptPath: String? = null,
    val paidInCash: Boolean = true,
    val at: Long? = null,
)

/**
 * Daily operating expenses. Every change runs in one transaction together with its audit-log row.
 * A new expense is linked to the currently open shift (if any) so the shift report can subtract cash expenses.
 */
class ExpenseService(
    private val db: KonozDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zone: TimeZone = TimeZone.getDefault(),
) {
    private val expenses get() = db.expenses()

    suspend fun add(input: ExpenseInput, userId: Long): Long = db.tx {
        validate(input)
        val now = clock()
        val id = expenses.insert(
            Expense(
                amount = input.amount,
                category = input.category,
                description = input.description.trim(),
                notes = input.notes.cleaned(),
                receiptPath = input.receiptPath,
                paidInCash = input.paidInCash,
                userId = userId,
                shiftId = db.shifts().getOpenShift()?.id,
                createdAt = input.at ?: now,
            )
        )
        audit(userId, "EXPENSE_ADDED", id, "amount=${input.amount} category=${input.category.name} cash=${input.paidInCash}", now)
        id
    }

    /** Edits an expense. The original user, shift link and (unless [ExpenseInput.at] is given) time are kept. */
    suspend fun update(id: Long, input: ExpenseInput, userId: Long) = db.tx {
        validate(input)
        val old = expenses.getById(id) ?: throw ExpenseException.NotFound(id)
        val now = clock()
        expenses.update(
            old.copy(
                amount = input.amount,
                category = input.category,
                description = input.description.trim(),
                notes = input.notes.cleaned(),
                receiptPath = input.receiptPath,
                paidInCash = input.paidInCash,
                createdAt = input.at ?: old.createdAt,
            )
        )
        audit(userId, "EXPENSE_UPDATED", id, "amount=${old.amount}->${input.amount} category=${old.category.name}->${input.category.name}", now)
    }

    /** Deletes an expense (the UI asks for confirmation first). Returns its receipt path so the caller can remove the image file. */
    suspend fun delete(id: Long, userId: Long): String? = db.tx {
        val old = expenses.getById(id) ?: throw ExpenseException.NotFound(id)
        audit(userId, "EXPENSE_DELETED", id, "amount=${old.amount} category=${old.category.name} description=${old.description}", clock())
        expenses.delete(id)
        old.receiptPath
    }

    fun rangeOf(kind: PeriodKind, at: Long = clock()): PeriodRange = Periods.rangeOf(kind, at, zone)

    fun observeTotal(kind: PeriodKind, at: Long = clock()): Flow<Long> {
        val r = rangeOf(kind, at)
        return expenses.observeTotalBetween(r.from, r.to)
    }

    fun observeList(kind: PeriodKind, at: Long = clock()): Flow<List<Expense>> {
        val r = rangeOf(kind, at)
        return expenses.observeBetween(r.from, r.to)
    }

    // ---------- internals ----------
    private fun validate(input: ExpenseInput) {
        if (input.amount <= 0L) throw ExpenseException.InvalidAmount()
        if (input.description.isBlank()) throw ExpenseException.DescriptionRequired()
    }

    private fun String?.cleaned(): String? = this?.trim()?.ifEmpty { null }

    private suspend fun audit(userId: Long, action: String, expenseId: Long, details: String, now: Long) {
        db.audit().insert(AuditLog(userId = userId, action = action, entity = "expense", entityId = expenseId, details = details, createdAt = now))
    }
}
