package com.xilon.jarvis
import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream
object KokoroImport {
 fun install(context:Context,uri:Uri){
  val target=File(context.filesDir,"kokoro")
  require(!target.exists()){"Modelele Kokoro există deja"}
  val temp=File(context.filesDir,"kokoro-"+java.util.UUID.randomUUID())
  check(temp.mkdir())
  try{
   var total=0L;var count=0
   val seen=mutableSetOf<String>()
   ZipInputStream(context.contentResolver.openInputStream(uri) ?: error("Fișier indisponibil")).use{zip->
    while(true){
     val entry=zip.nextEntry ?: break
     require(++count<=10000){"Prea multe fișiere"}
     val name=entry.name.removePrefix("kokoro/")
     if(name.isEmpty()){zip.closeEntry();continue}
     require(name in setOf("model.int8.onnx","voices.bin","tokens.txt","espeak-ng-data","espeak-ng-data/") || name.startsWith("espeak-ng-data/")){"Structură ZIP neacceptată"}
     val out=File(temp,name).canonicalFile
     require(out.path.startsWith(temp.canonicalPath+File.separator) && seen.add(out.path)){"Cale invalidă sau duplicată"}
     if(entry.isDirectory){check(out.isDirectory || out.mkdirs())}else{
      out.parentFile?.mkdirs()
      out.outputStream().use{dest->val buf=ByteArray(65536);while(true){val n=zip.read(buf);if(n<0)break;total+=n;require(total<=512L*1024*1024){"Arhivă prea mare"};dest.write(buf,0,n)}}
     }
     zip.closeEntry()
    }
   }
   require(listOf("model.int8.onnx","voices.bin","tokens.txt").all{File(temp,it).isFile && File(temp,it).length()>0} && File(temp,"espeak-ng-data").isDirectory){"Modele incomplete"}
   check(temp.renameTo(target)){"Instalare eșuată"}
  }finally{temp.deleteRecursively()}
 }
}
