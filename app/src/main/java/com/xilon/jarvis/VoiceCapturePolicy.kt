package com.xilon.jarvis
/** Pure coordination rules shared by the audio host and JVM tests. */
internal object VoiceCapturePolicy {
 fun mayStartNormalAsr(
  foreground:Boolean,closing:Boolean,busy:Boolean,
  sherpaCapturing:Boolean,interruptCapturing:Boolean,wakeActive:Boolean
 ):Boolean=foreground && !closing && !busy && !sherpaCapturing && !interruptCapturing && !wakeActive
 fun mayMonitorPlayback(
  foreground:Boolean,continuous:Boolean,interruptEnabled:Boolean,
  playing:Boolean,sherpaCapturing:Boolean,interruptCapturing:Boolean
 ):Boolean=foreground && continuous && interruptEnabled && playing &&
  !sherpaCapturing && !interruptCapturing
}
