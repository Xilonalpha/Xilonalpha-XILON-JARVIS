/*
Upstream: https://github.com/tokenarc/open-jarvis/blob/04873998933830ce582018fe4664ebce5ec64d2f/app/src/main/java/com/openjarvis/intelligence/ClipboardIntelligence.kt
MIT License

Copyright (c) 2024 TokenArc

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
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
IN THE SOFTWARE.
*/
package com.openjarvis.intelligence

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class ClipboardIntelligence(private val context: Context) {
    
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    
    fun read(): String? {
        return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
    }
    
    fun write(text: String) {
        val clip = ClipData.newPlainText("jarvis", text)
        clipboard.setPrimaryClip(clip)
    }
    
    fun analyze(): ClipboardContent {
        val text = read() ?: return ClipboardContent.Empty
        
        return when {
            text.startsWith("http://", true) || text.startsWith("https://", true) -> 
                ClipboardContent.Url(text)
            text.contains("@") && text.contains(".") -> 
                ClipboardContent.Email(text)
            text.isNotBlank() && text.all { it.isDigit() } -> 
                ClipboardContent.PhoneNumber(text)
            text.length > 200 -> 
                ClipboardContent.LongText(text.take(200))
            else -> 
                ClipboardContent.ShortText(text)
        }
    }
    
    fun hasContent(): Boolean {
        return clipboard.hasPrimaryClip() && (clipboard.primaryClip?.itemCount ?: 0) > 0
    }
    
    sealed class ClipboardContent {
        object Empty : ClipboardContent()
        data class Url(val url: String) : ClipboardContent()
        data class Email(val address: String) : ClipboardContent()
        data class PhoneNumber(val number: String) : ClipboardContent()
        data class LongText(val preview: String) : ClipboardContent()
        data class ShortText(val text: String) : ClipboardContent()
    }
}