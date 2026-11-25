package com.xilon.jarvis
import com.jarvis.mobile.core.tools.*
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

object RegistryAgentEngine {
 data class Step(val action:PlannedAction,val result:ToolResult)
 data class Outcome(val status:String,val steps:List<Step>,val remaining:List<PlannedAction> = emptyList(),val reason:String? = null)
 suspend fun run(plan:List<PlannedAction>,cancelled:()->Boolean,confirm:suspend (PlannedAction)->Boolean,onStep:(Step)->Unit={}):Outcome {
  require(plan.size in 1..20)
  val steps=mutableListOf<Step>()
  for(action in plan){
   val invalid=ToolEnvironment.validate(action.tool,action.args)
   if(invalid!=null)return Outcome("INVALID_PLAN",emptyList(),plan,invalid.message)
  }
  for((index,action) in plan.withIndex()){
   coroutineContext.ensureActive()
   if(cancelled())return Outcome("STOPPED",steps.toList(),plan.drop(index))
   if(!confirm(action))return Outcome("STOPPED",steps.toList(),plan.drop(index))
   if(cancelled())return Outcome("STOPPED",steps.toList(),plan.drop(index))
   val result=ToolEnvironment.execute(action.tool,action.args,true)
   val step=Step(action,result)
   steps.add(step)
   onStep(step)
   if(cancelled())return Outcome("STOPPED",steps.toList(),plan.drop(index+1))
   if(result.status!=ToolStatus.SUCCESS)return Outcome("FAILED",steps.toList(),plan.drop(index+1),result.message)
   if(result.verified!=Verification.VERIFIED)return Outcome("UNVERIFIED",steps.toList(),plan.drop(index+1),result.message)
  }
  return Outcome("COMPLETED",steps.toList())
 }
}
