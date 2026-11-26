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
 @Test fun streamedPunctuationDoesNotDropText(){
  val b=RealtimeSpeechBuffer(minChunkChars=4,maxChunkChars=20)
  val original="Salut! Aceasta este o propozitie. Si inca una?"
  val chunks=ArrayList<String>()
  for(c in original)chunks.addAll(b.append(c.toString()))
  chunks.addAll(b.finish())
  assertEquals(original,chunks.joinToString(" "))
 }
 @Test fun flushIsIdempotentAndResetStartsFreshTurn(){
  val b=RealtimeSpeechBuffer(minChunkChars=4)
  b.append("Primul raspuns")
  assertEquals(listOf("Primul raspuns"),b.finish())
  assertTrue(b.finish().isEmpty())
  b.reset()
  assertEquals(listOf("Al doilea"),b.append("Al doilea").let{it+b.finish()})
 }
 @Test fun noBlankSpeechAndNoNetworkRequired(){
  val b=RealtimeSpeechBuffer(minChunkChars=2)
  assertTrue(b.append("").isEmpty())
  assertTrue(b.finish().isEmpty())
 }
}
