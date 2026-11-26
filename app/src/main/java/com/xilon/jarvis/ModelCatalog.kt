package com.xilon.jarvis
object ModelCatalog {
 data class Entry(val name:String,val role:String,val repo:String,val file:String,val mb:Long,val revision:String="main",val directUrl:String?=null){
  val url:String get()=directUrl ?: "https://huggingface.co/$repo/resolve/$revision/$file?download=true"
 }
 val entries=listOf(
 Entry("SmolLM2 360M","Conversație ușoară","bartowski/SmolLM2-360M-Instruct-GGUF","SmolLM2-360M-Instruct-Q4_K_M.gguf",271),
 Entry("Qwen 2.5 1.5B","Asistent și agenți","bartowski/Qwen2.5-1.5B-Instruct-GGUF","Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",986),
 Entry("Qwen 2.5 Coder 1.5B","Programare","bartowski/Qwen2.5-Coder-1.5B-Instruct-GGUF","Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf",986),
 Entry("Qwen3 0.6B Q4","Conversație multilingvă și comenzi rapide; model compact","tensorblock/Qwen_Qwen3-0.6B-GGUF","Qwen3-0.6B-Q4_K_M.gguf",484),
 Entry("Qwen3 1.7B Q4","Raționament și planificare; necesită RAM disponibilă suplimentară","tensorblock/Qwen_Qwen3-1.7B-GGUF","Qwen3-1.7B-Q4_K_M.gguf",1282)
 )
 val ocr=listOf("ron","eng").map{lang->Entry("OCR "+lang,"Tesseract","lm-kit/tessdata-fast","$lang.traineddata",5,"83ff76b97461395f257255720bf7f3957606dbb5")}
 val whisper=listOf("tiny-encoder.int8.onnx" to 13L,"tiny-decoder.int8.onnx" to 90L,"tiny-tokens.txt" to 1L).map{(file,mb)->Entry(file,"Whisper Tiny","csukuangfj/sherpa-onnx-whisper-tiny",file,mb,"65176e2")}
 /** Streaming Zipformer EN INT8; model is English-only, not Romanian. */
 val streamingAsr=listOf(
  Entry("Zipformer EN encoder INT8","Sherpa streaming EN","csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26","encoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx",72),
  Entry("Zipformer EN decoder INT8","Sherpa streaming EN","csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26","decoder-epoch-99-avg-1-chunk-16-left-128.int8.onnx",2),
  Entry("Zipformer EN joiner INT8","Sherpa streaming EN","csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26","joiner-epoch-99-avg-1-chunk-16-left-128.int8.onnx",1),
  Entry("Zipformer EN tokens","Sherpa streaming EN","csukuangfj/sherpa-onnx-streaming-zipformer-en-2023-06-26","tokens.txt",1)
 )
 fun streamingTarget(entry:Entry):String=when(entry){
  streamingAsr[0]->"encoder.int8.onnx"
  streamingAsr[1]->"decoder.int8.onnx"
  streamingAsr[2]->"joiner.int8.onnx"
  streamingAsr[3]->"tokens.txt"
  else->error("Componentă streaming necunoscută")
 }
 val wake=listOf("hey_jarvis.onnx","melspectrogram.onnx","embedding_model.onnx").map{Entry(it,"Hey Jarvis experimental","Soulcreek2/speechkit-wakeword-models",it,2,"0795387053f80d32bcebbd3bf7f7009ef0e32db5")}
 val pose=Entry("Pose Landmarker Lite","33 repere corporale","litert-community/MediaPipe-PoseLandmarker-Lite","pose_landmarker_lite.task",6,"105f65f")
 val vision=listOf(
 Entry("EfficientDet Lite0 INT8","Detectare obiecte","litert-community/MediaPipe-ObjectDetector-EfficientDet-Lite0","efficientdet_lite0_int8.tflite",5),
 Entry("BlazeFace FP16","Detectare fețe","litert-community/MediaPipe-FaceDetector-BlazeFace-Short-Range","blaze_face_short_range.tflite",1),
 pose
 )
 val hand=Entry("Hand Landmarker","MediaPipe FP16","Leo-TX/mediapipe-hand","hand_landmarker.task",8,"6ed55322affd263688b9dfffda68a10545f15b95")
 // Include the same verified hand model in Vision and Air Touch catalogs.
 val visionModels:List<Entry> get()=vision+hand
 val silero=Entry("Silero VAD v6.2","Detectare vocală neuronală offline","snakers4/silero-vad","silero_vad.onnx",3,"v6.2",directUrl="https://raw.githubusercontent.com/snakers4/silero-vad/v6.2/src/silero_vad/data/silero_vad.onnx")
 val kokoro=Entry("Kokoro INT8","Voce engleză","k2-fsa/sherpa-onnx","kokoro-int8-en-v0_19.tar.bz2",256,directUrl=KokoroArchive.URL)
}
