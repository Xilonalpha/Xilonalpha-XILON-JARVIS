package com.xilon.jarvis
import com.jarvis.mobile.core.agent.AgentStatus
import com.jarvis.mobile.core.agent.AgentStateMachine
class TaskRuntime(private val clock:()->Long={System.nanoTime()/1_000_000L}) {
 data class StepResult(val command:String,val observation:String?,val verified:Boolean,val elapsedMs:Long)
 private val observations=mutableListOf<StepResult>()
 val steps:List<StepResult> get()=observations.toList()
 var status=AgentStatus.IDLE
  private set
 private fun move(next:AgentStatus){
  check(AgentStateMachine.isLegal(status,next)){"Invalid transition: $status -> $next"}
  status=next
 }
 fun run(commands:List<String>,execute:(String)->String?):String=run(commands,{false},execute)
 fun run(commands:List<String>,cancelled:()->Boolean,execute:(String)->String?):String{
  observations.clear()
  move(AgentStatus.THINKING)
  val normalized=commands.map{it.trim().lowercase(java.util.Locale.ROOT)}
  if(commands.size !in 1..ToolCatalog.readOnly.size || normalized.distinct().size!=normalized.size || normalized.any{!ToolCatalog.permits(it)}){
   move(AgentStatus.FAILED);return "Plan respins înainte de execuție."
  }
  val results=mutableListOf<String>()
  move(AgentStatus.ACTING)
  for(command in normalized){
   if(cancelled()){move(AgentStatus.STOPPED);return (results+"Execuție oprită.").joinToString("\n")}
   val started=clock()
   val answer=try{execute(command)}catch(e:Exception){null}
   move(AgentStatus.VERIFYING)
   val verified=answer!=null && ObservationVerifier.valid(command,answer)
   observations.add(StepResult(command,answer,verified,clock()-started))
   if(!verified){
    move(AgentStatus.FAILED)
    return (results+"Necunoscut sau eșuat: $command").joinToString("\n")
   }
   results.add("$command: $answer")
   if(cancelled()){move(AgentStatus.STOPPED);return (results+"Execuție oprită.").joinToString("\n")}
   move(AgentStatus.ACTING)
  }
  move(AgentStatus.COMPLETED)
  return results.joinToString("\n")
 }
}
