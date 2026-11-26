package com.xilon.jarvis

import org.junit.Assert.*
import org.junit.Test

class RealtimeSpeechBufferTest {
 @Test fun emitsFirstSentenceWithoutWaitingForCompletion(){
  val b=RealtimeSpeechBuffer(minChunkChars=8)
  assertTrue(b.append("Salut, ").isEmpty())
  assertEquals(listOf("Salut, ce faci?"),b.append("ce faci?"))
  assertEquals(listOf("Sunt aici"),b.append(" Sunt aici").let{it+b.finish()})
 }
 @Test fun cancelDiscardsUnspokenText(){
  val b=RealtimeSpeechBuffer()
  b.append("Text care nu trebuie redat")
  b.cancel()
  assertTrue(b.finish().isEmpty())
  assertTrue(b.append(" altceva").isEmpty())
  b.reset()
  assertEquals(listOf("Acum vorbesc"),b.append("Acum vorbesc").let{it+b.finish()})
 }
 @Test fun longResponsesAreBoundedAndKeepWords(){
  val b=RealtimeSpeechBuffer(minChunkChars=5,maxChunkChars=24)
  val chunks=b.append("Acesta este un raspuns destul de lung pentru sinteza vocala.")+b.finish()
  assertTrue(chunks.size>=2)
  assertEquals("Acesta este un raspuns destul de lung pentru sinteza vocala.",chunks.joinToString(" "))
 }
 @Test fun noBlankSpeechAndNoNetworkRequired(){
  val b=RealtimeSpeechBuffer(minChunkChars=2)
  assertTrue(b.append("").isEmpty())
  assertTrue(b.finish().isEmpty())
 }
}
