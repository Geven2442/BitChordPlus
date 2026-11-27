package com.music.bitchord.voice

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings

/**
 * The button-triggered listener: a see-through activity the Quick Settings tile
 * opens, which listens for one command, runs it and closes. It is an activity
 * because the microphone may only be started while something of the app is on
 * screen.
 */
class VoiceListenActivity : ComponentActivity() {

    private var recognizer: VoiceRecognizer? = null
    private var controller: ListenableFuture<MediaController>? = null
    private val handler = Handler(Looper.getMainLooper())

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) listen() else finishWith(R.string.voice_no_permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AppSettings.voiceControl.value) {
            finishWith(R.string.voice_not_enabled)
            return
        }
        if (VoiceControlService.canRecord(this)) listen() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun listen() {
        val future = VoiceActions.connect(this)
        controller = future
        val r = VoiceRecognizer(
            context = this,
            continuous = false,
            onCommand = { cmd, heard ->
                VoiceActions.run(this, future, cmd)
                Toast.makeText(this, heard, Toast.LENGTH_SHORT).show()
            },
            onFinished = { heard ->
                if (heard) {
                    finish()
                } else {
                    // The recogniser's own error code, so "heard nothing" and "the
                    // recogniser is broken" can be told apart.
                    val code = recognizer?.lastError ?: 0
                    val text = getString(R.string.voice_not_understood) + if (code > 0 && code != 7 && code != 6) " ($code)" else ""
                    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                    finish()
                }
            },
            onFatal = { finishWith(R.string.voice_no_permission) },
        )
        if (!r.start()) {
            finishWith(R.string.voice_not_available)
            return
        }
        recognizer = r
        Toast.makeText(this, R.string.voice_listening, Toast.LENGTH_SHORT).show()
        handler.postDelayed({ finish() }, LISTEN_LIMIT_MS)
    }

    private fun finishWith(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.stop()
        // The command has been sent by now, but give the session a moment before
        // letting go of the binding so a pending play can start the service.
        val held = controller
        if (held != null) handler.postDelayed({ MediaController.releaseFuture(held) }, 3_000L)
        super.onDestroy()
    }

    private companion object {
        const val LISTEN_LIMIT_MS = 10_000L
    }
}
