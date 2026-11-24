package com.xilon.jarvis
class OmegaRecorder {
 private var steps:MutableList<String>?=null
 fun start(){check(steps==null){"Înregistrare deja activă"};steps=mutableListOf()}
 fun observe(command:String,result:String){
  val list=steps ?: return
  val key=command.trim().lowercase(java.util.Locale.ROOT)
  if(list.size<4 && key !in list && ToolCatalog.permits(key) && ObservationVerifier.valid(key,result))list.add(key)
 }
 fun finish():List<String>{val result=steps?.toList() ?: error("Înregistrare inactivă");require(result.isNotEmpty()){"Niciun pas valid"};steps=null;return result}
 fun cancel(){steps=null}
}
