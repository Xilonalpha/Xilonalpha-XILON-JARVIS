package com.xilon.jarvis

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandGreetingTest {
 @Test fun greetingBeforeWhatsappCommand(){
  assertEquals("trimite un mesaj lui Olivia pe whatsapp",
   CommandGreeting.stripBeforeCommand("Salutare trimite un mesaj lui Olivia pe whatsapp"))
 }
 @Test fun preserveMessageTextAndConversation(){
  assertEquals("Salutare Olivia",CommandGreeting.stripBeforeCommand("Salutare Olivia"))
  assertEquals("trimite salutare lui Olivia",CommandGreeting.stripBeforeCommand("trimite salutare lui Olivia"))
  assertEquals("Salutare, ce mai faci?",CommandGreeting.stripBeforeCommand("Salutare, ce mai faci?"))
 }
 @Test fun supportCommonGreeting(){
  assertEquals("deschide WhatsApp",CommandGreeting.stripBeforeCommand("Bună ziua, deschide WhatsApp"))
 }
}
