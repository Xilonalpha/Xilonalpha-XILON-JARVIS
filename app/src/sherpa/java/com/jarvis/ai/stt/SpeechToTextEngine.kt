package com.jarvis.ai.stt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.jarvis.ai.memory.ModelStore
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.ArrayDeque
import kotlin.math.sqrt

/** Captures a short utterance and transcribes it offline with Sherpa-ONNX SenseVoice or Whisper. */
class SpeechToTextEngine(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val modelDirectory = ModelStore.modelDirectory(appContext)
    private val operationMutex = Mutex()
    // A stop must invalidate requests already waiting for the recognizer mutex.
    private val cancellationEpoch = java.util.concurrent.atomic.AtomicLong(0L)

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    @Volatile
    private var onlineRecognizer: OnlineRecognizer? = null

    @Volatile
    private var activeRecorder: AudioRecord? = null

    @Volatile
    private var closed = false

    @Volatile
    private var captureCancelled = false

    /** Prefer the multilingual Whisper decoder for Romanian instead of the English-only streaming model. */
    private fun hasRomanianWhisper():Boolean = ModelStore.whisperFiles(
        modelDirectory.listFiles()?.filter{it.isFile && it.length()>0L}.orEmpty()
    )!=null

    fun hasStreamingModelFiles():Boolean {
        val directory=File(modelDirectory,"streaming-asr")
        return listOf("encoder.int8.onnx","decoder.int8.onnx","joiner.int8.onnx","tokens.txt").all { name ->
            File(directory,name).let{it.isFile && it.length()>0L}
        }
    }

    /** Actually constructs the native recognizer; file presence alone is not readiness. */
    fun validateModelLoad(): Boolean {
        check(!closed) { "Speech engine closed" }
        kotlinx.coroutines.runBlocking {
            operationMutex.withLock {
                check(!closed) { "Speech engine closed" }
                if (hasRomanianWhisper()) ensureRecognizer()
                else if (ensureOnlineRecognizer() == null) ensureRecognizer()
            }
        }
        return true
    }

    /** Records until silence is detected, then runs local Sherpa-ONNX decoding. */
    suspend fun listenForCommand(onPartial: (String) -> Unit = {}): String {
        val requestEpoch = cancellationEpoch.get()
        return operationMutex.withLock {
        withContext(Dispatchers.IO) {
            if (requestEpoch != cancellationEpoch.get() || closed) return@withContext ""
            check(!closed) { "Speech recognition has stopped." }
            check(
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED,
            ) { "Microphone permission is required. Open JarvisAI and allow microphone access." }

            captureCancelled = false
            val online = if (hasRomanianWhisper()) null else ensureOnlineRecognizer()
            if (online != null) return@withContext captureStreaming(online, requestEpoch, onPartial)
            val activeRecognizer = ensureRecognizer()
            if (captureCancelled || closed || requestEpoch != cancellationEpoch.get()) return@withContext ""
            val recording = captureUtterance(requestEpoch)
            if (captureCancelled || closed || requestEpoch != cancellationEpoch.get() || recording.isEmpty()) return@withContext ""

            val stream = activeRecognizer.createStream()
            try {
                stream.acceptWaveform(recording, SAMPLE_RATE)
                if (captureCancelled || closed || requestEpoch != cancellationEpoch.get()) return@withContext ""
                activeRecognizer.decode(stream)
                if (captureCancelled || closed || requestEpoch != cancellationEpoch.get()) "" else activeRecognizer.getResult(stream).text.trim()
            } finally {
                stream.release()
            }
        }
        }
    }

    /** Stops any active microphone read and releases Sherpa's native recognizer. */
    fun stopCapture() {
        cancellationEpoch.incrementAndGet()
        captureCancelled = true
        runCatching { activeRecorder?.stop() }
    }

    override fun close() {
        closed = true
        stopCapture()
        // JNI recognizers must not be released while decode() is using them.
        // The engine is closed by the dedicated speech worker after listen returns.
        kotlinx.coroutines.runBlocking {
            operationMutex.withLock {
                activeRecorder = null
                runCatching { recognizer?.release() }
                recognizer = null
                runCatching { onlineRecognizer?.release() }
                onlineRecognizer = null
            }
        }
    }

    /**
     * Optional real streaming transducer. Only load a model explicitly installed
     * under models/streaming-asr; offline Whisper/SenseVoice are not online models.
     * The same Sherpa JNI processes 100 ms frames and produces partial hypotheses.
     */
    private fun ensureOnlineRecognizer(): OnlineRecognizer? {
        onlineRecognizer?.let { return it }
        val directory = File(modelDirectory, "streaming-asr")
        if (!directory.isDirectory) return null
        val files = directory.listFiles()?.filter { it.isFile && it.length() > 0L }.orEmpty()
        fun find(prefix: String): File? = files.firstOrNull {
            it.name.startsWith(prefix) && it.name.endsWith(".onnx", ignoreCase = true)
        }
        val encoder = find("encoder") ?: return null
        val decoder = find("decoder") ?: return null
        val joiner = find("joiner") ?: return null
        val tokens = files.firstOrNull { it.name == "tokens.txt" } ?: return null
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = encoder.absolutePath,
                    decoder = decoder.absolutePath,
                    joiner = joiner.absolutePath,
                ),
                tokens = tokens.absolutePath,
                numThreads = minOf(2, Runtime.getRuntime().availableProcessors().coerceAtLeast(1)),
            ),
            enableEndpoint = true,
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.4f, 0f),
                rule2 = EndpointRule(true, 0.8f, 0f),
                rule3 = EndpointRule(false, 0f, MAX_RECORDING_SECONDS.toFloat()),
            ),
        )
        return OnlineRecognizer(assetManager = null, config = config).also {
            onlineRecognizer = it
        }
    }

    /** True incremental decoding; no 20-second PCM accumulation or offline decode. */
    private fun captureStreaming(online: OnlineRecognizer, epoch: Long, onPartial: (String) -> Unit): String {
        val minimum = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimum > 0) { "Microfonul nu acceptă captură la 16 kHz." }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum, FRAME_SAMPLES * 4),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            error("Microfonul nu poate fi inițializat.")
        }
        activeRecorder = recorder
        val stream = try { online.createStream() } catch (failure: Throwable) {
            activeRecorder = null
            recorder.release()
            throw failure
        }
        val pcm = ShortArray(FRAME_SAMPLES)
        val normalizedFrame = FloatArray(FRAME_SAMPLES)
        var lastPartial = ""
        var totalSamples = 0
        var zeroReads = 0
        val aec = runCatching {
            if (android.media.audiofx.AcousticEchoCanceler.isAvailable())
                android.media.audiofx.AcousticEchoCanceler.create(recorder.audioSessionId)
            else null
        }.getOrNull()
        val ns = runCatching {
            if (android.media.audiofx.NoiseSuppressor.isAvailable())
                android.media.audiofx.NoiseSuppressor.create(recorder.audioSessionId)
            else null
        }.getOrNull()
        try {
            runCatching { aec?.enabled = true }
            runCatching { ns?.enabled = true }
            recorder.startRecording()
            while (!closed && !captureCancelled && epoch == cancellationEpoch.get() &&
                totalSamples < SAMPLE_RATE * MAX_RECORDING_SECONDS) {
                val n = recorder.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                if (n < 0) {
                    if (captureCancelled || closed) break
                    error("Eroare de citire microfon ($n)")
                }
                if (n == 0) {
                    if (++zeroReads >= 30) break
                    Thread.sleep(10)
                    continue
                }
                zeroReads = 0
                totalSamples += n
                for (i in 0 until n) normalizedFrame[i] = pcm[i] / 32768.0f
                // Normal 100-ms reads reuse the same array rather than allocating every frame.
                stream.acceptWaveform(if (n == normalizedFrame.size) normalizedFrame else normalizedFrame.copyOf(n), SAMPLE_RATE)
                while (!captureCancelled && !closed && epoch == cancellationEpoch.get() && online.isReady(stream)) {
                    online.decode(stream)
                }
                if (captureCancelled || closed || epoch != cancellationEpoch.get()) break
                val partial = online.getResult(stream).text.trim()
                if (partial.isNotBlank() && partial != lastPartial) {
                    lastPartial = partial
                    // A UI callback must not break the native capture loop.
                    runCatching { onPartial(partial) }
                }
                if (online.isEndpoint(stream) && lastPartial.isNotBlank()) break
            }
            if (captureCancelled || closed || epoch != cancellationEpoch.get()) return ""
            stream.inputFinished()
            while (!captureCancelled && !closed && epoch == cancellationEpoch.get() && online.isReady(stream)) online.decode(stream)
            if (captureCancelled || closed || epoch != cancellationEpoch.get()) return ""
            return online.getResult(stream).text.trim().ifBlank { lastPartial }
        } finally {
            runCatching { recorder.stop() }
            runCatching { aec?.release() }
            runCatching { ns?.release() }
            recorder.release()
            activeRecorder = null
            stream.release()
        }
    }

    private fun ensureRecognizer(): OfflineRecognizer {
        recognizer?.let { return it }
        val files = modelDirectory.listFiles()?.filter { it.isFile && it.length() > 0L }.orEmpty()
        val senseVoice = ModelStore.senseVoiceFiles(files)
        val whisper = ModelStore.whisperFiles(files)
        val memory=android.app.ActivityManager.MemoryInfo().also{appContext.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it)}
        val thermal=if(android.os.Build.VERSION.SDK_INT>=29)appContext.getSystemService(android.os.PowerManager::class.java).currentThermalStatus else 0
        val threads=com.xilon.jarvis.ResourceBudget.speechThreads(Runtime.getRuntime().availableProcessors(),memory.availMem/1048576,memory.lowMemory,thermal)
        val modelConfig = when {
            senseVoice != null && whisper == null -> OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = senseVoice.first.absolutePath,
                    // SenseVoice supports automatic language detection; do not force English
                    // for Romanian utterances when this model is the installed recognizer.
                    language = "auto",
                    useInverseTextNormalization = true,
                ),
                tokens = senseVoice.second.absolutePath,
                numThreads = threads,
                modelType = "sense_voice",
            )
            whisper != null -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = whisper.first.absolutePath,
                    decoder = whisper.second.absolutePath,
                    language = "ro",
                    task = "transcribe",
                    tailPaddings = 1_000,
                ),
                tokens = whisper.third.absolutePath,
                numThreads = threads,
                modelType = "whisper",
            )
            else -> throw IllegalStateException(
                "Speech model files are missing. Import a Sherpa-ONNX SenseVoice model and tokens.txt, " +
                    "or a Whisper encoder, decoder, and tokens file from the dashboard.",
            )
        }
        return OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
                modelConfig = modelConfig,
            ),
        ).also { recognizer = it }
    }

    private fun captureUtterance(epoch: Long): FloatArray {
        val minimumBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBufferBytes > 0) { "This device does not support 16 kHz microphone capture." }
        val recorder = AudioRecord(
            if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimumBufferBytes, FRAME_SAMPLES * 4),
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "Android could not initialize the microphone. Check device privacy settings."
        }
        activeRecorder = recorder

        // Primitive PCM buffer avoids boxing every sample (up to 320,000 Floats).
        // Allocate the bounded maximum once; no repeated GC during capture.
        val captured = FloatArray(SAMPLE_RATE * MAX_RECORDING_SECONDS)
        var capturedCount = 0
        fun appendSamples(samples: FloatArray) {
            val copyCount = minOf(samples.size, captured.size - capturedCount)
            if (copyCount > 0) {
                samples.copyInto(captured, capturedCount, 0, copyCount)
                capturedCount += copyCount
            }
        }
        val preRoll = ArrayDeque<FloatArray>()
        val pcm = ShortArray(FRAME_SAMPLES)
        var started = false
        var silentSamples = 0
        var voicedSamples = 0
        var peakSpeechRms = 0f
        var totalSamples = 0
        var zeroReads = 0
        var emptyReads = 0
        val aec=runCatching { if(android.media.audiofx.AcousticEchoCanceler.isAvailable()) android.media.audiofx.AcousticEchoCanceler.create(recorder.audioSessionId) else null }.getOrNull()
        val ns=runCatching { if(android.media.audiofx.NoiseSuppressor.isAvailable()) android.media.audiofx.NoiseSuppressor.create(recorder.audioSessionId) else null }.getOrNull()
        try {
            runCatching { aec?.setEnabled(true) }
            runCatching { ns?.setEnabled(true) }
            recorder.startRecording()
            while (!closed && !captureCancelled && epoch == cancellationEpoch.get() && totalSamples < SAMPLE_RATE * MAX_RECORDING_SECONDS) {
                val count = recorder.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                if (count < 0) {
                    if (captureCancelled || closed) break
                    if (count == AudioRecord.ERROR_DEAD_OBJECT) error("The microphone was disconnected.")
                    error("Android microphone capture failed ($count).")
                }
                if (count == 0) {
                    // Some Android audio drivers repeatedly return zero after interruption.
                    // Do not spin forever or monopolize the voice worker.
                    if (closed || captureCancelled || ++emptyReads >= 30) break
                    Thread.sleep(10)
                    continue
                }
                emptyReads = 0
                // Digital silence detection adapted from judejulius/jarvis VoiceInput.kt.
                if (isDigitalSilence(pcm, count)) { if (++zeroReads >= 30) error("Microfon blocat sau dezactivat de Android.") } else zeroReads = 0
                totalSamples += count
                val chunk = FloatArray(count) { index -> pcm[index] / 32768.0f }
                // Feed undistorted normalized PCM to Whisper.
                var power = 0.0
                for (sample in chunk) power += sample * sample
                val rms = sqrt(power / count).toFloat()
                val threshold = SPEECH_RMS_THRESHOLD
                if (rms > peakSpeechRms) peakSpeechRms = rms
                if (!started) {
                    preRoll.addLast(chunk)
                    while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
                    if (rms >= threshold) {
                        started = true
                        voicedSamples += count
                        preRoll.forEach { appendSamples(it) }
                        preRoll.clear()
                        silentSamples = 0
                    }
                } else {
                    appendSamples(chunk)
                    if (rms >= threshold) { silentSamples = 0; voicedSamples += count } else silentSamples += count
                    // A long utterance can contain brief natural pauses. Avoid
                    // ending on a weak trailing frame until speech has been heard.
                    if (silentSamples >= SILENCE_SAMPLES && voicedSamples >= MIN_VOICED_SAMPLES) break
                }
            }
        } finally {
            runCatching { recorder.stop() }
            runCatching { aec?.release() }
            runCatching { ns?.release() }
            recorder.release()
            activeRecorder = null
        }

        // Reject transient clicks and playback leakage shorter than 180 ms.
        if (closed || captureCancelled || epoch != cancellationEpoch.get() || !started || voicedSamples < MIN_VOICED_SAMPLES || peakSpeechRms < SPEECH_RMS_THRESHOLD || capturedCount == 0) return FloatArray(0)
        return captured.copyOf(capturedCount)
    }

    private fun isDigitalSilence(pcm: ShortArray, n: Int): Boolean {
        for (i in 0 until n) if (pcm[i].toInt() != 0) return false
        return true
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val FEATURE_DIM = 80
        const val FRAME_SAMPLES = 1_600 // 100 ms
        const val PRE_ROLL_FRAMES = 6
        const val SILENCE_SAMPLES = 12_800 // 800 ms; shorter turn-taking latency
        const val MIN_VOICED_SAMPLES = 2_880 // 180 ms
        const val MAX_RECORDING_SECONDS = 20
        const val SPEECH_RMS_THRESHOLD = 0.006f
    }
}
