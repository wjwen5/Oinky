package com.oinky.app

import android.app.Application
import android.content.Context
import com.oinky.app.data.AppDatabase
import com.oinky.app.data.LedgerRepository
import com.oinky.app.data.Settings
import com.oinky.app.data.StickerRepository
import com.oinky.app.data.TripRepository
import com.oinky.app.ocr.ReceiptScanner
import com.oinky.app.rates.ExchangeRateRepository
import com.oinky.app.work.DailyWorker
import com.oinky.app.work.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    /** Outlives screens; used for writes that must finish after a ViewModel is cleared. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.build(context)
    val settings = Settings(context)
    val rates = ExchangeRateRepository(db.rates())
    val ledger = LedgerRepository(context, db, rates, settings)
    val trips = TripRepository(context, db, ledger)
    val stickers = StickerRepository(context, db, settings)
    val scanner = ReceiptScanner(context)
}

class OinkyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        DailyWorker.schedule(this)
    }
}
