package com.xilon.jarvis

internal object ResourceBudget {
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
