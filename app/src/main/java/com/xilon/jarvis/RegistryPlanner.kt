package com.xilon.jarvis
import com.jarvis.mobile.core.tools.*
import kotlinx.serialization.json.*

object RegistryPlanner {
 fun prompt(goal:String):String = "Return only a JSON array of objects with tool and args keys. Use only listed tools and exact argument types. Do not invent values. If impossible return []. Goal: "+goal.take(1000)+"\nTools:\n"+ToolRegistry.catalogPrompt()
 fun parse(raw:String):List<PlannedAction> {
  val array=Json.parseToJsonElement(raw.trim()) as? JsonArray ?: error("Plan invalid")
  require(array.size in 1..20){"Plan gol sau prea lung"}
  return array.map { item ->
   val obj=item as? JsonObject ?: error("Pas invalid")
   require(obj.keys==setOf("tool","args"))
   val name=(obj["tool"] as? JsonPrimitive)?.takeIf{it.isString}?.content ?: error("Instrument invalid")
   val tool=ToolRegistry.get(name) ?: error("Instrument necunoscut")
   val args=obj["args"] as? JsonObject ?: error("Argumente invalide")
   val specs=tool.spec.params.associateBy{it.name}
   require(args.keys.all{it in specs})
   require(tool.spec.params.all{!it.required || args.containsKey(it.name)})
   PlannedAction(name,args)
  }
 }
}
