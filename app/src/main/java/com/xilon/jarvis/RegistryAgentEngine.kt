package com.xilon.jarvis
import com.jarvis.mobile.core.tools.*
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.coroutineContext

object RegistryAgentEngine {
 data class Step(val action:PlannedAction,val result:ToolResult)
 data class Outcome(val status:String,val steps:List<Step>,val remaining:List<PlannedAction> = emptyList(),val reason:String? = null)
 suspend fun run(plan:List<PlannedAction>,cancelled:()->Boolean,confirm:suspend (PlannedAction)->Boolean,onStep:(Step)->Unit={}):Outcome {
  if(plan.size !in 1..RegistryPlanner.MAX_STEPS)return Outcome("INVALID_PLAN",emptyList(),plan,"Plan gol sau prea lung")
  val steps=mutableListOf<Step>()
  for(action in plan){
   val invalid=ToolEnvironment.validate(action.tool,action.args)
   if(invalid!=null)return Outcome("INVALID_PLAN",emptyList(),plan,invalid.message)
  }
  for((index,action) in plan.withIndex()){
   coroutineContext.ensureActive()
   if(cancelled())return Outcome("STOPPED",steps.toList(),plan.drop(index))
   val currentInvalid=ToolEnvironment.validate(action.tool,action.args)
   if(currentInvalid!=null)return Outcome("INVALID_PLAN",steps.toList(),plan.drop(index),currentInvalid.message)
   if(!confirm(action))return Outcome("STOPPED",steps.toList(),plan.drop(index))
   coroutineContext.ensureActive()
   if(cancelled())return Outcome("STOPPED",steps.toList(),plan.drop(index))
   val afterConfirmation=ToolEnvironment.validate(action.tool,action.args)
   if(afterConfirmation!=null)return Outcome("INVALID_PLAN",steps.toList(),plan.drop(index),afterConfirmation.message)
   val result=try { kotlinx.coroutines.withTimeoutOrNull(45000L){ToolEnvironment.execute(action.tool,action.args,false)} ?: return Outcome("EXECUTION_UNKNOWN",steps.toList(),plan.drop(index+1),"Timp depășit; efectul acțiunii este necunoscut. Nu relua automat.") }
   catch(e:CancellationException){throw e}
   catch(e:Exception){return Outcome("EXECUTION_UNKNOWN",steps.toList(),plan.drop(index+1),"Rezultatul pasului ${index+1} (${action.tool}) este necunoscut: ${e.javaClass.simpleName}. Nu relua automat.")}
   if(result.status==ToolStatus.REQUIRES_CONFIRMATION)return Outcome("REQUIRES_CONFIRMATION",steps.toList(),plan.drop(index),result.message)
   val step=Step(action,result)
   steps.add(step)
   try { onStep(step) } catch(e:CancellationException){throw e} catch(e:Exception){return Outcome("REPORTING_FAILED",steps.toList(),plan.drop(index+1),"Raportarea pasului a eșuat: ${e.javaClass.simpleName}")}
   if(result.status!=ToolStatus.SUCCESS)return Outcome("FAILED",steps.toList(),plan.drop(index+1),result.message)
   if(result.verified!=Verification.VERIFIED)return Outcome("UNVERIFIED",steps.toList(),plan.drop(index+1),result.message)
   if(cancelled() && index<plan.lastIndex)return Outcome("STOPPED",steps.toList(),plan.drop(index+1))
  }
  return Outcome("COMPLETED",steps.toList())
 }
}
