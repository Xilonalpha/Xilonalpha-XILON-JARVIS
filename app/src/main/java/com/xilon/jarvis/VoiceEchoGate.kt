package com.xilon.jarvis
/** Speech echo is valid only around the current TTS turn, never indefinitely. */
internal class VoiceEchoGate(private val echoWindowMs:Long=15000L,private val clock:()->Long={System.nanoTime()/1_000_000L}) {
 private data class Spoken(val value:String,val at:Long)
 private val recentChunks=ArrayDeque<Spoken>()
 private val maxChunks=12
 private fun normalize(s:String)=java.text.Normalizer.normalize(s.lowercase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").replace(Regex("[^a-z0-9 ]")," ").replace(Regex("\\s+")," ").trim()
 @Synchronized fun remember(text:String){
  val normalized=normalize(text)
  if(normalized.isBlank())return
  val now=clock()
  prune(now)
  recentChunks.addLast(Spoken(normalized,now))
  while(recentChunks.size>maxChunks)recentChunks.removeFirst()
 }
 @Synchronized fun clear(){recentChunks.clear()}
 private fun prune(now:Long){
  while(recentChunks.isNotEmpty() && (now<recentChunks.first().at || now-recentChunks.first().at>echoWindowMs))
   recentChunks.removeFirst()
 }
 @Synchronized private fun recent():List<String>{
  prune(clock())
  return recentChunks.map{it.value}
 }
 fun isEcho(text:String):Boolean {
  val command=normalize(text)
  if(command.isBlank())return false
  val chunks=recent()
  val combined=chunks.joinToString(" ")
  return chunks.any{it==command || (command.length>=16 && it.contains(command))} ||
   (command.length>=16 && combined.contains(command))
 }
 fun isStop(text:String):Boolean {
  val command=normalize(text)
  val recent=recent()
  return command in setOf("jarvis opreste","jarvis stop","jarvis taci","jarvis opreste te","jarvis nu mai vorbi","jarvis intrerupe raspunsul","jarvis gata","jarvis termina","jarvis liniste","jarvis pauza","jarvis opreste conversatia","jarvis intrerupe","jarvis nu mai raspunde") && recent.none{it.contains(command)} && !recent.joinToString(" ").contains(command)
 }
}
