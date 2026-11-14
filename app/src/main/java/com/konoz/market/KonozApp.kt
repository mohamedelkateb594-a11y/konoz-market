package com.konoz.market

import android.app.Application
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.repo.CatalogRepository
import com.konoz.market.data.repo.ProductCsvService
import com.konoz.market.data.repo.ProductRepository
import com.konoz.market.data.seed.DatabaseInitializer
import com.konoz.market.domain.AuthSession
import com.konoz.market.domain.ExpenseService
import com.konoz.market.domain.PurchaseService
import com.konoz.market.domain.ReportService
import com.konoz.market.domain.ReturnService
import com.konoz.market.domain.SaleService
import com.konoz.market.domain.ShiftService
import com.konoz.market.domain.StockAdjustmentService
import com.konoz.market.domain.StockCountService
import com.konoz.market.domain.UserService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KonozApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Who is signed in. Empty on every cold start, so the login screen always comes first. */
    val session = AuthSession()

    val database: KonozDatabase by lazy { KonozDatabase.get(this) }
    val saleService: SaleService by lazy { SaleService(database) }
    val purchaseService: PurchaseService by lazy { PurchaseService(database) }
    val returnService: ReturnService by lazy { ReturnService(database) }
    val stockAdjustmentService: StockAdjustmentService by lazy { StockAdjustmentService(database) }
    val stockCountService: StockCountService by lazy { StockCountService(database, stockAdjustmentService) }
    val expenseService: ExpenseService by lazy { ExpenseService(database) }
    val reportService: ReportService by lazy { ReportService(database) }
    val shiftService: ShiftService by lazy { ShiftService(database) }
    val userService: UserService by lazy { UserService(database) }
    val productRepository: ProductRepository by lazy { ProductRepository(database) }
    val catalogRepository: CatalogRepository by lazy { CatalogRepository(database) }
    val productCsvService: ProductCsvService by lazy { ProductCsvService(database, productRepository) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { ensureSeeded() }
    }

    /** First-run setup (admin account, categories, settings). Idempotent; the login waits for it so the admin always exists. */
    suspend fun ensureSeeded() {
        DatabaseInitializer.seedIfNeeded(database)
    }

    /** The id operations are attributed to: the signed-in user. Screens are only reachable after login, so this does not fail in normal use. */
    suspend fun activeUserId(): Long = session.requireUserId()
}
