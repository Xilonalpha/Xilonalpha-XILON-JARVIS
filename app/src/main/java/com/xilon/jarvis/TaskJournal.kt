package com.xilon.jarvis
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
class TaskJournal(context:Context){
 private val prefs=context.getSharedPreferences("task_journal",Context.MODE_PRIVATE)
 private fun entries()=JSONArray(prefs.getString("runs","[]") ?: "[]")
 fun record(steps:List<String>,status:String,evidence:List<TaskRuntime.StepResult> = emptyList()){
  val old=entries();val next=JSONArray()
  for(i in maxOf(0,old.length()-49) until old.length())next.put(old.getJSONObject(i))
  next.put(JSONObject().put("time",System.currentTimeMillis()).put("steps",JSONArray(steps)).put("status",status).put("evidence",JSONArray(evidence.map{JSONObject().put("command",it.command).put("verified",it.verified).put("elapsedMs",it.elapsedMs)})))
  check(prefs.edit().putString("runs",next.toString()).commit()){"Jurnal nesalvat"}
 }
 @Synchronized fun recordRegistry(outcome:RegistryAgentEngine.Outcome){
  val old=entries();val next=JSONArray()
  for(i in maxOf(0,old.length()-49) until old.length())next.put(old.getJSONObject(i))
  next.put(JSONObject().put("time",System.currentTimeMillis()).put("steps",JSONArray(outcome.steps.map{it.action.tool})).put("status",outcome.status).put("remaining",outcome.remaining.size).put("evidence",JSONArray(outcome.steps.map{JSONObject().put("command",it.action.tool).put("status",it.result.status.name).put("verified",it.result.verified.name)})))
  check(prefs.edit().putString("runs",next.toString()).commit()){"Jurnal nesalvat"}
 }
 fun summary():String{
  val a=entries()
  return (0 until a.length()).reversed().joinToString("\n"){i->val x=a.getJSONObject(i);java.util.Date(x.getLong("time")).toString()+" "+x.getString("status")+" "+x.getJSONArray("steps")+(x.optJSONArray("evidence")?.let{" • dovezi: "+it.toString()}?:"")}.ifBlank{"Jurnal gol."}
 }
}
