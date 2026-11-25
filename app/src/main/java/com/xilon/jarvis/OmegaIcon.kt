package com.xilon.jarvis
import android.graphics.*
import android.graphics.drawable.Drawable
class OmegaIcon(private val kind:String,private val size:Int,private val tint:Int):Drawable(){
 private val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=tint;style=Paint.Style.STROKE;strokeWidth=1.7f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
 override fun getIntrinsicWidth()=size
 override fun getIntrinsicHeight()=size
 override fun setAlpha(alpha:Int){p.alpha=alpha;invalidateSelf()}
 override fun setColorFilter(filter:ColorFilter?){p.colorFilter=filter;invalidateSelf()}
 override fun getOpacity()=PixelFormat.TRANSLUCENT
 override fun draw(c:Canvas){
  c.save();c.translate(bounds.left.toFloat(),bounds.top.toFloat());c.scale(bounds.width()/24f,bounds.height()/24f)
  fun line(a:Float,b:Float,x:Float,y:Float)=c.drawLine(a,b,x,y,p)
  fun path(vararg xy:Float){val q=Path();q.moveTo(xy[0],xy[1]);for(i in 2 until xy.size step 2)q.lineTo(xy[i],xy[i+1]);c.drawPath(q,p)}
  when(kind){
   "eye"->{val q=Path();q.moveTo(2f,12f);q.quadTo(12f,-2f,22f,12f);q.quadTo(12f,26f,2f,12f);c.drawPath(q,p);c.drawCircle(12f,12f,3f,p)}
   "record"->{c.drawCircle(12f,12f,9f,p);c.drawCircle(12f,12f,4f,p)}
   "hand"->{path(7f,12f,7f,5f,10f,5f,10f,12f,10f,2f,13f,2f,13f,12f,13f,5f,16f,5f,16f,13f,19f,9f,21f,11f,18f,20f,9f,22f,3f,14f,5f,12f,7f,15f)}
   "mic"->{c.drawRoundRect(9f,2f,15f,15f,3f,3f,p);c.drawArc(5f,5f,19f,19f,0f,180f,false,p);line(12f,19f,12f,22f);line(8f,22f,16f,22f)}
   "send"->{path(3f,3f,22f,12f,3f,21f,7f,12f,3f,3f);line(7f,12f,22f,12f)}
   "stop"->c.drawRoundRect(5f,5f,19f,19f,2f,2f,p)
   "attach"->{val q=Path();q.moveTo(8f,15f);q.lineTo(15f,8f);q.cubicTo(19f,4f,22f,9f,18f,13f);q.lineTo(10f,21f);q.cubicTo(4f,25f,0f,17f,5f,12f);q.lineTo(14f,3f);c.drawPath(q,p)}
   "Chat"->{path(4f,4f,20f,4f,20f,17f,10f,17f,4f,22f,4f,4f);line(8f,9f,16f,9f);line(8f,13f,14f,13f)}
   "AI"->{c.drawRoundRect(4f,5f,20f,19f,5f,5f,p);c.drawCircle(9f,11f,1f,p);c.drawCircle(15f,11f,1f,p);line(9f,15f,15f,15f);line(12f,2f,12f,5f)}
   "Agenți"->{c.drawRoundRect(5f,5f,19f,19f,2f,2f,p);c.drawRect(9f,9f,15f,15f,p);for(i in 8..16 step 4){val v=i.toFloat();line(v,2f,v,5f);line(v,19f,v,22f);line(2f,v,5f,v);line(19f,v,22f,v)}}
   else->{c.drawCircle(12f,12f,7f,p);c.drawCircle(12f,12f,3f,p);for(i in 0..7){val a=i*Math.PI/4;line((12+7*kotlin.math.cos(a)).toFloat(),(12+7*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat())}}
  };c.restore()
 }
}
