package com.jarvis.ai.gesture
import org.junit.Test
import org.junit.Assert.*
class CustomGestureSessionTest {
 @Test fun emptyRecordingRejected(){val s=CustomGestureSession();s.begin();assertTrue(s.isRecording());assertNull(s.finish());assertFalse(s.isRecording())}
 @Test fun cancelClearsRecording(){val s=CustomGestureSession();s.begin();s.cancel();assertFalse(s.isRecording());assertNull(s.finish())}
}
