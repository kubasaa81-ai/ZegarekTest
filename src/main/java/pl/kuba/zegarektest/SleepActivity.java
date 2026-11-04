package pl.kuba.zegarektest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Zakładka snu: noc po nocy (fazy snu), statystyki i ostatnie 7 nocy. */
public class SleepActivity extends Activity {

    private final Calendar day = Calendar.getInstance();
    private TextView tvDate, tvStats, tvWeek;
    private Hypno hypno;
    private ChartView week;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable round(int c, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c);
        g.setCornerRadius(dp(r));
        return g;
    }

    private GradientDrawable grad(String a, String b, int r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor(a), Color.parseColor(b)});
        g.setCornerRadius(dp(r));
        return g;
    }

    private Button btn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setTextColor(Color.WHITE);
        b.setStateListAnimator(null);
        b.setBackground(grad("#4F46E5", "#4338CA", 24));
        b.setOnClickListener(l);
        return b;
    }

    private TextView card(float size) {
        TextView t = new TextView(this);
        t.setTextSize(size);
        t.setTextColor(Color.parseColor("#263238"));
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setBackground(round(Color.WHITE, 22));
        t.setElevation(dp(2));
        t.setLineSpacing(dp(4), 1f);
        return t;
    }

    private void add(LinearLayout p, View v, int h) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
        lp.setMargins(dp(4), dp(6), dp(4), dp(6));
        p.addView(v, lp);
    }

    private TextView section(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#3D4B63"));
        t.setPadding(dp(8), dp(14), 0, dp(2));
        return t;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#1E1B4B"));
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.parseColor("#EEF2F8"));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(34), dp(20), dp(18));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#1E1B4B"), Color.parseColor("#4338CA"), Color.parseColor("#7C3AED")});
        hg.setCornerRadii(new float[]{0, 0, 0, 0, dp(28), dp(28), dp(28), dp(28)});
        head.setBackground(hg);
        TextView back = new TextView(this);
        back.setText("‹  Wróć");
        back.setTextSize(15);
        back.setTextColor(Color.WHITE);
        back.setOnClickListener(v -> finish());
        head.addView(back);
        TextView title = new TextView(this);
        title.setText("😴  Sen");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        head.addView(title);
        outer.addView(head);

        ScrollView sv = new ScrollView(this);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(10), dp(8), dp(10), dp(24));
        sv.addView(p);
        outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout dr = new LinearLayout(this);
        dr.setGravity(Gravity.CENTER_VERTICAL);
        tvDate = new TextView(this);
        tvDate.setTextSize(17);
        tvDate.setTypeface(Typeface.DEFAULT_BOLD);
        tvDate.setGravity(Gravity.CENTER);
        tvDate.setTextColor(Color.parseColor("#263238"));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(dp(60), ViewGroup.LayoutParams.WRAP_CONTENT);
        l1.setMargins(dp(4), dp(4), dp(4), dp(4));
        dr.addView(btn("‹", v -> shift(-1)), l1);
        dr.addView(tvDate, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(dp(60), ViewGroup.LayoutParams.WRAP_CONTENT);
        l2.setMargins(dp(4), dp(4), dp(4), dp(4));
        dr.addView(btn("›", v -> shift(1)), l2);
        p.addView(dr);

        p.addView(section("Fazy snu"));
        hypno = new Hypno(this);
        hypno.setBackground(round(Color.WHITE, 22));
        hypno.setElevation(dp(2));
        add(p, hypno, dp(200));

        tvStats = card(16);
        add(p, tvStats, ViewGroup.LayoutParams.WRAP_CONTENT);

        p.addView(section("Ostatnie 7 nocy (godziny)"));
        week = new ChartView(this);
        week.setBars(true);
        week.setBackground(round(Color.WHITE, 22));
        week.setElevation(dp(2));
        add(p, week, dp(190));
        tvWeek = card(14);
        add(p, tvWeek, ViewGroup.LayoutParams.WRAP_CONTENT);

        Button get = btn("Pobierz sen z zegarka", v -> startActivity(new Intent(this, SdkActivity.class)));
        get.setTextSize(15);
        add(p, get, ViewGroup.LayoutParams.WRAP_CONTENT);

        setContentView(outer);
        // pokaż ostatnią noc, jaką mamy
        for (int i = 0; i < 7; i++) {
            if (sleep(key(day)) != null) break;
            day.add(Calendar.DAY_OF_MONTH, -1);
        }
        if (sleep(key(day)) == null) day.setTime(Calendar.getInstance().getTime());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private static String key(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    private JSONObject sleep(String dk) {
        return HistStore.load(this, dk).optJSONObject("sleep");
    }

    private void shift(int d) {
        day.add(Calendar.DAY_OF_MONTH, d);
        if (day.after(Calendar.getInstance())) day.setTime(Calendar.getInstance().getTime());
        refresh();
    }

    private static String hm(int min) {
        return String.format(Locale.US, "%dh %02dmin", min / 60, min % 60);
    }

    private void refresh() {
        String dk = key(day);
        tvDate.setText("Noc do " + new SimpleDateFormat("dd.MM.yyyy", Locale.US).format(day.getTime()));
        JSONObject s = sleep(dk);
        float goal = getSharedPreferences("cele", MODE_PRIVATE).getFloat("sen", 7f);
        if (s == null) {
            hypno.set(null, null, null, 0);
            tvStats.setText("Brak danych snu z tej nocy.\nStuknij „Pobierz sen z zegarka”.");
        } else {
            int all = s.optInt("min"), deep = s.optInt("gleboki"), light = s.optInt("plytki"), q = s.optInt("jakosc");
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < 5; i++) stars.append(i < q ? "★" : "☆");
            int awake = Math.max(0, all - deep - light);
            hypno.set(s.optString("linia", ""), s.optString("od"), s.optString("do"), all);
            tvStats.setText("Zaśnięcie " + s.optString("od") + "   ·   Pobudka " + s.optString("do")
                    + "\nRazem: " + hm(all) + "   " + stars
                    + "\n🟣 Głęboki: " + hm(deep) + (all > 0 ? " (" + deep * 100 / all + "%)" : "")
                    + "\n🔵 Płytki: " + hm(light) + (all > 0 ? " (" + light * 100 / all + "%)" : "")
                    + (awake > 0 ? "\n🟠 Czuwanie / inne: " + hm(awake) : "")
                    + "\nPobudki: " + s.optInt("pobudki")
                    + (all < goal * 60 ? String.format(Locale.US, "\nDo celu %.0f h zabrakło %s", goal, hm(Math.round(goal * 60) - all)) : "\n✅ Cel snu osiągnięty"));
        }

        Calendar c = (Calendar) day.clone();
        c.add(Calendar.DAY_OF_MONTH, -6);
        String[] wd = {"nd", "pn", "wt", "śr", "cz", "pt", "sb"};
        String[] lbl = new String[7];
        List<float[]> pts = new ArrayList<>();
        int sum = 0, n = 0, deepSum = 0;
        for (int i = 0; i < 7; i++, c.add(Calendar.DAY_OF_MONTH, 1)) {
            lbl[i] = wd[c.get(Calendar.DAY_OF_WEEK) - 1];
            JSONObject x = sleep(key(c));
            if (x == null) continue;
            int m = x.optInt("min");
            pts.add(new float[]{i + 0.5f, m / 60f});
            sum += m;
            deepSum += x.optInt("gleboki");
            n++;
        }
        week.setSlots(lbl);
        week.setGoal(goal);
        List<ChartView.Series> ls = new ArrayList<>();
        ls.add(ChartView.series(pts, Color.parseColor("#6366F1")));
        week.setData(ls, 0, Math.max(10, goal + 2), "");
        tvWeek.setText(n == 0 ? "Brak nocy w tym tygodniu."
                : "Średnio: " + hm(sum / n) + " na noc (z " + n + " nocy)\nŚredni sen głęboki: " + hm(deepSum / n));
    }

    /** Hipnogram: pasy faz snu z linii snu zegarka. */
    static class Hypno extends View {
        private String line, from, to;
        private int all;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;

        Hypno(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            p.setTextSize(11 * d);
        }

        void set(String line, String from, String to, int all) {
            this.all = all;
            this.line = line;
            this.from = from;
            this.to = to;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float left = 70 * d, right = w - 12 * d, top = 14 * d, bottom = h - 26 * d;
            String[] names = {"Czuwanie", "Płytki", "Głęboki"};
            int[] cols = {Color.parseColor("#FB923C"), Color.parseColor("#60A5FA"), Color.parseColor("#6D28D9")};
            float rowH = (bottom - top) / 3f;
            p.setColor(Color.parseColor("#8A97AB"));
            for (int i = 0; i < 3; i++) c.drawText(names[i], 10 * d, top + rowH * i + rowH / 2 + 4 * d, p);
            if (line == null || line.isEmpty()) {
                String t = "brak danych";
                c.drawText(t, (w - p.measureText(t)) / 2, h / 2, p);
                return;
            }
            // zwykły sen: 0 płytki, 1 głęboki, 2 czuwanie (5 min); dokładny: 0 głęboki, 1 płytki, 2 REM, 3 bezsenność, 4 czuwanie (1 min)
            boolean precise = line.matches(".*[34].*") || (all > 0 && line.length() >= all * 0.8);
            int n = line.length();
            float bw = (right - left) / n;
            for (int i = 0; i < n; i++) {
                char ch = line.charAt(i);
                int row;
                if (precise) row = ch == '0' ? 2 : (ch == '1' || ch == '2') ? 1 : 0;
                else row = ch == '1' ? 2 : ch == '0' ? 1 : 0;
                p.setColor(cols[row]);
                float x = left + i * bw;
                c.drawRoundRect(new RectF(x, top + rowH * row + 3 * d, x + bw + 0.6f, top + rowH * (row + 1) - 3 * d), 2 * d, 2 * d, p);
            }
            p.setColor(Color.parseColor("#8A97AB"));
            if (from != null) c.drawText(from, left, h - 8 * d, p);
            if (to != null) c.drawText(to, right - p.measureText(to), h - 8 * d, p);
        }
    }
}
