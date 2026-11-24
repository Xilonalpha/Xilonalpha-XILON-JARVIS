package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
import com.jarvis.mobile.core.agent.AgentStatus
class TaskRuntimeTest {
 @Test fun invalidPlanNeverExecutes(){
  val runtime=TaskRuntime();var calls=0
  runtime.run(listOf("baterie","unknown")){calls++;"50%"}
  assertEquals(0,calls);assertEquals(AgentStatus.FAILED,runtime.status)
 }
 @Test fun validObservationCompletes(){
  val runtime=TaskRuntime()
  runtime.run(listOf("baterie")){"50%"}
  assertEquals(AgentStatus.COMPLETED,runtime.status)
 }
 @Test fun impossibleBatteryFails(){
  val runtime=TaskRuntime()
  runtime.run(listOf("baterie")){"101%"}
  assertEquals(AgentStatus.FAILED,runtime.status)
 }
}
