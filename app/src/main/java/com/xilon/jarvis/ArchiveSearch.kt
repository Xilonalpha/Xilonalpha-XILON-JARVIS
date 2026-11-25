package com.xilon.jarvis

import java.io.File
import java.text.Normalizer
import java.util.Locale
import org.json.JSONObject

object ArchiveSearch {
 data class Hit(val title:String,val text:String,val score:Int)
 private fun normalize(s:String)=Normalizer.normalize(s.lowercase(Locale.ROOT),Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
 fun search(directory:File,query:String,limit:Int=20):List<Hit>{
  val terms=normalize(query.take(500)).split(Regex("[^\\p{L}\\p{N}]+")).filter{it.length>=2}.distinct()
  if(terms.isEmpty())return emptyList()
  val hits=mutableListOf<Hit>()
  for(file in directory.listFiles()?.filter{it.isFile&&it.extension=="json"&&it.length()<=32_000_000}.orEmpty()){
   runCatching{
    val obj=JSONObject(file.readText());val title=obj.optString("title");val messages=obj.getJSONArray("messages")
    for(i in 0 until messages.length()){
     val text=messages.optString(i);val normalized=normalize(text)
     val score=terms.count{normalized.contains(it)}
     if(score>0)hits.add(Hit(title,text.take(2000),score))
    }
   }
  }
  return hits.sortedByDescending{it.score}.distinctBy{it.title to it.text}.take(limit.coerceIn(1,50))
 }
}
