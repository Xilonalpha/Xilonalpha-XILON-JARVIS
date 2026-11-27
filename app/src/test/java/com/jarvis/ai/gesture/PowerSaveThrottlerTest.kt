package com.jarvis.ai.gesture
import org.junit.Assert.*
import org.junit.Test
class PowerSaveThrottlerTest {
 @Test fun idleAndResume() {
  val t=PowerSaveThrottler()
  t.onProcessed(0,false)
  t.onProcessed(1000,false)
  assertTrue(t.isIdle)
  assertFalse(t.shouldProcess(1033))
  assertTrue(t.shouldProcess(1100))
  t.onProcessed(1100,true)
  assertFalse(t.isIdle)
  assertTrue(t.shouldProcess(1133))
 }
}
