package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class OmegaRecorderTest {
 @Test fun recordsOnlyVerifiedUniqueSteps(){
  val r=OmegaRecorder()
  r.start()
  r.observe("baterie","101%")
  r.observe(" BATERIE ","50%")
  r.observe("baterie","60%")
  r.observe("unknown","ok")
  r.observe("stocare","100 MiB disponibili")
  assertEquals(listOf("baterie","stocare"),r.snapshot())
  assertEquals(r.snapshot(),r.finish())
 }
 @Test(expected=IllegalStateException::class)
 fun cancelledRecordingCannotBeSaved(){
  val r=OmegaRecorder();r.start();r.cancel();r.snapshot()
 }
}
