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
  fun ready(dir:File)=listOf("ro_RO-mihai-medium.onnx","tokens.txt","espeak-ng-data/phondata","espeak-ng-data/ro_dict").all{File(dir,it).isFile && File(dir,it).length()>0}
  if(ready(target))return
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
     require(relative in setOf("ro_RO-mihai-medium.onnx","tokens.txt","ro_RO-mihai-medium.onnx.json","MODEL_CARD","README.md","LICENSE","espeak-ng-data")||relative.startsWith("espeak-ng-data/")){"Fișier nepermis"}
     require(relative.split("/").none{it=="."||it==".."||it.isBlank()}){"Cale nesigură"}
     val out=File(temp,relative).canonicalFile
     require(out.path.startsWith(temp.canonicalPath+File.separator)&&seen.add(out.path))
     if(entry.isDirectory)check(out.isDirectory||out.mkdirs())
     else{
      require(entry.size>=0 && entry.size<=256L*1024*1024)
      total+=entry.size;require(total<=384L*1024*1024)
      check(out.parentFile?.isDirectory==true||out.parentFile?.mkdirs()==true)
      out.outputStream().use{dst->val buf=ByteArray(65536);var written=0L;while(true){val n=tar.read(buf);if(n<0)break;written+=n;require(written<=entry.size){"Dimensiune invalidă"};dst.write(buf,0,n)}}
      require(out.length()==entry.size)
     }
    }
   }
   require(listOf("ro_RO-mihai-medium.onnx","tokens.txt").all{File(temp,it).isFile&&File(temp,it).length()>0})
   require(ready(temp)){"Pachet Mihai incomplet: lipsesc datele eSpeak românești"}
   val backup=File(root,"mihai-backup-"+java.util.UUID.randomUUID())
   if(target.exists())check(target.renameTo(backup)){"Nu pot pregăti repararea Mihai"}
   if(!temp.renameTo(target)){
    if(backup.exists())check(backup.renameTo(target)){"Restaurare necesară: "+backup.name}
    error("Instalarea nu a putut fi finalizată")
   }
   backup.deleteRecursively()
  }finally{temp.deleteRecursively()}
 }
}
