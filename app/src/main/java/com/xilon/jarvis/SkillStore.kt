package com.xilon.jarvis
import android.content.Context
import org.json.JSONArray
class SkillStore(context:Context){
 private val prefs=context.getSharedPreferences("xilon_skills",Context.MODE_PRIVATE)
 private fun key(name:String):String{
  val k=name.trim().lowercase(java.util.Locale.ROOT)
  require(k.length in 1..64 && k.all{it.isLetterOrDigit() || it==' ' || it=='_' || it=='-'}){"Nume invalid"}
  return k
 }
 fun save(name:String,steps:List<String>){
  val k=key(name)
  require(steps.size in 1..4 && steps.distinct().size==steps.size && steps.all{ToolCatalog.permits(it)}){"Pași neautorizați"}
  require(prefs.contains(k)||prefs.all.size<100){"Maximum 100 abilități"}
  check(prefs.edit().putString(k,JSONArray(steps).toString()).commit()){"Salvare eșuată"}
 }
 fun load(name:String):List<String>?=prefs.getString(key(name),null)?.let{TaskPlanner.parse(it)}
 fun names():List<String> = prefs.all.keys.sorted()
 fun delete(name:String):Boolean{
  val k=key(name);if(!prefs.contains(k))return false
  check(prefs.edit().remove(k).commit()){"Ștergere eșuată"};return true
 }
}
