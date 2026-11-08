package com.konoz.market

import android.app.Application
import com.konoz.market.data.KonozDatabase
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

    override fun onCreate() {
        super.onCreate()
        appScope.launch { DatabaseInitializer.seedIfNeeded(database) }
    }
}
