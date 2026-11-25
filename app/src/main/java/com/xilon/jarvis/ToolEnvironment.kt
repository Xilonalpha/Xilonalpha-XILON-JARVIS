package com.xilon.jarvis
import android.content.Context
import com.jarvis.mobile.core.tools.ToolRegistry
import com.jarvis.mobile.core.tools.impl.*
object ToolEnvironment {
 lateinit var context: Context
 private set
 fun initialize(ctx:Context) {
  context=ctx.applicationContext
  ToolRegistry.registerAll(listOf(ControlBrightnessTool(),ControlVolumeTool(),ControlFlashlightTool(),ControlWifiTool(),ControlBluetoothTool()))
 }
}
