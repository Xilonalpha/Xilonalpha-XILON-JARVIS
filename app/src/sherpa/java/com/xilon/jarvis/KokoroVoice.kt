package com.xilon.jarvis
import android.content.Context
import com.jarvis.assistant.voice.Kokoro
class KokoroVoice(private val context:Context):NeuralVoice {
 override fun installed()=Kokoro.isInstalled(context) && !Kokoro.failed
 override fun speak(text:String){check(installed()){"Kokoro indisponibil"};Kokoro.speak(context,text.take(1000),0,1f){}}
 override fun stop(){Kokoro.stop()}
}
