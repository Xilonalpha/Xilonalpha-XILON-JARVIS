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
  val chunks=com.jarvis.assistant.voice.SpeechChunks.split(text)
  if(chunks.isEmpty())return null
  for((i,chunk) in chunks.withIndex()){
   if(tts.speak(chunk,if(i==0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,null,"xilon-reply-$i")!=TextToSpeech.SUCCESS){tts.stop();return "Sinteza vocală a eșuat."}
  }
  return null
 }
 fun stop(){ if(!closed) tts.stop() }
 override fun close(){ if(!closed){ closed=true; ready=false; tts.stop(); tts.shutdown() } }
}
