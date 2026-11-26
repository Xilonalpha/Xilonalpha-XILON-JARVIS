package com.xilon.jarvis

import org.junit.Assert.*
import org.junit.Test

class VoiceActivityDecisionTest {
 @Test fun requiresFourConsecutiveLoudFrames(){
  val detector=VoiceActivityDecision()
  repeat(3){assertFalse(detector.accept(0.10))}
  assertTrue(detector.accept(0.10))
 }
 @Test fun resetsAfterQuietFrame(){
  val detector=VoiceActivityDecision()
  repeat(3){assertFalse(detector.accept(0.10))}
  assertFalse(detector.accept(0.0))
  repeat(3){assertFalse(detector.accept(0.10))}
  assertTrue(detector.accept(0.10))
 }
 @Test fun ignoresNoiseBelowAbsoluteFloor(){
  val detector=VoiceActivityDecision()
  repeat(50){assertFalse(detector.accept(0.01))}
 }
}
