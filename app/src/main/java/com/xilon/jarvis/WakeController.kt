package com.xilon.jarvis
import android.content.Context
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.MediaRecorder
import com.jarvis.ai.wake.WakeWordEngine
import com.jarvis.assistant.voice.MicGain
import kotlinx.coroutines.runBlocking
class WakeController(private val context:Context,private val detected:()->Unit,private val status:(String)->Unit){
 @Volatile private var stopped=false
 @Volatile private var capture:AudioRecord?=null
 private val worker=java.util.concurrent.Executors.newSingleThreadExecutor()
 fun start(){worker.execute{
  var recorder:AudioRecord?=null
  var engine:WakeWordEngine?=null
  var hit=false
  try{
   val e=WakeWordEngine(context);engine=e
   check(runBlocking{e.initialize()}){e.unavailableReason ?: "Modele indisponibile"}
   if(!stopped){
    val min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
    check(min>0){"Format audio indisponibil"}
    val rec=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min,5120));recorder=rec;capture=rec
    check(rec.state==AudioRecord.STATE_INITIALIZED){"Microfon indisponibil"}
    if(stopped)return@execute
    rec.startRecording()
    status("Ascultare locală Hey Jarvis activă.")
    val frame=ShortArray(1280)
    val gain=MicGain()
    val samples=FloatArray(frame.size)
    while(!stopped){
     var offset=0
     while(offset<frame.size && !stopped){
      val n=rec.read(frame,offset,frame.size-offset)
      check(n>0){"Citire audio eșuată: "+n}
      offset+=n
     }
     if(!stopped){
      for(i in frame.indices)samples[i]=frame[i]/32768f
      gain.process(samples)
      for(i in frame.indices)frame[i]=(samples[i]*32768f).toInt().coerceIn(-32768,32767).toShort()
      if(runBlocking{e.detect(frame)}){hit=true;break}
     }
    }
   }
  }catch(t:Exception){if(!stopped)status("WakeWord: "+t.message)}
   catch(t:LinkageError){if(!stopped)status("ONNX indisponibil: "+t.message)}
  finally{
   capture=null;runCatching{recorder?.stop()};runCatching{recorder?.release()};runCatching{engine?.close()}
   if(!stopped){if(hit)detected() else status("Ascultare oprită.")}
   worker.shutdown()
  }
 }}
 fun stop(){stopped=true;runCatching{capture?.stop()}}
}
