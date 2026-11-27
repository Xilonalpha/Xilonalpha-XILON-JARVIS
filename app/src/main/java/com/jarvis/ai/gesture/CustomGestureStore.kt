package com.jarvis.ai.gesture
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedGesture(val template: CustomTemplate, val action: String)
class CustomGestureStore(context: Context) {
 private val prefs=context.getSharedPreferences("custom_air_gestures",Context.MODE_PRIVATE)
 fun load():List<SavedGesture> = runCatching {
  val raw=prefs.getString("items","[]") ?: "[]"
  require(raw.length<=2000000)
  val array=JSONArray(raw)
  require(array.length()<=16)
  (0 until array.length()).map { i ->
   val o=array.getJSONObject(i)
   val samples=SampleCodec.decode(o.getString("samples"))
   require(samples.size in 3..5 && samples.all { s -> s.durationMs in 300..3000 && s.steps.all { row -> row.all { it.isFinite() } } })
   SavedGesture(CustomTemplate(o.getLong("id"),o.getString("name"),samples,GestureQuality.radius(samples)),o.getString("action"))
  }
 }.getOrDefault(emptyList())
 fun save(items:List<SavedGesture>){
  require(items.size<=16)
  val a=JSONArray()
  items.forEach { item -> a.put(JSONObject().put("id",item.template.id).put("name",item.template.name).put("samples",SampleCodec.encode(item.template.samples)).put("action",item.action)) }
  prefs.edit().putString("items",a.toString()).apply()
 }
}
