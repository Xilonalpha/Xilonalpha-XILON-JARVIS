package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class GestureBindingsTest {
 @Test fun invalidValuesUseDefault(){assertEquals("Implicit",GestureBindings.resolve(null));assertEquals("Implicit",GestureBindings.resolve("unknown"))}
 @Test fun supportedActionsRoundTrip(){GestureBindings.actions.forEach{assertEquals(it,GestureBindings.resolve(it))}}
 @Test fun keysAreUnique(){assertEquals(GestureBindings.keys.size,GestureBindings.keys.toSet().size);assertEquals(GestureBindings.keys.size,GestureBindings.labels.size)}
}
