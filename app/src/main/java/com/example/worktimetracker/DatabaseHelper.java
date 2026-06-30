package com.example.worktimetracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME="worktime_tracker_android.db";
    private static final int DB_VERSION=8;
    DatabaseHelper(Context c){super(c,DB_NAME,null,DB_VERSION);} 

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS segments(id INTEGER PRIMARY KEY AUTOINCREMENT,start_ms INTEGER NOT NULL,end_ms INTEGER NOT NULL,start_ts TEXT,end_ts TEXT,greg_date TEXT,jalali_date TEXT,app_name TEXT,package_name TEXT,window_title TEXT,duration_seconds INTEGER,tag TEXT,category TEXT,color TEXT,screenshot_path TEXT,session_id TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_segments_start ON segments(start_ms)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_segments_date ON segments(greg_date)");
        db.execSQL("CREATE TABLE IF NOT EXISTS screenshots(id INTEGER PRIMARY KEY AUTOINCREMENT,captured_ms INTEGER NOT NULL,captured_ts TEXT,greg_date TEXT,jalali_date TEXT,app_name TEXT,package_name TEXT,path TEXT NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_screenshots_time ON screenshots(captured_ms)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_screenshots_pkg_time ON screenshots(package_name,captured_ms)");
        db.execSQL("CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY,value TEXT)");
        seed(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){onCreate(db);} 
    private void seed(SQLiteDatabase db){putIfMissing(db,"theme","dark");putIfMissing(db,"auto_track","true");putIfMissing(db,"auto_screenshot","true");putIfMissing(db,"pin","1183");putIfMissing(db,"screenshot_retention_days","7");putIfMissing(db,"idle_limit_seconds",String.valueOf(TimeUtils.DEFAULT_IDLE_LIMIT_MS/1000));}
    private void putIfMissing(SQLiteDatabase db,String k,String v){try(Cursor c=db.rawQuery("SELECT value FROM settings WHERE key=?",new String[]{k})){if(c.moveToFirst())return;}putSetting(db,k,v);} 
    void setSetting(String k,String v){putSetting(getWritableDatabase(),k,v);} 
    private void putSetting(SQLiteDatabase db,String k,String v){ContentValues cv=new ContentValues();cv.put("key",k);cv.put("value",v);db.insertWithOnConflict("settings",null,cv,SQLiteDatabase.CONFLICT_REPLACE);} 
    String getSetting(String k,String def){try(Cursor c=getReadableDatabase().rawQuery("SELECT value FROM settings WHERE key=?",new String[]{k})){return c.moveToFirst()?c.getString(0):def;}}

    synchronized long upsertLiveSegment(long id,long start,long end,String app,String pkg){
        if(end<=start)end=start+1000L;
        SQLiteDatabase db=getWritableDatabase();
        if(id>0){ContentValues cv=liveUpdateValues(start,end,pkg);cv.put("session_id","foreground-service-live");if(db.update("segments",cv,"id=?",new String[]{String.valueOf(id)})>0)return id;}
        String safe=app==null||app.isEmpty()?"Unknown":app;
        ContentValues cv=new ContentValues();cv.put("start_ms",start);cv.put("end_ms",end);cv.put("start_ts",TimeUtils.toIso(start));cv.put("end_ts",TimeUtils.toIso(end));cv.put("greg_date",TimeUtils.gregDate(start));cv.put("jalali_date",JalaliUtils.dateString(start));cv.put("app_name",safe);cv.put("package_name",pkg==null?"":pkg);cv.put("window_title",safe);cv.put("duration_seconds",Math.max(1,(end-start)/1000));cv.put("tag","");cv.put("category",AppUtil.categoryFor(safe,pkg));cv.put("color",AppUtil.colorFor(safe));cv.put("screenshot_path",findScreenshotPath(pkg,start,end));cv.put("session_id","foreground-service-live");return db.insert("segments",null,cv);
    }
    synchronized long finalizeLiveSegment(long id,long start,long end,String app,String pkg){if(end<=start||end-start<800L){if(id>0)getWritableDatabase().delete("segments","id=?",new String[]{String.valueOf(id)});return-1;}long nid=upsertLiveSegment(id,start,end,app,pkg);ContentValues cv=new ContentValues();cv.put("session_id","foreground-service");getWritableDatabase().update("segments",cv,"id=?",new String[]{String.valueOf(nid)});return nid;}
    private ContentValues liveUpdateValues(long start,long end,String pkg){ContentValues cv=new ContentValues();cv.put("end_ms",end);cv.put("end_ts",TimeUtils.toIso(end));cv.put("duration_seconds",Math.max(1,(end-start)/1000));String shot=findScreenshotPath(pkg,start,end);if(!shot.isEmpty())cv.put("screenshot_path",shot);return cv;}

    synchronized void insertScreenshot(long ms,String app,String pkg,String path){if(path==null||path.isEmpty())return;SQLiteDatabase db=getWritableDatabase();ContentValues cv=new ContentValues();cv.put("captured_ms",ms);cv.put("captured_ts",TimeUtils.toIso(ms));cv.put("greg_date",TimeUtils.gregDate(ms));cv.put("jalali_date",JalaliUtils.dateString(ms));cv.put("app_name",app==null?"Unknown":app);cv.put("package_name",pkg==null?"":pkg);cv.put("path",path);db.insert("screenshots",null,cv);attachScreenshot(pkg,ms,path);cleanupScreenshotsByRetention();}
    private void attachScreenshot(String pkg,long ms,String path){ContentValues cv=new ContentValues();cv.put("screenshot_path",path);getWritableDatabase().update("segments",cv,"package_name=? AND screenshot_path='' AND start_ms<=? AND end_ms>=?",new String[]{pkg==null?"":pkg,String.valueOf(ms+4000),String.valueOf(ms-4000)});} 
    String findScreenshotPath(String pkg,long start,long end){try(Cursor c=getReadableDatabase().rawQuery("SELECT path FROM screenshots WHERE package_name=? AND captured_ms>=? AND captured_ms<=? ORDER BY ABS(captured_ms-?) ASC LIMIT 1",new String[]{pkg==null?"":pkg,String.valueOf(Math.max(0,start-5000)),String.valueOf(end+7000),String.valueOf(start)})){return c.moveToFirst()?c.getString(0):"";}catch(Exception e){return"";}}
    void cleanupScreenshotsByRetention(){int days;try{days=Integer.parseInt(getSetting("screenshot_retention_days","7"));}catch(Exception e){days=7;}if(days<=0)return;long cutoff=System.currentTimeMillis()-days*24L*60L*60L*1000L;SQLiteDatabase db=getWritableDatabase();try(Cursor c=db.rawQuery("SELECT path FROM screenshots WHERE captured_ms<?",new String[]{String.valueOf(cutoff)})){while(c.moveToNext()){try{new File(c.getString(0)).delete();}catch(Exception ignored){}}}db.delete("screenshots","captured_ms<?",new String[]{String.valueOf(cutoff)});} 

    List<ActivitySegment> queryDay(long day){long start=TimeUtils.dayStart(day),end=TimeUtils.nextDayStart(day);return queryBetweenMillis(start,end);} 
    List<ActivitySegment> queryBetweenDays(long from,long to){return queryBetweenMillis(TimeUtils.dayStart(from),TimeUtils.nextDayStart(to));}
    private List<ActivitySegment> queryBetweenMillis(long start,long end){List<ActivitySegment> list=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM segments WHERE start_ms>=? AND start_ms<? ORDER BY start_ms ASC",new String[]{String.valueOf(start),String.valueOf(end)})){while(c.moveToNext())list.add(fromCursor(c));}return list;}
    long totalBetweenDays(long from,long to){long sum=0;for(ActivitySegment s:queryBetweenDays(from,to))sum+=s.durationSeconds;return sum;}
    void updateTag(List<Long> ids,String tag){if(ids==null||ids.isEmpty())return;SQLiteDatabase db=getWritableDatabase();ContentValues cv=new ContentValues();cv.put("tag",tag==null?"":tag.trim());for(Long id:ids)db.update("segments",cv,"id=?",new String[]{String.valueOf(id)});}

    ReportData buildRangeReport(List<ActivitySegment> rows){ReportData r=new ReportData();Map<String,Long> apps=new HashMap<>();Map<String,String> cats=new HashMap<>();Map<Long,ReportData.DailyItem> daily=new LinkedHashMap<>();Map<Long,Map<String,Long>> dailyApps=new HashMap<>();for(ActivitySegment s:rows){r.totalSeconds+=s.durationSeconds;String app=s.appName==null?"Unknown":s.appName;apps.put(app,apps.containsKey(app)?apps.get(app)+s.durationSeconds:s.durationSeconds);cats.put(app,s.category==null?"Other":s.category);long day=TimeUtils.dayStart(s.startMs);ReportData.DailyItem d=daily.get(day);if(d==null){d=new ReportData.DailyItem();d.dayStartMs=day;d.jalaliDate=JalaliUtils.dateString(day);d.weekday=JalaliUtils.weekdayName(day);d.start=TimeUtils.hhmm(s.startMs);d.end=TimeUtils.hhmm(s.endMs);daily.put(day,d);dailyApps.put(day,new HashMap<String,Long>());}d.total+=s.durationSeconds;d.end=TimeUtils.hhmm(Math.max(s.endMs,day));Map<String,Long> dm=dailyApps.get(day);dm.put(app,dm.containsKey(app)?dm.get(app)+s.durationSeconds:s.durationSeconds);}List<Map.Entry<String,Long>> e=new ArrayList<>(apps.entrySet());Collections.sort(e,(a,b)->Long.compare(b.getValue(),a.getValue()));for(int i=0;i<Math.min(5,e.size());i++){ReportData.TopItem t=new ReportData.TopItem();t.app=e.get(i).getKey();t.category=cats.get(t.app);t.seconds=e.get(i).getValue();t.percent=r.totalSeconds==0?0:(int)(t.seconds*100/r.totalSeconds);r.topActivities.add(t);}for(Map.Entry<Long,ReportData.DailyItem> de:daily.entrySet()){Map<String,Long> dm=dailyApps.get(de.getKey());String top="";long max=-1;for(Map.Entry<String,Long>x:dm.entrySet())if(x.getValue()>max){max=x.getValue();top=x.getKey();}de.getValue().topApp=top;r.dailyRows.add(de.getValue());}return r;}
    private ActivitySegment fromCursor(Cursor c){ActivitySegment s=new ActivitySegment();s.id=c.getLong(c.getColumnIndexOrThrow("id"));s.startMs=c.getLong(c.getColumnIndexOrThrow("start_ms"));s.endMs=c.getLong(c.getColumnIndexOrThrow("end_ms"));s.durationSeconds=c.getLong(c.getColumnIndexOrThrow("duration_seconds"));s.appName=text(c,"app_name","Unknown");s.packageName=text(c,"package_name","");s.title=text(c,"window_title",s.appName);s.category=text(c,"category",AppUtil.categoryFor(s.appName,s.packageName));s.color=text(c,"color",AppUtil.colorFor(s.appName));s.tag=text(c,"tag","");s.screenshotPath=text(c,"screenshot_path","");s.live="foreground-service-live".equals(text(c,"session_id",""));s.jalaliDate=text(c,"jalali_date",JalaliUtils.dateString(s.startMs));if(s.screenshotPath.isEmpty())s.screenshotPath=findScreenshotPath(s.packageName,s.startMs,s.endMs);return s;}
    private String text(Cursor c,String col,String def){int i=c.getColumnIndex(col);if(i<0||c.isNull(i))return def;String v=c.getString(i);return v==null?def:v;}
}
