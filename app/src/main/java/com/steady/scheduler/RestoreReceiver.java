package com.steady.scheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RestoreReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if ("android.intent.action.BOOT_COMPLETED".equals(a) || "android.intent.action.MY_PACKAGE_REPLACED".equals(a) || "android.intent.action.TIME_SET".equals(a) || "android.intent.action.TIMEZONE_CHANGED".equals(a) || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(a)) {
            Alarms.restore(c);
        }
    }
}
