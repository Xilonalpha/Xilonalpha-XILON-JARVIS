package com.xilon.jarvis
import android.content.Context
class OmegaEngine(private val context:Context){
 data class Outcome(val text:String,val status:String,val warning:String?)
 fun run(steps:List<String>,execute:(String)->String?):Outcome{
  val runtime=TaskRuntime()
  val normalized=steps.map{it.trim().lowercase(java.util.Locale.ROOT)}
  val text=runtime.run(normalized,execute)
  val status=runtime.status.name
  val warnings=mutableListOf<String>()
  if(status=="COMPLETED")runCatching{LocalGraph(context).use{it.record(normalized)}}.onFailure{warnings.add("Graf: "+it.message)}
  runCatching{TaskJournal(context).record(normalized,status)}.onFailure{warnings.add("Jurnal: "+it.message)}
  return Outcome(text,status,warnings.takeIf{it.isNotEmpty()}?.joinToString("; "))
 }
 fun replay(name:String,execute:(String)->String?):Outcome?=SkillStore(context).load(name)?.let{run(it,execute)}
}
