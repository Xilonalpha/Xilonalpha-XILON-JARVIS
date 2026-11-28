package com.xilon.jarvis
import android.content.Context
import com.jarvis.assistant.voice.Kokoro
class KokoroVoice(private val context:Context):NeuralVoice {
 private fun speechSpeed(text:String):Float {
  val base=context.getSharedPreferences("voice_prosody",Context.MODE_PRIVATE).getFloat("speed",1f).let{if(it.isFinite())it.coerceIn(0.8f,1.2f) else 1f}
  val end=text.trimEnd().trimEnd('"', '\u201d', '\u00bb', ')')
  return (base * when {end.endsWith("?")->0.96f;end.endsWith("!")->1.03f;end.endsWith("…") || end.endsWith("...")->0.92f;else->1f}).coerceIn(0.8f,1.2f)
 }
 fun validateModelLoad()=Kokoro.validateModelLoad(context)
 override fun installed()=Kokoro.isInstalled(context) && !Kokoro.failed
 override fun speak(text:String){check(installed()){"Kokoro indisponibil"};Kokoro.speak(context,text,0,speechSpeed(text)){}}
 override fun speakTracked(text:String,done:(String?)->Unit){
  check(installed()){"Kokoro indisponibil"}
  Kokoro.speak(context,text,0,speechSpeed(text)){done(Kokoro.lastError)}
 }
 override fun prepare(text:String){if(installed())Kokoro.prepare(context,text,0,speechSpeed(text))}
 override fun stop(){Kokoro.stop()}
}
