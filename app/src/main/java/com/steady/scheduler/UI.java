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
    public static final int GOLD = BuildConfig.REDESIGN ? 0xFF865D16 : 0xFFE8BE65;
    public static final int BG = BuildConfig.REDESIGN ? 0xFFF6F3EC : 0xFF14191F;
    public static final int CARD = BuildConfig.REDESIGN ? 0xFFFFFEFA : 0xFF1D252D;
    public static final int GREEN = BuildConfig.REDESIGN ? 0xFF16685E : 0xFF8FD3C5;
    public static final int LINE = BuildConfig.REDESIGN ? 0xFFDCDDD4 : 0xFF35414B;
    public static final int MUTED = BuildConfig.REDESIGN ? 0xFF616C68 : 0xFFA9B5BE;
    public static final int RED = BuildConfig.REDESIGN ? 0xFFAB3934 : 0xFFFFA7A1;
    public static final int TEXT = BuildConfig.REDESIGN ? 0xFF192B28 : 0xFFF2F1EB;
    public static final int TINT = BuildConfig.REDESIGN ? 0xFFE5EEE7 : 0xFF263B40;
    public static final int BORDER = BuildConfig.REDESIGN ? 0xFFDCDDD4 : 0x38FFFFFF;
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
        border.setStroke(dp(1.0f), BORDER);
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
        b.setTextColor(primary ? (BuildConfig.REDESIGN ? 0xFFFFFFFF : BG) : TEXT);
        GradientDrawable surface=bg(primary ? GREEN : CARD, 12);
        if(!primary)surface.setStroke(dp(1),BORDER);
        b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x26FFFFFF),surface,null));
        b.setBackgroundTintList(null);b.setStateListAnimator(null);
        b.setMinWidth(0);b.setMinimumWidth(0);
        b.setPadding(dp(16),dp(10),dp(16),dp(10));
        b.setGravity(android.view.Gravity.CENTER);b.setIncludeFontPadding(false);
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

    public Button chip(String label, boolean selected, Runnable action) {
        Button button=button(label,false,action);
        button.setTextSize(14);button.setTextColor(selected ? GREEN : MUTED);
        GradientDrawable surface=bg(selected ? TINT : CARD,16);
        surface.setStroke(dp(1), selected ? GREEN : BORDER);
        android.graphics.drawable.InsetDrawable inset=new android.graphics.drawable.InsetDrawable(surface,0,dp(5),0,dp(5));
        button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x228FD3C5),inset,null));
        // A background replacement can reset TextView padding on real Android devices.
        button.setPadding(dp(16),dp(5),dp(16),dp(5));
        button.setMaxLines(1);button.setMinWidth((int)Math.ceil(button.getPaint().measureText(label))+dp(32));
        button.setSelected(selected);return button;
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

    public LinearLayout empty(LinearLayout parent, int icon, String title, String description, boolean border) {
        LinearLayout card=row();card.setGravity(android.view.Gravity.TOP);
        card.setPadding(dp(16),dp(18),dp(16),dp(18));
        GradientDrawable surface=bg(BuildConfig.REDESIGN ? TINT : CARD,16);surface.setStroke(dp(1),BORDER);card.setBackground(surface);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(16);parent.addView(card,params);
        card.addView(new NavIcon(a,icon,GREEN),new LinearLayout.LayoutParams(dp(28),dp(28)));
        LinearLayout copy=col();copy.setPadding(dp(14),0,0,0);weight(card,copy);
        copy.addView(bold(title,18,TEXT));space(copy,6);copy.addView(text(description,14,MUTED));return card;
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
        GradientDrawable field=bg(CARD,12);field.setStroke(dp(1),LINE);
        e.setBackground(field);e.setBackgroundTintList(null);e.setMinHeight(dp(48));
        e.setHintTextColor(MUTED);
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

    public EditText search(LinearLayout parent,String value) {
        EditText query=new EditText(a);query.setText(value);query.setTextSize(15);query.setTextColor(TEXT);query.setHintTextColor(MUTED);
        query.setSingleLine(true);query.setHint("Search your library");query.setContentDescription("Search journal entries");
        query.setInputType(android.text.InputType.TYPE_CLASS_TEXT);query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        GradientDrawable surface=bg(CARD,12);surface.setStroke(dp(1),LINE);query.setBackground(surface);query.setBackgroundTintList(null);
        query.setPadding(dp(16),dp(12),dp(16),dp(12));query.setMinHeight(dp(48));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);lp.bottomMargin=dp(12);parent.addView(query,lp);return query;
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
