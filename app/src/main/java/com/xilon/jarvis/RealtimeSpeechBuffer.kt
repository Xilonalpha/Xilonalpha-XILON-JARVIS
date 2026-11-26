package com.xilon.jarvis

/**
 * Bounded sentence-level streaming adapter for local LLM token output.
 * No network calls, no Android dependencies, and no audio playback side effects.
 * A caller owns TTS dispatch and must discard chunks after cancellation.
 */
internal class RealtimeSpeechBuffer(
 private val minChunkChars:Int=24,
 private val maxChunkChars:Int=220
) {
 init { require(minChunkChars in 1..maxChunkChars && maxChunkChars<=1000) }
 private val pending=StringBuilder()
 private var cancelled=false
 @Synchronized fun append(delta:String):List<String> {
  if(cancelled || delta.isEmpty())return emptyList()
  val ready=ArrayList<String>()
  for(ch in delta){
   if(pending.length>=maxChunkChars && (ch.isWhitespace() || ch in ".!?;,")){
    emit(ready)
   }
   pending.append(ch)
   if(ch in ".!?" && pending.length>=minChunkChars)emit(ready)
   else if(pending.length>=maxChunkChars && ch.isWhitespace())emit(ready)
  }
  return ready
 }
 private fun emit(out:MutableList<String>) {
  val chunk=pending.toString().trim()
  if(chunk.isNotEmpty())out.add(chunk)
  pending.setLength(0)
 }
 @Synchronized fun finish():List<String>{
  if(cancelled)return emptyList()
  val out=ArrayList<String>()
  emit(out)
  return out
 }
 @Synchronized fun cancel(){cancelled=true;pending.setLength(0)}
 @Synchronized fun reset(){cancelled=false;pending.setLength(0)}
}
