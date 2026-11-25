/*
Upstream: https://github.com/judejulius/jarvis/blob/5dd9934361317258bb1e35654fc5b33dcd861b7e/app/src/main/java/com/jarvis/assistant/ai/AiMode.kt
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
package com.jarvis.assistant.ai

/** How Jarvis chooses between the on-phone model and cloud providers. Simple phone actions never depend on this. */
enum class AiMode {
    /** Decide per request: local Qwen for short simple chat, cloud for hard, current-info or Hindi/Hinglish requests. */
    AUTO,

    /** Prefer cloud providers when the internet works; the local model is the fallback. */
    ONLINE,

    /** Only models running on the phone or the local network. Nothing leaves the device. */
    OFFLINE;

    companion object {
        fun parse(value: String?): AiMode = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AUTO
    }
}

enum class TaskKind { CHAT, AGENT }
