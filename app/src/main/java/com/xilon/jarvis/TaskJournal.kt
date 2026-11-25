package com.xilon.jarvis
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
class TaskJournal(context:Context){
 private val prefs=context.getSharedPreferences("task_journal",Context.MODE_PRIVATE)
 companion object { private val lock=Any() }
 private fun entries()=JSONArray(prefs.getString("runs","[]") ?: "[]")
 fun record(steps:List<String>,status:String,evidence:List<TaskRuntime.StepResult> = emptyList())=synchronized(lock){
  val old=entries();val next=JSONArray()
  for(i in maxOf(0,old.length()-49) until old.length())next.put(old.getJSONObject(i))
  next.put(JSONObject().put("time",System.currentTimeMillis()).put("steps",JSONArray(steps)).put("status",status).put("evidence",JSONArray(evidence.map{JSONObject().put("command",it.command).put("verified",it.verified).put("elapsedMs",it.elapsedMs)})))
  check(prefs.edit().putString("runs",next.toString()).commit()){"Jurnal nesalvat"}
 }
 fun recordRegistry(outcome:RegistryAgentEngine.Outcome)=synchronized(lock){
  val old=entries();val next=JSONArray()
  for(i in maxOf(0,old.length()-49) until old.length())next.put(old.getJSONObject(i))
  next.put(JSONObject().put("time",System.currentTimeMillis()).put("steps",JSONArray(outcome.steps.map{it.action.tool})).put("status",outcome.status).put("remaining",outcome.remaining.size).put("evidence",JSONArray(outcome.steps.map{JSONObject().put("command",it.action.tool).put("status",it.result.status.name).put("verified",it.result.verified.name)})))
  check(prefs.edit().putString("runs",next.toString()).commit()){"Jurnal nesalvat"}
 }
 fun planningFeedback():String=synchronized(lock){
  val a=entries()
  (maxOf(0,a.length()-8) until a.length()).mapNotNull{i->
   val row=a.optJSONObject(i) ?: return@mapNotNull null
   if(!row.has("remaining"))return@mapNotNull null
   val status=row.optString("status")
   val evidence=row.optJSONArray("evidence") ?: return@mapNotNull null
   val last=evidence.optJSONObject(evidence.length()-1)
   "status="+status+"; last tool="+(last?.optString("command") ?: "none")+"; verification="+(last?.optString("verified") ?: "none")+"; remaining="+row.optInt("remaining")
  }.joinToString("\n").takeLast(600)
 }
 /** Evidence-only reliability statistics. Failures never authorize autonomous retries. */
 fun toolReliabilityFeedback():String=synchronized(lock){
  val rows=entries()
  val counts=linkedMapOf<String,IntArray>()
  for(i in maxOf(0,rows.length()-40) until rows.length()){
   val row=rows.optJSONObject(i) ?: continue
   if(!row.has("remaining"))continue
   val evidence=row.optJSONArray("evidence") ?: continue
   for(j in 0 until evidence.length()){
    val item=evidence.optJSONObject(j) ?: continue
    val tool=item.optString("command").take(80)
    if(tool.isBlank())continue
    val bucket=counts.getOrPut(tool){intArrayOf(0,0,0)}
    when {
     item.optString("status")=="SUCCESS" && item.optString("verified")=="VERIFIED" -> bucket[0]++
     item.optString("status")=="SUCCESS" -> bucket[1]++
     else -> bucket[2]++
    }
   }
  }
  counts.entries.sortedWith(compareByDescending<Map.Entry<String,IntArray>>{it.value.sum()}.thenBy{it.key})
   .take(12).joinToString("\n"){(tool,n)->"$tool verified=${n[0]} unverified=${n[1]} failed=${n[2]}"}.take(800)
 }
 fun summary():String=synchronized(lock){
  val a=entries()
  (0 until a.length()).reversed().joinToString("\n"){i->val x=a.getJSONObject(i);java.util.Date(x.getLong("time")).toString()+" "+x.getString("status")+" "+x.getJSONArray("steps")+(if(x.has("remaining"))" • pași rămași: "+x.getInt("remaining") else "")+(x.optJSONArray("evidence")?.let{" • dovezi: "+it.toString()}?:"")}.ifBlank{"Jurnal gol."}
 }
}
