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
 fun clean(text: String): String = text.trim().replace(Regex("^(?:(?:jarvis|te rog|poti sa|poți să|vreau sa|vreau să)[,\\s]+)+", RegexOption.IGNORE_CASE), "")
 fun negated(text: String): Boolean = Regex("^(?:nu|sa nu|niciodata|fara sa)\\b").containsMatchIn(normalize(clean(text)))
 private val conjugations = mapOf(
  "deschizi" to "deschide", "deschideti" to "deschide", "deschida" to "deschide",
  "pornesti" to "porneste", "porniti" to "porneste", "porneasca" to "porneste",
  "opresti" to "opreste", "opriti" to "opreste", "opreasca" to "opreste",
  "aprindeti" to "aprinde", "aprinzi" to "aprinde", "aprinda" to "aprinde",
  "stingi" to "stinge", "stingeti" to "stinge", "stinga" to "stinge",
  "setezi" to "seteaza", "setati" to "seteaza", "seteze" to "seteaza",
  "cauti" to "cauta", "cautati" to "cauta", "caute" to "cauta",
  "activezi" to "activeaza", "activati" to "activeaza",
  "dezactivezi" to "dezactiveaza", "dezactivati" to "dezactiveaza",
  "reglezi" to "regleaza", "reglati" to "regleaza",
  "lansezi" to "lanseaza", "lansati" to "lanseaza"
 )
 private val typos = mapOf("deschde" to "deschide", "deshide" to "deschide",
  "pornste" to "porneste", "oprestee" to "opreste", "setaza" to "seteaza",
  "catu a" to "cauta", "actveaza" to "activeaza", "dezactveaza" to "dezactiveaza")
 fun canonicalCommand(text: String): String {
  val cleaned = clean(text)
  val first = cleaned.substringBefore(' ')
  val key = normalize(first)
  val replacement = conjugations[key] ?: typos[key] ?: return cleaned
  return replacement + cleaned.substring(first.length)
 }
 fun parse(text: String): PlannedAction? {
  if (negated(text)) return null
  return entries[normalize(canonicalCommand(text))]?.action
 }
 fun translateReverse(text: String): String? {
  val key = normalize(text)
  val matches = glossary.filterValues { normalize(it) == key }.keys.sorted()
  return matches.takeIf { it.isNotEmpty() }?.joinToString(" / ")
 }
 fun split(text: String): List<String> = clean(text).split(Regex("\\s+(?:și|si|apoi|după aceea|dupa aceea)\\s+|;\\s*", RegexOption.IGNORE_CASE)).map { it.trim() }.filter { it.isNotEmpty() }
 fun compoundPlan(text: String): List<PlannedAction>? {
  val parts = split(text)
  if (parts.size !in 2..20 || parts.any { negated(it) }) return null
  return parts.map { NaturalToolCommands.parse(it) ?: return null }
 }
 fun translateKnown(text: String): String? = entries[normalize(text)]?.english ?: glossary[normalize(text)]
}
