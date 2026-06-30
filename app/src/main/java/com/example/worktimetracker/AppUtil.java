package com.example.worktimetracker;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import java.security.MessageDigest;
import java.util.Locale;

final class AppUtil {
    private static final String[] PALETTE={"#3b82f6","#f97316","#a855f7","#22c55e","#eab308","#06b6d4","#ef4444","#14b8a6","#8b5cf6","#f59e0b"};
    private AppUtil(){}
    static String appLabel(Context c,String pkg){try{PackageManager pm=c.getPackageManager();ApplicationInfo ai=pm.getApplicationInfo(pkg,0);CharSequence l=pm.getApplicationLabel(ai);return l==null?pkg:l.toString();}catch(Exception e){return pkg==null?"Unknown":pkg;}}
    static String categoryFor(String app,String pkg){String a=((app==null?"":app)+" "+(pkg==null?"":pkg)).toLowerCase(Locale.US);if(a.contains("chrome")||a.contains("browser")||a.contains("firefox"))return"Web";if(a.contains("telegram")||a.contains("whatsapp")||a.contains("messenger")||a.contains("eitaa"))return"Chat";if(a.contains("youtube")||a.contains("video")||a.contains("music"))return"Media";if(a.contains("docs")||a.contains("pdf")||a.contains("office"))return"Documents";if(a.contains("settings")||a.contains("systemui"))return"System";return"Other";}
    static String colorFor(String app){String key=app==null?"unknown":app.toLowerCase(Locale.US);try{byte[]d= MessageDigest.getInstance("MD5").digest(key.getBytes("UTF-8"));int v=Math.abs(((d[0]&255)<<24)|((d[1]&255)<<16)|((d[2]&255)<<8)|(d[3]&255));return PALETTE[v%PALETTE.length];}catch(Exception e){return PALETTE[Math.abs(key.hashCode())%PALETTE.length];}}
    static int parseColor(String c,int f){try{return Color.parseColor(c);}catch(Exception e){return f;}}
}
