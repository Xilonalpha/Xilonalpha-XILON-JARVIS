package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class TaskRuntimeEvidenceTest {
 @Test fun verifiedPrefix(){
  val runtime=TaskRuntime{0L}
  runtime.run(listOf("baterie","stocare","ram")){if(it=="baterie")"50%" else null}
  assertEquals("FAILED",runtime.status.name)
  assertEquals(listOf(true,false),runtime.steps.map{it.verified})
 }
 @Test fun rejectsUnknown(){
  val runtime=TaskRuntime{0L}
  runtime.run(listOf("necunoscut")){error("Must not execute")}
  assertEquals("FAILED",runtime.status.name)
  assertTrue(runtime.steps.isEmpty())
 }
 @Test fun cancellation(){
  val runtime=TaskRuntime{0L}
  runtime.run(listOf("baterie"),{true}){error("Must not execute")}
  assertEquals("STOPPED",runtime.status.name)
  assertTrue(runtime.steps.isEmpty())
 }
 @Test fun reuse(){
  val runtime=TaskRuntime{0L}
  runtime.run(listOf("baterie")){"50%"}
  runtime.run(listOf("baterie")){"60%"}
  assertEquals("COMPLETED",runtime.status.name)
  assertEquals(1,runtime.steps.size)
  assertEquals("60%",runtime.steps.single().observation)
 }
}
