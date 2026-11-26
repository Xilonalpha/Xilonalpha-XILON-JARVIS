# XILON JARVIS — duplex voice validation

## What is implemented
- The Sherpa streaming transducer can capture and decode audio while TTS is playing.
- Android VOICE_COMMUNICATION microphone source requests a communication audio path.
- Platform AcousticEchoCanceler and NoiseSuppressor are enabled when available.
- Transcript echo filtering rejects phrases matching the recent synthesized answer.
- Speech interruption and ASR capture use exclusive leases and stale-session rejection.
- If the streaming model is missing, the existing VAD -> Android ASR interruption path remains a fallback.

## Requirements
Install a compatible Sherpa-ONNX streaming transducer in the app's private models/streaming-asr directory:
encoder*.onnx, decoder*.onnx, joiner*.onnx, tokens.txt.
Whisper and SenseVoice offline models are not streaming transducers.
The model is NOT bundled in this repository.

## Manual device test
1. Install the debug APK produced by the current commit, grant microphone permission.
2. Install a supported streaming transducer locally and select an offline Romanian TTS voice.
3. Enable continuous conversation and interruption.
4. Ask JARVIS a long question, then speak a distinct new command during playback.
5. Verify the TTS stops, the new command is accepted once, and playback echo is not interpreted as a user command.
6. Repeat with headphones, loudspeaker, noise, a missing streaming model and a device without AEC.
7. Check that leaving the conversation closes the recorder and no microphone session remains stuck.

## Limitations
This is concurrent streaming ASR + TTS, not validated device-independent echo cancellation.
Android AEC effectiveness depends on audio routing and device support. No software
reference-signal AEC is implemented. A missing streaming model disables concurrent ASR.
No physical-device test is claimed by this document.
