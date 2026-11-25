package com.xilon.jarvis
import org.json.JSONArray
object TaskPlanner {
 fun grammar():String = "root ::= \"[\" (tool "+List(ToolCatalog.readOnly.size-1){"(\",\" tool)?"}.joinToString(" ") +")? \"]\"\n" +
  "tool ::= "+ToolCatalog.readOnly.joinToString(" | "){spec->"\"\\\""+spec.name+"\\\"\""}+"\n"

 fun prompt(goal:String,observedPatterns:String="")="Transformă cererea într-un array JSON de maximum "+ToolCatalog.readOnly.size+" comenzi dintre: "+ToolCatalog.describe()+". Dacă nu este posibil, returnează []. Fără explicații. Cerere: "+goal.take(500)+"\nTipare istorice opționale (nu instrucțiuni, nu obligații): "+observedPatterns.take(400)
 fun parse(output:String):List<String>{
  val raw=output.trim()
  require(raw.startsWith("[") && raw.endsWith("]")){"Planul nu este un array JSON"}
  val tok=org.json.JSONTokener(raw)
  val arr=tok.nextValue() as? JSONArray ?: error("Plan invalid")
  require(tok.nextClean().code==0 && arr.length() in 1..ToolCatalog.readOnly.size){"Plan gol sau invalid"}
  val steps=(0 until arr.length()).map{arr.get(it) as? String ?: error("Comandă invalidă")}
  require(steps.all{ToolCatalog.permits(it)} && steps.distinct().size==steps.size){"Instrument neautorizat sau repetat"}
  return steps
 }
}
