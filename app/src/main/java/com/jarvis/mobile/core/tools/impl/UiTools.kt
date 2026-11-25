package com.jarvis.mobile.core.tools.impl

import com.jarvis.mobile.core.observer.ScreenElement
import com.jarvis.mobile.core.observer.ScreenObservation
import com.jarvis.mobile.core.tools.Tool
import com.jarvis.mobile.core.tools.ToolContext
import com.jarvis.mobile.core.tools.ToolResult
import com.jarvis.mobile.core.tools.ToolSpec
import com.jarvis.mobile.core.tools.Verification

class ReadScreenTool : Tool(
    ToolSpec(
        "read_screen", "Read the current screen as structured elements.",
        emptyList(),
        com.jarvis.mobile.core.tools.Risk.LOW,
        needsAccessibility = true,
    ),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val obs = T.freshObserve() ?: return ToolResult.unavailable("Cannot read the screen - accessibility service not connected.")
        return ToolResult.ok("Read screen of ${obs.packageName}.", Verification.VERIFIED, detail = obs.toCompact())
    }
}

class FindElementTool : Tool(
    ToolSpec(
        "find_element", "Search the current screen for an element by text or description.",
        listOf(com.jarvis.mobile.core.tools.ParamSpec("text", "string", true, "text to search for")),
        com.jarvis.mobile.core.tools.Risk.LOW,
        needsAccessibility = true,
    ),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val q = T.str(args, "text") ?: return ToolResult.fail("Missing required arg: text.")
        val obs = T.freshObserve() ?: return ToolResult.unavailable("Cannot read the screen.")
        val matches = obs.elements.filter {
            it.text?.contains(q, true) == true || it.desc?.contains(q, true) == true
        }
        return if (matches.isEmpty()) {
            ToolResult.fail("\"$q\" not found on screen.", "scroll-then-retry")
        } else {
            val det = matches.take(6).joinToString("; ") { "[${it.idx}] ${it.role} \"${(it.text ?: it.desc)?.take(40)}\"" }
            ToolResult.ok("Found ${matches.size} match(es).", Verification.VERIFIED, detail = det)
        }
    }
}

class PressBackTool : Tool(
    ToolSpec("press_back", "Press the system Back button.", emptyList(), com.jarvis.mobile.core.tools.Risk.LOW, needsAccessibility = true),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val svc = com.xilon.jarvis.LocalScreen.service() ?: return ToolResult.unavailable("Accessibility service not connected.")
        val ok = svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        return if (ok) ToolResult.ok("Pressed Back.", Verification.UNVERIFIED) else ToolResult.fail("Back action rejected.")
    }
}

class PressHomeTool : Tool(
    ToolSpec("press_home", "Go to the home screen.", emptyList(), com.jarvis.mobile.core.tools.Risk.LOW, needsAccessibility = true),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val svc = com.xilon.jarvis.LocalScreen.service() ?: return ToolResult.unavailable("Accessibility service not connected.")
        val ok = svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
        if (!ok) return ToolResult.fail("Home action rejected.")
        T.settle(500)
        val pkg = com.xilon.jarvis.LocalScreen.observe()?.packageName
        val verified = pkg != null && (pkg.contains("launcher", true) || pkg == "com.google.android.apps.nexuslauncher")
        return ToolResult.ok("Pressed Home.", if (verified) Verification.VERIFIED else Verification.UNVERIFIED)
    }
}

class PressRecentsTool : Tool(
    ToolSpec("press_recents", "Open the recent-apps overview.", emptyList(), com.jarvis.mobile.core.tools.Risk.LOW, needsAccessibility = true),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val svc = com.xilon.jarvis.LocalScreen.service() ?: return ToolResult.unavailable("Accessibility service not connected.")
        val ok = runCatching { svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS) }.getOrDefault(false)
        return if (ok) ToolResult.ok("Opened recents.", Verification.UNVERIFIED) else ToolResult.fail("Recents action rejected.")
    }
}

class PressNotificationsTool : Tool(
    ToolSpec("press_notifications", "Pull down the notification shade.", emptyList(), com.jarvis.mobile.core.tools.Risk.LOW, needsAccessibility = true),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val svc = com.xilon.jarvis.LocalScreen.service() ?: return ToolResult.unavailable("Accessibility service not connected.")
        val ok = svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        return if (ok) ToolResult.ok("Opened the notification shade.", Verification.UNVERIFIED)
        else ToolResult.fail("Notification shade action rejected.")
    }
}

class WaitTool : Tool(
    ToolSpec(
        "wait", "Wait for the screen or app to settle.",
        listOf(com.jarvis.mobile.core.tools.ParamSpec("ms", "int", false, "milliseconds (default 800, max 5000)")),
        com.jarvis.mobile.core.tools.Risk.LOW,
    ),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val ms = (T.int(args, "ms") ?: 800).coerceIn(100, 5000)
        kotlinx.coroutines.delay(ms.toLong())
        return ToolResult.ok("Waited ${ms}ms.", Verification.VERIFIED)
    }
}

class WaitForChangeTool : Tool(
    ToolSpec(
        "wait_for_change", "Wait until the screen changes (window transition or content update).",
        listOf(com.jarvis.mobile.core.tools.ParamSpec("timeoutMs", "int", false, "max wait (default 6000)")),
        com.jarvis.mobile.core.tools.Risk.LOW,
    ),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val t = T.int(args, "timeoutMs")?.toLong()?.coerceIn(500, 15000) ?: 6000
        val before = T.freshObserve(0) ?: return ToolResult.unavailable("No screen observation")
        val changed = kotlinx.coroutines.withTimeoutOrNull(t) {
            var after: ScreenObservation? = null
            while(after == null) {
                kotlinx.coroutines.delay(150)
                val next = T.freshObserve(0)
                if(next != null && next.fingerprint() != before.fingerprint()) after = next
            }
            after
        }
        return if (changed != null) {
            ToolResult.ok("Screen changed (app=${changed.packageName}).", Verification.VERIFIED)
        } else {
            ToolResult(com.jarvis.mobile.core.tools.ToolStatus.FAILED, "Screen did not change within ${t}ms.", Verification.FAILED, recoveryHint = "try-scroll-or-replan")
        }
    }
}

class SwipeTool : Tool(
    ToolSpec(
        "swipe", "Perform a swipe gesture between two screen points.",
        listOf(
            com.jarvis.mobile.core.tools.ParamSpec("x1", "int", true, "start x"),
            com.jarvis.mobile.core.tools.ParamSpec("y1", "int", true, "start y"),
            com.jarvis.mobile.core.tools.ParamSpec("x2", "int", true, "end x"),
            com.jarvis.mobile.core.tools.ParamSpec("y2", "int", true, "end y"),
        ),
        com.jarvis.mobile.core.tools.Risk.LOW,
        needsAccessibility = true,
    ),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val svc = com.xilon.jarvis.LocalScreen.service() ?: return ToolResult.unavailable("Accessibility service not connected.")
        val x1 = T.int(args, "x1") ?: return ToolResult.fail("Missing x1.")
        val y1 = T.int(args, "y1") ?: return ToolResult.fail("Missing y1.")
        val x2 = T.int(args, "x2") ?: return ToolResult.fail("Missing x2.")
        val y2 = T.int(args, "y2") ?: return ToolResult.fail("Missing y2.")
        val ok = svc.swipeConfirmed(x1, y1, x2, y2)
        return if (ok) ToolResult.ok("Swiped ($x1,$y1)→($x2,$y2).", Verification.UNVERIFIED)
        else ToolResult.fail("Could not dispatch swipe gesture.")
    }
}

