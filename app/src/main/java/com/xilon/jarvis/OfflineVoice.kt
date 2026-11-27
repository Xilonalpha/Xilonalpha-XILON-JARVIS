package com.xilon.jarvis

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.jarvis.assistant.voice.SpeechChunks
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class OfflineVoice(context: Context) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val utteranceCounter = AtomicLong(1L)
    private val pendingUtterances = AtomicInteger(0)

    @Volatile private var ready = false
    @Volatile private var closed = false
    @Volatile private var streamOpen = false
    @Volatile private var currentPersona: VoicePersona = VoicePersona.fromId(context.getSharedPreferences("voice_stage", Context.MODE_PRIVATE).getString("persona", null))
    @Volatile private var activeGeneration = 0L

    var onFailure: (String) -> Unit = {}
    fun romanianVoiceAvailable() = ready && !closed && tts.voice?.let { it.locale.language == "ro" && !it.isNetworkConnectionRequired } == true
    var onSpeakingChanged: ((Boolean) -> Unit)? = null
    var onSpeechCadence: ((Float) -> Unit)? = null
    var onAllFinished: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = !closed && status == TextToSpeech.SUCCESS
        if (ready) {
            configureAudioAttributes()
            applyPersonaVoice(currentPersona)
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val generation = activeGeneration
                if (utteranceId?.startsWith("xilon-gen-$generation-") != true) return
                mainHandler.post {
                    if (closed || generation != activeGeneration) return@post
                    onSpeakingChanged?.invoke(true)
                    onSpeechCadence?.invoke(0.75f)
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (closed) return
                val pulse = 0.45f + ((end - start).coerceIn(1, 14) / 14f) * 0.55f
                mainHandler.post { if (!closed && utteranceId?.startsWith("xilon-gen-$activeGeneration-") == true) onSpeechCadence?.invoke(pulse) }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post { handleUtteranceComplete(utteranceId) }
            }

            @Deprecated("Deprecated in API 21")
            override fun onError(utteranceId: String?) {
                reportFailure(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                reportFailure(utteranceId)
            }
        })
    }

    private fun reportFailure(id: String?) {
        mainHandler.post {
            if (!closed && id?.startsWith("xilon-gen-$activeGeneration-") == true) {
                stop()
                onFailure("Sinteza vocală a eșuat.")
            }
        }
    }

    private fun handleUtteranceComplete(utteranceId: String?) {
        if (closed) return
        val gen = utteranceId?.substringAfter("gen-", "")?.substringBefore("-")?.toLongOrNull()
        if (gen != activeGeneration) return
        val remaining = pendingUtterances.decrementAndGet().coerceAtLeast(0)
        if (remaining == 0 && !streamOpen) {
            mainHandler.post {
                if (gen != activeGeneration || closed) return@post
                onSpeakingChanged?.invoke(false)
                onSpeechCadence?.invoke(0f)
                onAllFinished?.invoke()
            }
        }
    }

    private fun configureAudioAttributes() {
        runCatching {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }
    }

    fun setPersona(persona: VoicePersona) {
        currentPersona = persona
        if (ready && !closed) {
            applyPersonaVoice(persona)
        }
    }

    fun activeVoiceSummary(): String {
        if (!ready || closed) return "Se inițializează vocea..."
        val v = tts.voice
        return if (v != null) {
            val quality = when {
                v.quality >= Voice.QUALITY_VERY_HIGH -> "HD"
                v.quality >= Voice.QUALITY_HIGH -> "Natural"
                else -> "Standard"
            }
            val mode = if (v.isNetworkConnectionRequired) "Neural" else "Offline"
            "${v.locale.displayLanguage} • $quality ($mode)"
        } else {
            "Voce sistem (${Locale.getDefault().language.uppercase(Locale.ROOT)})"
        }
    }

    private fun applyPersonaVoice(persona: VoicePersona) {
        val available = runCatching { tts.voices?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { !it.isNetworkConnectionRequired && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }

        val roVoices = available.filter { it.locale.language.equals("ro", ignoreCase = true) }
        val chosen = pickBestVoice(roVoices, persona)

        if (chosen != null) tts.setVoice(chosen)
        tts.setSpeechRate(persona.speechRate)
        tts.setPitch(persona.pitch)
    }

    private fun pickBestVoice(candidates: List<Voice>, persona: VoicePersona): Voice? {
        if (candidates.isEmpty()) return null
        return candidates.maxWithOrNull(
            compareBy<Voice>(
                { it.quality },
                { !it.isNetworkConnectionRequired },
                {
                    val lowerName = it.name.lowercase(Locale.ROOT)
                    when {
                        persona.preferLowerTone && (lowerName.contains("male") || lowerName.contains("m-") || lowerName.contains("d-")) -> 2
                        !persona.preferLowerTone && (lowerName.contains("female") || lowerName.contains("f-") || lowerName.contains("a-")) -> 2
                        else -> 1
                    }
                },
                { it.name.contains("local", ignoreCase = true) || it.name.contains("network", ignoreCase = true) }
            )
        )
    }

    /**
     * Speaks a complete conversational response naturally (cleans markdown & splits into low-latency chunks).
     */
    fun speak(text: String, persona: VoicePersona = currentPersona): String? {
        if (!ready || closed) return "Motorul vocal se inițializează sau nu este disponibil."
        if (!romanianVoiceAvailable()) return "Instalează o voce română offline în setările Android sau selectează Piper."
        val clean = ConversationalSpeechFormatter.cleanForSpeech(text)
        if (clean.isBlank()) {
            mainHandler.post { onAllFinished?.invoke() }
            return null
        }

        currentPersona = persona
        applyPersonaVoice(persona)
        streamOpen = false
        val gen = ++activeGeneration
        pendingUtterances.set(0)

        val chunks = SpeechChunks.split(clean, first = 72, max = 140)
        if (chunks.isEmpty()) {
            mainHandler.post { onAllFinished?.invoke() }
            return null
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }

        for ((index, chunk) in chunks.withIndex()) {
            val id = "xilon-gen-$gen-${utteranceCounter.getAndIncrement()}"
            pendingUtterances.incrementAndGet()
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val status = tts.speak(chunk, mode, params, id)
            if (status != TextToSpeech.SUCCESS) {
                pendingUtterances.set(0)
                tts.stop()
                return "Sinteza vocală a eșuat."
            }
        }
        return null
    }

    /**
     * Begins a low-latency streaming speech session (speaks sentences as the LLM produces them).
     */
    fun beginStream(persona: VoicePersona = currentPersona) {
        if (!ready || closed) return
        currentPersona = persona
        applyPersonaVoice(persona)
        tts.stop()
        activeGeneration++
        pendingUtterances.set(0)
        streamOpen = true
    }

    /**
     * Queues a single newly-completed sentence during streaming generation.
     */
    fun enqueueStreamSentence(sentence: String): String? {
        if (!ready || closed || !streamOpen || !romanianVoiceAvailable()) return "Vocea română offline nu este disponibilă."
        val clean = ConversationalSpeechFormatter.cleanForSpeech(sentence)
        if (clean.isBlank()) return null
        val gen = activeGeneration
        val isFirst = pendingUtterances.get() == 0
        val id = "xilon-gen-$gen-${utteranceCounter.getAndIncrement()}"
        pendingUtterances.incrementAndGet()
        val mode = if (isFirst) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val status = tts.speak(clean, mode, null, id)
        if (status != TextToSpeech.SUCCESS) {
            stop()
            return "Sinteza vocală a eșuat."
        }
        return null
    }

    /**
     * Flushes any final trailing fragment and closes the stream so [onAllFinished] fires cleanly.
     */
    fun finishStream(trailingFragment: String = "") {
        if (closed) return
        if (trailingFragment.isNotBlank()) {
            enqueueStreamSentence(trailingFragment)
        }
        streamOpen = false
        val generation = activeGeneration
        if (pendingUtterances.get() <= 0) {
            mainHandler.post {
                if (closed || generation != activeGeneration) return@post
                onSpeakingChanged?.invoke(false)
                onSpeechCadence?.invoke(0f)
                onAllFinished?.invoke()
            }
        }
    }

    fun isSpeaking(): Boolean = !closed && ready && (tts.isSpeaking || pendingUtterances.get() > 0)

    fun stop() {
        streamOpen = false
        activeGeneration++
        pendingUtterances.set(0)
        if (!closed) {
            tts.stop()
        }
        mainHandler.post {
            onSpeakingChanged?.invoke(false)
            onSpeechCadence?.invoke(0f)
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            ready = false
            streamOpen = false
            activeGeneration++
            pendingUtterances.set(0)
            tts.stop()
            tts.shutdown()
        }
    }
}
