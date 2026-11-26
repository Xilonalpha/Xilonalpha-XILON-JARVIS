package com.xilon.jarvis
/**
 * Exclusive interrupt-recognition turn. Prevents duplicate VAD triggers and
 * overlapping speech recognizers while synthesized audio is playing.
 */
internal class VoiceBargeInState {
 private var inProgress=false
 @Synchronized fun begin():Boolean {
  if(inProgress)return false
  inProgress=true
  return true
 }
 @Synchronized fun active():Boolean=inProgress
 @Synchronized fun finish(){inProgress=false}
}
