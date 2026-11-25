package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class GpuBudgetTest {
 @Test fun conservativeOffload(){assertEquals(0,ResourceBudget.gpuLayers(1000,false,0,false));assertEquals(0,ResourceBudget.gpuLayers(8000,false,3,false));assertEquals(0,ResourceBudget.gpuLayers(8000,false,0,true));assertEquals(4,ResourceBudget.gpuLayers(2000,false,0,false));assertEquals(16,ResourceBudget.gpuLayers(5000,false,0,false))}
}
