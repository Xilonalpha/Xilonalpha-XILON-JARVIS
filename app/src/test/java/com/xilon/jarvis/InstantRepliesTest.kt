package com.xilon.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InstantRepliesTest {
 @Test fun greetEvenWithLoadedModel() {
  assertNotNull(InstantReplies.reply("Salut"))
  assertNotNull(InstantReplies.reply("Bună seara!"))
  assertNotNull(InstantReplies.reply("Salut, cine ești?"))
  assertNotNull(InstantReplies.reply("SALUT CINE ESTI"))
  assertNotNull(InstantReplies.reply("Ce faci?"))
 }
 @Test fun doNotSwallowAndroidCommandsOrOpenQuestions() {
  assertNull(InstantReplies.reply("Salutare trimite un mesaj lui Olivia pe WhatsApp"))
  assertNull(InstantReplies.reply("Salut, deschide WhatsApp"))
  assertNull(InstantReplies.reply("Explică-mi cum funcționează procesorul"))
  assertNull(InstantReplies.reply("Cine este președintele României?"))
  assertNull(InstantReplies.reply("Salut cine este Olivia"))
  assertNull(InstantReplies.reply("Salut ".repeat(100)))
 }
 @Test fun replyIsDeterministic() {
  assertEquals(InstantReplies.reply("Salut"),InstantReplies.reply(" salut! "))
 }
}
