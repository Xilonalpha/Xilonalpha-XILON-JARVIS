# XILON JARVIS — reutilizarea tehnologiilor vocale

## Componente deja în repository
- SherpaSpeech / sherpa-onnx: adaptorul existent pentru recunoaștere vocală offline.
- KokoroVoice / NeuralVoice: integrarea TTS neurală existentă, disponibilitatea limbii române trebuie validată pe dispozitiv.
- OfflineVoice: fallback Android TTS pentru vocile românești instalate.
- VoiceEchoGate, VoiceInterruptGate, VoiceTurnGuard: filtrele deja implementate pentru ecou, întreruperi și duplicate.
- SpeechChunks: fragmentarea textului deja complet, pentru TTS non-streaming.

## Referințe open source de reutilizat, fără copiere necontrolată
- https://github.com/k2-fsa/sherpa-onnx — ASR/VAD/TTS offline, Android; Apache-2.0.
- https://github.com/Wolfpkhan/voice_assistant — exemplu Android cu VAD, streaming ASR, TTS și orchestrare; folosește cloud LLM în configurația documentată, deci NU importăm acea dependență.
- https://github.com/techrifter/fluxvoice — model de orchestrare a întreruperilor și a frazelor; verifică licența înainte de orice copiere.
- https://github.com/m15-ai/droidkaigi2026-app-gvp — referință Android pentru AEC, măsurarea latenței și barge-in; verifică licența.

## Integrare etapizată
1. Reutilizează pipeline-ul audio existent; evită al doilea recorder sau a doua instanță STT.
2. Conectează deltele locale llama.cpp la RealtimeSpeechBuffer, apoi la un singur TTS player serializat.
3. La barge-in: anulează generarea, golește coada de fragmente, oprește TTS, apoi reia ascultarea. Nu executa instrumente din transcrieri parțiale.
4. Folosește Android AcousticEchoCanceler / NoiseSuppressor doar când sunt suportate de dispozitiv; păstrează VoiceEchoGate ca protecție logică.
5. Măsoară pe telefon latența ASR final, primul token LLM, primul sunet TTS, oprirea la întrerupere și rata de ecouri false.

## Limitări actuale
RealtimeSpeechBuffer este doar un modul testabil pentru text incremental; încă NU este conectat la llama.cpp sau TTS. Full-duplex și vocea expresivă nu sunt validate pe dispozitiv. Nu promitem paritate cu modul vocal ChatGPT.
