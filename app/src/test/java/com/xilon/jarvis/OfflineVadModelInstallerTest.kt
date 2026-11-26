package com.xilon.jarvis

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

class OfflineVadModelInstallerTest {
 @Test fun installsIntoPrivateDirectory() {
  val dir=Files.createTempDirectory("xilon-vad").toFile()
  try {
   val target=File(dir,"models/silero_vad.onnx")
   val bytes=byteArrayOf(1,2,3,4)
   assertEquals(4L,OfflineVadModelInstaller.install(ByteArrayInputStream(bytes),target))
   assertArrayEquals(bytes,target.readBytes())
   assertFalse(File(target.parentFile,target.name+".part").exists())
  }finally{dir.deleteRecursively()}
 }
 @Test fun emptyStreamDoesNotPublishModel() {
  val dir=Files.createTempDirectory("xilon-vad").toFile()
  try {
   val target=File(dir,"silero_vad.onnx")
   try {
    OfflineVadModelInstaller.install(ByteArrayInputStream(byteArrayOf()),target)
    fail("Expected rejection")
   }catch(_:IOException){}
   assertFalse(target.exists())
   assertFalse(File(dir,"silero_vad.onnx.part").exists())
  }finally{dir.deleteRecursively()}
 }
 @Test fun rejectsOversizedModelWithoutPublishing() {
  val dir=Files.createTempDirectory("xilon-vad").toFile()
  try {
   val target=File(dir,"silero_vad.onnx")
   val oversized=object:java.io.InputStream(){
    var remaining=OfflineVadModelInstaller.MAX_BYTES+1
    override fun read():Int=if(remaining-->0)1 else -1
    override fun read(b:ByteArray,off:Int,len:Int):Int {
     if(remaining<=0)return -1
     val n=minOf(len.toLong(),remaining).toInt()
     java.util.Arrays.fill(b,off,off+n,1.toByte())
     remaining-=n
     return n
    }
   }
   try {
    OfflineVadModelInstaller.install(oversized,target)
    fail("Expected oversized model rejection")
   }catch(_:IOException){}
   assertFalse(target.exists())
   assertFalse(File(dir,"silero_vad.onnx.part").exists())
  }finally{dir.deleteRecursively()}
 }
 @Test fun doesNotOverwriteExistingModel() {
  val dir=Files.createTempDirectory("xilon-vad").toFile()
  try {
   val target=File(dir,"silero_vad.onnx")
   target.writeBytes(byteArrayOf(7))
   try {
    OfflineVadModelInstaller.install(ByteArrayInputStream(byteArrayOf(9)),target)
    fail("Expected rejection")
   }catch(_:IllegalArgumentException){}
   assertArrayEquals(byteArrayOf(7),target.readBytes())
  }finally{dir.deleteRecursively()}
 }
}
