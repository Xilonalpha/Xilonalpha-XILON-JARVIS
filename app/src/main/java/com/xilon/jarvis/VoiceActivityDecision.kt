package com.xilon.jarvis

/** Testable, offline energy VAD decision; not a neural speech classifier. */
internal class VoiceActivityDecision {
 private var noiseFloor=0.008
 private var consecutive=0
 fun accept(rms:Double):Boolean {
  if(!rms.isFinite() || rms<0.0){consecutive=0;return false}
  if(rms<noiseFloor*2.0)noiseFloor=0.98*noiseFloor+0.02*rms
  val threshold=maxOf(0.035,noiseFloor*3.5)
  consecutive=if(rms>=threshold)consecutive+1 else 0
  return consecutive>=4
 }
}
