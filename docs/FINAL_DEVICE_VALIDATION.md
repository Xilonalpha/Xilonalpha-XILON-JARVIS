# Device acceptance gates

Run on a real Android phone with airplane mode enabled after model installation. Record device, APK commit, RAM, thermal state and actual observations. Do not mark untested cases passed.

- Romanian Whisper: cold and warm recognition, long pauses, noisy room, denied microphone permission, background cancellation.
- Kokoro English and Android Romanian: playback completion, stop, fallback, no overlapping playback.
- Continuous voice: ten turns, interruption, no self-trigger, no busy retry loop.
- Agent: invalid initial plan recovery, confirmation denied, timeout and unknown effect never retried.
- Camera gestures and VLM: permission denial, camera release, memory pressure.
- CPU and Vulkan APK: install, local model inference, compare actual latency and memory, CPU fallback.
- Verify CI unit tests and APK artifacts before release. Physical device results are pending.
