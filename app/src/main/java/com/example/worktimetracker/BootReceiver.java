package com.example.worktimetracker;
import android.content.BroadcastReceiver;import android.content.Context;import android.content.Intent;import android.os.Build;
public class BootReceiver extends BroadcastReceiver{ @Override public void onReceive(Context c,Intent i){DatabaseHelper db=new DatabaseHelper(c);if(!"true".equals(db.getSetting("auto_track","true")))return;Intent s=new Intent(c,UsageTrackerService.class);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(s);else c.startService(s);} }
