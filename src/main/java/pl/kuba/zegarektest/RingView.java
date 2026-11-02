package pl.kuba.zegarektest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;

/** Pierścień postępu (np. kroki względem celu) z liczbą w środku. */
public class RingView extends View {
    private float progress = 0;
    private String big = "–", small = "";
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tBig = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tSmall = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public RingView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(14 * d);
        track.setColor(Color.parseColor("#E3EAF3"));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(14 * d);
        arc.setStrokeCap(Paint.Cap.ROUND);
        tBig.setColor(Color.parseColor("#0D1B2A"));
        tBig.setTextAlign(Paint.Align.CENTER);
        tBig.setFakeBoldText(true);
        tSmall.setColor(Color.parseColor("#6B7A90"));
        tSmall.setTextAlign(Paint.Align.CENTER);
    }

    public void set(float p, String big, String small) {
        progress = Math.max(0, Math.min(1, p));
        this.big = big;
        this.small = small;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h) - 16 * d;
        float cx = w / 2, cy = h / 2;
        RectF r = new RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        c.drawArc(r, 0, 360, false, track);
        arc.setShader(new SweepGradient(cx, cy,
                new int[]{Color.parseColor("#00C6FF"), Color.parseColor("#0072FF"), Color.parseColor("#7B2FF7"), Color.parseColor("#00C6FF")}, null));
        c.save();
        c.rotate(-90, cx, cy);
        c.drawArc(r, 0, 360 * progress, false, arc);
        c.restore();
        tBig.setTextSize(size * 0.2f);
        tSmall.setTextSize(size * 0.085f);
        c.drawText(big, cx, cy + size * 0.05f, tBig);
        c.drawText(small, cx, cy + size * 0.2f, tSmall);
    }
}
