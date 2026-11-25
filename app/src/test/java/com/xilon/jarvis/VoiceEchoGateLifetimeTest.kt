package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceEchoGateLifetimeTest {
 @Test fun echoExpiresAndStopIsNotBlockedByOldSpeech(){
  var now=100L
  val gate=VoiceEchoGate(1000L){now}
  gate.remember("Jarvis opreste")
  assertTrue(gate.isEcho("jarvis opreste"))
  assertFalse(gate.isStop("jarvis opreste"))
  now=1200L
  assertFalse(gate.isEcho("jarvis opreste"))
  assertTrue(gate.isStop("jarvis opreste"))
 }
 @Test fun clearDropsEchoImmediately(){
  val gate=VoiceEchoGate(1000L){100L}
  gate.remember("Buna ziua")
  gate.clear()
  assertFalse(gate.isEcho("Buna ziua"))
 }
}
