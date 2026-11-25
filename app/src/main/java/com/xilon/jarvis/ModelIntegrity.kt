package com.xilon.jarvis
import java.io.InputStream
import java.security.MessageDigest
object ModelIntegrity {
 fun verify(input:InputStream,expectedSha256:String,expectedBytes:Long){
  require(expectedSha256.matches(Regex("[0-9a-fA-F]{64}")))
  require(expectedBytes>0)
  val digest=MessageDigest.getInstance("SHA-256")
  var total=0L
  input.use{src->
   val buffer=ByteArray(65536)
   while(true){val n=src.read(buffer);if(n<0)break;total+=n;require(total<=expectedBytes){"Fișier prea mare"};digest.update(buffer,0,n)}
  }
  require(total==expectedBytes){"Dimensiune incorectă"}
  val actual=digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
  require(actual.equals(expectedSha256,ignoreCase=true)){"SHA-256 diferit"}
 }
}
