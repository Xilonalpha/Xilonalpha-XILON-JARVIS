package com.xilon.jarvis
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*
class OmegaCoreView(context:Context):View(context){
 var active=false
  set(value){field=value;invalidate()}
 var speaking=false
  set(value){field=value;invalidate()}
 var listening=false
  set(value){field=value;invalidate()}
 var level=0f
  set(value){field=value.coerceIn(0f,1f);invalidate()}
 private var phase=0f
 private var lastFrame=0L
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 override fun onDraw(canvas:Canvas){
  super.onDraw(canvas)
  val radius=min(width,height)*0.44f*(if(listening)0.94f+level*0.06f else 1f)
  if(radius<=0)return
  canvas.save();canvas.translate(width/2f,height/2f);canvas.scale(radius/100,radius/100)
  val now=android.os.SystemClock.uptimeMillis()
  val delta=if(lastFrame==0L)0L else (now-lastFrame).coerceIn(0L,100L)
  lastFrame=now
  phase=(phase+delta*(if(listening)0.025f+level*0.09f else if(speaking)0.06f else if(active)0.085f else 0.015f))%360f
  paint.style=Paint.Style.FILL
  paint.shader=RadialGradient(0f,0f,100f,intArrayOf(if(active || speaking)0xdd30cfff.toInt() else 0x9930CFFF.toInt(),0x0030CFFF),null,Shader.TileMode.CLAMP)
  canvas.drawCircle(0f,0f,100f,paint);paint.shader=null
  paint.style=Paint.Style.STROKE;paint.color=0xff65dcff.toInt()
  for(i in 0..4){
   val r=45f+i*13f
   paint.strokeWidth=if(i==3)3f else 0.9f
   paint.alpha=130+i*25
   val segments=if(i==3)6 else 12
   for(j in 0 until segments)canvas.drawArc(-r,-r,r,r,j*360f/segments+phase*(if(i%2==0)1 else -1),360f/segments-9f,false,paint)
  }
  paint.alpha=220;paint.strokeWidth=1f
  for(i in 0 until 60){val a=i*PI/30;canvas.drawLine((91*cos(a)).toFloat(),(91*sin(a)).toFloat(),(95*cos(a)).toFloat(),(95*sin(a)).toFloat(),paint)}
  paint.alpha=255;paint.strokeWidth=2.5f;paint.color=0xffa6f4ff.toInt()
  val path=Path().apply{moveTo(-32f,0f);lineTo(-18f,0f);lineTo(-9f,-23f);lineTo(0f,25f);lineTo(11f,-15f);lineTo(19f,0f);lineTo(32f,0f)}
  canvas.drawPath(path,paint);canvas.restore()
  if(isShown && windowVisibility==VISIBLE)postInvalidateDelayed(if(active || listening || speaking)32 else 80)
 }
}
