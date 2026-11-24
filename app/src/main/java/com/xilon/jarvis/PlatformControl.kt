package com.xilon.jarvis
import android.content.Context
import android.content.Intent
import android.provider.Settings
object PlatformControl {
 fun execute(context:Context,command:String):String?=when(command.trim().lowercase(java.util.Locale.ROOT)){
  "platforme" -> "Android: control local disponibil. PC: agent neconectat. TV: configurare Cast prin Android; control direct neconectat. IoT: neconectat."
  "conectează televizorul" -> open(context,Intent(Settings.ACTION_CAST_SETTINGS),"Setările Cast")
  "conectează dispozitiv bluetooth" -> open(context,Intent(Settings.ACTION_BLUETOOTH_SETTINGS),"Setările Bluetooth")
  else -> null
 }
 private fun open(context:Context,intent:Intent,label:String):String=try{
  context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  "$label: solicitare lansată. Conectarea nu este confirmată."
 }catch(e:Exception){"$label indisponibile: "+e.javaClass.simpleName}
}
