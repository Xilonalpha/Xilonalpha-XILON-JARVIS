package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class SceneTrackerTest {
 @Test fun tracksMotion(){val t=SceneTracker();val a=t.update(listOf(SceneTracker.Detection("cup",0.2f,0.3f)),100).single();val b=t.update(listOf(SceneTracker.Detection("cup",0.3f,0.3f)),200).single();assertEquals(a.id,b.id);assertEquals("spre dreapta",b.motion)}
 @Test fun expires(){val t=SceneTracker();val d=listOf(SceneTracker.Detection("cup",0.2f,0.3f));assertNotEquals(t.update(d,100).single().id,t.update(d,20000).single().id)}
}
