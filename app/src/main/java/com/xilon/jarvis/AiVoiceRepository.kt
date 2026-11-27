package com.xilon.jarvis

enum class VoicePersona(
    val id: String,
    val displayName: String,
    val tagline: String,
    val speechRate: Float,
    val pitch: Float,
    val preferLowerTone: Boolean = false,
) {
    SOL(
        id = "sol",
        displayName = "Sol",
        tagline = "Cald și natural",
        speechRate = 1.04f,
        pitch = 1.01f,
    ),
    NOVA(
        id = "nova",
        displayName = "Nova",
        tagline = "Energic și clar",
        speechRate = 1.12f,
        pitch = 1.07f,
    ),
    COVE(
        id = "cove",
        displayName = "Cove",
        tagline = "Calm și grav",
        speechRate = 0.96f,
        pitch = 0.88f,
        preferLowerTone = true,
    ),
    BREEZE(
        id = "breeze",
        displayName = "Breeze",
        tagline = "Conversațional",
        speechRate = 1.07f,
        pitch = 1.04f,
    ),
    EMBER(
        id = "ember",
        displayName = "Ember",
        tagline = "Expresiv și rapid",
        speechRate = 1.16f,
        pitch = 0.96f,
        preferLowerTone = true,
    );

    companion object {
        fun fromId(id: String?): VoicePersona = entries.firstOrNull { it.id == id } ?: SOL
    }
}

/**
 * Formats markdown/technical replies into natural spoken Romanian cadence so on-device TTS
 * sounds fluid and conversational instead of reading raw symbols.
 */
object ConversationalSpeechFormatter {
    private val CODE_BLOCK = Regex("```[\\s\\S]*?```")
    private val INLINE_CODE = Regex("`([^`]+)`")
    private val MARKDOWN_LINKS = Regex("\\[([^\\]]+)]\\([^)]+\\)")
    private val URL_REGEX = Regex("https?://\\S+")
    private val BULLET_PREFIX = Regex("(?m)^\\s*(?:[-*•]+|\\d+\\.)\\s+")
    private val HEADING_PREFIX = Regex("(?m)^\\s*#{1,6}\\s+")
    private val MULTI_SPACE = Regex("\\s+")

    fun cleanForSpeech(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw
            .replace(CODE_BLOCK, " cod sursă omis. ")
            .replace(MARKDOWN_LINKS, "$1")
            .replace(URL_REGEX, "")
            .replace(INLINE_CODE, "$1")
            .replace(HEADING_PREFIX, "")
            .replace(BULLET_PREFIX, "")
            .replace("**", "")
            .replace("__", "")
            .replace("*", "")
            .replace("—", ", ")
            .replace("–", ", ")
            .replace("•", ", ")
            .replace("|", ", ")
            .replace("MiB", " megabytes")
            .replace("GiB", " gigabytes")
            .replace("GB", " gigabytes")
            .replace("MB", " megabytes")
            .replace("°C", " grade Celsius")
            .replace("%", " la sută")
            .replace("\n\n", ". ")
            .replace("\n", ". ")

        text = MULTI_SPACE.replace(text, " ").trim()
        text = text
            .replace("..", ".")
            .replace(",.", ".")
            .replace(".,", ",")
            .replace(" .", ".")
            .trim()
        return text
    }

    /**
     * Extracts complete speakable sentences from an incrementally growing local LLM stream buffer.
     * Returns Pair(list of newly ready sentences, new consumed index in fullText).
     */
    fun extractReadySentences(fullText: String, consumedIndex: Int): Pair<List<String>, Int> {
        if (consumedIndex >= fullText.length) return emptyList<String>() to consumedIndex
        val remainder = fullText.substring(consumedIndex)
        val ready = mutableListOf<String>()
        var localOffset = 0

        for (i in remainder.indices) {
            val ch = remainder[i]
            val isTerminator = ch == '.' || ch == '!' || ch == '?' || ch == '…' || ch == '\n'
            if (!isTerminator) continue

            // Keep decimals like 3.14 intact
            if (ch == '.' && i > 0 && i + 1 < remainder.length && remainder[i - 1].isDigit() && remainder[i + 1].isDigit()) {
                continue
            }

            val nextIsBoundary = ch == '\n' || (i + 1 < remainder.length && remainder[i + 1].isWhitespace())
            if (!nextIsBoundary) continue

            val segment = remainder.substring(localOffset, i + 1).trim()
            if (segment.length >= 12) {
                val cleaned = cleanForSpeech(segment)
                if (cleaned.isNotBlank()) ready += cleaned
                localOffset = i + 1
            }
        }

        if (localOffset == 0 && remainder.length > 110) {
            val commaIdx = remainder.lastIndexOf(", ", startIndex = minOf(remainder.length - 1, 130))
            if (commaIdx > 35) {
                val cleaned = cleanForSpeech(remainder.substring(0, commaIdx + 1))
                if (cleaned.isNotBlank()) ready += cleaned
                localOffset = commaIdx + 1
            }
        }

        return ready to (consumedIndex + localOffset)
    }
}
