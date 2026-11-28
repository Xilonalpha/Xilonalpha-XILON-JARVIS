package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class VoiceEchoGateTest {
 @Test fun explicitStopOnly(){val g=VoiceEchoGate();g.remember("Salut, cum te pot ajuta?");assertTrue(g.isStop("Jarvis, oprește!"));assertFalse(g.isStop("deschide aplicația"));assertFalse(g.isStop("salut"))}
 @Test fun rejectsOwnSpeech(){val g=VoiceEchoGate();g.remember("Spune Jarvis oprește pentru a termina.");assertFalse(g.isStop("Jarvis oprește"))}
 @Test fun retainsEarlierChunksWithinBoundedWindow(){
  var now=100L
  val g=VoiceEchoGate(echoWindowMs=1000L,clock={now})
  g.remember("Aceasta este prima propoziție vorbită.")
  g.remember("Aceasta este a doua propoziție vorbită.")
  assertTrue(g.isEcho("Aceasta este prima propoziție vorbită."))
  assertTrue(g.isEcho("Aceasta este a doua propoziție vorbită."))
  now=1200L
  assertFalse(g.isEcho("Aceasta este prima propoziție vorbită."))
 }
 @Test fun echoAcrossStreamingChunkBoundary(){
  val g=VoiceEchoGate()
  g.remember("Pentru a termina spune Jarvis")
  g.remember("oprește iar apoi poți continua")
  assertTrue(g.isEcho("spune Jarvis oprește iar apoi"))
  assertFalse(g.isStop("Jarvis oprește"))
 }
 @Test fun unrelatedStopStillWorksAfterStreaming(){
  val g=VoiceEchoGate()
  g.remember("Vremea de astăzi este frumoasă")
  g.remember("Poți ieși la plimbare")
  assertTrue(g.isStop("Jarvis oprește"))
 }
 @Test fun shortGenericWordsAreNotClassifiedAsEcho(){
  val g=VoiceEchoGate()
  g.remember("Am deschis camera si poti continua.")
  assertFalse(g.isEcho("camera"))
  assertFalse(g.isEcho("continua"))
 }
 @Test fun staleSpeechIsNotEchoAfterClockRollback(){
  var now=1000L
  val g=VoiceEchoGate(echoWindowMs=1000L,clock={now})
  g.remember("Jarvis opreste")
  now=10L
  assertFalse(g.isEcho("Jarvis opreste"))
  assertTrue(g.isStop("Jarvis opreste"))
 }
 @Test fun echoHistoryIsBounded(){
  val g=VoiceEchoGate()
  repeat(20){g.remember("Fraza de test foarte lunga numarul $it")}
  assertFalse(g.isEcho("Fraza de test foarte lunga numarul 0"))
  assertTrue(g.isEcho("Fraza de test foarte lunga numarul 19"))
 }
 @Test fun longTextIsBoundedAndStillMatchesShortPhrases(){
  val g=VoiceEchoGate()
  g.remember("jarvis "+("cuvant ".repeat(200)))
  assertTrue(g.isEcho("cuvant cuvant cuvant"))
 }
 @Test fun stopDoesNotMatchWordFragments(){
  val gate=VoiceEchoGate();gate.remember("Jarvis oprestete acum")
  assertTrue(gate.isStop("Jarvis opreste"))
 }
 @Test fun punctuationDoesNotDefeatEchoBoundary(){
  val g=VoiceEchoGate()
  g.remember("Jarvis, poți spune oprește acum.")
  assertTrue(g.isEcho("poti spune opreste"))
 }
 @Test fun expandedStopCommands(){val g=VoiceEchoGate();assertTrue(g.isStop("Jarvis, liniște!"));assertTrue(g.isStop("Jarvis, întrerupe"));assertFalse(g.isStop("Jarvis nu opri"));g.remember("Jarvis pauză");assertFalse(g.isStop("Jarvis pauză"))}
}
