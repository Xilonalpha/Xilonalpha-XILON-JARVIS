package com.xilon.jarvis
import java.text.Normalizer
import java.util.Locale
/** Shared Romanian text normalization for local speech commands. */
internal object VoiceTextNormalizer {
 private val marks=Regex("\\p{M}+")
 private val separators=Regex("[^a-z0-9]+")
 fun normalize(text:String):String = Normalizer.normalize(text.lowercase(Locale.ROOT),Normalizer.Form.NFD)
  .replace(marks,"").replace(separators," ").trim()
 fun containsPhrase(haystack:String,needle:String):Boolean =
  needle.isNotBlank() && (" "+haystack+" ").contains(" "+needle+" ")
}
