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
            var visibleLabels = emptyList<String>()
            val until = SystemClock.elapsedRealtime() + 10000
            while (SystemClock.elapsedRealtime() < until) {
                val labels = mutableListOf<String>()
                fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                    if (node == null) return null
                    val text = node.text?.toString().orEmpty()
                    val description = node.contentDescription?.toString().orEmpty()
                    if (node.isVisibleToUser) {
                        if (text.isNotBlank()) labels.add(text)
                        if (description.isNotBlank()) labels.add(description)
                        if (text == label || description == label ||
                            (label.startsWith("Proiecție") && description.startsWith(label))) return node
                    }
                    for (child in 0 until node.childCount) {
                        find(node.getChild(child))?.let { return it }
                    }
                    return null
                }
                find(instrumentation.uiAutomation.rootInActiveWindow)?.let { return it }
                visibleLabels = labels
                SystemClock.sleep(100)
            }
            error("Missing visible control: $label. Visible: $visibleLabels")
        }
        try {
            assertTrue(waitForNode("Hologramă").performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertTrue(waitForNode("Piramidă 4X").performAction(AccessibilityNodeInfo.ACTION_CLICK))
            val projection = waitForNode("Proiecție Hologramă Piramidă")
            val bounds = android.graphics.Rect()
            projection.getBoundsInScreen(bounds)
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try {
                val size = minOf(bounds.width(), bounds.height())
                val offset = (size * 0.305f).toInt()
                val radius = size / 9
                for ((dx, dy) in listOf(0 to -offset, offset to 0, 0 to offset, -offset to 0)) {
                    val cx = bounds.centerX() + dx
                    val cy = bounds.centerY() + dy
                    val colored = (cy-radius..cy+radius step 4).any { y ->
                        (cx-radius..cx+radius step 4).any { x ->
                            val pixel = screenshot.getPixel(x, y)
                            android.graphics.Color.blue(pixel) > 100 && android.graphics.Color.green(pixel) > 80
                        }
                    }
                    assertTrue("Projection missing at $cx,$cy", colored)
                }
            } finally {
                screenshot.recycle()
            }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            waitForNode("Hologramă")
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
