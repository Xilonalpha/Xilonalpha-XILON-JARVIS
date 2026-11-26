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
 @Volatile private var echoCanceler:AcousticEchoCanceler?=null
 @Volatile private var noiseSuppressor:NoiseSuppressor?=null
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
  recorder=record
  // Platform DSP is optional: hardware/OS support differs across devices.
  // It reduces playback leakage but does not guarantee full-duplex echo removal.
  echoCanceler=runCatching {
   if(AcousticEchoCanceler.isAvailable())AcousticEchoCanceler.create(record.audioSessionId)?.also{it.enabled=true}
   else null
  }.getOrNull()
  noiseSuppressor=runCatching {
   if(NoiseSuppressor.isAvailable())NoiseSuppressor.create(record.audioSessionId)?.also{it.enabled=true}
   else null
  }.getOrNull()
  worker=Thread({
   val samples=ShortArray(320)
   val decision=VoiceActivityDecision()
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
     if(decision.accept(rms)){
      detected=true
      break
     }
    }
   }catch(e:Exception){
    if(running.getAndSet(false))main.post{
     if(generation.get()==session)onError(e.message ?: "Eroare VAD local")
    }
   }finally {
    runCatching{record.stop()}
    runCatching{echoCanceler?.release()}
    runCatching{noiseSuppressor?.release()}
    echoCanceler=null
    noiseSuppressor=null
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
