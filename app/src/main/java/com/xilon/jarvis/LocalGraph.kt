package com.xilon.jarvis
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
class LocalGraph(context:Context):SQLiteOpenHelper(context,"local_graph.db",null,3){
 override fun onCreate(db:SQLiteDatabase){
  createRegistryEdges(db)
  createDemonstrationEdges(db)
  db.execSQL("CREATE TABLE edges (source TEXT NOT NULL, target TEXT NOT NULL, count INTEGER NOT NULL, last_seen INTEGER NOT NULL, PRIMARY KEY(source,target))")
 }
 private fun createDemonstrationEdges(db:SQLiteDatabase){db.execSQL("CREATE TABLE IF NOT EXISTS demo_edges(source TEXT NOT NULL,target TEXT NOT NULL,count INTEGER NOT NULL,last_seen INTEGER NOT NULL,PRIMARY KEY(source,target))")}
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){if(oldVersion<2)createDemonstrationEdges(db);if(oldVersion<3)createRegistryEdges(db)}
 private fun createRegistryEdges(db:SQLiteDatabase){db.execSQL("CREATE TABLE IF NOT EXISTS registry_edges(source TEXT NOT NULL,target TEXT NOT NULL,count INTEGER NOT NULL,PRIMARY KEY(source,target))")}
 fun recordRegistry(outcome:RegistryAgentEngine.Outcome){
  val verified=outcome.steps.takeWhile{it.result.status==com.jarvis.mobile.core.tools.ToolStatus.SUCCESS && it.result.verified==com.jarvis.mobile.core.tools.Verification.VERIFIED}
  val db=writableDatabase;db.beginTransaction()
  try{for((a,b) in verified.map{it.action.tool}.zipWithNext()){
   db.execSQL("INSERT OR IGNORE INTO registry_edges(source,target,count) VALUES(?,?,0)",arrayOf(a,b))
   db.execSQL("UPDATE registry_edges SET count=count+1 WHERE source=? AND target=?",arrayOf(a,b))
  };db.setTransactionSuccessful()}finally{db.endTransaction()}
 }
 fun registrySummary():String=readableDatabase.rawQuery("SELECT source,target,count FROM registry_edges ORDER BY count DESC,source,target LIMIT 30",null).use{c->buildList{while(c.moveToNext())add(c.getString(0)+" → "+c.getString(1)+" : "+c.getLong(2))}.joinToString("\n").ifBlank{"Nicio tranziție verificată."}}
 fun recordDemonstration(steps:List<com.jarvis.mobile.core.skills.ElementTarget>){
  require(steps.isNotEmpty()&&steps.all{!it.pkg.isNullOrBlank()&&!it.viewId.isNullOrBlank()})
  val db=writableDatabase;db.beginTransaction()
  try{for((a,b) in steps.map{it.pkg+" / "+it.viewId}.zipWithNext()){
   val now=System.currentTimeMillis()
   db.execSQL("INSERT OR IGNORE INTO demo_edges(source,target,count,last_seen) VALUES(?,?,0,?)",arrayOf(a,b,now))
   db.execSQL("UPDATE demo_edges SET count=count+1,last_seen=? WHERE source=? AND target=?",arrayOf(now,a,b))
  };db.setTransactionSuccessful()}finally{db.endTransaction()}
 }
 fun rebuildDemonstrations(demos:List<List<com.jarvis.mobile.core.skills.ElementTarget>>){
  val db=writableDatabase;db.beginTransaction()
  try{db.delete("demo_edges",null,null);for(steps in demos){for((a,b) in steps.map{it.pkg+" / "+it.viewId}.zipWithNext()){val now=System.currentTimeMillis();db.execSQL("INSERT OR IGNORE INTO demo_edges(source,target,count,last_seen) VALUES(?,?,0,?)",arrayOf(a,b,now));db.execSQL("UPDATE demo_edges SET count=count+1,last_seen=? WHERE source=? AND target=?",arrayOf(now,a,b))}};db.setTransactionSuccessful()}finally{db.endTransaction()}
 }
 fun demonstrationSummary():String=readableDatabase.rawQuery("SELECT source,target,count FROM demo_edges ORDER BY count DESC,source,target LIMIT 50",null).use{c->buildList{while(c.moveToNext())add(c.getString(0)+" → "+c.getString(1)+" : "+c.getLong(2))}.joinToString("\n").ifBlank{"Nicio tranziție demonstrată."}}
 fun record(steps:List<String>){
  require(steps.size in 1..ToolCatalog.readOnly.size && steps.all{ToolCatalog.permits(it.lowercase(java.util.Locale.ROOT))})
  val db=writableDatabase
  db.beginTransaction()
  try{
   for((a,b) in steps.map{it.lowercase(java.util.Locale.ROOT)}.zipWithNext()){
    val now=System.currentTimeMillis()
    db.execSQL("INSERT OR IGNORE INTO edges(source,target,count,last_seen) VALUES(?,?,0,?)",arrayOf(a,b,now))
    db.execSQL("UPDATE edges SET count=count+1,last_seen=? WHERE source=? AND target=?",arrayOf(now,a,b))
   }
   db.setTransactionSuccessful()
  }finally{db.endTransaction()}
 }
 fun summary():String=readableDatabase.rawQuery("SELECT source,target,count FROM edges ORDER BY count DESC,source,target LIMIT 30",null).use{c->
  val rows=mutableListOf<String>()
  while(c.moveToNext())rows.add(c.getString(0)+" → "+c.getString(1)+" : "+c.getLong(2))
  rows.joinToString("\n").ifBlank{"Nicio relație observată."}
 }
 fun observedSuccessors(source:String):List<Pair<String,Long>>{
  val key=source.trim().lowercase(java.util.Locale.ROOT)
  require(ToolCatalog.permits(key))
  return readableDatabase.rawQuery("SELECT target,count FROM edges WHERE source=? ORDER BY count DESC,target",arrayOf(key)).use{c->buildList{while(c.moveToNext()){val target=c.getString(0);if(ToolCatalog.permits(target))add(target to c.getLong(1))}}}
 }
 fun recommend(source:String):String{
  val key=source.trim().lowercase(java.util.Locale.ROOT)
  require(ToolCatalog.permits(key)){"Instrument necunoscut"}
  return readableDatabase.rawQuery("SELECT target,count FROM edges WHERE source=? AND count>=2 ORDER BY count DESC,target LIMIT 3",arrayOf(key)).use{c->
   val rows=mutableListOf<String>()
   while(c.moveToNext()){val target=c.getString(0);if(ToolCatalog.permits(target))rows.add(target+" — "+c.getLong(1)+" tranziții observate")}
   rows.joinToString("\n").ifBlank{"Date insuficiente pentru recomandare."}
  }
 }
 fun clear(){val db=writableDatabase;db.beginTransaction();try{db.delete("registry_edges",null,null);db.delete("edges",null,null);db.delete("demo_edges",null,null);db.setTransactionSuccessful()}finally{db.endTransaction()}}
}
