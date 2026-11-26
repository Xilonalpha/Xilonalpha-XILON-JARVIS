package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceCapturePolicyTest {
 @Test fun normalAsrRequiresExclusiveMicrophone(){
  assertTrue(VoiceCapturePolicy.mayStartNormalAsr(true,false,false,false,false,false))
  assertFalse(VoiceCapturePolicy.mayStartNormalAsr(true,false,false,true,false,false))
  assertFalse(VoiceCapturePolicy.mayStartNormalAsr(true,false,false,false,true,false))
  assertFalse(VoiceCapturePolicy.mayStartNormalAsr(true,false,true,false,false,false))
  assertFalse(VoiceCapturePolicy.mayStartNormalAsr(false,false,false,false,false,false))
  assertFalse(VoiceCapturePolicy.mayStartNormalAsr(true,true,false,false,false,false))
 }
 @Test fun playbackMonitorOnlyWhenInterruptIsEnabled(){
  assertTrue(VoiceCapturePolicy.mayMonitorPlayback(true,true,true,true,false,false))
  assertFalse(VoiceCapturePolicy.mayMonitorPlayback(true,true,true,true,true,false))
  assertFalse(VoiceCapturePolicy.mayMonitorPlayback(true,true,true,true,false,true))
  assertFalse(VoiceCapturePolicy.mayMonitorPlayback(true,true,false,true,false,false))
  assertFalse(VoiceCapturePolicy.mayMonitorPlayback(true,true,true,false,false,false))
 }
}
