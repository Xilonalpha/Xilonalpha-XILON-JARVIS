package com.xilon.jarvis
import android.content.Context
import android.net.Uri
import java.io.File
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/** Safe, bounded install of the official Sherpa Romanian Mihai VITS package. */
object RomanianPiperArchive {
 private const val PREFIX="vits-piper-ro_RO-mihai-medium/"
 fun install(context:Context,uri:Uri){
  val root=File(context.filesDir,"models/romanian").apply{check(isDirectory||mkdirs())}
  val target=File(root,"mihai")
  require(!target.exists()){"Mihai RO este deja instalat"}
  val temp=File(root,"mihai-"+java.util.UUID.randomUUID()).apply{check(mkdir())}
  try{
   var count=0;var total=0L
   val seen=mutableSetOf<String>()
   TarArchiveInputStream(BZip2CompressorInputStream((context.contentResolver.openInputStream(uri)?:error("Arhivă inaccesibilă")).buffered())).use{tar->
    while(true){
     val entry=tar.nextTarEntry?:break
     require(++count<=12000){"Prea multe fișiere"}
     require((entry.isDirectory||entry.isFile)&&!entry.isSymbolicLink&&!entry.isLink)
     val name=entry.name.removePrefix("./").removeSuffix("/")
     if(name==PREFIX.removeSuffix("/"))continue
     require(name.startsWith(PREFIX)){"Cale neașteptată: $name"}
     val relative=name.removePrefix(PREFIX)
     require(relative in setOf("ro_RO-mihai-medium.onnx","tokens.txt","ro_RO-mihai-medium.onnx.json","README.md","LICENSE","espeak-ng-data")||relative.startsWith("espeak-ng-data/")){"Fișier nepermis"}
     val out=File(temp,relative).canonicalFile
     require(out.path.startsWith(temp.canonicalPath+File.separator)&&seen.add(out.path))
     if(entry.isDirectory)check(out.isDirectory||out.mkdirs())
     else{
      require(entry.size>=0 && entry.size<=256L*1024*1024)
      total+=entry.size;require(total<=384L*1024*1024)
      check(out.parentFile?.isDirectory==true||out.parentFile?.mkdirs()==true)
      out.outputStream().use{dst->val buf=ByteArray(65536);while(true){val n=tar.read(buf);if(n<0)break;dst.write(buf,0,n)}}
      require(out.length()==entry.size)
     }
    }
   }
   require(listOf("ro_RO-mihai-medium.onnx","tokens.txt").all{File(temp,it).isFile&&File(temp,it).length()>0})
   require(File(temp,"espeak-ng-data").walkTopDown().any{it.isFile&&it.length()>0})
   check(temp.renameTo(target)){"Instalarea nu a putut fi finalizată"}
  }finally{temp.deleteRecursively()}
 }
}
