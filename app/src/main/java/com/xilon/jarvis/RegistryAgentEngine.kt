package com.xilon.jarvis
import com.jarvis.mobile.core.tools.*
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

object RegistryAgentEngine {
 data class Step(val action:PlannedAction,val result:ToolResult)
 data class Outcome(val status:String,val steps:List<Step>)
 suspend fun run(plan:List<PlannedAction>,cancelled:()->Boolean,confirm:suspend (PlannedAction)->Boolean,onStep:(Step)->Unit={}):Outcome {
  require(plan.size in 1..20)
  val steps=mutableListOf<Step>()
  for(action in plan){
   coroutineContext.ensureActive()
   if(cancelled())return Outcome("STOPPED",steps.toList())
   if(!confirm(action))return Outcome("STOPPED",steps.toList())
   if(cancelled())return Outcome("STOPPED",steps.toList())
   val result=ToolEnvironment.execute(action.tool,action.args,true)
   val step=Step(action,result)
   steps.add(step)
   onStep(step)
   if(result.status!=ToolStatus.SUCCESS)return Outcome("FAILED",steps.toList())
   if(result.verified!=Verification.VERIFIED)return Outcome("UNVERIFIED",steps.toList())
  }
  return Outcome("COMPLETED",steps.toList())
 }
}
