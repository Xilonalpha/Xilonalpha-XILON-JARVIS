package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class VoiceRetryPolicyTest {
 @Test fun boundedBackoffAndReset(){val p=VoiceRetryPolicy();p.failed(100);assertFalse(p.ready(599));assertTrue(p.ready(600));p.failed(600);assertFalse(p.ready(1599));assertTrue(p.ready(1600));repeat(10){p.failed(2000)};assertTrue(p.ready(10000));p.reset();assertTrue(p.ready(0))}
 @Test fun speechBudget(){assertEquals(1,ResourceBudget.speechThreads(8,512,false,0));assertEquals(2,ResourceBudget.speechThreads(8,4096,false,2));assertEquals(4,ResourceBudget.speechThreads(8,4096,false,0))}
}
