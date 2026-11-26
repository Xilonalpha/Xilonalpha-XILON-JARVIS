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
        val before = T.freshObserve(0)
        val ok = svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        if (!ok) return ToolResult.fail("Back action rejected.")
        val after = T.freshObserve(450)
        val changed = before != null && after != null &&
            (before.packageName != after.packageName || before.fingerprint() != after.fingerprint())
        return ToolResult.ok("Pressed Back.", if (changed) Verification.VERIFIED else Verification.UNVERIFIED)
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
                if(next != null && (next.packageName != before.packageName || next.fingerprint() != before.fingerprint())) after = next
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
        val ok = svc.swipeCompleted(x1, y1, x2, y2)
        return if (ok) ToolResult.ok("Swipe gesture completed ($x1,$y1)→($x2,$y2); screen effect not verified.", Verification.UNVERIFIED)
        else ToolResult.fail("Swipe gesture rejected, cancelled or timed out.")
    }
}


class ScrollTool : Tool(
    ToolSpec(
        "scroll", "Scroll the screen or a list.",
        listOf(
            com.jarvis.mobile.core.tools.ParamSpec("forward", "bool", false, "true = down/forward (default true)"),
            com.jarvis.mobile.core.tools.ParamSpec("direction", "string", false, "up|down|left|right (alternative to forward)"),
        ),
        com.jarvis.mobile.core.tools.Risk.LOW,
        needsAccessibility = true,
    ),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val svc = com.xilon.jarvis.LocalScreen.service() ?: return ToolResult.unavailable("Accessibility service not connected.")
        val dir = T.str(args, "direction")?.lowercase()
        // Direction names describe finger motion: swipe up advances the list.
        val forward = when (dir) {
            "up" -> true
            "down" -> false
            else -> T.bool(args, "forward") ?: true
        }
        // Horizontal directions ride a centered swipe gesture (pagers, galleries, tabs).
        if (dir == "left" || dir == "right") {
            val m = svc.resources.displayMetrics
            val cy = m.heightPixels / 2
            val ok = if (dir == "left") svc.swipeCompleted((m.widthPixels * 0.78).toInt(), cy, (m.widthPixels * 0.22).toInt(), cy)
            else svc.swipeCompleted((m.widthPixels * 0.22).toInt(), cy, (m.widthPixels * 0.78).toInt(), cy)
            return if (ok) ToolResult.ok("Swiped $dir.", Verification.UNVERIFIED)
            else ToolResult.fail("Could not dispatch swipe gesture.")
        }
        val before = T.freshObserve(0)
        val ok = svc.scrollCompleted(forward)
        if (!ok) return ToolResult.fail("Scroll gesture rejected, cancelled or timed out.")
        val after = T.freshObserve(420)
        val changed = before != null && after != null &&
            (before.packageName != after.packageName || before.fingerprint() != after.fingerprint())
        return ToolResult(
            com.jarvis.mobile.core.tools.ToolStatus.SUCCESS,
            if (changed) "Scrolled ${if (forward) "forward" else "back"}." else "Scrolled (screen may already be at the ${if (forward) "end" else "top"}).",
            if (changed) Verification.VERIFIED else Verification.UNVERIFIED,
            recoveryHint = if (!changed && forward) "list-end" else null,
        )
    }
}


class TapTool : Tool(
    ToolSpec(
        "tap", "Tap an on-screen element. Pass elementIdx from read_screen, or the visible text of the target. As a last resort pass exact screen coordinates x/y (only for targets you can see in the screen block).",
        listOf(
            com.jarvis.mobile.core.tools.ParamSpec("elementIdx", "int", false, "index from the last screen observation"),
            com.jarvis.mobile.core.tools.ParamSpec("text", "string", false, "visible text of the target"),
            com.jarvis.mobile.core.tools.ParamSpec("viewId", "string", false, "view id resource name"),
            com.jarvis.mobile.core.tools.ParamSpec("x", "int", false, "last-resort: exact x coordinate of a visible target"),
            com.jarvis.mobile.core.tools.ParamSpec("y", "int", false, "last-resort: exact y coordinate of a visible target"),
        ),
        com.jarvis.mobile.core.tools.Risk.MEDIUM,
        needsAccessibility = true,
    ),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val svc = com.xilon.jarvis.LocalScreen.service() ?: return ToolResult.unavailable("Accessibility disconnected")
        val fresh = T.freshObserve(0) ?: return ToolResult.fail("No screen")
        val planned = ctx.observation ?: return ToolResult.fail("No initial observation")
        if(fresh.packageName != planned.packageName || fresh.fingerprint()!=planned.fingerprint()) return ToolResult.fail("Screen changed; reobserve")
        val idx = T.int(args,"elementIdx")
        val label = T.str(args,"text")
        val id = T.str(args,"viewId")
        if(idx==null && label==null && id==null) return ToolResult.fail("Specify a visible element")
        val el = fresh.elements.filter { e -> (idx==null || e.idx==idx) && (label==null || e.text==label || e.desc==label) && (id==null || e.viewId==id) && !e.isPassword && !e.editable }.singleOrNull() ?: return ToolResult.fail("Target missing or ambiguous")
        val ok = svc.elementGestureCompleted(el.centerX,el.centerY,"tap",fresh.packageName,fresh.fingerprint())
        return if(ok) ToolResult.ok("Gesture completed; screen effect not verified.",Verification.UNVERIFIED) else ToolResult.fail("Gesture rejected")
    }
}

