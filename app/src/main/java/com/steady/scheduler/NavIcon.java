package com.steady.scheduler;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

public final class NavIcon extends View {
    private final int color;
    private final int kind;
    private final Paint p;

    public NavIcon(Context c, int kind, int color) {
        super(c);
        this.p = new Paint(1);
        this.kind = kind;
        this.color = color;
        setImportantForAccessibility(2);
    }

    @Override // android.view.View
    protected void onDraw(Canvas c) {
        Canvas canvas = c;
        super.onDraw(c);
        canvas.save();
        canvas.scale(getWidth() / 24.0f, getHeight() / 24.0f);
        this.p.setColor(this.color);
        this.p.setStrokeWidth(1.7f);
        this.p.setStrokeCap(Paint.Cap.ROUND);
        this.p.setStyle(Paint.Style.STROKE);
        if (this.kind == 0) {
            canvas.drawRoundRect(3.0f, 5.0f, 21.0f, 21.0f, 2.0f, 2.0f, this.p);
            c.drawLine(3.0f, 10.0f, 21.0f, 10.0f, this.p);
            c.drawLine(8.0f, 3.0f, 8.0f, 7.0f, this.p);
            c.drawLine(16.0f, 3.0f, 16.0f, 7.0f, this.p);
            c.drawPoint(8.0f, 14.0f, this.p);
            c.drawPoint(12.0f, 14.0f, this.p);
            c.drawPoint(16.0f, 14.0f, this.p);
            c.drawPoint(8.0f, 18.0f, this.p);
            c.drawPoint(12.0f, 18.0f, this.p);
        } else if (this.kind == 1) {
            Path path = new Path();
            path.moveTo(3.0f, 20.0f);
            path.lineTo(3.0f, 4.0f);
            path.lineTo(9.0f, 4.0f);
            path.lineTo(12.0f, 7.0f);
            path.lineTo(21.0f, 7.0f);
            path.lineTo(21.0f, 20.0f);
            path.close();
            canvas.drawPath(path, this.p);
            canvas.drawLine(3.0f, 11.0f, 21.0f, 11.0f, this.p);
        } else if (this.kind == 2) {
            canvas.drawCircle(12.0f, 12.0f, 9.0f, this.p);
            canvas.drawCircle(12.0f, 12.0f, 5.0f, this.p);
            canvas.drawCircle(12.0f, 12.0f, 1.0f, this.p);
        } else if (this.kind == 3) {
            Path b = new Path();
            b.moveTo(12.0f, 5.0f);
            b.cubicTo(9.0f, 2.0f, 4.0f, 3.0f, 2.0f, 3.0f);
            b.lineTo(2.0f, 20.0f);
            b.cubicTo(5.0f, 19.0f, 9.0f, 19.0f, 12.0f, 21.0f);
            b.cubicTo(15.0f, 19.0f, 19.0f, 19.0f, 22.0f, 20.0f);
            b.lineTo(22.0f, 3.0f);
            b.cubicTo(19.0f, 3.0f, 15.0f, 2.0f, 12.0f, 5.0f);
            b.lineTo(12.0f, 21.0f);
            canvas.drawPath(b, this.p);
        } else if (this.kind == 4) {
            canvas.save();
            canvas.rotate(-35.0f, 12.0f, 12.0f);
            canvas.drawLine(6.0f, 12.0f, 18.0f, 12.0f, this.p);
            c.drawRoundRect(3.0f, 6.0f, 6.0f, 18.0f, 1.0f, 1.0f, this.p);
            c.drawRoundRect(18.0f, 6.0f, 21.0f, 18.0f, 1.0f, 1.0f, this.p);
            c.drawLine(1.0f, 9.0f, 1.0f, 15.0f, this.p);
            c.drawLine(23.0f, 9.0f, 23.0f, 15.0f, this.p);
            c.restore();
        } else if (this.kind == 6) {
            canvas.drawCircle(12.0f, 12.0f, 10.0f, this.p);
            canvas.drawLine(12.0f, 5.0f, 12.0f, 12.0f, this.p);
            c.drawLine(12.0f, 12.0f, 17.0f, 15.0f, this.p);
        } else {
            canvas.drawCircle(12.0f, 12.0f, 6.0f, this.p);
            canvas.drawCircle(12.0f, 12.0f, 2.0f, this.p);
            int i = 0;
            while (i < 8) {
                canvas.save();
                canvas.rotate(i * 45, 12.0f, 12.0f);
                canvas.drawLine(12.0f, 3.0f, 12.0f, 6.0f, this.p);
                c.restore();
                i++;
                canvas = c;
            }
        }
        c.restore();
    }
}
