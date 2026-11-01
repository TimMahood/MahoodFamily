package com.mahood.family.core.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mahood.family.core.Family
import com.mahood.family.core.data.Op

/** Local notifications raised after a sync finds changes from others. No push server involved. */
object Notifier {
    private const val CHANNEL = "family-updates"

    /** Set by the app so tapping a notification opens it. */
    var launchActivity: Class<*>? = null

    var appIcon: Int = android.R.drawable.ic_dialog_info

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Family updates", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    /** Whether the app is in the foreground; no notification is needed then. */
    @Volatile
    var appVisible = false

    fun notifyChanges(context: Context, remote: List<Op>) {
        if (appVisible) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val lines = Family.enabledModules().flatMap { it.notificationLines(Family.context, remote) }
        if (lines.isEmpty()) return
        ensureChannel(context)

        val intent = launchActivity?.let {
            PendingIntent.getActivity(
                context, 0, android.content.Intent(context, it),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        val style = NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach { s.addLine(it) } }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(appIcon)
            .setContentTitle(Family.familyName ?: "Family")
            .setContentText(lines.first())
            .setStyle(style)
            .setAutoCancel(true)
            .apply { if (intent != null) setContentIntent(intent) }
            .build()
        try {
            NotificationManagerCompat.from(context).notify(1, notification)
        } catch (e: SecurityException) {
            // Permission revoked between check and notify.
        }
    }
}
