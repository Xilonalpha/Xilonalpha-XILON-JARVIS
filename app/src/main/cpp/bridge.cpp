#include <jni.h>
#include <llama.h>
#include <vector>
#include <string>
#include <mutex>
static std::once_flag init;
static void fail(JNIEnv* e,const char* msg){e->ThrowNew(e->FindClass("java/lang/IllegalStateException"),msg);}
extern "C" JNIEXPORT jlong JNICALL Java_com_xilon_jarvis_NativeModel_openModel(JNIEnv* e,jobject,jstring path){
 std::call_once(init,[]{llama_backend_init();});
 const char* p=e->GetStringUTFChars(path,nullptr); if(!p)return 0;
 auto params=llama_model_default_params(); params.n_gpu_layers=0;
 auto* m=llama_model_load_from_file(p,params); e->ReleaseStringUTFChars(path,p);
 return reinterpret_cast<jlong>(m);
}
extern "C" JNIEXPORT void JNICALL Java_com_xilon_jarvis_NativeModel_freeModel(JNIEnv*,jobject,jlong h){llama_model_free(reinterpret_cast<llama_model*>(h));}
extern "C" JNIEXPORT jstring JNICALL Java_com_xilon_jarvis_NativeModel_infer(JNIEnv* e,jobject,jlong h,jstring prompt){
 auto* m=reinterpret_cast<llama_model*>(h); auto* v=llama_model_get_vocab(m);
 const char* p=e->GetStringUTFChars(prompt,nullptr); if(!p)return nullptr;
 std::string input(p); e->ReleaseStringUTFChars(prompt,p);
 std::vector<llama_token> tokens(input.size()+16);
 int n=llama_tokenize(v,input.data(),input.size(),tokens.data(),tokens.size(),true,false);
 if(n<=0 || n>768){fail(e,"Prompt exceeds supported token budget");return nullptr;}
 auto cp=llama_context_default_params();cp.n_ctx=1024;cp.n_batch=1024;cp.n_threads=2;cp.n_threads_batch=2;
 auto* ctx=llama_init_from_model(m,cp);if(!ctx){fail(e,"Context allocation failed");return nullptr;}
 auto* sampler=llama_sampler_init_greedy();
 std::string out;auto batch=llama_batch_get_one(tokens.data(),n);bool ok=true;
 for(int i=0;i<128;i++){
  if(llama_decode(ctx,batch)!=0){ok=false;break;}
  auto t=llama_sampler_sample(sampler,ctx,-1);if(llama_vocab_is_eog(v,t))break;
  char piece[1024];int len=llama_token_to_piece(v,t,piece,sizeof(piece),0,false);
  if(len<0){ok=false;break;}out.append(piece,len);tokens[0]=t;batch=llama_batch_get_one(tokens.data(),1);
 }
 llama_sampler_free(sampler);llama_free(ctx);
 if(!ok){fail(e,"Inference failed");return nullptr;}
 auto bytes=e->NewByteArray(out.size());if(!bytes)return nullptr;
 e->SetByteArrayRegion(bytes,0,out.size(),reinterpret_cast<const jbyte*>(out.data()));
 auto cls=e->FindClass("java/lang/String");auto ctor=e->GetMethodID(cls,"<init>","([BLjava/lang/String;)V");
 return static_cast<jstring>(e->NewObject(cls,ctor,bytes,e->NewStringUTF("UTF-8")));
}
