package com.xilon.jarvis
import org.junit.Test
import org.junit.Assert.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
class GgufHeaderTest {
 private fun bytes(version:Int=3,tensors:Long=10,metadata:Long=4)=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).put(byteArrayOf(71,71,85,70)).putInt(version).putLong(tensors).putLong(metadata).array()
 @Test fun validHeader(){assertEquals(GgufHeader.Header(3,10,4),GgufHeader.inspect(bytes().inputStream()))}
 @Test(expected=IllegalArgumentException::class) fun invalidVersion(){GgufHeader.inspect(bytes(99).inputStream())}
 @Test(expected=IllegalArgumentException::class) fun invalidCount(){GgufHeader.inspect(bytes(tensors=-1).inputStream())}
 @Test(expected=java.io.EOFException::class) fun truncated(){GgufHeader.inspect(bytes().copyOf(12).inputStream())}
}
