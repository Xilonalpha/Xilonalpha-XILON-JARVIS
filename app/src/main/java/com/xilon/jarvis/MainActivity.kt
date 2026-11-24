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
 private lateinit var memory: LocalMemory
 private lateinit var transcript: TextView
 private lateinit var scroll: ScrollView
 private lateinit var modelStatus: TextView
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  memory = LocalMemory(this)
  val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,48,24,24) }
  root.addView(TextView(this).apply { text = "XILON JARVIS"; textSize = 24f })
  modelStatus = TextView(this).apply { text = "Inferență GGUF indisponibilă: motorul nativ nu este integrat." }
  root.addView(modelStatus)
  root.addView(Button(this).apply { text = "Selectează GGUF"; setOnClickListener {
   startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }, 41)
  } })
  transcript = TextView(this).apply { textSize = 16f; setTextIsSelectable(true) }
  scroll = ScrollView(this).apply { addView(transcript) }
  root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
  val input = EditText(this).apply { hint = "Scrie o comandă"; maxLines = 4 }
  root.addView(input)
  root.addView(Button(this).apply { text = "Trimite"; setOnClickListener {
   val q = input.text.toString().trim()
   if(q.isNotEmpty()) { append("Tu: "+q); append("JARVIS: "+respond(q)); input.text.clear() }
  } })
  setContentView(root)
  append("Comenzi locale: ora, baterie, stocare, memorează cheie=valoare, amintește cheie.")
 }
 private fun append(s: String) { transcript.append(s+"\n\n"); scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) } }
 private fun respond(q: String): String = try {
  when {
   q.equals("ora",true) -> DateFormat.getTimeInstance().format(Date())
   q.equals("baterie",true) -> getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).let { if(it in 0..100) "$it%" else "Nivel indisponibil" }
   q.equals("stocare",true) -> "${StatFs(filesDir.path).availableBytes / 1048576} MiB disponibili"
   q.startsWith("memorează ",true) -> { val parts=q.substringAfter(" ").split("=",limit=2); if(parts.size!=2 || parts.any { it.isBlank() }) "Format: memorează cheie=valoare" else if(memory.remember(parts[0],parts[1])) "Salvat local." else "Salvarea a eșuat." }
   q.startsWith("amintește ",true) -> memory.recall(q.substringAfter(" ")) ?: "Cheie negăsită."
   else -> "Motorul AI local nu este încă integrat. Comanda nu a fost executată."
  }
 } catch(e: Exception) { "Eroare: "+(e.message ?: e.javaClass.simpleName) }
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode!=41 || resultCode!=RESULT_OK) return
  val uri=data?.data ?: return
  try {
   val magic=ByteArray(4)
   val valid=contentResolver.openInputStream(uri)?.use { java.io.DataInputStream(it).readFully(magic); magic.contentEquals(byteArrayOf(71,71,85,70)) } ?: false
   if(!valid) { modelStatus.text="Fișierul nu are antet GGUF."; return }
   contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
   getSharedPreferences("jarvis_model",MODE_PRIVATE).edit().putString("uri",uri.toString()).apply()
   modelStatus.text="Antet GGUF valid. Model selectat, dar inferența nu este încă disponibilă."
  } catch(e:Exception) { modelStatus.text="Selectarea a eșuat: "+e.message }
 }
}
