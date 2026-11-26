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
  require(entry in (ModelCatalog.entries+ModelCatalog.ocr+ModelCatalog.whisper+ModelCatalog.streamingAsr+ModelCatalog.romanianModels+ModelCatalog.wake+ModelCatalog.vision+listOf(ModelCatalog.hand,ModelCatalog.kokoro,ModelCatalog.silero)))
  check(active.putIfAbsent(entry.file,Transfer(DownloadManager.STATUS_PENDING,0,-1,0))==null){"Transfer deja activ"}
  val id=System.currentTimeMillis()
  try{
   check(!file(entry).exists()){"Fișier deja descărcat"}
   check(root.usableSpace>=200000000L){"Spațiu insuficient"}
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
    ModelCatalog.silero -> ModelMetadata.Reference("1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3",2327524L,"pinned-silero")
    ModelCatalog.hand -> ModelMetadata.Reference("fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1",7819105L,"pinned-hand")
    else -> ModelMetadata.fetch(entry)
   }
   val url=if(entry==ModelCatalog.hand || entry==ModelCatalog.silero)entry.url else if(entry.directUrl==null)entry.copy(revision=ref.revision).url else entry.url
   run {
    require(ref.bytes>0L){"Dimensiune model invalidă"}
    check(prefs.edit().putString(entry.file+".sha",ref.sha256).putLong(entry.file+".bytes",ref.bytes).putString(entry.file+".revision",ref.revision).putString(entry.file+".algorithm",ref.algorithm).commit())
   }
   val partial=part(entry)
   // Never append bytes from a different release or changed model revision.
   val previousHash=prefs.getString(entry.file+".partial.sha",null)
   val previousBytes=prefs.getLong(entry.file+".partial.bytes",-1L)
   if(previousHash!=ref.sha256 || previousBytes!=ref.bytes)partial.delete()
   check(prefs.edit().putString(entry.file+".partial.sha",ref.sha256)
    .putLong(entry.file+".partial.bytes",ref.bytes).commit()){"Nu pot salva starea transferului"}
   require(root.usableSpace>=maxOf(0L,ref.bytes-partial.length())+100000000L){"Spațiu insuficient pentru finalizarea modelului"}
   val offset=partial.length().takeIf{it in 1 until ref.bytes} ?: 0L
   if(partial.length()>0L && offset==0L)partial.delete()
   val http=(URL(url).openConnection() as HttpURLConnection).apply{
    connectTimeout=30000;readTimeout=120000;instanceFollowRedirects=true
    setRequestProperty("Accept-Encoding","identity")
    if(offset>0L)setRequestProperty("Range","bytes=$offset-")
   }
   connection=http
   val response=http.responseCode
   check(response==200 || (offset>0L && response==206)){"HTTP $response"}
   val resumed=offset>0L && response==206 &&
    http.getHeaderField("Content-Range")?.startsWith("bytes $offset-")==true
   if(response==206)check(resumed){"Răspuns Range invalid"}
   val initial=if(resumed)offset else 0L
   val total=ref?.bytes?:http.contentLengthLong
   val limit=ref?.bytes?:if(entry==ModelCatalog.hand)64L*1024*1024 else 512L*1024*1024
   var bytes=initial
   val started=android.os.SystemClock.elapsedRealtime()
   http.inputStream.use{src->java.io.FileOutputStream(partial,resumed).use{dst->
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
   prefs.edit().putInt(entry.file+".state",DownloadManager.STATUS_SUCCESSFUL).remove(entry.file+".partial.sha").remove(entry.file+".partial.bytes").remove(entry.file+".error").apply()
  }catch(e:Exception){
   // Preserve partial bytes for the next verified retry.
   prefs.edit().putInt(entry.file+".state",DownloadManager.STATUS_FAILED).putString(entry.file+".error",e.message?:"Eroare").apply()
  }finally{connection?.disconnect();active.remove(entry.file);if(cancelled.remove(entry.file))clear(entry)}
 }
 fun reference(entry:ModelCatalog.Entry):ModelMetadata.Reference{
  if(prefs.getString(entry.file+".sha",null)==null && file(entry).isFile){
   // Recover metadata for older completed downloads without trusting file presence.
   val recovered=when(entry){
    ModelCatalog.hand -> ModelMetadata.Reference("fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1",7819105L,"pinned-hand")
    ModelCatalog.silero -> ModelMetadata.Reference("1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3",2327524L,"pinned-silero")
    ModelCatalog.kokoro -> ModelMetadata.kokoro()
    else -> ModelMetadata.fetch(entry)
   }
   ModelIntegrity.verify(file(entry).inputStream(),recovered.sha256,recovered.bytes,recovered.algorithm)
   check(prefs.edit().putString(entry.file+".sha",recovered.sha256).putLong(entry.file+".bytes",recovered.bytes).putString(entry.file+".revision",recovered.revision).putString(entry.file+".algorithm",recovered.algorithm).commit()){"Nu pot salva metadatele recuperate"}
  }
  val hash=prefs.getString(entry.file+".sha",null)?:error("Lipsesc metadatele; descarcă din nou")
  val revision=prefs.getString(entry.file+".revision",null)?:error("Lipsește revizia")
  val bytes=prefs.getLong(entry.file+".bytes",-1)
  val algorithm=prefs.getString(entry.file+".algorithm","SHA-256")?:error("Algoritm absent")
  require(algorithm in setOf("SHA-256","git-blob-sha1"))
  require(hash.matches(Regex(if(algorithm=="SHA-256")"[0-9a-fA-F]{64}" else "[0-9a-fA-F]{40}"))&&(when(entry){ModelCatalog.kokoro->revision.matches(Regex("asset-[0-9]+"));ModelCatalog.hand->revision=="pinned-hand";ModelCatalog.silero->revision=="pinned-silero";else->revision.matches(Regex("[0-9a-f]{40}"))})&&bytes>0)
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
  return Transfer(DownloadManager.STATUS_FAILED,part(entry).length(),-1,0,error=(prefs.getString(entry.file+".error",null) ?: "Transfer întrerupt")+"; reîncearcă")
 }
 fun status(entry:ModelCatalog.Entry)=transfer(entry).label
 fun canStart(entry:ModelCatalog.Entry)=transfer(entry).state in setOf(-1,DownloadManager.STATUS_FAILED)
 private fun clear(entry:ModelCatalog.Entry){file(entry).delete();part(entry).delete();val edit=prefs.edit();for(s in listOf("",".sha",".bytes",".revision",".algorithm",".state",".error",".partial.sha",".partial.bytes"))edit.remove(entry.file+s);edit.apply()}
 fun cancel(entry:ModelCatalog.Entry){if(active.containsKey(entry.file))cancelled.add(entry.file) else clear(entry)}
 fun uri(entry:ModelCatalog.Entry):Uri?=file(entry).takeIf{it.isFile}?.let{Uri.fromFile(it)}
}
