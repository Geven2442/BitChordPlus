package com.music.bitchord.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Listens through the system speech recogniser and hands over the first phrase
 * that is a [VoiceCommand].
 *
 * One-shot ([continuous] false): listens once, reports, and calls [onFinished].
 * Continuous: starts a new listening session after every result or error, with
 * a back-off when the recogniser keeps failing, until [stop].
 *
 * Everything runs on the main thread — a SpeechRecognizer insists on it.
 */
class VoiceRecognizer(
    private val context: Context,
    private val continuous: Boolean,
    private val onCommand: (VoiceCommand, String) -> Unit,
    private val onFinished: (heardCommand: Boolean) -> Unit = {},
    private val onFatal: () -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var failures = 0
    private var useOnDevice = false
    private var switched = false

    /** The last recogniser error code, for telling the user why nothing happened. */
    var lastError: Int = 0
        private set

    val isActive: Boolean get() = active

    /** False when this device has no speech recogniser to use. */
    fun start(): Boolean {
        if (active) return true
        val created = create() ?: return false
        recognizer = created
        active = true
        failures = 0
        listen()
        return true
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun create(): SpeechRecognizer? {
        // The system's regular recogniser first (on Samsung that is Google's, which
        // has Chinese models). The on-device one is the fallback: it needs a
        // language pack that is often missing and then fails without a sound.
        val r = when {
            !useOnDevice && SpeechRecognizer.isRecognitionAvailable(context) ->
                SpeechRecognizer.createSpeechRecognizer(context)
            Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
            else -> return null
        }
        r.setRecognitionListener(listener)
        return r
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            if (useOnDevice) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        runCatching { recognizer?.startListening(intent) }.onFailure { scheduleRestart(1_000L) }
    }

    private fun scheduleRestart(delayMs: Long) {
        if (!active) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!active) return@postDelayed
            runCatching { recognizer?.cancel() }
            listen()
        }, delayMs)
    }

    private fun finishOneShot(heard: Boolean) {
        if (!active) return
        stop()
        onFinished(heard)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            if (!active) return
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val hit = VoiceCommandParser.parseAny(heard, strict = continuous)
            if (hit != null) {
                failures = 0
                onCommand(hit.first, hit.second)
            }
            if (continuous) scheduleRestart(150L) else finishOneShot(hit != null)
        }

        override fun onError(error: Int) {
            if (!active) return
            lastError = error
            // A recogniser that cannot do this language or has gone away is swapped
            // for the other kind once, instead of retrying the same dead one.
            if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                error == SpeechRecognizer.ERROR_CLIENT ||
                error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
            ) {
                if (!switched) {
                    switched = true
                    useOnDevice = !useOnDevice
                    runCatching { recognizer?.destroy() }
                    recognizer = create()
                    if (recognizer == null) {
                        if (continuous) stop().also { onFatal() } else finishOneShot(false)
                    } else {
                        scheduleRestart(300L)
                    }
                    return
                }
            }
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    stop()
                    onFatal()
                }
                // Nothing said, or nothing understood: the normal idle state of
                // a recogniser that is always listening.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    if (continuous) scheduleRestart(150L) else finishOneShot(false)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    if (continuous) scheduleRestart(1_000L) else finishOneShot(false)
                else -> {
                    if (!continuous) {
                        finishOneShot(false)
                    } else {
                        failures++
                        // 1 s, 2 s, 4 s … up to 10 s, so a recogniser that is down
                        // (offline, model missing) is not hammered.
                        scheduleRestart((1_000L shl (failures - 1).coerceAtMost(4)).coerceAtMost(10_000L))
                    }
                }
            }
        }
    }

    private fun languageTag(): String {
        val device = Locale.getDefault()
        return if (device.language == "zh") {
            if (device.country == "CN") "zh-CN" else "zh-TW"
        } else {
            device.toLanguageTag()
        }
    }
}
