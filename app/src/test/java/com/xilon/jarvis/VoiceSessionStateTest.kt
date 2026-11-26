package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceSessionStateTest {
 @Test fun supersededTurnsAreRejected(){
  val state=VoiceSessionState();val first=state.begin()
  assertTrue(state.isCurrent(first));val second=state.begin()
  assertFalse(state.isCurrent(first));assertTrue(state.isCurrent(second))
 }
 @Test fun doubleCloseInvalidatesSession(){
  val state=VoiceSessionState();val ticket=state.begin()
  state.close();state.close();assertFalse(state.isCurrent(ticket))
 }
 @Test fun cancellationAndShutdownInvalidateCallbacks(){
  val state=VoiceSessionState();val first=state.begin()
  state.cancel();assertFalse(state.isCurrent(first))
  val second=state.begin();state.close();assertFalse(state.isCurrent(second))
  try { state.begin();fail("Closed session must reject new turns") } catch (_:IllegalStateException) {}
 }
}
