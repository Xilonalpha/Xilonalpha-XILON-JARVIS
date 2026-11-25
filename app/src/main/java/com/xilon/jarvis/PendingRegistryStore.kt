package com.xilon.jarvis

import android.content.Context
import com.jarvis.mobile.core.tools.PlannedAction
import kotlinx.serialization.json.*

/** Explicitly stopped, verified plans only. Never restores in-flight actions. */
class PendingRegistryStore(context: Context) {
 private val prefs = context.getSharedPreferences("pending_registry", Context.MODE_PRIVATE)
 fun load(): List<PlannedAction> {
  val raw = prefs.getString("plan", null) ?: return emptyList()
  return RegistryPlanner.parse(raw)
 }
 fun save(steps: List<PlannedAction>) {
  require(steps.size in 1..20)
  val array = JsonArray(steps.map { step -> buildJsonObject {
   put("tool", JsonPrimitive(step.tool))
   put("args", step.args)
  } })
  check(prefs.edit().putString("plan", array.toString()).commit()) { "Plan nesalvat" }
 }
 fun clear() {
  check(prefs.edit().remove("plan").commit()) { "Planul persistent nu a putut fi șters" }
 }
}
