package com.xilon.jarvis

import java.io.File
import java.io.DataInputStream
import java.io.InputStream

internal object GgufHeader {
 data class Header(val version:Int,val tensors:Long,val metadata:Long)
 fun inspect(file:File):Header = file.inputStream().buffered().use { inspect(it) }
 fun inspect(input:InputStream):Header {
  val d=DataInputStream(input)
  require(d.readInt()==0x47475546){"Antet GGUF invalid"}
  val version=Integer.reverseBytes(d.readInt())
  require(version in 2..3){"Versiune GGUF nesuportată: "+version}
  val tensors=java.lang.Long.reverseBytes(d.readLong())
  val metadata=java.lang.Long.reverseBytes(d.readLong())
  require(tensors in 1..1000000L && metadata in 0..1000000L){"Contoare GGUF invalide"}
  return Header(version,tensors,metadata)
 }
}
