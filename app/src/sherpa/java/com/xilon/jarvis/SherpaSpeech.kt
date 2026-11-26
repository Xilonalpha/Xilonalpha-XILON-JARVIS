package com.xilon.jarvis
import android.content.Context
import com.jarvis.ai.stt.SpeechToTextEngine
class SherpaSpeech(context:Context):NeuralSpeech {
 private val engine=SpeechToTextEngine(context)
 fun validateModelLoad()=engine.validateModelLoad()
 override fun supportsConcurrentPlayback()=engine.hasStreamingModelFiles()
 override suspend fun listen()=engine.listenForCommand()
 override suspend fun listen(onPartial:(String)->Unit)=engine.listenForCommand(onPartial)
 override fun stop()=engine.stopCapture()
 override fun close()=engine.close()
}