class DoubleTapTool : Tool(
    ToolSpec(
        "double_tap", "Double-tap a point on screen. This is the LIKE gesture in Instagram/YouTube/Facebook feeds and reels.",
        listOf(
            com.jarvis.mobile.core.tools.ParamSpec("elementIdx", "int", false, "index from read_screen (e.g. the video/image element)"),
            com.jarvis.mobile.core.tools.ParamSpec("text", "string", false, "visible text of the target"),
            com.jarvis.mobile.core.tools.ParamSpec("x", "int", false, "exact x of a visible target (e.g. center of the video)"),
            com.jarvis.mobile.core.tools.ParamSpec("y", "int", false, "exact y of a visible target"),
        ),
        com.jarvis.mobile.core.tools.Risk.MEDIUM,
        needsAccessibility = true,
    ),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val svc = com.xilon.jarvis.LocalScreen.service() ?: return ToolResult.unavailable("Accessibility disconnected")
        val fresh = T.freshObserve(0) ?: return ToolResult.fail("No screen")
        val planned = ctx.observation ?: return ToolResult.fail("No initial observation")
        if(fresh.packageName != planned.packageName || fresh.fingerprint()!=planned.fingerprint()) return ToolResult.fail("Screen changed; reobserve")
        val idx = T.int(args,"elementIdx")
        val label = T.str(args,"text")
        val id = T.str(args,"viewId")
        if(idx==null && label==null && id==null) return ToolResult.fail("Specify a visible element")
        val el = fresh.elements.filter { e -> (idx==null || e.idx==idx) && (label==null || e.text==label || e.desc==label) && (id==null || e.viewId==id) && !e.isPassword && !e.editable }.singleOrNull() ?: return ToolResult.fail("Target missing or ambiguous")
        val ok = svc.elementGestureCompleted(el.centerX,el.centerY,"double",fresh.packageName,fresh.fingerprint())
        return if(ok) ToolResult.ok("Gesture completed; screen effect not verified.",Verification.UNVERIFIED) else ToolResult.fail("Gesture rejected")
    }
}

class LongPressTool : Tool(
    ToolSpec(
        "long_press", "Long-press an on-screen element.",
        listOf(
            com.jarvis.mobile.core.tools.ParamSpec("elementIdx", "int", false, "index from read_screen"),
            com.jarvis.mobile.core.tools.ParamSpec("text", "string", false, "visible text of the target"),
            com.jarvis.mobile.core.tools.ParamSpec("x", "int", false, "x coordinate of a visible target"),
            com.jarvis.mobile.core.tools.ParamSpec("y", "int", false, "y coordinate of a visible target"),
        ),
        com.jarvis.mobile.core.tools.Risk.MEDIUM,
        needsAccessibility = true,
    ),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val svc = com.xilon.jarvis.LocalScreen.service() ?: return ToolResult.unavailable("Accessibility disconnected")
        val fresh = T.freshObserve(0) ?: return ToolResult.fail("No screen")
        val planned = ctx.observation ?: return ToolResult.fail("No initial observation")
        if(fresh.packageName != planned.packageName || fresh.fingerprint()!=planned.fingerprint()) return ToolResult.fail("Screen changed; reobserve")
        val idx = T.int(args,"elementIdx")
        val label = T.str(args,"text")
        val id = T.str(args,"viewId")
        if(idx==null && label==null && id==null) return ToolResult.fail("Specify a visible element")
        val el = fresh.elements.filter { e -> (idx==null || e.idx==idx) && (label==null || e.text==label || e.desc==label) && (id==null || e.viewId==id) && !e.isPassword && !e.editable }.singleOrNull() ?: return ToolResult.fail("Target missing or ambiguous")
        val ok = svc.elementGestureCompleted(el.centerX,el.centerY,"long",fresh.packageName,fresh.fingerprint())
        return if(ok) ToolResult.ok("Gesture completed; screen effect not verified.",Verification.UNVERIFIED) else ToolResult.fail("Gesture rejected")
    }
}


