package com.xilon.jarvis
import org.junit.Assert.*
import org.junit.Test
class VoiceEchoGateRegressionTest {
 @Test fun detectsAssistantEchoWithoutBlockingUserStop(){
  val gate=VoiceEchoGate()
  gate.remember("Salut! Pot să te ajut cu o întrebare despre telefon.")
  assertTrue(gate.isEcho("pot sa te ajut cu o intrebare"))
  assertFalse(gate.isEcho("Jarvis oprește"))
  assertTrue(gate.isStop("Jarvis oprește"))
 }
 @Test fun toleratesOneSubstitutionButNotNegation(){
  val gate=VoiceEchoGate()
  gate.remember("Astăzi putem discuta despre vremea frumoasă din oraș")
  assertTrue(gate.isEcho("astazi putem discuta despre vremea buna din oras"))
  assertFalse(gate.isEcho("astazi putem discuta despre vremea nu din oras"))
  assertFalse(gate.isEcho("astazi putem discuta"))
 }
 @Test fun retainsTailOfLongSpokenReply(){
  val gate=VoiceEchoGate()
  gate.remember(("Acesta este un exemplu de răspuns mai lung. ").repeat(12)+"Acum putem verifica setările telefonului împreună.")
  assertTrue(gate.isEcho("acum putem verifica setarile telefonului impreuna"))
 }
}
