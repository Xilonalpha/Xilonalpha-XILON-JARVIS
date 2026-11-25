package com.jarvis.mobile.core.triggers

import android.os.SystemClock

/**
 * Adapted from the vendor trigger dispatch pattern.
 * Matching only proposes a skill; execution belongs to the existing approval flow.
 */
class TriggerEngine(private val clock: () -> Long = SystemClock::elapsedRealtime) {
    data class Binding(val skill: String, val trigger: SkillTrigger)
    data class Proposal(val skill: String, val event: String)
    private val last = LinkedHashMap<String, Long>()

    @Synchronized
    private fun match(bindings: List<Binding>, event: String,
                      predicate: (Binding) -> Boolean): List<Proposal> {
        val now = clock()
        return bindings.asSequence().filter { it.skill.isNotBlank() &&
            it.trigger.isValid() && predicate(it) }.distinctBy { it.skill }
            .mapNotNull { binding ->
                val previous = last[binding.skill]
                if (previous != null && now - previous < binding.trigger.cooldownSec * 1000L)
                    null
                else {
                    last[binding.skill] = now
                    if (last.size > 100) last.remove(last.keys.first())
                    Proposal(binding.skill, event)
                }
            }.toList()
    }

    fun onAppEvent(bindings: List<Binding>, pkg: String, opened: Boolean) =
        match(bindings, if (opened) "APP_OPEN" else "APP_CLOSE") {
            it.trigger.matchesApp(pkg, opened)
        }

    fun onNotification(bindings: List<Binding>, pkg: String, title: String, body: String) =
        match(bindings, "NOTIFICATION") { it.trigger.matchesNotification(pkg, title, body) }

    fun onBatteryLow(bindings: List<Binding>) =
        match(bindings, "BATTERY_LOW") { it.trigger.type == "BATTERY_LOW" }

    fun onTime(bindings: List<Binding>, hour: Int, minute: Int) =
        match(bindings, "TIME") {
            it.trigger.type == "TIME" && it.trigger.hour == hour && it.trigger.minute == minute
        }
}
