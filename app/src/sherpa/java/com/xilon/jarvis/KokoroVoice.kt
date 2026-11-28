package com.xilon.jarvis
import android.content.Context
import com.jarvis.assistant.voice.Kokoro
class KokoroVoice(private val context:Context):NeuralVoice {
 fun validateModelLoad()=Kokoro.validateModelLoad(context)
 override fun installed()=Kokoro.isInstalled(context) && !Kokoro.failed
 override fun speak(text:String){check(installed()){"Kokoro indisponibil"};Kokoro.speak(context,text,0,1f){}}
 override fun speakTracked(text:String,done:(String?)->Unit){
  check(installed()){"Kokoro indisponibil"}
  Kokoro.speak(context,text,0,1f){done(Kokoro.lastError)}
 }
 override fun prepare(text:String){if(installed())Kokoro.prepare(context,text,0,1f)}
 override fun stop(){Kokoro.stop()}
}
