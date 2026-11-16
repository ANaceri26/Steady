package com.steady.scheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ActionReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context c, Intent intent) {
        Item item = Store.get(c).find(intent.getLongExtra("id", 0L));
        String period = intent.getStringExtra("period");
        if (item == null || period == null) {
            return;
        }
        Alarms.respond(c, item, period, intent.getAction());
    }
}
