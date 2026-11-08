package com.konoz.market

import android.app.Application

class KonozApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Database / DI container is initialised here in the next batch.
    }
}
