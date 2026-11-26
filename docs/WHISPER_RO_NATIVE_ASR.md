# Romanian Whisper native ASR

Android speech recognition is implemented by SpeechToTextEngine using the Sherpa-ONNX
OfflineRecognizer and OfflineWhisperModelConfig(language = "ro", task = "transcribe").
Install the three files from ModelCatalog.whisper into files/models/:
- tiny-encoder.int8.onnx
- tiny-decoder.int8.onnx
- tiny-tokens.txt

These files are from the Sherpa-compatible multilingual Whisper Tiny export.
When a compatible Whisper encoder/decoder/tokens triple is present, the engine
now prioritizes it over the English-only streaming Zipformer model. Recording,
offline inference, and returned text use the existing microphone pipeline.

The separately listed Gntz/whisper-ro-onnx encoder_model_quantized.onnx and
decoder_model_merged_quantized.onnx are Transformers.js artifacts. They are NOT
loaded by this Sherpa adapter: they need a dedicated Transformers.js-compatible
decoder, tokenizer, and mel preprocessing implementation. Downloading them alone
does not enable native ASR. This change deliberately avoids claiming otherwise.

This is offline Romanian Whisper transcription, not a Romanian streaming Whisper
decoder or a proof of full-duplex. Test recognition and latency on a physical phone.
