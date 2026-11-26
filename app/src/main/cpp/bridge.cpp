#include <jni.h>
#include <llama.h>
#ifdef XILON_VLM
#include <mtmd.h>
#include <mtmd-helper.h>
#include <memory>
#endif
#include <vector>
#include <string>
#include <mutex>
#include <chrono>
#include <algorithm>
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
struct ModelState {
 llama_model* model;
 llama_context* context=nullptr;
 int size=0;
 int threads=0;
 int requestedSize=0;
 ~ModelState(){if(context)llama_free(context);llama_model_free(model);}
};
static std::once_flag init;
static void fail(JNIEnv* e,const char* msg){e->ThrowNew(e->FindClass("java/lang/IllegalStateException"),msg);}
extern "C" JNIEXPORT jlong JNICALL Java_com_xilon_jarvis_NativeModel_openModel(JNIEnv* e,jobject,jstring path,jint gpuLayers){
 std::call_once(init,[]{llama_backend_init();});
 const char* p=e->GetStringUTFChars(path,nullptr); if(!p)return 0;
 auto params=llama_model_default_params();
 params.n_gpu_layers=llama_supports_gpu_offload()? (gpuLayers<0?0:(gpuLayers>32?32:gpuLayers)) : 0;
 params.load_mode=LLAMA_LOAD_MODE_AUTO;
 // A zero GPU layer count alone may still allow GPU host buffers or
 // auxiliary backend allocations. Explicitly pass an empty device list.
 // llama.cpp may retain this list in model parameters for later contexts.
 // Do not pass a stack-local device array whose lifetime ends at model load.
 static ggml_backend_dev_t cpuOnlyDevices[]={nullptr};
 if(params.n_gpu_layers==0)params.devices=cpuOnlyDevices;
 auto* m=llama_model_load_from_file(p,params);
 if(!m && params.n_gpu_layers>0){
  params.n_gpu_layers=0;
  params.devices=cpuOnlyDevices;
  m=llama_model_load_from_file(p,params);
 }
 e->ReleaseStringUTFChars(path,p);
 if(!m){fail(e,"GGUF load failed: unsupported architecture, invalid tensors, or insufficient memory; see native logcat");return 0;}
 return reinterpret_cast<jlong>(new ModelState{m});
}
extern "C" JNIEXPORT void JNICALL Java_com_xilon_jarvis_NativeModel_freeModel(JNIEnv*,jobject,jlong h){delete reinterpret_cast<ModelState*>(h);}
extern "C" JNIEXPORT jstring JNICALL Java_com_xilon_jarvis_NativeModel_infer(JNIEnv* e,jobject self,jlong h,jobjectArray roles,jobjectArray messages,jint threads,jint context,jint maxTokens,jstring grammar){
 auto* state=reinterpret_cast<ModelState*>(h); auto* m=state->model; auto* v=llama_model_get_vocab(m);
 // Report native phases before the first generated token. Otherwise Android
 // cannot distinguish long prompt evaluation from a deadlocked backend.
 auto* stageClass=e->GetObjectClass(self);
 auto stageMethod=e->GetMethodID(stageClass,"publishStage","(Ljava/lang/String;)V");
 e->DeleteLocalRef(stageClass);
 if(!stageMethod)return nullptr;
 auto publishStage=[&](const std::string& status){
  if(e->ExceptionCheck())return;
  jstring msg=e->NewStringUTF(status.c_str());
  if(!msg)return;
  e->CallVoidMethod(self,stageMethod,msg);
  e->DeleteLocalRef(msg);
 };
 publishStage("Pregătesc conversația...");
 const int trained=llama_model_n_ctx_train(m);
 if(trained>0)context=std::min<int>(context,trained);
 if(context<64){fail(e,"Model context too small");return nullptr;}
 maxTokens=std::min<int>(maxTokens,context/2);
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
 std::vector<llama_token> tokens;
 int n=0;
 for(;;){
  if(tmpl){
   int size=llama_chat_apply_template(tmpl,chat.data(),chat.size(),true,nullptr,0);
   if(size<0){fail(e,"Unsupported GGUF chat template");return nullptr;}
   std::vector<char> formatted(size+1);
   int written=llama_chat_apply_template(tmpl,chat.data(),chat.size(),true,formatted.data(),formatted.size());
   if(written<0 || written>size){fail(e,"Chat formatting failed");return nullptr;}
   input.assign(formatted.data(),written);
  }else{
   input.clear();
   for(const auto& message:chat){input+=std::string(message.role)+": "+message.content+"\n";}
   input+="assistant: ";
  }
  tokens.resize(input.size()+16);
  n=llama_tokenize(v,input.data(),input.size(),tokens.data(),tokens.size(),true,tmpl!=nullptr);
  if(n<0){tokens.resize(-n);n=llama_tokenize(v,input.data(),input.size(),tokens.data(),tokens.size(),true,tmpl!=nullptr);}
  if(n>0 && n<=context-maxTokens)break;
  if(n<=0){fail(e,"Tokenization failed");return nullptr;}
  // Preserve system messages and the current user request; remove oldest complete turns.
  auto first=chat.begin();
  while(first!=chat.end() && std::string(first->role)=="system")++first;
  if(first>=chat.end()-1){fail(e,"Current prompt exceeds supported token budget");return nullptr;}
  auto end=first+1;
  while(end<chat.end()-1 && std::string(end->role)!="user" && std::string(end->role)!="system")++end;
  chat.erase(first,end);
 }
 publishStage("Prompt tokenizat: "+std::to_string(n)+" tokenuri");
 // Retry with smaller context and batch sizes without dropping the current prompt.
 const int required=n+maxTokens;
 const int requested=context;
 if(state->context && (state->requestedSize!=requested || state->size<required)){
  llama_free(state->context);state->context=nullptr;
 }
 int batchSize=std::min<int>(requested,512);
 if(!state->context){
  publishStage("Inițializez contextul GGUF...");
  for(int attempt=requested;;attempt=std::max(required,attempt/2)){
   auto cp=llama_context_default_params();
   cp.n_ctx=attempt;cp.n_batch=std::min<int>(attempt,64);
   cp.n_ubatch=std::min<int>(attempt,32);
   cp.n_threads=threads;cp.n_threads_batch=threads;
   state->context=llama_init_from_model(m,cp);
   if(state->context){state->size=attempt;state->requestedSize=requested;state->threads=threads;batchSize=cp.n_batch;break;}
   if(attempt<=required)break;
  }
 }
 auto* ctx=state->context;
 if(!ctx){fail(e,"Context allocation failed even at minimum prompt budget; reduce history or use a smaller GGUF");return nullptr;}
 if(state->threads!=threads){llama_set_n_threads(ctx,threads,threads);state->threads=threads;}
 batchSize=std::min<int>(state->size,64);
 llama_memory_clear(llama_get_memory(ctx), true);
 auto* sampler=llama_sampler_chain_init(llama_sampler_chain_default_params());
 if(!sampler){fail(e,"Sampler allocation failed");return nullptr;}
 const char* rules=e->GetStringUTFChars(grammar,nullptr);
 if(!rules){llama_sampler_free(sampler);return nullptr;}
 const bool constrainedOutput=*rules!=0;
 if(constrainedOutput){
  auto* constrained=llama_sampler_init_grammar(v,rules,"root");
  if(!constrained){e->ReleaseStringUTFChars(grammar,rules);llama_sampler_free(sampler);fail(e,"Invalid grammar");return nullptr;}
  llama_sampler_chain_add(sampler,constrained);
 }
 e->ReleaseStringUTFChars(grammar,rules);
 if(constrainedOutput){
  llama_sampler_chain_add(sampler,llama_sampler_init_greedy());
 }else{
  llama_sampler_chain_add(sampler,llama_sampler_init_top_k(40));
  llama_sampler_chain_add(sampler,llama_sampler_init_top_p(0.9f,1));
  llama_sampler_chain_add(sampler,llama_sampler_init_temp(0.7f));
  llama_sampler_chain_add(sampler,llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
 }
 std::string out;bool ok=true;
 auto cancel=e->GetMethodID(e->GetObjectClass(self),"isCancelled","()Z");
 if(!cancel){llama_sampler_free(sampler);return nullptr;}
 auto progress=e->GetMethodID(e->GetObjectClass(self),"publishProgress","([B)V");
 if(!progress){llama_sampler_free(sampler);return nullptr;}
 auto tokenEvent=e->GetMethodID(e->GetObjectClass(self),"publishToken","()V");
 if(!tokenEvent){llama_sampler_free(sampler);return nullptr;}
 JavaVM* vm=nullptr;
 if(e->GetJavaVM(&vm)!=JNI_OK){llama_sampler_free(sampler);fail(e,"Java VM unavailable");return nullptr;}
 jobject owner=e->NewGlobalRef(self);
 if(!owner){llama_sampler_free(sampler);return nullptr;}
 AbortState abortState{vm,owner,cancel};
 llama_set_abort_callback(ctx,abortDecode,&abortState);
 size_t publishedBytes=0;
 auto lastProgress=std::chrono::steady_clock::now();
 auto* batch=llama_batch_ext_init(ctx);
 auto decode=[&](const llama_token* ids,int count,int offset,bool output)->int {
  if(!batch)return -1;
  llama_batch_ext_clear(batch);
  for(int j=0;j<count;j++){
   const int idx=llama_batch_ext_add_token(batch,0,ids[j]);
   const llama_pos pos=offset+j;
   if(idx<0 || !llama_batch_ext_set_pos(batch,idx,&pos) ||
      !llama_batch_ext_set_output_logits(batch,idx,output && j==count-1))return -1;
  }
  return llama_process(ctx,LLAMA_PROCESS_TYPE_DECODE,batch);
 };
 bool ready=true;
 publishStage("Evaluez promptul: 0/"+std::to_string(n)+" tokenuri");
 for(int offset=0;offset<n;offset+=batchSize){
  if(e->CallBooleanMethod(self,cancel)||e->ExceptionCheck()){ready=false;break;}
  const int count=std::min(batchSize,n-offset);
  // Publish before entering the synchronous native backend: a slow first
  // decode otherwise leaves the UI at 0/N with no indication of the phase.
  publishStage("GGUF: procesez lotul "+std::to_string(offset+1)+"-"+
   std::to_string(offset+count)+"/"+std::to_string(n)+" tokenuri...");
  const auto batchStarted=std::chrono::steady_clock::now();
  const int status=decode(tokens.data()+offset,count,offset,offset+count==n);
  const auto elapsedMs=std::chrono::duration_cast<std::chrono::milliseconds>(
   std::chrono::steady_clock::now()-batchStarted).count();
  if(status!=0){
   const bool cancelled=e->CallBooleanMethod(self,cancel);
   if(!cancelled){
    ok=false;
    publishStage("Eroare evaluare GGUF la tokenul "+std::to_string(offset)+
     " (cod "+std::to_string(status)+")");
   }
   ready=false;break;
  }
  if(elapsedMs>2000){
   publishStage("Evaluare GGUF lentă: "+std::to_string(offset+count)+"/"+
    std::to_string(n)+" tokenuri, ultimul lot "+std::to_string(elapsedMs)+" ms");
  }
  if((offset/batchSize)%2==0 || offset+count==n){
   publishStage("Evaluez promptul: "+std::to_string(offset+count)+"/"+std::to_string(n)+" tokenuri");
  }
 }
 if(!ready){
  // A cancelled or failed prefill must never be returned as a successful
  // empty answer. Clean up the native resources before leaving JNI.
  llama_synchronize(ctx);
  if(batch)llama_batch_ext_free(batch);
  llama_set_abort_callback(ctx,nullptr,nullptr);
  llama_sampler_free(sampler);
  e->DeleteGlobalRef(owner);
  if(e->ExceptionCheck())return nullptr;
  if(!ok)fail(e,"GGUF prompt evaluation failed");
  else fail(e,"GGUF prompt evaluation cancelled");
  return nullptr;
 }
 publishStage("Generez primul token...");
 for(int i=0;ready && i<maxTokens;i++){
  if(e->CallBooleanMethod(self,cancel) || e->ExceptionCheck())break;
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
  publishedBytes=out.size();
  }
  if(i+1<maxTokens){if(decode(&t,1,n+i,true)!=0){
    const bool cancelled=e->CallBooleanMethod(self,cancel);
    if(!cancelled){ok=false;publishStage("Eroare GGUF la generarea tokenului "+std::to_string(i+1));}
    break;
   }}
 }
 if(ok && !e->ExceptionCheck() && publishedBytes!=out.size()){
  auto partial=e->NewByteArray(out.size());
  if(partial){e->SetByteArrayRegion(partial,0,out.size(),reinterpret_cast<const jbyte*>(out.data()));if(!e->ExceptionCheck())e->CallVoidMethod(self,progress,partial);e->DeleteLocalRef(partial);}
 }
 publishStage("Finalizez răspunsul...");
 llama_synchronize(ctx);if(batch)llama_batch_ext_free(batch);llama_set_abort_callback(ctx,nullptr,nullptr);llama_sampler_free(sampler);e->DeleteGlobalRef(owner);
 if(e->ExceptionCheck())return nullptr;
 if(!ok){fail(e,"Inference failed");return nullptr;}
 auto bytes=e->NewByteArray(out.size());if(!bytes)return nullptr;
 e->SetByteArrayRegion(bytes,0,out.size(),reinterpret_cast<const jbyte*>(out.data()));
 auto cls=e->FindClass("java/lang/String");auto ctor=e->GetMethodID(cls,"<init>","([BLjava/lang/String;)V");
 return static_cast<jstring>(e->NewObject(cls,ctor,bytes,e->NewStringUTF("UTF-8")));
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_xilon_jarvis_NativeModel_probeVision(JNIEnv* e,jobject,jlong h,jstring path){
#ifdef XILON_VLM
 auto* state=reinterpret_cast<ModelState*>(h);
 if(!state || !path)return JNI_FALSE;
 const char* p=e->GetStringUTFChars(path,nullptr);if(!p)return JNI_FALSE;
 auto params=mtmd_context_params_default();params.use_gpu=false;params.warmup=false;params.n_threads=2;
 auto* vision=mtmd_init_from_file(p,state->model,params);
 e->ReleaseStringUTFChars(path,p);
 if(!vision)return JNI_FALSE;
 const bool supported=mtmd_support_vision(vision);mtmd_free(vision);return supported?JNI_TRUE:JNI_FALSE;
#else
 return JNI_FALSE;
#endif
}

// Single-image inference; uses a separate context to avoid contaminating text history.
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_xilon_jarvis_NativeModel_inferImage(JNIEnv* e,jobject self,jlong h,jstring projector,jbyteArray rgb,jint width,jint height,jbyteArray question,jint context,jint limit){
#ifdef XILON_VLM
 auto* state=reinterpret_cast<ModelState*>(h);
 if(!state || !projector || !rgb || !question || width<1 || height<1 || width>1024 || height>1024 || e->GetArrayLength(rgb)!=width*height*3){fail(e,"Invalid image input");return nullptr;}
 if(state->context){llama_free(state->context);state->context=nullptr;}
 const char* path=e->GetStringUTFChars(projector,nullptr);if(!path)return nullptr;
 auto mp=mtmd_context_params_default();mp.use_gpu=false;mp.warmup=false;mp.n_threads=2;
 std::unique_ptr<mtmd_context,decltype(&mtmd_free)> vision(mtmd_init_from_file(path,state->model,mp),mtmd_free);
 e->ReleaseStringUTFChars(projector,path);
 if(!vision || !mtmd_support_vision(vision.get())){fail(e,"Incompatible vision projector");return nullptr;}
 std::vector<unsigned char> pixels(width*height*3);
 e->GetByteArrayRegion(rgb,0,pixels.size(),reinterpret_cast<jbyte*>(pixels.data()));
 std::unique_ptr<mtmd_bitmap,decltype(&mtmd_bitmap_free)> bitmap(mtmd_bitmap_init(width,height,pixels.data()),mtmd_bitmap_free);
 if(!bitmap){fail(e,"Image allocation failed");return nullptr;}
 std::string body(e->GetArrayLength(question),'\0');
 e->GetByteArrayRegion(question,0,body.size(),reinterpret_cast<jbyte*>(body.data()));
 body=std::string(mtmd_get_marker(vision.get()))+"\n"+body;
 llama_chat_message msg{"user",body.c_str()};
 const char* tmpl=llama_model_chat_template(state->model,nullptr);
 if(!tmpl){fail(e,"Vision model requires chat template");return nullptr;}
 int length=llama_chat_apply_template(tmpl,&msg,1,true,nullptr,0);
 if(length<=0){fail(e,"Vision chat template failed");return nullptr;}
 std::vector<char> prompt(length+1);
 int written=llama_chat_apply_template(tmpl,&msg,1,true,prompt.data(),prompt.size());
 if(written<=0 || written>length){fail(e,"Vision prompt failed");return nullptr;}
 mtmd_input_text input{prompt.data(),static_cast<size_t>(written),true,true};
 const mtmd_bitmap* images[]={bitmap.get()};
 std::unique_ptr<mtmd_input_chunks,decltype(&mtmd_input_chunks_free)> chunks(mtmd_input_chunks_init(),mtmd_input_chunks_free);
 if(!chunks || mtmd_tokenize(vision.get(),chunks.get(),&input,images,1)!=0){fail(e,"Vision tokenization failed");return nullptr;}
 auto cp=llama_context_default_params();cp.n_ctx=context;cp.n_batch=256;cp.n_ubatch=128;cp.n_threads=2;cp.n_threads_batch=2;
 std::unique_ptr<llama_context,decltype(&llama_free)> ctx(llama_init_from_model(state->model,cp),llama_free);
 if(!ctx || mtmd_helper_get_n_tokens(chunks.get())+limit>llama_n_ctx(ctx.get())){fail(e,"Insufficient vision context");return nullptr;}
 auto cancel=e->GetMethodID(e->GetObjectClass(self),"isCancelled","()Z");if(!cancel)return nullptr;
 llama_pos pos=0;
 if(e->CallBooleanMethod(self,cancel)){return e->NewByteArray(0);}
 JavaVM* vm=nullptr;
 if(e->GetJavaVM(&vm)!=JNI_OK){fail(e,"Java VM unavailable");return nullptr;}
 jobject owner=e->NewGlobalRef(self);if(!owner)return nullptr;
 AbortState abortState{vm,owner,cancel};
 llama_set_abort_callback(ctx.get(),abortDecode,&abortState);
 struct AbortCleanup { llama_context* ctx; JNIEnv* env; jobject owner; ~AbortCleanup(){llama_synchronize(ctx);llama_set_abort_callback(ctx,nullptr,nullptr);env->DeleteGlobalRef(owner);} } cleanup{ctx.get(),e,owner};
 if(mtmd_helper_eval_chunks(vision.get(),ctx.get(),chunks.get(),0,0,llama_n_batch(ctx.get()),true,&pos)!=0){if(e->CallBooleanMethod(self,cancel))return e->NewByteArray(0);fail(e,"Vision embedding evaluation failed");return nullptr;}
 auto* vocab=llama_model_get_vocab(state->model);
 std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)> sampler(llama_sampler_init_greedy(),llama_sampler_free);
 std::unique_ptr<llama_batch_ext,decltype(&llama_batch_ext_free)> batch(llama_batch_ext_init(ctx.get()),llama_batch_ext_free);
 if(!sampler || !batch){fail(e,"Vision generation allocation failed");return nullptr;}
 std::string output;
 for(int i=0;i<limit;i++){
  if(e->CallBooleanMethod(self,cancel)||e->ExceptionCheck())break;
  auto token=llama_sampler_sample(sampler.get(),ctx.get(),-1);
  if(llama_vocab_is_eog(vocab,token))break;
  std::vector<char> piece(128);int n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false);
  if(n<0){piece.resize(-n);n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false);}
  if(n<0){fail(e,"Vision token conversion failed");return nullptr;}
  output.append(piece.data(),n);
  if(i+1==limit)break;
  llama_batch_ext_clear(batch.get());
  int idx=llama_batch_ext_add_token(batch.get(),0,token);
  if(idx<0 || !llama_batch_ext_set_pos(batch.get(),idx,&pos) || !llama_batch_ext_set_output_logits(batch.get(),idx,true) || llama_process(ctx.get(),LLAMA_PROCESS_TYPE_DECODE,batch.get())!=0){fail(e,"Vision generation failed");return nullptr;}
  ++pos;
 }
 if(e->ExceptionCheck())return nullptr;
 auto bytes=e->NewByteArray(output.size());if(bytes)e->SetByteArrayRegion(bytes,0,output.size(),reinterpret_cast<const jbyte*>(output.data()));return bytes;
#else
 fail(e,"VLM backend not enabled in this APK");return nullptr;
#endif
}
