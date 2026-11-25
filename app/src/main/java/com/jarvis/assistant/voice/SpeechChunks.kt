/*
Upstream: https://github.com/judejulius/jarvis/blob/master/app/src/main/java/com/jarvis/assistant/voice/SpeechChunks.kt
Upstream blob: c0f3137507d7169274c51cd932d7757d92ff1e41
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
package com.jarvis.assistant.voice

/**
 * Cuts a reply into pieces an on-device voice can make quickly. Without this, one long sentence
 * with commas is made in a single run, and nothing is heard until all of it is done.
 *
 * A piece ends at the end of a sentence when one fits, otherwise at a comma, otherwise at a space.
 */
object SpeechChunks {
    /** A sentence ends at . ! ? followed by a space, so decimals like 0.5 stay whole. */
    private val SENTENCE = Regex(".+?(?:[.!?…]+[\"')\\]]*(?=\\s|$)|$)\\s*", RegexOption.DOT_MATCHES_ALL)

    /**
     * The next piece of [text], at most [limit] characters, and what is left after it. Takes as
     * many whole sentences as fit; if not even the first fits, it is cut at a comma or a space.
     */
    fun take(text: String, limit: Int): Pair<String, String> {
        val rest = text.trim()
        if (rest.length <= limit) return rest to ""
        var end = 0
        for (sentence in SENTENCE.findAll(rest)) {
            val stop = sentence.range.first + sentence.value.trimEnd().length
            if (stop > limit) break
            end = stop
        }
        if (end == 0) end = cutPoint(rest, limit)
        return rest.substring(0, end).trim() to rest.substring(end).trim()
    }

    /** All of [text] in pieces: the first at most [first] long so speech starts sooner, the rest at most [max]. */
    fun split(text: String, first: Int = 80, max: Int = 150): List<String> {
        val out = mutableListOf<String>()
        var rest = text.trim()
        while (rest.isNotEmpty()) {
            val (piece, after) = take(rest, if (out.isEmpty()) first else max)
            out += piece
            rest = after
        }
        return out
    }

    /** Where to cut [s] so the first part is at most [limit] long: at a comma if there is one, else a space. */
    private fun cutPoint(s: String, limit: Int): Int {
        val head = s.substring(0, minOf(s.length, limit + 1))
        val clause = maxOf(head.lastIndexOf(", "), head.lastIndexOf("; "), head.lastIndexOf(": "))
        if (clause >= limit / 3.0) return clause + 1
        val space = head.lastIndexOf(' ')
        return if (space > 0) space else limit
    }
}
