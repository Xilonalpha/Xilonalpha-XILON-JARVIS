package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceTextNormalizerTest {
 @Test fun romanianText(){
  assertEquals("jarvis opreste conversatia",VoiceTextNormalizer.normalize("JARVIS, oprește conversația!"))
 }
 @Test fun phraseBoundaries(){
  assertTrue(VoiceTextNormalizer.containsPhrase("jarvis opreste acum","jarvis opreste"))
  assertFalse(VoiceTextNormalizer.containsPhrase("jarvis oprestete","jarvis opreste"))
 }
}
