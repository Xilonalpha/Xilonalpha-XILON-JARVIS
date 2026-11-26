package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
/** Pure policy tests: Android hardware AEC remains device-dependent. */
class VoiceAudioSourceContractTest {
 @Test fun interruptionSessionRemainsExclusive(){
  val state=VoiceBargeInState()
  val ticket=state.beginTicket()!!
  assertNull(state.beginTicket())
  assertTrue(state.isCurrent(ticket))
  assertTrue(state.finish(ticket))
  assertNotNull(state.beginTicket())
 }
 @Test fun cancelledSherpaLeaseMustFinishBeforeNextStart(){
  val gate=VoiceCaptureGate()
  assertTrue(gate.begin(7))
  gate.cancel()
  assertFalse(gate.begin(8))
  assertTrue(gate.finish(7))
  assertTrue(gate.begin(8))
 }
}
