package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class RecentVisualObservationTest {
 @Test fun expiresAndClears(){
  val o=RecentVisualObservation()
  assertNull(o.get(0));o.put("obiect",10)
  assertEquals("obiect",o.get(120010));assertNull(o.get(120011))
  assertNull(o.get(9));o.clear();assertNull(o.get(10))
 }
 @Test fun bounded(){val o=RecentVisualObservation();o.put("a".repeat(5000),1);assertEquals(4000,o.get(1)?.length)}
}
