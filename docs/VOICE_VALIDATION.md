# Local voice validation

Run Android and Sherpa checks on the same commit before distributing an APK.
On a device with Romanian Whisper and Piper installed:

- Test continuous mode with GGUF disabled for supported device commands.
- Test a long response and interrupt with an unrelated Romanian request.
- Repeat in a quiet room, with speaker audio, and with headphones.
- Verify silence does not trigger a command, and that negated requests are not treated as approximate echo.
- Switch Mihai/Liana/Kokoro and verify cancellation and subsequent playback.
- Check logcat tags XilonASR, XilonVAD, XilonPiper for effect activation, PCM latency, underruns and errors.

AEC availability or enablement is not proof of acoustic cancellation. Whisper batch capture is not incremental decoding. Device latency and quality are not yet certified.
