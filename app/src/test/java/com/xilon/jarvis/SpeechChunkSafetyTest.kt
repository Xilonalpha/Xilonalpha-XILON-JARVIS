package com.xilon.jarvis
import com.jarvis.assistant.voice.SpeechChunks
import org.junit.Assert.*
import org.junit.Test
class SpeechChunkSafetyTest {
 @Test fun preservesWordsAndProgress(){
  val text="Salut. Cum te simți astăzi? Putem vorbi despre telefon."
  val chunks=SpeechChunks.split(text,12,20)
  assertTrue(chunks.all{it.isNotBlank() && it.length<=20})
  assertEquals(text,chunks.joinToString(" "))
 }
 @Test(expected=IllegalArgumentException::class) fun rejectsZeroLimit(){SpeechChunks.take("Salut",0)}
}
