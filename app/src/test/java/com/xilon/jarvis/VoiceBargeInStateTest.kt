package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceBargeInStateTest {
 @Test fun onlyOneInterruptAtATime(){
  val s=VoiceBargeInState()
  assertTrue(s.begin());assertTrue(s.active())
  assertFalse(s.begin())
  s.finish();assertFalse(s.active());assertTrue(s.begin())
 }
 @Test fun repeatedFinishIsSafe(){
  val s=VoiceBargeInState()
  s.finish();assertTrue(s.begin());s.finish();s.finish()
  assertTrue(s.begin())
 }
}
