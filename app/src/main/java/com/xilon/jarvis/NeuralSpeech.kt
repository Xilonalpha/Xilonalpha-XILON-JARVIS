package com.xilon.jarvis
import android.content.Context
interface NeuralSpeech : java.io.Closeable {
 suspend fun listen():String
 /** Interim hypotheses are UI hints only; never dispatch them as commands. */
 suspend fun listen(onPartial:(String)->Unit):String=listen()
 fun stop()
}
object NeuralSpeechFactory {
 fun create(context:Context):NeuralSpeech? {
  return try {
   Class.forName("com.xilon.jarvis.SherpaSpeech").getConstructor(Context::class.java).newInstance(context) as NeuralSpeech
  } catch(e:ClassNotFoundException){null}
 }
}
