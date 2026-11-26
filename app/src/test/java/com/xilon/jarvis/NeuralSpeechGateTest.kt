package com.xilon.jarvis

import org.junit.Assert.*
import org.junit.Test

class NeuralSpeechGateTest {
 @Test fun requiresTwoConsecutivePositiveFrames(){
  val gate=NeuralSpeechGate()
  assertFalse(gate.accept(0.90f))
  assertTrue(gate.accept(0.90f))
 }
 @Test fun silenceResetsConsecutiveFrames(){
  val gate=NeuralSpeechGate()
  assertFalse(gate.accept(0.90f))
  assertFalse(gate.accept(0.10f))
  assertFalse(gate.accept(0.90f))
  assertTrue(gate.accept(0.90f))
 }
 @Test fun invalidProbabilityNeverTriggersSpeech(){
  val gate=NeuralSpeechGate()
  assertFalse(gate.accept(0.90f))
  assertFalse(gate.accept(Float.NaN))
  assertFalse(gate.accept(0.90f))
  assertFalse(gate.accept(1.1f))
  assertFalse(gate.accept(0.90f))
  assertTrue(gate.accept(0.90f))
 }
 @Test fun resetClearsHistory(){
  val gate=NeuralSpeechGate()
  assertFalse(gate.accept(0.90f))
  gate.reset()
  assertFalse(gate.accept(0.90f))
  assertTrue(gate.accept(0.90f))
 }
}
