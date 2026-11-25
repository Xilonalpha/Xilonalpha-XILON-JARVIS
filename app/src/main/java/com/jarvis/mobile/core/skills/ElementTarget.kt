package com.jarvis.mobile.core.skills

data class ElementTarget(
    val mode: String = "NODE",
    val text: String? = null,
    val desc: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val fx: Float? = null,   // 0..1 fraction of screen WIDTH
    val fy: Float? = null,   // 0..1 fraction of screen HEIGHT
    val pxX: Int? = null,    // legacy absolute pixels (pre-2.3 skills)
    val pxY: Int? = null,
    val pkg: String? = null, // the app this target lives in (run-time guard)
) {
    val hasNode: Boolean get() = !viewId.isNullOrBlank() || !text.isNullOrBlank() || !desc.isNullOrBlank()
    val hasPoint: Boolean get() = (fx != null && fy != null) || (pxX != null && pxY != null)

    fun describe(): String = when {
        !text.isNullOrBlank() -> "\"${text.take(28)}\""
        !desc.isNullOrBlank() -> "\"${desc.take(28)}\""
        !viewId.isNullOrBlank() -> viewId!!.substringAfterLast('/')
        fx != null && fy != null -> "(point ${"%.2f".format(fx)},${"%.2f".format(fy)})"
        pxX != null && pxY != null -> "(point $pxX,$pxY)"
        else -> "target"
    }

    /** Resolve to absolute pixels for the CURRENT screen metrics. */
    fun pointPx(screenW: Int, screenH: Int): Pair<Int, Int>? = when {
        fx != null && fy != null ->
            ((fx.coerceIn(0f, 1f) * screenW).toInt() to (fy.coerceIn(0f, 1f) * screenH).toInt())
        pxX != null && pxY != null -> (pxX to pxY)
        else -> null
    }
}

