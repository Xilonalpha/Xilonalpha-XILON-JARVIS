package com.xilon.jarvis

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Local Piper VITS through Sherpa-ONNX v1.12.x, with eSpeak phonemization data. */
class PiperRomanianVoice(context:Context):NeuralVoice {
 private val app=context.applicationContext
 private val root=File(app.filesDir,"models/romanian")
 private val mihai=File(root,"mihai")
 private fun useMihai()=File(mihai,"ro_RO-mihai-medium.onnx").isFile
 private val model:File get()=if(useMihai())File(mihai,"ro_RO-mihai-medium.onnx") else File(root,"ro_RO-liana-medium.onnx")
 private val config=File(root,"ro_RO-liana-medium.onnx.json")
 private val tokens:File get()=if(useMihai())File(mihai,"tokens.txt") else File(root,"tokens.txt")
 private val espeak=File(app.filesDir,"kokoro/espeak-ng-data")
 private val alternateEspeak=File(root,"espeak-ng-data")
 private val mihaiEspeak=File(mihai,"espeak-ng-data")
 private fun phonemeData():File?=listOf(mihaiEspeak,alternateEspeak,espeak).firstOrNull{it.isDirectory && it.list()?.isNotEmpty()==true}
 private val executor=Executors.newSingleThreadExecutor{r->Thread(r,"piper-ro-tts").apply{isDaemon=true}}
 private val epoch=AtomicLong(0L)
 @Volatile private var track:AudioTrack?=null
 override fun installed()=model.isFile && model.length()>0 && (useMihai() || (config.isFile && config.length()>0)) && tokens.isFile && tokens.length()>0 && phonemeData()!=null
 override fun speak(text:String){speakTracked(text){}}
 override fun speakTracked(text:String,done:(String?)->Unit){
  if(!installed()){done("Piper RO: lipsesc tokens.txt sau espeak-ng-data; fișierele ONNX singure nu sunt suficiente.");return}
  val id=epoch.incrementAndGet()
  executor.execute{
   var tts:OfflineTts?=null
   var error:String?=null
   try{
    if(id!=epoch.get())return@execute
    tts=OfflineTts(config=OfflineTtsConfig(model=OfflineTtsModelConfig(
     vits=OfflineTtsVitsModelConfig(model=model.absolutePath,tokens=tokens.absolutePath,dataDir=checkNotNull(phonemeData()){"Lipsesc datele eSpeak pentru limba română"}.absolutePath),
     numThreads=2)))
    val audio=tts.generateWithConfigAndCallback(text=text,config=com.k2fsa.sherpa.onnx.GenerationConfig(),callback={if(id==epoch.get())1 else 0})
    if(id!=epoch.get())return@execute
    val samples=audio.samples
    val sampleRate=audio.sampleRate
    check(samples.isNotEmpty() && sampleRate>0){"Piper nu a generat sunet"}
    val buffer=AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
    check(buffer>0){"AudioTrack indisponibil"}
    val player=AudioTrack.Builder()
     .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
     .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
     .setBufferSizeInBytes(maxOf(buffer,4096)).setTransferMode(AudioTrack.MODE_STREAM).build()
    track=player
    try{
     player.play()
     val pcm=ShortArray(2048)
     var offset=0
     while(offset<samples.size && id==epoch.get()){
      val count=minOf(pcm.size,samples.size-offset)
      for(i in 0 until count)pcm[i]=(samples[offset+i].coerceIn(-1f,1f)*32767).toInt().toShort()
      val written=player.write(pcm,0,count,AudioTrack.WRITE_BLOCKING)
      check(written>0){"AudioTrack write failed"}
      offset+=written
     }
     if(id==epoch.get()) {
      val durationMs=(samples.size.toLong()*1000L/sampleRate).coerceAtMost(120000L)
      val deadline=android.os.SystemClock.elapsedRealtime()+durationMs+2000
      while(id==epoch.get() && player.playbackHeadPosition.toLong()<samples.size && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(25)
     }
    }finally{track=null;runCatching{player.stop()};player.release()}
   }catch(e:Exception){error=e.message?:"Eroare Piper"}catch(e:LinkageError){error=e.message?:"Motor Sherpa indisponibil"}
   finally{runCatching{tts?.release()};if(id==epoch.get())done(error)}
  }
 }
 override fun stop(){epoch.incrementAndGet();runCatching{track?.pause()};runCatching{track?.flush()}}
}
