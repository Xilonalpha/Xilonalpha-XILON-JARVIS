package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceBargeInStateTest {
 @Test fun onlyOneInterruptAtATime(){
  val s=VoiceBargeInState()
  val first=s.beginTicket()!!
  assertTrue(s.active());assertNull(s.beginTicket())
  assertTrue(s.finish(first));assertFalse(s.active())
  val second=s.beginTicket()!!
  assertTrue(second>first)
 }
 @Test fun staleCallbacksCannotFinishNewerInterrupt(){
  val s=VoiceBargeInState()
  val old=s.beginTicket()!!
  assertTrue(s.finish(old))
  val current=s.beginTicket()!!
  assertFalse(s.finish(old))
  assertTrue(s.isCurrent(current));assertTrue(s.active())
  assertTrue(s.finish(current))
 }
 @Test fun repeatedFinishIsSafe(){
  val s=VoiceBargeInState()
  s.finish();assertTrue(s.begin());s.finish();s.finish()
  assertTrue(s.begin())
 }
}
