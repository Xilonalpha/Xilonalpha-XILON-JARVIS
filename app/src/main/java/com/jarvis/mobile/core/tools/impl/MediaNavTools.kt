package com.jarvis.mobile.core.tools.impl

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.view.KeyEvent
import com.xilon.jarvis.ToolEnvironment

import com.jarvis.mobile.core.tools.Tool
import com.jarvis.mobile.core.tools.ToolContext
import com.jarvis.mobile.core.tools.ToolResult
import com.jarvis.mobile.core.tools.ToolSpec
import com.jarvis.mobile.core.tools.Verification
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder

/**
 * Media, navigation, alarm and system-action tools with JARVIS's honest
 * result model: every tool reports what ACTUALLY happened, never fake success.
 */
class OpenUrlTool : Tool(
    ToolSpec(
        "open_url", "Open a web URL in the browser.",
        listOf(com.jarvis.mobile.core.tools.ParamSpec("url", "string", true, "full or bare domain, e.g. example.com")),
        com.jarvis.mobile.core.tools.Risk.LOW,
    ),
) {
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        var url = T.str(args, "url")?.trim() ?: return ToolResult.fail("Missing required arg: url.")
        if (url.startsWith("javascript:")) return ToolResult.fail("Refusing non-web URL.")
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        return CommStart.start(i)
    }
}

class YoutubeSearchTool : Tool(
    ToolSpec(
        "youtube_search", "Search on YouTube and open the results (plays the top result if the user taps it).",
        listOf(com.jarvis.mobile.core.tools.ParamSpec("query", "string", true, "search text"), com.jarvis.mobile.core.tools.ParamSpec("play", "bool", false, "request playback instead of search only")),
        com.jarvis.mobile.core.tools.Risk.LOW,
    ),
) {
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val q = T.str(args, "query") ?: return ToolResult.fail("Missing required arg: query.")
        val enc = URLEncoder.encode(q, "UTF-8")
        val context = ToolEnvironment.context
        val videoId = Regex("^[A-Za-z0-9_-]{11}$")
        val uri = runCatching { Uri.parse(q.trim()) }.getOrNull()
        val suppliedId = when {
            uri?.scheme in listOf("https", "http") && uri?.host == "youtu.be" -> uri?.pathSegments?.singleOrNull()
            uri?.scheme in listOf("https", "http") && uri?.host in listOf("www.youtube.com", "youtube.com", "m.youtube.com") && uri?.path == "/watch" -> uri?.getQueryParameter("v")
            else -> null
        }
        val id = suppliedId?.takeIf(videoId::matches)
            ?: if (T.bool(args, "play") == true) YoutubeVideoLookup.first(enc) else null
        if (id != null) {
            val watch = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(Intent(watch).setPackage("com.google.android.youtube")) }.isSuccess)
                return ToolResult.ok("Am deschis videoclipul cerut; redarea nu este confirmată.", Verification.UNVERIFIED)
            return CommStart.start(watch)
        }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$enc")).apply {
            setPackage("com.google.android.youtube")
        }
        if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) {
            return ToolResult.ok("Rezultatele YouTube pentru \"$q\" sunt deschise; redarea nu este confirmată.", Verification.UNVERIFIED)
        }
        return CommStart.start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$enc")))
            
    }
}

class MapsNavigateTool : Tool(
    ToolSpec(
        "maps_navigate", "Open Google Maps directions/search for a destination.",
        listOf(com.jarvis.mobile.core.tools.ParamSpec("destination", "string", true, "place name or address")),
        com.jarvis.mobile.core.tools.Risk.LOW,
    ),
) {
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val d = T.str(args, "destination") ?: return ToolResult.fail("Missing required arg: destination.")
        val enc = URLEncoder.encode(d, "UTF-8")
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$enc"))
        return CommStart.start(i)
    }
}

