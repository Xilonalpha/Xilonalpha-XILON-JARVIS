package com.xilon.jarvis

import java.text.Normalizer
import java.util.Locale

object RegistryIntent {
 private val verbs = "deschide|seteaza|activeaza|dezactiveaza|cauta|porneste|arata|citeste|trimite|creeaza|apeleaza|navigheaza|opreste|inchide"
 private val action = Regex("^(?:(?:te rog|jarvis)[,\\s]+)*(?:$verbs)\\b")
 private val question = Regex("^(cum|ce|de ce|care|explica|poti explica)\\b")
 private val separator = Regex("(?:\\b(?:si|apoi|dupa aceea)\\b[,\\s]*|;\\s*)(?:(?:te rog|jarvis)[,\\s]+)*(?:$verbs)\\b")
 private fun normalized(text: String): String =
  Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
   .replace(Regex("\\p{M}+"), "")
 fun matches(text: String): Boolean {
  val q = normalized(text)
  return !question.containsMatchIn(q) && action.containsMatchIn(q)
 }
 fun compound(text: String): Boolean {
  val q = normalized(text)
  return matches(q) && separator.containsMatchIn(q)
 }
}
