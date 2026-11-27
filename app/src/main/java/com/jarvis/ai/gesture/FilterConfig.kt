/* MIT import from https://github.com/Serg1o2/GestureControl/tree/7db01a590d2eedd225b9305b7eadf536bca1b054; vendor/gesturecontrol/LICENSE */
package com.jarvis.ai.gesture

data class FilterConfig(
    val enabled: Boolean = true,
    /** Cutoff at zero speed, Hz. Lower = less jitter, more lag. */
    val minCutoff: Float = 1.5f,
    /** Speed coefficient. Higher = less lag during fast motion. */
    val beta: Float = 0.5f,
    /** Cutoff for the derivative, Hz. */
    val dCutoff: Float = 1.0f,
)

