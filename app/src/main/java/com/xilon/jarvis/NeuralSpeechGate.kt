package com.xilon.jarvis

/**
 * Requires two adjacent positive 32 ms Silero frames before a barge-in.
 * Rejects malformed model outputs rather than triggering a false interruption.
 */
internal class NeuralSpeechGate(
 private val threshold:Float=0.65f,
 private val consecutiveFrames:Int=2
){
 private var consecutive=0
 init {
  require(threshold in 0f..1f && consecutiveFrames>0)
 }
 fun accept(probability:Float):Boolean {
  if(!probability.isFinite() || probability !in 0f..1f){
   consecutive=0
   return false
  }
  consecutive=if(probability>=threshold) minOf(consecutiveFrames,consecutive+1) else 0
  return consecutive>=consecutiveFrames
 }
 fun reset(){consecutive=0}
}
