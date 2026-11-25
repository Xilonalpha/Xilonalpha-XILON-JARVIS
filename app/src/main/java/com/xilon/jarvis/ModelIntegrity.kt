package com.xilon.jarvis
import java.io.InputStream
import java.security.MessageDigest
object ModelIntegrity {
 fun verify(input:InputStream,expectedSha256:String,expectedBytes:Long,algorithm:String="SHA-256"){
  require(algorithm in setOf("SHA-256","git-blob-sha1"))
  require(expectedSha256.matches(Regex(if(algorithm=="SHA-256")"[0-9a-fA-F]{64}" else "[0-9a-fA-F]{40}")))
  require(expectedBytes>0)
  val digest=MessageDigest.getInstance(if(algorithm=="SHA-256")"SHA-256" else "SHA-1")
  if(algorithm=="git-blob-sha1")digest.update(("blob "+expectedBytes+"\u0000").toByteArray(Charsets.UTF_8))
  var total=0L
  input.use{src->
   val buffer=ByteArray(65536)
   while(true){val n=src.read(buffer);if(n<0)break;total+=n;require(total<=expectedBytes){"Fișier prea mare"};digest.update(buffer,0,n)}
  }
  require(total==expectedBytes){"Dimensiune incorectă"}
  val actual=digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
  require(actual.equals(expectedSha256,ignoreCase=true)){"Digest diferit"}
 }
}
