package com.xilon.jarvis

internal object WhatsAppHeader {
    private val international = Regex("""(?:\+|00)[0-9(). -]+""")

    fun matchesNumber(phone: String, title: CharSequence?, description: CharSequence?): Boolean {
        val numbers = listOfNotNull(title, description).map { it.toString().trim() }
            .filter(international::matches)
            .map { it.removePrefix("00").filter(Char::isDigit) }
        return numbers.isNotEmpty() && numbers.all { it == phone }
    }
}
