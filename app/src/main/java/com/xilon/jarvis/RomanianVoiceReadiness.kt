package com.xilon.jarvis
/** Do not confuse downloaded model weights with a usable Android inference backend. */
internal object RomanianVoiceReadiness {
 fun lianaInstalled(names:Set<String>)=
  "ro_RO-liana-medium.onnx" in names && "ro_RO-liana-medium.onnx.json" in names
 fun whisperExportInstalled(names:Set<String>)=
  "encoder_model_quantized.onnx" in names && "decoder_model_merged_quantized.onnx" in names
 fun piperRuntimeReady(modelPresent:Boolean,phonemizerAvailable:Boolean,engineAvailable:Boolean)=
  modelPresent && phonemizerAvailable && engineAvailable
}
