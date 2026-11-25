package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceEchoGateRegressionTest {
 @Test fun detectsAssistantEchoWithoutBlockingUserStop(){
  val gate=VoiceEchoGate()
  gate.remember("Salut! Pot să te ajut cu o întrebare despre telefon.")
  assertTrue(gate.isEcho("pot sa te ajut cu o intrebare"))
  assertFalse(gate.isEcho("Jarvis oprește"))
  assertTrue(gate.isStop("Jarvis oprește"))
 }
}
