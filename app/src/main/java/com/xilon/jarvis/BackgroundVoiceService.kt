package com.xilon.jarvis

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Explicitly opted-in, user-visible microphone foreground service.
 * No hidden recording, auto-start on boot, or cloud inference.
 * Transcription, GGUF response generation and Piper playback are all local.
 * Only explicitly opted-in foreground service use is supported.
 */
class BackgroundVoiceService:Service(){
 companion object{
  const val ACTION_START="com.xilon.jarvis.BACKGROUND_VOICE_START"
  const val ACTION_STOP="com.xilon.jarvis.BACKGROUND_VOICE_STOP"
  private const val CHANNEL="xilon_background_voice"
  private const val NOTIFICATION_ID=4206
 }
 private val running=AtomicBoolean(false)
 private val executor=Executors.newSingleThreadExecutor()
 @Volatile private var engine:NeuralSpeech?=null
 @Volatile private var model:NativeModel?=null
 @Volatile private var piper:NeuralVoice?=null
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  if(intent?.action==ACTION_STOP){stopSelf();return START_NOT_STICKY}
  if(intent?.action!=ACTION_START)return START_NOT_STICKY
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
   stopSelf();return START_NOT_STICKY
  }
  if(!running.compareAndSet(false,true))return START_NOT_STICKY
  val manager=getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel(CHANNEL,"JARVIS • microfon în fundal",NotificationManager.IMPORTANCE_LOW))
  try{startForeground(NOTIFICATION_ID,notification("Ascultare locală activă"))}
  catch(e:Exception){running.set(false);stopSelf();return START_NOT_STICKY}
  executor.execute{
   try{
    val speech=NeuralSpeechFactory.create(applicationContext)
    engine=speech
    val saved=getSharedPreferences("jarvis_model",MODE_PRIVATE).getString("file",null)
    val file=saved?.takeIf{it.matches(Regex("model-[a-f0-9-]+\\.gguf"))}?.let{File(filesDir,it)}?.takeIf{it.isFile}
    if(file!=null){
     runCatching{NativeModel(0).also{it.load(file.absolutePath);model=it}}
      .onFailure{manager.notify(NOTIFICATION_ID,notification("GGUF indisponibil: "+(it.message?:"eroare").take(70)))}
    }
    piper=NeuralVoiceFactory.romanian(applicationContext)?.takeIf{it.installed()}
    while(running.get()){
     val transcript=runBlocking{speech.listen()}.trim()
     if(!running.get())break
     if(transcript.isNotBlank()){
      manager.notify(NOTIFICATION_ID,notification("Recunoscut: "+transcript.take(90)))
      val local=model
      if(local!=null){
       val answer=runCatching{local.resetCancellation();local.generate(
        "Răspunde în română, concis, fără să inventezi informații. Cerere: "+transcript.take(500),
        threads=2,context=1024,maxTokens=100
       )}.getOrNull()?.trim().orEmpty()
       if(running.get() && answer.isNotBlank()){
        manager.notify(NOTIFICATION_ID,notification("JARVIS: "+answer.take(90)))
        val voice=piper
        if(voice!=null){
         val done=CountDownLatch(1)
         voice.speakTracked(answer){done.countDown()}
         done.await(40,TimeUnit.SECONDS)
         voice.stop()
        }
       }
      }
     }else Thread.sleep(250)
    }
   }catch(e:Exception){
    if(running.get())manager.notify(NOTIFICATION_ID,notification("ASR indisponibil: "+(e.message?:"model lipsă").take(80)))
   }finally{stopSelf()}
  }
  return START_NOT_STICKY
 }
 private fun notification(message:String):Notification{
  val stop=PendingIntent.getService(this,1,Intent(this,BackgroundVoiceService::class.java).setAction(ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val open=PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
   .setContentTitle("XILON JARVIS • microfon activ").setContentText(message)
   .setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_media_pause,"Oprește",stop).build()
 }
 override fun onDestroy(){
  running.set(false)
  runCatching{engine?.stop()}
  runCatching{model?.stop()}
  runCatching{piper?.stop()}
  // Closing must follow any active decode, on the same executor.
  runCatching{executor.execute{runCatching{engine?.close()};engine=null;runCatching{model?.close()};model=null;piper=null}}
  executor.shutdown()
  super.onDestroy()
 }
}
