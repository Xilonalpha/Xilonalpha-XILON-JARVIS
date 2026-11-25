package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class OptionalVisionBudgetTest {
 @Test fun pausesUnderPressure(){
  assertFalse(ResourceBudget.allowOptionalVision(511,false,0))
  assertFalse(ResourceBudget.allowOptionalVision(4096,true,0))
  assertFalse(ResourceBudget.allowOptionalVision(4096,false,4))
  assertTrue(ResourceBudget.allowOptionalVision(512,false,3))
 }
}
