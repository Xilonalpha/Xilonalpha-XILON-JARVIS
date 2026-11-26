package com.xilon.jarvis

internal object CommandGreeting {
 private val greeting=Regex("^(?:salutare|salut|bună ziua|buna ziua|bună|buna|hei|hello)[,!\\s]+",RegexOption.IGNORE_CASE)
 private val imperative=Regex("^(?:trimite|deschide|apelează|apeleaza|sună|suna|setează|seteaza|pornește|porneste|oprește|opreste|caută|cauta|arată|arata|citește|citeste|navighează|navigheaza|activează|activeaza|dezactivează|dezactiveaza)\\b",RegexOption.IGNORE_CASE)
 fun stripBeforeCommand(input:String):String {
  val candidate=greeting.replaceFirst(input.trim(),"").trim()
  return if(candidate!=input.trim() && imperative.containsMatchIn(candidate))candidate else input
 }
}
