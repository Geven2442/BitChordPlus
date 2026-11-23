package com.music.bitchord.voice

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.music.bitchord.playback.ACTION_TOGGLE_SHUFFLE
import com.music.bitchord.playback.PlaybackService
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Carries a [VoiceCommand] out against the player and the phone's media volume. */
object VoiceActions {

    /**
     * A controller bound to the playback session. Binding, not starting, is what
     * is allowed from the background — see [com.music.bitchord.widget.MediaWidgetActions].
     */
    fun connect(context: Context): ListenableFuture<MediaController> {
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        return MediaController.Builder(app, token).buildAsync()
    }

    /** Runs [cmd] once the controller is up. Returns immediately. */
    fun run(context: Context, future: ListenableFuture<MediaController>, cmd: VoiceCommand) {
        val app = context.applicationContext
        future.addListener(
            {
                val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
                runCatching { execute(app, controller, cmd) }
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    private fun execute(context: Context, c: MediaController, cmd: VoiceCommand) {
        when (cmd) {
            VoiceCommand.VolumeUp -> stepVolume(context, +1)
            VoiceCommand.VolumeDown -> stepVolume(context, -1)
            is VoiceCommand.VolumePercent -> setVolume(context, cmd.percent)
            VoiceCommand.Mute -> audio(context).adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI,
            )
            VoiceCommand.Unmute -> audio(context).adjustStreamVolume(
                AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI,
            )
            else -> {
                // No live or restored queue: nothing for the transport to act on.
                if (c.mediaItemCount == 0) return
                when (cmd) {
                    VoiceCommand.Next -> { c.seekToNextMediaItem(); prepareIfIdle(c) }
                    VoiceCommand.Previous -> { c.seekToPreviousMediaItem(); prepareIfIdle(c) }
                    VoiceCommand.Pause -> c.pause()
                    VoiceCommand.Play -> {
                        if (c.playbackState == Player.STATE_ENDED) c.seekTo(0L)
                        prepareIfIdle(c)
                        c.play()
                    }
                    VoiceCommand.Restart -> { c.seekTo(0L); prepareIfIdle(c) }
                    is VoiceCommand.Seek -> {
                        val duration = c.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        c.seekTo((c.currentPosition + cmd.deltaMs).coerceIn(0L, duration))
                    }
                    VoiceCommand.ToggleShuffle -> c.sendCustomCommand(
                        SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY), Bundle.EMPTY,
                    )
                    is VoiceCommand.Repeat -> c.repeatMode = when (cmd.kind) {
                        RepeatKind.OFF -> Player.REPEAT_MODE_OFF
                        RepeatKind.ONE -> Player.REPEAT_MODE_ONE
                        RepeatKind.ALL -> Player.REPEAT_MODE_ALL
                    }
                    else -> Unit
                }
            }
        }
        confirm(context)
    }

    private fun prepareIfIdle(c: MediaController) {
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
    }

    private fun audio(context: Context) = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** About a tenth of the range per "louder" / "quieter", never less than one notch. */
    private fun stepVolume(context: Context, direction: Int) {
        val am = audio(context)
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val step = max(1, (maxVol * 0.1f).roundToInt())
        val next = (am.getStreamVolume(AudioManager.STREAM_MUSIC) + direction * step).coerceIn(0, maxVol)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, next, AudioManager.FLAG_SHOW_UI)
    }

    private fun setVolume(context: Context, percent: Int) {
        val am = audio(context)
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = min(maxVol, (percent / 100f * maxVol).roundToInt())
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
    }

    /** A short buzz so the rider knows it was heard without looking at the phone. */
    private fun confirm(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