class SetAlarmTool : Tool(
    ToolSpec(
        "set_alarm", "Request direct alarm creation in the clock app.",
        listOf(
            com.jarvis.mobile.core.tools.ParamSpec("hour", "int", true, "hour 0-23 (24h format)"),
            com.jarvis.mobile.core.tools.ParamSpec("minute", "int", true, "minute 0-59"),
            com.jarvis.mobile.core.tools.ParamSpec("label", "string", false, "alarm label"),
        ),
        com.jarvis.mobile.core.tools.Risk.LOW,
    ),
) {
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val hour = T.int(args, "hour") ?: return ToolResult.fail("Missing required arg: hour (0-23). If the user says \"6 pm\" that is 18.")
        val minute = T.int(args, "minute") ?: return ToolResult.fail("Missing required arg: minute (0-59).")
        if (hour !in 0..23 || minute !in 0..59) return ToolResult.fail("Hour must be 0-23 and minute 0-59 (got $hour:$minute).")
        val i = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            T.str(args, "label")?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        }
        return CommStart.start(i).let { if(it.status == com.jarvis.mobile.core.tools.ToolStatus.SUCCESS) it.copy(message = "Am solicitat setarea directă a alarmei. Aplicația Ceas nu oferă confirmare de rezultat.", detail = "alarm=$hour:$minute") else it }
    }
}

class SetTimerTool : Tool(
    ToolSpec(
        "set_timer", "Start a countdown timer in the clock app.",
        listOf(
            com.jarvis.mobile.core.tools.ParamSpec("seconds", "int", true, "duration in seconds"),
            com.jarvis.mobile.core.tools.ParamSpec("label", "string", false, "timer label"),
        ),
        com.jarvis.mobile.core.tools.Risk.LOW,
    ),
) {
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val s = T.int(args, "seconds") ?: return ToolResult.fail("Missing required arg: seconds.")
        if (s <= 0 || s > 86_400) return ToolResult.fail("seconds must be 1-86400.")
        val i = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, s)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            T.str(args, "label")?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        }
        return CommStart.start(i).let { if(it.status == com.jarvis.mobile.core.tools.ToolStatus.SUCCESS) it.copy(message = "Am solicitat pornirea directă a temporizatorului.", detail = "timer=${s}s") else it }
    }
}

class MediaPlayPauseTool : Tool(
    ToolSpec("media_play_pause", "Control current media or request playback in a local music player.", listOf(com.jarvis.mobile.core.tools.ParamSpec("action", "string", false, "toggle|play|pause|next|previous|local"), com.jarvis.mobile.core.tools.ParamSpec("query", "string", false, "local song title")), com.jarvis.mobile.core.tools.Risk.LOW),
) {
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val am = ToolEnvironment.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val action = T.str(args, "action") ?: "toggle"
        if (action == "local") {
            val query = T.str(args, "query")?.trim().orEmpty()
            val context = ToolEnvironment.context
            val media = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val columns = arrayOf("_id", "title", "artist", "mime_type")
            return try {
                val target = com.xilon.jarvis.RomanianLexicon.normalize(query)
                var best = 0
                var selected: android.net.Uri? = null
                var mime = "audio/*"
                context.contentResolver.query(media, columns, "is_music != 0", null, "title COLLATE NOCASE ASC")?.use { songs ->
                    while (songs.moveToNext()) {
                        val title = com.xilon.jarvis.RomanianLexicon.normalize(songs.getString(1).orEmpty())
                        val artist = com.xilon.jarvis.RomanianLexicon.normalize(songs.getString(2).orEmpty())
                        val score = when {
                            target.isEmpty() || title == target -> 3
                            title.contains(target) -> 2
                            (title + " " + artist).contains(target) -> 1
                            else -> 0
                        }
                        if (score > best) {
                            best = score
                            selected = android.content.ContentUris.withAppendedId(media, songs.getLong(0))
                            mime = songs.getString(3) ?: "audio/*"
                        }
                        if (best == 3) break
                    }
                }
                val track = selected ?: return ToolResult.fail("Nu am găsit o piesă potrivită în muzica salvată pe telefon.")
                val play = Intent(Intent.ACTION_VIEW).setDataAndType(track, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(play)
                ToolResult.ok("Am deschis piesa salvată în playerul audio.", Verification.UNVERIFIED)
            } catch (e: SecurityException) {
                ToolResult.fail("Permite accesul JARVIS la muzica de pe telefon.")
            } catch (e: android.content.ActivityNotFoundException) {
                ToolResult.fail("Nu am găsit un player pentru acest fișier audio.")
            }
        }
        val key = when(action) {
            "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return ToolResult.fail("Comandă media necunoscută.")
        }
        return runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            ToolResult.ok("Comanda media a fost transmisă; redarea nu este verificată.", Verification.UNVERIFIED)
        }.getOrElse { ToolResult.fail("Media key failed: ${it.message?.take(60)}") }
    }
}

