package com.steady.scheduler;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.service.notification.StatusBarNotification;
import java.time.LocalDate;
import java.time.ZonedDateTime;

public final class Alarms {
    public static final String CHANNEL = "steady_alarm_display_v3";
    private static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

    private Alarms() {
    }

    public static void channel(Context c) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Task alarms", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Ringing task reminders and goal check-ins");
        channel.enableVibration(false);
        channel.setSound(null, null);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        ((NotificationManager) c.getSystemService(NotificationManager.class)).createNotificationChannel(channel);
    }

    public static boolean exact(Context c) {
        return Build.VERSION.SDK_INT < 31 || ((AlarmManager) c.getSystemService(AlarmManager.class)).canScheduleExactAlarms();
    }

    public static boolean fullScreen(Context c) {
        return Build.VERSION.SDK_INT < 34 || ((NotificationManager) c.getSystemService(NotificationManager.class)).canUseFullScreenIntent();
    }

    public static boolean notifications(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(NotificationManager.class);
        NotificationChannel channel = nm.getNotificationChannel(CHANNEL);
        return nm.areNotificationsEnabled() && (channel == null || channel.getImportance() != 0);
    }

    private static Intent intent(Context c, Class<?> cls, long id, String period, String action) {
        return new Intent(c, cls).setAction(action).setData(Uri.parse("steady://" + action + "/" + id + "/" + Uri.encode(period))).putExtra("id", id).putExtra("period", period);
    }

    private static PendingIntent scheduled(Context c, long id, String period, boolean snooze, long at) {
        Intent i = intent(c, AlarmReceiver.class, id, snooze ? period : "next", snooze ? "snooze" : "ring").putExtra("period", period).putExtra("at", at).putExtra("snoozed", snooze);
        return PendingIntent.getBroadcast(c, 0, i, FLAGS);
    }

    private static boolean at(Context c, Item item, String period, long time, boolean snooze) {
        if (!exact(c)) {
            return false;
        }
        PendingIntent alarm = scheduled(c, item.id, period, snooze, time);
        PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, (Class<?>) MainActivity.class), FLAGS);
        try {
            ((AlarmManager) c.getSystemService(AlarmManager.class)).setAlarmClock(new AlarmManager.AlarmClockInfo(time, show), alarm);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    public static void schedule(Context c, Item i) {
        ZonedDateTime next;
        ((AlarmManager) c.getSystemService(AlarmManager.class)).cancel(scheduled(c, i.id, "", false, 0L));
        if (i.enabled && i.scheduled) {
            if (exact(c)) {
                ZonedDateTime now = ZonedDateTime.now();
                int tries = 0;
                while (tries < 3 && (next = Rules.next(LocalDate.parse(i.start), i.minute, i.repeat, now)) != null) {
                    String key = i.key(next.toLocalDate());
                    String state = Store.get(c).status(i.id, key);
                    if (!state.equals("done") && !state.equals("skipped") && !state.equals("started")) {
                        at(c, i, key, next.toInstant().toEpochMilli(), false);
                        return;
                    } else {
                        now = next.plusSeconds(1L);
                        tries++;
                        i = i;
                    }
                }
            }
        }
    }

    public static void restore(Context c) {
        Context c2;
        channel(c);
        Store store = Store.get(c);
        for (Item i : store.items()) {
            schedule(c, i);
        }
        for (String[] s : store.snoozes()) {
            Item item = store.find(Long.parseLong(s[0]));
            long time = Long.parseLong(s[2]);
            if (item != null && item.enabled) {
                if (time < System.currentTimeMillis()) {
                    store.clearSnooze(item.id, s[1]);
                    c2 = c;
                } else {
                    c2 = c;
                    at(c2, item, s[1], time, true);
                }
                c = c2;
            }
        }
    }

    public static String tag(long id, String period) {
        return "item:" + id + ":" + period;
    }

    public static void quiet(Context c, long id, String period) {
        AlarmService.dismiss(id, period);
        ((NotificationManager) c.getSystemService(NotificationManager.class)).cancel(tag(id, period), 1);
        ((AlarmManager) c.getSystemService(AlarmManager.class)).cancel(scheduled(c, id, period, true, 0L));
        Store.get(c).clearSnooze(id, period);
    }

    public static void cancel(Context c, Item item) {
        AlarmService.dismissItem(item.id);
        ((AlarmManager) c.getSystemService(AlarmManager.class)).cancel(scheduled(c, item.id, "", false, 0L));
        for (StatusBarNotification n : ((NotificationManager) c.getSystemService(NotificationManager.class)).getActiveNotifications()) {
            if (n.getTag() != null && n.getTag().startsWith("item:" + item.id + ":")) {
                ((NotificationManager) c.getSystemService(NotificationManager.class)).cancel(n.getTag(), n.getId());
            }
        }
        for (String[] s : Store.get(c).snoozes()) {
            if (Long.parseLong(s[0]) == item.id) {
                quiet(c, item.id, s[1]);
            }
        }
    }

    static void cancelTransferred(Context c, java.util.List<Item> oldItems, java.util.List<String[]> oldSnoozes) {
        AlarmManager manager = c.getSystemService(AlarmManager.class);
        for (Item item : oldItems) {
            AlarmService.dismissItem(item.id);
            manager.cancel(scheduled(c, item.id, "", false, 0L));
        }
        for (String[] snooze : oldSnoozes)
            manager.cancel(scheduled(c, Long.parseLong(snooze[0]), snooze[1], true, 0L));
        c.getSystemService(NotificationManager.class).cancelAll();
    }

    public static boolean snooze(Context c, Item item, String period) {
        if (!exact(c)) {
            return false;
        }
        long time = System.currentTimeMillis() + AlarmService.MAX_RING_MS;
        if (!at(c, item, period, time, true)) {
            return false;
        }
        AlarmService.dismiss(item.id, period);
        ((NotificationManager) c.getSystemService(NotificationManager.class)).cancel(tag(item.id, period), 1);
        Store.get(c).snooze(item.id, period, time);
        Store.get(c).status(item.id, period, "snoozed");
        return true;
    }

    public static void respond(Context c, Item i, String period, String action) {
        if ("snooze".equals(action)) {
            snooze(c, i, period);
        } else {
            quiet(c, i.id, period);
            Store.get(c).status(i.id, period, "accept".equals(action) ? "started" : "skipped");
        }
    }

    public static void ring(Context c, Item item, String period) {
        channel(c);
        if (notifications(c)) {
            try {
                c.startForegroundService(new Intent(c, (Class<?>) AlarmService.class).putExtra("id", item.id).putExtra("period", period));
            } catch (RuntimeException e) {
                c.getSharedPreferences("alarm_sound", 0).edit().putString("last_error", "Android blocked alarm playback. Recheck alarm permissions and battery restrictions, then test again.").apply();
                ((NotificationManager) c.getSystemService(NotificationManager.class)).notify(tag(item.id, period), 1, notification(c, item, period));
            }
        }
    }

    public static Notification notification(Context c, Item item, String period) {
        String str;
        Intent open = intent(c, AlarmActivity.class, item.id, period, "open");
        PendingIntent screen = PendingIntent.getActivity(c, 0, open, FLAGS);
        Notification.Builder b = new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_alarm).setContentTitle(item.goal ? "Time to check in" : "Your next step is calling").setContentText(item.title).setCategory("alarm").setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(screen).setFullScreenIntent(screen, true).setOngoing(true).setAutoCancel(false).setTimeoutAfter(AlarmService.MAX_RING_MS).setPublicVersion(new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_alarm).setContentTitle("Steady reminder").build());
        String[] strArr = {"accept", "skip", "snooze"};
        int i = 0;
        while (i < 3) {
            String action = strArr[i];
            int i2 = i;
            PendingIntent respond = PendingIntent.getBroadcast(c, 0, intent(c, ActionReceiver.class, item.id, period, action), FLAGS);
            if (action.equals("accept")) {
                str = "Accept";
            } else {
                str = action.equals("skip") ? "Skip" : "Snooze 10m";
            }
            b.addAction(new Notification.Action.Builder((Icon) null, str, respond).build());
            i = i2 + 1;
        }
        return b.build();
    }
}
