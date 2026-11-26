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
 @Test fun concurrentInstallationDoesNotCorruptPublishedModel() {
  val dir=Files.createTempDirectory("xilon-vad-race").toFile()
  try {
   val target=File(dir,"models/silero_vad.onnx")
   val ready=java.util.concurrent.CountDownLatch(2)
   val go=java.util.concurrent.CountDownLatch(1)
   val outcomes=java.util.Collections.synchronizedList(mutableListOf<Boolean>())
   val threads=(1..2).map { index ->
    Thread {
     ready.countDown()
     go.await()
     outcomes.add(runCatching {
      OfflineVadModelInstaller.install(ByteArrayInputStream(ByteArray(4096){index.toByte()}),target)
     }.isSuccess)
    }.also { it.start() }
   }
   assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS))
   go.countDown()
   threads.forEach { it.join(5000);assertFalse(it.isAlive) }
   assertEquals(1,outcomes.count { it })
   assertEquals(4096L,target.length())
   val value=target.readBytes()[0]
   assertTrue(value==1.toByte() || value==2.toByte())
   assertTrue(target.readBytes().all { it==value })
   assertTrue(target.parentFile.listFiles()?.none { it.name.endsWith(".part") }==true)
  }finally {dir.deleteRecursively()}
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
