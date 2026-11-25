package com.xilon.jarvis

import android.content.Context

internal object DeviceReports {
 fun execute(context:Context,command:String):String? {
  when(command){
   "stare multimedia" -> {
     val audio=context.getSystemService(android.media.AudioManager::class.java)
     val streams=listOf("Muzică" to android.media.AudioManager.STREAM_MUSIC,"Alarmă" to android.media.AudioManager.STREAM_ALARM,"Sonerie" to android.media.AudioManager.STREAM_RING)
     return (streams.joinToString("\n"){(label,stream)->label+": "+audio.getStreamVolume(stream)+"/"+audio.getStreamMaxVolume(stream)}+"\nRedare muzicală activă: "+audio.isMusicActive)
   }
   "stare senzori" -> {
     val sensors=context.getSystemService(android.hardware.SensorManager::class.java).getSensorList(android.hardware.Sensor.TYPE_ALL)
     return (sensors.distinctBy{it.type}.sortedBy{it.type}.take(40).joinToString("\n"){it.name+" — "+it.vendor}.ifBlank{"Niciun senzor disponibil."})
   }
   "stare afisaj" -> {
     val metrics=context.resources.displayMetrics
     return ("Rezoluție raportată aplicației: "+metrics.widthPixels+" × "+metrics.heightPixels+" px\nDensitate: "+metrics.densityDpi+" dpi\nOrientare: "+(if(context.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)"orizontală" else "verticală"))
   }
   "stare conexiuni" -> {
     val cm=context.getSystemService(android.net.ConnectivityManager::class.java)
     val caps=cm.activeNetwork?.let{cm.getNetworkCapabilities(it)}
     val transports=listOf("Wi-Fi" to android.net.NetworkCapabilities.TRANSPORT_WIFI,"Mobil" to android.net.NetworkCapabilities.TRANSPORT_CELLULAR,"Ethernet" to android.net.NetworkCapabilities.TRANSPORT_ETHERNET,"VPN" to android.net.NetworkCapabilities.TRANSPORT_VPN)
     return ("Transport activ: "+transports.filter{caps?.hasTransport(it.second)==true}.joinToString(", "){it.first}.ifBlank{"indisponibil"}+"\nInternet validat: "+(caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true))
   }
   else -> return null
  }
 }
}
