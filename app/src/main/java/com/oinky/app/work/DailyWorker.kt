package com.oinky.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oinky.app.OinkyApp
import com.oinky.app.widget.WidgetRefresher
import com.oinky.core.Currencies
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Runs a few times a day:
 *  1. refreshes exchange rates and converts entries recorded offline,
 *  2. posts due AUTO_POST recurring payments,
 *  3. reminds about REMIND payments that passed their grace period unrecorded,
 *  4. in the evening, nudges to write the diary if today is still empty.
 */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as OinkyApp).container
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("d MMM")

        c.rates.refreshIfStale()
        c.ledger.reconvertPending()

        val posted = c.ledger.postDueRecurring(today)
        if (posted.isNotEmpty()) {
            Notifications.autoPosted(
                applicationContext,
                posted.map { "${it.title} · ${Currencies.format(it.amount, it.currency)} (${fmt.format(java.time.LocalDate.ofEpochDay(it.epochDay))})" },
            )
        }

        val missed = c.ledger.missedPayments(today)
        if (missed.isNotEmpty() && c.settings.lastMissedNotifiedDay != today.toEpochDay()) {
            Notifications.missedPayments(
                applicationContext,
                missed.map { (rule, m) ->
                    "${rule.name} (${Currencies.format(rule.amount, rule.currency)}) was due ${fmt.format(m.dueDate)}"
                },
            )
            c.settings.lastMissedNotifiedDay = today.toEpochDay()
        }

        if (c.settings.diaryReminder.value &&
            LocalTime.now().hour >= 20 &&
            c.settings.lastDiaryNudgeDay != today.toEpochDay()
        ) {
            val entry = c.db.days().get(today.toEpochDay())
            if (entry == null || (entry.moodScore == null && entry.note.isBlank())) {
                Notifications.diaryNudge(applicationContext)
                c.settings.lastDiaryNudgeDay = today.toEpochDay()
            }
        }
        // Keeps the widget's "today" current across midnight and after rate updates.
        WidgetRefresher.request(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "daily-worker"
        private const val NOW = "daily-worker-now"

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyWorker>(4, TimeUnit.HOURS).build(),
            )
            // Also run once on app start so rates and reminders are fresh.
            wm.enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DailyWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                    .build(),
            )
        }
    }
}
