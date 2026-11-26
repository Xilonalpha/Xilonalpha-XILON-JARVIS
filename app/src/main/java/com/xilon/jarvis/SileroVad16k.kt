package com.xilon.jarvis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Silero VAD ONNX streaming adapter for the 16 kHz / 512-sample with 64-sample left context
 * interface (input, state, sr -> output, stateN). Model must be supplied locally.
 * Never downloads a model or invents neural predictions.
 */
internal class SileroVad16k(model:File):AutoCloseable {
 private val env=OrtEnvironment.getEnvironment()
 private val session=OrtSession.SessionOptions().use { options ->
  require(model.isFile && model.length()>0){"Missing local Silero VAD model"}
  env.createSession(model.absolutePath,options)
 }
 private var state=FloatArray(2*1*128)
 private val window=FloatArray(512)
 private val contextSamples=FloatArray(64)
 private var buffered=0
 private val gate=NeuralSpeechGate()
 init {
  try {
   require(session.inputNames.containsAll(setOf("input","state","sr"))){"Unsupported Silero VAD ONNX inputs"}
   require(session.outputNames.containsAll(setOf("output","stateN"))){"Unsupported Silero VAD ONNX outputs"}
  }catch(e:Exception){session.close();throw e}
 }
 fun accept(samples:ShortArray,count:Int):Boolean {
  require(count in 0..samples.size)
  var detected=false
  for(i in 0 until count){
   window[buffered++]=samples[i]/32768f
   if(buffered==512){
    buffered=0
    val probability=infer()
    if(gate.accept(probability))detected=true
   }
  }
  return detected
 }
 fun reset(){
  state.fill(0f)
  window.fill(0f)
  contextSamples.fill(0f)
  buffered=0
  gate.reset()
 }
 private fun infer():Float {
  val framed=FloatArray(576)
  System.arraycopy(contextSamples,0,framed,0,64)
  System.arraycopy(window,0,framed,64,512)
  System.arraycopy(window,448,contextSamples,0,64)
  OnnxTensor.createTensor(env,FloatBuffer.wrap(framed),longArrayOf(1,576)).use{audio->
   OnnxTensor.createTensor(env,FloatBuffer.wrap(state),longArrayOf(2,1,128)).use{memory->
    OnnxTensor.createTensor(env,LongBuffer.wrap(longArrayOf(16000)),longArrayOf()).use{rate->
     session.run(mapOf("input" to audio,"state" to memory,"sr" to rate)).use{outputs->
      val prediction=outputs.get("output").orElseThrow{
       IllegalStateException("Missing Silero output")
      }.value as Array<*>
      val probability=(prediction.first() as FloatArray).first()
      val next=outputs.get("stateN").orElseThrow{
       IllegalStateException("Missing Silero recurrent state")
      }.value as Array<*>
      val updated=FloatArray(256)
      var pos=0
      for(layer in next){
       for(batch in layer as Array<*>){
        for(value in batch as FloatArray){
         require(pos<updated.size){"Unexpected Silero state dimensions"}
         updated[pos++]=value
        }
       }
      }
      require(pos==updated.size && probability.isFinite() && probability in 0f..1f){"Invalid Silero output"}
      state=updated
      return probability
     }
    }
   }
  }
 }
 override fun close(){session.close()}
}
