package com.xilon.jarvis
/** Speech echo is valid only around the current TTS turn, never indefinitely. */
internal class VoiceEchoGate(private val echoWindowMs:Long=15000L,private val clock:()->Long={System.nanoTime()/1_000_000L}) {
 private data class Spoken(val value:String,val at:Long)
 private val recentChunks=ArrayDeque<Spoken>()
 private val maxChunks=12
 private val maxEchoChars=300
 private val stopCommands=setOf("jarvis opreste","jarvis stop","jarvis taci","jarvis opreste te","jarvis nu mai vorbi","jarvis intrerupe raspunsul","jarvis gata","jarvis termina","jarvis liniste","jarvis pauza","jarvis opreste conversatia","jarvis intrerupe","jarvis nu mai raspunde")
 private fun normalize(s:String)=VoiceTextNormalizer.normalize(s)
 @Synchronized fun remember(text:String){
  val normalized=normalize(text)
  if(normalized.isBlank())return
  val now=clock()
  prune(now)
  val words=normalized.split(' ')
  var part=""
  for(word in words){
   if(part.isNotEmpty() && part.length+word.length+1>maxEchoChars){recentChunks.addLast(Spoken(part,now));part=""}
   part=if(part.isEmpty())word.take(maxEchoChars) else part+" "+word.take(maxEchoChars)
  }
  if(part.isNotEmpty())recentChunks.addLast(Spoken(part,now))
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
 // Conservative token alignment: tolerate one ASR substitution in a long
 // contiguous phrase, never suppress short commands or explicit stop requests.
 private val polarity=setOf("nu","nici","fara","da","opreste","porneste","inchide","deschide")
 private fun nearEcho(command:String,spoken:String):Boolean {
  val words=command.split(' ').filter{it.isNotBlank()}
  if(words.size<7 || command in stopCommands)return false
  val source=spoken.split(' ').filter{it.isNotBlank()}
  if(source.size<words.size)return false
  return (0..source.size-words.size).any{start->
   words.indices.count{i->words[i]!=source[start+i]}<=1 &&
    words.indices.none{i->words[i]!=source[start+i] && (words[i] in polarity || source[start+i] in polarity)} &&
    words.first()==source[start] && words.last()==source[start+words.lastIndex]
  }
 }
 fun isEcho(text:String):Boolean {
  val command=normalize(text)
  if(command.isBlank())return false
  val chunks=recent()
  val combined=chunks.joinToString(" ")
  // A long ASR fragment may span TTS chunks; short generic words are not echoes.
  return chunks.any{it==command || (command.length>=16 && VoiceTextNormalizer.containsPhrase(it,command))} ||
   (command.length>=16 && VoiceTextNormalizer.containsPhrase(combined,command)) || nearEcho(command,combined)
 }
 fun isStop(text:String):Boolean {
  val command=normalize(text)
  val recent=recent()
  // Stop requests are accepted only if they are not part of our own recent TTS.
  return command in stopCommands && recent.none{VoiceTextNormalizer.containsPhrase(it,command)} && !VoiceTextNormalizer.containsPhrase(recent.joinToString(" "),command)
 }
}
