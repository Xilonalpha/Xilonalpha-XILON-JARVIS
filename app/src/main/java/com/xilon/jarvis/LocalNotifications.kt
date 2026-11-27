package com.xilon.jarvis
import android.service.notification.NotificationListenerService
import android.app.Notification
class LocalNotifications:NotificationListenerService(){
 companion object {
  @Volatile private var active:LocalNotifications?=null
  fun replyToWhatsApp(phone: String, body: String): Boolean {
   val service = active ?: return false
   if (android.os.Build.VERSION.SDK_INT < 30) return false
   val matches = service.activeNotifications.orEmpty().mapNotNull { item ->
    if (item.packageName !in setOf("com.whatsapp", "com.whatsapp.w4b") || item.user != android.os.Process.myUserHandle()) return@mapNotNull null
    val notification = item.notification
    if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return@mapNotNull null
    val style = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification) ?: return@mapNotNull null
    if (style.isGroupConversation || style.conversationTitle != null) return@mapNotNull null
    val sender = style.messages.lastOrNull()?.person?.uri ?: return@mapNotNull null
    val uri = android.net.Uri.parse(sender)
    if (uri.scheme != "tel" || android.telephony.PhoneNumberUtils.normalizeNumber(uri.schemeSpecificPart) != "+$phone") return@mapNotNull null
    notification.actions.orEmpty().singleOrNull { action ->
     action.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY && action.actionIntent?.creatorPackage == item.packageName &&
      action.remoteInputs.orEmpty().count { it.allowFreeFormInput } == 1
    }
   }
   val action = matches.singleOrNull() ?: return false
   val input = action.remoteInputs.single { it.allowFreeFormInput }
   val result = android.content.Intent()
   android.app.RemoteInput.addResultsToIntent(arrayOf(input), result, android.os.Bundle().apply { putCharSequence(input.resultKey, body) })
   return try { action.actionIntent.send(service, 0, result); true } catch (_: android.app.PendingIntent.CanceledException) { false }
  }
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
