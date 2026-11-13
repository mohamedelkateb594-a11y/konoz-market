package com.konoz.market.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.konoz.market.data.entity.AppSetting
import com.konoz.market.data.entity.AuditLog
import com.konoz.market.data.entity.Expense
import com.konoz.market.data.entity.PurchaseInvoice
import com.konoz.market.data.entity.PurchaseItem
import com.konoz.market.data.entity.Shift
import com.konoz.market.data.entity.StockCount
import com.konoz.market.data.entity.StockCountItem
import com.konoz.market.data.entity.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert suspend fun insert(u: User): Long
    @Update suspend fun update(u: User)
    @Query("SELECT * FROM users WHERE username = :username AND isActive = 1 LIMIT 1")
    suspend fun findActiveByUsername(username: String): User?
    /** Active or not: the username is unique across all users. */
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): User?
    @Query("SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND isActive = 1")
    suspend fun countActiveAdmins(): Int
    @Query("SELECT * FROM users WHERE id = :id") suspend fun getById(id: Long): User?
    @Query("SELECT * FROM users ORDER BY name") fun observeAll(): Flow<List<User>>
    @Query("SELECT COUNT(*) FROM users") suspend fun count(): Int
}

@Dao
interface ShiftDao {
    @Insert suspend fun insert(s: Shift): Long
    @Update suspend fun update(s: Shift)
    @Query("SELECT * FROM shifts WHERE status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    suspend fun getOpenShift(): Shift?
    @Query("SELECT * FROM shifts WHERE status = 'OPEN' ORDER BY openedAt DESC LIMIT 1")
    fun observeOpenShift(): Flow<Shift?>
    @Query("SELECT * FROM shifts WHERE id = :id") suspend fun getById(id: Long): Shift?
    @Query("SELECT * FROM shifts ORDER BY openedAt DESC") fun observeAll(): Flow<List<Shift>>
}

@Dao
interface ExpenseDao {
    @Insert suspend fun insert(e: Expense): Long
    @Update suspend fun update(e: Expense)
    @Query("DELETE FROM expenses WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM expenses WHERE id = :id") suspend fun getById(id: Long): Expense?
    @Query("SELECT * FROM expenses WHERE uuid = :uuid") suspend fun getByUuid(uuid: String): Expense?
    @Query("SELECT * FROM expenses WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<Expense>>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE createdAt BETWEEN :from AND :to")
    fun observeTotalBetween(from: Long, to: Long): Flow<Long>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE shiftId = :shiftId AND paidInCash = 1")
    suspend fun cashExpensesForShift(shiftId: Long): Long
}

@Dao
interface PurchaseDao {
    @Insert suspend fun insertInvoice(i: PurchaseInvoice): Long
    @Update suspend fun updateInvoice(i: PurchaseInvoice)
    @Insert suspend fun insertItems(items: List<PurchaseItem>): List<Long>
    @Query("DELETE FROM purchase_items WHERE invoiceId = :invoiceId") suspend fun deleteItems(invoiceId: Long)
    @Query("DELETE FROM purchase_invoices WHERE id = :id AND status = 'DRAFT'") suspend fun deleteDraft(id: Long)
    @Query("SELECT * FROM purchase_invoices WHERE id = :id") suspend fun getInvoice(id: Long): PurchaseInvoice?
    @Query("SELECT * FROM purchase_invoices WHERE uuid = :uuid") suspend fun getByUuid(uuid: String): PurchaseInvoice?
    @Query("SELECT * FROM purchase_items WHERE invoiceId = :invoiceId") suspend fun getItems(invoiceId: Long): List<PurchaseItem>
    @Query("SELECT COALESCE(MAX(number), 0) + 1 FROM purchase_invoices") suspend fun nextNumber(): Long
    @Query("SELECT * FROM purchase_invoices ORDER BY createdAt DESC") fun observeAll(): Flow<List<PurchaseInvoice>>
    @Query("SELECT * FROM purchase_invoices WHERE supplierId = :supplierId ORDER BY createdAt DESC")
    fun observeForSupplier(supplierId: Long): Flow<List<PurchaseInvoice>>

    @Query("SELECT * FROM purchase_invoices WHERE supplierId = :supplierId AND status = 'APPROVED' ORDER BY approvedAt, id")
    suspend fun approvedForSupplier(supplierId: Long): List<PurchaseInvoice>

    /** What we owe the supplier: unsettled part of purchases minus unsettled part of returns (negative = they owe us). */
    @Query(
        """SELECT COALESCE(SUM(CASE WHEN isReturn = 1 THEN -remaining ELSE remaining END), 0)
           FROM purchase_invoices WHERE supplierId = :supplierId AND status = 'APPROVED'"""
    )
    suspend fun supplierBalance(supplierId: Long): Long

    @Query(
        """SELECT COALESCE(SUM(CASE WHEN isReturn = 1 THEN -remaining ELSE remaining END), 0)
           FROM purchase_invoices WHERE supplierId = :supplierId AND status = 'APPROVED'"""
    )
    fun observeSupplierBalance(supplierId: Long): Flow<Long>
}

@Dao
interface StockCountDao {
    @Insert suspend fun insert(c: StockCount): Long
    @Update suspend fun update(c: StockCount)
    @Insert suspend fun insertItems(items: List<StockCountItem>)
    @Update suspend fun updateItem(item: StockCountItem)
    @Query("SELECT * FROM stock_counts WHERE id = :id") suspend fun getById(id: Long): StockCount?
    @Query("SELECT * FROM stock_count_items WHERE countId = :countId") suspend fun getItems(countId: Long): List<StockCountItem>
    @Query("SELECT * FROM stock_count_items WHERE countId = :countId AND productId = :productId")
    suspend fun getItem(countId: Long, productId: Long): StockCountItem?
    @Query("SELECT * FROM stock_count_items WHERE countId = :countId") fun observeItems(countId: Long): Flow<List<StockCountItem>>
    @Query("SELECT * FROM stock_counts ORDER BY startedAt DESC") fun observeAll(): Flow<List<StockCount>>
    @Query("DELETE FROM stock_count_items WHERE countId = :countId") suspend fun deleteItems(countId: Long)
    @Query("DELETE FROM stock_counts WHERE id = :id AND status = 'DRAFT'") suspend fun deleteDraft(id: Long)
}

@Dao
interface AuditDao {
    @Insert suspend fun insert(l: AuditLog): Long
    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLog>>
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(s: AppSetting)
    @Query("SELECT value FROM app_settings WHERE `key` = :key") suspend fun get(key: String): String?
    @Query("SELECT * FROM app_settings") fun observeAll(): Flow<List<AppSetting>>
}
