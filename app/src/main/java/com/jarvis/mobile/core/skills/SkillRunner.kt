package com.jarvis.mobile.core.skills

import com.xilon.jarvis.LocalScreen
import com.jarvis.mobile.core.observer.ScreenObservation
import kotlinx.coroutines.delay

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
                val el = ElementResolver.resolve(obs, target.copy(fx=null,fy=null,pxX=null,pxY=null))
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
}
