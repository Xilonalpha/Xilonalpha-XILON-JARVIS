package com.xilon.jarvis
import android.content.Context
interface NeuralSpeech : java.io.Closeable {
 suspend fun listen():String
 fun stop()
}
object NeuralSpeechFactory {
 fun create(context:Context):NeuralSpeech? {
  return try {
   Class.forName("com.xilon.jarvis.SherpaSpeech").getConstructor(Context::class.java).newInstance(context) as NeuralSpeech
  } catch(e:ClassNotFoundException){null}
 }
}
