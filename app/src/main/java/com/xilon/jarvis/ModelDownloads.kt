package com.xilon.jarvis
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
class ModelDownloads(context:Context){
 private val context=context.applicationContext
 private val prefs=this.context.getSharedPreferences("omega_private_downloads",Context.MODE_PRIVATE)
 private val root=File(this.context.filesDir,"downloads").apply{mkdirs()}
 companion object{
  private val pool=Executors.newFixedThreadPool(2)
  private val active=ConcurrentHashMap<String,Transfer>()
  private val cancelled=ConcurrentHashMap.newKeySet<String>()
 }
 private fun file(e:ModelCatalog.Entry)=File(root,e.file)
 private fun part(e:ModelCatalog.Entry)=File(root,e.file+".part")
 @Synchronized fun start(entry:ModelCatalog.Entry):Long{
  require(entry in (ModelCatalog.entries+ModelCatalog.ocr+ModelCatalog.whisper+ModelCatalog.wake+ModelCatalog.vision+listOf(ModelCatalog.hand,ModelCatalog.kokoro)))
  check(active.putIfAbsent(entry.file,Transfer(DownloadManager.STATUS_PENDING,0,-1,0))==null){"Transfer deja activ"}
  val id=System.currentTimeMillis()
  try{
   check(!file(entry).exists()){"Fișier deja descărcat"}
   check(root.usableSpace>=entry.mb*2000000L+200000000L){"Spațiu insuficient"}
   cancelled.remove(entry.file)
   check(prefs.edit().putLong(entry.file,id).putInt(entry.file+".state",DownloadManager.STATUS_PENDING).commit())
   pool.execute{download(entry)}
   return id
  }catch(e:Exception){active.remove(entry.file);throw e}
 }
 private fun download(entry:ModelCatalog.Entry){
  var connection:HttpURLConnection?=null
  try{
   val ref=when(entry){
    ModelCatalog.kokoro -> ModelMetadata.kokoro()
    ModelCatalog.hand -> ModelMetadata.Reference("fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1",7819105L,"pinned-hand")
    else -> ModelMetadata.fetch(entry)
   }
   val url=if(entry==ModelCatalog.hand)entry.url else if(entry.directUrl==null)entry.copy(revision=ref.revision).url else entry.url
   {
    require(ref.bytes<=(root.usableSpace-200000000L)/2){"Spațiu insuficient"}
    check(prefs.edit().putString(entry.file+".sha",ref.sha256).putLong(entry.file+".bytes",ref.bytes).putString(entry.file+".revision",ref.revision).putString(entry.file+".algorithm",ref.algorithm).commit())
   }
   val http=(URL(url).openConnection() as HttpURLConnection).apply{connectTimeout=20000;readTimeout=30000;instanceFollowRedirects=true;setRequestProperty("Accept-Encoding","identity")}
   connection=http
   check(http.responseCode==200){"HTTP "+http.responseCode}
   val total=ref?.bytes?:http.contentLengthLong
   val limit=ref?.bytes?:if(entry==ModelCatalog.hand)64L*1024*1024 else 512L*1024*1024
   var bytes=0L
   val started=android.os.SystemClock.elapsedRealtime()
   http.inputStream.use{src->part(entry).outputStream().use{dst->
    val buffer=ByteArray(65536)
    while(true){
     check(!cancelled.contains(entry.file)){"Anulat"}
     val n=src.read(buffer);if(n<0)break
     bytes+=n;require(bytes<=limit){"Dimensiune depășită"};dst.write(buffer,0,n)
     active[entry.file]=Transfer(DownloadManager.STATUS_RUNNING,bytes,total,0,bytes*1000/maxOf(1,android.os.SystemClock.elapsedRealtime()-started))
    }
   }}
   require(bytes>0&&(total<=0||bytes==total)){"Transfer incomplet"}
   ModelIntegrity.verify(part(entry).inputStream(),ref.sha256,ref.bytes,ref.algorithm)
   check(!cancelled.contains(entry.file)){"Anulat"}
   check(part(entry).renameTo(file(entry))){"Salvare eșuată"}
   prefs.edit().putInt(entry.file+".state",DownloadManager.STATUS_SUCCESSFUL).apply()
  }catch(e:Exception){
   part(entry).delete()
   prefs.edit().putInt(entry.file+".state",DownloadManager.STATUS_FAILED).putString(entry.file+".error",e.message?:"Eroare").apply()
  }finally{connection?.disconnect();active.remove(entry.file);if(cancelled.remove(entry.file))clear(entry)}
 }
 fun reference(entry:ModelCatalog.Entry):ModelMetadata.Reference{
  val hash=prefs.getString(entry.file+".sha",null)?:error("Lipsesc metadatele; descarcă din nou")
  val revision=prefs.getString(entry.file+".revision",null)?:error("Lipsește revizia")
  val bytes=prefs.getLong(entry.file+".bytes",-1)
  val algorithm=prefs.getString(entry.file+".algorithm","SHA-256")?:error("Algoritm absent")
  require(algorithm in setOf("SHA-256","git-blob-sha1"))
  require(hash.matches(Regex(if(algorithm=="SHA-256")"[0-9a-fA-F]{64}" else "[0-9a-fA-F]{40}"))&&(when(entry){ModelCatalog.kokoro->revision.matches(Regex("asset-[0-9]+"));ModelCatalog.hand->revision=="pinned-hand";else->revision.matches(Regex("[0-9a-f]{40}"))})&&bytes>0)
  return ModelMetadata.Reference(hash,bytes,revision,algorithm)
 }

