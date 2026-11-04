package pl.kuba.zegarektest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Wykres: domyślnie doba (oś X w minutach 0–1440), albo dni tygodnia/miesiąca (setSlots). */
public class ChartView extends View {

    public static class Series {
        final List<float[]> pts;
        final int color;
        Series(List<float[]> pts, int color) { this.pts = pts; this.color = color; }
    }

    private final List<Series> series = new ArrayList<>();
    private float min = 0, max = 100;
    private boolean bars = false;
    private float xMax = 1440, barSlots = 48, breakGap = 15;
    private String[] labels = {"0:00", "6:00", "12:00", "18:00", "24:00"};
    private boolean centeredLabels = false;
    private float goal = -1;

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint goalP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public ChartView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        grid.setColor(Color.parseColor("#EDF1F7"));
        grid.setStrokeWidth(d);
        text.setColor(Color.parseColor("#8A97AB"));
        text.setTextSize(11 * d);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.8f * d);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        dot.setStyle(Paint.Style.FILL);
        goalP.setStyle(Paint.Style.STROKE);
        goalP.setStrokeWidth(1.5f * d);
        goalP.setColor(Color.parseColor("#22C55E"));
        goalP.setPathEffect(new DashPathEffect(new float[]{8 * d, 6 * d}, 0));
    }

    public void setBars(boolean b) { bars = b; }

    /** Tryb dni: n słupków/punktów, punkt i ma x = i + 0.5. */
    public void setSlots(String[] lbl) {
        xMax = lbl.length;
        barSlots = lbl.length;
        breakGap = 1.5f;
        labels = lbl;
        centeredLabels = true;
    }

    public void setGoal(float g) { goal = g; }

    /** Powrót do trybu doby. */
    public void setDayMode() {
        xMax = 1440;
        barSlots = 48;
        breakGap = 15;
        labels = new String[]{"0:00", "6:00", "12:00", "18:00", "24:00"};
        centeredLabels = false;
    }

    public void setData(List<Series> s, float min, float max, String unit) {
        series.clear();
        series.addAll(s);
        this.min = min;
        this.max = max <= min ? min + 1 : max;
        invalidate();
    }

    public static Series series(List<float[]> pts, int color) { return new Series(pts, color); }

    private float y(float v, float top, float bottom) {
        float y = bottom - (bottom - top) * (v - min) / (max - min);
        return Math.max(top, Math.min(bottom, y));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float left = 34 * d, right = w - 8 * d, top = 8 * d, bottom = h - 18 * d;
        int n = labels.length;
        for (int i = 0; i < n; i++) {
            float x = centeredLabels ? left + (right - left) * (i + 0.5f) / n : left + (right - left) * i / (float) (n - 1);
            if (!centeredLabels) c.drawLine(x, top, x, bottom, grid);
            String lbl = labels[i];
            if (lbl == null || lbl.isEmpty()) continue;
            float tw = text.measureText(lbl);
            c.drawText(lbl, Math.min(Math.max(x - tw / 2, 0), w - tw), h - 4 * d, text);
        }
        for (int i = 0; i <= 2; i++) {
            float yy = bottom - (bottom - top) * i / 2f;
            c.drawLine(left, yy, right, yy, grid);
            float v = min + (max - min) * i / 2f;
            String t = Math.abs(max - min) < 10 ? String.format(java.util.Locale.US, "%.1f", v) : String.valueOf(Math.round(v));
            c.drawText(t, 2 * d, yy + 4 * d, text);
        }
        boolean any = false;
        for (Series s : series) {
            if (s.pts.isEmpty()) continue;
            any = true;
            line.setColor(s.color);
            dot.setColor(s.color);
            if (bars) {
                float bw = (right - left) / barSlots * 0.65f;
                float half = xMax / barSlots / 2f;
                for (float[] pt : s.pts) {
                    float cx = centeredLabels ? pt[0] : pt[0] + half;
                    float x = left + (right - left) * cx / xMax;
                    c.drawRoundRect(new RectF(x - bw / 2, y(pt[1], top, bottom), x + bw / 2, bottom), 3 * d, 3 * d, dot);
                }
                continue;
            }
            Path p = new Path();
            float lastX = -1e9f;
            boolean first = true;
            for (float[] pt : s.pts) {
                float x = left + (right - left) * pt[0] / xMax;
                float yy = y(pt[1], top, bottom);
                if (first || pt[0] - lastX > breakGap) p.moveTo(x, yy); else p.lineTo(x, yy);
                if (s.pts.size() < 40) c.drawCircle(x, yy, 3f * d, dot);
                lastX = pt[0];
                first = false;
            }
            c.drawPath(p, line);
        }
        if (goal > min && goal < max) {
            float gy = y(goal, top, bottom);
            c.drawLine(left, gy, right, gy, goalP);
        }
        if (!any) {
            String t = "brak danych";
            c.drawText(t, (w - text.measureText(t)) / 2, (top + bottom) / 2, text);
        }
    }
}
