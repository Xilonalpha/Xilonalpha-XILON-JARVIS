package com.jarvis.mobile.core.triggers

import android.content.Context
import com.xilon.jarvis.SkillStore
import org.json.JSONObject

class TriggerStore(context: Context) {
    private val skills = SkillStore(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences("xilon_triggers", Context.MODE_PRIVATE)

    fun save(skill: String, trigger: SkillTrigger) {
        require(skill in skills.names() && trigger.isValid())
        val json = JSONObject().put("type", trigger.type)
            .put("pkg", trigger.pkg ?: JSONObject.NULL)
            .put("hour", trigger.hour).put("minute", trigger.minute)
            .put("text", trigger.text ?: JSONObject.NULL).put("cooldown", trigger.cooldownSec)
        check(prefs.edit().putString(skill, json.toString()).commit())
    }

    fun remove(skill: String) {
        check(prefs.edit().remove(skill).commit())
    }

    fun bindings(): List<TriggerEngine.Binding> = skills.names().mapNotNull { skill ->
        val raw = prefs.getString(skill, null) ?: return@mapNotNull null
        runCatching {
            val j = JSONObject(raw)
            val trigger = SkillTrigger(j.getString("type"),
                if (j.isNull("pkg")) null else j.getString("pkg"),
                j.getInt("hour"), j.getInt("minute"),
                if (j.isNull("text")) null else j.getString("text"), j.getLong("cooldown"))
            if (trigger.isValid()) TriggerEngine.Binding(skill, trigger) else null
        }.getOrNull()
    }
}
