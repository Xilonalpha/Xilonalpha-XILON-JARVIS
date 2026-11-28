package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceProsodyTest {
 @Test fun pacing(){
  assertEquals(1f,VoiceProsody.speed("Salut."),0.001f)
  assertEquals(0.96f,VoiceProsody.speed("Ce faci?"),0.001f)
  assertEquals(0.92f,VoiceProsody.speed("Poate..."),0.001f)
  assertEquals(1.2f,VoiceProsody.speed("Salut!",2f),0.001f)
  assertEquals(1f,VoiceProsody.speed("Salut",Float.NaN),0.001f)
  assertEquals(0.98f,VoiceProsody.speed("Unu, doi, trei, patru."),0.001f)
  assertEquals(0.96f,VoiceProsody.speed("„Ce faci?”"),0.001f)
 }
}
