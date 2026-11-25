package com.xilon.jarvis
import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.jarvis.mobile.core.skills.ElementTarget
class DemonstrationStore(context:Context):SQLiteOpenHelper(context.applicationContext,"demonstrations.db",null,1){
 override fun onCreate(db:SQLiteDatabase){
  db.execSQL("CREATE TABLE demos(id INTEGER PRIMARY KEY,name TEXT NOT NULL,created INTEGER NOT NULL)")
  db.execSQL("CREATE TABLE steps(demo INTEGER NOT NULL,position INTEGER NOT NULL,pkg TEXT NOT NULL,view_id TEXT NOT NULL,PRIMARY KEY(demo,position),FOREIGN KEY(demo) REFERENCES demos(id) ON DELETE CASCADE)")
 }
 override fun onConfigure(db:SQLiteDatabase){super.onConfigure(db);db.setForeignKeyConstraintsEnabled(true)}
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){error("Unsupported migration")}
 data class Demo(val id:Long,val name:String,val count:Int)
 fun save(name:String,steps:List<ElementTarget>):Long{
  require(name.isNotBlank()&&steps.isNotEmpty())
  require(steps.all{!it.pkg.isNullOrBlank()&&!it.viewId.isNullOrBlank()})
  val db=writableDatabase;db.beginTransaction()
  try{
   val id=db.insertOrThrow("demos",null,ContentValues().apply{put("name",name.trim());put("created",System.currentTimeMillis())})
   steps.forEachIndexed{i,t->db.insertOrThrow("steps",null,ContentValues().apply{put("demo",id);put("position",i);put("pkg",t.pkg);put("view_id",t.viewId)})}
   db.setTransactionSuccessful();return id
  }finally{db.endTransaction()}
 }
 fun list():List<Demo> = readableDatabase.rawQuery("SELECT d.id,d.name,COUNT(s.position) FROM demos d LEFT JOIN steps s ON s.demo=d.id GROUP BY d.id ORDER BY d.id DESC",null).use{c->buildList{while(c.moveToNext())add(Demo(c.getLong(0),c.getString(1),c.getInt(2)))}}
 fun load(id:Long):List<ElementTarget> = readableDatabase.rawQuery("SELECT pkg,view_id FROM steps WHERE demo=? ORDER BY position",arrayOf(id.toString())).use{c->buildList{while(c.moveToNext())add(ElementTarget(pkg=c.getString(0),viewId=c.getString(1)))}}
 fun delete(id:Long):Boolean=writableDatabase.delete("demos","id=?",arrayOf(id.toString()))==1
}
