package com.xilon.jarvis
import com.jarvis.mobile.core.agent.AgentStatus
import com.jarvis.mobile.core.agent.AgentStateMachine
class TaskRuntime {
 data class StepResult(val command:String,val observation:String?,val verified:Boolean,val elapsedMs:Long)
 private val observations=mutableListOf<StepResult>()
 val steps:List<StepResult> get()=observations.toList()
 var status=AgentStatus.IDLE
  private set
 private fun move(next:AgentStatus){
  check(AgentStateMachine.isLegal(status,next)){"Invalid transition: $status -> $next"}
  status=next
 }
 fun run(commands:List<String>,execute:(String)->String?):String{
  move(AgentStatus.THINKING)
  val normalized=commands.map{it.trim().lowercase(java.util.Locale.ROOT)}
  if(commands.size !in 1..5 || normalized.distinct().size!=normalized.size || normalized.any{!ToolCatalog.permits(it)}){
   move(AgentStatus.FAILED);return "Plan respins înainte de execuție."
  }
  val results=mutableListOf<String>()
  move(AgentStatus.ACTING)
  for(command in normalized){
   val started=android.os.SystemClock.elapsedRealtime()
   val answer=try{execute(command)}catch(e:Exception){null}
   move(AgentStatus.VERIFYING)
   val verified=answer!=null && ObservationVerifier.valid(command,answer)
   observations.add(StepResult(command,answer,verified,android.os.SystemClock.elapsedRealtime()-started))
   if(!verified){
    move(AgentStatus.FAILED)
    return (results+"Necunoscut sau eșuat: $command").joinToString("\n")
   }
   results.add("$command: $answer")
   move(AgentStatus.ACTING)
  }
  move(AgentStatus.COMPLETED)
  return results.joinToString("\n")
 }
}
