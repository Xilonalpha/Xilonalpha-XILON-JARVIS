package com.xilon.jarvis
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import java.util.Locale

class MediaActions(private val activity:Context){
 fun execute(command:String):String? {
  val raw=command.trim()
  val q=raw.lowercase(Locale.ROOT)
  val audio=activity.getSystemService(AudioManager::class.java)
  val parsed=if(q.contains(Regex("\\b(and|then|aur|not|never|don.t)\\b")))null else com.jarvis.assistant.router.FastCommandRouter.parse(raw)
  val media=(parsed as? com.jarvis.assistant.router.FastCommand.Media)?.action
  val key=when(q){
   "play","redă","reda","pornește muzica","porneste muzica" -> KeyEvent.KEYCODE_MEDIA_PLAY
   "pause","pauză","pauza","oprește muzica","opreste muzica" -> KeyEvent.KEYCODE_MEDIA_PAUSE
   "următoarea melodie" -> KeyEvent.KEYCODE_MEDIA_NEXT
   "melodia anterioară" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
   else -> when(media){
    com.jarvis.assistant.router.MediaAction.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
    com.jarvis.assistant.router.MediaAction.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
    com.jarvis.assistant.router.MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
    com.jarvis.assistant.router.MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
    null -> null
   }
  }
  if(key!=null){
   return runCatching {
    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,key))
    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,key))
    "Comandă multimedia transmisă; redarea nu este verificată."
   }.getOrElse{"Comanda multimedia a eșuat: "+it.javaClass.simpleName}
  }
  val stream=AudioManager.STREAM_MUSIC
  if(q in setOf("crește volumul","scade volumul")){
   return runCatching{
    if(audio.isVolumeFixed)return "Volumul este fix pe acest dispozitiv."
    audio.adjustStreamVolume(stream,if(q=="crește volumul")AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI)
    "Volum multimedia: "+audio.getStreamVolume(stream)+"/"+audio.getStreamMaxVolume(stream)
   }.getOrElse{"Ajustare indisponibilă: "+it.javaClass.simpleName}
  }
  if(q=="volum procent"){
   val maximum=audio.getStreamMaxVolume(stream)
   return if(maximum>0)"Volum multimedia: "+(audio.getStreamVolume(stream)*100/maximum)+"%" else "Volum indisponibil."
  }
  if(q=="volum" || q.startsWith("volum ")){
   val max=audio.getStreamMaxVolume(stream)
   if(q!="volum"){
    val percent=q.substringAfter(" ").removeSuffix("%").toIntOrNull() ?: return "Format: volum 0–100"
    if(percent !in 0..100)return "Volum invalid."
    if(audio.isVolumeFixed)return "Volumul este fix pe acest dispozitiv."
    audio.setStreamVolume(stream,((max*percent+50)/100).coerceIn(0,max),AudioManager.FLAG_SHOW_UI)
   }
   return "Volum multimedia: "+audio.getStreamVolume(stream)+"/"+max
  }
  if(q.startsWith("caută youtube ")){
   val query=raw.substringAfter(" ").substringAfter(" ").trim()
   if(query.isBlank())return "Specifică melodia."
   val uri=Uri.parse("https://www.youtube.com/results").buildUpon().appendQueryParameter("search_query",query).build()
   return runCatching{activity.startActivity(Intent(Intent.ACTION_VIEW,uri).apply{if(activity !is Activity)addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)});"Căutare YouTube deschisă; redarea automată nu este confirmată."}.getOrElse{"YouTube indisponibil: "+it.javaClass.simpleName}
  }
  return null
 }
}
