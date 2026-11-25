package com.xilon.jarvis
object RegistryIntent {
 private val action=Regex("^(?:te rog[, ]+)?(?:jarvis[, ]+)?(?:deschide|setează|seteaza|activează|activeaza|dezactivează|dezactiveaza|caută|cauta|pornește|porneste|arată|arata|citește|citeste|trimite|creează|creeaza|apelează|apeleaza|navighează|navigheaza)\\b",RegexOption.IGNORE_CASE)
 private val question=Regex("^(cum|ce|de ce|care|explică|explica|poți explica)\\b",RegexOption.IGNORE_CASE)
 fun matches(text:String):Boolean {
  val q=text.trim()
  return !question.containsMatchIn(q)&&action.containsMatchIn(q)
 }
}
