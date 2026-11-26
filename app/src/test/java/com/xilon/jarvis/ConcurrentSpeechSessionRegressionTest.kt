package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class ConcurrentSpeechSessionRegressionTest {
 @Test fun cancelledDuplexCannotStartAnotherCaptureUntilWorkerExits(){
  val sessions=VoiceSessionState()
  val gate=VoiceCaptureGate()
  val first=sessions.begin()
  assertTrue(gate.begin(first))
  sessions.cancel();gate.cancel()
  assertFalse(sessions.isCurrent(first))
  assertFalse(gate.begin(sessions.begin()))
  assertTrue(gate.finish(first))
  val next=sessions.begin()
  assertTrue(gate.begin(next))
  assertTrue(sessions.isCurrent(next))
 }
 @Test fun interruptedBargeInDoesNotReleaseNewerTicket(){
  val gate=VoiceBargeInState()
  val first=gate.beginTicket()!!
  assertTrue(gate.finish(first))
  val second=gate.beginTicket()!!
  assertFalse(gate.finish(first))
  assertTrue(gate.isCurrent(second))
 }
}
