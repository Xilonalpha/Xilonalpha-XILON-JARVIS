package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VisionWorkGateTest{
 @Test fun singleSlotReleasedAfterWork(){
  VisionWorkGate.leave()
  assertTrue(VisionWorkGate.tryEnter())
  assertFalse(VisionWorkGate.tryEnter())
  VisionWorkGate.leave()
  assertTrue(VisionWorkGate.tryEnter())
  VisionWorkGate.leave()
 }
}
