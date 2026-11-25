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
}
