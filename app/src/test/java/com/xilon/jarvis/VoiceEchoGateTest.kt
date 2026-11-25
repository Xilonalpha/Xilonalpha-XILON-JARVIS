package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
class VoiceEchoGateTest {
 @Test fun explicitStopOnly(){val g=VoiceEchoGate();g.remember("Salut, cum te pot ajuta?");assertTrue(g.isStop("Jarvis, oprește!"));assertFalse(g.isStop("deschide aplicația"));assertFalse(g.isStop("salut"))}
 @Test fun rejectsOwnSpeech(){val g=VoiceEchoGate();g.remember("Spune Jarvis oprește pentru a termina.");assertFalse(g.isStop("Jarvis oprește"))}
 @Test fun expandedStopCommands(){val g=VoiceEchoGate();assertTrue(g.isStop("Jarvis, liniște!"));assertTrue(g.isStop("Jarvis, întrerupe"));assertFalse(g.isStop("Jarvis nu opri"));g.remember("Jarvis pauză");assertFalse(g.isStop("Jarvis pauză"))}
}
