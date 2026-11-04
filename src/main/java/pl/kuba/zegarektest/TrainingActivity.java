package pl.kuba.zegarektest;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Trening: czas, tętno na żywo, kroki, dystans i kalorie (szacunkowo). */
public class TrainingActivity extends Activity {

    static final String[][] TYPES = {{"🚶", "Spacer", "3.5", "#16A34A"}, {"🏃", "Bieg", "8", "#E11D48"},
            {"🚴", "Rower", "6", "#2563EB"}, {"🏋️", "Inne", "4", "#7C3AED"}};

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView tvTime, tvHr, tvSteps, tvKcal, tvInfo, tvHist;
    private Button bStart;
    private final TextView[] typeBtn = new TextView[4];
    private int type = 0;
    private long t0 = 0;
    private long steps0 = -1;
    private int hrSum = 0, hrN = 0, hrMax = 0, hrNow = 0;

    private SharedPreferences sp() { return getSharedPreferences("trening", MODE_PRIVATE); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable grad(String a, String b, int r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor(a), Color.parseColor(b)});
        g.setCornerRadius(dp(r));
        return g;
    }

    private GradientDrawable round(int c, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c);
        g.setCornerRadius(dp(r));
        return g;
    }

    private TextView stat(LinearLayout row, String label, String color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(14), dp(8), dp(14));
        box.setBackground(round(Color.WHITE, 20));
        box.setElevation(dp(2));
        TextView v = new TextView(this);
        v.setTextSize(26);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Color.parseColor(color));
        v.setGravity(Gravity.CENTER);
        v.setText("–");
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(Color.parseColor("#64748B"));
        l.setGravity(Gravity.CENTER);
        box.addView(v);
        box.addView(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        row.addView(box, lp);
        return v;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#C2410C"));
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.parseColor("#EEF2F8"));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(30), dp(20), dp(18));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#EA580C"), Color.parseColor("#DB2777")});
        hg.setCornerRadii(new float[]{0, 0, 0, 0, dp(28), dp(28), dp(28), dp(28)});
        head.setBackground(hg);
        TextView back = new TextView(this);
        back.setText("‹  Wróć");
        back.setTextSize(15);
        back.setTextColor(Color.WHITE);
        back.setOnClickListener(v -> finish());
        head.addView(back);
        TextView title = new TextView(this);
        title.setText("Trening 🏃");
        title.setTextSize(24);
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

        LinearLayout tr = new LinearLayout(this);
        for (int k = 0; k < 4; k++) {
            final int kk = k;
            TextView t = new TextView(this);
            t.setText(TYPES[k][0] + "\n" + TYPES[k][1]);
            t.setTextSize(14);
            t.setGravity(Gravity.CENTER);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding(0, dp(10), 0, dp(10));
            t.setOnClickListener(v -> { if (t0 == 0) { type = kk; types(); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            tr.addView(t, lp);
            typeBtn[k] = t;
        }
        p.addView(tr);

        tvTime = new TextView(this);
        tvTime.setTextSize(56);
        tvTime.setTypeface(Typeface.DEFAULT_BOLD);
        tvTime.setGravity(Gravity.CENTER);
        tvTime.setTextColor(Color.parseColor("#0F172A"));
        tvTime.setText("00:00");
        tvTime.setPadding(0, dp(12), 0, dp(4));
        p.addView(tvTime);

        LinearLayout r1 = new LinearLayout(this);
        tvHr = stat(r1, "tętno teraz", "#DB2777");
        tvSteps = stat(r1, "kroki", "#2563EB");
        tvKcal = stat(r1, "kcal (szac.)", "#EA580C");
        p.addView(r1);

        bStart = new Button(this);
        bStart.setAllCaps(false);
        bStart.setTextSize(18);
        bStart.setTypeface(Typeface.DEFAULT_BOLD);
        bStart.setTextColor(Color.WHITE);
        bStart.setPadding(0, dp(16), 0, dp(16));
        bStart.setOnClickListener(v -> { if (t0 == 0) start(); else stop(); });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.setMargins(dp(4), dp(12), dp(4), dp(4));
        p.addView(bStart, bl);

        tvInfo = new TextView(this);
        tvInfo.setTextSize(13);
        tvInfo.setTextColor(Color.parseColor("#64748B"));
        tvInfo.setPadding(dp(8), dp(6), dp(8), dp(6));
        tvInfo.setText("Wybierz rodzaj i stuknij Start. Ekran telefonu może być wygaszony – trening liczy się dalej, "
                + "ale tętno na żywo zbiera się, gdy ten ekran jest otwarty.");
        p.addView(tvInfo);

        tvHist = new TextView(this);
        tvHist.setTextSize(14);
        tvHist.setTextColor(Color.parseColor("#263238"));
        tvHist.setPadding(dp(16), dp(14), dp(16), dp(14));
        tvHist.setBackground(round(Color.WHITE, 20));
        tvHist.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.setMargins(dp(4), dp(10), dp(4), dp(4));
        p.addView(tvHist, hl);

        setContentView(outer);

        // trening rozpoczęty wcześniej (ekran był zamknięty)
        t0 = sp().getLong("t0", 0);
        steps0 = sp().getLong("steps0", -1);
        type = sp().getInt("typ", 0);
        if (t0 > 0) { startHr(); main.post(tick); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
        types();
        history();
    }

    private void types() {
        for (int k = 0; k < 4; k++) {
            boolean on = k == type;
            typeBtn[k].setBackground(on ? grad(TYPES[k][3], TYPES[k][3], 18) : round(Color.WHITE, 18));
            typeBtn[k].setTextColor(on ? Color.WHITE : Color.parseColor("#334155"));
        }
        bStart.setText(t0 == 0 ? "▶  Start" : "⏹  Zakończ i zapisz");
        bStart.setBackground(t0 == 0 ? grad("#16A34A", "#15803D", 22) : grad("#DC2626", "#B91C1C", 22));
    }

    private static long num(String s) {
        try { return Long.parseLong(s.replaceAll("[^0-9]", "")); } catch (Exception e) { return -1; }
    }

    private long stepsNow() { return num(BleService.sSteps); }

    private void start() {
        if (BleService.instance == null || !BleService.instance.linked()) {
            tvInfo.setText("⚠️ Zegarek nie jest połączony – trening policzy tylko czas.");
        }
        t0 = System.currentTimeMillis();
        steps0 = stepsNow();
        hrSum = hrN = hrMax = 0;
        sp().edit().putLong("t0", t0).putLong("steps0", steps0).putInt("typ", type).apply();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        startHr();
        types();
        main.post(tick);
    }

    private void startHr() {
        if (!Sdk.ready) return;
        try {
            Sdk.m().startDetectHeart(Sdk.wr, d -> {
                int v = d.getData();
                if (v > 30 && v < 230) {
                    hrNow = v;
                    hrSum += v;
                    hrN++;
                    hrMax = Math.max(hrMax, v);
                }
            });
        } catch (Throwable t) {
            tvInfo.setText("Tętno na żywo niedostępne: " + t.getMessage());
        }
    }

    private void stopHr() {
        try { if (Sdk.ready) Sdk.m().stopDetectHeart(Sdk.wr); } catch (Throwable ignored) { }
    }

    private int tickN = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (t0 == 0) return;
            long sec = (System.currentTimeMillis() - t0) / 1000;
            tvTime.setText(sec >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
                    : String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60));
            tvHr.setText(hrNow > 0 ? String.valueOf(hrNow) : "–");
            long st = steps();
            tvSteps.setText(st >= 0 ? String.valueOf(st) : "–");
            tvKcal.setText(String.valueOf(kcal(sec)));
            if (++tickN % 20 == 0) {
                BleService s = BleService.instance;
                if (s != null) s.sendVeepoo(new byte[]{(byte) 0xD8, 0});
            }
            main.postDelayed(this, 1000);
        }
    };

    private long steps() {
        long now = stepsNow();
        if (steps0 < 0 || now < 0) return -1;
        return Math.max(0, now - steps0);
    }

    private int kcal(long sec) {
        double met = Double.parseDouble(TYPES[type][2]);
        float kg = getSharedPreferences("cele", MODE_PRIVATE).getFloat("waga", 80f);
        return (int) Math.round(met * kg * sec / 3600.0);
    }

    private void stop() {
        long sec = (System.currentTimeMillis() - t0) / 1000;
        long st = steps();
        stopHr();
        main.removeCallbacks(tick);
        try {
            JSONObject t = new JSONObject();
            t.put("typ", TYPES[type][1]);
            t.put("ikona", TYPES[type][0]);
            t.put("od", new SimpleDateFormat("HH:mm", Locale.US).format(new Date(t0)));
            t.put("sek", sec);
            if (st >= 0) { t.put("kroki", st); t.put("m", Math.round(st * 0.75)); }
            if (hrN > 0) { t.put("hrSr", hrSum / hrN); t.put("hrMax", hrMax); }
            t.put("kcal", kcal(sec));
            HistStore.addTraining(this, BleService.today(), t);
            HistStore.flush();
            tvInfo.setText(String.format(Locale.US, "✅ Zapisano: %s, %d min", TYPES[type][1], sec / 60));
        } catch (Exception e) {
            tvInfo.setText("❌ " + e.getMessage());
        }
        t0 = 0;
        sp().edit().clear().apply();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        types();
        history();
    }

    static String describe(JSONObject t) {
        long sec = t.optLong("sek");
        StringBuilder b = new StringBuilder();
        b.append(t.optString("ikona")).append("  ").append(t.optString("od")).append("  ").append(t.optString("typ"))
                .append(" · ").append(sec / 60).append(" min");
        if (t.has("kroki")) b.append(" · ").append(t.optLong("kroki")).append(" kroków")
                .append(String.format(Locale.US, " · %.2f km", t.optLong("m") / 1000.0));
        if (t.has("hrSr")) b.append("\n      tętno śr. ").append(t.optInt("hrSr")).append(", max ").append(t.optInt("hrMax"));
        b.append(" · ").append(t.optInt("kcal")).append(" kcal");
        return b.toString();
    }

    private void history() {
        StringBuilder b = new StringBuilder("Ostatnie treningi\n");
        Calendar c = Calendar.getInstance();
        int n = 0;
        for (int d = 0; d < 30 && n < 15; d++, c.add(Calendar.DAY_OF_MONTH, -1)) {
            String dk = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
            JSONArray a = HistStore.load(this, dk).optJSONArray("treningi");
            if (a == null) continue;
            for (int i = a.length() - 1; i >= 0; i--) {
                JSONObject t = a.optJSONObject(i);
                if (t == null) continue;
                b.append("\n").append(new SimpleDateFormat("dd.MM", Locale.US).format(c.getTime())).append("  ").append(describe(t));
                n++;
            }
        }
        if (n == 0) b.append("\nJeszcze nie ma żadnego treningu.");
        tvHist.setText(b.toString());
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(tick);
        stopHr();
        super.onDestroy();
    }
}
