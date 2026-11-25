package com.xilon.jarvis
import org.junit.Assert.assertEquals
import org.junit.Test
class VisionResourceBudgetTest {
 @Test fun normal() { assertEquals(3000L,ResourceBudget.visionInterval(2048,false,0,false)) }
 @Test fun powerSave() { assertEquals(6000L,ResourceBudget.visionInterval(2048,false,0,true)) }
 @Test fun thermalPriority() { assertEquals(10000L,ResourceBudget.visionInterval(2048,false,3,true)) }
 @Test fun lowMemory() { assertEquals(10000L,ResourceBudget.visionInterval(256,true,0,false)) }
 @Test fun constrainedMemory() { assertEquals(6000L,ResourceBudget.visionInterval(512,false,0,false)) }
}
