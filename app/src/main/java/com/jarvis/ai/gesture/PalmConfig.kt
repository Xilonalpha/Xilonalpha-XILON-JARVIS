/* MIT import from https://github.com/Serg1o2/GestureControl/tree/7db01a590d2eedd225b9305b7eadf536bca1b054; vendor/gesturecontrol/LICENSE */
package com.jarvis.ai.gesture

data class PalmConfig(
    /** How long the open palm must be held still. */
    val holdMs: Long = 500,
    /** Max speed of the palm center while holding. */
    val maxVelocity: Float = 0.25f,
    /** Window over which the palm speed is measured. */
    val velocityWindowMs: Long = 150,
    /** Short misclassifications within the hold are tolerated up to this duration. */
    val dropoutToleranceMs: Long = 120,
    /** A finger is extended when dist(wrist, tip) > dist(wrist, pip) * ratio. */
    val fingerExtensionRatio: Float = 1.1f,
    /** The thumb is extended when dist(tip, pinkyMcp) > dist(ip, pinkyMcp) * ratio. */
    val thumbExtensionRatio: Float = 1.0f,
    /** Whether the thumb must be extended for an open palm. */
    val requireThumb: Boolean = true,
)
