package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
import kotlinx.serialization.json.*
class NaturalToolCommandsTest {
 @Test fun dictatedMessagesPreserveContentAndDistinguishSendingFromDrafting(){
  assertEquals("Ana Maria",NaturalToolCommands.whatsAppRecipient("Trimite un mesaj lui Ana Maria pe WhatsApp"))
  assertNull(NaturalToolCommands.whatsAppRecipient("Nu trimite un mesaj lui Ana pe WhatsApp"))
  val sent=NaturalToolCommands.parse("Trimite-i lui Ana Maria pe WhatsApp mesajul Ajung în zece minute!")
  assertEquals("whatsapp_send",sent?.tool)
  assertEquals(JsonPrimitive("Ana Maria"),sent?.args?.get("contact"))
  assertEquals(JsonPrimitive("Ajung în zece minute!"),sent?.args?.get("message"))
  assertEquals("whatsapp_message",NaturalToolCommands.parse("Scrie lui Ana pe WhatsApp: Bună!")?.tool)
  assertNull(NaturalToolCommands.parse("Nu trimite lui Ana pe WhatsApp: Bună"))
 }
 @Test fun savedMusicAndDictatedRecipient(){
  val message=NaturalToolCommands.parse("Trimite mesaj lui Olivia pe whatsapp spunei buna dimineața")
  assertEquals("whatsapp_send",message?.tool)
  assertEquals(JsonPrimitive("Olivia"),message?.args?.get("contact"))
  assertEquals(JsonPrimitive("buna dimineața"),message?.args?.get("message"))
  assertEquals(JsonPrimitive("local"),NaturalToolCommands.parse("Dă drumul la muzica salvată")?.args?.get("action"))
  assertEquals(JsonPrimitive("Luna"),NaturalToolCommands.parse("Pune Luna de pe telefon")?.args?.get("query"))
 }
 @Test fun arbitraryRecipientsMessagesAndMusicTitles(){
  for ((recipient,body) in listOf("Vlad Ionescu" to "Ajung la 18!", "Daria Pop" to "Trenul pleacă mâine.")){
   val action=NaturalToolCommands.parse("Spune-i lui $recipient pe WhatsApp că $body")
   assertEquals("whatsapp_send",action?.tool)
   assertEquals(JsonPrimitive(recipient),action?.args?.get("contact"))
   assertEquals(JsonPrimitive(body),action?.args?.get("message"))
  }
  val song=NaturalToolCommands.parse("Pune pe YouTube piesa Nothing Else Matters")
  assertEquals("youtube_search",song?.tool)
  assertEquals(JsonPrimitive("Nothing Else Matters"),song?.args?.get("query"))
  assertNull(NaturalToolCommands.parse("Nu spune-i lui Vlad pe WhatsApp că plec"))
 }
 @Test fun spokenClockCommandsResolveHourAndMinutes(){
  val next=NaturalToolCommands.parse("Trezește-mă la șapte și jumătate dimineața")
  assertEquals("set_alarm",next?.tool)
  assertEquals(JsonPrimitive(7),next?.args?.get("hour"))
  assertEquals(JsonPrimitive(30),next?.args?.get("minute"))
  val prior=NaturalToolCommands.parse("Pune alarma la douăsprezece fără un sfert dimineața")
  assertEquals(JsonPrimitive(23),prior?.args?.get("hour"))
  assertEquals(JsonPrimitive(45),prior?.args?.get("minute"))
  assertNull(NaturalToolCommands.parse("Nu mă trezi la șapte"))
 }
 @Test fun flashlight(){
  assertEquals(JsonPrimitive(true),NaturalToolCommands.parse("Aprinde lanterna")!!.args["on"])
  assertEquals(JsonPrimitive(false),NaturalToolCommands.parse("Stinge lanterna")!!.args["on"])
 }
 @Test fun volume(){
  assertEquals(JsonPrimitive(50),NaturalToolCommands.parse("Reglează volumul la 50%")!!.args["value"])
  assertNull(NaturalToolCommands.parse("Reglează volumul la 101%"))
 }
 @Test fun app(){assertEquals(JsonPrimitive("WhatsApp"),NaturalToolCommands.parse("Deschide WhatsApp")!!.args["app"])}
 @Test fun alarm(){assertNull(NaturalToolCommands.parse("Setează alarma la 25:00"));assertEquals(JsonPrimitive(30),NaturalToolCommands.parse("Setează alarma la 07:30")!!.args["minute"])}
 @Test fun naturalRomanianAlarmsBypassLlmPlanner(){
  val morning=NaturalToolCommands.parse("Setează alarma la ora 8 dimineață")
  assertEquals("set_alarm",morning?.tool)
  assertEquals(JsonPrimitive(8),morning?.args?.get("hour"))
  assertEquals(JsonPrimitive(0),morning?.args?.get("minute"))
  val evening=NaturalToolCommands.parse("Pune alarma la 9 seara")
  assertEquals("set_alarm",evening?.tool)
  assertEquals(JsonPrimitive(21),evening?.args?.get("hour"))
  assertEquals(JsonPrimitive(30),NaturalToolCommands.parse("Programează alarma la 7:30")?.args?.get("minute"))
  assertNull(NaturalToolCommands.parse("Setează alarma la ora 25 dimineața"))
  assertNull(NaturalToolCommands.parse("Nu seta alarma la ora 8 dimineața"))
 }
 @Test fun timer(){assertEquals(JsonPrimitive(300),NaturalToolCommands.parse("Pornește un temporizator de 5 minute")!!.args["seconds"])}
 @Test fun youtubeMusicRoutesWithoutLlmPlanning(){
  val jazz=NaturalToolCommands.parse("Deschide YouTube și pune muzica jazz")
  assertEquals("youtube_search",jazz?.tool)
  assertEquals(JsonPrimitive("jazz"),jazz?.args?.get("query"))
  assertEquals("youtube_search",NaturalToolCommands.parse("Pune jazz pe YouTube")?.tool)
  assertEquals(JsonPrimitive("jazz"),NaturalToolCommands.parse("Pune jazz pe YouTube")?.args?.get("query"))
  assertNull(NaturalToolCommands.parse("Nu deschide YouTube și pune muzica jazz"))
  assertNull(NaturalToolCommands.parse("Deschide YouTube și trimite un mesaj"))
 }
 @Test fun romanianLanguageSourcesHandlePoliteVariants(){
  assertEquals("open_app",NaturalToolCommands.parse("Te rog, deschide WhatsApp")?.tool)
  assertEquals("control_flashlight",NaturalToolCommands.parse("Poți să aprinzi lanterna")?.tool)
  assertEquals("press_back",NaturalToolCommands.parse("Jarvis, înapoi")?.tool)
 }
 @Test fun genericCapabilityRoutingRejectsMultiActionAsAppName(){
  assertEquals("open_app",NaturalToolCommands.parse("Deschide WhatsApp")?.tool)
  assertNull(NaturalToolCommands.parse("Deschide WhatsApp și trimite un mesaj"))
  assertNull(NaturalToolCommands.parse("Nu deschide WhatsApp"))
  assertEquals("control_flashlight",NaturalToolCommands.parse("Aprinde lanterna")?.tool)
  assertEquals("maps_navigate",NaturalToolCommands.parse("Navighează către București")?.tool)
 }
 @Test fun normalizedRomanianSynonymsUseExistingAndroidCapabilities(){
  assertEquals(JsonPrimitive(true),NaturalToolCommands.parse("Dă drumul la lanternă")?.args?.get("on"))
  assertEquals(JsonPrimitive(false),NaturalToolCommands.parse("Închide lanterna")?.args?.get("on"))
  assertEquals(JsonPrimitive(35),NaturalToolCommands.parse("Fixează sunetul la 35%")?.args?.get("value"))
  assertEquals("maps_navigate",NaturalToolCommands.parse("Du-mă spre Brașov")?.tool)
  assertNull(NaturalToolCommands.parse("Nu da drumul la lanternă"))
 }
 @Test fun conjunctionInArgumentsDoesNotBecomeMultipleActions(){
  assertEquals(1,RomanianLexicon.split("Caută pe YouTube jazz și blues").size)
  assertEquals(1,RomanianLexicon.split("Deschide aplicația Tom și Jerry").size)
  assertEquals(2,RomanianLexicon.split("Aprinde lanterna și deschide WhatsApp").size)
  assertEquals(2,RomanianLexicon.split("Aprinde lanterna; deschide WhatsApp").size)
  assertEquals("youtube_search",NaturalToolCommands.parse("Caută pe YouTube jazz și blues")?.tool)
 }
 @Test fun applicationNamesCanContainConjunctions(){
  assertEquals(JsonPrimitive("Tom și Jerry"),NaturalToolCommands.parse("Deschide aplicația Tom și Jerry")?.args?.get("app"))
  assertNull(NaturalToolCommands.parse("Deschide WhatsApp și trimite un mesaj"))
  assertNull(NaturalToolCommands.parse("Deschide YouTube și pune jazz apoi trimite un mesaj"))
 }
 @Test fun negativeSecondClauseMustNotExecuteFirstAction(){
  assertTrue(RomanianLexicon.negated("Deschide WhatsApp și nu trimite mesaje"))
  assertTrue(RomanianLexicon.negated("Aprinde lanterna apoi nu deschide camera"))
  assertTrue(RomanianLexicon.negated("Pornește muzica dar nu deschide YouTube"))
  assertNull(NaturalToolCommands.parse("Deschide WhatsApp și nu trimite mesaje"))
  assertNull(RomanianLexicon.compoundPlan("Aprinde lanterna și nu deschide camera"))
  assertFalse(RomanianLexicon.negated("Caută jazz și blues pe YouTube"))
 }
 @Test fun unknown(){assertNull(NaturalToolCommands.parse("Ce este inteligența artificială?"))}
 @Test fun upstreamNavigation(){
  assertEquals("press_back",NaturalToolCommands.parse("go back")!!.tool)
  assertEquals("press_home",NaturalToolCommands.parse("go home")!!.tool)
  assertEquals("press_recents",NaturalToolCommands.parse("recent apps")!!.tool)
  assertEquals("take_screenshot",NaturalToolCommands.parse("take screenshot")!!.tool)
  assertEquals(JsonPrimitive("left"),NaturalToolCommands.parse("scroll left")!!.args["direction"])
 }
 @Test fun upstreamAppAndTimer(){
  assertEquals("open_app",NaturalToolCommands.parse("open WhatsApp")!!.tool)
  assertEquals(JsonPrimitive(300),NaturalToolCommands.parse("set timer for 5 minutes")!!.args["seconds"])
 }
 @Test fun upstreamDialAndSearch(){
  assertEquals(JsonPrimitive("dial"),NaturalToolCommands.parse("dial 123456789")!!.args["kind"])
  assertEquals(JsonPrimitive("search"),NaturalToolCommands.parse("search for weather")!!.args["kind"])
 }
 @Test fun romanianNavigation(){
  assertEquals("press_back",NaturalToolCommands.parse("Înapoi")!!.tool)
  assertEquals("list_apps",NaturalToolCommands.parse("Listează aplicațiile")!!.tool)
  assertEquals(JsonPrimitive("right"),NaturalToolCommands.parse("Derulează la dreapta")!!.args["direction"])
 }
 @Test fun unsupportedCommands(){
  assertNull(NaturalToolCommands.parse("switch to online mode"))
  assertNull(NaturalToolCommands.parse("open WhatsApp and open Gmail"))
  assertNull(NaturalToolCommands.parse("do not open WhatsApp"))
 }
}
