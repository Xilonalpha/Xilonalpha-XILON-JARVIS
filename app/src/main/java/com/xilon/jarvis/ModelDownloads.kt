package com.xilon.jarvis
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
class ModelDownloads(private val context:Context){
 private val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
 private val prefs=context.getSharedPreferences("model_downloads",Context.MODE_PRIVATE)
 fun start(entry:ModelCatalog.Entry):Long{
  require(entry in (ModelCatalog.entries+ModelCatalog.ocr+ModelCatalog.whisper+ModelCatalog.wake+listOf(ModelCatalog.hand,ModelCatalog.kokoro)))
  check(!prefs.contains(entry.file)){"Există deja o descărcare pentru acest model."}
  check(context.filesDir.usableSpace>=if(entry==ModelCatalog.kokoro) 1024L*1024*1024 else entry.mb*2000000L+200000000L){"Spațiu insuficient"}
  val reference=if(entry==ModelCatalog.kokoro)ModelMetadata.kokoro() else if(entry.directUrl==null && entry!=ModelCatalog.hand)ModelMetadata.fetch(entry) else null
  val url=if(reference!=null && entry.directUrl==null)entry.copy(revision=reference.revision).url else entry.url
  if(reference!=null)require(reference.bytes<= (context.filesDir.usableSpace-200000000L)/2){"Spațiu insuficient"}
  val id=dm.enqueue(DownloadManager.Request(Uri.parse(url)).setTitle(entry.name)
   .setAllowedOverMetered(false).setAllowedOverRoaming(false)
   .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
   .setDestinationInExternalFilesDir(context,Environment.DIRECTORY_DOWNLOADS,entry.file))
  check(id>=0){"Transferul nu a fost acceptat"}
  val edit=prefs.edit().putLong(entry.file,id)
  if(reference!=null)edit.putString(entry.file+".sha",reference.sha256).putLong(entry.file+".bytes",reference.bytes).putString(entry.file+".revision",reference.revision).putString(entry.file+".algorithm",reference.algorithm)
  if(!edit.commit()){dm.remove(id);error("Metadatele nu au fost salvate")}

  return id
 }
 fun reference(entry:ModelCatalog.Entry):ModelMetadata.Reference{
  val hash=prefs.getString(entry.file+".sha",null)?:error("Lipsesc metadatele; descarcă din nou")
  val revision=prefs.getString(entry.file+".revision",null)?:error("Lipsește revizia")
  val bytes=prefs.getLong(entry.file+".bytes",-1)
  val algorithm=prefs.getString(entry.file+".algorithm","SHA-256")?:error("Algoritm absent")
  require(algorithm in setOf("SHA-256","git-blob-sha1"))
  require(hash.matches(Regex(if(algorithm=="SHA-256")"[0-9a-fA-F]{64}" else "[0-9a-fA-F]{40}"))&&(if(entry==ModelCatalog.kokoro)revision.matches(Regex("asset-[0-9]+")) else revision.matches(Regex("[0-9a-f]{40}")))&&bytes>0)
  return ModelMetadata.Reference(hash,bytes,revision,algorithm)
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
 fun cancel(entry:ModelCatalog.Entry){val id=id(entry);if(id>=0)dm.remove(id);prefs.edit().remove(entry.file).remove(entry.file+".sha").remove(entry.file+".bytes").remove(entry.file+".revision").remove(entry.file+".algorithm").apply()}
 fun uri(entry:ModelCatalog.Entry):Uri?=id(entry).takeIf{it>=0}?.let{dm.getUriForDownloadedFile(it)}
}
