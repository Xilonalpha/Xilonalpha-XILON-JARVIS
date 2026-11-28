package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class CreativeTextRoutingTest {
 @Test fun creativeRequestsAreNotAndroidActions(){
  for(q in listOf("Generează o poveste","Scrie o poezie","Compune un basm","Spune-mi o glumă")){
   assertTrue(q,RegistryIntent.creativeText(q))
   assertFalse(q,RegistryIntent.matches(q))
  }
 }
 @Test fun externalActionsAreNotCreativeOnly(){
  assertFalse(RegistryIntent.creativeText("Scrie o poveste și trimite pe WhatsApp"))
  assertFalse(RegistryIntent.creativeText("Deschide WhatsApp"))
 }
}
