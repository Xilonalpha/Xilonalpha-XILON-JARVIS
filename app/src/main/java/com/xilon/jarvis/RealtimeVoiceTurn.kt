package com.xilon.jarvis

/**
 * Coordinates text-to-speech chunks for a single conversational turn.
 * No audio I/O is performed here. The Android host owns the TTS player,
 * microphone and LLM cancellation. Generation IDs reject late callbacks.
 */
internal class RealtimeVoiceTurn(
 private val minChunkChars:Int=24,
 private val maxChunkChars:Int=220,
 private val maxQueuedChunks:Int=12
) {
 init { require(maxQueuedChunks>0) }
 private var generation=0L
 private var active=false
 private val speech=RealtimeSpeechBuffer(minChunkChars,maxChunkChars)
 private val queue=ArrayDeque<String>()
 private var previousSnapshot=""
 /**
  * Native onProgress currently emits cumulative text, not token deltas.
  * Convert snapshots to deltas without replaying already spoken words.
  * A rewritten/non-prefix snapshot invalidates the turn rather than
  * speaking contradictory content.
  */
 @Synchronized fun offerSnapshot(id:Long,snapshot:String):Boolean {
  if(!active || id!=generation)return false
  if(snapshot==previousSnapshot)return true
  if(!snapshot.startsWith(previousSnapshot)){
   interrupt()
   return false
  }
  val delta=snapshot.substring(previousSnapshot.length)
  if(!offer(id,delta))return false
  previousSnapshot=snapshot
  return true
 }

 @Synchronized fun start():Long {
  generation++
  active=true
  queue.clear()
  previousSnapshot=""
  speech.reset()
  return generation
 }
 /** Returns false when the producer must pause to avoid unbounded memory. */
 @Synchronized fun offer(id:Long,delta:String):Boolean {
  if(!active || id!=generation)return false
  // Reject oversized producer batches before modifying buffered speech.
  // Streaming callers should supply small deltas and retry only when false.
  if(delta.length>maxChunkChars*maxQueuedChunks)return false
  // Refuse new tokens before consuming them when the queue is saturated.
  if(queue.size>=maxQueuedChunks)return false
  val chunks=speech.append(delta)
  if(queue.size+chunks.size>maxQueuedChunks){
   // The producer should send small deltas. A rejected oversized delta
   // cannot be replayed safely after it was appended; cancel this turn.
   interrupt()
   return false
  }
  queue.addAll(chunks)
  return true
 }
 @Synchronized fun finish(id:Long):Boolean {
  if(!active || id!=generation)return false
  val chunks=speech.finish()
  if(queue.size+chunks.size>maxQueuedChunks){interrupt();return false}
  queue.addAll(chunks)
  return true
 }
 @Synchronized fun poll(id:Long):String? =
  if(active && id==generation && queue.isNotEmpty())queue.removeFirst() else null
 @Synchronized fun interrupt():Long {
  generation++
  active=false
  queue.clear()
  previousSnapshot=""
  speech.cancel()
  return generation
 }
 @Synchronized fun isCurrent(id:Long):Boolean=active && id==generation
 @Synchronized fun queued():Int=queue.size
}
