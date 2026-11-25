package com.xilon.jarvis
import android.content.Context
class OmegaEngine(private val context:Context){
 data class Outcome(val text:String,val status:String,val warning:String?,val steps:List<TaskRuntime.StepResult> = emptyList())
 fun run(steps:List<String>,execute:(String)->String?):Outcome=run(steps,{false},execute)
 fun run(steps:List<String>,cancelled:()->Boolean,execute:(String)->String?):Outcome{
  val runtime=TaskRuntime()
  val normalized=steps.map{it.trim().lowercase(java.util.Locale.ROOT)}
  val text=runtime.run(normalized,cancelled,execute)
  val status=runtime.status.name
  val warnings=mutableListOf<String>()
  val verified=runtime.steps.takeWhile{it.verified}.map{it.command}
  if(verified.isNotEmpty())runCatching{LocalGraph(context).use{it.record(verified)}}.onFailure{warnings.add("Graf: "+it.message)}
  runCatching{TaskJournal(context).record(normalized,status,runtime.steps)}.onFailure{warnings.add("Jurnal: "+it.message)}
  return Outcome(text,status,warnings.takeIf{it.isNotEmpty()}?.joinToString("; "),runtime.steps)
 }
 fun replay(name:String,execute:(String)->String?):Outcome?=SkillStore(context).load(name)?.let{run(it,execute)}
}
