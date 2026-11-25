package com.xilon.jarvis

/** Recovery guidance never authorizes or repeats a tool invocation. */
internal object AgentRecovery {
 fun guidance(status:String,remaining:Int):String? = when(status){
  "FAILED" -> "Verifică obstacolul raportat și formulează un obiectiv nou pentru pașii nefinalizați. Nu repeta pașii verificați."
  "UNVERIFIED","EXECUTION_UNKNOWN" -> "Verifică manual efectul ultimei acțiuni înainte de orice plan nou. Reluarea automată este blocată."
  "INVALID_PLAN" -> "Solicită un plan nou cu instrumente disponibile și argumente valide."
  "REPORTING_FAILED" -> "Verifică rezultatul și jurnalul înainte de continuare."
  "STOPPED" -> if(remaining>0) "Există pași neexecutați; reluarea necesită solicitarea utilizatorului." else null
  else -> null
 }
}
