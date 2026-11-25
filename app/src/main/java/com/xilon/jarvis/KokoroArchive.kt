package com.xilon.jarvis
import android.content.Context
import android.net.Uri
import java.io.File
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
object KokoroArchive {
 const val URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2"
 fun install(context:Context,uri:Uri){
  val target=File(context.filesDir,"kokoro")
  require(!target.exists()){"Kokoro există deja"}
  val temp=File(context.filesDir,"kokoro-"+java.util.UUID.randomUUID());check(temp.mkdir())
  try{
   var bytes=0L;var count=0
   val seen=mutableSetOf<String>()
   TarArchiveInputStream(BZip2CompressorInputStream((context.contentResolver.openInputStream(uri)?:error("Arhivă inaccesibilă")).buffered())).use{tar->
    while(true){
     val entry=tar.nextTarEntry?:break
     require(++count<=10000)
     require(entry.isDirectory||entry.isFile)
     require(!entry.isSymbolicLink&&!entry.isLink)
     val name=entry.name.removeSuffix("/")
     if(name=="kokoro-int8-en-v0_19")continue
     require(name.startsWith("kokoro-int8-en-v0_19/"))
     val relative=name.removePrefix("kokoro-int8-en-v0_19/")
     require(relative in setOf("model.int8.onnx","voices.bin","tokens.txt","LICENSE","README.md","espeak-ng-data")||relative.startsWith("espeak-ng-data/"))
     val out=File(temp,relative).canonicalFile
     require(out.path.startsWith(temp.canonicalPath+File.separator)&&seen.add(out.path))
     if(entry.isDirectory){check(out.isDirectory||out.mkdirs())}else{
      require(entry.size>=0&&entry.size<=512L*1024*1024-bytes)
      out.parentFile?.mkdirs()
      out.outputStream().use{dst->val buffer=ByteArray(65536);while(true){val n=tar.read(buffer);if(n<0)break;bytes+=n;require(bytes<=512L*1024*1024);dst.write(buffer,0,n)}}
      require(out.length()==entry.size)
     }
    }
   }
   require(listOf("model.int8.onnx","voices.bin","tokens.txt").all{File(temp,it).isFile&&File(temp,it).length()>0})
   require(File(temp,"espeak-ng-data").walkTopDown().any{it.isFile&&it.length()>0})
   check(temp.renameTo(target))
  }finally{temp.deleteRecursively()}
 }
}
