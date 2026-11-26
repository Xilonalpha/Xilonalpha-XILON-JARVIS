package com.xilon.jarvis
/**
 * Tracks the exclusive VAD -> ASR interruption handoff.
 * Tickets prevent late callbacks from cancelling a newer interruption.
 */
internal class VoiceBargeInState {
 private var sequence=0L
 private var ticket:Long?=null
 @Synchronized fun beginTicket():Long? {
  if(ticket!=null)return null
  sequence++
  ticket=sequence
  return sequence
 }
 @Synchronized fun begin():Boolean=beginTicket()!=null
 @Synchronized fun active():Boolean=ticket!=null
 @Synchronized fun isCurrent(id:Long):Boolean=ticket==id
 @Synchronized fun finish(id:Long):Boolean {
  if(ticket!=id)return false
  ticket=null
  return true
 }
 @Synchronized fun finish(){ticket=null}
}
