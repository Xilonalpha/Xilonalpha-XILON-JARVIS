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
  when(q.lowercase(java.util.Locale.ROOT)){
   "listează aplicațiile","listeaza aplicatiile","list apps" -> return call("list_apps")
   "derulează la stânga","deruleaza la stanga" -> return call("scroll","direction" to JsonPrimitive("left"))
   "derulează la dreapta","deruleaza la dreapta" -> return call("scroll","direction" to JsonPrimitive("right"))
   "înapoi","inapoi" -> return call("press_back")
   "ecran principal","mergi acasă","mergi acasa" -> return call("press_home")
   "aplicații recente","aplicatii recente" -> return call("press_recents")
   "arată notificările","arata notificarile" -> return call("press_notifications")
   "captură de ecran","captura de ecran" -> return call("take_screenshot")
   "derulează în jos","deruleaza in jos" -> return call("scroll","direction" to JsonPrimitive("down"))
   "derulează în sus","deruleaza in sus" -> return call("scroll","direction" to JsonPrimitive("up"))
  }
  // Map only supported upstream commands to existing confirmed Android tools.
  if(q.contains(Regex("\\b(and|then|aur|not|never|don.t)\\b",RegexOption.IGNORE_CASE)))return null
  return when(val cmd=com.jarvis.assistant.router.FastCommandRouter.parse(q)){
   is com.jarvis.assistant.router.FastCommand.Press -> when(cmd.key){
    "back" -> call("press_back")
    "home" -> call("press_home")
    "recents" -> call("press_recents")
    "notifications" -> call("press_notifications")
    else -> null
   }
   is com.jarvis.assistant.router.FastCommand.Scroll ->
    call("scroll","direction" to JsonPrimitive(cmd.direction))
   com.jarvis.assistant.router.FastCommand.Screenshot -> call("take_screenshot")
   is com.jarvis.assistant.router.FastCommand.OpenApp ->
    call("open_app","app" to JsonPrimitive(cmd.query))
   is com.jarvis.assistant.router.FastCommand.Flashlight ->
    cmd.on?.let{call("control_flashlight","on" to JsonPrimitive(it))}
   is com.jarvis.assistant.router.FastCommand.Volume ->
    if(cmd.change==com.jarvis.assistant.router.VolumeChange.SET)
     cmd.percent?.let{call("control_volume","value" to JsonPrimitive(it))} else null
   is com.jarvis.assistant.router.FastCommand.SetAlarm ->
    call("set_alarm","hour" to JsonPrimitive(cmd.hour),"minute" to JsonPrimitive(cmd.minute))
   is com.jarvis.assistant.router.FastCommand.SetTimer ->
    call("set_timer","seconds" to JsonPrimitive(cmd.seconds))
   is com.jarvis.assistant.router.FastCommand.WebSearch ->
    if(cmd.site=="youtube")call("youtube_search","query" to JsonPrimitive(cmd.query))
    else if(cmd.site=="google")call("launch_intent","kind" to JsonPrimitive("search"),"value" to JsonPrimitive(cmd.query)) else null
   is com.jarvis.assistant.router.FastCommand.Dial ->
    call("launch_intent","kind" to JsonPrimitive("dial"),"value" to JsonPrimitive(cmd.number))
   else -> null
  }
 }
}
