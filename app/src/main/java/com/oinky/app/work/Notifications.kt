package com.oinky.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.oinky.app.MainActivity
import com.oinky.app.R

object Notifications {
    const val CHANNEL_PAYMENTS = "payments"
    const val CHANNEL_DIARY = "diary"
    const val EXTRA_DESTINATION = "destination"

    private const val ID_MISSED = 1001
    private const val ID_POSTED = 1002
    private const val ID_DIARY = 1003

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PAYMENTS, "Payment reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Recurring payments that are due or were not recorded"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DIARY, "Diary nudges", NotificationManager.IMPORTANCE_LOW).apply {
                description = "An evening reminder to jot down your day"
            },
        )
    }

    fun missedPayments(context: Context, lines: List<String>) =
        show(
            context, ID_MISSED, CHANNEL_PAYMENTS,
            title = if (lines.size == 1) "Payment not recorded" else "${lines.size} payments not recorded",
            text = lines.first(), bigText = lines.joinToString("\n"), destination = "recurring",
        )

    fun autoPosted(context: Context, lines: List<String>) =
        show(
            context, ID_POSTED, CHANNEL_PAYMENTS,
            title = "Recorded ${lines.size} recurring payment${if (lines.size > 1) "s" else ""}",
            text = lines.first(), bigText = lines.joinToString("\n"), destination = "today",
        )

    fun diaryNudge(context: Context) =
        show(
            context, ID_DIARY, CHANNEL_DIARY,
            title = "How was your day?", text = "Pick a mood, jot a line, log what you spent.",
            bigText = null, destination = "today",
        )

    private fun show(
        context: Context, id: Int, channel: String, title: String, text: String, bigText: String?, destination: String,
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_DESTINATION, destination)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .apply { if (bigText != null) setStyle(NotificationCompat.BigTextStyle().bigText(bigText)) }
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
