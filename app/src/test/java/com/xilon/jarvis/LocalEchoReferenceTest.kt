package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class LocalEchoReferenceTest {
 @Test fun subtractsMatchingReference(){
  LocalEchoReference.clear()
  val signal=FloatArray(1600){kotlin.math.sin(it*0.13).toFloat()*0.3f}
  LocalEchoReference.offer(signal,0,signal.size,16000)
  val pcm=ShortArray(signal.size){(signal[it]*16384).toInt().toShort()}
  assertTrue(LocalEchoReference.suppress(pcm,pcm.size))
  assertTrue(pcm.all{kotlin.math.abs(it.toInt())<3})
  LocalEchoReference.clear()
  assertFalse(LocalEchoReference.suppress(pcm,pcm.size))
 }
}
