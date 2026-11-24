package com.xilon.jarvis
import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class OfflineVoice(context: Context) : AutoCloseable {
 private var ready=false
 private var closed=false
 private val tts=TextToSpeech(context.applicationContext) { status -> ready=!closed && status==TextToSpeech.SUCCESS }
 fun speak(text:String):String? {
  if(!ready || closed) return "Motorul vocal nu este disponibil."
  val voice=tts.voices?.filter { !it.isNetworkConnectionRequired && it.locale.language=="ro" && !it.features.orEmpty().contains("notInstalled") }?.maxByOrNull { it.quality }
   ?: return "Instalează o voce română offline în configurarea Android."
  if(tts.setVoice(voice)!=TextToSpeech.SUCCESS) return "Vocea nu poate fi activată."
  return if(tts.speak(text.take(TextToSpeech.getMaxSpeechInputLength()),TextToSpeech.QUEUE_FLUSH,null,"xilon-reply")==TextToSpeech.SUCCESS) null else "Sinteza vocală a eșuat."
 }
 fun stop(){ if(!closed) tts.stop() }
 override fun close(){ if(!closed){ closed=true; ready=false; tts.stop(); tts.shutdown() } }
}
