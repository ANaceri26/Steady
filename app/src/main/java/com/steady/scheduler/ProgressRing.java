package com.steady.scheduler;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

public final class ProgressRing extends View {
    private final int done;
    private final Paint p;
    private final int total;

    public ProgressRing(Context c, int done, int total) {
        super(c);
        this.p = new Paint(1);
        this.done = done;
        this.total = total;
        setContentDescription(done + " of " + total + " completed");
    }

    @Override // android.view.View
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float size = Math.min(getWidth(), getHeight());
        float w = 0.09f * size;
        RectF oval = new RectF(w, w, size - w, size - w);
        this.p.setStyle(Paint.Style.STROKE);
        this.p.setStrokeWidth(w);
        this.p.setColor(UI.LINE);
        c.drawOval(oval, this.p);
        this.p.setColor(UI.GREEN);
        float progress = this.total == 0 ? 0.0f : this.done / this.total;
        c.drawArc(oval, -90.0f, progress * 360.0f, false, this.p);
        this.p.setStyle(Paint.Style.FILL);
        this.p.setTextAlign(Paint.Align.CENTER);
        this.p.setColor(UI.TEXT);
        this.p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        this.p.setTextSize(0.24f * size);
        c.drawText(Math.round(100.0f * progress) + "%", size / 2.0f, (size / 2.0f) - ((this.p.ascent() + this.p.descent()) / 2.0f), this.p);
    }
}
