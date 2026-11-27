package com.jarvis.ai.gesture
import org.junit.Assert.*
import org.junit.Test
class ScreenMapperTest {
 @Test fun regionMapsToEdges(){
  val m=ScreenMapper.fromRegion()
  assertEquals(0f,m.map(NormPoint(0.2f,0.2f)).x,0.0001f)
  assertEquals(1f,m.map(NormPoint(0.8f,0.8f)).y,0.0001f)
 }
 @Test fun calibrationRejectsCollinear(){
  assertNull(Calibration.solve(listOf(NormPoint(0f,0f),NormPoint(0.5f,0.5f),NormPoint(1f,1f))))
 }
}
