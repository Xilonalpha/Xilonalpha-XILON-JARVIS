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
 @Test fun includesEveryAuxiliaryCatalogCategory(){
  val required=AuxiliaryModelBootstrap.required
  assertTrue(required.containsAll(ModelCatalog.ocr))
  assertTrue(required.containsAll(ModelCatalog.whisper))
  assertTrue(required.containsAll(ModelCatalog.wake))
  assertTrue(required.containsAll(ModelCatalog.vision))
  assertTrue(required.contains(ModelCatalog.hand))
  assertTrue(required.contains(ModelCatalog.kokoro))
  assertEquals(required.size,required.map { it.file }.distinct().size)
 }
}
