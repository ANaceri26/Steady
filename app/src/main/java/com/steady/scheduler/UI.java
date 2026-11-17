package com.steady.scheduler;

import android.app.Activity;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class UI {
    public static final int GOLD = 0xFFE8BE65;
    public static final int BG = -15722733;
    public static final int CARD = -15129827;
    public static final int GREEN = -4919436;
    public static final int LINE = -13418184;
    public static final int MUTED = -5654611;
    public static final int RED = -21853;
    public static final int TEXT = -920078;
    public final Activity a;

    public UI(Activity a) {
        this.a = a;
    }

    public int dp(float v) {
        return (int) ((this.a.getResources().getDisplayMetrics().density * v) + 0.5f);
    }

    public GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    public LinearLayout col() {
        LinearLayout v = new LinearLayout(this.a);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    public LinearLayout row() {
        LinearLayout v = new LinearLayout(this.a);
        v.setGravity(16);
        return v;
    }

    public TextView text(String s, int size, int color) {
        TextView v = new TextView(this.a);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setLineSpacing(dp(3.0f), 1.0f);
        return v;
    }

    public TextView bold(String s, int size, int color) {
        TextView v = text(s, size, color);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }

    public void space(LinearLayout p, int h) {
        View v = new View(this.a);
        p.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
    }

    public LinearLayout card(LinearLayout p) {
        LinearLayout v = col();
        v.setPadding(dp(18.0f), dp(18.0f), dp(18.0f), dp(18.0f));
        GradientDrawable border = bg(CARD, 18);
        border.setStroke(dp(1.0f), LINE);
        v.setBackground(border);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12.0f);
        p.addView(v, lp);
        return v;
    }

    public Button button(String s, boolean primary, final Runnable r) {
        Button b = new Button(this.a);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15.0f);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(primary ? BG : TEXT);
        b.setBackground(bg(primary ? GREEN : LINE, 14));
        b.setPadding(dp(14.0f), dp(8.0f), dp(14.0f), dp(8.0f));
        b.setMinHeight(dp(48.0f));
        b.setMinimumHeight(dp(48.0f));
        b.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                r.run();
            }
        });
        return b;
    }

    public void action(LinearLayout p, String s, boolean primary, Runnable r) {
        Button b = button(s, primary, r);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8.0f);
        p.addView(b, lp);
    }

    public void eyebrow(LinearLayout p, String s) {
        TextView t = bold(s, 11, MUTED);
        t.setLetterSpacing(0.12f);
        p.addView(t);
        space(p, 12);
    }

    public Button link(String s, Runnable r) {
        Button b = button(s, false, r);
        b.setTextColor(GREEN);
        b.setBackgroundColor(0);
        return b;
    }

    public void primary(LinearLayout p, String s, Runnable r) {
        Button b = button(s, true, r);
        b.setPadding(dp(18.0f), dp(12.0f), dp(18.0f), dp(12.0f));
        p.addView(b, new LinearLayout.LayoutParams(-2, -2));
    }

    public LinearLayout empty(LinearLayout linearLayout, int icon, String title, String description, boolean border) {
        LinearLayout c = col();
        c.setGravity(17);
        c.setPadding(dp(20.0f), dp(36.0f), dp(20.0f), dp(36.0f));
        if (border) {
            GradientDrawable d = bg(BG, 20);
            d.setStroke(dp(1.0f), LINE);
            c.setBackground(d);
        }
        linearLayout.addView(c, new LinearLayout.LayoutParams(-1, -2));
        c.addView(new NavIcon(this.a, icon, GREEN), new LinearLayout.LayoutParams(dp(36.0f), dp(36.0f)));
        space(c, 24);
        TextView t = bold(title, 22, TEXT);
        t.setGravity(17);
        c.addView(t);
        space(c, 16);
        TextView desc = text(description, 15, MUTED);
        desc.setGravity(17);
        c.addView(desc);
        space(c, 20);
        return c;
    }

    public void weight(LinearLayout row, View view) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1.0f);
        lp.setMargins(dp(3.0f), 0, dp(3.0f), 0);
        row.addView(view, lp);
    }

    public EditText input(LinearLayout p, String label, String value, boolean multiline) {
        p.addView(text(label, 13, MUTED));
        EditText e = new EditText(this.a);
        e.setText(value);
        e.setTextColor(TEXT);
        e.setTextSize(16.0f);
        e.setSingleLine(!multiline);
        e.setBackground(bg(BG, 9));
        e.setPadding(dp(12.0f), dp(12.0f), dp(12.0f), dp(12.0f));
        if (multiline) {
            e.setMinLines(3);
            e.setGravity(48);
        }
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
        ep.topMargin = dp(7.0f);
        p.addView(e, ep);
        space(p, 12);
        return e;
    }

    static /* synthetic */ WindowInsets lambda$insets$1(View v, WindowInsets w) {
        if (Build.VERSION.SDK_INT < 30) return w;
        Insets i = w.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
        v.setPadding(i.left, i.top, i.right, i.bottom);
        return w;
    }

    public void insets(View root) {
        if (Build.VERSION.SDK_INT >= 30) {
            this.a.getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override // android.view.View.OnApplyWindowInsetsListener
                public final WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                    return UI.lambda$insets$1(view, windowInsets);
                }
            });
        }
    }
}
