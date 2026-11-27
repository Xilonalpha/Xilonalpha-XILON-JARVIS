package com.xilon.jarvis

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class OfflineListening(private val context: Context) : AutoCloseable {
    private var engine: SpeechRecognizer? = null

    var onLevel: (Float) -> Unit = {}
    var onPartialText: (String) -> Unit = {}
    var onListening: (Boolean) -> Unit = {}
    var onSpeechStart: () -> Unit = {}
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var timeout: Runnable? = null
    fun listen(
        result: (String) -> Unit,
        error: (String) -> Unit,
        onPartial: ((String) -> Unit)? = onPartialText,
        onRms: ((Float) -> Unit)? = onLevel,
        onReady: (() -> Unit)? = null,
        onSpeechEnd: (() -> Unit)? = null,
        preferOffline: Boolean = true,
    ) {
        close()
        val recognizer = createBestRecognizer(preferOffline)
        if (recognizer == null) {
            error("Serviciul de recunoaștere vocală Android nu este disponibil pe acest dispozitiv.")
            return
        }
        try {
            engine = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (engine === recognizer) onReady?.invoke()
                }

                override fun onBeginningOfSpeech() { if (engine === recognizer) onSpeechStart() }

                override fun onRmsChanged(rmsdB: Float) {
                    if (engine === recognizer) {
                        // Android SpeechRecognizer rmsdB typically ranges from -2f to ~12f
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        onRms?.invoke(normalized)
                    }
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    if (engine === recognizer) {
                        onRms?.invoke(0f)
                        onSpeechEnd?.invoke()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (engine !== recognizer) return
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        onPartial?.invoke(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onError(code: Int) {
                    if (engine === recognizer) {
                        close()
                        onRms?.invoke(0f)
                        error(mapErrorMessage(code))
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (engine !== recognizer) return
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    close()
                    onRms?.invoke(0f)
                    if (text.isNullOrBlank()) {
                        error("Nu am detectat un mesaj vocal.")
                    } else {
                        result(text)
                    }
                }
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ro-RO")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ro-RO")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1100L)
                if (preferOffline) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
            onListening(true)
            timeout = Runnable { if (engine === recognizer) { close(); error("Recunoaștere offline: timp depășit.") } }.also { handler.postDelayed(it, 30000) }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            close()
            error("Microfon: ${e.message ?: "eroare necunoscută"}")
        }
    }

    private fun createBestRecognizer(preferOffline: Boolean): SpeechRecognizer? {
        return try {
            if (preferOffline && Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mapErrorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permisiunea microfonului este necesară."
        SpeechRecognizer.ERROR_AUDIO -> "Eroare audio la microfon."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Recunoașterea vocală necesită conexiune sau pachetul vocal român offline."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Microfonul este ocupat, încerc din nou..."
        12, 13 -> "Pachetul vocal român Android nu este disponibil."
        else -> "Recunoaștere vocală: eroare $code"
    }

    override fun close() {
        timeout?.let(handler::removeCallbacks); timeout = null
        onLevel(0f); onListening(false)
        val previous = engine
        engine = null
        runCatching { previous?.cancel() }
        runCatching { previous?.destroy() }
    }
}
