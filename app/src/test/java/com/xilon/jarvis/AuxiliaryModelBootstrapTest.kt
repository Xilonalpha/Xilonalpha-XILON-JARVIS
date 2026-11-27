package com.xilon.jarvis

import org.junit.Assert.*
import org.junit.Test

class AuxiliaryModelBootstrapTest {
 @Test fun excludesEveryConversationalGgufModel(){
  val required=AuxiliaryModelBootstrap.required
  assertTrue(required.isNotEmpty())
  assertTrue(required.none { it in ModelCatalog.entries })
  assertTrue(required.none { it.file.endsWith(".gguf",ignoreCase=true) })
 }
 @Test fun handModelUsesRealCatalogRevisionNotIntegrityMarker(){
  assertFalse(ModelCatalog.hand.url.contains("pinned-hand"))
  assertTrue(ModelCatalog.hand.url.contains(ModelCatalog.hand.revision))
 }
 @Test fun onlyCoreSpeechAssetsAreAutomatic(){
  val required=AuxiliaryModelBootstrap.required
  assertTrue(required.none { it in ModelCatalog.ocr })
  assertTrue(required.containsAll(ModelCatalog.whisper))
  assertTrue(required.none { it in ModelCatalog.wake })
  assertTrue(required.none { it in ModelCatalog.vision })
  assertFalse(required.contains(ModelCatalog.hand))
  assertFalse(required.contains(ModelCatalog.kokoro))
  assertTrue(required.contains(ModelCatalog.silero))
  assertEquals((ModelCatalog.whisper+ModelCatalog.silero).toSet(),required.toSet())
  assertTrue(ModelCatalog.silero.url.contains("/v6.2/"))
  assertEquals(required.size,required.map { it.file }.distinct().size)
 }
}
