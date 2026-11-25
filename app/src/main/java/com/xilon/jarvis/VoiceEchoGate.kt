package com.xilon.jarvis
/** Speech echo is valid only around the current TTS turn, never indefinitely. */
internal class VoiceEchoGate(private val echoWindowMs:Long=15000L,private val clock:()->Long={System.nanoTime()/1_000_000L}) {
 @Volatile private var spoken=""
 @Volatile private var spokenAt=Long.MIN_VALUE
 private fun normalize(s:String)=java.text.Normalizer.normalize(s.lowercase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").replace(Regex("[^a-z0-9 ]")," ").replace(Regex("\\s+")," ").trim()
 fun remember(text:String){spoken=normalize(text);spokenAt=clock()}
 fun clear(){spoken="";spokenAt=Long.MIN_VALUE}
 private fun recent():String {
  val at=spokenAt
  val now=clock()
  return if(at!=Long.MIN_VALUE && now>=at && now-at<=echoWindowMs)spoken else ""
 }
 fun isEcho(text:String):Boolean {
  val command=normalize(text)
  val recent=recent()
  return command.isNotBlank() && recent.isNotBlank() && (recent==command || (command.length>=16 && recent.contains(command)))
 }
 fun isStop(text:String):Boolean {
  val command=normalize(text)
  val recent=recent()
  return command in setOf("jarvis opreste","jarvis stop","jarvis taci","jarvis opreste te","jarvis nu mai vorbi","jarvis intrerupe raspunsul","jarvis gata","jarvis termina","jarvis liniste","jarvis pauza","jarvis opreste conversatia","jarvis intrerupe","jarvis nu mai raspunde") && !recent.contains(command)
 }
}
