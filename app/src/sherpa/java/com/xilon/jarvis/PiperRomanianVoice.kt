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
 private fun mihaiReady() = listOf("ro_RO-mihai-medium.onnx","tokens.txt","espeak-ng-data/phondata").all {
  File(mihai,it).let { file -> file.isFile && file.length()>0 }
 }
 private fun lianaData() = listOf(File(root,"espeak-ng-data"),File(mihai,"espeak-ng-data"),File(app.filesDir,"kokoro/espeak-ng-data"))
  .firstOrNull { File(it,"phondata").length()>0 && File(it,"ro_dict").length()>0 }
 private fun lianaReady() = LianaVoicePackage.sourcesPresent(root) && lianaData()!=null
 private fun useLiana() = when(app.getSharedPreferences("piper_voice",Context.MODE_PRIVATE).getString("speaker","auto")){
  "liana" -> true
  "mihai" -> false
  else -> !mihaiReady()
 }
 private val executor=Executors.newSingleThreadExecutor{r->Thread(r,"piper-ro-tts").apply{isDaemon=true}}
 private val epoch=AtomicLong(0L)
 // Worker-owned cache: never release native inference while generation is active.
 private var cachedTts:OfflineTts?=null
 private var cachedKey:String?=null
 private fun releaseCached(){runCatching{cachedTts?.release()};cachedTts=null;cachedKey=null}
 private fun runtime():OfflineTts {
  val liana=useLiana()
  val dir=if(liana)LianaVoicePackage.prepare(root){app.assets.open("liana-sherpa-metadata.pb")} else mihai
  val m=File(dir,if(liana)"model.onnx" else "ro_RO-mihai-medium.onnx")
  val t=File(dir,"tokens.txt")
  val d=if(liana)checkNotNull(lianaData()){"Lipsesc datele eSpeak"} else File(mihai,"espeak-ng-data")
  val key=listOf(m.absolutePath,m.lastModified().toString(),m.length().toString(),t.absolutePath,t.lastModified().toString(),d.absolutePath).joinToString("|")
  if(cachedKey==key)cachedTts?.let{return it}
  releaseCached()
  return OfflineTts(config=OfflineTtsConfig(model=OfflineTtsModelConfig(vits=OfflineTtsVitsModelConfig(model=m.absolutePath,tokens=t.absolutePath,dataDir=d.absolutePath),numThreads=localTtsThreadBudget(app)))).also{cachedTts=it;cachedKey=key}
 }
 @Volatile private var track:AudioTrack?=null
 override fun installed()=if(useLiana())lianaReady() else mihaiReady()
 override fun speak(text:String){speakTracked(text){}}
 override fun speakTracked(text:String,done:(String?)->Unit){
  if(text.isBlank()){done(null);return}
  if(!installed()){done("Piper RO: lipsesc tokens.txt sau espeak-ng-data; fișierele ONNX singure nu sunt suficiente.");return}
  val id=epoch.incrementAndGet()
  executor.execute{
   var error:String?=null
   try{
    if(id!=epoch.get())return@execute
    val tts=runtime()
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
    if(id!=epoch.get()){player.release();return@execute}
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
   finally{if(error!=null)releaseCached();if(id==epoch.get())done(error)}
  }
 }
 override fun stop(){val stopped=epoch.incrementAndGet();val current=track;runCatching{current?.pause()};runCatching{current?.flush()};executor.execute{if(epoch.get()==stopped)releaseCached()}}
}
