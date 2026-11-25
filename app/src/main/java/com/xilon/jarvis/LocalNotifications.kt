package com.xilon.jarvis
import android.service.notification.NotificationListenerService
import android.app.Notification
class LocalNotifications:NotificationListenerService(){
 companion object {
  @Volatile private var active:LocalNotifications?=null
  fun snapshot():List<String>? {
   val svc=active?:return null
   return try { svc.activeNotifications?.map { n ->
    val extras=n.notification.extras
    val title=extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(120).orEmpty()
    val body=extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(160).orEmpty()
    val sensitive=listOf("otp","verification code","one-time","cod de verificare","password").any{(title+" "+body).contains(it,true)}
    if(sensitive || n.notification.visibility==Notification.VISIBILITY_SECRET) "[${n.packageName}] Protected notification" else "[${n.packageName}] $title — $body"
   } } catch(e:Exception){null}
  }
 }
 override fun onListenerConnected(){super.onListenerConnected();active=this}
 override fun onListenerDisconnected(){if(active===this)active=null;super.onListenerDisconnected()}
 override fun onDestroy(){if(active===this)active=null;super.onDestroy()}
}