class TypeTextTool : Tool(
    ToolSpec(
        "type_text", "Type text into an editable field. Refuses password/OTP fields.",
        listOf(
            com.jarvis.mobile.core.tools.ParamSpec("text", "string", true, "the text to enter"),
            com.jarvis.mobile.core.tools.ParamSpec("elementIdx", "int", false, "index of the field (uses focused field if omitted)"),
        ),
        com.jarvis.mobile.core.tools.Risk.MEDIUM,
        needsAccessibility = true,
    ),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val svc = com.xilon.jarvis.LocalScreen.service() ?: return ToolResult.unavailable("Accessibility disconnected")
        if(T.int(args,"elementIdx")!=null) return ToolResult.fail("Focus the intended field manually; indexed editing is not supported")
        val pkg = ctx.observation?.packageName ?: return ToolResult.fail("No initial screen")
        val value = T.str(args,"text") ?: return ToolResult.fail("Missing text")
        val ok = svc.editFocused(pkg,"type",value)
        return if(ok) ToolResult.ok("Editor action accepted.",Verification.UNVERIFIED) else ToolResult.fail("No eligible focused editor or action rejected")
    }
}

class ClearTextTool : Tool(
    ToolSpec(
        "clear_text", "Clear an editable text field.",
        listOf(com.jarvis.mobile.core.tools.ParamSpec("elementIdx", "int", false, "index of the field")),
        com.jarvis.mobile.core.tools.Risk.MEDIUM,
        needsAccessibility = true,
    ),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val svc = com.xilon.jarvis.LocalScreen.service() ?: return ToolResult.unavailable("Accessibility disconnected")
        if(T.int(args,"elementIdx")!=null) return ToolResult.fail("Focus the intended field manually; indexed editing is not supported")
        val pkg = ctx.observation?.packageName ?: return ToolResult.fail("No initial screen")
        val value = ""
        val ok = svc.editFocused(pkg,"clear",value)
        return if(ok) ToolResult.ok("Editor action accepted.",Verification.UNVERIFIED) else ToolResult.fail("No eligible focused editor or action rejected")
    }
}

class CopyTextTool : Tool(
    ToolSpec(
        "copy_text", "Select all text in a field and copy it to the clipboard.",
        listOf(com.jarvis.mobile.core.tools.ParamSpec("elementIdx", "int", false, "index of the field")),
        com.jarvis.mobile.core.tools.Risk.MEDIUM,
        needsAccessibility = true,
    ),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val svc = com.xilon.jarvis.LocalScreen.service() ?: return ToolResult.unavailable("Accessibility disconnected")
        if(T.int(args,"elementIdx")!=null) return ToolResult.fail("Focus the intended field manually; indexed editing is not supported")
        val pkg = ctx.observation?.packageName ?: return ToolResult.fail("No initial screen")
        val value = ""
        val ok = svc.editFocused(pkg,"copy",value)
        return if(ok) ToolResult.ok("Editor action accepted.",Verification.UNVERIFIED) else ToolResult.fail("No eligible focused editor or action rejected")
    }
}

class PasteTool : Tool(
    ToolSpec(
        "paste_text", "Paste the current clipboard content into an editable field.",
        listOf(com.jarvis.mobile.core.tools.ParamSpec("elementIdx", "int", false, "index of the field")),
        com.jarvis.mobile.core.tools.Risk.MEDIUM,
        needsAccessibility = true,
    ),
) {
    override suspend fun execute(args: kotlinx.serialization.json.JsonObject, ctx: ToolContext): ToolResult {
        val svc = com.xilon.jarvis.LocalScreen.service() ?: return ToolResult.unavailable("Accessibility disconnected")
        if(T.int(args,"elementIdx")!=null) return ToolResult.fail("Focus the intended field manually; indexed editing is not supported")
        val pkg = ctx.observation?.packageName ?: return ToolResult.fail("No initial screen")
        val value = ""
        val ok = svc.editFocused(pkg,"paste",value)
        return if(ok) ToolResult.ok("Editor action accepted.",Verification.UNVERIFIED) else ToolResult.fail("No eligible focused editor or action rejected")
    }
}

