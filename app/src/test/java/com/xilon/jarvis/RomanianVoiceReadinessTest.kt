package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class RomanianVoiceReadinessTest {
 @Test fun piperWeightsAreNotAnInstalledVoiceEngine(){
  assertTrue(RomanianVoiceReadiness.lianaInstalled(setOf("ro_RO-liana-medium.onnx","ro_RO-liana-medium.onnx.json")))
  assertFalse(RomanianVoiceReadiness.piperRuntimeReady(true,false,true))
  assertFalse(RomanianVoiceReadiness.piperRuntimeReady(true,true,false))
  assertTrue(RomanianVoiceReadiness.piperRuntimeReady(true,true,true))
 }
 @Test fun whisperNeedsBothExportFiles(){
  assertFalse(RomanianVoiceReadiness.whisperExportInstalled(setOf("encoder_model_quantized.onnx")))
  assertTrue(RomanianVoiceReadiness.whisperExportInstalled(setOf("encoder_model_quantized.onnx","decoder_model_merged_quantized.onnx")))
 }
}
