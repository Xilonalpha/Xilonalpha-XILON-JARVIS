package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceTurnGuardTest {
 @Test fun suppressesDuplicateFinalsButNotNewTurns(){
  val guard=VoiceTurnGuard()
  assertTrue(guard.accept("Deschide camera",1000))
  assertFalse(guard.accept("deschide, CAMERA!",1100))
  assertTrue(guard.accept("deschide camera",3000))
  assertTrue(guard.accept("închide camera",3100))
 }
 @Test fun clockRollbackAcceptsNextTurn(){
  val guard=VoiceTurnGuard()
  assertTrue(guard.accept("Deschide camera",5000))
  assertTrue(guard.accept("Deschide camera",100))
 }
 @Test fun romanianDiacriticsAreDeduplicated(){
  val guard=VoiceTurnGuard()
  assertTrue(guard.accept("Închide aplicația",1000))
  assertFalse(guard.accept("inchide aplicatia",1200))
 }
 @Test fun emptyAndReset(){
  val guard=VoiceTurnGuard()
  assertFalse(guard.accept("  ",10))
  assertTrue(guard.accept("Salut",10))
  guard.reset()
  assertTrue(guard.accept("Salut",11))
 }
}
