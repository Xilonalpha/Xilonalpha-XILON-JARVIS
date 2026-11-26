package com.xilon.jarvis

internal fun localTtsThreadBudget(context: android.content.Context): Int {
 val memory=android.app.ActivityManager.MemoryInfo()
 context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(memory)
 val thermal=if(android.os.Build.VERSION.SDK_INT>=29)context.getSystemService(android.os.PowerManager::class.java).currentThermalStatus else 0
 return ResourceBudget.speechThreads(Runtime.getRuntime().availableProcessors(),memory.availMem/1048576,memory.lowMemory,thermal)
}
