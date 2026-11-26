package com.xilon.jarvis

import android.content.Context
import java.io.File

/** Caller serializes access to each engine. */
internal class VisionDetectorCache<T:AutoCloseable> {
 private var engine:T?=null
 private var revision=""
 fun release(){val old=engine;engine=null;revision="";old?.close()}
 fun acquire(file:File,open:()->T):T {
  val key="${file.absolutePath}:${file.length()}:${file.lastModified()}"
  if(key!=revision)release()
  return engine ?: open().also{engine=it;revision=key}
 }
 companion object {
  fun retain(context:Context):Boolean {
   val info=android.app.ActivityManager.MemoryInfo()
   context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(info)
   return !info.lowMemory && info.availMem>=1536L*1024*1024
  }
 }
}
