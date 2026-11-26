package com.xilon.jarvis
import android.content.Context
class LocalMemory(context: Context) {
 private val prefs = context.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)
 fun remember(key: String, value: String): Boolean {
  require(key.isNotBlank() && value.isNotBlank())
  return prefs.edit().putString(key.trim(), value.trim()).commit()
 }
 fun recall(key: String): String? = prefs.getString(key.trim(), null)
 /** Local lexical retrieval over explicitly saved memories; no inferred facts. */
 fun search(query:String,limit:Int=5):List<Pair<String,String>> {
  fun words(s:String)=RomanianLexicon.normalize(s).split(Regex("[^\\p{L}\\p{N}]+")).filter{it.length>=2}.toSet()
  val terms=words(query)
  if(terms.isEmpty())return emptyList()
  return prefs.all.entries.mapNotNull{entry->
   val value=entry.value as? String ?: return@mapNotNull null
   val keyWords=words(entry.key)
   val valueWords=words(value)
   val score=3*terms.intersect(keyWords).size+terms.intersect(valueWords).size
   if(score==0)null else Triple(entry.key,value,score)
  }.sortedWith(compareByDescending<Triple<String,String,Int>>{it.third}.thenBy{it.first})
   .take(limit.coerceIn(1,20)).map{it.first to it.second}
 }
 fun forget(key: String): Boolean = prefs.edit().remove(key.trim()).commit()
}
