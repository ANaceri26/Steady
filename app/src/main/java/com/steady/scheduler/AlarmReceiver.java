package com.steady.scheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override // android.content.BroadcastReceiver
    public void onReceive(Context c, Intent intent) {
        String period;
        Store store = Store.get(c);
        Item item = store.find(intent.getLongExtra("id", 0L));
        if (item == null || !item.enabled || !item.scheduled || (period = intent.getStringExtra("period")) == null) {
            return;
        }
        boolean snoozed = intent.getBooleanExtra("snoozed", false);
        if (snoozed) {
            store.clearSnooze(item.id, period);
        } else {
            Alarms.schedule(c, item);
        }
        String state = store.status(item.id, period);
        if (state.equals("done") || state.equals("skipped") || state.equals("started") || System.currentTimeMillis() - intent.getLongExtra("at", 0L) > 900000) {
            return;
        }
        store.status(item.id, period, "ringing");
        Alarms.ring(c, item, period);
    }
}
