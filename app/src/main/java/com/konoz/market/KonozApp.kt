package com.konoz.market

import android.app.Application
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.repo.ProductRepository
import com.konoz.market.data.seed.DatabaseInitializer
import com.konoz.market.domain.SaleService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KonozApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: KonozDatabase by lazy { KonozDatabase.get(this) }
    val saleService: SaleService by lazy { SaleService(database) }
    val productRepository: ProductRepository by lazy { ProductRepository(database) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { DatabaseInitializer.seedIfNeeded(database) }
    }

    /**
     * TEMPORARY until the login screen exists: operations are attributed to the seeded admin user.
     * Replace with the logged-in user's id (see PROJECT_STATE.md, unfinished work).
     */
    suspend fun activeUserId(): Long {
        DatabaseInitializer.seedIfNeeded(database) // idempotent; guards against racing the async seed in onCreate
        return database.users().findActiveByUsername(DatabaseInitializer.DEFAULT_ADMIN_USERNAME)!!.id
    }
}
