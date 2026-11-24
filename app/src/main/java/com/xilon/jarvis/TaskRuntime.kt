package com.xilon.jarvis
import com.jarvis.mobile.core.agent.AgentStatus
import com.jarvis.mobile.core.agent.AgentStateMachine
class TaskRuntime {
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
   val answer=try{execute(command)}catch(e:Exception){null}
   move(AgentStatus.VERIFYING)
   if(answer==null || !ObservationVerifier.valid(command,answer)){
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
