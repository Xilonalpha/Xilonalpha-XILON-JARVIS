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
 private var nativeModel: NativeModel? = null
 private var busy = false
 private lateinit var memory: LocalMemory
 private lateinit var transcript: TextView
 private lateinit var scroll: ScrollView
 private lateinit var modelStatus: TextView
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  memory = LocalMemory(this)
  val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,48,24,24) }
  root.addView(TextView(this).apply { text = "XILON JARVIS"; textSize = 24f })
  modelStatus = TextView(this).apply { text = "Selectează un model GGUF pentru inferență locală." }
  root.addView(modelStatus)
  root.addView(Button(this).apply { text = "Selectează GGUF"; setOnClickListener {
   if(!busy) startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }, 41)
  } })
  transcript = TextView(this).apply { textSize = 16f; setTextIsSelectable(true) }
  scroll = ScrollView(this).apply { addView(transcript) }
  root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
  val input = EditText(this).apply { hint = "Scrie o comandă"; maxLines = 4 }
  root.addView(input)
  root.addView(Button(this).apply { text = "Trimite"; setOnClickListener {
   val q = input.text.toString().trim()
   if(q.isNotEmpty() && !busy) {
    append("Tu: "+q); input.text.clear()
    val local = respond(q)
    if(local != null) append("JARVIS: "+local)
    else {
     busy=true
     worker.execute {
      val answer=try { nativeModel?.generate(q) ?: "Selectează și încarcă un model GGUF." } catch(e:Exception) { "Eroare inferență: "+e.message } catch(e:LinkageError) { "Biblioteca nativă indisponibilă: "+e.message }
      runOnUiThread { if(!isDestroyed) { busy=false; append("JARVIS: "+answer) } }
     }
    }
   }
  } })
  setContentView(root)
  append("Comenzi locale: ora, baterie, stocare, memorează cheie=valoare, amintește cheie.")
 }
 private fun append(s: String) { transcript.append(s+"\n\n"); scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) } }
 private fun respond(q: String): String? = try {
  when {
   q.equals("ora",true) -> DateFormat.getTimeInstance().format(Date())
   q.equals("baterie",true) -> getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).let { if(it in 0..100) "$it%" else "Nivel indisponibil" }
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
   getSharedPreferences("jarvis_model",MODE_PRIVATE).edit().putString("uri",uri.toString()).apply()
   busy=true
   modelStatus.text="Se copiază și se încarcă modelul local..."
   worker.execute {
    val target=java.io.File(filesDir,"model-"+java.util.UUID.randomUUID()+".gguf")
    var candidate: NativeModel?=null
    val status=try {
     contentResolver.openInputStream(uri)?.use { src -> target.outputStream().use { dst -> src.copyTo(dst) } } ?: error("Fișier inaccesibil")
     val next=NativeModel()
     candidate=next
     next.load(target.absolutePath)
     nativeModel?.close()
     nativeModel=next
     candidate=null
     filesDir.listFiles()?.filter { it.name.startsWith("model-") && it.extension=="gguf" && it!=target }?.forEach { it.delete() }
     "Model GGUF încărcat. Poți trimite un mesaj."
    } catch(e:Exception) { candidate?.close(); target.delete(); "Încărcare eșuată: "+e.message } catch(e:LinkageError) { target.delete(); "Biblioteca nativă indisponibilă: "+e.message }
    runOnUiThread { if(!isDestroyed) { busy=false; modelStatus.text=status } }
   }
  } catch(e:Exception) { modelStatus.text="Selectarea a eșuat: "+e.message }
 }
 override fun onDestroy() {
  worker.execute { nativeModel?.close(); nativeModel=null }
  worker.shutdown()
  super.onDestroy()
 }
}
