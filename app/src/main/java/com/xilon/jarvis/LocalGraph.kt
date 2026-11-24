package com.xilon.jarvis
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
class LocalGraph(context:Context):SQLiteOpenHelper(context,"local_graph.db",null,1){
 override fun onCreate(db:SQLiteDatabase){
  db.execSQL("CREATE TABLE edges (source TEXT NOT NULL, target TEXT NOT NULL, count INTEGER NOT NULL, last_seen INTEGER NOT NULL, PRIMARY KEY(source,target))")
 }
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){error("Unsupported graph migration")}
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
 fun clear(){writableDatabase.delete("edges",null,null)}
}
