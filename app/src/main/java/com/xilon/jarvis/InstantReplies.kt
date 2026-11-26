package com.xilon.jarvis

import java.text.Normalizer
import java.util.Locale

/**
 * Instant, offline replies for fixed social intents only. No model calls, network,
 * or Android actions. Longer/compound commands must go through the guarded router.
 */
internal object InstantReplies {
 private fun normalized(text:String):String = Normalizer.normalize(
  text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD
 ).replace(Regex("\\p{M}+"),"")
  .replace(Regex("[,.!?;:]+")," ")
  .replace(Regex("\\s+")," ")
  .trim()

 fun reply(input:String):String? {
  val text=normalized(input)
  if(text.length>90)return null
  return when(text) {
   "salut", "salutare", "buna", "buna ziua", "buna dimineata",
   "buna seara", "hello", "hi", "hei" ->
    "Salut! Sunt JARVIS, asistentul tău Android local. Cu ce te pot ajuta?"
   "cine esti", "tu cine esti", "salut cine esti",
   "salutare cine esti", "buna cine esti", "buna ziua cine esti",
   "cum te cheama", "care este numele tau" ->
    "Sunt XILON JARVIS, asistentul tău Android. Pot răspunde offline prin modelul GGUF și pot folosi instrumente Android, cu permisiunile necesare."
   "ce faci", "salut ce faci", "cum esti", "esti bine" ->
    "Sunt aici și pregătit să te ajut. Ce ai vrea să facem?"
   "multumesc", "mersi", "multumesc jarvis" ->
    "Cu plăcere!"
   else -> null
  }
 }
}
