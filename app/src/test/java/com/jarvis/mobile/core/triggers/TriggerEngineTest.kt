package com.jarvis.mobile.core.triggers

import org.junit.Assert.*
import org.junit.Test

class TriggerEngineTest {
    @Test fun appCooldownAndDeduplication() {
        var now = 1000L
        val engine = TriggerEngine { now }
        val binding = TriggerEngine.Binding("routine", SkillTrigger("APP_OPEN", pkg="example.app", cooldownSec=10))
        val bindings = listOf(binding, binding)
        assertTrue(engine.onAppEvent(bindings, "other.app", true).isEmpty())
        assertEquals(1, engine.onAppEvent(bindings, "example.app", true).size)
        assertTrue(engine.onAppEvent(bindings, "example.app", true).isEmpty())
        now += 10000
        assertEquals(1, engine.onAppEvent(bindings, "example.app", true).size)
    }

    @Test fun notificationAndManualIsolation() {
        val engine = TriggerEngine { 1000L }
        val bindings = listOf(
            TriggerEngine.Binding("manual", SkillTrigger("MANUAL")),
            TriggerEngine.Binding("notice", SkillTrigger("NOTIFICATION", pkg="example.app", text="hello"))
        )
        assertTrue(engine.onNotification(bindings, "other.app", "hello", "").isEmpty())
        assertEquals(listOf("notice"), engine.onNotification(bindings, "example.app", "", "HELLO").map { it.skill })
        assertTrue(engine.onBatteryLow(bindings).isEmpty())
    }

    @Test fun invalidTimeNeverMatches() {
        val engine = TriggerEngine { 1000L }
        val bindings = listOf(TriggerEngine.Binding("invalid", SkillTrigger("TIME", hour=25, minute=0)))
        assertTrue(engine.onTime(bindings, 25, 0).isEmpty())
    }
}
