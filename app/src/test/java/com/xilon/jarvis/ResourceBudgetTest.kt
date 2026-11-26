package com.xilon.jarvis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
class ResourceBudgetTest {
 @Test fun confirmedVulkanCrashDeviceIsBlocked(){
  assertTrue(ResourceBudget.vulkanBlocked("Redmi","kunzite"))
  assertTrue(ResourceBudget.vulkanBlocked("redmi","KUNZITE"))
  assertTrue(ResourceBudget.vulkanBlocked("Xiaomi","kunzite"))
  assertTrue(ResourceBudget.vulkanBlocked("POCO","kunzite"))
  assertFalse(ResourceBudget.vulkanBlocked("Redmi","other"))
  assertFalse(ResourceBudget.vulkanBlocked("Other","kunzite"))
 }
 @Test fun inferencePolicyRequiresOptInAndBlocksAffectedDevices(){
  assertEquals(0,ResourceBudget.inferenceGpuLayers("Xiaomi","kunzite",true,8192,false,0,false))
  assertEquals(0,ResourceBudget.inferenceGpuLayers("Redmi","kunzite",true,8192,false,0,false))
  assertEquals(0,ResourceBudget.inferenceGpuLayers("Other","safe",false,8192,false,0,false))
  assertEquals(16,ResourceBudget.inferenceGpuLayers("Other","safe",true,8192,false,0,false))
  assertEquals(0,ResourceBudget.inferenceGpuLayers("Other","safe",true,8192,true,0,false))
  assertEquals(0,ResourceBudget.inferenceGpuLayers("Other","safe",true,8192,false,3,false))
 }
 @Test fun planningBoundaries(){
  assertEquals(1024,ResourceBudget.planningContext(767,false,0))
  assertEquals(2048,ResourceBudget.planningContext(768,false,0))
  assertEquals(4096,ResourceBudget.planningContext(2048,false,0))
  assertEquals(1024,ResourceBudget.planningContext(8192,true,0))
  assertEquals(2048,ResourceBudget.planningContext(8192,false,2))
  assertEquals(1024,ResourceBudget.planningContext(8192,false,6))
 }
 @Test fun quickConversationKeepsFullWorkflowForOtherTasks(){
  assertTrue(ResourceBudget.quickConversation("Salut cine esti"))
  assertTrue(ResourceBudget.quickConversation("Bună ziua, cine ești?"))
  assertTrue(ResourceBudget.quickConversation("Ce faci?"))
  assertFalse(ResourceBudget.quickConversation("Trimite un mesaj lui Olivia pe WhatsApp"))
  assertFalse(ResourceBudget.quickConversation("Scrie cod Kotlin pentru o aplicație"))
  assertFalse(ResourceBudget.quickConversation("Salut explică-mi arhitectura aplicației"))
  assertEquals(96,ResourceBudget.chatResponseTokens(1024,true))
  assertEquals(64,ResourceBudget.chatResponseTokens(512,true))
  assertEquals(384,ResourceBudget.chatResponseTokens(2048,false))
 }
 @Test fun conversationLimits(){
  assertEquals(512,ResourceBudget.conversationContext(8192,false,"shutdown"))
  assertEquals(1024,ResourceBudget.conversationContext(8192,false,"moderate"))
  assertEquals(2048,ResourceBudget.conversationContext(8192,false,"none"))
 }
 @Test fun threadLimits(){
  assertEquals(1,ResourceBudget.threads(8,false,"none",767))
  assertEquals(2,ResourceBudget.threads(8,false,"none",768))
  assertEquals(2,ResourceBudget.threads(8,false,"none",1535))
  assertEquals(4,ResourceBudget.threads(8,false,"none",1536))
  assertEquals(1,ResourceBudget.threads(1,false,"none",1024))
  assertEquals(1,ResourceBudget.threads(1,false,"moderate"))
  assertEquals(1,ResourceBudget.threads(16,false,"emergency"))
  assertEquals(4,ResourceBudget.threads(16,false,"none"))
 }
}
