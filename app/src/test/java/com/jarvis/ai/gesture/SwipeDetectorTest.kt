package com.jarvis.ai.gesture
import org.junit.Assert.*
import org.junit.Test
class SwipeDetectorTest {
 @Test fun fastHorizontal() {
  val d=SwipeDetector(SwipeConfig())
  assertNull(d.update(0,0.1f,0.5f))
  assertNull(d.update(100,0.2f,0.5f))
  assertEquals(SwipeDirection.RIGHT,d.update(200,0.4f,0.5f))
 }
 @Test fun verticalIgnored() {
  val d=SwipeDetector(SwipeConfig())
  repeat(5){assertNull(d.update(it*50L,0.5f,it*0.1f))}
 }
}
