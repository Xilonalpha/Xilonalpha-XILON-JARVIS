package com.xilon.jarvis

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SpeechLeadInTest {
    @Test fun partialReadsAndWrapPreserveRecentSpeechInOrder() {
        val lead = SpeechLeadIn(4)
        lead.append(shortArrayOf(1, 2, 99), 2)
        assertArrayEquals(shortArrayOf(1, 2), lead.snapshot())
        lead.append(shortArrayOf(3, 4, 5), 3)
        assertArrayEquals(shortArrayOf(2, 3, 4, 5), lead.snapshot())
    }
}
