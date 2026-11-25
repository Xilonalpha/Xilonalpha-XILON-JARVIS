/*
MIT License

Copyright (c) 2026 ansaribilal14

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

*/
package com.jarvis.mobile.core.triggers


/**
 * What makes a skill fire on its own (OpenTasker/Easer-grade triggers, scoped
 * to what JARVIS can observe without extra hardware plumbing):
 *
 *  - MANUAL      run only when the user taps Run (default; every skill has this)
 *  - APP_OPEN    the named app came to the foreground (a11y window events)
 *  - APP_CLOSE   the named app left the foreground
 *  - TIME        daily at hour:minute (exact AlarmManager, fallback inexact)
 *  - NOTIFICATION a notification from [pkg] whose title/text contains [text]
 *  - BATTERY_LOW the system broadcasts BATTERY_LOW
 *
 * Level vs pulse: APP_OPEN/CLOSE/TIME/NOTIFICATION/BATTERY_LOW are pulses -
 * they fire the skill when they happen, honoring [cooldownSec] against
 * re-firing storms (the OpenTasker grace-period lesson).
 */
data class SkillTrigger(
    val type: String,                       // MANUAL | APP_OPEN | APP_CLOSE | TIME | NOTIFICATION | BATTERY_LOW
    val pkg: String? = null,                // APP_OPEN / APP_CLOSE / NOTIFICATION
    val hour: Int = -1,                     // TIME
    val minute: Int = -1,                   // TIME
    val text: String? = null,               // NOTIFICATION substring (title or text)
    val cooldownSec: Long = 60,             // minimum seconds between fires
) {
    fun isValid(): Boolean {
        if (type !in TYPES || cooldownSec !in 0L..86400L) return false
        if (pkg != null && (pkg.isBlank() || pkg.length > 255)) return false
        if (text != null && text.length > 512) return false
        return when (type) {
            "TIME" -> hour in 0..23 && minute in 0..59
            "APP_OPEN", "APP_CLOSE" -> !pkg.isNullOrBlank()
            else -> true
        }
    }

    fun matchesApp(packageName: String, opened: Boolean): Boolean =
        isValid() && type == (if (opened) "APP_OPEN" else "APP_CLOSE") && pkg == packageName

    fun matchesNotification(packageName: String, title: String, body: String): Boolean =
        isValid() && type == "NOTIFICATION" && (pkg == null || pkg == packageName) &&
            (text.isNullOrBlank() || title.contains(text, true) || body.contains(text, true))

    fun describe(): String = when (type) {
        "APP_OPEN" -> "When ${pkg?.substringBefore('.') ?: "an app"} opens"
        "APP_CLOSE" -> "When ${pkg?.substringBefore('.') ?: "an app"} closes"
        "TIME" -> {
            val h = hour.coerceIn(0, 23)
            val m = minute.coerceIn(0, 59)
            "Daily at %02d:%02d".format(h, m)
        }
        "NOTIFICATION" -> "On notification from ${pkg?.substringBefore('.') ?: "any app"}" +
            (text?.takeIf { it.isNotBlank() }?.let { " containing \"$it\"" } ?: "")
        "BATTERY_LOW" -> "When battery is low"
        else -> "Manual"
    }

    companion object {
        val TYPES = listOf("MANUAL", "APP_OPEN", "APP_CLOSE", "TIME", "NOTIFICATION", "BATTERY_LOW")
    }
}
