package com.xilon.jarvis

import android.content.Context
import com.jarvis.mobile.core.tools.PlannedAction
import kotlinx.serialization.json.*
import java.text.Normalizer
import java.util.Locale

object RomanianLexicon {
 private data class Entry(val action: PlannedAction, val english: String)
 @Volatile private var entries: Map<String, Entry> = emptyMap()
 @Volatile private var glossary: Map<String, String> = emptyMap()
 @Volatile var verbs: List<String> = emptyList()
  private set
 fun normalize(text: String): String =
  Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
   .replace(Regex("\\p{M}+"), "").trim().trimEnd('!','?','.')
   .replace(Regex("\\s+"), " ")
 fun initialize(context: Context) {
  val root = Json.parseToJsonElement(context.assets.open("romanian_lexicon.json").bufferedReader().use { it.readText() }).jsonObject
  val map = mutableMapOf<String, Entry>()
  for (item in root.getValue("commands").jsonArray) {
   val obj = item.jsonObject
   val entry = Entry(PlannedAction(obj.getValue("tool").jsonPrimitive.content, obj.getValue("args").jsonObject), obj.getValue("english").jsonPrimitive.content)
   for (phrase in obj.getValue("phrases").jsonArray) map[normalize(phrase.jsonPrimitive.content)] = entry
  }
  glossary = root.getValue("translations").jsonObject.mapValues { it.value.jsonPrimitive.content }
  verbs = root.getValue("verbs").jsonArray.map { normalize(it.jsonPrimitive.content) }
  entries = map.toMap()
 }
 fun parse(text: String): PlannedAction? = entries[normalize(text)]?.action
 fun translateKnown(text: String): String? = entries[normalize(text)]?.english ?: glossary[normalize(text)]
}
