package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceActivityDecisionTest {
 @Test fun rejectsSilenceAndInvalidSamples(){
  val gate=VoiceActivityDecision()
  repeat(100){assertFalse(gate.accept(0.0))}
  assertFalse(gate.accept(Double.NaN))
  assertFalse(gate.accept(Double.POSITIVE_INFINITY))
  assertFalse(gate.accept(-1.0))
 }
 @Test fun requiresConsecutiveVoiceFrames(){
  val gate=VoiceActivityDecision()
  repeat(3){assertFalse(gate.accept(0.2))}
  assertTrue(gate.accept(0.2))
 }
 @Test fun silenceResetsSpeechWindow(){
  val gate=VoiceActivityDecision()
  repeat(3){assertFalse(gate.accept(0.2))}
  assertFalse(gate.accept(0.0))
  repeat(3){assertFalse(gate.accept(0.2))}
  assertTrue(gate.accept(0.2))
 }
}
