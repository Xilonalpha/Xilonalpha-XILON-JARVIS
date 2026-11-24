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
  require(commands.size in 1..5)
  move(AgentStatus.ACTING)
  val results=mutableListOf<String>()
  for(command in commands){
   // Only read-only device observations are supported by this first runner.
   if(!ToolCatalog.permits(command.lowercase(java.util.Locale.ROOT))){
    move(AgentStatus.FAILED)
    return (results+"Blocat: $command (instrument neautorizat)").joinToString("\n")
   }
   val answer=try{execute(command)}catch(e:Exception){null}
   if(answer==null || !ObservationVerifier.valid(command,answer)){
    move(AgentStatus.FAILED)
    return (results+"Necunoscut sau eșuat: $command").joinToString("\n")
   }
   move(AgentStatus.VERIFYING)
   results.add("$command: $answer")
   move(AgentStatus.ACTING)
  }
  move(AgentStatus.COMPLETED)
  return results.joinToString("\n")
 }
}
