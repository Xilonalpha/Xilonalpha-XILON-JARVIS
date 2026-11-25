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
   "stare baterie detaliata" -> {
    val battery=context.registerReceiver(null,android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) ?: return "Date baterie indisponibile."
    val level=battery.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,-1)
    val scale=battery.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,-1)
    val temperature=battery.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE)
    val voltage=battery.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE,-1)
    return "Nivel: "+(if(level>=0&&scale>0)(level*100.0/scale).toInt().toString()+"%" else "indisponibil")+"\nTemperatură baterie: "+(if(temperature!=Int.MIN_VALUE)(temperature/10.0).toString()+" °C" else "indisponibilă")+"\nTensiune: "+(if(voltage>0)voltage.toString()+" mV" else "indisponibilă")
   }
   "stare termica" -> {
    if(android.os.Build.VERSION.SDK_INT<29)return "Starea termică necesită Android 10."
    val power=context.getSystemService(android.os.PowerManager::class.java)
    val status=when(power.currentThermalStatus){0->"normală";1->"ușoară";2->"moderată";3->"severă";4->"critică";5->"urgență";6->"oprire";else->"necunoscută"}
    return "Stare termică raportată de Android: "+status
   }
   "stare energie" -> {
    val p=context.getSystemService(android.os.PowerManager::class.java)
    return "Economisire energie: "+p.isPowerSaveMode+"\nEcran interactiv: "+p.isInteractive+"\nMod inactivitate: "+p.isDeviceIdleMode+"\nExceptare optimizare baterie JARVIS: "+p.isIgnoringBatteryOptimizations(context.packageName)
   }
   "stare audio iesire" -> {
    val audio=context.getSystemService(android.media.AudioManager::class.java)
    return audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).map{it.productName.toString()+" (tip Android "+it.type+")"}.distinct().take(30).joinToString("\n").ifBlank{"Nicio ieșire audio detectată."}
   }
   "stare aplicatie" -> {
    val manager=context.packageManager
    val info=manager.getPackageInfo(context.packageName,0)
    return "Versiune: "+info.versionName+"\nAndroid SDK: "+android.os.Build.VERSION.SDK_INT+"\nABI: "+android.os.Build.SUPPORTED_ABIS.joinToString(", ")+"\nSpațiu disponibil: "+android.os.StatFs(context.filesDir.path).availableBytes/1048576+" MiB"
   }
   else -> return null
  }
 }
}
