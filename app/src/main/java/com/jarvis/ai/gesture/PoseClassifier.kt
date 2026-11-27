/* MIT upstream Serg1o2/GestureControl 7db01a590d2eedd225b9305b7eadf536bca1b054; vendor/gesturecontrol/LICENSE */
package com.jarvis.ai.gesture


data class PoseResult(val pose: HandPose, val confidence: Float)

/** Classifies the static hand pose of one frame. Implementations may keep state (smoothing). */
interface PoseClassifier {
    /** [aspect] = width / height of the frame the landmarks are normalized to. */
    fun classify(landmarks: List<Landmark>, aspect: Float): PoseResult

    /** Forget any temporal state (hand lost). */
    fun reset() = Unit
}

/** The hand-written geometric rules of stages 1-2. */
class RuleBasedPoseClassifier(config: PalmConfig = PalmConfig()) : PoseClassifier {
    private val rules = StaticGestureClassifier(config)
    override fun classify(landmarks: List<Landmark>, aspect: Float) = PoseResult(rules.classify(landmarks), 1f)
}

