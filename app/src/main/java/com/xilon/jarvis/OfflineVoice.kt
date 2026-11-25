package com.xilon.jarvis
import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class OfflineVoice(context: Context) : AutoCloseable {
 var onSpeaking:(Boolean)->Unit={}
 private val main=android.os.Handler(android.os.Looper.getMainLooper())
 private var generation=0L
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
  val id=++generation
  tts.setOnUtteranceProgressListener(object:android.speech.tts.UtteranceProgressListener(){
   private fun publish(value:Boolean){main.post{if(!closed && generation==id)onSpeaking(value)}}
   override fun onStart(utteranceId:String?){if(utteranceId?.startsWith("xilon-$id-")==true)publish(true)}
   override fun onDone(utteranceId:String?){if(utteranceId=="xilon-$id-${chunks.lastIndex}")publish(false)}
   override fun onError(utteranceId:String?){if(utteranceId?.startsWith("xilon-$id-")==true)publish(false)}
  })
  onSpeaking(true)
  for((i,chunk) in chunks.withIndex()){
   if(tts.speak(chunk,if(i==0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,null,"xilon-$id-$i")!=TextToSpeech.SUCCESS){stop();return "Sinteza vocală a eșuat."}
  }
  return null
 }
 fun stop(){ generation++;if(!closed)tts.stop();onSpeaking(false) }
 override fun close(){ if(!closed){ stop();closed=true; ready=false; tts.shutdown() } }
}
