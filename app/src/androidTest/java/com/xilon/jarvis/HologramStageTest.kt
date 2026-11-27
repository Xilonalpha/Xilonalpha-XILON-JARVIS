package com.xilon.jarvis

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class HologramStageTest {
    @Test fun chatOpensFourWayProjectionAndReturnsToChat() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        fun waitForNode(label: String): AccessibilityNodeInfo {
            val until = SystemClock.elapsedRealtime() + 8000
            while (SystemClock.elapsedRealtime() < until) {
                val root = instrumentation.uiAutomation.rootInActiveWindow
                root?.findAccessibilityNodeInfosByText(label)?.firstOrNull {
                    it.isVisibleToUser && (it.text?.toString() == label || (label.startsWith("Proiecție") && it.contentDescription?.toString()?.startsWith(label) == true))
                }?.let { return it }
                SystemClock.sleep(100)
            }
            error("Missing visible control: $label")
        }
        try {
            assertTrue(waitForNode("Hologramă").performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertTrue(waitForNode("Piramidă 4X").performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitForNode("Proiecție Hologramă Piramidă")
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitForNode("Hologramă")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
