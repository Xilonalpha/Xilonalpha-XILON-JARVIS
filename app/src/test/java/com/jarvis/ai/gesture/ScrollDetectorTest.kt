package com.jarvis.ai.gesture
import org.junit.Assert.*
import org.junit.Test
class ScrollDetectorTest {
 @Test fun confirmedStroke() {
  val d=ScrollDetector(ScrollConfig())
  assertNull(d.update(0,0.5f,0.5f))
  assertNull(d.update(50,0.5f,0.42f))
  assertNull(d.update(100,0.5f,0.30f))
  assertEquals(VerticalDirection.UP,d.update(250,0.5f,0.30f))
 }
 @Test fun horizontalIgnored() {
  val d=ScrollDetector(ScrollConfig())
  repeat(8){assertNull(d.update(it*50L,it*0.08f,0.5f))}
 }
}
