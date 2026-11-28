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
  require(entry in (ModelCatalog.entries+ModelCatalog.ocr+ModelCatalog.whisper+ModelCatalog.streamingAsr+ModelCatalog.romanianModels+ModelCatalog.wake+ModelCatalog.vision+listOf(ModelCatalog.hand,ModelCatalog.kokoro,ModelCatalog.silero,ModelCatalog.mihai)))
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
   val restored=runCatching{restoreCompleted(entry)}.getOrElse{failure->
    check(!cancelled.contains(entry.file)){"Anulat"}
    context.getSharedPreferences("omega_backup",Context.MODE_PRIVATE).edit().putString("last_error",failure.message?:"Backup inaccesibil").apply()
    false
   }
   if(restored)return
   check(!cancelled.contains(entry.file)){"Anulat"}
   val ref=when(entry){
    ModelCatalog.kokoro -> ModelMetadata.kokoro()
    ModelCatalog.mihai -> ModelMetadata.releaseAsset(ModelCatalog.mihai)
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
   val parent = checkNotNull(partial.parentFile)
   check(parent.isDirectory || parent.mkdirs() || parent.isDirectory){"Nu pot crea directorul de descărcare"}
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
  }finally{connection?.disconnect();try{if(!cancelled.contains(entry.file)&&file(entry).isFile)runCatching{backupCompleted(entry)}.onFailure{context.getSharedPreferences("omega_backup",Context.MODE_PRIVATE).edit().putString("last_error",it.message).apply()}}finally{if(cancelled.remove(entry.file))clear(entry);active.remove(entry.file)}}
 }
 private fun restoreCompleted(entry:ModelCatalog.Entry):Boolean{
  val tree=context.getSharedPreferences("omega_backup",Context.MODE_PRIVATE).getString("tree",null)?.let{Uri.parse(it)}?:return false
  val resolver=context.contentResolver
  val rootId=android.provider.DocumentsContract.getTreeDocumentId(tree)
  val children=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree,rootId)
  val candidates=mutableListOf<Pair<Uri,ModelMetadata.Reference>>()
  resolver.query(children,arrayOf("document_id","_display_name","_size"),null,null,null)?.use{cursor->
   while(cursor.moveToNext()){
    val name=cursor.getString(1)?:continue
    val suffix="-"+backupKey(entry)
    if(!name.startsWith("omega-")||!name.endsWith(suffix))continue
    val match=Regex("omega-(SHA-256|git-blob-sha1)-([a-fA-F0-9]{40,64})-([a-f0-9]{40}|asset-[0-9]+|pinned-hand|pinned-silero)-([0-9]+)").matchEntire(name.removeSuffix(suffix))?:continue
    val (algorithm,hash,revision,size)=match.destructured
    val bytes=size.toLongOrNull()?:continue
    if(bytes<=0||bytes!=cursor.getLong(2)||hash.length!=(if(algorithm=="SHA-256")64 else 40))continue
    if(entry==ModelCatalog.hand && (revision!="pinned-hand"||hash!="fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1"))continue
    if(entry==ModelCatalog.silero && (revision!="pinned-silero"||hash!="1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3"))continue
    if(entry in listOf(ModelCatalog.kokoro,ModelCatalog.mihai) && !revision.startsWith("asset-"))continue
    if(entry !in listOf(ModelCatalog.kokoro,ModelCatalog.mihai,ModelCatalog.hand,ModelCatalog.silero) && !revision.matches(Regex("[a-f0-9]{40}")))continue
    candidates.add(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0)) to ModelMetadata.Reference(hash,bytes,revision,algorithm))
   }
  }
  for((uri,ref) in candidates){
   val temp=File(root,"restore-"+java.util.UUID.randomUUID())
   try{
    check(root.usableSpace>=ref.bytes+100000000L){"Spațiu insuficient"}
    resolver.openInputStream(uri)?.use{input->temp.outputStream().use{out->
     val buffer=ByteArray(65536);var copied=0L
     while(true){check(!cancelled.contains(entry.file)){"Anulat"};val n=input.read(buffer);if(n<0)break;copied+=n;check(copied<=ref.bytes);out.write(buffer,0,n);active[entry.file]=Transfer(DownloadManager.STATUS_RUNNING,copied,ref.bytes,0)}
    }}?:error("Backup inaccesibil")
    ModelIntegrity.verify(temp.inputStream(),ref.sha256,ref.bytes,ref.algorithm)
    check(!cancelled.contains(entry.file))
    file(entry).parentFile?.mkdirs()
    check(prefs.edit().putString(entry.file+".sha",ref.sha256).putLong(entry.file+".bytes",ref.bytes).putString(entry.file+".revision",ref.revision).putString(entry.file+".algorithm",ref.algorithm).commit())
    check(temp.renameTo(file(entry)))
    part(entry).delete()
    prefs.edit().putInt(entry.file+".state",DownloadManager.STATUS_SUCCESSFUL).remove(entry.file+".error").apply()
    return true
   }catch(e:Exception){
    check(!cancelled.contains(entry.file)){"Anulat"}
    context.getSharedPreferences("omega_backup",Context.MODE_PRIVATE).edit().putString("last_error",e.message?:"Copie invalidă").apply()
   }finally{temp.delete()}
  }
  return false
 }
 private fun backupKey(entry:ModelCatalog.Entry):String=java.security.MessageDigest.getInstance("SHA-256").digest((entry.url+"|"+entry.file).toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it.toInt() and 255)}+"-"+file(entry).name
 private fun backupCompleted(entry:ModelCatalog.Entry){
  val settings=context.getSharedPreferences("omega_backup",Context.MODE_PRIVATE)
  val tree=settings.getString("tree",null)?.let{Uri.parse(it)}?:return
  val resolver=context.contentResolver
  val rootId=android.provider.DocumentsContract.getTreeDocumentId(tree)
  val parent=android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,rootId)
  val source=file(entry)
  val ref=reference(entry)
  val name="omega-"+ref.algorithm+"-"+ref.sha256+"-"+ref.revision+"-"+ref.bytes+"-"+backupKey(entry)
  val children=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree,rootId)
  val exists=resolver.query(children,arrayOf("document_id","_display_name","_size"),null,null,null)?.use{cursor->
   var found=false
   while(cursor.moveToNext())if(cursor.getString(1)==name && cursor.getLong(2)==source.length()){
    val existing=android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0))
    found=runCatching{resolver.openInputStream(existing)?.use{ModelIntegrity.verify(it,ref.sha256,ref.bytes,ref.algorithm)}?:error("Backup inaccesibil");true}.getOrDefault(false)
    if(found)break
   }
   found
  }?:false
  if(exists){settings.edit().remove("last_error").apply();return}
  val dest=android.provider.DocumentsContract.createDocument(resolver,parent,"application/octet-stream",name)?:error("Nu pot crea backup")
  try{
   resolver.openOutputStream(dest,"w")?.use{out->source.inputStream().use{it.copyTo(out,65536)}}?:error("Backup inaccesibil")
   resolver.openInputStream(dest)?.use{ModelIntegrity.verify(it,ref.sha256,ref.bytes,ref.algorithm)}?:error("Backup inaccesibil")
   settings.edit().remove("last_error").apply()
  }catch(e:Exception){runCatching{android.provider.DocumentsContract.deleteDocument(resolver,dest)};throw e}
 }
 fun reference(entry:ModelCatalog.Entry):ModelMetadata.Reference{
  if(prefs.getString(entry.file+".sha",null)==null && file(entry).isFile){
   // Recover metadata for older completed downloads without trusting file presence.
   val recovered=when(entry){
    ModelCatalog.hand -> ModelMetadata.Reference("fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1",7819105L,"pinned-hand")
    ModelCatalog.silero -> ModelMetadata.Reference("1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3",2327524L,"pinned-silero")
    ModelCatalog.kokoro -> ModelMetadata.kokoro()
    ModelCatalog.mihai -> ModelMetadata.releaseAsset(ModelCatalog.mihai)
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
  require(hash.matches(Regex(if(algorithm=="SHA-256")"[0-9a-fA-F]{64}" else "[0-9a-fA-F]{40}"))&&(when(entry){ModelCatalog.kokoro,ModelCatalog.mihai->revision.matches(Regex("asset-[0-9]+"));ModelCatalog.hand->revision=="pinned-hand";ModelCatalog.silero->revision=="pinned-silero";else->revision.matches(Regex("[0-9a-f]{40}"))})&&bytes>0)
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
