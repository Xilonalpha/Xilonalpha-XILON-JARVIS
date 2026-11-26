package com.xilon.jarvis
/** Limits interrupt-recognizer restarts while TTS is active; uses monotonic time. */
internal class VoiceInterruptGate(private val intervalMs:Long=2500L){
 @Volatile private var last=Long.MIN_VALUE
 @Synchronized fun ready(now:Long):Boolean {
  if(last!=Long.MIN_VALUE && now>=last && now-last<intervalMs)return false
  last=now
  return true
 }
 @Synchronized fun reset(){last=Long.MIN_VALUE}
}
