package com.xilon.jarvis

/** Suppress repeated ASR finals from a single utterance; no transcript is persisted. */
internal class VoiceTurnGuard(private val duplicateWindowMs:Long=1800L) {
 private var lastText=""
 private var lastAt=Long.MIN_VALUE
 @Synchronized fun accept(text:String,nowMs:Long):Boolean {
  val normalized=java.text.Normalizer.normalize(text.lowercase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD)
   .replace(Regex("\\p{M}+"),"").replace(Regex("[^a-z0-9]+")," ").trim()
  if(normalized.isBlank())return false
  if(normalized==lastText && lastAt!=Long.MIN_VALUE && nowMs>=lastAt && nowMs-lastAt<duplicateWindowMs)return false
  lastText=normalized
  lastAt=nowMs
  return true
 }
 @Synchronized fun reset(){lastText="";lastAt=Long.MIN_VALUE}
}
