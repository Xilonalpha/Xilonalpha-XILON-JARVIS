package com.xilon.jarvis

import org.junit.Assert.*
import org.junit.Test

class WhatsAppHeaderTest {
    @Test fun namedHeaderRequiresIndependentInternationalNumber() {
        val phone = "40700123456"
        assertTrue(WhatsAppHeader.matchesNumber(phone, "Contact salvat", "+40 700 123 456"))
        assertTrue(WhatsAppHeader.matchesNumber(phone, "0040 700 123 456", null))
        assertFalse(WhatsAppHeader.matchesNumber(phone, "Contact salvat", null))
        assertFalse(WhatsAppHeader.matchesNumber(phone, "+40 700 123 456", "+40 700 123 457"))
        assertFalse(WhatsAppHeader.matchesNumber(phone, "Grup +40 700 123 456", "Grup"))
    }
}
