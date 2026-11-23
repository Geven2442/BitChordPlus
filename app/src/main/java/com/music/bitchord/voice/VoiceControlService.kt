package com.music.bitchord.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps the microphone open so music can be steered by voice without touching
 * the phone — on a bike, with the phone in a pocket.
 *
 * Android only lets a microphone service *start* while the app is on screen
 * (settings switch, or the app being opened), after which it may keep running in
 * the background. That is why it is started from the UI and not from boot.
 */
class VoiceControlService : Service() {

    private var recognizer: VoiceRecognizer? = null
    private var controller: ListenableFuture<MediaController>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AppSettings.setVoiceControlContinuous(false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!canRecord(this) || !startForegroundSafely()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (recognizer == null) {
            val future = VoiceActions.connect(this)
            controller = future
            val r = VoiceRecognizer(
                context = this,
                continuous = true,
                onCommand = { cmd, _ -> VoiceActions.run(this, future, cmd) },
                onFatal = { stopSelf() },
            )
            if (!r.start()) {
                MediaController.releaseFuture(future)
                controller = null
                stopSelf()
                return START_NOT_STICKY
            }
            recognizer = r
            _running.value = true
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        recognizer?.stop()
        recognizer = null
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        _running.value = false
        super.onDestroy()
    }

    private fun startForegroundSafely(): Boolean = runCatching {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.voice_control), NotificationManager.IMPORTANCE_LOW),
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, VoiceControlService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(getString(R.string.voice_notification_title))
            .setContentText(getString(R.string.voice_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.voice_notification_stop), stop)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
    }.isSuccess

    companion object {
        private const val CHANNEL_ID = "voice_control"
        private const val NOTIFICATION_ID = 7421
        private const val ACTION_STOP = "com.music.bitchord.voice.STOP"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        fun canRecord(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        /** Call only while the app is on screen — see the class note. */
        fun start(context: Context) {
            if (!canRecord(context)) return
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, VoiceControlService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceControlService::class.java))
        }
    }
}
