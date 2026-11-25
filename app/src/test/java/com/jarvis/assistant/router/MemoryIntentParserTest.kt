/*
Upstream: https://github.com/judejulius/jarvis/blob/5dd9934361317258bb1e35654fc5b33dcd861b7e/app/src/test/java/com/jarvis/assistant/router/MemoryIntentParserTest.kt
MIT License

Copyright (c) 2026 Mohammad Joud Julius

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package com.jarvis.assistant.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryIntentParserTest {
    private fun p(s: String) = MemoryIntentParser.parse(s)

    @Test fun teachesAppNames() {
        assertEquals(MemoryIntent.TeachApp("yt", "youtube"), p("when I say yt, open youtube"))
        assertEquals(MemoryIntent.TeachApp("you too", "youtube"), p("When I say you too, open the YouTube app."))
        assertEquals(MemoryIntent.TeachApp("gram", "instagram"), p("Jarvis, when I say gram, launch Instagram"))
        assertEquals(MemoryIntent.TeachApp("whatsap", "whatsapp"), p("whatsap means the WhatsApp app"))
        assertEquals(MemoryIntent.TeachApp("yutub", "youtube"), p("jab main yutub bolun to youtube kholo"))
        assertEquals(MemoryIntent.TeachApp("yutub", "youtube"), p("yutub ka matlab youtube app hai"))
    }

    @Test fun teachesHowTheUserSounds() {
        assertEquals(MemoryIntent.TeachPhrase("you too", "youtube"), p("when I say you too, I mean youtube"))
        assertEquals(MemoryIntent.TeachPhrase("what sap", "whatsapp"), p("When I say what sap I meant WhatsApp."))
        assertEquals(MemoryIntent.TeachPhrase("yutub", "youtube"), p("jab main yutub bolun to mera matlab youtube hota hai"))
    }

    @Test fun remembersFactsAndPreferences() {
        assertEquals(MemoryIntent.Remember("I prefer short answers", true), p("remember that I prefer short answers"))
        assertEquals(MemoryIntent.Remember("my wifi name is home", false), p("Remember my wifi name is home."))
        assertEquals(MemoryIntent.Remember("main chai pasand karta hoon", false), p("yaad rakho ki main chai pasand karta hoon"))
    }

    @Test fun rememberingATeachSentenceTeachesInstead() {
        assertEquals(MemoryIntent.TeachApp("yt", "youtube"), p("remember that when I say yt open youtube"))
    }

    @Test fun forgetsSpecificThingsOnly() {
        assertEquals(MemoryIntent.Forget("my wifi name"), p("forget my wifi name"))
        assertEquals(MemoryIntent.Forget("chai"), p("bhool jao chai"))
        assertNull(p("forget it"))
        assertNull(p("forget everything"))
    }

    @Test fun listsMemories() {
        assertEquals(MemoryIntent.ListAll, p("what do you remember"))
        assertEquals(MemoryIntent.ListAll, p("show my memories"))
    }

    @Test fun ordinaryTalkIsNotMemory() {
        assertNull(p("what is the weather"))
        assertNull(p("open youtube"))
        assertNull(p("remember"))
        assertNull(p("remember that"))
        assertNull(p(""))
    }
}
