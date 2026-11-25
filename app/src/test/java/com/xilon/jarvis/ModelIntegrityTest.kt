package com.xilon.jarvis
import org.junit.Test
class ModelIntegrityTest {
 private val hash="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
 @Test fun valid(){ModelIntegrity.verify("abc".byteInputStream(),hash,3)}
 @Test(expected=IllegalArgumentException::class) fun corrupt(){ModelIntegrity.verify("abd".byteInputStream(),hash,3)}
 @Test(expected=IllegalArgumentException::class) fun truncated(){ModelIntegrity.verify("ab".byteInputStream(),hash,3)}
 @Test(expected=IllegalArgumentException::class) fun oversized(){ModelIntegrity.verify("abcd".byteInputStream(),hash,3)}
 @Test(expected=IllegalArgumentException::class) fun invalidReference(){ModelIntegrity.verify("abc".byteInputStream(),"unknown",3)}
}
