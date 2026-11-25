/*
Upstream: https://github.com/judejulius/jarvis/blob/5dd9934361317258bb1e35654fc5b33dcd861b7e/app/src/main/java/com/jarvis/assistant/agent/SensitiveActions.kt
MIT License

Copyright (c) 2026 Mohammad Joud Julius

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
package com.jarvis.assistant.agent

/**
 * Code-level safeguard for actions that cannot be undone: sending, calling, posting, paying. While "Ask before calls
 * and messages" is on, the phone agent is not allowed to tap such a button until the user has said yes to a question
 * asked in this task. This does not depend on the language model following its instructions.
 */
object SensitiveActions {
    private val SENSITIVE = Regex(
        """^\s*(send|call|dial|post|tweet|publish|share|reply|pay|transfer|place order|buy now|confirm payment|delete|submit)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val YES = Regex(
        """\b(yes|yeah|yep|yup|sure|ok|okay|confirm|confirmed|go ahead|do it|send it|haan|han|ha|theek hai|thik hai|kar do|bhej do)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val NO = Regex("""\b(no|nope|don'?t|cancel|stop|wait|nahi|nahin|mat|ruko)\b""", RegexOption.IGNORE_CASE)
    private val YES_HINDI = listOf("हाँ", "हां", "ठीक है", "कर दो", "भेज दो")
    private val NO_HINDI = listOf("नहीं", "मत", "रुको")

    /** True if tapping a control with this label sends, places or deletes something. */
    fun needsConfirmation(label: String): Boolean = label.isNotBlank() && SENSITIVE.containsMatchIn(label)

    /** True only for a clear yes. Anything unclear, or containing a no, counts as "not confirmed". */
    fun isYes(answer: String?): Boolean {
        if (answer.isNullOrBlank()) return false
        val no = NO.containsMatchIn(answer) || NO_HINDI.any { it in answer }
        val yes = YES.containsMatchIn(answer) || YES_HINDI.any { it in answer }
        return yes && !no
    }
}
