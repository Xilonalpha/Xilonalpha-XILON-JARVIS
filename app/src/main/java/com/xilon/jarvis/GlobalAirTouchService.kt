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
   view.onPointTap={x,y->
    val now=android.os.SystemClock.elapsedRealtime()
    if(now-lastTap>=700L){
     val svc=LocalScreen.service()
     val obs=LocalScreen.observe()
     val loc=IntArray(2);view.getLocationOnScreen(loc)
     if(svc!=null && obs!=null && obs.packageName!=packageName){
      lastTap=now
      svc.airTouchMotion((x+loc[0]).toInt(),(y+loc[1]).toInt(),(x+loc[0]).toInt(),(y+loc[1]).toInt(),"tap",obs.packageName,obs.fingerprint())
     }
    }
   }
   view.onFistHold={stopSelf()}
   registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
   controller=AirTouchController(this,this,view,{android.util.Log.d("GlobalAirTouch",it)},{stopSelf()}).also{it.start()}
  }catch(e:Exception){android.util.Log.e("GlobalAirTouch","Start failed",e);stopSelf()}
  return START_NOT_STICKY
 }
 override fun onDestroy(){
  controller?.close();controller=null
  cursor?.let{runCatching{(getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it)}};cursor=null
  registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
  stopForeground(STOP_FOREGROUND_REMOVE)
  super.onDestroy()
 }
}
