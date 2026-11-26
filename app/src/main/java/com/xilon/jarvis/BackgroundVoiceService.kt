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

/**
 * Explicitly opted-in, user-visible microphone foreground service.
 * No hidden recording, auto-start on boot, or cloud inference.
 * Transcription is local; generated LLM replies remain in the foreground Activity.
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
    while(running.get()){
     val transcript=runBlocking{speech.listen()}.trim()
     if(!running.get())break
     if(transcript.isNotBlank()){
      // The notification displays recognition; it never executes commands without an Activity.
      manager.notify(NOTIFICATION_ID,notification("Recunoscut: "+transcript.take(90)))
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
  // Closing must follow any active decode, on the same executor.
  runCatching{executor.execute{runCatching{engine?.close()};engine=null}}
  executor.shutdown()
  super.onDestroy()
 }
}
