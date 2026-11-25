package com.jarvis.mobile.core.skills

import com.xilon.jarvis.LocalScreen
import com.jarvis.mobile.core.observer.ScreenObservation
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Adapted from MIT upstream SkillRunner: observation-only target polling. */
object SkillRunner {
 const val POLL_MS=60L
 const val FIND_TIMEOUT_MS=8000L
    suspend fun waitForElement(
        
        target: ElementTarget,
        timeoutMs: Long = FIND_TIMEOUT_MS,
    ): Pair<ScreenObservation, com.jarvis.mobile.core.observer.ScreenElement>? {
        require(!target.pkg.isNullOrBlank() && target.hasNode)
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs.coerceIn(0L,FIND_TIMEOUT_MS)
        while (true) {
            val obs = LocalScreen.observe()
            if (obs != null && obs.packageName == target.pkg) {
                val el = obs.elements.filter { candidate ->
                    (target.viewId == null || candidate.viewId == target.viewId) &&
                    (target.text == null || candidate.text == target.text) &&
                    (target.desc == null || candidate.desc == target.desc)
                }.singleOrNull()
                if (el != null && el.interactive && !el.isPassword) return obs to el
            }
            if (android.os.SystemClock.elapsedRealtime() >= deadline) return null
            delay(POLL_MS)
        }
    }



    suspend fun verifyChanged(obs: ScreenObservation?): Boolean {
        delay(420)
        val after = LocalScreen.observe() ?: return false
        return obs != null && after.packageName == obs.packageName && after.fingerprint() != obs.fingerprint()
    }

 data class ActionOutcome(val ok:Boolean,val message:String,val verified:Boolean)
 suspend fun click(target:ElementTarget,confirm:suspend (ElementTarget)->Boolean):ActionOutcome{
  val found=waitForElement(target)?:return ActionOutcome(false,"Țintă indisponibilă",false)
  val el=found.second
  if(!el.clickable)return ActionOutcome(false,"Elementul nu este clicabil",false)
  val exact=ElementTarget(text=el.text,desc=el.desc,viewId=el.viewId,pkg=found.first.packageName)
  if(!exact.hasNode||!confirm(exact))return ActionOutcome(false,"Acțiune neautorizată",false)
  val before=LocalScreen.observe()?:return ActionOutcome(false,"Ecran indisponibil",false)
  if(before.packageName!=exact.pkg||!LocalScreen.clickConfirmed(exact))return ActionOutcome(false,"Țintă schimbată, ambiguă sau inaccesibilă",false)
  val changed=verifyChanged(before)
  return ActionOutcome(true,if(changed)"Apăsare acceptată; ecran modificat" else "Apăsare acceptată; efect neverificat",changed)
 }

 data class ReplayOutcome(val completed:Int,val total:Int,val reason:String)
 suspend fun replay(steps:List<ElementTarget>,confirm:suspend (ElementTarget)->Boolean):ReplayOutcome{
  var completed=0
  for(target in steps){
   kotlinx.coroutines.currentCoroutineContext().ensureActive()
   val outcome=click(target,confirm)
   if(!outcome.ok||!outcome.verified)return ReplayOutcome(completed,steps.size,outcome.message)
   completed++
  }
  return ReplayOutcome(completed,steps.size,"Toate apăsările au produs schimbări observabile; obiectivul final necesită verificare separată.")
 }
}
