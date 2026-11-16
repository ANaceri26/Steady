package com.steady.scheduler;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class AlarmService extends Service {
    private static final int FOREGROUND_ID = 4103;
    public static final long MAX_RING_MS = 600000;
    private static AlarmService running;
    private AudioFocusRequest focus;
    private MediaPlayer player;
    private Vibrator vibrator;
    private final LinkedHashMap<String, Ring> rings = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable expire = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            for (Ring r : new ArrayList<>(AlarmService.this.rings.values())) {
                if (SystemClock.elapsedRealtime() >= r.until) {
                    Store.get(AlarmService.this).status(r.item.id, r.period, "missed");
                    AlarmService.this.remove(r.item.id, r.period);
                }
            }
            if (AlarmService.this.rings.isEmpty()) {
                return;
            }
            AlarmService.this.handler.postDelayed(this, 1000L);
        }
    };

    private static class Ring {
        final Item item;
        final String period;
        final long until = SystemClock.elapsedRealtime() + AlarmService.MAX_RING_MS;

        Ring(Item i, String p) {
            this.item = i;
            this.period = p;
        }
    }

    public static void dismiss(long id, String period) {
        if (running != null) {
            running.remove(id, period);
        }
    }

    public static void dismissItem(long id) {
        if (running != null) {
            for (Ring r : new ArrayList<>(running.rings.values())) {
                if (r.item.id == id) {
                    running.remove(id, r.period);
                }
            }
        }
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        running = this;
        Alarms.channel(this);
        this.vibrator = (Vibrator) getSystemService(Vibrator.class);
    }

    @Override // android.app.Service
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Item item = Store.get(this).find(intent.getLongExtra("id", 0L));
        String period = intent.getStringExtra("period");
        if (item == null || period == null || !item.enabled || !item.scheduled) {
            if (this.rings.isEmpty()) {
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }
        String state = Store.get(this).status(item.id, period);
        if (!state.equals("ringing")) {
            if (this.rings.isEmpty()) {
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }
        String key = Alarms.tag(item.id, period);
        boolean first = this.rings.isEmpty();
        this.rings.putIfAbsent(key, new Ring(item, period));
        Ring active = this.rings.values().iterator().next();
        startForeground(FOREGROUND_ID, Alarms.notification(this, active.item, active.period));
        if (!first && !key.equals(Alarms.tag(active.item.id, active.period))) {
            ((NotificationManager) getSystemService(NotificationManager.class)).notify(key, 1, Alarms.notification(this, item, period));
        }
        if (first) {
            startAudio(false);
            if (this.vibrator != null && this.vibrator.hasVibrator()) {
                this.vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 600}, 0), AlarmSound.attributes());
            }
        }
        this.handler.removeCallbacks(this.expire);
        this.handler.postDelayed(this.expire, 1000L);
        return START_NOT_STICKY;
    }

    private void startAudio(final boolean fallback) {
        if (this.player != null) {
            this.player.release();
            this.player = null;
        }
        try {
            if (this.focus == null) {
                this.focus = new AudioFocusRequest.Builder(2).setAudioAttributes(AlarmSound.attributes()).setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                    @Override // android.media.AudioManager.OnAudioFocusChangeListener
                    public final void onAudioFocusChange(int i) {
                        AlarmService.this.lambda$startAudio$0(i);
                    }
                }).build();
                ((AudioManager) getSystemService(AudioManager.class)).requestAudioFocus(this.focus);
            }
            MediaPlayer mp = new MediaPlayer();
            this.player = mp;
            mp.setAudioAttributes(AlarmSound.attributes());
            mp.setWakeMode(this, 1);
            mp.setDataSource(this, fallback ? AlarmSound.builtIn(this) : AlarmSound.uri(this));
            mp.setLooping(true);
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override // android.media.MediaPlayer.OnPreparedListener
                public final void onPrepared(MediaPlayer mediaPlayer) {
                    AlarmService.this.lambda$startAudio$1(mediaPlayer);
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override // android.media.MediaPlayer.OnErrorListener
                public final boolean onError(MediaPlayer mediaPlayer, int i, int i2) {
                    return AlarmService.this.lambda$startAudio$3(fallback, mediaPlayer, i, i2);
                }
            });
            mp.prepareAsync();
        } catch (Exception e) {
            if (!fallback) {
                startAudio(true);
            } else {
                getSharedPreferences("alarm_sound", 0).edit().putString("last_error", "Sound could not play. Try the built-in tone and test again.").apply();
            }
        }
    }

    public /* synthetic */ void lambda$startAudio$0(int change) {
        if (this.player != null) {
            if (change == -1 || change == -2) {
                if (this.player.isPlaying()) {
                    this.player.pause();
                }
            } else if (change == 1) {
                this.player.start();
            }
        }
    }

    public /* synthetic */ void lambda$startAudio$1(MediaPlayer p) {
        if (this.player != p || this.rings.isEmpty()) {
            return;
        }
        p.start();
    }

    public /* synthetic */ void lambda$startAudio$2() {
        if (this.rings.isEmpty()) {
            return;
        }
        startAudio(true);
    }

    public /* synthetic */ boolean lambda$startAudio$3(boolean fallback, MediaPlayer p, int what, int extra) {
        if (fallback) {
            getSharedPreferences("alarm_sound", 0).edit().putString("last_error", "Sound playback failed. Check alarm volume and test again.").apply();
            return true;
        }
        this.handler.post(new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                AlarmService.this.lambda$startAudio$2();
            }
        });
        return true;
    }

    public void remove(long id, String period) {
        this.rings.remove(Alarms.tag(id, period));
        ((NotificationManager) getSystemService(NotificationManager.class)).cancel(Alarms.tag(id, period), 1);
        if (!this.rings.isEmpty()) {
            Ring next = this.rings.values().iterator().next();
            ((NotificationManager) getSystemService(NotificationManager.class)).cancel(Alarms.tag(next.item.id, next.period), 1);
            ((NotificationManager) getSystemService(NotificationManager.class)).notify(FOREGROUND_ID, Alarms.notification(this, next.item, next.period));
        } else {
            stopAudio();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void stopAudio() {
        this.handler.removeCallbacksAndMessages(null);
        if (this.player != null) {
            this.player.release();
            this.player = null;
        }
        if (this.vibrator != null) {
            this.vibrator.cancel();
        }
        if (this.focus != null) {
            ((AudioManager) getSystemService(AudioManager.class)).abandonAudioFocusRequest(this.focus);
            this.focus = null;
        }
    }

    @Override // android.app.Service
    public void onDestroy() {
        stopAudio();
        for (Ring r : this.rings.values()) {
            ((NotificationManager) getSystemService(NotificationManager.class)).cancel(Alarms.tag(r.item.id, r.period), 1);
            Store.get(this).status(r.item.id, r.period, "missed");
        }
        this.rings.clear();
        if (running == this) {
            running = null;
        }
        super.onDestroy();
    }
}
