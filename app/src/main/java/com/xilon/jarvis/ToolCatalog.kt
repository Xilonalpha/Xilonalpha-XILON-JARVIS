package com.xilon.jarvis

object ToolCatalog {
 data class Spec(val name:String,val description:String)
 val readOnly=listOf(
  Spec("ora","Ora locală"),
  Spec("baterie","Nivel baterie"),
  Spec("ram","Memorie RAM disponibilă"),
  Spec("stocare","Spațiu disponibil")
 )
 fun permits(name:String)=readOnly.any{it.name==name}
 fun describe()=readOnly.joinToString("; "){it.name+": "+it.description}
}
