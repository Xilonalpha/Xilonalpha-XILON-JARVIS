package com.xilon.jarvis
import java.text.Normalizer
import java.util.Locale
/** Shared Romanian text normalization for local speech commands. */
internal object VoiceTextNormalizer {
 fun normalize(text:String):String = Normalizer.normalize(text.lowercase(Locale.ROOT),Normalizer.Form.NFD)
  .replace(Regex("\\p{M}+"),"").replace(Regex("[^a-z0-9]+")," ").trim()
 fun containsPhrase(haystack:String,needle:String):Boolean =
  needle.isNotBlank() && (" "+haystack+" ").contains(" "+needle+" ")
}
