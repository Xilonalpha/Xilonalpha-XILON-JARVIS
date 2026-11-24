package com.xilon.jarvis
import android.content.Context
import android.net.Uri
import java.io.File
import com.codotype.jarvis.rag.KnowledgeBase
class LocalKnowledge(private val context:Context){
 private val dir=File(context.filesDir,"knowledge").apply{mkdirs()}
 fun add(uri:Uri):String{
  require(dir.listFiles().orEmpty().size<20){"Maximum 20 documents"}
  val target=File(dir,java.util.UUID.randomUUID().toString()+".txt")
  try{
   context.contentResolver.openInputStream(uri)?.use{src->
    target.outputStream().use{dst->
     val buffer=ByteArray(8192);var total=0
     while(true){val n=src.read(buffer);if(n<0)break;total+=n;require(total<=262144){"Maximum 256 KiB per document"};dst.write(buffer,0,n)}
    }
   }?:error("Document inaccesibil")
   return "Document local: "+target.name
  }catch(e:Exception){target.delete();throw e}
 }
 fun search(query:String):String{
  val index=KnowledgeBase(dir);index.load()
  return index.retrieve(query,2).joinToString("\n\n"){it.source+": "+it.text.take(600)}.ifBlank{"Niciun pasaj relevant."}
 }
}