 fun id(entry:ModelCatalog.Entry)=prefs.getLong(entry.file,-1)
 data class Transfer(val state:Int,val bytes:Long,val total:Long,val reason:Int,val speed:Long=0,val error:String?=null){
  val percent:Int? get()=if(total>0)(bytes.toDouble()*100/total).toInt().coerceIn(0,100) else null
  val label:String get()=when(state){
   -1->"Nedescărcat"
   DownloadManager.STATUS_SUCCESSFUL->"Descărcat local"
   DownloadManager.STATUS_FAILED->"Eșuat: "+(error?:"Transfer întrerupt")
   DownloadManager.STATUS_PENDING->"Pregătire transfer"
   else->(percent?.let{"Transfer: "+it+"%"}?:"Transfer: "+bytes/1048576+" MiB")+" • "+speed/1024+" KiB/s"+(if(speed>0&&total>bytes)" • "+((total-bytes)/speed)+" s rămase" else "")
  }
 }
 fun transfer(entry:ModelCatalog.Entry):Transfer{
  active[entry.file]?.let{return it}
  if(file(entry).isFile)return Transfer(DownloadManager.STATUS_SUCCESSFUL,file(entry).length(),file(entry).length(),0)
  if(id(entry)<0)return Transfer(-1,0,-1,0)
  return Transfer(DownloadManager.STATUS_FAILED,0,-1,0,error=prefs.getString(entry.file+".error",null))
 }
 fun status(entry:ModelCatalog.Entry)=transfer(entry).label
 fun canStart(entry:ModelCatalog.Entry)=transfer(entry).state in setOf(-1,DownloadManager.STATUS_FAILED)
 private fun clear(entry:ModelCatalog.Entry){file(entry).delete();part(entry).delete();val edit=prefs.edit();for(s in listOf("",".sha",".bytes",".revision",".algorithm",".state",".error"))edit.remove(entry.file+s);edit.apply()}
 fun cancel(entry:ModelCatalog.Entry){if(active.containsKey(entry.file))cancelled.add(entry.file) else clear(entry)}
 fun uri(entry:ModelCatalog.Entry):Uri?=file(entry).takeIf{it.isFile}?.let{Uri.fromFile(it)}
}
