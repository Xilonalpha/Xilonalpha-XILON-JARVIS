package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceRetryPolicyTest {
 @Test fun boundedBackoffAndReset(){
  val p=VoiceRetryPolicy();p.failed(1000)
  assertFalse(p.ready(1499));assertTrue(p.ready(1500))
  p.failed(1500);assertFalse(p.ready(2499));assertTrue(p.ready(2500))
  repeat(10){p.failed(3000)}
  assertFalse(p.ready(10999));assertTrue(p.ready(11000))
  p.reset();assertTrue(p.ready(0))
 }
}
