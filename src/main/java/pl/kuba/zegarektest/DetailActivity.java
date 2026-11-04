package pl.kuba.zegarektest;

import android.app.Activity;
import android.graphics.Color;
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
import android.widget.Toast;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/** Szczegóły jednego parametru: dzień / tydzień / miesiąc, wykres, statystyki, lista pomiarów. */
public class DetailActivity extends Activity implements BleService.Listener {

    public static final String EXTRA = "metryka";

    // klucz, tytuł, jednostka, kolor1, kolor2, min wykresu, max wykresu
    static final String[][] M = {
            {"steps", "Kroki", "kroków", "#2F80ED", "#56CCF2", "0", "0"},
            {"hr", "Tętno", "ud./min", "#FF3D77", "#FF8FB1", "40", "160"},
            {"bp", "Ciśnienie", "mmHg", "#FF9F1C", "#FFC857", "50", "170"},
            {"spo2", "Tlen we krwi", "%", "#7C3AED", "#A78BFA", "85", "100"},
            {"temp", "Temperatura ciała", "°C", "#F43F5E", "#FB7185", "34", "38.5"},
            {"glu", "Glukoza (szacunkowo)", "mmol/L", "#E11D74", "#F472B6", "2", "12"},
            {"resp", "Oddech", "/min", "#0EA5A4", "#5EEAD4", "0", "30"}
    };

