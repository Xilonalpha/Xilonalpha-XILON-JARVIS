package com.xilon.jarvis
import android.content.Context
interface NeuralVoice {
 fun installed():Boolean
 fun speak(text:String)
 fun speakTracked(text:String,done:(String?)->Unit){speak(text);done(null)}
 fun prepare(text:String){}
 fun stop()
}
object NeuralVoiceFactory {
 fun romanian(context:Context):NeuralVoice?=try{
  Class.forName("com.xilon.jarvis.PiperRomanianVoice").getConstructor(Context::class.java).newInstance(context) as NeuralVoice
 }catch(e:Exception){null}catch(e:LinkageError){null}

 fun create(context:Context):NeuralVoice?=try{
  Class.forName("com.xilon.jarvis.KokoroVoice").getConstructor(Context::class.java).newInstance(context) as NeuralVoice
 }catch(e:Exception){null}catch(e:LinkageError){null}
}
