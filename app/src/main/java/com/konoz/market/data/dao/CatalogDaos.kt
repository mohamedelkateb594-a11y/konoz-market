package com.konoz.market.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.konoz.market.data.entity.Barcode
import com.konoz.market.data.entity.Category
import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.Supplier
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert suspend fun insert(c: Category): Long
    @Update suspend fun update(c: Category)
    @Query("SELECT * FROM categories WHERE isActive = 1 ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<Category>>
    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Category?
    @Query("SELECT COUNT(*) FROM categories") suspend fun count(): Int
}

@Dao
interface SupplierDao {
    @Insert suspend fun insert(s: Supplier): Long
    @Update suspend fun update(s: Supplier)
    @Query("DELETE FROM suppliers WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM suppliers ORDER BY name") fun observeAll(): Flow<List<Supplier>>
    @Query("SELECT * FROM suppliers WHERE id = :id") suspend fun getById(id: Long): Supplier?
}

@Dao
interface ProductDao {
    @Insert suspend fun insert(p: Product): Long
    @Update suspend fun update(p: Product)
    @Query("SELECT * FROM products WHERE id = :id") suspend fun getById(id: Long): Product?
    @Query("SELECT * FROM products WHERE id = :id") fun observeById(id: Long): Flow<Product?>

    @Query("SELECT * FROM products ORDER BY name") fun observeAll(): Flow<List<Product>>
    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name") suspend fun getAllActive(): List<Product>
    @Query("SELECT * FROM products WHERE isFavorite = 1 AND isActive = 1 ORDER BY name")
    fun observeFavorites(): Flow<List<Product>>

    /** Cashier search: active products only. */
    @Query(
        """SELECT * FROM products WHERE isActive = 1 AND (
            name LIKE '%' || :q || '%' OR internalCode LIKE '%' || :q || '%'
            OR id IN (SELECT productId FROM barcodes WHERE code LIKE :q || '%')
        ) ORDER BY name LIMIT 50"""
    )
    fun search(q: String): Flow<List<Product>>

    /** Management search: includes deactivated products. */
    @Query(
        """SELECT * FROM products WHERE (
            name LIKE '%' || :q || '%' OR internalCode LIKE '%' || :q || '%'
            OR id IN (SELECT productId FROM barcodes WHERE code LIKE :q || '%')
        ) ORDER BY name LIMIT 200"""
    )
    fun searchManage(q: String): Flow<List<Product>>

    @Query(
        """SELECT p.* FROM products p INNER JOIN barcodes b ON b.productId = p.id
           WHERE b.code = :code LIMIT 1"""
    )
    suspend fun findByBarcode(code: String): Product?

    @Query("SELECT * FROM products WHERE internalCode = :code LIMIT 1")
    suspend fun findByInternalCode(code: String): Product?

    @Query("UPDATE products SET currentQtyMilli = currentQtyMilli + :deltaMilli WHERE id = :id")
    suspend fun adjustQty(id: Long, deltaMilli: Long)

    @Query("UPDATE products SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("UPDATE products SET salePrice = :price WHERE id = :id")
    suspend fun setSalePrice(id: Long, price: Long)

    @Query("UPDATE products SET costPrice = :cost WHERE id = :id")
    suspend fun setCostPrice(id: Long, cost: Long)

    @Query("SELECT * FROM products WHERE isActive = 1 AND currentQtyMilli <= lowStockThresholdMilli AND currentQtyMilli > 0 ORDER BY currentQtyMilli")
    fun observeLowStock(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE isActive = 1 AND currentQtyMilli <= 0 ORDER BY name")
    fun observeOutOfStock(): Flow<List<Product>>

    @Query("SELECT COUNT(*) FROM products WHERE isActive = 1 AND currentQtyMilli <= lowStockThresholdMilli")
    fun observeLowStockCount(): Flow<Int>

    /** Stock value at cost (estimate: integer division per product). */
    @Query("SELECT COALESCE(SUM(currentQtyMilli * costPrice / 1000), 0) FROM products WHERE isActive = 1 AND currentQtyMilli > 0")
    fun observeInventoryValue(): Flow<Long>

    @Query("SELECT COUNT(*) FROM products") suspend fun count(): Int
}

@Dao
interface BarcodeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(b: Barcode): Long
    @Update suspend fun update(b: Barcode)
    @Query("DELETE FROM barcodes WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM barcodes WHERE productId = :productId ORDER BY isPrimary DESC, id")
    fun observeForProduct(productId: Long): Flow<List<Barcode>>
    @Query("SELECT * FROM barcodes WHERE productId = :productId ORDER BY isPrimary DESC, id")
    suspend fun getForProduct(productId: Long): List<Barcode>
    @Query("SELECT * FROM barcodes WHERE code = :code LIMIT 1") suspend fun findByCode(code: String): Barcode?
}
