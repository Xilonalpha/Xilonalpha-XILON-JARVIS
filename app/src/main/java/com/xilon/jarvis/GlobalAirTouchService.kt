package com.xilon.jarvis

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.WindowManager
import android.view.Gravity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.jarvis.ai.gesture.GestureCursorView

/** Explicitly enabled camera foreground service; no automatic restart. */
class GlobalAirTouchService: Service(), LifecycleOwner {
 private val registry=LifecycleRegistry(this)
 override val lifecycle:Lifecycle get()=registry
 private var controller:AirTouchController?=null
 private var cursor:GestureCursorView?=null
 private var lastTap=0L
 private data class DragAnchor(val x:Int,val y:Int,val pkg:String,val fingerprint:String,val time:Long)
 private var anchor:DragAnchor?=null
 private var continuous:ContinuousAirDrag?=null
 private fun endDrag(){continuous?.finish();continuous=null;anchor=null}
 private fun available():Boolean{
  val power=getSystemService(android.os.PowerManager::class.java)
  val lock=getSystemService(KeyguardManager::class.java)
  return power.isInteractive && !lock.isKeyguardLocked && LocalScreen.connected()
 }
 private fun executeBinding(action:String):Boolean{
  if(action=="Implicit")return false
  if(action=="Dezactivat")return true
  if(!available())return true
  val now=android.os.SystemClock.elapsedRealtime()
  if(now-lastTap<700L)return true
  val obs=LocalScreen.observe()?:return true
  if(obs.packageName==null || obs.packageName==packageName)return true
  endDrag()
  lastTap=now
  val global=when(action){
   "Înapoi"->android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
   "Acasă"->android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
   "Recente"->android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
   else->null
  }
  if(global!=null)LocalScreen.service()?.performGlobalAction(global)
  else if(action in listOf("următoarea melodie","melodia anterioară","crește volumul","scade volumul"))MediaActions(this).execute(action)
  // Microphone remains explicitly controlled by the voice UI.
  return true
 }
 private fun binding(key:String)=GestureBindings.resolve(getSharedPreferences("air_touch_profile",MODE_PRIVATE).getString(key,null))
 private fun scroll(direction:Int){
  endDrag()
  if(!available())return
  val now=android.os.SystemClock.elapsedRealtime()
  if(now-lastTap<700L)return
  val obs=LocalScreen.observe()?:return
  if(obs.packageName==packageName)return
  lastTap=now
  LocalScreen.service()?.scrollConfirmed(direction>0)
 }
 override fun onCreate(){super.onCreate();registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)}
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  if(intent?.action=="stop"){stopSelf();return START_NOT_STICKY}
  if(controller!=null)return START_NOT_STICKY
  try{
   val nm=getSystemService(NotificationManager::class.java)
   nm.createNotificationChannel(NotificationChannel("air_global","Air Touch global",NotificationManager.IMPORTANCE_LOW))
   val stop=PendingIntent.getService(this,501,Intent(this,GlobalAirTouchService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
   startForeground(501,Notification.Builder(this,"air_global").setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("JARVIS • Air Touch global").setContentText("Camera frontală activă").setOngoing(true).addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Oprește",stop).build()).build())
   if(!android.provider.Settings.canDrawOverlays(this)||!LocalScreen.connected()||checkSelfPermission(android.Manifest.permission.CAMERA)!=android.content.pm.PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY}
   val view=GestureCursorView(this){}
   val wm=getSystemService(WINDOW_SERVICE) as WindowManager
   val params=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START;alpha=0.75f}
   wm.addView(view,params);cursor=view
   view.onPointTap=tap@{x,y->
    val now=android.os.SystemClock.elapsedRealtime()
    if(!available()){anchor=null;return@tap}
    if(executeBinding(binding("pinch")))return@tap
    if(now-lastTap>=700L){
     val svc=LocalScreen.service()
     val obs=LocalScreen.observe()
     val loc=IntArray(2);view.getLocationOnScreen(loc)
     if(svc!=null && obs!=null && obs.packageName!=null && obs.packageName!=packageName){
      lastTap=now
      val sx=(x+loc[0]).toInt();val sy=(y+loc[1]).toInt()
      val selected=getSharedPreferences("air_touch_profile",MODE_PRIVATE).getString("advanced_touch","off")
      val mode=if(selected in setOf("double","long","drag","zoom_in","zoom_out"))selected!! else "tap"
      endDrag()
      if(mode=="drag"){
       anchor=DragAnchor(sx,sy,obs.packageName,obs.fingerprint(),now)
       continuous=ContinuousAirDrag(svc).also{it.start(sx.toFloat(),sy.toFloat())}
      }else svc.airTouchMotion(sx,sy,sx,sy,mode,obs.packageName,obs.fingerprint())
     }
    }
   }
   view.onTrackingLost={endDrag()}
   view.onPinchMove={x,y->
    val previous=anchor
    if(previous!=null){
     val obs=LocalScreen.observe()
     if(!available() || obs?.packageName!=previous.pkg || android.os.SystemClock.elapsedRealtime()-previous.time>10000L)endDrag()
     else {val loc=IntArray(2);view.getLocationOnScreen(loc);continuous?.move((x+loc[0]).coerceIn(0f,(resources.displayMetrics.widthPixels-1).toFloat()),(y+loc[1]).coerceIn(0f,(resources.displayMetrics.heightPixels-1).toFloat()))}
    }
   }
   view.onPinchRelease={_,_->endDrag()}
   view.onCustomGesture={id->controller?.savedGestures()?.firstOrNull{it.template.id==id}?.let{executeBinding(GestureBindings.resolve(it.action))}}
   view.onHorizontal={direction->executeBinding(binding(if(direction>0)"right" else "left"))}
   view.onScroll={if(!executeBinding(binding(if(it>0)"up" else "down")))scroll(it)}
   view.onTwoFingerScroll={scroll(it)}
   view.onFistHold={anchor=null;stopSelf()}
   registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
   controller=AirTouchController(this,this,view,{android.util.Log.d("GlobalAirTouch",it)},{stopSelf()}).also{it.start()}
  }catch(e:Exception){android.util.Log.e("GlobalAirTouch","Start failed",e);stopSelf()}
  return START_NOT_STICKY
 }
 override fun onDestroy(){
  endDrag()
  controller?.close();controller=null
  cursor?.let{runCatching{(getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)}};cursor=null
  registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
  stopForeground(STOP_FOREGROUND_REMOVE)
  super.onDestroy()
 }
}
