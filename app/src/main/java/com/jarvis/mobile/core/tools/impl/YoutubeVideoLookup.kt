package com.jarvis.mobile.core.tools.impl

import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

internal object YoutubeVideoLookup {
    private val videoId = Regex("[A-Za-z0-9_-]{11}")

    fun first(encodedQuery: String): String? = runCatching {
        val connection = URL("https://www.youtube.com/results?search_query=$encodedQuery&hl=en").openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 5000
            connection.readTimeout = 6000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")
            if (connection.responseCode != 200) return null
            val html = connection.inputStream.bufferedReader().use { reader ->
                val data = StringBuilder()
                val chunk = CharArray(8192)
                while (data.length < 4_000_000) {
                    val count = reader.read(chunk)
                    if (count < 0) break
                    data.append(chunk, 0, count)
                }
                data.toString()
            }
            val marker = "var ytInitialData = "
            val start = html.indexOf(marker)
            if (start < 0) return null
            val end = html.indexOf("</script>", start)
            if (end < 0) return null
            val result = JSONObject(html.substring(start + marker.length, end).trim().trimEnd(';'))
            val sections = result.getJSONObject("contents").getJSONObject("twoColumnSearchResultsRenderer")
                .getJSONObject("primaryContents").getJSONObject("sectionListRenderer").getJSONArray("contents")
            for (section in 0 until sections.length()) {
                val items = sections.optJSONObject(section)?.optJSONObject("itemSectionRenderer")?.optJSONArray("contents") ?: continue
                for (item in 0 until items.length()) {
                    val entry = items.optJSONObject(item) ?: continue
                    val id = entry.optJSONObject("videoRenderer")?.optString("videoId")
                        ?: entry.optJSONObject("lockupViewModel")?.takeIf { it.optString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO" }?.optString("contentId")
                    if (id != null && videoId.matches(id)) return id
                }
            }
            null
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
