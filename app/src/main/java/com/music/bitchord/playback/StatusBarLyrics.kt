package com.music.bitchord.playback

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.music.bitchord.R

/**
 * The current lyric line as an Android 16+ Live Update: the short critical
 * text becomes the status bar chip, and Samsung's One UI also shows it in the
 * Now Bar. On older systems, or before the user allows Live Updates for the
 * app, it is an ordinary silent notification.
 */
class StatusBarLyrics(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var shown: String? = null

    fun update(line: String?, title: String?, artist: String?) {
        if (line.isNullOrBlank()) {
            cancel()
            return
        }
        val key = "$line|$title|$artist"
        if (key == shown) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel()
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 7421, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(line)
            .setContentText(listOfNotNull(title, artist).filter { it.isNotBlank() }.joinToString(" · "))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        open?.let(builder::setContentIntent)
        promote(builder, line)
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
        shown = key
    }

    @SuppressLint("NewApi")
    private fun promote(builder: Notification.Builder, line: String) {
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
            builder.setShortCriticalText(line)
        }
    }

    fun cancel() {
        if (shown == null) return
        shown = null
        runCatching { manager.cancel(NOTIFICATION_ID) }
    }

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL) != null) return
        // LOW rather than MIN: MIN channels can't be promoted to Live Updates.
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.status_bar_lyrics),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL = "status_bar_lyrics"
        private const val NOTIFICATION_ID = 7421
    }
}