class ToggleDndTool : Tool(
    ToolSpec(
        "toggle_dnd", "Turn Do-Not-Disturb on or off (needs Do-Not-Disturb access once).",
        listOf(com.jarvis.mobile.core.tools.ParamSpec("on", "bool", true, "true = DND on, false = off")),
        com.jarvis.mobile.core.tools.Risk.MEDIUM,
    ),
) {
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val nm = ToolEnvironment.context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val on = T.bool(args, "on") ?: return ToolResult.fail("Missing required arg: on (true/false).")
        if (!nm.isNotificationPolicyAccessGranted) {
            val i = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ToolEnvironment.context.startActivity(i) }
            return ToolResult(
                com.jarvis.mobile.core.tools.ToolStatus.REQUIRES_CONFIRMATION,
                "I need \"Do Not Disturb access\" once - grant it to JARVIS in the screen I opened, then ask again.",
                Verification.COULD_NOT_VERIFY,
                recoveryHint = "grant-dnd-access",
            )
        }
        return runCatching {
            nm.setInterruptionFilter(
                if (on) android.app.NotificationManager.INTERRUPTION_FILTER_NONE
                else android.app.NotificationManager.INTERRUPTION_FILTER_ALL,
            )
            val readBack = nm.currentInterruptionFilter
            ToolResult.ok(
                "Do-Not-Disturb turned ${if (on) "on" else "off"}.",
                if ((on && readBack == android.app.NotificationManager.INTERRUPTION_FILTER_NONE) ||
                    (!on && readBack == android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                ) Verification.VERIFIED else Verification.UNVERIFIED,
            )
        }.getOrElse { ToolResult.fail("DND change failed: ${it.message?.take(60)}") }
    }
}

/** Shared launcher used by the intent-based tools in this file and CommTools. */
object CommStart {
    fun start(intent: Intent): ToolResult = runCatching {
        ToolEnvironment.context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ToolResult.ok("Opened.", Verification.UNVERIFIED)
    }.getOrElse { ToolResult.fail("Could not open it: ${it.message?.take(60)}") }
}

class TakeScreenshotTool : Tool(
    ToolSpec("take_screenshot", "Take a screenshot of the current screen (saved by the system).", emptyList(), com.jarvis.mobile.core.tools.Risk.LOW),
) {
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        if (Build.VERSION.SDK_INT < 30) {
            return ToolResult.unavailable("Screenshots via accessibility need Android 11+; this device runs ${Build.VERSION.SDK_INT}.", "use-device-shortcut")
        }
        val svc = com.xilon.jarvis.LocalScreen.service()
            ?: return ToolResult.unavailable("Accessibility service is off - screenshots need it.", "enable-accessibility")
        val ok = runCatching { svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }
            .getOrDefault(false)
        return if (ok) ToolResult.ok("Screenshot requested; check the system notification.", Verification.UNVERIFIED)
        else ToolResult.fail("The system refused the screenshot action.")
    }
}
