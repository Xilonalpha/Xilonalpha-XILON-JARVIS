package com.jarvis.mobile.core.tools.impl
import com.xilon.jarvis.LocalScreen
import com.jarvis.mobile.core.observer.ScreenObservation
import kotlinx.serialization.json.*
object T {
 fun str(args:JsonObject,key:String)= (args[key] as? JsonPrimitive)?.contentOrNull
 fun int(args:JsonObject,key:String)= (args[key] as? JsonPrimitive)?.intOrNull
 fun bool(args:JsonObject,key:String)= (args[key] as? JsonPrimitive)?.booleanOrNull
 fun dbl(args:JsonObject,key:String)= (args[key] as? JsonPrimitive)?.doubleOrNull
 suspend fun freshObserve(settleMs:Long=260):ScreenObservation? {
  kotlinx.coroutines.delay(settleMs)
  return LocalScreen.observe() ?: run { kotlinx.coroutines.delay(220); LocalScreen.observe() }
 }
 suspend fun settle(ms:Long=400)=kotlinx.coroutines.delay(ms)
}
