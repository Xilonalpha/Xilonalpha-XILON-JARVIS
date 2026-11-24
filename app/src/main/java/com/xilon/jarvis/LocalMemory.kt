package com.xilon.jarvis
import android.content.Context
class LocalMemory(context: Context) {
 private val prefs = context.getSharedPreferences("jarvis_memory", Context.MODE_PRIVATE)
 fun remember(key: String, value: String): Boolean {
  require(key.isNotBlank() && value.isNotBlank())
  return prefs.edit().putString(key.trim(), value.trim()).commit()
 }
 fun recall(key: String): String? = prefs.getString(key.trim(), null)
 fun forget(key: String): Boolean = prefs.edit().remove(key.trim()).commit()
}
