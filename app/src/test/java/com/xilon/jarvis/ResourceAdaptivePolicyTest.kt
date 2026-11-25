package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class ResourceAdaptivePolicyTest {
 @Test fun optionalVisionIsBounded(){
  assertEquals(0,ResourceBudget.maxConcurrentOptionalVision(8192,false,3,false))
  assertEquals(0,ResourceBudget.maxConcurrentOptionalVision(8192,false,0,true))
  assertEquals(0,ResourceBudget.maxConcurrentOptionalVision(900,false,0,false))
  assertEquals(1,ResourceBudget.maxConcurrentOptionalVision(2048,false,0,false))
 }
 @Test fun batchSizeRespondsToPressure(){
  assertEquals(1,ResourceBudget.adaptiveBatchSize(512,false,0))
  assertEquals(2,ResourceBudget.adaptiveBatchSize(2048,false,0))
  assertEquals(4,ResourceBudget.adaptiveBatchSize(8192,false,0))
  assertEquals(1,ResourceBudget.adaptiveBatchSize(8192,true,0))
 }
}
