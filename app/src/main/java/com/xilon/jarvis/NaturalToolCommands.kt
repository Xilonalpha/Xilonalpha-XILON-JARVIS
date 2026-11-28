package com.xilon.jarvis
import kotlinx.serialization.json.*
import com.jarvis.mobile.core.tools.PlannedAction
object NaturalToolCommands {
 /** Recipient-only requests need the user's message before they can enter the tool runner. */
 fun whatsAppRecipient(input:String):String? {
  if(RomanianLexicon.negated(input) || RegistryIntent.compound(input))return null
  val pattern=Regex("""^trimite(?:-i)? (?:un )?mesaj (?:lui |către |catre )?([\p{L}\p{M}\d +()'-]+?) pe whatsapp[.!?]?$""",RegexOption.IGNORE_CASE)
  return pattern.matchEntire(RomanianLexicon.canonicalCommand(input).trim())?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
 }
 fun parse(input:String):PlannedAction? = parseCanonical(RomanianLexicon.canonicalCommand(input))
 private fun parseCanonical(input:String):PlannedAction? {
  // Separate routing syntax from arbitrary dictated text. Never interpret
  // message contents as device commands or replace them with canned replies.
  val cleaned=RomanianLexicon.clean(input)
  val initial=cleaned.substringBefore(' ')
  val canonical=RomanianLexicon.messagingVerb(initial)
  val envelope=if(canonical!=null)canonical+cleaned.substring(initial.length) else cleaned
  val verbPattern="(trimite|scrie)(?:-i)?"
  val recipientPattern="(?:lui |către |catre |la )?"
  val separator="""(?:\s*[:,]\s*|\s+(?=(?:spune[- ]?i|zi[- ]?i|întreab[ăa](?:-?o|-?l)?|intreab[ao](?:-?o|-?l)?|cu textul|cu mesajul|mesajul|că|ca)\b))"""
  val first=Regex("""^$verbPattern\s+(?:un\s+)?mesaj\s+$recipientPattern(.+?)\s+(?:pe|prin)\s+whatsapp(?:\s*[:,]\s*|\s+)([\s\S]+)$""",RegexOption.IGNORE_CASE)
  val second=Regex("""^$verbPattern\s+(?:un\s+)?mesaj\s+(?:pe|prin)\s+whatsapp\s+$recipientPattern(.+?)$separator([\s\S]+)$""",RegexOption.IGNORE_CASE)
  val match=first.matchEntire(envelope)?:second.matchEntire(envelope)
  if(match!=null){
   val (verb,recipient,dictation)=match.destructured
   val body=dictation.replaceFirst(Regex("""^(?:spune[- ]?i|zi[- ]?i)(?:\s+(?:că|ca))?\s+|^(?:cu textul|cu mesajul|mesajul|că|ca)\s*:?\s+""",RegexOption.IGNORE_CASE),"").trim()
   val header=envelope.substring(0,match.groups[3]!!.range.first)
   if(!RomanianLexicon.negated(header)&&!RegistryIntent.compound(header)&&recipient.isNotBlank()&&body.isNotBlank())
    return PlannedAction(if(verb.equals("scrie",true))"whatsapp_message" else "whatsapp_send",JsonObject(mapOf("contact" to JsonPrimitive(recipient.trim()),"message" to JsonPrimitive(body))))
  }
  // Explicit payload delimiters protect literal text from command segmentation.
  val literal=Regex("""^(trimite|scrie)\s+(?:un\s+)?(?:mesaj\s+)?(?:pe\s+|prin\s+)?(sms|whatsapp)\s+(?:lui\s+|către\s+|catre\s+)?([^:]+):\s*([\s\S]+)$""",RegexOption.IGNORE_CASE).matchEntire(input)
  if(literal!=null){
   val (verb,channel,recipient,body)=literal.destructured
   val header=input.substring(0,literal.groups[4]!!.range.first)
   if(!RomanianLexicon.negated(header)&&!RegistryIntent.compound(header)&&recipient.isNotBlank()&&body.isNotBlank())
    return PlannedAction(if(channel.equals("sms",true))"send_sms" else if(verb.equals("scrie",true))"whatsapp_message" else "whatsapp_send",JsonObject(mapOf("contact" to JsonPrimitive(recipient.trim()),"message" to JsonPrimitive(body.trim()))))
  }
  // The dedicated single-intent YouTube rule must run before the generic
  // lexicon (which can mistake "deschide YouTube și..." for open_app).
  // Do not bypass the negation and compound-action safety guards.
  if(RomanianLexicon.negated(input) || RomanianLexicon.split(input).any { RomanianLexicon.negated(it) })return null
  val yt=input.trim().trimEnd('!','?','.').replace(Regex("\\s+")," ")
  val ytPrefix=Regex("^(?:deschide|pornește|porneste|lansează|lanseaza) (?:aplicația |aplicatia )?youtube (?:și|si) (?:pune|redă|reda|pornește|porneste|caută|cauta) (?:muzică |muzica |melodia |piesa |un cântec |un cantec |niște muzică |niste muzica )?(.+)$",RegexOption.IGNORE_CASE)
  val ytSuffix=Regex("^(?:pune|redă|reda|pornește|porneste) (?:muzică |muzica |melodia |piesa )?(.+) (?:pe|în|in) youtube$",RegexOption.IGNORE_CASE)
  val ytFirst=Regex("""^(?:pune|redă|reda|pornește|porneste) (?:pe|în|in) youtube (?:melodia |piesa )?(.+)$""",RegexOption.IGNORE_CASE)
  val ytQuery=(ytPrefix.matchEntire(yt) ?: ytSuffix.matchEntire(yt) ?: ytFirst.matchEntire(yt))?.groupValues?.get(1)?.trim()
  if(!ytQuery.isNullOrBlank() && !Regex("\\b(?:și|si|apoi)\\s+(?:deschide|trimite|apelează|apeleaza|șterge|sterge|cumpără|cumpara)\\b",RegexOption.IGNORE_CASE).containsMatchIn(ytQuery))
   return PlannedAction("youtube_search",JsonObject(mapOf("query" to JsonPrimitive(ytQuery),"play" to JsonPrimitive(!yt.contains(Regex("\\b(?:caută|cauta)\\b",RegexOption.IGNORE_CASE))) )))
  if(RegistryIntent.compound(input))return null
  // Direct Romanian commands with arbitrary argument values, no GGUF.
  val raw=input.trim().trimEnd('!','.')
  fun direct(name:String,vararg args:Pair<String,JsonPrimitive>)=PlannedAction(name,JsonObject(mapOf(*args)))
  fun rawMatch(pattern:String)=Regex(pattern,RegexOption.IGNORE_CASE).matchEntire(raw)
  rawMatch("(?:pune|redă|reda|pornește|porneste|dă drumul la|da drumul la|ascultă|asculta) (?:(?:muzica|muzică|melodiile)(?: salvată| salvata)? (?:de pe telefon|din telefon|locală|locala|offline)|muzic[ăa] salvat[ăa])")?.let{
   return direct("media_play_pause","action" to JsonPrimitive("local"))
  }
  rawMatch("(?:pune|redă|reda|pornește|porneste) (?:(?:melodia|piesa) )?(.+) (?:de pe telefon|din telefon|offline)")?.let{
   return direct("media_play_pause","action" to JsonPrimitive("local"),"query" to JsonPrimitive(it.groupValues[1]))
  }
  val spokenClock=Regex("""(?:trezeste-ma|(?:pune|seteaza|programeaza) (?:o )?alarma) la (?:ora )?(\d{1,2}|unu|una|doi|doua|trei|patru|cinci|sase|sapte|opt|noua|zece|unsprezece|doisprezece|douasprezece)(?: (si un sfert|si jumatate|fara un sfert))?(?: (dimineata|seara|dupa[ -]amiaza))?""")
   .matchEntire(RomanianLexicon.normalize(raw))
  if(spokenClock!=null){
   val (word,fraction,period)=spokenClock.destructured
   val names=listOf("zero","unu","doi","trei","patru","cinci","sase","sapte","opt","noua","zece","unsprezece","doisprezece")
   var hour=word.toIntOrNull() ?: when(word){"una"->1;"doua"->2;"douasprezece"->12;else->names.indexOf(word)}
   if(hour !in 0..23 || period.isNotEmpty() && hour !in 1..12)return null
   if(period.isNotEmpty())hour=hour%12 + if(period=="dimineata")0 else 12
   if(fraction=="fara un sfert")hour=(hour+23)%24
   val minute=when(fraction){"si un sfert"->15;"si jumatate"->30;"fara un sfert"->45;else->0}
   return direct("set_alarm","hour" to JsonPrimitive(hour),"minute" to JsonPrimitive(minute))
  }
  rawMatch("(?:sună|suna|apelează|apeleaza|telefonează|telefoneaza) (?:pe |la )?(.+)")?.let{
   return direct("call_contact","contact" to JsonPrimitive(it.groupValues[1]))
  }
  val dictatedWhatsApp=Regex("""^(trimite|scrie|spune)(?:[- ]i)? (?:un )?(?:mesaj )?(?:lui |către |catre )?(.+?) pe whatsapp(?:\s*:\s*|\s+(?:cu (?:textul|mesajul)|mesajul|spune[- ]?i|că|ca)\s*:?\s+)(.+)$""",RegexOption.IGNORE_CASE).matchEntire(input.trim())
  if(dictatedWhatsApp!=null){
   val (verb,recipient,body)=dictatedWhatsApp.destructured
   return direct(if(verb.equals("scrie",true)) "whatsapp_message" else "whatsapp_send",
    "contact" to JsonPrimitive(recipient.trim()), "message" to JsonPrimitive(body.trim()))
  }
  Regex("(?:scrie|trimite) (?:un )?(?:mesaj (?:pe |prin )?)?(whatsapp|sms) (?:lui |către |catre )?([^:]+):\\s*(.+)",RegexOption.IGNORE_CASE).matchEntire(input.trim())?.let{
   return direct(if(it.groupValues[1].equals("sms",true))"send_sms" else if(raw.startsWith("trimite",true))"whatsapp_send" else "whatsapp_message","contact" to JsonPrimitive(it.groupValues[2].trim()),"message" to JsonPrimitive(it.groupValues[3]))
  }
  rawMatch("(?:deschide|arată|arata) (?:conversația|conversatia|chatul) (?:pe |din )?telegram (?:cu )?(.+)")?.let{
   return direct("telegram_message","handle" to JsonPrimitive(it.groupValues[1]))
  }
  rawMatch("(?:caută|cauta|găsește|gaseste) (?:fișierul|fisierul|documentul) (.+)")?.let{
   return direct("find_file","name" to JsonPrimitive(it.groupValues[1]))
  }
  rawMatch("(?:caută|cauta) (?:pe google|pe internet) (.+)")?.let{
   return direct("launch_intent","kind" to JsonPrimitive("search"),"value" to JsonPrimitive(it.groupValues[1]))
  }
  rawMatch("(?:scrie|tastează|tasteaza) (?:textul )?[:]\\s*(.+)")?.let{
   return direct("type_text","text" to JsonPrimitive(it.groupValues[1]))
  }
  rawMatch("(?:trimite|scrie) (?:un )?(?:email|e-mail) (?:către |catre |la )?([^\\s:]+@[^\\s:]+):\\s*(.+)")?.let{
   return direct("send_email","to" to JsonPrimitive(it.groupValues[1]),"body" to JsonPrimitive(it.groupValues[2]))
  }
  rawMatch("(?:distribuie|partajează|partajeaza) (?:textul )?:\\s*(.+)")?.let{
   return direct("share_content","text" to JsonPrimitive(it.groupValues[1]))
  }
  rawMatch("(?:găsește|gaseste|identifică|identifica) (?:butonul|elementul) (.+)")?.let{
   return direct("find_element","text" to JsonPrimitive(it.groupValues[1]))
  }
  rawMatch("(?:ține|tine) apăsat (?:pe )?(.+)")?.let{
   return direct("long_press","text" to JsonPrimitive(it.groupValues[1]))
  }
  rawMatch("(?:apasă|apasa) de două ori (?:pe )?(.+)")?.let{
   return direct("double_tap","text" to JsonPrimitive(it.groupValues[1]))
  }
  rawMatch("(?:pune|setează|seteaza|pornește|porneste) (?:un )?(?:cronometru|timer|temporizator) (?:de |pentru )?(\\d{1,5}) (ore|oră|ora|minute|minut|secunde|secundă|secunda)")?.let{
   val unit=RomanianLexicon.normalize(it.groupValues[2])
   val seconds=it.groupValues[1].toLong()*when(unit){"ore","ora"->3600;"minute","minut"->60;else->1}
   if(seconds in 1..86400)return direct("set_timer","seconds" to JsonPrimitive(seconds.toInt()))
   return null
  }
  rawMatch("(?:arată|arata|citește|citeste) calendarul (?:pentru )?(?:următoarele |urmatoarele )?(\\d{1,2}) zile")?.let{
   val days=it.groupValues[1].toInt()
   if(days in 1..30)return direct("read_calendar","days" to JsonPrimitive(days))
   return null
  }
  rawMatch("(?:arată|arata|caută|cauta) pe hartă (.+)")?.let{
   return direct("launch_intent","kind" to JsonPrimitive("map"),"value" to JsonPrimitive(it.groupValues[1]))
  }
  rawMatch("(?:apasă|apasa|atinge) (?:butonul )?(.+)")?.let{
   return direct("tap","text" to JsonPrimitive(it.groupValues[1]))
  }
  rawMatch("(?:mărește|mareste|crește|creste|ridică|ridica|scade|redu|micșorează|micsoreaza|oprește|opreste) (?:volumul|sunetul)")?.let{
   val v=RomanianLexicon.normalize(raw).substringBefore(' ')
   return direct("control_volume","direction" to JsonPrimitive(when(v){"opreste"->"mute";"scade","redu","micsoreaza"->"down";else->"up"}))
  }
  when(RomanianLexicon.normalize(raw).trimEnd('?')){
   "schimba melodia","urmatoarea melodie","urmatoarea piesa","treci la urmatoarea melodie" -> return direct("media_play_pause","action" to JsonPrimitive("next"))
   "melodia anterioara","piesa anterioara" -> return direct("media_play_pause","action" to JsonPrimitive("previous"))
   "opreste muzica","pune muzica pe pauza","pauza muzica","opreste redarea" -> return direct("media_play_pause","action" to JsonPrimitive("pause"))
   "continua muzica","reia muzica","reia redarea","continua redarea" -> return direct("media_play_pause","action" to JsonPrimitive("play"))
   "unde sunt","arata locatia mea","locatia mea" -> return direct("get_location")
   "ce am in calendar","arata calendarul","citeste calendarul","ce am de facut azi" -> return direct("read_calendar","days" to JsonPrimitive(1))
   "citeste notificarile","ce notificari am" -> return direct("read_notifications")
   "citeste ecranul","ce scrie pe ecran","ce este pe ecran" -> return direct("read_screen")
   "inchide aplicatia","iesi din aplicatie" -> return direct("close_app")
   "sterge textul","goleste campul" -> return direct("clear_text")
   "copiaza textul" -> return direct("copy_text")
   "lipeste textul" -> return direct("paste_text")
   "fa o captura de ecran","fa screenshot" -> return direct("take_screenshot")
   "arata aplicatiile recente","deschide aplicatiile recente" -> return direct("press_recents")
   "mergi inapoi","da inapoi" -> return direct("press_back")
  }
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
   // An app title can contain conjunctions (e.g. "Tom și Jerry").
   // The shared Romanian clause splitter decides whether another verb
   // introduces a separate action, rather than rejecting all conjunctions.
   if(RomanianLexicon.split(q).size==1)
    return call("open_app","app" to JsonPrimitive(app))
  }
  // The single-intent YouTube forms are handled once, above the lexicon.
  match("(?:caută|cauta) pe youtube (.+)")?.let{return call("youtube_search","query" to JsonPrimitive(it.groupValues[1]))}
  match("(?:navighează|navigheaza|du-mă|du-ma|condu-mă|condu-ma) (?:la|către|catre|spre) (.+)")?.let{return call("maps_navigate","destination" to JsonPrimitive(it.groupValues[1]))}
  // Romanian natural alarm times must bypass GGUF planning.
  foldedMatch("(?:seteaza|pune|programeaza) (?:o |un )?alarma (?:la )?(?:ora )?(\\d{1,2})(?::(\\d{2}))? ?(dimineata|seara|dupa-amiaza|dupa amiaza)?")?.let{
   var h=it.groupValues[1].toInt()
   val m=it.groupValues[2].ifBlank{"0"}.toInt()
   val period=it.groupValues[3]
   if(period=="seara" || period.startsWith("dupa")){if(h in 1..11)h+=12}
   if(period=="dimineata" && h==12)h=0
   if(h in 0..23 && m in 0..59)
    return call("set_alarm","hour" to JsonPrimitive(h),"minute" to JsonPrimitive(m))
  }
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
