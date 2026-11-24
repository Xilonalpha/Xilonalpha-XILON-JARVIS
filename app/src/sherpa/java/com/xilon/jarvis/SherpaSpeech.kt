package com.xilon.jarvis
import android.content.Context
import com.jarvis.ai.stt.SpeechToTextEngine
class SherpaSpeech(context:Context):NeuralSpeech {
 private val engine=SpeechToTextEngine(context)
 override suspend fun listen()=engine.listenForCommand()
 override fun stop()=engine.stopCapture()
 override fun close()=engine.close()
}
