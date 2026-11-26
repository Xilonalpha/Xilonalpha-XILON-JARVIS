package com.xilon.jarvis

import com.jarvis.mobile.core.observer.ScreenElement
import com.jarvis.mobile.core.observer.ScreenObservation
import org.junit.Assert.*
import org.junit.Test

class ScreenObservationFingerprintTest {
 private fun element(
  text: String? = null,
  left: Int = 10,
  selected: Boolean = false,
  editable: Boolean = false
 ) = ScreenElement(
  idx = 0, role = if (editable) "edittext" else "button",
  text = text, desc = null, viewId = null, className = null,
  left = left, top = 10, right = left + 50, bottom = 50,
  clickable = true, editable = editable, scrollable = false,
  selected = selected, isPassword = false
 )

 @Test fun detectsIconOnlyLayoutChanges() {
  val before = ScreenObservation("app.example", null, listOf(element()))
  val after = ScreenObservation("app.example", null, listOf(element(left = 80)))
  assertNotEquals(before.fingerprint(), after.fingerprint())
 }

 @Test fun detectsSelectionChanges() {
  val before = ScreenObservation("app.example", null, listOf(element()))
  val after = ScreenObservation("app.example", null, listOf(element(selected = true)))
  assertNotEquals(before.fingerprint(), after.fingerprint())
 }

 @Test fun doesNotExposeEditableValues() {
  val secret = "PRIVATE_INPUT_8675309"
  val screen = ScreenObservation("app.example", null, listOf(element(text = secret, editable = true)))
  assertFalse(screen.fingerprint().contains(secret))
  assertFalse(screen.toCompact().contains(secret))
 }

 @Test fun detectsCrossAppTransitions() {
  val before = ScreenObservation("app.one", null, listOf(element()))
  val after = ScreenObservation("app.two", null, listOf(element()))
  assertNotEquals(before.fingerprint(), after.fingerprint())
 }
}
