#include <jni.h>
#include <llama.h>
#include <vector>
#include <string>
#include <mutex>
#include <chrono>
struct AbortState { JavaVM* vm; jobject owner; jmethodID method; };
static bool abortDecode(void* data){
 auto* state=static_cast<AbortState*>(data);
 JNIEnv* env=nullptr;
 bool attached=false;
 if(state->vm->GetEnv(reinterpret_cast<void**>(&env),JNI_VERSION_1_6)==JNI_EDETACHED){
  if(state->vm->AttachCurrentThread(&env,nullptr)!=JNI_OK)return true;
  attached=true;
 }
 if(!env)return true;
 bool stop=env->CallBooleanMethod(state->owner,state->method)||env->ExceptionCheck();
 if(attached){if(env->ExceptionCheck())env->ExceptionClear();state->vm->DetachCurrentThread();}
 return stop;
}
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
extern "C" JNIEXPORT jstring JNICALL Java_com_xilon_jarvis_NativeModel_infer(JNIEnv* e,jobject self,jlong h,jobjectArray roles,jobjectArray messages,jint threads,jint context,jint maxTokens,jstring grammar){
 auto* m=reinterpret_cast<llama_model*>(h); auto* v=llama_model_get_vocab(m);
 const int count=e->GetArrayLength(messages);
 if(count<1 || count!=e->GetArrayLength(roles)){fail(e,"Invalid conversation");return nullptr;}
 std::vector<std::string> role(count),body(count);
 std::vector<llama_chat_message> chat(count);
 for(int i=0;i<count;i++){
  auto r=static_cast<jstring>(e->GetObjectArrayElement(roles,i));
  auto a=static_cast<jbyteArray>(e->GetObjectArrayElement(messages,i));
  const char* rp=e->GetStringUTFChars(r,nullptr); if(!rp)return nullptr;
  role[i]=rp;e->ReleaseStringUTFChars(r,rp);
  body[i].resize(e->GetArrayLength(a));
  e->GetByteArrayRegion(a,0,body[i].size(),reinterpret_cast<jbyte*>(body[i].data()));
  e->DeleteLocalRef(r);e->DeleteLocalRef(a);
  chat[i]={role[i].c_str(),body[i].c_str()};
 }
 const char* tmpl=llama_model_chat_template(m,nullptr);
 std::string input;
 if(tmpl){
  int size=llama_chat_apply_template(tmpl,chat.data(),chat.size(),true,nullptr,0);
  if(size<0){fail(e,"Unsupported GGUF chat template");return nullptr;}
  std::vector<char> formatted(size+1);
  int written=llama_chat_apply_template(tmpl,chat.data(),chat.size(),true,formatted.data(),formatted.size());
  if(written<0 || written>size){fail(e,"Chat formatting failed");return nullptr;}
  input.assign(formatted.data(),written);
 }else{
  for(int i=0;i<count;i++){input+=role[i]+": "+body[i]+"\n";}
  input+="assistant: ";
 }

 std::vector<llama_token> tokens(input.size()+16);
 int n=llama_tokenize(v,input.data(),input.size(),tokens.data(),tokens.size(),true,tmpl!=nullptr);
 if(n<=0 || n>context-maxTokens){fail(e,"Prompt exceeds supported token budget");return nullptr;}
 auto cp=llama_context_default_params();cp.n_ctx=context;cp.n_batch=context;cp.n_threads=threads;cp.n_threads_batch=threads;
 auto* ctx=llama_init_from_model(m,cp);if(!ctx){fail(e,"Context allocation failed");return nullptr;}
 auto* sampler=llama_sampler_chain_init(llama_sampler_chain_default_params());
 if(!sampler){llama_free(ctx);fail(e,"Sampler allocation failed");return nullptr;}
 const char* rules=e->GetStringUTFChars(grammar,nullptr);
 if(!rules){llama_sampler_free(sampler);llama_free(ctx);return nullptr;}
 if(*rules){
  auto* constrained=llama_sampler_init_grammar(v,rules,"root");
  if(!constrained){e->ReleaseStringUTFChars(grammar,rules);llama_sampler_free(sampler);llama_free(ctx);fail(e,"Invalid grammar");return nullptr;}
  llama_sampler_chain_add(sampler,constrained);
 }
 e->ReleaseStringUTFChars(grammar,rules);
 llama_sampler_chain_add(sampler,llama_sampler_init_greedy());
 std::string out;auto batch=llama_batch_get_one(tokens.data(),n);bool ok=true;
 auto cancel=e->GetMethodID(e->GetObjectClass(self),"isCancelled","()Z");
 if(!cancel){llama_sampler_free(sampler);llama_free(ctx);return nullptr;}
 auto progress=e->GetMethodID(e->GetObjectClass(self),"publishProgress","([B)V");
 if(!progress){llama_sampler_free(sampler);llama_free(ctx);return nullptr;}
 auto tokenEvent=e->GetMethodID(e->GetObjectClass(self),"publishToken","()V");
 if(!tokenEvent){llama_sampler_free(sampler);llama_free(ctx);return nullptr;}
 JavaVM* vm=nullptr;
 if(e->GetJavaVM(&vm)!=JNI_OK){llama_sampler_free(sampler);llama_free(ctx);fail(e,"Java VM unavailable");return nullptr;}
 jobject owner=e->NewGlobalRef(self);
 if(!owner){llama_sampler_free(sampler);llama_free(ctx);return nullptr;}
 AbortState abortState{vm,owner,cancel};
 llama_set_abort_callback(ctx,abortDecode,&abortState);
 auto lastProgress=std::chrono::steady_clock::now();
 for(int i=0;i<maxTokens;i++){
  if(e->CallBooleanMethod(self,cancel) || e->ExceptionCheck())break;
  if(llama_decode(ctx,batch)!=0){ok=e->CallBooleanMethod(self,cancel);break;}
  auto t=llama_sampler_sample(sampler,ctx,-1);if(llama_vocab_is_eog(v,t))break;
  std::vector<char> piece(128);int len=llama_token_to_piece(v,t,piece.data(),piece.size(),0,false);
  if(len<0){piece.resize(-len);len=llama_token_to_piece(v,t,piece.data(),piece.size(),0,false);}
  if(len<0){ok=false;break;}out.append(piece.data(),len);
  e->CallVoidMethod(self,tokenEvent);if(e->ExceptionCheck()){ok=false;break;}
  const auto now=std::chrono::steady_clock::now();
  if(i==0 || now-lastProgress>=std::chrono::milliseconds(50) || i==maxTokens-1){
  lastProgress=now;
  auto partial=e->NewByteArray(out.size());
  if(!partial){ok=false;break;}
  e->SetByteArrayRegion(partial,0,out.size(),reinterpret_cast<const jbyte*>(out.data()));
  e->CallVoidMethod(self,progress,partial);e->DeleteLocalRef(partial);
  if(e->ExceptionCheck()){ok=false;break;}
  }
  tokens[0]=t;batch=llama_batch_get_one(tokens.data(),1);
 }
 llama_sampler_free(sampler);llama_free(ctx);e->DeleteGlobalRef(owner);
 if(e->ExceptionCheck())return nullptr;
 if(!ok){fail(e,"Inference failed");return nullptr;}
 auto bytes=e->NewByteArray(out.size());if(!bytes)return nullptr;
 e->SetByteArrayRegion(bytes,0,out.size(),reinterpret_cast<const jbyte*>(out.data()));
 auto cls=e->FindClass("java/lang/String");auto ctor=e->GetMethodID(cls,"<init>","([BLjava/lang/String;)V");
 return static_cast<jstring>(e->NewObject(cls,ctor,bytes,e->NewStringUTF("UTF-8")));
}
