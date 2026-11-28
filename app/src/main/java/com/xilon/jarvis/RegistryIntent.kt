package com.xilon.jarvis

import java.text.Normalizer
import java.util.Locale

object RegistryIntent {
 private val verbs = "deschide|seteaza|activeaza|dezactiveaza|cauta|porneste|arata|citeste|scrie|trimite|creeaza|apeleaza|navigheaza|opreste|inchide|pune|baga|reda|asculta|aprinde|stinge|regleaza|deruleaza|afiseaza|listeaza|lanseaza|da drumul"
 private val action get() = Regex("^(?:(?:te rog|jarvis)[,\\s]+)*(?:${(verbs.split("|") + RomanianLexicon.verbs).distinct().joinToString("|"){Regex.escape(it)}})\\b")
 private val question = Regex("^(cum|ce|de ce|care|explica|poti explica)\\b")
 private val separator get() = Regex("(?:\\b(?:si|apoi|dupa aceea)\\b[,\\s]*|;\\s*)(?:(?:te rog|jarvis)[,\\s]+)*(?:${(verbs.split("|") + RomanianLexicon.verbs).distinct().joinToString("|"){Regex.escape(it)}})\\b")
 private fun normalized(text: String): String =
  Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
   .replace(Regex("\\p{M}+"), "")
 fun matches(text: String): Boolean {
  val q = normalized(RomanianLexicon.canonicalCommand(text))
  return !RomanianLexicon.negated(text) && !question.containsMatchIn(q) && action.containsMatchIn(q)
 }
 fun compound(text: String): Boolean {
  val q = normalized(text)
  return matches(q) && RomanianLexicon.split(text).size > 1
 }
}
