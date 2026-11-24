package com.xilon.jarvis
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.os.BatteryManager
import android.os.StatFs
import android.widget.*
import android.view.ViewGroup
import java.text.DateFormat
import java.util.Date
class MainActivity : Activity() {
 private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
 @Volatile private var nativeModel: NativeModel? = null
 private val history=mutableListOf<Pair<String,String>>()
 private lateinit var voice: OfflineVoice
 private var speakReplies=false
 private lateinit var listening: OfflineListening
 private lateinit var voiceInput: EditText
 private var busy = false
 private lateinit var memory: LocalMemory
 private lateinit var transcript: TextView
 private lateinit var scroll: ScrollView
 private lateinit var modelStatus: TextView
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  memory = LocalMemory(this)
  voice=OfflineVoice(this)
  listening=OfflineListening(this)
  try {
   val saved=org.json.JSONArray(getSharedPreferences("conversation",MODE_PRIVATE).getString("turns","[]"))
   for(i in maxOf(0,saved.length()-6) until saved.length()){
    val item=saved.getJSONObject(i)
    val role=item.getString("role")
    if(role=="user" || role=="assistant") history.add(role to item.getString("text"))
   }
  } catch(e:org.json.JSONException){ history.clear() }
  val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,48,24,24) }
  root.addView(TextView(this).apply { text = "XILON JARVIS"; textSize = 24f })
  modelStatus = TextView(this).apply { text = "Selectează un model GGUF pentru inferență locală." }
  root.addView(modelStatus)
  root.addView(Switch(this).apply { text="Răspuns vocal offline"; setOnCheckedChangeListener { _, enabled -> speakReplies=enabled; if(!enabled) voice.stop() } })
  root.addView(Button(this).apply { text = "Selectează GGUF"; setOnClickListener {
   if(!busy) startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }, 41)
  } })
  transcript = TextView(this).apply { textSize = 16f; setTextIsSelectable(true) }
  scroll = ScrollView(this).apply { addView(transcript) }
  root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
  val input = EditText(this).apply { hint = "Scrie o comandă"; maxLines = 4 }
  voiceInput=input
  root.addView(input)
  root.addView(Button(this).apply { text="Microfon offline"; setOnClickListener { startMicrophone() } })
  root.addView(Button(this).apply { text = "Trimite"; setOnClickListener {
   val q = input.text.toString().trim()
   if(q.isNotEmpty() && !busy) {
    append("Tu: "+q); input.text.clear()
    val local = respond(q)
    if(local != null) { append("JARVIS: "+local); speakReply(local) }
    else {
     busy=true
     nativeModel?.resetCancellation()
     val prefix=transcript.text.toString()+"JARVIS: "
     nativeModel?.onProgress = { partial -> runOnUiThread { if(!isDestroyed && busy) { transcript.text=prefix+partial; scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) } } } }
     worker.execute {
      val answer=try { nativeModel?.let { model ->
       val info=android.app.ActivityManager.MemoryInfo()
       getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info)
       val mib=info.availMem/1048576
       val context=when { info.lowMemory || mib<768 -> 512; mib<2048 -> 1024; else -> 2048 }
       val threads=if(info.lowMemory) 1 else (Runtime.getRuntime().availableProcessors()/2).coerceIn(1,4)
       val recent=history.takeLast(if(context==512) 2 else 6)
       val answer=model.generate(q,threads,context,if(context==512) 96 else 256,recent)
       if(!model.isCancelled() && answer.isNotBlank()){
        history.add("user" to q); history.add("assistant" to answer)
        while(history.size>6) history.removeAt(0)
        persistHistory()
       }
       answer
      } ?: "Selectează și încarcă un model GGUF." } catch(e:Exception) { "Eroare inferență: "+e.message } catch(e:LinkageError) { "Biblioteca nativă indisponibilă: "+e.message }
      nativeModel?.onProgress=null
      runOnUiThread { if(!isDestroyed) { busy=false; transcript.text=prefix.removeSuffix("JARVIS: "); append("JARVIS: "+answer + if(nativeModel?.isCancelled()==true) " [Oprit]" else ""); if(nativeModel?.isCancelled()!=true) speakReply(answer) } }
     }
    }
   }
  } })
  root.addView(Button(this).apply { text="STOP"; setOnClickListener { nativeModel?.stop(); voice.stop(); listening.close() } })
  setContentView(root)
  val saved=getSharedPreferences("jarvis_model",MODE_PRIVATE).getString("file",null)
  if(saved!=null && saved.matches(Regex("model-[a-f0-9-]+\\.gguf"))) {
   val file=java.io.File(filesDir,saved)
   if(file.isFile) {
    busy=true
    modelStatus.text="Se restaurează ultimul model..."
    worker.execute {
     val status=try { val next=NativeModel(); next.load(file.absolutePath); nativeModel=next; "Model restaurat." }
      catch(e:Exception) { "Restaurare eșuată: "+e.message }
      catch(e:LinkageError) { "Biblioteca nativă indisponibilă: "+e.message }
     runOnUiThread { if(!isDestroyed) { busy=false; modelStatus.text=status } }
    }
   }
  }
  append("Comenzi locale: ora, baterie, ram, stocare, memorează cheie=valoare, amintește cheie.")
 }
 private fun startMicrophone(){
  if(busy)return
  if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
   requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO),42);return
  }
  voice.stop()
  listening.listen({ text -> if(!isDestroyed) voiceInput.setText(text) },{ error -> if(!isDestroyed) append(error) })
 }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  if(requestCode==42){if(grantResults.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED)startMicrophone() else append("Permisiunea microfonului a fost refuzată.")}
 }
 private fun speakReply(text:String){ if(speakReplies && text.isNotBlank()) voice.speak(text)?.let { append(it) } }
 private fun persistHistory(){
  val array=org.json.JSONArray()
  history.forEach { (role,text) -> array.put(org.json.JSONObject().put("role",role).put("text",text)) }
  getSharedPreferences("conversation",MODE_PRIVATE).edit().putString("turns",array.toString()).apply()
 }
 private fun append(s: String) { transcript.append(s+"\n\n"); scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) } }
 private fun respond(q: String): String? = try {
  when {
   q.equals("ora",true) -> DateFormat.getTimeInstance().format(Date())
   q.equals("baterie",true) -> getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).let { if(it in 0..100) "$it%" else "Nivel indisponibil" }
   q.equals("ram",true) -> android.app.ActivityManager.MemoryInfo().let { info -> getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info); "${info.availMem / 1048576} MiB disponibili din ${info.totalMem / 1048576} MiB; memorie redusă: ${info.lowMemory}" }
   q.equals("stocare",true) -> "${StatFs(filesDir.path).availableBytes / 1048576} MiB disponibili"
   q.startsWith("memorează ",true) -> { val parts=q.substringAfter(" ").split("=",limit=2); if(parts.size!=2 || parts.any { it.isBlank() }) "Format: memorează cheie=valoare" else if(memory.remember(parts[0],parts[1])) "Salvat local." else "Salvarea a eșuat." }
   q.startsWith("amintește ",true) -> memory.recall(q.substringAfter(" ")) ?: "Cheie negăsită."
   else -> null
  }
 } catch(e: Exception) { "Eroare: "+(e.message ?: e.javaClass.simpleName) }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode!=41 || resultCode!=RESULT_OK || busy) return
  val uri=data?.data ?: return
  try {
   val magic=ByteArray(4)
   val valid=contentResolver.openInputStream(uri)?.use { java.io.DataInputStream(it).readFully(magic); magic.contentEquals(byteArrayOf(71,71,85,70)) } ?: false
   if(!valid) { modelStatus.text="Fișierul nu are antet GGUF."; return }
   contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
   busy=true
   modelStatus.text="Se copiază și se încarcă modelul local..."
   worker.execute {
    val target=java.io.File(filesDir,"model-"+java.util.UUID.randomUUID()+".gguf")
    var candidate: NativeModel?=null
    val status=try {
     val size=contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.SIZE),null,null,null)?.use { c -> if(c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L } ?: -1L
     if(size>0 && size>StatFs(filesDir.path).availableBytes) error("Spațiu insuficient pentru copiere")
     contentResolver.openInputStream(uri)?.use { src -> target.outputStream().use { dst -> src.copyTo(dst) } } ?: error("Fișier inaccesibil")
     nativeModel?.close()
     nativeModel=null
     val next=NativeModel()
     candidate=next
     next.load(target.absolutePath)
     nativeModel=next
     history.clear()
     persistHistory()
     candidate=null
     getSharedPreferences("jarvis_model",MODE_PRIVATE).edit().putString("file",target.name).apply()
     filesDir.listFiles()?.filter { it.name.startsWith("model-") && it.extension=="gguf" && it!=target }?.forEach { it.delete() }
     "Model GGUF încărcat. Poți trimite un mesaj."
    } catch(e:Exception) { candidate?.close(); target.delete(); "Încărcare eșuată: "+e.message } catch(e:LinkageError) { target.delete(); "Biblioteca nativă indisponibilă: "+e.message }
    runOnUiThread { if(!isDestroyed) { busy=false; modelStatus.text=status } }
   }
  } catch(e:Exception) { modelStatus.text="Selectarea a eșuat: "+e.message }
 }
 override fun onDestroy() {
  listening.close()
  voice.close()
  nativeModel?.stop()
  worker.execute { nativeModel?.close(); nativeModel=null }
  worker.shutdown()
  super.onDestroy()
 }
}
