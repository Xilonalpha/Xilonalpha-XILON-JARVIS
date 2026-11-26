package com.xilon.jarvis

import org.junit.Assert.*
import org.junit.Test

class VoicePartialGateTest {
 @Test fun throttlesFastPartialsAndAllowsChanges(){
  val gate=VoicePartialGate(180)
  assertTrue(gate.shouldPublish("sal",1000))
  assertFalse(gate.shouldPublish("sal",1200))
  assertFalse(gate.shouldPublish("salut",1100))
  assertTrue(gate.shouldPublish("salut",1200))
  assertFalse(gate.shouldPublish("salut",1500))
  assertTrue(gate.shouldPublish("salut Jarvis",1510))
 }
 @Test fun resetAllowsNewSession(){
  val gate=VoicePartialGate()
  assertFalse(gate.shouldPublish(" ",0))
  assertTrue(gate.shouldPublish("Bună",100))
  gate.reset()
  assertTrue(gate.shouldPublish("Bună",101))
 }
}
