package com.xilon.jarvis
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
object ModelMetadata {
 data class Reference(val sha256:String,val bytes:Long,val revision:String,val algorithm:String="SHA-256")
 fun kokoro():Reference = releaseAsset(ModelCatalog.kokoro)
 fun releaseAsset(entry:ModelCatalog.Entry):Reference {
  require(entry==ModelCatalog.kokoro || entry==ModelCatalog.mihai)
  val connection=URL("https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/tags/tts-models").openConnection() as HttpsURLConnection
  connection.connectTimeout=15000;connection.readTimeout=15000;connection.instanceFollowRedirects=false
  connection.setRequestProperty("Accept","application/vnd.github+json")
  try{
   check(connection.responseCode==200){"Manifest GitHub indisponibil"}
   val body=connection.inputStream.use{src->val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192);while(true){val n=src.read(buf);if(n<0)break;require(out.size()+n<=8388608);out.write(buf,0,n)};out.toString("UTF-8")}
   val assets=JSONObject(body).getJSONArray("assets")
   for(i in 0 until assets.length()){
    val asset=assets.getJSONObject(i)
    if(asset.optString("name")!=entry.file)continue
    require(asset.getString("browser_download_url")==entry.url)
    val digest=asset.optString("digest")
    require(digest.matches(Regex("sha256:[0-9a-fA-F]{64}"))){"GitHub nu publică SHA-256 pentru arhivă"}
    val size=asset.getLong("size");require(size>0)
    return Reference(digest.removePrefix("sha256:"),size,"asset-"+asset.getLong("id"))
   }
   error("Arhiva vocală absentă din release")
  }finally{connection.disconnect()}
 }
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
    val lfs=file.optJSONObject("lfs")
    if(lfs==null){val hash=file.getString("blobId");val size=file.getLong("size");require(hash.matches(Regex("[0-9a-f]{40}"))&&size>0);return Reference(hash,size,revision,"git-blob-sha1")}
    val hash=lfs.getString("sha256")
    val size=lfs.getLong("size")
    require(hash.matches(Regex("[0-9a-fA-F]{64}"))&&size>0)
    return Reference(hash,size,revision)
   }
   error("Model absent din manifest")
  }finally{connection.disconnect()}
 }
}
