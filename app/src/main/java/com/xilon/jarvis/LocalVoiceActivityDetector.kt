package com.xilon.jarvis

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
 private val main=Handler(Looper.getMainLooper())
 @Volatile private var recorder:AudioRecord?=null
 @Volatile private var worker:Thread?=null
 fun start(onVoice:()->Unit,onError:(String)->Unit={}):Boolean {
  if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)return false
  if(!running.compareAndSet(false,true))return false
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
  worker=Thread({
   val samples=ShortArray(320)
   var consecutive=0
   var detected=false
   try {
    record.startRecording()
    while(running.get()){
     val count=record.read(samples,0,samples.size)
     if(count<=0)continue
     var energy=0.0
     for(i in 0 until count){val v=samples[i].toDouble()/32768.0;energy+=v*v}
     val rms=sqrt(energy/count)
     // Four consecutive 20 ms frames limit transient clicks.
     consecutive=if(rms>=0.035)consecutive+1 else 0
     if(consecutive>=4){
      running.set(false)
      detected=true
      break
     }
    }
   }catch(e:Exception){
    if(running.getAndSet(false))main.post{onError(e.message ?: "Eroare VAD local")}
   }finally {
    runCatching{record.stop()}
    record.release()
    if(recorder===record)recorder=null
    if(detected)main.post(onVoice)
   }
  },"xilon-local-vad").also{it.isDaemon=true;it.start()}
  return true
 }
 fun stop(){
  running.set(false)
  // Stop unblocks a blocking read; the worker owns release().
  runCatching{recorder?.stop()}
 }
 override fun close(){stop()}
}
