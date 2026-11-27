package com.jarvis.ai.gesture

/** Analyzer-thread session. Recording never executes actions. */
class CustomGestureSession {
    private val recognizer = CustomGestureRecognizer(emptyList())
    private val frames = ArrayList<FeatureFrame>()
    private var recording = false
    @Synchronized fun setTemplates(templates: List<CustomTemplate>) {
        require(templates.size <= 16 && templates.all { it.samples.size in 3..5 && it.radius.isFinite() && it.radius > 0f })
        recognizer.templates = templates.toList()
        recognizer.reset()
    }
    @Synchronized fun begin() { frames.clear(); recognizer.reset(); recording = true }
    @Synchronized fun cancel() { recording = false; frames.clear(); recognizer.reset() }
    @Synchronized fun finish(): GestureSample? {
        recording = false
        val sample = SampleBuilder.build(frames)
        frames.clear()
        recognizer.reset()
        return sample?.takeIf { it.durationMs in 300..3000 && it.steps.all { row -> row.all { v -> v.isFinite() } } }
    }
    @Synchronized fun isRecording() = recording
    @Synchronized fun process(frame: HandFrame): CustomMatch? {
        if (recording) {
            val feature = SampleBuilder.featureFrame(frame)
            if (feature != null && frames.size < 180 && (frames.isEmpty() || feature.t - frames.first().t <= 3000)) frames.add(feature)
            return null
        }
        if (recognizer.templates.isEmpty()) return null
        return recognizer.process(frame)
    }
}
