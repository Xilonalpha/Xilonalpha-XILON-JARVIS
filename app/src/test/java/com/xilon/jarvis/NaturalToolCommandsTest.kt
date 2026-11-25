package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
import kotlinx.serialization.json.*
class NaturalToolCommandsTest {
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
 @Test fun timer(){assertEquals(JsonPrimitive(300),NaturalToolCommands.parse("Pornește un temporizator de 5 minute")!!.args["seconds"])}
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
