package pl.kuba.zegarektest;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Podsumowanie 30 dni: ciśnienie, tętno, kroki, sen + porównanie dwóch połówek miesiąca. */
public class TrendActivity extends Activity {

    private static final int N = 30;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable round(int c, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c);
        g.setCornerRadius(dp(r));
        return g;
    }

    private TextView section(LinearLayout p, String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextColor(Color.parseColor("#3D4B63"));
        t.setPadding(dp(8), dp(18), 0, dp(6));
        p.addView(t);
        return t;
    }

    private ChartView chart(LinearLayout p, String[] labels) {
        ChartView c = new ChartView(this);
        c.setSlots(labels);
        c.setBackground(round(Color.WHITE, 22));
        c.setElevation(dp(2));
        c.setPadding(dp(6), dp(8), dp(6), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
        lp.setMargins(dp(4), 0, dp(4), dp(4));
        p.addView(c, lp);
        return c;
    }

    private static String arrow(float a, float b, float tol) {
        if (a <= 0 || b <= 0) return "";
        if (b > a + tol) return " ↑";
        if (b < a - tol) return " ↓";
        return " →";
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#1D4ED8"));
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.parseColor("#EEF2F8"));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(30), dp(20), dp(18));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#1D4ED8"), Color.parseColor("#7C3AED")});
        hg.setCornerRadii(new float[]{0, 0, 0, 0, dp(28), dp(28), dp(28), dp(28)});
        head.setBackground(hg);
        TextView back = new TextView(this);
        back.setText("‹  Wróć");
        back.setTextSize(15);
        back.setTextColor(Color.WHITE);
        back.setOnClickListener(v -> finish());
        head.addView(back);
        TextView title = new TextView(this);
        title.setText("Ostatnie 30 dni 📈");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        head.addView(title);
        outer.addView(head);

        ScrollView sv = new ScrollView(this);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(10), dp(4), dp(10), dp(24));
        sv.addView(p);
        outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- dane ----
        String[] labels = new String[N];
        float[] sys = new float[N], dia = new float[N], hr = new float[N], steps = new float[N], sleep = new float[N];
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -(N - 1));
        SimpleDateFormat key = new SimpleDateFormat("yyyy-MM-dd", Locale.US), lbl = new SimpleDateFormat("dd.MM", Locale.US);
        for (int i = 0; i < N; i++, c.add(Calendar.DAY_OF_MONTH, 1)) {
            labels[i] = (i % 7 == 1 || i == N - 1) ? lbl.format(c.getTime()) : "";
            JSONObject o = HistStore.load(this, key.format(c.getTime()));
            JSONObject r = o.optJSONObject("r");
            int bn = 0, hn = 0;
            float bs = 0, bd = 0, hs = 0;
            long st = 0;
            if (r != null) {
                Iterator<String> it = r.keys();
                while (it.hasNext()) {
                    JSONObject x = r.optJSONObject(it.next());
                    if (x == null) continue;
                    if (x.optInt("sys") > 0) { bn++; bs += x.optInt("sys"); bd += x.optInt("dia"); }
                    if (x.optInt("hr") > 0) { hn++; hs += x.optInt("hr"); }
                    st += x.optInt("st", 0);
                }
            }
            if (bn > 0) { sys[i] = bs / bn; dia[i] = bd / bn; }
            if (hn > 0) hr[i] = hs / hn;
            steps[i] = Math.max(st, o.optLong("steps", 0));
            JSONObject sl = o.optJSONObject("sleep");
            if (sl != null) sleep[i] = sl.optInt("min") / 60f;
        }

        // ---- podsumowanie ----
        float[][] halves = new float[5][4]; // [metric][sumA, nA, sumB, nB]
        float[][] all = {sys, dia, hr, steps, sleep};
        for (int m = 0; m < 5; m++)
            for (int i = 0; i < N; i++) {
                if (all[m][i] <= 0) continue;
                int h = i < N / 2 ? 0 : 2;
                halves[m][h] += all[m][i];
                halves[m][h + 1]++;
            }
        float[] a = new float[5], bb = new float[5];
        for (int m = 0; m < 5; m++) {
            a[m] = halves[m][1] > 0 ? halves[m][0] / halves[m][1] : 0;
            bb[m] = halves[m][3] > 0 ? halves[m][2] / halves[m][3] : 0;
        }
        StringBuilder s = new StringBuilder("Pierwsze 2 tygodnie → ostatnie 2 tygodnie\n");
        s.append(String.format(Locale.US, "\n🩸 Ciśnienie: %s → %s%s",
                a[0] > 0 ? Math.round(a[0]) + "/" + Math.round(a[1]) : "–", bb[0] > 0 ? Math.round(bb[0]) + "/" + Math.round(bb[1]) : "–", arrow(a[0], bb[0], 3)));
        s.append(String.format(Locale.US, "\n❤️ Tętno średnie: %s → %s%s",
                a[2] > 0 ? String.valueOf(Math.round(a[2])) : "–", bb[2] > 0 ? String.valueOf(Math.round(bb[2])) : "–", arrow(a[2], bb[2], 2)));
        s.append(String.format(Locale.US, "\n👟 Kroki dziennie: %s → %s%s",
                a[3] > 0 ? String.valueOf(Math.round(a[3])) : "–", bb[3] > 0 ? String.valueOf(Math.round(bb[3])) : "–", arrow(a[3], bb[3], 500)));
        s.append(String.format(Locale.US, "\n😴 Sen: %s → %s%s",
                a[4] > 0 ? String.format(Locale.US, "%.1f h", a[4]) : "–", bb[4] > 0 ? String.format(Locale.US, "%.1f h", bb[4]) : "–", arrow(a[4], bb[4], 0.3f)));
        if (a[0] > 0 && bb[0] > 0) {
            float sysHi = BleService.th(this, "sysHi");
            if (bb[0] >= sysHi) s.append("\n\n⚠️ Średnie ciśnienie z ostatnich 2 tygodni jest powyżej progu ").append(Math.round(sysHi)).append(" – warto pokazać raport lekarzowi.");
        }
        TextView sum = new TextView(this);
        sum.setText(s.toString());
        sum.setTextSize(15);
        sum.setTextColor(Color.parseColor("#263238"));
        sum.setLineSpacing(dp(3), 1f);
        sum.setPadding(dp(16), dp(14), dp(16), dp(14));
        sum.setBackground(round(Color.WHITE, 22));
        sum.setElevation(dp(2));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.setMargins(dp(4), dp(10), dp(4), dp(4));
        p.addView(sum, sl);

        // ---- wykresy ----
        section(p, "Ciśnienie – średnia dnia (skurczowe / rozkurczowe)");
        ChartView cb = chart(p, labels);
        cb.setGoal(BleService.th(this, "sysHi"));
        List<ChartView.Series> ls = new ArrayList<>();
        ls.add(ChartView.series(pts(sys), Color.parseColor("#E11D48")));
        ls.add(ChartView.series(pts(dia), Color.parseColor("#F59E0B")));
        cb.setData(ls, 50, 170, "mmHg");

        section(p, "Tętno – średnia dnia");
        ChartView ch = chart(p, labels);
        List<ChartView.Series> lh = new ArrayList<>();
        lh.add(ChartView.series(pts(hr), Color.parseColor("#DB2777")));
        ch.setData(lh, 40, 120, "ud./min");

        int goal = getSharedPreferences("cele", MODE_PRIVATE).getInt("kroki", 10000);
        section(p, "Kroki dziennie");
        ChartView cs = chart(p, labels);
        cs.setBars(true);
        cs.setGoal(goal);
        List<ChartView.Series> lst = new ArrayList<>();
        lst.add(ChartView.series(pts(steps), Color.parseColor("#2563EB")));
        float mx = goal;
        for (float v : steps) mx = Math.max(mx, v);
        cs.setData(lst, 0, mx * 1.1f, "kroki");

        section(p, "Sen (godziny)");
        ChartView cz = chart(p, labels);
        cz.setBars(true);
        cz.setGoal(getSharedPreferences("cele", MODE_PRIVATE).getFloat("sen", 7f));
        List<ChartView.Series> lz = new ArrayList<>();
        lz.add(ChartView.series(pts(sleep), Color.parseColor("#4F46E5")));
        cz.setData(lz, 0, 10, "h");

        setContentView(outer);
    }

    private static List<float[]> pts(float[] v) {
        List<float[]> l = new ArrayList<>();
        for (int i = 0; i < v.length; i++) if (v[i] > 0) l.add(new float[]{i + 0.5f, v[i]});
        return l;
    }
}
