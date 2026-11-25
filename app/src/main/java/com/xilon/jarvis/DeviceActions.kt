package com.xilon.jarvis
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.provider.AlarmClock
import android.net.Uri
import java.util.Locale

class DeviceActions(private val activity:Activity) {
 private fun apps()=activity.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0).filter { it.activityInfo.exported && it.activityInfo.packageName!=activity.packageName }.distinctBy { it.activityInfo.packageName }
 private fun launch(name:String):String {
  if(name.isBlank())return "Specifică numele aplicației."
  val matches=apps().filter { it.loadLabel(activity.packageManager).toString().equals(name,true) || it.activityInfo.packageName.equals(name,true) }
  if(matches.isEmpty())return "Aplicație negăsită: "+name
  if(matches.size>1)return "Nume ambiguu: "+matches.joinToString { it.activityInfo.packageName }
  val info=matches.single().activityInfo
  return try { activity.startActivity(Intent.makeMainActivity(android.content.ComponentName(info.packageName,info.name))); "Cerere de lansare transmisă: "+name } catch(e:Exception){ "Lansare eșuată: "+e.message }
 }
 fun execute(command:String):String? {
  val raw=command.trim()
  if(raw.equals("lista aplicații",true))return apps().map { it.loadLabel(activity.packageManager).toString()+" ("+it.activityInfo.packageName+")" }.sorted().joinToString("\n").ifEmpty { "Nicio aplicație vizibilă." }
  if(raw.startsWith("lansează ",true))return launch(raw.substringAfter(" ").trim())
  if(raw.startsWith("caută aplicații ",true)){
   val term=raw.substringAfter(" ").substringAfter(" ").trim()
   if(term.isBlank())return "Specifică un nume."
   return apps().filter{it.loadLabel(activity.packageManager).toString().contains(term,true)||it.activityInfo.packageName.contains(term,true)}.map{it.loadLabel(activity.packageManager).toString()+" ("+it.activityInfo.packageName+")"}.sorted().take(30).joinToString("\n").ifBlank{"Nicio aplicație vizibilă potrivită."}
  }
  val q=command.trim().lowercase(Locale.ROOT)
  val intent=when(q){
   "deschide setări" -> Intent(Settings.ACTION_SETTINGS)
   "deschide wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
   "deschide bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
   "deschide setări baterie" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
   "deschide setări afișaj" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
   "deschide setări sunet" -> Intent(Settings.ACTION_SOUND_SETTINGS)
   "deschide setări stocare" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
   "deschide setări accesibilitate" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
   "deschide setări notificări" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName)
   "deschide setări limbă" -> Intent(Settings.ACTION_LOCALE_SETTINGS)
   "deschide setări dată" -> Intent(Settings.ACTION_DATE_SETTINGS)
   "deschide setări locație" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
   "deschide alarme" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
   else -> when {
    q.startsWith("alarmă ") -> {
     val m=Regex("alarmă (\\d{1,2}):(\\d{2})").matchEntire(q) ?: return "Format: alarmă HH:MM"
     val h=m.groupValues[1].toInt();val min=m.groupValues[2].toInt()
     if(h !in 0..23 || min !in 0..59)return "Ora invalidă."
     Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,h).putExtra(AlarmClock.EXTRA_MINUTES,min).putExtra(AlarmClock.EXTRA_SKIP_UI,false)
    }
    q.startsWith("cronometru ") -> {
     val seconds=q.substringAfter(" ").toIntOrNull() ?: return "Format: cronometru secunde"
     if(seconds !in 1..86400)return "Durata trebuie să fie între 1 și 86400 secunde."
     Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH,seconds).putExtra(AlarmClock.EXTRA_SKIP_UI,false)
    }
    q.startsWith("formează ") -> {
     val number=q.substringAfter(" ")
     if(!Regex("[+]?[0-9]{3,15}").matches(number))return "Număr invalid."
     Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",number,null))
    }
    else -> return null
   }
  }
  return try { activity.startActivity(intent); "Solicitare transmisă aplicației Android; verifică și confirmă acolo." }
  catch(e:Exception){ "Acțiunea nu a putut fi deschisă: "+e.message }
 }
}
