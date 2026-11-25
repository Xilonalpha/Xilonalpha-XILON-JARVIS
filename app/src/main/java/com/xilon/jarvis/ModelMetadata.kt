package com.xilon.jarvis
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
object ModelMetadata {
 data class Reference(val sha256:String,val bytes:Long,val revision:String)
 fun fetch(entry:ModelCatalog.Entry):Reference {
  require(entry.directUrl==null)
  val url=URL("https://huggingface.co/api/models/"+entry.repo+"/revision/"+entry.revision+"?blobs=true")
  val connection=url.openConnection() as HttpsURLConnection
  connection.connectTimeout=15000
  connection.readTimeout=15000
  connection.instanceFollowRedirects=false
  try {
   check(connection.responseCode==200){"Metadate indisponibile"}
   val body=connection.inputStream.use{src->
    val out=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(8192)
    while(true){val n=src.read(buffer);if(n<0)break;require(out.size()+n<=4194304);out.write(buffer,0,n)}
    out.toString("UTF-8")
   }
   val manifest=JSONObject(body)
   val revision=manifest.getString("sha")
   require(revision.matches(Regex("[0-9a-f]{40}")))
   val files=manifest.getJSONArray("siblings")
   for(i in 0 until files.length()){
    val file=files.getJSONObject(i)
    if(file.getString("rfilename")!=entry.file)continue
    val lfs=file.getJSONObject("lfs")
    val hash=lfs.getString("sha256")
    val size=lfs.getLong("size")
    require(hash.matches(Regex("[0-9a-fA-F]{64}"))&&size>0)
    return Reference(hash,size,revision)
   }
   error("Model absent din manifest")
  }finally{connection.disconnect()}
 }
}
