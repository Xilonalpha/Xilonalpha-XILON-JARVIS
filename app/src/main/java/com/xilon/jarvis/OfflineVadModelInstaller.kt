package com.xilon.jarvis

import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Copies an APK-provided ONNX model to private storage without exposing a
 * partially written model to inference. No network access is performed.
 */
internal object OfflineVadModelInstaller {
 const val MAX_BYTES=32L*1024*1024
 private val publishLock=Any()
 fun install(source:InputStream,destination:File):Long {
  require(!destination.exists()){"Model already exists"}
  val parent=destination.parentFile ?: throw IOException("Missing model directory")
  if(!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory)throw IOException("Cannot create model directory")
  val temporary=File.createTempFile(destination.name+".", ".part", parent)
  var copied=0L
  try {
   temporary.outputStream().use{output->
    val buffer=ByteArray(16384)
    while(true){
     val read=source.read(buffer)
     if(read<0)break
     if(read==0)continue
     copied+=read
     if(copied>MAX_BYTES)throw IOException("ONNX model exceeds size limit")
     output.write(buffer,0,read)
    }
    output.flush()
    output.fd.sync()
   }
   if(copied==0L)throw IOException("Empty ONNX model")
   synchronized(publishLock){
    if(destination.exists())throw IOException("Model installed concurrently")
    if(!temporary.renameTo(destination))throw IOException("Cannot finalize ONNX model")
   }
   return copied
  }finally{temporary.delete()}
 }
}
