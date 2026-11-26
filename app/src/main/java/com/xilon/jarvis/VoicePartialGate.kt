package com.xilon.jarvis

/**
 * Limits ASR partial UI updates without delaying the final transcript.
 * All timestamps are elapsed realtime; no partial text is stored beyond this session.
 */
internal class VoicePartialGate(private val intervalMs:Long=180L) {
 private var previous=""
 private var lastAt=Long.MIN_VALUE
 @Synchronized fun shouldPublish(text:String,nowMs:Long):Boolean {
  val value=text.trim()
  if(value.isEmpty() || value==previous)return false
  if(lastAt!=Long.MIN_VALUE && nowMs>=lastAt && nowMs-lastAt<intervalMs)return false
  // A clock reset must not permanently suppress all future partials.
  previous=value
  lastAt=nowMs
  return true
 }
 @Synchronized fun reset(){previous="";lastAt=Long.MIN_VALUE}
}
