package com.xilon.jarvis
import android.content.Context
interface NeuralSpeech : java.io.Closeable {
 suspend fun listen():String
 /** Interim hypotheses are UI hints only; never dispatch them as commands. */
 suspend fun listen(onPartial:(String)->Unit):String=listen()
 suspend fun listenAfterDetection(prefix:ShortArray):String=listen()
 fun supportsConcurrentPlayback():Boolean=false
 fun stop()
}
object NeuralSpeechFactory {
 fun create(context:Context):NeuralSpeech? {
  return try {
   Class.forName("com.xilon.jarvis.SherpaSpeech").getConstructor(Context::class.java).newInstance(context) as NeuralSpeech
  } catch(e:ReflectiveOperationException){
   android.util.Log.w("NeuralSpeech", "Offline ASR adapter initialization failed", e)
   null
  } catch(e:LinkageError){
   android.util.Log.w("NeuralSpeech", "Offline ASR native runtime unavailable", e)
   null
  }
 }
}
