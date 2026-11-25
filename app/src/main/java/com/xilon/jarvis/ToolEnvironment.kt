package com.xilon.jarvis
import android.content.Context
import com.jarvis.mobile.core.tools.*
import com.jarvis.mobile.core.safety.RiskClassifier
import kotlinx.serialization.json.*
import com.jarvis.mobile.core.tools.impl.*
object ToolEnvironment {
 lateinit var context: Context
 private set
 fun initialize(ctx:Context) {
  context=ctx.applicationContext
  ToolRegistry.registerAll(listOf(ControlBrightnessTool(),ControlVolumeTool(),ControlFlashlightTool(),ControlWifiTool(),ControlBluetoothTool(),OpenUrlTool(),YoutubeSearchTool(),MapsNavigateTool(),SetAlarmTool(),SetTimerTool(),MediaPlayPauseTool(),ToggleDndTool(),OpenAppTool(),LaunchIntentTool(),ShareContentTool(),CallContactTool(),SendSmsTool(),WhatsAppMessageTool(),TelegramMessageTool(),SendEmailTool(),GetLocationTool(),ReadCalendarTool(),FindFileTool(),OpenFileTool(),ShareFileTool(),ListAppsTool(),ReadScreenTool(),FindElementTool(),PressBackTool(),PressHomeTool(),PressRecentsTool(),PressNotificationsTool(),WaitTool(),WaitForChangeTool()))
 }
 suspend fun execute(name:String,args:JsonObject,confirmed:Boolean=false):ToolResult {
  val tool=ToolRegistry.get(name) ?: return ToolResult.unavailable("Instrument necunoscut")
  if(!tool.available()) return ToolResult.unavailable("Instrument indisponibil")
  val specs=tool.spec.params.associateBy{it.name}
  if(args.keys.any{it !in specs}) return ToolResult.fail("Argument necunoscut")
  for(p in tool.spec.params){
   val v=args[p.name]
   if(v==null){if(p.required)return ToolResult.fail("Argument lipsă: "+p.name);continue}
   val j=v as? JsonPrimitive ?: return ToolResult.fail("Argument invalid: "+p.name)
   val valid=when(p.type){"string"->j.isString && !j.content.isBlank(); "int"->!j.isString && j.intOrNull!=null; "bool"->!j.isString && j.booleanOrNull!=null; else->false}
   if(!valid)return ToolResult.fail("Tip invalid: "+p.name)
  }
  val obs=LocalScreen.observe()
  val risk=RiskClassifier.classify(tool.spec,args,obs?.packageName)
  if(!confirmed && (risk!=Risk.LOW || name=="launch_intent")) return ToolResult(ToolStatus.REQUIRES_CONFIRMATION,"Confirmare necesară",Verification.COULD_NOT_VERIFY)
  return try {tool.execute(args,ToolContext(obs))} catch(e:kotlinx.coroutines.CancellationException){throw e} catch(e:Exception){ToolResult.fail("Execuție eșuată: "+e.javaClass.simpleName)}
 }
}
