# Piper Romanian integration status

PiperRomanianVoice is now wired into the existing NeuralVoice UI and playback path.
Sherpa-ONNX VITS generation and Android AudioTrack output are implemented, but the
Liana model must not be reported as ready unless its dependencies are installed.

Expected files:
- files/models/romanian/ro_RO-liana-medium.onnx
- files/models/romanian/ro_RO-liana-medium.onnx.json
- files/models/romanian/tokens.txt
- files/models/romanian/espeak-ng-data/ (or files/kokoro/espeak-ng-data/)

The existing model catalog currently downloads only ONNX and JSON. The required
Sherpa-compatible tokens.txt and eSpeak data are not yet offered as verified
downloads. The model's compatibility with Sherpa's VITS phoneme IDs has not been
verified on a physical Android device. Until that is addressed, the UI must refuse
activation and the Android offline TTS engine remains the fallback.

The Whisper RO Transformers.js ONNX export likewise still requires a compatible
decoder/tokenizer and is not yet wired into the native Sherpa ASR inference path.

Validation: run :app:testDebugUnitTest :app:assembleDebug and the Sherpa Android
workflow, then perform on-device inference with Romanian sentences and cancellation.
