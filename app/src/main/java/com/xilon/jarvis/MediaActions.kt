package com.xilon.jarvis
import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import java.util.Locale

class MediaActions(private val activity:Activity){
 fun execute(command:String):String? {
  val raw=command.trim()
  val q=raw.lowercase(Locale.ROOT)
  val audio=activity.getSystemService(AudioManager::class.java)
  val key=when(q){
   "play","redă" -> KeyEvent.KEYCODE_MEDIA_PLAY
   "pause","pauză" -> KeyEvent.KEYCODE_MEDIA_PAUSE
   "următoarea melodie" -> KeyEvent.KEYCODE_MEDIA_NEXT
   "melodia anterioară" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
   else -> null
  }
  if(key!=null){
   audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,key))
   audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,key))
   return "Comandă multimedia transmisă; redarea nu este verificată."
  }
  val stream=AudioManager.STREAM_MUSIC
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
   activity.startActivity(Intent(Intent.ACTION_VIEW,uri))
   return "Căutare YouTube deschisă; redarea automată nu este confirmată."
  }
  return null
 }
}
