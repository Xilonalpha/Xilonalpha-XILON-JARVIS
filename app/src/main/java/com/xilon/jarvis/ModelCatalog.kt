package com.xilon.jarvis
object ModelCatalog {
 data class Entry(val name:String,val role:String,val repo:String,val file:String,val mb:Long,val revision:String="main"){
  val url:String get()="https://huggingface.co/$repo/resolve/$revision/$file?download=true"
 }
 val entries=listOf(
 Entry("SmolLM2 360M","Conversație ușoară","bartowski/SmolLM2-360M-Instruct-GGUF","SmolLM2-360M-Instruct-Q4_K_M.gguf",271),
 Entry("Qwen 2.5 1.5B","Asistent și agenți","bartowski/Qwen2.5-1.5B-Instruct-GGUF","Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",986),
 Entry("Qwen 2.5 Coder 1.5B","Programare","bartowski/Qwen2.5-Coder-1.5B-Instruct-GGUF","Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf",986)
 )
 val ocr=listOf("ron","eng").map{lang->Entry("OCR "+lang,"Tesseract","lm-kit/tessdata-fast","$lang.traineddata",5,"83ff76b97461395f257255720bf7f3957606dbb5")}
 val whisper=listOf("tiny-encoder.int8.onnx" to 13L,"tiny-decoder.int8.onnx" to 90L,"tiny-tokens.txt" to 1L).map{(file,mb)->Entry(file,"Whisper Tiny","csukuangfj/sherpa-onnx-whisper-tiny",file,mb,"65176e2")}
 val wake=listOf("hey_jarvis.onnx","melspectrogram.onnx","embedding_model.onnx").map{Entry(it,"Hey Jarvis experimental","Soulcreek2/speechkit-wakeword-models",it,2,"0795387053f80d32bcebbd3bf7f7009ef0e32db5")}
}
