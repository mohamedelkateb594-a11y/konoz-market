package com.konoz.market.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.konoz.market.data.dao.AuditDao
import com.konoz.market.data.dao.BarcodeDao
import com.konoz.market.data.dao.CategoryDao
import com.konoz.market.data.dao.ExpenseDao
import com.konoz.market.data.dao.ProductDao
import com.konoz.market.data.dao.PurchaseDao
import com.konoz.market.data.dao.ReportDao
import com.konoz.market.data.dao.SalesDao
import com.konoz.market.data.dao.SettingsDao
import com.konoz.market.data.dao.ShiftDao
import com.konoz.market.data.dao.StockCountDao
import com.konoz.market.data.dao.StockDao
import com.konoz.market.data.dao.SupplierDao
import com.konoz.market.data.dao.SupplierPaymentDao
import com.konoz.market.data.dao.UserDao
import com.konoz.market.data.entity.AppSetting
import com.konoz.market.data.entity.AuditLog
import com.konoz.market.data.entity.Barcode
import com.konoz.market.data.entity.Category
import com.konoz.market.data.entity.Expense
import com.konoz.market.data.entity.InventoryMovement
import com.konoz.market.data.entity.Payment
import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.PurchaseInvoice
import com.konoz.market.data.entity.PurchaseItem
import com.konoz.market.data.entity.ReturnItem
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.data.entity.SalesItem
import com.konoz.market.data.entity.SalesReturn
import com.konoz.market.data.entity.Shift
import com.konoz.market.data.entity.StockBatch
import com.konoz.market.data.entity.StockCount
import com.konoz.market.data.entity.StockCountItem
import com.konoz.market.data.entity.Supplier
import com.konoz.market.data.entity.SupplierPayment
import com.konoz.market.data.entity.User

@Database(
    entities = [
        Product::class, Barcode::class, Category::class, Supplier::class,
        PurchaseInvoice::class, PurchaseItem::class,
        SalesInvoice::class, SalesItem::class, Payment::class,
        InventoryMovement::class, StockBatch::class,
        Expense::class, StockCount::class, StockCountItem::class,
        SalesReturn::class, ReturnItem::class,
        User::class, Shift::class, AuditLog::class, AppSetting::class,
        SupplierPayment::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class KonozDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun barcodes(): BarcodeDao
    abstract fun categories(): CategoryDao
    abstract fun suppliers(): SupplierDao
    abstract fun stock(): StockDao
    abstract fun sales(): SalesDao
    abstract fun purchases(): PurchaseDao
    abstract fun expenses(): ExpenseDao
    abstract fun stockCounts(): StockCountDao
    abstract fun users(): UserDao
    abstract fun shifts(): ShiftDao
    abstract fun audit(): AuditDao
    abstract fun settings(): SettingsDao
    abstract fun reports(): ReportDao
    abstract fun supplierPayments(): SupplierPaymentDao

    /** Run [block] atomically; any exception rolls everything back. */
    suspend fun <R> tx(block: suspend () -> R): R = withTransaction(block)

    companion object {
        const val FILE_NAME = "konoz_market.db"

        /** v2: supplier payments / refunds that move the cash drawer. Existing data is untouched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `supplier_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`purchaseInvoiceId` INTEGER NOT NULL, `supplierId` INTEGER NOT NULL, `shiftId` INTEGER, " +
                        "`amount` INTEGER NOT NULL, `userId` INTEGER, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        @Volatile private var instance: KonozDatabase? = null

        fun get(context: Context): KonozDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, KonozDatabase::class.java, FILE_NAME,
            )
                // Never fallbackToDestructiveMigration: shop data must survive app updates.
                // Add explicit Migration objects here whenever `version` is increased.
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
