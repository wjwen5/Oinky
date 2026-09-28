package com.moodledger.app

import android.app.Application
import android.content.Context
import com.moodledger.app.data.AppDatabase
import com.moodledger.app.data.LedgerRepository
import com.moodledger.app.data.Settings
import com.moodledger.app.ocr.ReceiptScanner
import com.moodledger.app.rates.ExchangeRateRepository
import com.moodledger.app.work.DailyWorker
import com.moodledger.app.work.Notifications
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
    val scanner = ReceiptScanner(context)
}

class MoodLedgerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        DailyWorker.schedule(this)
    }
}
