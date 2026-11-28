package com.xilon.jarvis

/** Shared, deterministic pacing policy for local neural voices. */
internal object VoiceProsody {
 fun speed(text:String,base:Float=1f):Float {
  val safe=if(base.isFinite())base.coerceIn(0.8f,1.2f) else 1f
  val end=text.trimEnd().trimEnd('"','”','»',')').trimEnd()
  val factor=when {
   end.endsWith("?")->0.96f
   end.endsWith("!")->1.03f
   end.endsWith("…") || end.endsWith("...")->0.92f
   else->1f
  }
  val lengthFactor=when {text.length>180->0.97f;else->1f}
  val clauseFactor=if(text.count{it==',' || it==';' || it==':'}>=3)0.98f else 1f
  return (safe*factor*lengthFactor*clauseFactor).coerceIn(0.8f,1.2f)
 }
}
