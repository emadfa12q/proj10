package com.example.worktimetracker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ScreenshotAccessibilityService extends AccessibilityService {
    static long lastUserEventMs = System.currentTimeMillis();
    private static final long CAPTURE_DELAY_MS=350L, MIN_CAPTURE_INTERVAL_MS=1200L;
    private final Handler handler=new Handler(Looper.getMainLooper()); private final Map<String,Long> lastByPkg=new HashMap<>(); private DatabaseHelper db; private String pendingPkg=""; private long pendingMs=0;
    private final Runnable captureRunnable=new Runnable(){@Override public void run(){capturePending();}};
    @Override public void onServiceConnected(){super.onServiceConnected();db=new DatabaseHelper(this);AccessibilityServiceInfo info=getServiceInfo();if(info!=null){info.eventTypes=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED|AccessibilityEvent.TYPE_WINDOWS_CHANGED|AccessibilityEvent.TYPE_VIEW_CLICKED|AccessibilityEvent.TYPE_VIEW_SCROLLED|AccessibilityEvent.TYPE_VIEW_FOCUSED|AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED|AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;info.feedbackType=AccessibilityServiceInfo.FEEDBACK_GENERIC;info.notificationTimeout=100;setServiceInfo(info);}}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){if(e==null)return;long now=System.currentTimeMillis();lastUserEventMs=now;if(db==null)db=new DatabaseHelper(this);int type=e.getEventType();boolean windowEvent=type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||type==AccessibilityEvent.TYPE_WINDOWS_CHANGED;if(!windowEvent)return;if(!"true".equals(db.getSetting("auto_screenshot","true")))return;if(Build.VERSION.SDK_INT<Build.VERSION_CODES.R)return;CharSequence p=e.getPackageName();if(p==null)return;String pkg=p.toString();if(pkg.isEmpty()||pkg.equals(getPackageName())||pkg.equals("com.android.systemui"))return;Long last=lastByPkg.get(pkg);if(last!=null&&now-last<MIN_CAPTURE_INTERVAL_MS)return;pendingPkg=pkg;pendingMs=now;handler.removeCallbacks(captureRunnable);handler.postDelayed(captureRunnable,CAPTURE_DELAY_MS);} 
    @Override public void onInterrupt(){}
    private void capturePending(){final String pkg=pendingPkg;final long ms=pendingMs<=0?System.currentTimeMillis():pendingMs;if(pkg==null||pkg.isEmpty()||Build.VERSION.SDK_INT<Build.VERSION_CODES.R)return;lastByPkg.put(pkg,System.currentTimeMillis());takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){@Override public void onSuccess(AccessibilityService.ScreenshotResult r){HardwareBuffer b=null;try{b=r.getHardwareBuffer();Bitmap bmp=Bitmap.wrapHardwareBuffer(b,r.getColorSpace()==null? ColorSpace.get(ColorSpace.Named.SRGB):r.getColorSpace());if(bmp==null)return;Bitmap scaled=scale(bmp);String path=save(scaled,pkg,ms);if(!path.isEmpty()){if(db==null)db=new DatabaseHelper(ScreenshotAccessibilityService.this);db.insertScreenshot(ms,AppUtil.appLabel(ScreenshotAccessibilityService.this,pkg),pkg,path);sendBroadcast(new Intent(UsageTrackerService.ACTION_REFRESH).setPackage(getPackageName()));}if(scaled!=bmp&&!scaled.isRecycled())scaled.recycle();}catch(Exception ignored){}finally{if(b!=null)b.close();}}@Override public void onFailure(int errorCode){}});} 
    private Bitmap scale(Bitmap src){int w=src.getWidth(),h=src.getHeight(),max=720;if(w<=max)return src.copy(Bitmap.Config.ARGB_8888,false);return Bitmap.createScaledBitmap(src,max,Math.max(1,(int)(h*(float)max/w)),true);} 
    private String save(Bitmap bitmap,String pkg,long ms){try{File dir=new File(getFilesDir(),"screenshots");if(!dir.exists())dir.mkdirs();File f=new File(dir,String.format(Locale.US,"%d_%s.jpg",ms,pkg.replaceAll("[^a-zA-Z0-9._-]","_")));FileOutputStream out=new FileOutputStream(f);bitmap.compress(Bitmap.CompressFormat.JPEG,72,out);out.close();return f.getAbsolutePath();}catch(Exception e){return"";}}
    static boolean isEnabled(Context c){try{String en=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);if(en==null)return false;String svc=new ComponentName(c,ScreenshotAccessibilityService.class).flattenToString();TextUtils.SimpleStringSplitter sp=new TextUtils.SimpleStringSplitter(':');sp.setString(en);while(sp.hasNext())if(svc.equalsIgnoreCase(sp.next()))return true;}catch(Exception ignored){}return false;}
    static void openAccessibilitySettings(Context c){Intent i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);} 
}
