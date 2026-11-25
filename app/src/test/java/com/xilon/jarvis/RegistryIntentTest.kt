package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class RegistryIntentTest {
 @Test fun action(){assertTrue(RegistryIntent.matches("Deschide YouTube"))}
 @Test fun question(){assertFalse(RegistryIntent.matches("Cum deschid YouTube?"))}
 @Test fun greeting(){assertFalse(RegistryIntent.matches("Salut"))}
 @Test fun compound(){assertTrue(RegistryIntent.compound("Setează volumul la 40% și deschide YouTube"))}
 @Test fun simple(){assertFalse(RegistryIntent.compound("Deschide YouTube"))}

 @Test fun accentsAndPrefixes(){assertTrue(RegistryIntent.matches("JARVIS, te rog, OPREȘTE muzica"))}
 @Test fun allActionVerbsInCompound(){
  for(verb in listOf("arată","apelează","navighează","oprește","închide")){
   assertTrue(verb,RegistryIntent.compound("Deschide aplicația și $verb ceva"))
  }
 }
 @Test fun punctuation(){assertTrue(RegistryIntent.compound("Deschide YouTube; apoi, setează volumul"))}
 @Test fun questionWithActions(){assertFalse(RegistryIntent.compound("Cum deschid YouTube și setez volumul?"))}
 @Test fun conjunctionInObject(){assertFalse(RegistryIntent.compound("Caută muzică și filme"))}
}
