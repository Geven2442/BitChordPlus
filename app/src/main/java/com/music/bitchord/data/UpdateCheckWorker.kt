package com.music.bitchord.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.music.bitchord.R
import java.util.concurrent.TimeUnit

/**
 * BitChord+ daily background check of the fork's GitHub releases. A newer
 * release posts one notification (once per version); tapping it opens the app,
 * whose launch check then shows the normal update dialog.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppUpdateChecker.check()
        val info = AppUpdateChecker.available.value ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_NOTIFIED, null) == info.version) return Result.success()
        if (notify(info.version)) prefs.edit().putString(KEY_NOTIFIED, info.version).apply()
        return Result.success()
    }

    private fun notify(version: String): Boolean {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return false
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP) ?: return false
        val tap = PendingIntent.getActivity(ctx, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(ctx.getString(R.string.update_notification_title))
            .setContentText(ctx.getString(R.string.update_notification_text, version))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    companion object {
        private const val PREFS = "update_check"
        private const val KEY_NOTIFIED = "notified_version"
        private const val CHANNEL = "app_update"
        private const val NOTIFICATION_ID = 7430
        private const val WORK = "update_check_daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
