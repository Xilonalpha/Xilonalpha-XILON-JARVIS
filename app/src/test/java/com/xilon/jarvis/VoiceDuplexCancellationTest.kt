package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceDuplexCancellationTest {
 @Test fun cancellationInvalidatesRecognitionBeforeRecorderReleases(){
  val sessions=VoiceSessionState()
  val capture=VoiceCaptureGate()
  val id=sessions.begin()
  assertTrue(capture.begin(id))
  sessions.cancel()
  capture.cancel()
  assertFalse(sessions.isCurrent(id))
  assertTrue(capture.active())
  assertFalse(capture.begin(sessions.begin()))
  assertTrue(capture.finish(id))
  assertFalse(capture.active())
 }
 @Test fun stopThenRestartNeverAcceptsStaleTicket(){
  val state=VoiceBargeInState()
  val stale=state.beginTicket()!!
  state.finish()
  val current=state.beginTicket()!!
  assertFalse(state.isCurrent(stale))
  assertFalse(state.finish(stale))
  assertTrue(state.finish(current))
 }
}
