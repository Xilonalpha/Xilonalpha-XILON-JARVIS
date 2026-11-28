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
 private val executor=Executors.newSingleThreadScheduledExecutor{r->Thread(r,"piper-ro-tts").apply{isDaemon=true}}
 private val playback=Executors.newSingleThreadExecutor{r->Thread(r,"piper-ro-player").apply{isDaemon=true}}
 private val epoch=AtomicLong(0L)
 private val preparationEpoch=AtomicLong(0L)
 private data class Prepared(val text:String,val key:String?,val rate:Int,val samples:FloatArray)
 private var prepared:Prepared?=null // accessed only on synthesis executor
 override fun prepare(text:String){
  if(text.isBlank() || text.length>400 || !installed())return
  val request=preparationEpoch.incrementAndGet()
  executor.execute{
   if(request!=preparationEpoch.get())return@execute
   try{
    idleRelease?.cancel(false)
    val tts=runtime()
    val audio=tts.generateWithConfigAndCallback(text=text,config=com.k2fsa.sherpa.onnx.GenerationConfig(),callback=com.jarvis.assistant.voice.SherpaAudioCallback { request==preparationEpoch.get() })
    if(request==preparationEpoch.get() && audio.samples.size in 1..960000 && audio.samples.all{it.isFinite()})prepared=Prepared(text,cachedKey,audio.sampleRate,audio.samples)
   }catch(e:Exception){prepared=null}catch(e:LinkageError){prepared=null}finally{scheduleRelease()}
  }
 }
 private var idleRelease:java.util.concurrent.ScheduledFuture<*>?=null
 private fun scheduleRelease(){idleRelease?.cancel(false);idleRelease=executor.schedule({releaseCached()},60,java.util.concurrent.TimeUnit.SECONDS)}
 // Worker-owned cache: never release native inference while generation is active.
 private var cachedTts:OfflineTts?=null
 private var cachedKey:String?=null
 private fun releaseCached(){prepared=null;runCatching{cachedTts?.release()};cachedTts=null;cachedKey=null}
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
  val previous=track;runCatching{previous?.pause()};runCatching{previous?.flush()}
  executor.execute{
   var error:String?=null
   try{
    if(id!=epoch.get())return@execute
    idleRelease?.cancel(false)
    val tts=runtime()
    // Sherpa exposes PCM chunks during inference. Use its runtime sample rate
    // and play each chunk immediately instead of waiting for the whole reply.
    val sampleRate=tts.sampleRate()
    val ready=prepared?.takeIf{it.text==text && it.key==cachedKey && it.rate==sampleRate}
    prepared=null
    val queue=java.util.concurrent.ArrayBlockingQueue<FloatArray>(8)
    val synthesisDone=java.util.concurrent.atomic.AtomicBoolean(false)
    val synthesisError=java.util.concurrent.atomic.AtomicReference<String?>(null)
    val playerFailed=java.util.concurrent.atomic.AtomicBoolean(false)
    playback.execute play@{
     var playbackError:String?=null
     try{
    check(sampleRate>0){"Piper sample rate invalid"}
    val buffer=AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
    check(buffer>0){"AudioTrack indisponibil"}
    val player=AudioTrack.Builder()
     .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
     .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
     .setBufferSizeInBytes(maxOf(buffer,4096)).setTransferMode(AudioTrack.MODE_STREAM).build()
    if(id!=epoch.get()){player.release();return@play}
    track=player
    try{
     check(player.state==AudioTrack.STATE_INITIALIZED){"AudioTrack nu s-a inițializat"}
     var frames=0L
     var audible=false
     var callbackError:String?=null
     val pcm=ShortArray(2048)
     fun emit(samples:FloatArray):Boolean {
      if(id!=epoch.get())return false
      if(samples.isEmpty())return true
      return try{
       check(samples.all{it.isFinite()}){"Semnal Piper invalid"}
       audible=audible||samples.any{kotlin.math.abs(it)>0.0001f}
       if(player.playState!=AudioTrack.PLAYSTATE_PLAYING)player.play()
       var offset=0
       while(offset<samples.size && id==epoch.get()){
        val count=minOf(pcm.size,samples.size-offset)
        for(i in 0 until count)pcm[i]=(samples[offset+i].coerceIn(-1f,1f)*32767).toInt().toShort()
        val written=player.write(pcm,0,count,AudioTrack.WRITE_BLOCKING)
        if(id!=epoch.get())return false
        check(written>0){"AudioTrack write failed"}
        offset+=written;frames+=written
       }
       id==epoch.get()
      }catch(e:Exception){callbackError=e.message?:"Eroare audio Piper";false}
     }
     while(id==epoch.get()){
      val chunk=queue.poll(50,java.util.concurrent.TimeUnit.MILLISECONDS)
      if(chunk==null){if(synthesisDone.get())break;continue}
      if(!emit(chunk))break
     }
     if(id!=epoch.get())return@play
     check(synthesisError.get()==null){synthesisError.get()?:"Eroare sinteză"}
     check(callbackError==null){callbackError?:"Eroare audio"}
     check(frames>0 && audible){"Piper a generat semnal audio silențios"}
     val deadline=android.os.SystemClock.elapsedRealtime()+(frames*1000L/sampleRate).coerceAtMost(120000L)+2000
     while(id==epoch.get() && (player.playbackHeadPosition.toLong() and 0xffffffffL)<frames && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(25)
     if(id==epoch.get())check((player.playbackHeadPosition.toLong() and 0xffffffffL)>=frames){"Redarea Piper nu s-a terminat în intervalul permis"}
    }finally{track=null;runCatching{player.stop()};player.release()}
     }catch(e:Exception){playbackError=e.message?:"Eroare redare Piper"}catch(e:LinkageError){playbackError=e.message?:"Motor audio indisponibil"}
     finally{playerFailed.set(true);if(id==epoch.get())done(playbackError)}
    }
    fun enqueue(samples:FloatArray):Boolean{
     if(samples.isEmpty())return true
     var offset=0
     while(offset<samples.size){
      val end=minOf(offset+4096,samples.size)
      val part=samples.copyOfRange(offset,end)
      while(id==epoch.get() && !playerFailed.get()){
       if(queue.offer(part,50,java.util.concurrent.TimeUnit.MILLISECONDS))break
      }
      if(id!=epoch.get() || playerFailed.get())return false
      offset=end
     }
     return true
    }
    try{
     if(ready!=null)enqueue(ready.samples)
     else{
      var emitted=false
      val audio=tts.generateWithConfigAndCallback(text=text,config=com.k2fsa.sherpa.onnx.GenerationConfig(),callback=com.jarvis.assistant.voice.SherpaAudioCallback { samples->if(samples.isNotEmpty())emitted=true;enqueue(samples) })
      if(!emitted && id==epoch.get())enqueue(audio.samples)
     }
    }catch(e:Exception){synthesisError.set(e.message?:"Eroare sinteză Piper")}catch(e:LinkageError){synthesisError.set(e.message?:"Motor Sherpa indisponibil")}
    finally{synthesisDone.set(true)}

   }catch(e:Exception){error=e.message?:"Eroare Piper"}catch(e:LinkageError){error=e.message?:"Motor Sherpa indisponibil"}
   finally{if(error!=null){releaseCached();if(id==epoch.get())done(error)}else scheduleRelease()}
  }
 }
 override fun stop(){preparationEpoch.incrementAndGet();val stopped=epoch.incrementAndGet();val current=track;runCatching{current?.pause()};runCatching{current?.flush()};executor.execute{if(epoch.get()==stopped){prepared=null;scheduleRelease()}}}
}
