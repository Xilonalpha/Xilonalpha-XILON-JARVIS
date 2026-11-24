package com.xilon.jarvis
import org.json.JSONArray
object TaskPlanner {
 private val allowed=setOf("ora","baterie","ram","stocare")
 fun prompt(goal:String)="Transformă cererea într-un array JSON de maximum 4 comenzi dintre ora, baterie, ram, stocare. Dacă nu este posibil, returnează []. Fără explicații. Cerere: "+goal
 fun parse(output:String):List<String>{
  val raw=output.trim()
  require(raw.startsWith("[") && raw.endsWith("]")){"Planul nu este un array JSON"}
  val tok=org.json.JSONTokener(raw)
  val arr=tok.nextValue() as? JSONArray ?: error("Plan invalid")
  require(tok.nextClean().code==0 && arr.length() in 1..4){"Plan gol sau invalid"}
  val steps=(0 until arr.length()).map{arr.get(it) as? String ?: error("Comandă invalidă")}
  require(steps.all{it in allowed} && steps.distinct().size==steps.size){"Instrument neautorizat sau repetat"}
  return steps
 }
}
