package com.xilon.jarvis

internal object ResourceBudget {
 // Confirmed native Vulkan device-lost crash during llama_decode on this Redmi family.
 // Keep the GPU backend in the APK, but never auto-enable it on affected devices.
 fun vulkanBlocked(manufacturer:String,device:String):Boolean =
  device.equals("kunzite",ignoreCase=true) &&
   (manufacturer.equals("Xiaomi",ignoreCase=true) ||
    manufacturer.equals("Redmi",ignoreCase=true) ||
    manufacturer.equals("POCO",ignoreCase=true))

 // One policy for automatic and experimental inference selection.
 // Explicit device exclusions override user opt-in to avoid native process aborts.
 fun inferenceGpuLayers(manufacturer:String,device:String,experimental:Boolean,
  availableMiB:Long,lowMemory:Boolean,thermal:Int,powerSave:Boolean):Int =
  if(!experimental || vulkanBlocked(manufacturer,device))0
  else gpuLayers(availableMiB,lowMemory,thermal,powerSave)

 fun gpuLayers(availableMiB:Long,lowMemory:Boolean,thermal:Int,powerSave:Boolean):Int = when {lowMemory || thermal>=3 || powerSave || availableMiB<1536 -> 0;availableMiB<2560 || thermal>=2 -> 4;availableMiB<4096 -> 8;else -> 16}
 fun maxConcurrentOptionalVision(availableMiB:Long,lowMemory:Boolean,thermal:Int,powerSave:Boolean):Int =
  if(lowMemory || powerSave || thermal>=3 || availableMiB<1024)0 else 1
 fun adaptiveBatchSize(availableMiB:Long,lowMemory:Boolean,thermal:Int):Int =
  when {lowMemory || thermal>=3 || availableMiB<1024 -> 1;thermal>=2 || availableMiB<3072 -> 2;else -> 4}
 fun speechThreads(cpus:Int,availableMiB:Long,lowMemory:Boolean,thermal:Int):Int = when {lowMemory || availableMiB<768 || thermal>=3 -> 1;thermal>=2 || availableMiB<1536 -> minOf(2,cpus.coerceAtLeast(1));else -> (cpus/2).coerceIn(1,4)}
 fun allowOptionalVision(availableMiB:Long,lowMemory:Boolean,thermal:Int):Boolean = maxConcurrentOptionalVision(availableMiB,lowMemory,thermal,false)>0
 fun gpuEnabled(availableMiB:Long,lowMemory:Boolean,thermal:Int,powerSave:Boolean):Boolean = gpuLayers(availableMiB,lowMemory,thermal,powerSave)>0
 fun visionInterval(availableMiB:Long, lowMemory:Boolean, thermal:Int, powerSave:Boolean):Long =
  when { lowMemory || thermal>=3 -> 10000L
   powerSave || thermal>=2 || availableMiB<768 -> 6000L
   else -> 3000L }
 fun planningContext(availableMiB:Long, lowMemory:Boolean, thermal:Int):Int =
  when { lowMemory || thermal>=3 || availableMiB<768 -> 1024
   thermal>=2 || availableMiB<2048 -> 2048
   else -> 4096 }
 // Social greetings and identity requests need a short, context-free response.
 // This avoids a long multi-turn prefill for 4B-class GGUFs on CPU.
 fun quickConversation(question:String):Boolean {
  val plain=java.text.Normalizer.normalize(question.trim().lowercase(java.util.Locale.ROOT),
   java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
   .trimEnd(' ','!','.','?')
  return Regex("^(?:(?:salut|salutare|buna ziua|buna|hello|hei)[,! ]+)?(?:cine esti|ce faci|cum te cheama|salut|salutare|buna|buna ziua|hello)$").matches(plain)
 }
 fun chatResponseTokens(context:Int,quick:Boolean):Int =
  if(quick) { if(context<=512)64 else 96 }
  else if(context<=512)96 else if(context>=2048)384 else 192

 fun conversationContext(availableMiB:Long,lowMemory:Boolean,thermal:String):Int =
  when { lowMemory || availableMiB<768 || thermal in setOf("severe","critical","emergency","shutdown") -> 512
   availableMiB<2048 || thermal=="moderate" -> 1024
   else -> 2048 }
 fun threads(cpus:Int,lowMemory:Boolean,thermal:String,availableMiB:Long=Long.MAX_VALUE):Int =
  when { lowMemory || availableMiB<768 || thermal in setOf("severe","critical","emergency","shutdown") -> 1
   availableMiB<1536 || thermal=="moderate" -> 2.coerceAtMost(cpus.coerceAtLeast(1))
   else -> (cpus/2).coerceIn(1,4) }
}
