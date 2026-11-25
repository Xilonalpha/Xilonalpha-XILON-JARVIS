package com.xilon.jarvis
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
class LocalGraph(context:Context):SQLiteOpenHelper(context,"local_graph.db",null,2){
 override fun onCreate(db:SQLiteDatabase){
  createDemonstrationEdges(db)
  db.execSQL("CREATE TABLE edges (source TEXT NOT NULL, target TEXT NOT NULL, count INTEGER NOT NULL, last_seen INTEGER NOT NULL, PRIMARY KEY(source,target))")
 }
 private fun createDemonstrationEdges(db:SQLiteDatabase){db.execSQL("CREATE TABLE IF NOT EXISTS demo_edges(source TEXT NOT NULL,target TEXT NOT NULL,count INTEGER NOT NULL,last_seen INTEGER NOT NULL,PRIMARY KEY(source,target))")}
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){if(oldVersion==1&&newVersion==2)createDemonstrationEdges(db) else error("Unsupported graph migration")}
 fun recordDemonstration(steps:List<com.jarvis.mobile.core.skills.ElementTarget>){
  require(steps.isNotEmpty()&&steps.all{!it.pkg.isNullOrBlank()&&!it.viewId.isNullOrBlank()})
  val db=writableDatabase;db.beginTransaction()
  try{for((a,b) in steps.map{it.pkg+" / "+it.viewId}.zipWithNext()){
   val now=System.currentTimeMillis()
   db.execSQL("INSERT OR IGNORE INTO demo_edges(source,target,count,last_seen) VALUES(?,?,0,?)",arrayOf(a,b,now))
   db.execSQL("UPDATE demo_edges SET count=count+1,last_seen=? WHERE source=? AND target=?",arrayOf(now,a,b))
  };db.setTransactionSuccessful()}finally{db.endTransaction()}
 }
 fun demonstrationSummary():String=readableDatabase.rawQuery("SELECT source,target,count FROM demo_edges ORDER BY count DESC,source,target LIMIT 50",null).use{c->buildList{while(c.moveToNext())add(c.getString(0)+" → "+c.getString(1)+" : "+c.getLong(2))}.joinToString("\n").ifBlank{"Nicio tranziție demonstrată."}}
 fun record(steps:List<String>){
  require(steps.size in 1..5 && steps.all{ToolCatalog.permits(it.lowercase(java.util.Locale.ROOT))})
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
 fun recommend(source:String):String{
  val key=source.trim().lowercase(java.util.Locale.ROOT)
  require(ToolCatalog.permits(key)){"Instrument necunoscut"}
  return readableDatabase.rawQuery("SELECT target,count FROM edges WHERE source=? AND count>=2 ORDER BY count DESC,target LIMIT 3",arrayOf(key)).use{c->
   val rows=mutableListOf<String>()
   while(c.moveToNext()){val target=c.getString(0);if(ToolCatalog.permits(target))rows.add(target+" — "+c.getLong(1)+" tranziții observate")}
   rows.joinToString("\n").ifBlank{"Date insuficiente pentru recomandare."}
  }
 }
 fun clear(){val db=writableDatabase;db.beginTransaction();try{db.delete("edges",null,null);db.delete("demo_edges",null,null);db.setTransactionSuccessful()}finally{db.endTransaction()}}
}
