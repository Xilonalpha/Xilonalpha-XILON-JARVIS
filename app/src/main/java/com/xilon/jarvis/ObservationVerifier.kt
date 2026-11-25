package com.xilon.jarvis
object ObservationVerifier {
 fun valid(command:String,answer:String):Boolean {
  if(answer.isBlank() || answer.startsWith("Eroare",true) || answer.contains("indisponibil",true)) return false
  return when(command.lowercase(java.util.Locale.ROOT)){
   "baterie" -> answer.removeSuffix("%").toIntOrNull()?.let{it in 0..100}==true && answer.endsWith("%")
   "stocare" -> Regex("""(\d+) MiB disponibili""").matchEntire(answer)?.groupValues?.get(1)?.toLongOrNull()!=null
   "ram" -> Regex("""(\d+) MiB disponibili din (\d+) MiB; memorie redusă: (true|false)""").matchEntire(answer)?.let{m->
    val a=m.groupValues[1].toLongOrNull();val t=m.groupValues[2].toLongOrNull()
    a!=null && t!=null && t>0 && a in 0..t
   }==true
   "ora" -> java.text.DateFormat.getTimeInstance().apply{isLenient=false}.let{f->val p=java.text.ParsePosition(0);f.parse(answer,p)!=null && p.index==answer.length}
   "profil dispozitiv" -> answer.startsWith("Profile(") && answer.contains("thermalStatus=") && answer.contains("totalRamGb=")
   "lista aplicații" -> answer=="Nicio aplicație vizibilă." || answer.lines().all{Regex("""[^\n]+ \([A-Za-z0-9_.]+\)""").matches(it)}
   "volum" -> Regex("""Volum multimedia: (\d+)/(\d+)""").matchEntire(answer)?.let{val a=it.groupValues[1].toIntOrNull();val b=it.groupValues[2].toIntOrNull();a!=null&&b!=null&&a in 0..b}==true
   "rețea" -> answer in setOf("Fără conexiune activă","Conexiune validată","Conexiune nevalidată")
   "încărcare" -> answer in setOf("Se încarcă","Încărcată","Nu se încarcă","Se descarcă")
   else -> false
  }
 }
}
