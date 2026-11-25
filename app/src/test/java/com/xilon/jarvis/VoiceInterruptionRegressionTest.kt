package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class VoiceInterruptionRegressionTest {
 @Test fun acceptsRomanianInterruptions() {
  val gate=VoiceEchoGate()
  gate.remember("Acesta este un răspuns.")
  assertTrue(gate.isStop("Jarvis, oprește-te!"))
  assertTrue(gate.isStop("Jarvis, nu mai vorbi"))
  assertFalse(gate.isStop("Nu vreau să spui Jarvis stop"))
 }
 @Test fun rejectsEcho() {
  val gate=VoiceEchoGate()
  gate.remember("Spune Jarvis, nu mai vorbi pentru a opri răspunsul.")
  assertFalse(gate.isStop("Jarvis nu mai vorbi"))
 }
}
