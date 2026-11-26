package com.xilon.jarvis

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs auxiliary on-device assets automatically on unmetered networks.
 * GGUF conversational models are deliberately excluded.
 * Failed downloads are retried on a later app launch, not in a tight loop.
 */
internal object AuxiliaryModelBootstrap {
 private val running=AtomicBoolean(false)
 private val executor=Executors.newSingleThreadExecutor()
 val required:List<ModelCatalog.Entry> get()=
  ModelCatalog.ocr+ModelCatalog.whisper+ModelCatalog.wake+
   ModelCatalog.vision+listOf(ModelCatalog.hand,ModelCatalog.kokoro)

 fun launch(context:Context){
  val app=context.applicationContext
  if(!running.compareAndSet(false,true))return
  executor.execute{
   try{
    val network=(app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager) ?: return@execute
    if(network.isActiveNetworkMetered)return@execute
    val downloads=ModelDownloads(app)
    for(entry in required.distinctBy{it.file}){
     if(Thread.currentThread().isInterrupted)break
     if(network.isActiveNetworkMetered)break
     if(ModelInventory.installed(app,entry))continue
     try{
      if(downloads.uri(entry)==null){
       if(downloads.canStart(entry))downloads.start(entry)
       val deadline=android.os.SystemClock.elapsedRealtime()+5L*60*1000
       while(android.os.SystemClock.elapsedRealtime()<deadline){
        val state=downloads.transfer(entry).state
        if(state==DownloadManager.STATUS_SUCCESSFUL || state==DownloadManager.STATUS_FAILED || state==-1)break
        Thread.sleep(1000)
       }
      }
      val uri=downloads.uri(entry)?:continue
      val ref=downloads.reference(entry)
      app.contentResolver.openInputStream(uri)?.use{
       ModelIntegrity.verify(it,ref.sha256,ref.bytes,ref.algorithm)
      }?:continue
      install(app,entry,uri)
     }catch(_:Exception){
      // Preserve the failed item for manual inspection and a future launch.
     }
    }
   }finally{running.set(false)}
  }
 }

 private fun install(context:Context,entry:ModelCatalog.Entry,uri:Uri){
  when {
   entry in ModelCatalog.ocr ->
    LocalOcr.install(context,uri,entry.file.substringBefore('.'))
   entry in ModelCatalog.vision ->
    if(entry==ModelCatalog.pose)LocalPose.install(context,uri)
    else LocalVision.install(context,uri,entry==ModelCatalog.vision[1])
   entry==ModelCatalog.kokoro -> KokoroArchive.install(context,uri)
   else -> {
    val target=when{
     entry in ModelCatalog.whisper -> File(context.filesDir,"models/"+entry.file)
     entry in ModelCatalog.wake -> File(context.filesDir,"models/"+if(entry.file=="hey_jarvis.onnx")"jarvis_wake_word.onnx" else entry.file)
     entry==ModelCatalog.hand -> File(context.filesDir,entry.file)
     else -> error("Unsupported auxiliary model")
    }
    val parent=target.parentFile?:error("Missing directory")
    check(parent.isDirectory || parent.mkdirs())
    val temp=File.createTempFile("aux-", ".part",parent)
    try{
     context.contentResolver.openInputStream(uri)?.use{src->
      temp.outputStream().use{dst->src.copyTo(dst,65536)}
     }?:error("Missing downloaded file")
     val ref=ModelDownloads(context).reference(entry)
     ModelIntegrity.verify(temp.inputStream(),ref.sha256,ref.bytes,ref.algorithm)
     if(!target.isFile)check(temp.renameTo(target))
    }finally{temp.delete()}
   }
  }
 }
}
