/*
Upstream: https://github.com/judejulius/jarvis/blob/5dd9934361317258bb1e35654fc5b33dcd861b7e/app/src/main/java/com/jarvis/assistant/memory/SensitiveFilter.kt
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
package com.jarvis.assistant.memory

/**
 * Keeps secrets out of Jarvis's memory. Passwords, one-time codes, card and ID numbers are never saved, even if the
 * user says "remember". Pure logic.
 */
object SensitiveFilter {
    private val WORDS = Regex(
        """\b(password|passcode|pass word|pin|otp|one time (?:password|code)|cvv|cvc|card number|credit card|debit card|aadhaar|aadhar|""" +
            """pan card|ssn|social security|bank account|account number|ifsc|upi pin|secret key|private key|api key|access token|login code|recovery code)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val LONG_NUMBER = Regex("""(?:\d[ -]?){12,}""")

    fun isSensitive(text: String): Boolean = WORDS.containsMatchIn(text) || LONG_NUMBER.containsMatchIn(text)

    const val REFUSAL = "I won't store that, because it looks like a password or a private number."
}
