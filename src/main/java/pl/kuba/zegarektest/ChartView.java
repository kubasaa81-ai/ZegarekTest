package pl.kuba.zegarektest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Prosty wykres dnia: oś X 0–24 h, jedna lub dwie serie punktów (minuta dnia, wartość). */
public class ChartView extends View {

    public static class Series {
        final List<float[]> pts;
        final int color;
        Series(List<float[]> pts, int color) { this.pts = pts; this.color = color; }
    }

    private final List<Series> series = new ArrayList<>();
    private float min = 0, max = 100;
    private String unit = "";
    private boolean bars = false;

    public void setBars(boolean b) { bars = b; }
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public ChartView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        grid.setColor(Color.parseColor("#DDE3EA"));
        grid.setStrokeWidth(d);
        text.setColor(Color.parseColor("#607D8B"));
        text.setTextSize(11 * d);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.2f * d);
        line.setStrokeJoin(Paint.Join.ROUND);
        dot.setStyle(Paint.Style.FILL);
    }

    public void setData(List<Series> s, float min, float max, String unit) {
        series.clear();
        series.addAll(s);
        this.min = min;
        this.max = max;
        this.unit = unit;
        invalidate();
    }

    public static Series series(List<float[]> pts, int color) { return new Series(pts, color); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float left = 34 * d, right = w - 8 * d, top = 8 * d, bottom = h - 18 * d;
        for (int i = 0; i <= 4; i++) {
            float x = left + (right - left) * i / 4f;
            c.drawLine(x, top, x, bottom, grid);
            String lbl = (i * 6) + ":00";
            float tw = text.measureText(lbl);
            c.drawText(lbl, Math.min(Math.max(x - tw / 2, 0), w - tw), h - 4 * d, text);
        }
        for (int i = 0; i <= 2; i++) {
            float y = bottom - (bottom - top) * i / 2f;
            c.drawLine(left, y, right, y, grid);
            float v = min + (max - min) * i / 2f;
            c.drawText(String.valueOf(Math.round(v)), 2 * d, y + 4 * d, text);
        }
        boolean any = false;
        for (Series s : series) {
            if (s.pts.isEmpty()) continue;
            any = true;
            line.setColor(s.color);
            dot.setColor(s.color);
            if (bars) {
                float bw = (right - left) / 48f * 0.7f;
                for (float[] pt : s.pts) {
                    float x = left + (right - left) * (pt[0] + 15) / 1440f;
                    float y = bottom - (bottom - top) * (pt[1] - min) / (max - min);
                    y = Math.max(top, Math.min(bottom, y));
                    c.drawRect(x - bw / 2, y, x + bw / 2, bottom, dot);
                }
                continue;
            }
            Path p = new Path();
            float lastX = -1;
            boolean first = true;
            for (float[] pt : s.pts) {
                float x = left + (right - left) * pt[0] / 1440f;
                float y = bottom - (bottom - top) * (pt[1] - min) / (max - min);
                y = Math.max(top, Math.min(bottom, y));
                if (first || x - lastX > 20 * d) p.moveTo(x, y); else p.lineTo(x, y);
                if (s.pts.size() < 40) c.drawCircle(x, y, 2.5f * d, dot);
                lastX = x;
                first = false;
            }
            c.drawPath(p, line);
        }
        if (!any) {
            String t = "brak danych";
            c.drawText(t, (w - text.measureText(t)) / 2, (top + bottom) / 2, text);
        }
    }
}
