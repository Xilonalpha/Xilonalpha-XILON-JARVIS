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
  synchronized(this) {
   corpus?.words?.close()
   corpus = null
   synonymCache.clear()
   corpusAssets = context.applicationContext.assets
   indexFile = java.io.File(context.noBackupFilesDir, "romanian-lexicon-3.3.10-v1.db")
  }
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
 @Volatile private var corpusAssets: android.content.res.AssetManager? = null
 private data class Rule(val prefix: Boolean, val flag: String, val strip: String, val add: String, val condition: Regex)
 private class Corpus(val words: android.database.sqlite.SQLiteDatabase, val rules: List<Rule>) {
  fun flags(word: String): String? = words.rawQuery("SELECT group_concat(flags, '') FROM lex WHERE word=?", arrayOf(word)).use { cursor ->
   if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
  }
 }
 @Volatile private var indexFile: java.io.File? = null
 @Volatile private var corpus: Corpus? = null
 @Synchronized private fun loadCorpus(): Corpus? {
  corpus?.let { return it }
  val assets = corpusAssets ?: return null
  return try {
   val file = indexFile ?: return null
   val rules = ArrayList<Rule>()
   assets.open("ro_hunspell/ro_RO.aff").bufferedReader(Charsets.UTF_8).useLines { lines ->
    lines.forEach { line ->
     val p = line.trim().split(Regex("\\s+"))
     if (p.size >= 5 && (p[0] == "PFX" || p[0] == "SFX")) {
      val prefix = p[0] == "PFX"
      val condition = runCatching { Regex(if (prefix) "^" + p[4] else p[4] + "$") }.getOrNull()
      if (condition != null) rules.add(Rule(prefix, p[1], p[2].takeUnless { it == "0" } ?: "", p[3].substringBefore('/').takeUnless { it == "0" } ?: "", condition))
     }
    }
   }
   val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
   try {
    if (db.version != 1) {
     db.beginTransaction()
     try {
      db.execSQL("DROP TABLE IF EXISTS lex")
      db.execSQL("CREATE TABLE lex(word TEXT NOT NULL, flags TEXT NOT NULL, PRIMARY KEY(word,flags))")
      val statement = db.compileStatement("INSERT OR IGNORE INTO lex VALUES (?,?)")
      var count = 0
      try {
       assets.open("ro_hunspell/ro_RO.dic").bufferedReader(Charsets.UTF_8).useLines { lines ->
        lines.drop(1).forEach { line ->
         val token = line.trim().substringBefore(' ').substringBefore('\t')
         val word = token.substringBefore('/').lowercase(Locale.ROOT).replace('ş','ș').replace('ţ','ț')
         if (word.isNotEmpty()) {
          statement.bindString(1, word)
          statement.bindString(2, token.substringAfter('/', ""))
          statement.executeInsert()
          count++
         }
        }
       }
      } finally { statement.close() }
      check(count > 10000) { "Incomplete Romanian dictionary" }
      db.version = 1
      db.setTransactionSuccessful()
     } finally { db.endTransaction() }
    }
    Corpus(db, rules).also { corpus = it }
   } catch (e: Exception) { db.close(); throw e }
  } catch (_: java.io.IOException) { null }
    catch (_: android.database.SQLException) { null }
    catch (_: IllegalStateException) { null }
 }
 @Synchronized fun dictionaryContains(word: String): Boolean {
  val c = loadCorpus() ?: return false
  val w = word.lowercase(Locale.ROOT).replace('ş','ș').replace('ţ','ț')
  if (c.flags(w) != null) return true
  return c.rules.any { r ->
   val stem = if (r.prefix && w.startsWith(r.add)) r.strip + w.removePrefix(r.add)
    else if (!r.prefix && w.endsWith(r.add)) w.removeSuffix(r.add) + r.strip else return@any false
   c.flags(stem)?.contains(r.flag) == true && r.condition.containsMatchIn(stem)
  }
 }
 private fun oneEdit(a: String, b: String): Boolean {
  if (a == b || kotlin.math.abs(a.length - b.length) > 1) return false
  if (a.length == b.length) {
   val d = a.indices.filter { a[it] != b[it] }
   return d.size == 1 || (d.size == 2 && d[1] == d[0]+1 && a[d[0]] == b[d[1]] && a[d[1]] == b[d[0]])
  }
  val longer = if (a.length > b.length) a else b
  val shorter = if (a.length > b.length) b else a
  return longer.indices.any { longer.removeRange(it,it+1) == shorter }
 }
 private fun correctVerb(first: String): String? {
  if (first.length !in 5..40) return null
  val possible = verbs.distinct().filter { oneEdit(normalize(first), it) }
  if (possible.isEmpty() || dictionaryContains(first)) return null
  val candidates = possible.filter { dictionaryContains(it) }
  return candidates.singleOrNull()
 }
 private val synonymCache = object : LinkedHashMap<String, List<String>>(64, 0.75f, true) {
  override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean = size > 64
 }
 @Synchronized fun synonyms(word: String): List<String> {
  val key = normalize(word)
  if (key.isBlank() || key.length > 80) return emptyList()
  synonymCache[key]?.let { return it }
  val assets = corpusAssets ?: return emptyList()
  val result = linkedSetOf<String>()
  try {
   assets.open("ro_hunspell/th_ro_RO_v2.dat").bufferedReader(Charsets.UTF_8).use { reader ->
    val encoding = reader.readLine()?.trim()?.removePrefix("\uFEFF") ?: return emptyList()
    if (!encoding.equals("UTF-8", true)) return emptyList()
    while (true) {
     val header = reader.readLine() ?: break
     val count = header.substringAfterLast('|').toIntOrNull() ?: continue
     if (count !in 0..1000) break
     val matches = normalize(header.substringBefore('|')) == key
     repeat(count) {
      val line = reader.readLine() ?: return emptyList()
      if (matches) line.split('|').drop(1).forEach { value ->
       val term = value.trim()
       if (term.isNotEmpty() && normalize(term) != key && result.size < 64) result.add(term)
      }
     }
     if (matches) break
    }
   }
  } catch (_: java.io.IOException) { return emptyList() }
  return result.toList().also { synonymCache[key] = it }
 }
 // Synonyms are suggestions only: never silently execute a different action.
 fun suggestedPhrases(text: String): List<String> {
  if (negated(text) || split(text).size != 1) return emptyList()
  val cleaned = normalize(canonicalCommand(text))
  if (entries.containsKey(cleaned)) return emptyList()
  val first = cleaned.substringBefore(' ')
  val tail = cleaned.removePrefix(first)
  return synonyms(first).map { normalize(it) + tail }
   .filter { entries.containsKey(it) }.distinct().take(8)
 }
 private val typos = mapOf("deschde" to "deschide", "deshide" to "deschide",
  "pornste" to "porneste", "oprestee" to "opreste", "setaza" to "seteaza",
  "catu a" to "cauta", "actveaza" to "activeaza", "dezactveaza" to "dezactiveaza")
 fun canonicalCommand(text: String): String {
  val cleaned = clean(text)
  val first = cleaned.substringBefore(' ')
  val key = normalize(first)
  val replacement = conjugations[key] ?: typos[key] ?: correctVerb(first) ?: return cleaned
  return replacement + cleaned.substring(first.length)
 }
 fun parse(text: String): PlannedAction? {
  if (negated(text)) return null
  return entries[normalize(clean(text))]?.action ?: entries[normalize(canonicalCommand(text))]?.action
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
