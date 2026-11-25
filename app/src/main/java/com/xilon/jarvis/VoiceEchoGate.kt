package com.xilon.jarvis
internal class VoiceEchoGate {
 @Volatile private var spoken=""
 private fun normalize(s:String)=java.text.Normalizer.normalize(s.lowercase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").replace(Regex("[^a-z0-9 ]")," ").replace(Regex("\\s+")," ").trim()
 fun remember(text:String){spoken=normalize(text)}
 fun isStop(text:String):Boolean {
  val command=normalize(text)
  return command in setOf("jarvis opreste","jarvis stop","jarvis taci","jarvis opreste te","jarvis nu mai vorbi","jarvis intrerupe raspunsul","jarvis gata") && !spoken.contains(command)
 }
}
