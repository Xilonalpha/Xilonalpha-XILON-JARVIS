package com.xilon.jarvis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Lightweight on-device energy-based speech activity trigger.
 * Not acoustic echo cancellation: the caller must verify recognized text.
 * Owns the microphone only while sampling; stop before starting ASR.
 */
internal class LocalVoiceActivityDetector(private val context:Context):AutoCloseable {
 private val running=AtomicBoolean(false)
 private val generation=java.util.concurrent.atomic.AtomicLong(0L)
 private val main=Handler(Looper.getMainLooper())
 @Volatile private var recorder:AudioRecord?=null
 @Volatile private var worker:Thread?=null
 private val lifecycleLock=Any()
 fun start(onVoice:()->Unit,onError:(String)->Unit={}):Boolean {
  if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return false
  val session=synchronized(lifecycleLock){
   // A previous recorder must finish releasing before the next capture starts.
   if(recorder!=null || !running.compareAndSet(false,true))return false
   generation.incrementAndGet()
  }
  val sampleRate=16000
  val min=AudioRecord.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
  if(min<=0){running.set(false);return false}
  val bufferSize=maxOf(min,3200)
  val record=try {
   AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,sampleRate,
    AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,bufferSize)
  }catch(_:Exception){running.set(false);return false}
  if(record.state!=AudioRecord.STATE_INITIALIZED){
   record.release();running.set(false);return false
  }
  // Stop may have arrived while AudioRecord was being constructed.
  // Never start an obsolete session or publish its recorder to a new turn.
  val accepted=synchronized(lifecycleLock){
   if(generation.get()!=session || !running.get())false
   else {recorder=record;true}
  }
  if(!accepted){record.release();return false}
  // Platform DSP is optional: hardware/OS support differs across devices.
  // It reduces playback leakage but does not guarantee full-duplex echo removal.
  val sessionEcho=runCatching {
   if(AcousticEchoCanceler.isAvailable())AcousticEchoCanceler.create(record.audioSessionId)?.also{it.enabled=true}
   else null
  }.getOrNull()
  val sessionNoise=runCatching {
   if(NoiseSuppressor.isAvailable())NoiseSuppressor.create(record.audioSessionId)?.also{it.enabled=true}
   else null
  }.getOrNull()
  worker=Thread({
   val samples=ShortArray(320)
   val decision=VoiceActivityDecision()
   // Optional offline neural VAD. Energy gate remains available if the
   // model is absent or incompatible; no network access is performed.
   var neuralFailure:String?=null
   val neural=runCatching{
    val model=java.io.File(context.filesDir,"models/silero_vad.onnx")
    // A real compatible model can be bundled at assets/models/silero_vad.onnx.
    // Copy once to private storage because ONNX Runtime needs a filesystem path.
    if(!model.isFile){
     val stream=runCatching{context.assets.open("models/silero_vad.onnx")}.getOrNull()
     stream?.use{source->
      model.parentFile?.mkdirs()
      val temporary=java.io.File(model.parentFile,"silero_vad.onnx.part")
      try {
       source.copyTo(temporary.outputStream())
       if(temporary.length()>0 && !temporary.renameTo(model))
        throw java.io.IOException("Cannot install Silero model")
      }finally{temporary.delete()}
     }
    }
    if(model.isFile && model.length()>0)SileroVad16k(model) else null
   }.onFailure{neuralFailure=it.message?:"Model Silero incompatibil"}.getOrNull()
   if(neuralFailure!=null){
    main.post{if(generation.get()==session)onError("Silero VAD: "+neuralFailure)}
   }
   val startedAt=android.os.SystemClock.elapsedRealtime()
   var detected=false
   try {
    record.startRecording()
    while(running.get()){
     // Bound microphone ownership even when the environment remains silent.
     if(android.os.SystemClock.elapsedRealtime()-startedAt>=6000L)break
     val count=record.read(samples,0,samples.size)
     if(count<=0){
      // An interrupted or failed AudioRecord read must not spin indefinitely.
      break
     }
     var energy=0.0
     for(i in 0 until count){val v=samples[i].toDouble()/32768.0;energy+=v*v}
     val rms=sqrt(energy/count)
     val activity=if(neural!=null){
      // Do not silently reinterpret a failed neural inference as speech.
      // A model failure ends this capture; the next session can use energy VAD.
      try { neural.accept(samples,count) }
      catch(e:Exception){
       main.post{if(generation.get()==session)onError("Silero VAD: "+(e.message?:"inferență eșuată"))}
       break
      }
     }else decision.accept(rms)
     if(activity){
      detected=true
      break
     }
    }
   }catch(e:Exception){
    if(running.getAndSet(false))main.post{
     if(generation.get()==session)onError(e.message ?: "Eroare VAD local")
    }
   }finally {
    runCatching{neural?.close()}
    runCatching{record.stop()}
    // Effects belong to this AudioRecord, never to a newer session.
    runCatching{sessionEcho?.release()}
    runCatching{sessionNoise?.release()}
    record.release()
    synchronized(lifecycleLock){
     if(recorder===record)recorder=null
     // Do not mark the recorder free until AudioRecord.release() completed.
     if(generation.get()==session)running.set(false)
    }
    if(detected)main.post{if(generation.get()==session)onVoice()}
   }
  },"xilon-local-vad").also{it.isDaemon=true;it.start()}
  return true
 }
 fun stop(){
  val previous=synchronized(lifecycleLock){
   generation.incrementAndGet()
   running.set(false)
   recorder
  }
  // Stop unblocks a blocking read; the worker owns release().
  runCatching{previous?.stop()}
 }
 override fun close(){stop()}
}
