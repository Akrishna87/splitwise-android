package com.example.splitwise.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.splitwise.MainActivity
import com.example.splitwise.data.AppDb
import com.example.splitwise.data.Repository
import com.example.splitwise.data.Settings
import com.example.splitwise.domain.Money
import kotlinx.coroutines.flow.first

object Notifier {
    private const val CHANNEL = "reminders"

    @SuppressLint("MissingPermission")
    fun post(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("Reminders").build()
        )
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(id, n)
    }
}

/** Runs daily: creates due recurring expenses and (if enabled) reminds you what you owe. */
class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = Repository(AppDb.get(applicationContext), Settings(applicationContext))
        val created = repo.generateDue()
        if (created.isNotEmpty()) {
            Notifier.post(applicationContext, 2, "Recurring expenses added", created.joinToString("\n") { "${it.description} (${Money.format(it.amount, it.currency)})" })
        }
        if (repo.settings.reminders) {
            val snap = repo.snapshot.first()
            val owes = snap.groups.mapNotNull { g ->
                val n = snap.myNet(g.id)
                if (n < 0) "${snap.title(g)}: you owe ${Money.format(-n, g.currency)}" else null
            }
            if (owes.isNotEmpty()) Notifier.post(applicationContext, 1, "You have unsettled balances", owes.joinToString("\n"))
        }
        return Result.success()
    }
}
