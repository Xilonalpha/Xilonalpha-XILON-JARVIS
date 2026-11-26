# Offline Romanian speech recognition

“Instalează Whisper română offline” installs multilingual Whisper Tiny for Sherpa. This is the lighter option. The engine sets the language to Romanian and transcribes audio captured at 16 kHz.

The Romanian model catalog also offers Gntz/whisper-ro-onnx, a fine-tuned Whisper Small model at revision 25ccec388c51ae98b5fd928f9ee798b1b6d313be. Install its quantized encoder, merged decoder, vocabulary and generation configuration. Installing the two older ONNX downloads alone leaves the package incomplete. The Kotlin adapter computes the model's 80-channel log-Mel features, uses ONNX Runtime and decodes Romanian tokens with cached attention state. Recognition runs locally after installation.

When both models are installed, Tiny remains the default. Enable “Voce: preferă Whisper Small RO când este instalat” to switch at the next utterance. The existing English streaming transducer remains available for English. Neither offline Whisper model emits streaming partial results. The end-to-end device test for microphone, echo and barge-in remains separate from offline decoding of recorded audio.
