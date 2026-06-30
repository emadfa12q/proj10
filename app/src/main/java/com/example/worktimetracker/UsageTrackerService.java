package com.example.worktimetracker;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

public class UsageTrackerService extends Service {
    static final String ACTION_REFRESH="com.example.worktimetracker.ACTION_REFRESH";
    private static final String CHANNEL_ID="tracker"; private static final int NOTIFICATION_ID=1183;
    private DatabaseHelper db; private final Handler handler=new Handler(Looper.getMainLooper());
    private String currentPackage="", currentAppName=""; private long currentStart=0, currentSegmentId=-1, lastBroadcast=0, lastNotification=0; private String lastText=""; private boolean screenOn=true;
    private final Runnable poller=new Runnable(){@Override public void run(){try{tick();}catch(Exception ignored){}handler.postDelayed(this,TimeUtils.POLL_MS);}};
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){String a=i.getAction();if(Intent.ACTION_SCREEN_OFF.equals(a)){screenOn=false;closeCurrent(System.currentTimeMillis());}else if(Intent.ACTION_SCREEN_ON.equals(a)||Intent.ACTION_USER_PRESENT.equals(a)){screenOn=true;}}};
    @Override public void onCreate(){super.onCreate();db=new DatabaseHelper(this);createChannel();startForeground(NOTIFICATION_ID,buildNotification("در حال آماده‌سازی…"));IntentFilter f=new IntentFilter();f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_USER_PRESENT);registerReceiver(screenReceiver,f);handler.post(poller);} 
    @Override public int onStartCommand(Intent i,int flags,int startId){return START_STICKY;} @Override public IBinder onBind(Intent i){return null;} @Override public void onDestroy(){handler.removeCallbacks(poller);closeCurrent(System.currentTimeMillis());try{unregisterReceiver(screenReceiver);}catch(Exception ignored){}super.onDestroy();}
    private void tick(){if(!"true".equals(db.getSetting("auto_track","true"))){closeCurrent(System.currentTimeMillis());updateNotification("ثبت خودکار خاموش است");return;}if(!hasUsageAccess(this)){closeCurrent(System.currentTimeMillis());updateNotification("Usage Access را فعال کنید");return;}if(!screenOn){closeCurrent(System.currentTimeMillis());updateNotification("صفحه خاموش است");return;}long now=System.currentTimeMillis();long idleLimitMs;try{idleLimitMs=Long.parseLong(db.getSetting("idle_limit_seconds",String.valueOf(TimeUtils.DEFAULT_IDLE_LIMIT_MS/1000)))*1000L;}catch(Exception e){idleLimitMs=TimeUtils.DEFAULT_IDLE_LIMIT_MS;}if(ScreenshotAccessibilityService.isEnabled(this)&&idleLimitMs>0&&currentStart>0&&now-ScreenshotAccessibilityService.lastUserEventMs>idleLimitMs){closeCurrent(now);updateNotification("بیکاری/توقف: "+TimeUtils.fmtHms((now-ScreenshotAccessibilityService.lastUserEventMs)/1000));sendRefresh(true);return;}String pkg=latestForegroundPackage();if(pkg==null||pkg.trim().isEmpty()||pkg.equals(getPackageName())||pkg.equals("com.android.systemui")){closeCurrent(now);updateNotification("در انتظار فعالیت بعدی…");sendRefresh(false);return;}if(currentPackage.isEmpty())openCurrent(pkg,now);else if(!currentPackage.equals(pkg)){closeCurrent(now);openCurrent(pkg,now);}else if(TimeUtils.dayStart(currentStart)!=TimeUtils.dayStart(now)){long mid=TimeUtils.dayStart(now);closeCurrent(mid);openCurrent(pkg,mid);}updateCurrent(now);updateNotification("ثبت زنده: "+currentAppName+" • "+TimeUtils.fmtHms(Math.max(0,(now-currentStart)/1000)));sendRefresh(false);} 
    private String latestForegroundPackage(){UsageStatsManager usm=(UsageStatsManager)getSystemService(USAGE_STATS_SERVICE);if(usm==null)return currentPackage;long now=System.currentTimeMillis();UsageEvents ev=usm.queryEvents(Math.max(0,now-90000),now);UsageEvents.Event e=new UsageEvents.Event();String last=null;while(ev!=null&&ev.hasNextEvent()){ev.getNextEvent(e);int t=e.getEventType();if(t==UsageEvents.Event.MOVE_TO_FOREGROUND||t==UsageEvents.Event.ACTIVITY_RESUMED)last=e.getPackageName();else if((t==UsageEvents.Event.MOVE_TO_BACKGROUND||t==UsageEvents.Event.ACTIVITY_PAUSED)&&e.getPackageName()!=null&&e.getPackageName().equals(last))last=null;}return last!=null?last:currentPackage;}
    private void openCurrent(String pkg,long start){currentPackage=pkg;currentAppName=AppUtil.appLabel(this,pkg);currentStart=start;currentSegmentId=db.upsertLiveSegment(-1,start,Math.max(start+1000,System.currentTimeMillis()),currentAppName,currentPackage);sendRefresh(true);} 
    private void updateCurrent(long end){if(!currentPackage.isEmpty()&&currentStart>0&&end>currentStart)currentSegmentId=db.upsertLiveSegment(currentSegmentId,currentStart,end,currentAppName,currentPackage);} 
    private void closeCurrent(long end){if(!currentPackage.isEmpty()&&currentStart>0&&end>currentStart){currentSegmentId=db.finalizeLiveSegment(currentSegmentId,currentStart,end,currentAppName,currentPackage);sendRefresh(true);}currentPackage="";currentAppName="";currentStart=0;currentSegmentId=-1;}
    private void sendRefresh(boolean force){long now=System.currentTimeMillis();if(!force&&now-lastBroadcast<TimeUtils.UI_REFRESH_MS)return;lastBroadcast=now;sendBroadcast(new Intent(ACTION_REFRESH).setPackage(getPackageName()));}
    private void createChannel(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"WorkTimeTracker",NotificationManager.IMPORTANCE_LOW);NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.createNotificationChannel(ch);}}
    private Notification buildNotification(String text){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL_ID):new Notification.Builder(this);return b.setContentTitle("Work Time Tracker").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_recent_history).setContentIntent(pi).setOngoing(true).setShowWhen(false).build();}
    private void updateNotification(String text){long now=System.currentTimeMillis();if(text!=null&&text.equals(lastText)&&now-lastNotification<3000)return;lastText=text==null?"":text;lastNotification=now;NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(nm!=null)nm.notify(NOTIFICATION_ID,buildNotification(lastText));}
    static boolean hasUsageAccess(Context c){try{AppOpsManager ops=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);ApplicationInfo ai=c.getApplicationInfo();int mode=Build.VERSION.SDK_INT>=29?ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,ai.uid,c.getPackageName()):ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,ai.uid,c.getPackageName());return mode==AppOpsManager.MODE_ALLOWED;}catch(Exception e){return false;}}
    static void openUsageAccessSettings(Context c){Intent i=new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);} 
}
