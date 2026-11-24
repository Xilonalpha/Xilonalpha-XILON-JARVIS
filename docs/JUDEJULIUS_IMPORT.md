# judejulius/jarvis import

Source: https://github.com/judejulius/jarvis/tree/5dd9934

MIT license preserved at app/src/main/assets/licenses/judejulius-jarvis.txt.

Original unchanged Kotlin files under app/src/main/java/com/jarvis/assistant/voice: SpeechChunks.kt (c0f3137507d7169274c51cd932d7757d92ff1e41), SpeechPacer.kt (62db3f64ee514640ff71e84174c3206bbce644e3), Cpu.kt (56db54cc58aaaf02ae9903946cbce7344e0a3a21), MicGain.kt (11bb603ee4b7ed38a6255013a04c0357a8ca5b31).

SpeechChunks is called by OfflineVoice. The other three are not wired to runtime; audio timing and raw PCM inputs are still needed. No cloud provider imported. Build and device tests pending.
