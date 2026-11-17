package com.mahood.family.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mahood.family.core.Family
import com.mahood.family.core.data.Record
import com.mahood.family.core.sync.Notifier
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Each phone sets its own alarm for the next reminder meant for it (shared ones,
 * or ones assigned to this person). Nothing is pushed from a server: the reminder
 * list arrives by sync and every phone works out the times itself.
 */
object ReminderAlarms {
    const val COLLECTION = "reminders"
    private const val CHANNEL = "family-reminders"
    private const val K_LAST = "reminders_last_alert"
    private const val ACTION_FIRE = "com.mahood.family.reminders.FIRE"
    const val ACTION_DONE = "com.mahood.family.reminders.DONE"
    const val EXTRA_ID = "id"
    const val EXTRA_DATE = "date"

    fun start(r: Record): LocalDate? = r.string("start")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun doneThrough(r: Record): LocalDate? = r.string("doneThrough")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun minutes(r: Record): Int = (r.long("time") ?: (9 * 60L)).toInt().coerceIn(0, 24 * 60 - 1)
    fun repeat(r: Record): String = r.string("repeat") ?: "once"

    /** Reminders this phone should alert for. */
    private fun mine(): List<Record> {
        val me = Family.myEmail
        return Family.store.query(COLLECTION).filter { it.string("who").isNullOrEmpty() || it.string("who") == me }
    }

    private fun lastAlert(): LocalDateTime =
        Family.store.get(K_LAST)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: LocalDateTime.now()

    private fun pending(context: Context, flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context, 4242, Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE), flags or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Set (or clear) the alarm for the earliest upcoming reminder. Safe to call often. */
    fun schedule(context: Context) {
        if (!Family.isSetUp) return
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val after = maxOf(lastAlert(), LocalDateTime.now().minusMinutes(1))
        if (Family.store.get(K_LAST) == null) Family.store.set(K_LAST, after.toString())
        val next = mine().mapNotNull { r ->
            val s = start(r) ?: return@mapNotNull null
            ReminderRules.nextAlertAfter(s, repeat(r), minutes(r), doneThrough(r), after)
        }.minOrNull()
        val intent = pending(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        if (next == null) {
            alarms.cancel(intent)
            return
        }
        val at = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact but allowed in Doze, so no special "alarms" permission is needed. Usually on time.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
    }

    /** Post a notification for every reminder that came due since the last check. */
    fun fire(context: Context) {
        if (!Family.isSetUp) return
        val from = lastAlert()
        val now = LocalDateTime.now()
        mine().forEach { r ->
            val s = start(r) ?: return@forEach
            val at = ReminderRules.nextAlertAfter(s, repeat(r), minutes(r), doneThrough(r), from) ?: return@forEach
            if (!at.isAfter(now)) notify(context, r, at.toLocalDate())
        }
        Family.store.set(K_LAST, now.toString())
        schedule(context)
    }

    private fun notify(context: Context, r: Record, date: LocalDate) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH))
        }
        val done = PendingIntent.getBroadcast(
            context, r.id.hashCode(),
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_DONE)
                .putExtra(EXTRA_ID, r.id).putExtra(EXTRA_DATE, date.toString()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = Notifier.launchActivity?.let {
            PendingIntent.getActivity(context, 0, Intent(context, it), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(Notifier.appIcon)
            .setContentTitle("⏰ ${r.string("title") ?: "Reminder"}")
            .setContentText(r.string("note")?.takeIf { it.isNotBlank() } ?: "Tap Done when it's sorted, and it's ticked off for both of you.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Done", done)
            .apply { if (open != null) setContentIntent(open) }
            .build()
        try {
            NotificationManagerCompat.from(context).notify(r.id.hashCode(), n)
        } catch (e: SecurityException) {
            // Permission revoked between check and notify.
        }
    }

    /** Tick off the occurrence on [date]; syncs to the other phones. */
    fun markDone(context: Context, id: String, date: String) {
        Family.context.put(COLLECTION, id, mapOf("doneThrough" to date, "doneBy" to Family.myEmail, "doneAt" to System.currentTimeMillis()))
        NotificationManagerCompat.from(context).cancel(id.hashCode())
        schedule(context)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderAlarms.ACTION_DONE -> {
                val id = intent.getStringExtra(ReminderAlarms.EXTRA_ID) ?: return
                val date = intent.getStringExtra(ReminderAlarms.EXTRA_DATE) ?: return
                ReminderAlarms.markDone(context, id, date)
            }
            else -> ReminderAlarms.fire(context)
        }
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ReminderAlarms.schedule(context)
}
