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
 @Volatile private var reverseGlossary: Map<String, String> = emptyMap()
 private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
 @Volatile private var indexState = "not_initialized"
 fun dictionaryStatus(): String = indexState
 fun warmUp() {
  synchronized(this) {
   if (indexState == "building" || indexState == "ready" || corpusAssets == null) return
   indexState = "building"
  }
  worker.execute {
   synchronized(this) {
    indexState = if (loadCorpus() != null) "ready" else "unavailable"
   }
  }
 }
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
  glossary = root.getValue("translations").jsonObject.entries.associate { normalize(it.key) to it.value.jsonPrimitive.content }
  reverseGlossary = root.getValue("translations").jsonObject.entries.groupBy { normalize(it.value.jsonPrimitive.content) }
   .mapValues { (_, values) -> values.map { it.key }.sorted().joinToString(" / ") }
  verbs = root.getValue("verbs").jsonArray.map { normalize(it.jsonPrimitive.content) }
  entries = map.toMap()
  synchronized(this) {
   corpus?.words?.close()
   corpus = null
   synonymCache.clear()
   corpusAssets = context.applicationContext.assets
   indexFile = java.io.File(context.noBackupFilesDir, "romanian-lexicon-v3.db")
   indexState = "idle"
  }
  warmUp()
  initializeDefinitions(context.applicationContext)
 }
 fun clean(text: String): String = text.trim().replace(Regex("^(?:(?:jarvis|te rog|poti sa|poți să|vreau sa|vreau să)[,\\s]+)+", RegexOption.IGNORE_CASE), "")
 fun negated(text: String): Boolean {
  val key = normalize(clean(text))
  return Regex("^(?:nu|sa nu|niciodata|fara sa|mai bine nu|te rog nu|as prefera sa nu|nu vreau sa|nu trebuie sa)\\b").containsMatchIn(key)
 }
 data class LanguageReview(val original: String, val normalized: String, val blocked: Boolean,
  val spelling: List<String>, val synonyms: List<String>, val translation: String?, val dictionaryState: String)
 // Read-only analysis, suitable for a conversation UI; never dispatches tools.
 fun review(text: String): LanguageReview {
  val blocked = negated(text) || split(text).any { negated(it) }
  val lexical = if (blocked) emptyList() else sentenceSuggestions(text)
  return LanguageReview(text, normalize(text), blocked,
   if (blocked) emptyList() else (spellingSuggestions(text) + lexical).distinct().take(12),
   if (blocked) emptyList() else suggestedPhrases(text),
   translateKnown(text) ?: translateReverse(text) ?: englishDefinitions(text).takeIf { it.isNotEmpty() }?.joinToString(" / ")?.let { "Definiții EN: " + it }, dictionaryStatus())
 }
 fun reviewAsync(text: String, callback: (LanguageReview) -> Unit) {
  worker.execute {
   val result = review(text)
   android.os.Handler(android.os.Looper.getMainLooper()).post { callback(result) }
  }
 }
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
  "lansezi" to "lanseaza", "lansati" to "lanseaza",
  "navighezi" to "navigheaza", "navigati" to "navigheaza",
  "derulezi" to "deruleaza", "derulati" to "deruleaza",
  "listezi" to "listeaza", "listati" to "listeaza",
  "arati" to "arata", "aratati" to "arata"
 )
 @Volatile private var corpusAssets: android.content.res.AssetManager? = null
 private data class Rule(val prefix: Boolean, val flag: String, val strip: String, val add: String, val condition: Regex, val cross: Boolean, val continuation: String)
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
   val crossClasses = mutableSetOf<Pair<String,String>>()
   assets.open("ro_hunspell/ro_RO.aff").bufferedReader(Charsets.UTF_8).useLines { lines ->
    lines.forEach { line ->
     val p = line.trim().split(Regex("\\s+"))
     if (p.size == 4 && p[0] in setOf("PFX","SFX") && p[2] == "Y") crossClasses.add(p[0] to p[1])
     if (p.size >= 5 && (p[0] == "PFX" || p[0] == "SFX")) {
      val prefix = p[0] == "PFX"
      val condition = runCatching { Regex(if (prefix) "^" + p[4] else p[4] + "$") }.getOrNull()
      if (condition != null) rules.add(Rule(prefix, p[1], p[2].takeUnless { it == "0" } ?: "", p[3].substringBefore('/').takeUnless { it == "0" } ?: "", condition, (p[0] to p[1]) in crossClasses, p[3].substringAfter('/', "")))
     }
    }
   }
   val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
   try {
    if (db.version != 3) {
     db.beginTransaction()
     try {
      db.execSQL("DROP TABLE IF EXISTS lex")
      db.execSQL("DROP TABLE IF EXISTS folded")
      db.execSQL("DROP TABLE IF EXISTS thesaurus")
      db.execSQL("CREATE TABLE lex(word TEXT NOT NULL, flags TEXT NOT NULL, PRIMARY KEY(word,flags))")
      db.execSQL("CREATE TABLE IF NOT EXISTS folded(key TEXT NOT NULL, word TEXT NOT NULL, PRIMARY KEY(key,word))")
      val folded = db.compileStatement("INSERT OR IGNORE INTO folded VALUES (?,?)")
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
          folded.bindString(1, normalize(word))
          folded.bindString(2, word)
          folded.executeInsert()
          count++
         }
        }
       }
      } finally { statement.close(); folded.close() }
      check(count > 10000) { "Incomplete Romanian dictionary" }
      db.execSQL("CREATE TABLE thesaurus(key TEXT NOT NULL, term TEXT NOT NULL, PRIMARY KEY(key,term))")
      val syn = db.compileStatement("INSERT OR IGNORE INTO thesaurus VALUES (?,?)")
      try {
       assets.open("ro_hunspell/th_ro_RO_v2.dat").bufferedReader(Charsets.UTF_8).use { reader ->
        check(reader.readLine()?.trim()?.removePrefix("\uFEFF")?.equals("UTF-8", true) == true)
        while (true) {
         val header = reader.readLine() ?: break
         val count = header.substringAfterLast('|').toIntOrNull() ?: continue
         check(count in 0..1000)
         val key = normalize(header.substringBefore('|'))
         repeat(count) {
          val line = reader.readLine() ?: error("Incomplete thesaurus")
          for (term in line.split('|').drop(1).map { it.trim() }) {
           if (term.isNotBlank() && normalize(term) != key) {
            syn.bindString(1,key); syn.bindString(2,term); syn.executeInsert()
           }
          }
         }
        }
       }
      } finally { syn.close() }
      db.version = 3
      db.setTransactionSuccessful()
     } finally { db.endTransaction() }
    }
    Corpus(db, rules).also { corpus = it }
   } catch (e: Exception) { db.close(); throw e }
  } catch (_: java.io.IOException) { null }
    catch (_: android.database.SQLException) { null }
    catch (_: IllegalStateException) { null }
 }
 fun dictionaryContains(word: String): Boolean {
  if (indexState != "ready") return false
  return synchronized(this) { dictionaryContainsReady(word) }
 }
 private fun dictionaryContainsReady(word: String): Boolean {
  val c = corpus ?: return false
  val w = word.lowercase(Locale.ROOT).replace('ş','ș').replace('ţ','ț')
  if (c.flags(w) != null) return true
  if (w == normalize(w) && c.words.rawQuery("SELECT 1 FROM folded WHERE key=? LIMIT 1", arrayOf(w)).use { it.moveToFirst() }) return true
  return morphologicalRoots(w).isNotEmpty()
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
  if (indexState != "ready" || first.length !in 5..40) return null
  val possible = verbs.distinct().filter { oneEdit(normalize(first), it) }
  if (possible.isEmpty() || dictionaryContains(first)) return null
  val candidates = possible.filter { dictionaryContains(it) }
  return candidates.singleOrNull()
 }
 private val synonymCache = object : LinkedHashMap<String, List<String>>(64, 0.75f, true) {
  override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean = size > 64
 }
 fun synonyms(word: String): List<String> {
  if (indexState != "ready") return emptyList()
  val key = normalize(word)
  if (key.isBlank() || key.length > 80) return emptyList()
  return synchronized(this) {
   synonymCache[key]?.let { return@synchronized it }
   val c = corpus ?: return@synchronized emptyList()
   val result = linkedSetOf<String>()
   val keys = (listOf(key) + morphologicalRoots(word).map { normalize(it) }).distinct().take(12)
   for (root in keys) {
    c.words.rawQuery("SELECT term FROM thesaurus WHERE key=? ORDER BY term LIMIT 64", arrayOf(root)).use { cursor ->
     while (cursor.moveToNext() && result.size < 64) result.add(cursor.getString(0))
    }
   }
   (result.toList() + corpusSynonyms(word)).distinct().take(64).also { synonymCache[key] = it }
  }
 }
 // Reverse the imported affix rules; return only stems licensed by dictionary flags.
 fun morphologicalRoots(word: String): List<String> {
  if (indexState != "ready" || word.length !in 2..80) return emptyList()
  return synchronized(this) {
   val c = corpus ?: return@synchronized emptyList()
   val w = word.lowercase(Locale.ROOT).replace('ş','ș').replace('ţ','ț')
   val result = linkedSetOf<String>()
   if (c.flags(w) != null) result.add(w)
   fun reverse(rule: Rule, form: String): String? {
    val stem = if (rule.prefix && form.startsWith(rule.add)) rule.strip + form.removePrefix(rule.add)
     else if (!rule.prefix && form.endsWith(rule.add)) form.removeSuffix(rule.add) + rule.strip else return null
    return stem.takeIf { it.isNotEmpty() && rule.condition.containsMatchIn(it) }
   }
   val flagCache = HashMap<String,String?>()
   fun flags(stem: String): String? = if (flagCache.containsKey(stem)) flagCache[stem] else c.flags(stem).also { flagCache[stem] = it }
   for (outer in c.rules) {
    val intermediate = reverse(outer,w) ?: continue
    if (flags(intermediate)?.contains(outer.flag) == true) result.add(intermediate)
    for (inner in c.rules) {
     val cross = inner.prefix != outer.prefix && inner.cross && outer.cross
     val continuation = inner.continuation.contains(outer.flag)
     if (!cross && !continuation) continue
     val root = reverse(inner,intermediate) ?: continue
     val rootFlags = flags(root) ?: continue
     if (rootFlags.contains(inner.flag) && (continuation || rootFlags.contains(outer.flag))) result.add(root)
     if (result.size >= 24) break
    }
    if (result.size >= 24) break
   }
   result.toList()
  }
 }
 // Synonyms are suggestions only: never silently execute a different action.
 fun corpusSynonyms(word: String): List<String> {
  if (definitionsState != "ready" || word.isBlank() || word.length > 160) return emptyList()
  return synchronized(this) {
   val db = definitionsDb ?: return@synchronized emptyList()
   val keys = (listOf(normalize(word)) + grammarMatches(word).map { normalize(it.lemma) }).distinct().take(12)
   val result = linkedSetOf<String>()
   for (key in keys) {
    db.rawQuery("SELECT term FROM relations WHERE key=? ORDER BY term LIMIT 32",arrayOf(key)).use { cursor ->
     while (cursor.moveToNext() && result.size < 32) result.add(cursor.getString(0))
    }
   }
   result.toList()
  }
 }
 fun suggestedPhrases(text: String): List<String> {
  if (text.length > 160 || negated(text) || split(text).size != 1) return emptyList()
  val cleaned = normalize(clean(text))
  if (entries.containsKey(cleaned)) return emptyList()
  val tokens = cleaned.split(' ')
  if (tokens.size !in 2..12 || tokens.any { it.isBlank() || !it.all(Char::isLetter) }) return emptyList()
  // Corpus alternatives are evidence for clarification, never executable actions.
  fun roots(word: String): Set<String> = (listOf(word) +
   grammarMatches(word).map { normalize(it.lemma) } +
   morphologicalRoots(word).map { normalize(it) }).toSet()
  val inputRoots = tokens.map { roots(it) }
  val first = tokens.first()
  val related = (synonyms(first) + corpusSynonyms(first)).map { normalize(it) }.toSet()
  val candidates = ArrayList<Pair<String,Int>>()
  for (phrase in entries.keys) {
   val parts = phrase.split(' ')
   if (parts.size != tokens.size) continue
   val differences = tokens.indices.filter { tokens[it] != parts[it] }
   if (differences.size !in 1..2) continue
   var score = 0
   var valid = true
   for (i in differences) {
    if (inputRoots[i].intersect(roots(parts[i])).isNotEmpty()) score += 2
    else if (i == 0 && parts[i] in related) score += 1
    else { valid = false; break }
   }
   if (valid) candidates.add(phrase to score)
  }
  return candidates.sortedWith(compareByDescending<Pair<String,Int>> { it.second }.thenBy { it.first }).map { it.first }.distinct().take(8)
 }
 // Only suggest corrections to complete registered phrases; preserve arbitrary payloads.
 fun spellingSuggestions(text: String): List<String> {
  if (negated(text) || split(text).size != 1 || indexState != "ready") return emptyList()
  val key = normalize(clean(text))
  if (key.length > 160 || entries.containsKey(key)) return emptyList()
  val tokens = key.split(' ')
  if (tokens.size > 12 || tokens.any { !it.all(Char::isLetter) }) return emptyList()
  return entries.keys.asSequence().filter { phrase ->
   val candidate = phrase.split(' ')
   if (candidate.size != tokens.size) return@filter false
   val changed = tokens.indices.filter { tokens[it] != candidate[it] }
   if (changed.size != 1) return@filter false
   val i = changed.single()
   tokens[i].length >= 5 && oneEdit(tokens[i], candidate[i]) &&
    !dictionaryContains(tokens[i]) && dictionaryContains(candidate[i])
  }.sorted().take(8).toList()
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
  if (negated(text) || split(text).any { negated(it) }) return null
  return entries[normalize(clean(text))]?.action ?: entries[normalize(canonicalCommand(text))]?.action
 }
 // Return alternatives per word, never rewrite or execute the original sentence.
 fun sentenceSuggestions(text: String): List<String> {
  if (indexState != "ready" || text.length > 500) return emptyList()
  val tokens = Regex("[\\p{L}\\p{M}]+").findAll(text)
   .map { it.value }.filter { it.length in 3..40 }.distinct().take(24)
  val result = ArrayList<String>()
  for (token in tokens) {
   if (result.size >= 8) break
   val exact = token.lowercase(Locale.ROOT).replace('ş','ș').replace('ţ','ț')
   if (dictionaryContains(token)) {
    if (exact != normalize(exact)) continue
    val accents = dictionarySuggestions(token).filter { normalize(it) == exact && it != exact }
    if (accents.isNotEmpty()) result.add(token + " → " + accents.take(4).joinToString(" / "))
   } else {
    val options = dictionarySuggestions(token).filter { it != exact }.take(4)
    if (options.isNotEmpty()) result.add(token + " → " + options.joinToString(" / "))
   }
  }
  return result
 }
 fun dictionarySuggestions(word: String): List<String> {
  if (indexState != "ready" || word.length !in 3..40 || !word.all(Char::isLetter)) return emptyList()
  val key = normalize(word)
  return synchronized(this) {
   val c = corpus ?: return@synchronized emptyList()
   val result = linkedSetOf<String>()
   c.words.rawQuery("SELECT word FROM folded WHERE key=? ORDER BY word LIMIT 12", arrayOf(key)).use { cursor ->
    while (cursor.moveToNext()) result.add(cursor.getString(0))
   }
   if (result.isEmpty()) {
    val alphabet = "abcdefghijklmnopqrstuvwxyz"
    val variants = linkedSetOf<String>()
    for (i in key.indices) {
     variants.add(key.removeRange(i,i+1))
     if (i+1 < key.length) variants.add(key.substring(0,i)+key[i+1]+key[i]+key.substring(i+2))
     for (ch in alphabet) variants.add(key.substring(0,i)+ch+key.substring(i+1))
    }
    for (i in 0..key.length) for (ch in alphabet) variants.add(key.substring(0,i)+ch+key.substring(i))
    for (batch in variants.chunked(200)) {
     val placeholders = batch.joinToString(",") { "?" }
     c.words.rawQuery("SELECT word FROM folded WHERE key IN ($placeholders) ORDER BY word LIMIT 12", batch.toTypedArray()).use { cursor ->
      while (cursor.moveToNext()) if (result.size < 12) result.add(cursor.getString(0))
     }
     if (result.size >= 12) break
    }
   }
   result.toList()
  }
 }
 // Optional imported Wiktionary JSONL, indexed separately from command translations.
 @Volatile private var definitionsDb: android.database.sqlite.SQLiteDatabase? = null
 @Volatile private var definitionsState = "not_initialized"
 fun definitionsStatus(): String = definitionsState
 private fun initializeDefinitions(context: Context) {
  definitionsState = "building"
  worker.execute {
   var db: android.database.sqlite.SQLiteDatabase? = null
   try {
    val input = context.assets.open("ro_wiktionary/lexicon.jsonl")
    input.bufferedReader(Charsets.UTF_8).useLines { lines ->
     val opened = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
      java.io.File(context.noBackupFilesDir,"romanian-definitions-v4.db"),null)
     db = opened
     if (opened.version != 4) {
      opened.beginTransaction()
      try {
       opened.execSQL("CREATE TABLE IF NOT EXISTS definitions(key TEXT, lemma TEXT, gloss TEXT, PRIMARY KEY(key,lemma,gloss))")
       opened.execSQL("CREATE TABLE IF NOT EXISTS grammar(key TEXT NOT NULL, lemma TEXT NOT NULL, pos TEXT NOT NULL, tags TEXT NOT NULL, PRIMARY KEY(key,lemma,pos,tags))")
       opened.execSQL("CREATE TABLE IF NOT EXISTS relations(key TEXT NOT NULL, term TEXT NOT NULL, PRIMARY KEY(key,term))")
       val relationInsert = opened.compileStatement("INSERT OR IGNORE INTO relations VALUES (?,?)")
       val grammatical = opened.compileStatement("INSERT OR IGNORE INTO grammar VALUES (?,?,?,?)")
       val insert = opened.compileStatement("INSERT OR IGNORE INTO definitions VALUES (?,?,?)")
       var count = 0
       try {
        for (line in lines) {
         val row = Json.parseToJsonElement(line).jsonObject
         val lemma = row.getValue("word").jsonPrimitive.content
         val pos = (row["pos"] as? JsonPrimitive)?.contentOrNull.orEmpty()
         fun addGrammar(form: String, root: String, tags: List<String>) {
          if (form.isBlank() || root.isBlank() || form.length > 160) return
          grammatical.bindString(1,normalize(form)); grammatical.bindString(2,root)
          grammatical.bindString(3,pos); grammatical.bindString(4,tags.distinct().sorted().joinToString("|"))
          grammatical.executeInsert()
         }
         fun tagsOf(obj: JsonObject): List<String> = (obj["tags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
         addGrammar(lemma,lemma,emptyList())
         for (item in (row["grammar"] as? JsonArray).orEmpty()) {
          val form = item as? JsonObject ?: continue
          addGrammar((form["form"] as? JsonPrimitive)?.contentOrNull.orEmpty(),lemma,tagsOf(form))
         }
         for (item in (row["senses"] as? JsonArray).orEmpty()) {
          val sense = item as? JsonObject ?: continue
          for (synonym in (sense["synonyms"] as? JsonArray).orEmpty()) {
           val term = (synonym as? JsonPrimitive)?.contentOrNull ?: continue
           if (term.isBlank() || normalize(term) == normalize(lemma)) continue
           relationInsert.bindString(1,normalize(lemma)); relationInsert.bindString(2,term); relationInsert.executeInsert()
          }
          for (relation in listOf("form_of","alt_of")) {
           for (link in (sense[relation] as? JsonArray).orEmpty()) {
            val root = ((link as? JsonObject)?.get("word") as? JsonPrimitive)?.contentOrNull ?: continue
            addGrammar(lemma,root,tagsOf(sense) + relation)
           }
          }
         }
         val glosses = row.getValue("english_glosses").jsonArray.map { it.jsonPrimitive.content }
         val forms = row.getValue("forms").jsonArray.map { it.jsonPrimitive.content }.filter { it.isNotBlank() && it.length <= 160 }
         for (form in (listOf(lemma) + forms).distinct()) {
          for (gloss in glosses) {
           insert.bindString(1,normalize(form)); insert.bindString(2,lemma)
           insert.bindString(3,gloss); insert.executeInsert()
          }
         }
         count++
        }
       } finally { insert.close(); grammatical.close(); relationInsert.close() }
       check(count >= 10000)
       opened.execSQL("CREATE INDEX IF NOT EXISTS definitions_gloss ON definitions(gloss COLLATE NOCASE,lemma)")
       opened.version = 4
       opened.setTransactionSuccessful()
      } finally { opened.endTransaction() }
     }
     synchronized(this) {
      definitionsDb?.close()
      definitionsDb = opened
      definitionsState = "ready"
     }
    }
   } catch (_: Exception) {
    db?.close()
    definitionsState = "unavailable"
   }
  }
 }
 data class GrammarMatch(val lemma: String, val partOfSpeech: String, val tags: List<String>)
 fun grammarMatches(word: String): List<GrammarMatch> {
  if (definitionsState != "ready" || word.length !in 1..160) return emptyList()
  return synchronized(this) {
   val db = definitionsDb ?: return@synchronized emptyList()
   db.rawQuery("SELECT lemma,pos,tags FROM grammar WHERE key=? ORDER BY lemma,pos,tags LIMIT 24",arrayOf(normalize(word))).use { cursor ->
    val result = ArrayList<GrammarMatch>()
    while (cursor.moveToNext()) result.add(GrammarMatch(cursor.getString(0),cursor.getString(1),cursor.getString(2).split('|').filter { it.isNotBlank() }))
    result
   }
  }
 }
 fun grammarReport(text: String): List<String> =
  Regex("[\\p{L}\\p{M}]+").findAll(text.take(500)).map { it.value }.distinct().take(24).mapNotNull { word ->
   val matches = grammarMatches(word)
   if (matches.isEmpty()) null else word + ": " + matches.take(4).joinToString(" / ") { it.lemma + " [" + (listOf(it.partOfSpeech) + it.tags).filter { tag -> tag.isNotBlank() }.joinToString(", ") + "]" }
  }.toList()
 fun englishDefinitions(word: String): List<String> {
  if (definitionsState != "ready" || word.length !in 1..160) return emptyList()
  return synchronized(this) {
   val db = definitionsDb ?: return@synchronized emptyList()
   db.rawQuery("SELECT DISTINCT gloss FROM definitions WHERE key=? ORDER BY gloss LIMIT 12",arrayOf(normalize(word))).use { cursor ->
    val result = ArrayList<String>()
    while (cursor.moveToNext()) result.add(cursor.getString(0))
    result
   }
  }
 }
 // Reverse lookup is deliberately exact: definitions are not sentence translations.
 fun romanianDefinitionMatches(english: String): List<String> {
  if (definitionsState != "ready" || english.isBlank() || english.length > 160) return emptyList()
  return synchronized(this) {
   val db = definitionsDb ?: return@synchronized emptyList()
   db.rawQuery("SELECT DISTINCT lemma FROM definitions WHERE gloss=? COLLATE NOCASE ORDER BY lemma LIMIT 12",arrayOf(english.trim())).use { cursor ->
    val result = ArrayList<String>()
    while (cursor.moveToNext()) result.add(cursor.getString(0))
    result
   }
  }
 }
 fun translateReverse(text: String): String? {
  val key = normalize(text)
  return reverseGlossary[key] ?: romanianDefinitionMatches(text).takeIf { it.isNotEmpty() }?.joinToString(" / ")?.let { "Potriviri lexicale RO: " + it }
 }
 fun split(text: String): List<String> = clean(text).split(Regex("\\s+(?:și|si|apoi|după aceea|dupa aceea)\\s+|;\\s*", RegexOption.IGNORE_CASE)).map { it.trim() }.filter { it.isNotEmpty() }
 fun compoundPlan(text: String): List<PlannedAction>? {
  val parts = split(text)
  if (parts.size !in 2..20 || parts.any { negated(it) }) return null
  return parts.map { NaturalToolCommands.parse(it) ?: return null }
 }
 fun translateKnown(text: String): String? = entries[normalize(text)]?.english ?: glossary[normalize(text)]
}
