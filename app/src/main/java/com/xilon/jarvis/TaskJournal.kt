package com.xilon.jarvis
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
class TaskJournal(context:Context){
 private val prefs=context.getSharedPreferences("task_journal",Context.MODE_PRIVATE)
 private fun entries()=JSONArray(prefs.getString("runs","[]") ?: "[]")
 fun record(steps:List<String>,status:String){
  val old=entries();val next=JSONArray()
  for(i in maxOf(0,old.length()-49) until old.length())next.put(old.getJSONObject(i))
  next.put(JSONObject().put("time",System.currentTimeMillis()).put("steps",JSONArray(steps)).put("status",status))
  check(prefs.edit().putString("runs",next.toString()).commit()){"Jurnal nesalvat"}
 }
 fun summary():String{
  val a=entries()
  return (0 until a.length()).reversed().joinToString("\n"){i->val x=a.getJSONObject(i);java.util.Date(x.getLong("time")).toString()+" "+x.getString("status")+" "+x.getJSONArray("steps")}.ifBlank{"Jurnal gol."}
 }
}
