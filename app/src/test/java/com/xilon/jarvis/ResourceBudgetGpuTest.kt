package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class ResourceBudgetGpuTest {
 @Test fun gpuOffloadStopsWhenMemoryThermalOrBatteryIsConstrained(){
  assertFalse(ResourceBudget.gpuEnabled(1000,false,0,false))
  assertFalse(ResourceBudget.gpuEnabled(8192,true,0,false))
  assertFalse(ResourceBudget.gpuEnabled(8192,false,3,false))
  assertFalse(ResourceBudget.gpuEnabled(8192,false,0,true))
  assertTrue(ResourceBudget.gpuEnabled(8192,false,0,false))
 }
 @Test fun gpuLayerCountIsMonotonicWithMemoryAtNormalTemperature(){
  val sizes=listOf(1024L,2048L,3072L,4096L,8192L)
  val layers=sizes.map{ResourceBudget.gpuLayers(it,false,0,false)}
  assertEquals(layers.sorted(),layers)
 }
}
