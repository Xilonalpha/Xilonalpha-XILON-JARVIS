package com.xilon.jarvis

import java.text.Normalizer
import java.util.Locale

internal object VlmCommand {
 private fun normalize(text:String)=Normalizer.normalize(text,Normalizer.Form.NFD)
  .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).trim().replace(Regex("[?!.]+$"),"").replace(Regex("\\s+")," ")
 private val exact=setOf("ce vezi","ce vezi in fata ta","ce vezi prin camera","descrie scena","descrie ce vezi","analizeaza camera","ce se afla in fata camerei","ce obiecte vezi")
 fun parse(text:String):String? {
  val q=normalize(text)
  if(q in exact)return text.trim()
  if(q.startsWith("intreaba camera:") && q.substringAfter(':').isNotBlank())return text.substringAfter(':').trim().take(1000)
  return null
 }
}
