package com.xilon.jarvis
/**
 * Prevents overlapping microphone capture sessions, including stale callbacks
 * from a cancelled Sherpa recognizer. No audio or transcript is stored.
 */
internal class VoiceCaptureGate {
 private var ticket:Long?=null
 private var closed=false
 @Synchronized fun begin(id:Long):Boolean {
  if(closed || ticket!=null)return false
  ticket=id
  return true
 }
 @Synchronized fun active():Boolean=ticket!=null
 @Synchronized fun currentTicket():Long?=ticket
 @Synchronized fun available():Boolean=!closed && ticket==null
 @Synchronized fun finish(id:Long):Boolean {
  if(ticket!=id)return false
  ticket=null
  return true
 }
 @Synchronized fun cancel(){/* Keep microphone leased until the capture worker finishes. */}
 @Synchronized fun close(){closed=true;/* Worker still owns any active capture. */}
}
