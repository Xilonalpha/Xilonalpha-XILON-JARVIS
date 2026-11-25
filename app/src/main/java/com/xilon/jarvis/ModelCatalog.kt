package com.xilon.jarvis
object ModelCatalog {
 data class Entry(val name:String,val role:String,val repo:String,val file:String,val mb:Long){
  val url:String get()="https://huggingface.co/$repo/resolve/main/$file?download=true"
 }
 val entries=listOf(
 Entry("SmolLM2 360M","Conversație ușoară","bartowski/SmolLM2-360M-Instruct-GGUF","SmolLM2-360M-Instruct-Q4_K_M.gguf",271),
 Entry("Qwen 2.5 1.5B","Asistent și agenți","bartowski/Qwen2.5-1.5B-Instruct-GGUF","Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",986),
 Entry("Qwen 2.5 Coder 1.5B","Programare","bartowski/Qwen2.5-Coder-1.5B-Instruct-GGUF","Qwen2.5-Coder-1.5B-Instruct-Q4_K_M.gguf",986)
 )
}
