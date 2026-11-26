package com.xilon.jarvis

import org.junit.Assert.*
import org.junit.Test

class RealtimeVoiceTurnTest {
 @Test fun streamsSentencesBeforeCompletion(){
  val turn=RealtimeVoiceTurn(minChunkChars=5)
  val id=turn.start()
  assertTrue(turn.offer(id,"Salut, cum esti? Restul"))
  assertEquals("Salut, cum esti?",turn.poll(id))
  assertNull(turn.poll(id))
  assertTrue(turn.finish(id))
  assertEquals("Restul",turn.poll(id))
 }
 @Test fun interruptDropsOldAudioAndLateTokens(){
  val turn=RealtimeVoiceTurn(minChunkChars=4)
  val old=turn.start()
  turn.offer(old,"Acesta este un raspuns.")
  turn.interrupt()
  assertNull(turn.poll(old))
  assertFalse(turn.offer(old,"Prea tarziu"))
  val next=turn.start()
  assertNotEquals(old,next)
  assertTrue(turn.offer(next,"Raspuns nou."))
  assertEquals("Raspuns nou.",turn.poll(next))
 }
 @Test fun boundedQueueRejectsProducerUntilDrained(){
  val turn=RealtimeVoiceTurn(minChunkChars=4,maxQueuedChunks=1)
  val id=turn.start()
  assertTrue(turn.offer(id,"Prima."))
  assertFalse(turn.offer(id,"A doua."))
  assertEquals("Prima.",turn.poll(id))
  assertTrue(turn.offer(id,"A doua."))
  assertEquals("A doua.",turn.poll(id))
 }
 @Test fun oversizedDeltaCanBeRetriedWithoutLosingBufferedSpeech(){
  val turn=RealtimeVoiceTurn(minChunkChars=4,maxChunkChars=20,maxQueuedChunks=1)
  val id=turn.start()
  assertTrue(turn.offer(id,"Salut "))
  assertFalse(turn.offer(id,"a".repeat(21)))
  assertTrue(turn.isCurrent(id))
  assertTrue(turn.offer(id,"prietene."))
  assertEquals("Salut prietene.",turn.poll(id))
 }
 @Test fun resetInvalidatesPreviousGeneration(){
  val turn=RealtimeVoiceTurn(minChunkChars=4)
  val first=turn.start()
  val second=turn.start()
  assertFalse(turn.isCurrent(first))
  assertFalse(turn.finish(first))
  assertTrue(turn.isCurrent(second))
 }
}
