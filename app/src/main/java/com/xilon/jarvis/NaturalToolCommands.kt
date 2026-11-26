package com.xilon.jarvis
import kotlinx.serialization.json.*
import com.jarvis.mobile.core.tools.PlannedAction
object NaturalToolCommands {
 fun parse(input:String):PlannedAction? {
  // The dedicated single-intent YouTube rule must run before the generic
  // lexicon (which can mistake "deschide YouTube și..." for open_app).
  // Do not bypass the negation and compound-action safety guards.
  if(RomanianLexicon.negated(input))return null
  val yt=input.trim().trimEnd('!','?','.').replace(Regex("\\s+")," ")
  val ytPrefix=Regex("^(?:deschide|pornește|porneste|lansează|lanseaza) (?:aplicația |aplicatia )?youtube (?:și|si) (?:pune|redă|reda|pornește|porneste|caută|cauta) (?:muzică |muzica |melodia |piesa |un cântec |un cantec |niște muzică |niste muzica )?(.+)$",RegexOption.IGNORE_CASE)
  val ytSuffix=Regex("^(?:pune|redă|reda|pornește|porneste) (?:muzică |muzica |melodia |piesa )?(.+) (?:pe|în|in) youtube$",RegexOption.IGNORE_CASE)
  val ytQuery=(ytPrefix.matchEntire(yt) ?: ytSuffix.matchEntire(yt))?.groupValues?.get(1)?.trim()
  if(!ytQuery.isNullOrBlank() && !Regex("\\b(?:și|si|apoi)\\s+(?:deschide|trimite|apelează|apeleaza|șterge|sterge|cumpără|cumpara)\\b",RegexOption.IGNORE_CASE).containsMatchIn(ytQuery))
   return PlannedAction("youtube_search",JsonObject(mapOf("query" to JsonPrimitive(ytQuery))))
  if(RegistryIntent.compound(input))return null
  // A lexicon rule is accepted only for a single intent. A compound request
  // needs a multi-step plan with per-action verification.
  RomanianLexicon.parse(input)?.let { return it }
  // Reuse the Romanian normalization and conjugation tables for all
  // deterministic tools, not only exact JSON phrases. Keep original values
  // (contact names, app names, search text) where arguments are extracted.
  val q=RomanianLexicon.canonicalCommand(input).trim().trimEnd('!','?')
  val folded=RomanianLexicon.normalize(q)
  fun foldedMatch(pattern:String)=Regex(pattern,RegexOption.IGNORE_CASE).matchEntire(folded)
  fun call(name:String,vararg args:Pair<String,JsonPrimitive>)=PlannedAction(name,JsonObject(mapOf(*args)))
  fun match(pattern:String)=Regex(pattern,RegexOption.IGNORE_CASE).matchEntire(q)
  foldedMatch("(?:aprinde|porneste|activeaza|da drumul la|stinge|opreste|dezactiveaza|inchide) (?:lanterna|blitul|blit|lanterna telefonului)")?.let{
   val verb=folded.substringBefore(' ')
   return call("control_flashlight","on" to JsonPrimitive(verb in setOf("aprinde","porneste","activeaza","da")))
  }
  foldedMatch("(?:seteaza|regleaza|pune|fixeaza) (?:volumul|sunetul) (?:la )?(\\d{1,3})%?")?.let{
   val v=it.groupValues[1].toInt();if(v in 0..100)return call("control_volume","value" to JsonPrimitive(v))
  }
  match("(?:deschide site-ul|deschide pagina) (.+)")?.let{return call("open_url","url" to JsonPrimitive(it.groupValues[1]))}
  match("(?:deschide|lansează|lanseaza) (?:aplicația |aplicatia )?(.+)")?.let{
   val app=it.groupValues[1].trim()
   // Reject free-form clauses; do not pass them as a fake app name.
   if(!Regex("\\b(?:și|si|apoi|iar|după aceea|dupa aceea)\\b",RegexOption.IGNORE_CASE).containsMatchIn(app))
    return call("open_app","app" to JsonPrimitive(app))
  }
  // The single-intent YouTube forms are handled once, above the lexicon.
  match("(?:caută|cauta) pe youtube (.+)")?.let{return call("youtube_search","query" to JsonPrimitive(it.groupValues[1]))}
  match("(?:navighează|navigheaza|du-mă|du-ma|condu-mă|condu-ma) (?:la|către|catre|spre) (.+)")?.let{return call("maps_navigate","destination" to JsonPrimitive(it.groupValues[1]))}
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
