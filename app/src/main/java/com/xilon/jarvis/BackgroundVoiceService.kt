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
import android.os.SystemClock

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
  const val ACTION_INITIATIVE_ON="com.xilon.jarvis.BACKGROUND_INITIATIVE_ON"
  const val ACTION_INITIATIVE_OFF="com.xilon.jarvis.BACKGROUND_INITIATIVE_OFF"
  @Volatile internal var sessionActive=false
   private set
  private val sessionSlot=AtomicBoolean(false)
  private const val CHANNEL="xilon_background_voice"
  private const val NOTIFICATION_ID=4206
 }
 private val mainHandler=android.os.Handler(android.os.Looper.getMainLooper())
 private var speaker:OfflineVoice?=null
 private val running=AtomicBoolean(false)
 private val executor=Executors.newSingleThreadExecutor()
 @Volatile private var engine:NeuralSpeech?=null
 @Volatile private var model:NativeModel?=null
 @Volatile private var piper:NeuralVoice?=null
 @Volatile private var initiative=false
 private var lastInteraction=0L
 private var lastInitiative=0L
 private val initiativeDelay=600000L
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  if(intent?.action==ACTION_STOP){running.set(false);stopSelf();return START_NOT_STICKY}
  if(intent?.action==ACTION_INITIATIVE_ON){initiative=true;lastInteraction=SystemClock.elapsedRealtime();return START_NOT_STICKY}
  if(intent?.action==ACTION_INITIATIVE_OFF){initiative=false;return START_NOT_STICKY}
  if(intent?.action!=ACTION_START)return START_NOT_STICKY
  if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
   stopSelf();return START_NOT_STICKY
  }
  if(!running.compareAndSet(false,true))return START_NOT_STICKY
  if(!sessionSlot.compareAndSet(false,true)){running.set(false);stopSelf();return START_NOT_STICKY}
  sessionActive=true
  initiative=getSharedPreferences("conversation",MODE_PRIVATE).getBoolean("background_initiative",false)
  lastInteraction=SystemClock.elapsedRealtime()
  val manager=getSystemService(NotificationManager::class.java)
  manager.createNotificationChannel(NotificationChannel(CHANNEL,"JARVIS • microfon în fundal",NotificationManager.IMPORTANCE_LOW))
  try{startForeground(NOTIFICATION_ID,notification("Ascultare locală activă"))}
  catch(e:Exception){running.set(false);sessionActive=false;sessionSlot.set(false);stopSelf();return START_NOT_STICKY}
  speaker=OfflineVoice(this)
  executor.execute{
   try{
    val speech=NeuralSpeechFactory.create(applicationContext)
    if(speech==null){
     manager.notify(NOTIFICATION_ID,notification("Motorul ASR local nu este disponibil."))
     return@execute
    }
    engine=speech
    if(!running.get())return@execute
    val saved=getSharedPreferences("jarvis_model",MODE_PRIVATE).getString("file",null)
    val file=saved?.takeIf{it.matches(Regex("model-[a-f0-9-]+\\.gguf"))}?.let{File(filesDir,it)}?.takeIf{it.isFile}
    if(file!=null){
     runCatching{
      val candidate=NativeModel(0)
      try { candidate.load(file.absolutePath); if(running.get())model=candidate else candidate.close() }
      catch(e:Exception){candidate.close();throw e}
     }
      .onFailure{manager.notify(NOTIFICATION_ID,notification("GGUF indisponibil: "+(it.message?:"eroare").take(70)))}
    }
    if(!running.get())return@execute
    piper=NeuralVoiceFactory.romanian(applicationContext)?.takeIf{it.installed()}
    while(running.get()){
     // The service only processes local speech; no network recognition fallback.
     val transcript=runBlocking{speech.listen()}.trim()
     if(!running.get())break
     if(transcript.isNotBlank()){
      lastInteraction=SystemClock.elapsedRealtime()
      manager.notify(NOTIFICATION_ID,notification("Comandă vocală recunoscută local"))
      val local=model
      if(local!=null){
       val answer=runCatching{local.resetCancellation();local.generate(
        "Răspunde în română, concis, fără să inventezi informații. Cerere: "+transcript.take(500),
        threads=inferenceThreads(),context=1024,maxTokens=100
       )}.getOrNull()?.trim().orEmpty()
       if(running.get() && answer.isNotBlank()){
        manager.notify(NOTIFICATION_ID,notification("JARVIS răspunde local"))
        speakAnswer(answer)
       }
      }
     }else{
      val now=SystemClock.elapsedRealtime()
      initiative=getSharedPreferences("conversation",MODE_PRIVATE).getBoolean("background_initiative",false)
      if(initiative && model!=null && now-lastInteraction>=initiativeDelay && now-lastInitiative>=initiativeDelay){
       lastInitiative=now
       lastInteraction=now
       val message=runCatching{model!!.resetCancellation();model!!.generate(
        "Inițiază în română o conversație scurtă, cu o singură întrebare utilă. Nu pretinde că ai observat ceva despre utilizator.",
        threads=inferenceThreads(),context=1024,maxTokens=80
       )}.getOrNull()?.trim().orEmpty()
       if(running.get() && initiative && message.isNotBlank()){
        manager.notify(NOTIFICATION_ID,notification("JARVIS inițiază conversația"))
        speakAnswer(message)
       }
      }
      Thread.sleep(250)
     }
    }
   }catch(e:Exception){
    if(running.get())manager.notify(NOTIFICATION_ID,notification("ASR indisponibil: "+(e.message?:"model lipsă").take(80)))
   }finally{
    running.set(false)
    runCatching{engine?.close()};engine=null
    runCatching{model?.close()};model=null
    runCatching{piper?.stop()};piper=null
    sessionActive=false;sessionSlot.set(false)
    stopSelf()
   }
  }
  return START_NOT_STICKY
 }
 private fun speakAnswer(text:String){
  val done=CountDownLatch(1)
  val failed=AtomicBoolean(false)
  val active=AtomicBoolean(true)
  mainHandler.post {
   if(!active.get() || !running.get()){done.countDown();return@post}
   val voice=speaker
   voice?.onAllFinished={done.countDown()}
   voice?.onFailure={failed.set(true);done.countDown()}
   if(voice==null || voice.speak(text)!=null){failed.set(true);done.countDown()}
  }
  val deadline=SystemClock.elapsedRealtime()+60000L
  try{
   while(running.get() && SystemClock.elapsedRealtime()<deadline && !done.await(100,TimeUnit.MILLISECONDS)){}
  }finally{
   active.set(false)
   mainHandler.post{speaker?.stop()}
  }
  if(failed.get() && running.get()){
   val fallback=piper
   if(fallback!=null)speakUntilStopped(fallback,text)
   else getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,notification("Vocea română offline lipsește."))
  }
 }
 private fun speakUntilStopped(voice:NeuralVoice,text:String){
  if(!running.get())return
  val done=CountDownLatch(1)
  val deadline=SystemClock.elapsedRealtime()+40000L
  try{
   voice.speakTracked(text){done.countDown()}
   while(running.get() && SystemClock.elapsedRealtime()<deadline){
    if(done.await(100,TimeUnit.MILLISECONDS))break
   }
  }finally{voice.stop()}
 }
 private fun inferenceThreads():Int{
  val info=android.app.ActivityManager.MemoryInfo()
  getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info)
  val thermal=if(Build.VERSION.SDK_INT>=29)getSystemService(android.os.PowerManager::class.java).currentThermalStatus else 0
  return minOf(2,ResourceBudget.speechThreads(Runtime.getRuntime().availableProcessors(),info.availMem/1048576,info.lowMemory,thermal))
 }
 private fun notification(message:String):Notification{
  val stop=PendingIntent.getService(this,1,Intent(this,BackgroundVoiceService::class.java).setAction(ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val open=PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
   .setContentTitle("XILON JARVIS • microfon activ").setContentText(message)
   .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_media_pause,"Oprește",stop).build()
 }
 override fun onDestroy(){
  running.set(false)
  runCatching{engine?.stop()}
  runCatching{model?.stop()}
  runCatching{piper?.stop()}
  // The worker releases native engines in its finally block, after decoding exits.
  speaker?.close();speaker=null
  executor.shutdown()
  super.onDestroy()
 }
}