    private String[] m;
    private int range = 0; // 0 dzień, 1 tydzień, 2 miesiąc
    private final Calendar day = Calendar.getInstance();
    private TextView tvDate, tvStats, tvList;
    private ChartView chart;
    private final Button[] rb = new Button[3];
    private Button btnMeasure;
    private final Runnable refresher = this::refresh;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    private GradientDrawable grad(String a, String b, int r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor(a), Color.parseColor(b)});
        g.setCornerRadius(dp(r));
        return g;
    }

    private Button btn(String t, String c1, String c2, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setMinHeight(dp(46));
        b.setMinimumHeight(dp(46));
        b.setStateListAnimator(null);
        b.setBackground(grad(c1, c2, 24));
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
        t.setLineSpacing(dp(3), 1f);
        return t;
    }

    private void add(LinearLayout p, View v, int h) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
        lp.setMargins(dp(4), dp(6), dp(4), dp(6));
        p.addView(v, lp);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String key = getIntent().getStringExtra(EXTRA);
        m = M[0];
        for (String[] x : M) if (x[0].equals(key)) m = x;

        getWindow().setStatusBarColor(Color.parseColor(m[3]));
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.parseColor("#EEF2F8"));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(34), dp(20), dp(18));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor(m[3]), Color.parseColor(m[4])});
        hg.setCornerRadii(new float[]{0, 0, 0, 0, dp(28), dp(28), dp(28), dp(28)});
        head.setBackground(hg);
        TextView back = new TextView(this);
        back.setText("‹  Wróć");
        back.setTextSize(15);
        back.setTextColor(Color.WHITE);
        back.setOnClickListener(v -> finish());
        head.addView(back);
        TextView title = new TextView(this);
        title.setText(m[1]);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, dp(6), 0, 0);
        head.addView(title);
        outer.addView(head);

        ScrollView sv = new ScrollView(this);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(10), dp(8), dp(10), dp(24));
        sv.addView(p);
        outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout rr = new LinearLayout(this);
        String[] names = {"Dzień", "Tydzień", "Miesiąc"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            Button bt = new Button(this);
            bt.setText(names[i]);
            bt.setAllCaps(false);
            bt.setTextSize(14);
            bt.setStateListAnimator(null);
            bt.setOnClickListener(v -> { range = k; refresh(); });
            rb[i] = bt;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            rr.addView(bt, lp);
        }
        p.addView(rr);

        LinearLayout dr = new LinearLayout(this);
        dr.setGravity(Gravity.CENTER_VERTICAL);
        Button prev = btn("‹", "#64748B", "#475569", v -> shift(-1));
        Button next = btn("›", "#64748B", "#475569", v -> shift(1));
        tvDate = new TextView(this);
        tvDate.setTextSize(17);
        tvDate.setTypeface(Typeface.DEFAULT_BOLD);
        tvDate.setGravity(Gravity.CENTER);
        tvDate.setTextColor(Color.parseColor("#263238"));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(dp(60), ViewGroup.LayoutParams.WRAP_CONTENT);
        l1.setMargins(dp(4), dp(4), dp(4), dp(4));
        dr.addView(prev, l1);
        dr.addView(tvDate, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(dp(60), ViewGroup.LayoutParams.WRAP_CONTENT);
        l2.setMargins(dp(4), dp(4), dp(4), dp(4));
        dr.addView(next, l2);
        p.addView(dr);

        chart = new ChartView(this);
        chart.setBackground(round(Color.WHITE, 22));
        chart.setElevation(dp(2));
        add(p, chart, dp(220));

        tvStats = card(16);
        add(p, tvStats, ViewGroup.LayoutParams.WRAP_CONTENT);

        if (m[0].equals("hr")) {
            btnMeasure = btn("❤️  Zmierz tętno teraz", "#FF3D77", "#C2185B", v -> toggleMeasure());
            add(p, btnMeasure, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        tvList = card(14);
        tvList.setTypeface(Typeface.MONOSPACE);
        add(p, tvList, ViewGroup.LayoutParams.WRAP_CONTENT);

        if (m[0].equals("glu")) {
            TextView n = new TextView(this);
            n.setText("Glukozę zegarek tylko szacuje z czujnika optycznego – traktuj wartości orientacyjnie.");
            n.setTextSize(12);
            n.setTextColor(Color.parseColor("#64748B"));
            n.setPadding(dp(10), dp(4), dp(10), dp(4));
            p.addView(n);
        }
        setContentView(outer);
    }

    private boolean measuring = false;

    private void toggleMeasure() {
        BleService s = BleService.instance;
        if (s == null) { Toast.makeText(this, "Brak połączenia z zegarkiem", Toast.LENGTH_SHORT).show(); return; }
        measuring = !measuring;
        s.sendVeepoo(new byte[]{(byte) 0xD0, (byte) (measuring ? 1 : 0)});
        if (!measuring && BleService.sHr.contains("na żywo")) BleService.sHr = BleService.sHr.replace("na żywo", "ostatni pomiar");
        refresh();
    }

    private void shift(int dir) {
        if (range == 0) day.add(Calendar.DAY_OF_MONTH, dir);
        else if (range == 1) day.add(Calendar.DAY_OF_MONTH, 7 * dir);
        else day.add(Calendar.MONTH, dir);
        if (day.after(Calendar.getInstance())) day.setTime(Calendar.getInstance().getTime());
        refresh();
    }

    private static String key(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    /** Wartości parametru z jednego wpisu: [główna] lub [skurczowe, rozkurczowe]. */
    private float[] vals(JSONObject r) {
        switch (m[0]) {
            case "steps": return r.has("st") ? new float[]{r.optInt("st")} : null;
            case "hr": return r.has("hr") ? new float[]{r.optInt("hr")} : null;
            case "bp": return r.has("sys") ? new float[]{r.optInt("sys"), r.optInt("dia")} : null;
            case "spo2": return r.has("spo2") ? new float[]{r.optInt("spo2")} : null;
            case "temp": return r.has("temp") ? new float[]{(float) r.optDouble("temp")} : null;
            case "glu": return r.has("glu") ? new float[]{(float) r.optDouble("glu")} : null;
            case "resp": return r.has("resp") ? new float[]{r.optInt("resp")} : null;
        }
        return null;
    }

    private String fmt(float v) {
        if (m[0].equals("temp") || m[0].equals("glu")) return String.format(Locale.US, "%.1f", v);
        return String.valueOf(Math.round(v));
    }

    private TreeMap<String, JSONObject> records(String dayKey) {
        TreeMap<String, JSONObject> out = new TreeMap<>();
        JSONObject o = HistStore.load(this, dayKey);
        JSONObject recs = o.optJSONObject("r");
        if (recs == null) return out;
        Iterator<String> it = recs.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject r = recs.optJSONObject(k);
            if (r != null) out.put(k, r);
        }
        return out;
    }

    private long daySteps(String dayKey) {
        JSONObject o = HistStore.load(this, dayKey);
        long sum = 0;
        for (JSONObject r : records(dayKey).values()) sum += r.optInt("st", 0);
        return Math.max(sum, o.optLong("steps", 0));
    }

    private void refresh() {
        for (int i = 0; i < 3; i++) {
            boolean on = i == range;
            rb[i].setBackground(on ? grad(m[3], m[4], 20) : round(Color.WHITE, 20));
            rb[i].setTextColor(on ? Color.WHITE : Color.parseColor("#64748B"));
            rb[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        if (btnMeasure != null) {
            btnMeasure.setText(measuring ? "⏹  Zatrzymaj pomiar (" + BleService.sHr + ")" : "❤️  Zmierz tętno teraz");
        }
        if (range == 0) refreshDay(); else refreshRange(range == 1 ? 7 : 30);
    }

    private void refreshDay() {
        String dk = key(day);
        boolean today = dk.equals(BleService.today());
        tvDate.setText((today ? "Dziś, " : "") + new SimpleDateFormat("dd.MM.yyyy", Locale.US).format(day.getTime()));
        chart.setDayMode();
        chart.setBars(m[0].equals("steps"));
        List<float[]> a = new ArrayList<>(), b = new ArrayList<>();
        int[] slots = new int[48];
        float mn = Float.MAX_VALUE, mx = -Float.MAX_VALUE, sum = 0;
        int n = 0;
        StringBuilder list = new StringBuilder();
        TreeMap<String, JSONObject> recs = records(dk);
        for (String k : recs.descendingKeySet()) {
            float[] v = vals(recs.get(k));
            if (v == null) continue;
            float minute = Integer.parseInt(k.substring(0, 2)) * 60 + Integer.parseInt(k.substring(3, 5));
            if (m[0].equals("steps")) {
                slots[Math.min(47, (int) (minute / 30))] += (int) v[0];
                continue;
            }
            a.add(0, new float[]{minute, v[0]});
            if (v.length > 1) b.add(0, new float[]{minute, v[1]});
            mn = Math.min(mn, v[0]); mx = Math.max(mx, v[0]); sum += v[0]; n++;
            if (list.length() < 6000)
                list.append(k).append("   ").append(fmt(v[0])).append(v.length > 1 ? "/" + fmt(v[1]) : "").append(" ").append(m[2]).append("\n");
        }
        List<ChartView.Series> ss = new ArrayList<>();
        if (m[0].equals("steps")) {
            long total = 0;
            int best = 0, bestI = -1;
            for (int i = 0; i < 48; i++) {
                total += slots[i];
                if (slots[i] > 0) a.add(new float[]{i * 30, slots[i]});
                if (slots[i] > best) { best = slots[i]; bestI = i; }
            }
            for (int i = 47; i >= 0; i--) if (slots[i] > 0)
                list.append(String.format(Locale.US, "%02d:%02d   %5d kroków\n", i / 2, (i % 2) * 30, slots[i]));
            total = Math.max(total, daySteps(dk));
            int goal = getSharedPreferences("cele", MODE_PRIVATE).getInt("kroki", 10000);
            tvStats.setText("Razem: " + total + " kroków  (" + (total * 100 / Math.max(1, goal)) + "% celu)"
                    + (bestI >= 0 ? String.format(Locale.US, "\nNajaktywniej: %02d:%02d – %d kroków", bestI / 2, (bestI % 2) * 30, best) : ""));
            ss.add(ChartView.series(a, Color.parseColor(m[3])));
            chart.setGoal(-1);
            chart.setData(ss, 0, Math.max(100, best * 1.15f), "");
        } else {
            ss.add(ChartView.series(a, Color.parseColor(m[3])));
            if (!b.isEmpty()) ss.add(ChartView.series(b, Color.parseColor("#4F46E5")));
            chart.setGoal(-1);
            chart.setData(ss, Float.parseFloat(m[5]), Float.parseFloat(m[6]), "");
            tvStats.setText(n == 0 ? "Brak pomiarów tego dnia"
                    : "Średnio: " + fmt(sum / n) + " " + m[2] + "\nNajniżej: " + fmt(mn) + "    Najwyżej: " + fmt(mx) + "\nPomiarów: " + n);
        }
        tvList.setText(list.length() == 0 ? "Brak danych – pojawią się po pobraniu historii z zegarka." : list.toString().trim());
    }

    private void refreshRange(int days) {
        Calendar end = (Calendar) day.clone();
        Calendar start = (Calendar) day.clone();
        start.add(Calendar.DAY_OF_MONTH, -(days - 1));
        SimpleDateFormat f = new SimpleDateFormat("dd.MM", Locale.US);
        tvDate.setText(f.format(start.getTime()) + " – " + f.format(end.getTime()));
        String[] lbl = new String[days];
        List<float[]> a = new ArrayList<>(), b = new ArrayList<>();
        StringBuilder list = new StringBuilder();
        float mn = Float.MAX_VALUE, mx = -Float.MAX_VALUE, sum = 0, top = 0;
        int nDays = 0, goalDays = 0;
        int goal = getSharedPreferences("cele", MODE_PRIVATE).getInt("kroki", 10000);
        String[] wd = {"nd", "pn", "wt", "śr", "cz", "pt", "sb"};
        Calendar c = (Calendar) start.clone();
        for (int i = 0; i < days; i++, c.add(Calendar.DAY_OF_MONTH, 1)) {
            String dk = key(c);
            lbl[i] = days == 7 ? wd[c.get(Calendar.DAY_OF_WEEK) - 1] : (i % 5 == 0 ? String.valueOf(c.get(Calendar.DAY_OF_MONTH)) : "");
            String dTxt = f.format(c.getTime());
            if (m[0].equals("steps")) {
                long st = daySteps(dk);
                if (st <= 0) continue;
                a.add(new float[]{i + 0.5f, st});
                top = Math.max(top, st);
                sum += st; nDays++;
                if (st >= goal) goalDays++;
                list.insert(0, String.format(Locale.US, "%s   %6d kroków%s\n", dTxt, st, st >= goal ? "  ✓" : ""));
                continue;
            }
            float dmn = Float.MAX_VALUE, dmx = -Float.MAX_VALUE, dsum = 0, dsum2 = 0;
            int dn = 0;
            for (JSONObject r : records(dk).values()) {
                float[] v = vals(r);
                if (v == null) continue;
                dmn = Math.min(dmn, v[0]); dmx = Math.max(dmx, v[0]); dsum += v[0];
                if (v.length > 1) dsum2 += v[1];
                dn++;
            }
            if (dn == 0) continue;
            float avg = dsum / dn;
            a.add(new float[]{i + 0.5f, avg});
            if (m[0].equals("bp")) b.add(new float[]{i + 0.5f, dsum2 / dn});
            mn = Math.min(mn, dmn); mx = Math.max(mx, dmx); sum += avg; nDays++;
            list.insert(0, dTxt + "   śr. " + fmt(avg) + (m[0].equals("bp") ? "/" + fmt(dsum2 / dn) : "")
                    + "   (" + fmt(dmn) + "–" + fmt(dmx) + ")\n");
        }
        chart.setSlots(lbl);
        List<ChartView.Series> ss = new ArrayList<>();
        ss.add(ChartView.series(a, Color.parseColor(m[3])));
        if (!b.isEmpty()) ss.add(ChartView.series(b, Color.parseColor("#4F46E5")));
        if (m[0].equals("steps")) {
            chart.setBars(true);
            chart.setGoal(goal);
            chart.setData(ss, 0, Math.max(goal * 1.1f, top * 1.1f), "");
            tvStats.setText(nDays == 0 ? "Brak danych w tym okresie"
                    : "Średnio dziennie: " + Math.round(sum / nDays) + " kroków\nCel osiągnięty: " + goalDays + " z " + nDays + " dni");
        } else {
            chart.setBars(false);
            chart.setGoal(-1);
            chart.setData(ss, Float.parseFloat(m[5]), Float.parseFloat(m[6]), "");
            tvStats.setText(nDays == 0 ? "Brak danych w tym okresie"
                    : "Średnia z dni: " + fmt(sum / nDays) + " " + m[2] + "\nNajniżej: " + fmt(mn) + "    Najwyżej: " + fmt(mx) + "\nDni z pomiarami: " + nDays);
        }
        tvList.setText(list.length() == 0 ? "Brak danych – historia zbiera się od dnia instalacji aplikacji." : list.toString().trim());
    }

    @Override
    protected void onResume() {
        super.onResume();
        BleService.listener = this;
        refresh();
    }

    @Override
    protected void onPause() {
        if (BleService.listener == this) BleService.listener = null;
        super.onPause();
    }

    @Override
    public void onLog(String line) { }

    @Override
    public void onStateChanged() {
        tvDate.removeCallbacks(refresher);
        tvDate.postDelayed(refresher, 500);
    }
}
