package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class ResourceBudgetOptionalVisionTest {
 @Test fun optionalVisionStopsUnderPowerOrThermalPressure(){
  assertFalse(ResourceBudget.allowOptionalVision(800,false,0))
  assertFalse(ResourceBudget.allowOptionalVision(4096,true,0))
  assertFalse(ResourceBudget.allowOptionalVision(4096,false,3))
  assertTrue(ResourceBudget.allowOptionalVision(4096,false,0))
  assertEquals(0,ResourceBudget.maxConcurrentOptionalVision(4096,false,0,true))
 }
}
