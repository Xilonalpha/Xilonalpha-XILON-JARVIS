package com.xilon.jarvis

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
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
            KokoroArchive.install(context, Uri.fromFile(fixture(ModelCatalog.kokoro.file)))
            assertTrue(ModelInventory.installed(context, ModelCatalog.kokoro))
            play(checkNotNull(NeuralVoiceFactory.create(context)), "Hello. This is Jarvis.")
        } finally {
            context.deleteSharedPreferences(root.name + "-piper_voice")
            root.deleteRecursively()
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
