package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceInterruptGateTest {
 @Test fun throttlesRestarts(){
  val g=VoiceInterruptGate(2500)
  assertTrue(g.ready(5000));assertFalse(g.ready(5100))
  assertTrue(g.ready(7500));assertTrue(g.ready(100))
  g.reset();assertTrue(g.ready(101))
 }
}
