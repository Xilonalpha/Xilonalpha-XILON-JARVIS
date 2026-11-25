package com.xilon.jarvis

internal object ResourceBudget {
 fun gpuLayers(availableMiB:Long,lowMemory:Boolean,thermal:Int,powerSave:Boolean):Int = when {lowMemory || thermal>=3 || powerSave || availableMiB<1536 -> 0;availableMiB<2560 || thermal>=2 -> 4;availableMiB<4096 -> 8;else -> 16}
 fun speechThreads(cpus:Int,availableMiB:Long,lowMemory:Boolean,thermal:Int):Int = when {lowMemory || availableMiB<768 || thermal>=3 -> 1;thermal>=2 || availableMiB<1536 -> minOf(2,cpus.coerceAtLeast(1));else -> (cpus/2).coerceIn(1,4)}
 fun allowOptionalVision(availableMiB:Long,lowMemory:Boolean,thermal:Int):Boolean = !lowMemory && availableMiB>=512 && thermal<4
 fun visionInterval(availableMiB:Long, lowMemory:Boolean, thermal:Int, powerSave:Boolean):Long =
  when { lowMemory || thermal>=3 -> 10000L
   powerSave || thermal>=2 || availableMiB<768 -> 6000L
   else -> 3000L }
 fun planningContext(availableMiB:Long, lowMemory:Boolean, thermal:Int):Int =
  when { lowMemory || thermal>=3 || availableMiB<768 -> 1024
   thermal>=2 || availableMiB<2048 -> 2048
   else -> 4096 }
 fun conversationContext(availableMiB:Long,lowMemory:Boolean,thermal:String):Int =
  when { lowMemory || availableMiB<768 || thermal in setOf("severe","critical","emergency","shutdown") -> 512
   availableMiB<2048 || thermal=="moderate" -> 1024
   else -> 2048 }
 fun threads(cpus:Int,lowMemory:Boolean,thermal:String):Int =
  when { lowMemory || thermal in setOf("severe","critical","emergency","shutdown") -> 1
   thermal=="moderate" -> 2.coerceAtMost(cpus.coerceAtLeast(1))
   else -> (cpus/2).coerceIn(1,4) }
}
