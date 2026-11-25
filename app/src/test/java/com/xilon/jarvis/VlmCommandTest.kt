package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class VlmCommandTest {
 @Test fun romanian(){assertNotNull(VlmCommand.parse("Ce vezi în fața ta?"));assertNotNull(VlmCommand.parse("ANALIZEAZĂ CAMERA"))}
 @Test fun question(){assertEquals("Ce culoare are obiectul?",VlmCommand.parse("Întreabă camera: Ce culoare are obiectul?"))}
 @Test fun negatives(){assertNull(VlmCommand.parse("Nu analiza camera"));assertNull(VlmCommand.parse("analizează imaginea din OCR"));assertNull(VlmCommand.parse("ce vezi și trimite mesaj"))}
}
