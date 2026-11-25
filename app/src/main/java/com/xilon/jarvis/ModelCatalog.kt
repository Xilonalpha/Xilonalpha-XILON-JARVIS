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
}
