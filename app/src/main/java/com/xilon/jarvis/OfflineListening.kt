package com.xilon.jarvis
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.*

class OfflineListening(private val context: Context): AutoCloseable {
 var onSpeechStart:()->Unit={}
 var onLevel:(Float)->Unit={}
 var onListening:(Boolean)->Unit={}
 private var engine: SpeechRecognizer?=null
 fun listen(result:(String)->Unit,error:(String)->Unit){
  close()
  if(Build.VERSION.SDK_INT<31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)){
   error("Recunoașterea offline nu este disponibilă pe acest dispozitiv."); return
  }
  try {
   val recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
   engine=recognizer
   recognizer.setRecognitionListener(object:RecognitionListener{
    override fun onReadyForSpeech(params:Bundle?){}
    override fun onBeginningOfSpeech(){if(engine===recognizer)onSpeechStart()}
    override fun onRmsChanged(rmsdB:Float){if(engine===recognizer && rmsdB.isFinite())onLevel(((rmsdB+2f)/12f).coerceIn(0f,1f))}
    override fun onBufferReceived(buffer:ByteArray?){}
    override fun onEndOfSpeech(){}
    override fun onPartialResults(partialResults:Bundle?){}
    override fun onEvent(eventType:Int,params:Bundle?){}
    override fun onError(code:Int){if(engine===recognizer){close();error(when(code){12->"Româna nu este acceptată de serviciul vocal offline Android. Instalează și activează Whisper/Sherpa din OMEGA Model Center.";13->"Modelul vocal offline pentru română nu este disponibil. Instalează modelul local Whisper/Sherpa.";else->"Recunoaștere offline: eroare "+code})}}
    override fun onResults(results:Bundle?){
     if(engine!==recognizer)return
     val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
     close()
     if(text.isNullOrBlank())error("Nu am detectat un mesaj.") else result(text)
    }
   })
   onListening(true)
   recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ro-RO")
    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
   })
  }catch(e:Exception){close();error("Microfon: "+e.message)}
 }
 override fun close(){val previous=engine;engine=null;previous?.destroy();onLevel(0f);onListening(false)}
}
