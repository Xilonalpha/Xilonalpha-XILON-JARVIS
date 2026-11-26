package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceCaptureGateTest {
 @Test fun noOverlappingCapture(){
  val g=VoiceCaptureGate()
  assertTrue(g.begin(1));assertTrue(g.active())
  assertFalse(g.begin(2));assertFalse(g.finish(2))
  assertTrue(g.finish(1));assertTrue(g.begin(2))
 }
 @Test fun cancellationRejectsStaleCompletion(){
  val g=VoiceCaptureGate()
  assertTrue(g.begin(3));g.cancel()
  assertFalse(g.begin(4));assertTrue(g.finish(3))
  assertTrue(g.begin(4));assertFalse(g.finish(3))
  assertTrue(g.active());assertTrue(g.finish(4))
 }
 @Test fun closePermanentlyDisablesCapture(){
  val g=VoiceCaptureGate();assertTrue(g.begin(1))
  g.close();assertTrue(g.active());assertFalse(g.begin(2))
  assertTrue(g.finish(1));assertFalse(g.active());assertFalse(g.available())
 }
}
