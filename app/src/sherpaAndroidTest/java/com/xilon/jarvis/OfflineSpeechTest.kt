package com.xilon.jarvis

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class OfflineSpeechTest {
    @Test fun installOfficialPackagesAndPlayAllThreeVoices() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val hasFixtures = !assets.list("speech").isNullOrEmpty()
        if (InstrumentationRegistry.getArguments().getString("requireVoiceFixtures") == "true") {
            assertTrue("Missing pinned voice fixtures", hasFixtures)
        } else {
            assumeTrue("Stage the pinned voice fixtures to run this test", hasFixtures)
        }
        val app = instrumentation.targetContext
        val root = java.nio.file.Files.createTempDirectory(app.cacheDir.toPath(), "voice-test").toFile()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = root
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences(root.name + "-" + name, mode)
        }
        fun fixture(name: String, dest: File = File(root, name)): File {
            assets.open("speech/$name").use { src -> dest.outputStream().use { src.copyTo(it) } }
            return dest
        }
        try {
            RomanianPiperArchive.install(context, Uri.fromFile(fixture(ModelCatalog.mihai.file)))
            assertTrue(ModelInventory.installed(context, ModelCatalog.mihai))
            val piper = checkNotNull(NeuralVoiceFactory.romanian(context)) { "Sherpa Piper unavailable" }
            play(piper, "Bună! Sunt Mihai.")
            val dir = File(root, "models/romanian")
            for (entry in ModelCatalog.romanianVoice) {
                val name = entry.file.substringAfterLast('/')
                fixture(name, File(dir, name))
                assertTrue(ModelInventory.installed(context, entry))
            }
            context.getSharedPreferences("piper_voice", Context.MODE_PRIVATE).edit().putString("speaker", "liana").commit()
            play(piper, "Bună! Sunt Liana.")
            for (name in RomanianWhisperOnnx.files) fixture(name, File(dir, name))
            val speech = makeSpeechAt16k(File(dir, "mihai"))
            RomanianWhisperOnnx(dir, 2).use { model ->
                val transcript = model.transcribe(speech) { false }
                assertTrue("Unexpected Romanian transcription: $transcript", transcript.contains("astăzi", ignoreCase = true))
            }
            KokoroArchive.install(context, Uri.fromFile(fixture(ModelCatalog.kokoro.file)))
            assertTrue(ModelInventory.installed(context, ModelCatalog.kokoro))
            play(checkNotNull(NeuralVoiceFactory.create(context)), "Hello. This is Jarvis.")
        } finally {
            context.deleteSharedPreferences(root.name + "-piper_voice")
            root.deleteRecursively()
        }
    }

    private fun makeSpeechAt16k(dir: File): FloatArray {
        val model = OfflineTtsVitsModelConfig(model = File(dir, "ro_RO-mihai-medium.onnx").path,
            tokens = File(dir, "tokens.txt").path, dataDir = File(dir, "espeak-ng-data").path)
        val engine = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = model, numThreads = 2)))
        val audio = try { engine.generate("Bună! Sunt Jarvis. Cu ce te pot ajuta astăzi?") } finally { engine.release() }
        val ratio = audio.sampleRate / 16000.0
        return FloatArray((audio.samples.size / ratio).toInt()) { index ->
            val pos = index * ratio
            val left = pos.toInt()
            val fraction = (pos - left).toFloat()
            val right = minOf(left + 1, audio.samples.lastIndex)
            audio.samples[left] * (1 - fraction) + audio.samples[right] * fraction
        }
    }

    private fun play(voice: NeuralVoice, phrase: String) {
        assertTrue(voice.installed())
        val finished = CountDownLatch(1)
        val error = AtomicReference<String?>()
        try {
            voice.speakTracked(phrase) { message ->
                error.set(message)
                finished.countDown()
            }
            assertTrue("Playback timed out", finished.await(90, TimeUnit.SECONDS))
            assertNull(error.get())
        } finally {
            voice.stop()
        }
    }
}
