package com.xilon.jarvis
import kotlinx.serialization.json.*
import com.jarvis.mobile.core.tools.PlannedAction
object NaturalToolCommands {
 fun parse(input:String):PlannedAction? {
  val q=input.trim().trimEnd('!','?')
  fun call(name:String,vararg args:Pair<String,JsonPrimitive>)=PlannedAction(name,JsonObject(mapOf(*args)))
  fun match(pattern:String)=Regex(pattern,RegexOption.IGNORE_CASE).matchEntire(q)
  match("(?:aprinde|pornește|porneste|stinge|oprește|opreste) (?:lanterna|lanternă)")?.let{
   return call("control_flashlight","on" to JsonPrimitive(q.startsWith("aprinde",true)||q.startsWith("porn",true)))
  }
  match("(?:setează|seteaza|reglează|regleaza) volumul (?:la )?(\\d{1,3})%?")?.let{
   val v=it.groupValues[1].toInt();if(v in 0..100)return call("control_volume","value" to JsonPrimitive(v))
  }
  match("(?:deschide site-ul|deschide pagina) (.+)")?.let{return call("open_url","url" to JsonPrimitive(it.groupValues[1]))}
  match("(?:deschide|lansează|lanseaza) (?:aplicația |aplicatia )?(.+)")?.let{return call("open_app","app" to JsonPrimitive(it.groupValues[1]))}
  match("(?:caută|cauta) pe youtube (.+)")?.let{return call("youtube_search","query" to JsonPrimitive(it.groupValues[1]))}
  match("(?:navighează|navigheaza) (?:la|către|catre) (.+)")?.let{return call("maps_navigate","destination" to JsonPrimitive(it.groupValues[1]))}
  match("(?:setează|seteaza) alarma (?:la )?(\\d{1,2}):(\\d{2})")?.let{
   val h=it.groupValues[1].toInt();val m=it.groupValues[2].toInt()
   if(h in 0..23 && m in 0..59)return call("set_alarm","hour" to JsonPrimitive(h),"minute" to JsonPrimitive(m))
  }
  match("(?:setează|seteaza|pornește|porneste) (?:un )?temporizator (?:de )?(\\d{1,5}) (secunde|minut|minute)")?.let{
   val sec=it.groupValues[1].toInt()*(if(it.groupValues[2].startsWith("min",true))60 else 1)
   if(sec in 1..86400)return call("set_timer","seconds" to JsonPrimitive(sec))
  }
  match("(?:activează|activeaza|dezactivează|dezactiveaza) (wifi|wi-fi|bluetooth|nu deranja)")?.let{
   val name=when(it.groupValues[1].lowercase()){ "bluetooth"->"control_bluetooth";"nu deranja"->"toggle_dnd";else->"control_wifi"}
   return call(name,"on" to JsonPrimitive(q.startsWith("activ",true)))
  }
  match("(?:setează|seteaza|reglează|regleaza) luminozitatea (?:la )?(\\d{1,3})%")?.let{
   val v=it.groupValues[1].toInt()
   if(v in 1..100)return call("control_brightness","value" to JsonPrimitive(v))
  }

  if(q.equals("redare pauză",true)||q.equals("redare pauza",true))return call("media_play_pause")
  return null
 }
}
