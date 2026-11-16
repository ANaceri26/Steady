package com.steady.scheduler;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AlarmActivity extends Activity {
    private boolean demo;
    private Item item;
    private String period;
    private UI ui;

    @Override // android.app.Activity
    public void onCreate(Bundle state) {
        String str;
        super.onCreate(state);
        this.ui = new UI(this);
        this.demo = getIntent().getBooleanExtra("demo", false);
        this.item = Store.get(this).find(getIntent().getLongExtra("id", 0L));
        this.period = getIntent().getStringExtra("period");
        if (this.demo) {
            this.item = new Item();
            this.item.title = "Your next step";
            this.period = "demo";
        }
        if (this.item == null || this.period == null) {
            finish();
            return;
        }
        getWindow().addFlags(2621568);
        LinearLayout linearLayoutCol = this.ui.col();
        linearLayoutCol.setBackgroundColor(UI.BG);
        this.ui.insets(linearLayoutCol);
        setContentView(linearLayoutCol);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        linearLayoutCol.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout root = this.ui.col();
        root.setPadding(this.ui.dp(28.0f), this.ui.dp(24.0f), this.ui.dp(28.0f), this.ui.dp(24.0f));
        root.setGravity(17);
        scroll.addView(root);
        TextView brand = this.ui.bold("S T E A D Y", 14, UI.GREEN);
        brand.setGravity(17);
        root.addView(brand);
        this.ui.space(root, 44);
        TextView icon = this.ui.bold(this.item.goal ? "✎" : "◷", 76, UI.GREEN);
        icon.setGravity(17);
        root.addView(icon);
        UI ui = this.ui;
        if (this.demo) {
            str = "REMINDER PREVIEW";
        } else {
            str = this.item.goal ? "YOUR CHECK-IN IS CALLING" : "YOUR NEXT STEP IS CALLING";
        }
        TextView label = ui.text(str, 12, UI.MUTED);
        label.setGravity(17);
        root.addView(label);
        this.ui.space(root, 18);
        TextView title = this.ui.bold(this.item.title, 32, UI.TEXT);
        title.setGravity(17);
        root.addView(title);
        this.ui.space(root, 12);
        TextView sub = this.ui.text(this.item.goal ? "Accept to start. Reflect when you complete your goal." : "A little progress starts with showing up.", 16, UI.MUTED);
        sub.setGravity(17);
        root.addView(sub);
        this.ui.space(root, 40);
        TextView swipe = this.ui.bold("← Skip       SLIDE       Accept →", 16, UI.TEXT);
        swipe.setGravity(17);
        swipe.setBackground(this.ui.bg(UI.LINE, 30));
        swipe.setPadding(this.ui.dp(8.0f), this.ui.dp(28.0f), this.ui.dp(8.0f), this.ui.dp(28.0f));
        root.addView(swipe, new LinearLayout.LayoutParams(-1, -2));
        swipe.setContentDescription("Swipe right to accept or left to skip. Accessible buttons are below.");
        swipe.setOnTouchListener(new View.OnTouchListener() {
            float x;

            @Override // android.view.View.OnTouchListener
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == 0) {
                    this.x = e.getX();
                    return true;
                }
                if (e.getAction() != 1) {
                    return true;
                }
                float dx = e.getX() - this.x;
                if (Math.abs(dx) > AlarmActivity.this.ui.dp(72.0f)) {
                    AlarmActivity.this.act(dx > 0.0f ? "accept" : "skip");
                } else {
                    v.performClick();
                }
                return true;
            }
        });
        this.ui.space(root, 18);
        LinearLayout buttons = this.ui.row();
        root.addView(buttons);
        this.ui.weight(buttons, this.ui.button("Skip", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                AlarmActivity.this.lambda$onCreate$0();
            }
        }));
        this.ui.weight(buttons, this.ui.button("Accept", true, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                AlarmActivity.this.lambda$onCreate$1();
            }
        }));
        this.ui.action(root, "Snooze · 10 minutes", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                AlarmActivity.this.lambda$onCreate$2();
            }
        });
        this.ui.space(root, 20);
        root.addView(this.ui.text("Accept starts the task. Complete it in Today.", 12, UI.MUTED));
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                AlarmActivity.this.lambda$onCreate$3();
            }
        }, AlarmService.MAX_RING_MS);
    }

    public /* synthetic */ void lambda$onCreate$0() {
        act("skip");
    }

    public /* synthetic */ void lambda$onCreate$1() {
        act("accept");
    }

    public /* synthetic */ void lambda$onCreate$2() {
        act("snooze");
    }

    public /* synthetic */ void lambda$onCreate$3() {
        if (isFinishing()) {
            return;
        }
        finish();
    }

    public void act(String action) {
        if (!this.demo) {
            if (action.equals("snooze") && !Alarms.snooze(this, this.item, this.period)) {
                Toast.makeText(this, "Enable Alarms & reminders in Setup first.", Toast.LENGTH_LONG).show();
                return;
            } else if (!action.equals("snooze")) {
                Alarms.respond(this, this.item, this.period, action);
            }
        }
        if (action.equals("accept")) {
            final Intent open = new Intent(this, (Class<?>) MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if (!this.demo) {
                open.putExtra("open_item", this.item.id).putExtra("period", this.period);
            }
            KeyguardManager km = (KeyguardManager) getSystemService(KeyguardManager.class);
            if (!km.isKeyguardLocked()) {
                startActivity(open);
                finish();
                return;
            } else {
                km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                    @Override // android.app.KeyguardManager.KeyguardDismissCallback
                    public void onDismissSucceeded() {
                        AlarmActivity.this.startActivity(open);
                        AlarmActivity.this.finish();
                    }

                    @Override // android.app.KeyguardManager.KeyguardDismissCallback
                    public void onDismissCancelled() {
                        AlarmActivity.this.finish();
                    }

                    @Override // android.app.KeyguardManager.KeyguardDismissCallback
                    public void onDismissError() {
                        AlarmActivity.this.finish();
                    }
                });
                return;
            }
        }
        finish();
    }
}
