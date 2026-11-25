package com.xilon.jarvis
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
class ModelDownloads(private val context:Context){
 private val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
 private val prefs=context.getSharedPreferences("model_downloads",Context.MODE_PRIVATE)
 fun start(entry:ModelCatalog.Entry):Long{
  require(entry in (ModelCatalog.entries+ModelCatalog.ocr+ModelCatalog.whisper+ModelCatalog.wake+listOf(ModelCatalog.hand)))
  check(!prefs.contains(entry.file)){"Există deja o descărcare pentru acest model."}
  check(context.filesDir.usableSpace>=entry.mb*2000000L+200000000L){"Spațiu insuficient"}
  val id=dm.enqueue(DownloadManager.Request(Uri.parse(entry.url)).setTitle(entry.name)
   .setAllowedOverMetered(false).setAllowedOverRoaming(false)
   .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
   .setDestinationInExternalFilesDir(context,Environment.DIRECTORY_DOWNLOADS,entry.file))
  check(id>=0){"Transferul nu a fost acceptat"}
  prefs.edit().putLong(entry.file,id).apply()
  return id
 }
 fun id(entry:ModelCatalog.Entry)=prefs.getLong(entry.file,-1)
 fun status(entry:ModelCatalog.Entry):String{
  val id=id(entry);if(id<0)return "Nedescărcat"
  return dm.query(DownloadManager.Query().setFilterById(id))?.use{c->
   if(!c.moveToFirst())"Transfer indisponibil" else {
    val state=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
    val bytes=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
    val total=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
    when(state){
     DownloadManager.STATUS_SUCCESSFUL->"Descărcat • integritate neverificată"
     DownloadManager.STATUS_FAILED->"Eșuat • cod "+c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
     DownloadManager.STATUS_PAUSED->"În așteptare rețea"
     else->if(total>0)"Transfer: "+(bytes*100/total)+"%" else "Transfer: "+bytes/1048576+" MiB"
    }
   }
  } ?: "Transfer indisponibil"
 }
 fun cancel(entry:ModelCatalog.Entry){val id=id(entry);if(id>=0)dm.remove(id);prefs.edit().remove(entry.file).apply()}
 fun uri(entry:ModelCatalog.Entry):Uri?=id(entry).takeIf{it>=0}?.let{dm.getUriForDownloadedFile(it)}
}
