package com.xilon.jarvis
import android.content.Context
class OmegaEngine(private val context:Context){
 data class Outcome(val text:String,val status:String,val warning:String?)
 fun run(steps:List<String>,execute:(String)->String?):Outcome{
  val runtime=TaskRuntime()
  val text=runtime.run(steps,execute)
  val status=runtime.status.name
  val warnings=mutableListOf<String>()
  if(status=="COMPLETED")runCatching{LocalGraph(context).use{it.record(steps)}}.onFailure{warnings.add("Graf: "+it.message)}
  runCatching{TaskJournal(context).record(steps,status)}.onFailure{warnings.add("Jurnal: "+it.message)}
  return Outcome(text,status,warnings.joinToString("; ").ifBlank{null})
 }
 fun replay(name:String,execute:(String)->String?):Outcome?=SkillStore(context).load(name)?.let{run(it,execute)}
}
