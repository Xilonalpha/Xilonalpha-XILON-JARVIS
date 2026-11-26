package com.xilon.jarvis

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Silero VAD ONNX streaming adapter for the classic 16 kHz / 512-sample
 * interface (input, state, sr -> output, stateN). Model must be supplied locally.
 * Never downloads a model or invents neural predictions.
 */
internal class SileroVad16k(model:File):AutoCloseable {
 private val env=OrtEnvironment.getEnvironment()
 private val session=env.createSession(model.absolutePath,OrtSession.SessionOptions())
 private var state=FloatArray(2*1*128)
 private val window=FloatArray(512)
 private var buffered=0
 private var active=0
 init {
  require(model.isFile && model.length()>0){"Missing local Silero VAD model"}
  require(session.inputNames.containsAll(setOf("input","state","sr"))){"Unsupported Silero VAD ONNX inputs"}
  require(session.outputNames.containsAll(setOf("output","stateN"))){"Unsupported Silero VAD ONNX outputs"}
 }
 fun accept(samples:ShortArray,count:Int):Boolean {
  require(count in 0..samples.size)
  var detected=false
  for(i in 0 until count){
   window[buffered++]=samples[i]/32768f
   if(buffered==512){
    buffered=0
    val probability=infer()
    active=if(probability>=0.65f)active+1 else 0
    if(active>=2)detected=true
   }
  }
  return detected
 }
 private fun infer():Float {
  OnnxTensor.createTensor(env,FloatBuffer.wrap(window),longArrayOf(1,512)).use{audio->
   OnnxTensor.createTensor(env,FloatBuffer.wrap(state),longArrayOf(2,1,128)).use{memory->
    OnnxTensor.createTensor(env,LongBuffer.wrap(longArrayOf(16000)),longArrayOf(1)).use{rate->
     session.run(mapOf("input" to audio,"state" to memory,"sr" to rate)).use{outputs->
      val prediction=(outputs[0].value as Array<*>)[0] as FloatArray
      val next=outputs[1].value as Array<*>
      val updated=FloatArray(256)
      var pos=0
      for(layer in next){
       for(batch in layer as Array<*>){
        for(value in batch as FloatArray)updated[pos++]=value
       }
      }
      state=updated
      return prediction[0]
     }
    }
   }
  }
 }
 override fun close(){session.close()}
}
