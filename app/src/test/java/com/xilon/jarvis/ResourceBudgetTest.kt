package com.xilon.jarvis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
class ResourceBudgetTest {
 @Test fun confirmedVulkanCrashDeviceIsBlocked(){
  assertTrue(ResourceBudget.vulkanBlocked("Redmi","kunzite"))
  assertTrue(ResourceBudget.vulkanBlocked("redmi","KUNZITE"))
  assertFalse(ResourceBudget.vulkanBlocked("Redmi","other"))
  assertFalse(ResourceBudget.vulkanBlocked("Other","kunzite"))
 }
 @Test fun planningBoundaries(){
  assertEquals(1024,ResourceBudget.planningContext(767,false,0))
  assertEquals(2048,ResourceBudget.planningContext(768,false,0))
  assertEquals(4096,ResourceBudget.planningContext(2048,false,0))
  assertEquals(1024,ResourceBudget.planningContext(8192,true,0))
  assertEquals(2048,ResourceBudget.planningContext(8192,false,2))
  assertEquals(1024,ResourceBudget.planningContext(8192,false,6))
 }
 @Test fun conversationLimits(){
  assertEquals(512,ResourceBudget.conversationContext(8192,false,"shutdown"))
  assertEquals(1024,ResourceBudget.conversationContext(8192,false,"moderate"))
  assertEquals(2048,ResourceBudget.conversationContext(8192,false,"none"))
 }
 @Test fun threadLimits(){
  assertEquals(1,ResourceBudget.threads(1,false,"moderate"))
  assertEquals(1,ResourceBudget.threads(16,false,"emergency"))
  assertEquals(4,ResourceBudget.threads(16,false,"none"))
 }
}
