# Romanian Piper voices

Install the Mihai package from OMEGA Model Center for Romanian VITS weights, tokens and eSpeak data. To use Liana, install its ONNX and JSON entries from the Romanian catalog and select Liana under “Alege vocea română Piper”. Choosing “Automat” prefers the complete Mihai package. Liana can also use Romanian eSpeak data from Kokoro or models/romanian/espeak-ng-data.

On first synthesis the speech worker verifies the two pinned Liana sources and builds an app-private Sherpa-compatible copy and token table. Allow about 64 MB for that extra copy. Later utterances reuse it. The original download remains intact. The packaged 129-byte protobuf metadata resource adds the VITS model type, Piper comment, Romanian language, ro voice, eSpeak availability, one speaker and 22050 Hz sample rate to the ONNX graph. The phoneme IDs come from the downloaded voice configuration.

Sherpa-ONNX 1.12.38 produced audio on a Linux CPU from the official Mihai archive and the converted Liana model. Android playback, voice switching, interruptions and microphone echo still require device tests.
