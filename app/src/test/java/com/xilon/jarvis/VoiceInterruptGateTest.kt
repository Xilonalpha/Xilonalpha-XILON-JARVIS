package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceInterruptGateTest{
 @Test fun throttlesAndResets(){
  val g=VoiceInterruptGate()
  assertTrue(g.ready(100))
  assertFalse(g.ready(200))
  assertTrue(g.ready(2700))
  g.reset()
  assertTrue(g.ready(2701))
 }
}
