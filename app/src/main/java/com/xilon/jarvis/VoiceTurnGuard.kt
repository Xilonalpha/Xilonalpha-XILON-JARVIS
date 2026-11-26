package com.xilon.jarvis

/** Suppress repeated ASR finals from a single utterance; no transcript is persisted. */
internal class VoiceTurnGuard(private val duplicateWindowMs:Long=1800L) {
 private var lastText=""
 private var lastAt=Long.MIN_VALUE
 @Synchronized fun accept(text:String,nowMs:Long):Boolean {
  val normalized=VoiceTextNormalizer.normalize(text)
  if(normalized.isBlank())return false
  if(normalized==lastText && lastAt!=Long.MIN_VALUE && nowMs>=lastAt && nowMs-lastAt<duplicateWindowMs)return false
  lastText=normalized
  lastAt=nowMs
  return true
 }
 @Synchronized fun reset(){lastText="";lastAt=Long.MIN_VALUE}
}
