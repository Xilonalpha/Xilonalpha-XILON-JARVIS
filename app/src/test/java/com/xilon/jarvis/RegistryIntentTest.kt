package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class RegistryIntentTest {
 @Test fun action(){assertTrue(RegistryIntent.matches("Deschide YouTube"))}
 @Test fun question(){assertFalse(RegistryIntent.matches("Cum deschid YouTube?"))}
 @Test fun greeting(){assertFalse(RegistryIntent.matches("Salut"))}
 @Test fun compound(){assertTrue(RegistryIntent.compound("Setează volumul la 40% și deschide YouTube"))}
 @Test fun simple(){assertFalse(RegistryIntent.compound("Deschide YouTube"))}
}
