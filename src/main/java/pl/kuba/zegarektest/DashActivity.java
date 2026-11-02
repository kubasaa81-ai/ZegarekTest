package pl.kuba.zegarektest;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
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

/** Główny ekran: kafelki z wynikami i wykresy dnia. */
public class DashActivity extends Activity implements BleService.Listener {

    private TextView tvState, tvDate;
    private final TextView[] tileVal = new TextView[10];
    private final TextView[] tileSub = new TextView[10];
    private ChartView chHr, chBp, chSpo2, chTemp;
    private final Calendar day = Calendar.getInstance();
    private boolean hrRunning = false;
    private final Runnable refresher = this::refresh;

    private static final String[] TITLES = {"Kroki", "Dystans", "Kalorie", "Tętno", "Ciśnienie",
            "Tlen (SpO2)", "Temperatura", "Glukoza (szac.)", "Oddech", "Bateria zegarka"};
    private static final String[] COLORS = {"#1E88E5", "#3949AB", "#F4511E", "#D81B60", "#FB8C00",
            "#5E35B1", "#E53935", "#C2185B", "#00897B", "#43A047"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        requestPerms();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private Button btn(String text, String color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(48));
        b.setMinimumHeight(dp(48));
        b.setPadding(dp(6), dp(6), dp(6), dp(6));
        b.setBackground(round(Color.parseColor(color), 14));
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout row(View... vs) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (View v : vs) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            r.addView(v, lp);
        }
        return r;
    }

    private View tile(int i) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setBackground(round(Color.parseColor(COLORS[i]), 18));
        TextView title = new TextView(this);
        title.setText(TITLES[i]);
        title.setTextColor(Color.parseColor("#E8FFFFFF"));
        title.setTextSize(13);
        TextView val = new TextView(this);
        val.setTextColor(Color.WHITE);
        val.setTextSize(24);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        val.setText("–");
        TextView sub = new TextView(this);
        sub.setTextColor(Color.parseColor("#D8FFFFFF"));
        sub.setTextSize(12);
        t.addView(title);
        t.addView(val);
        t.addView(sub);
        tileVal[i] = val;
        tileSub[i] = sub;
        if (i == 3) t.setOnClickListener(v -> toggleHr());
        return t;
    }

    private TextView section(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#263238"));
        t.setPadding(dp(6), dp(14), 0, dp(4));
        return t;
    }

    private ChartView chart(LinearLayout root) {
        ChartView c = new ChartView(this);
        c.setBackground(round(Color.WHITE, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170));
        lp.setMargins(dp(4), 0, dp(4), dp(4));
        root.addView(c, lp);
        return c;
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#F1F5F9"));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(30), dp(10), dp(20));
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("Mój zegarek JS300");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#0D47A1"));
        title.setPadding(dp(6), 0, 0, 0);
        root.addView(title);

        tvState = new TextView(this);
        tvState.setTextSize(15);
        tvState.setTypeface(Typeface.DEFAULT_BOLD);
        tvState.setPadding(dp(6), dp(2), 0, dp(6));
        root.addView(tvState);

        Button prev = btn("‹", "#546E7A", v -> shiftDay(-1));
        Button next = btn("›", "#546E7A", v -> shiftDay(1));
        tvDate = new TextView(this);
        tvDate.setTextSize(18);
        tvDate.setTypeface(Typeface.DEFAULT_BOLD);
        tvDate.setGravity(Gravity.CENTER);
        tvDate.setTextColor(Color.parseColor("#263238"));
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpb = new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT);
        lpb.setMargins(dp(4), dp(4), dp(4), dp(4));
        dateRow.addView(prev, lpb);
        dateRow.addView(tvDate, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lpb2 = new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT);
        lpb2.setMargins(dp(4), dp(4), dp(4), dp(4));
        dateRow.addView(next, lpb2);
        root.addView(dateRow);

        for (int i = 0; i < 10; i += 2) root.addView(row(tile(i), tile(i + 1)));

        root.addView(section("Tętno w ciągu dnia"));
        chHr = chart(root);
        root.addView(section("Ciśnienie (skurczowe / rozkurczowe)"));
        chBp = chart(root);
        root.addView(section("Tlen we krwi"));
        chSpo2 = chart(root);
        root.addView(section("Temperatura ciała"));
        chTemp = chart(root);

        root.addView(row(
                btn("Odśwież", "#00897B", v -> refreshWatch()),
                btn("Bateria bez limitu", "#EF6C00", v -> askBattery())
        ));
        root.addView(row(
                btn("Połącz ponownie", "#2E7D32", v -> { stopBle(); tvState.postDelayed(this::startBle, 1500); }),
                btn("Tryb testowy", "#455A64", v -> startActivity(new Intent(this, MainActivity.class)))
        ));

        setContentView(sv);
    }

    // ---------------- dane ----------------

    private String dayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(day.getTime());
    }

    private boolean isToday() {
        return dayKey().equals(BleService.today());
    }

    private void shiftDay(int delta) {
        day.add(Calendar.DAY_OF_MONTH, delta);
        if (day.after(Calendar.getInstance())) day.setTime(Calendar.getInstance().getTime());
        refresh();
    }

    private static String strip(String s) {
        int i = s.indexOf(" (");
        return i > 0 ? s.substring(0, i) : s;
    }

    private static String when(String s) {
        int i = s.indexOf(" (");
        return i > 0 ? s.substring(i + 2, s.length() - 1) : "";
    }

    private void set(int i, String val, String sub) {
        tileVal[i].setText(val);
        tileSub[i].setText(sub);
    }

    private void refresh() {
        String st = BleService.state;
        tvState.setText(st);
        tvState.setTextColor(Color.parseColor(st.startsWith("Połączony") ? "#2E7D32"
                : (st.contains("Czekam") || st.startsWith("Rozłączony")) ? "#EF6C00" : "#C62828"));
        tvDate.setText((isToday() ? "Dziś, " : "") + new SimpleDateFormat("dd.MM.yyyy", Locale.US).format(day.getTime()));

        JSONObject o = HistStore.load(this, dayKey());
        JSONObject recs = o.optJSONObject("r");
        TreeMap<String, JSONObject> sorted = new TreeMap<>();
        if (recs != null) {
            Iterator<String> it = recs.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject r = recs.optJSONObject(k);
                if (r != null) sorted.put(k, r);
            }
        }
        List<float[]> hr = new ArrayList<>(), sys = new ArrayList<>(), dia = new ArrayList<>(),
                spo2 = new ArrayList<>(), temp = new ArrayList<>();
        String lastBp = null, lastSpo2 = null, lastTemp = null, lastGlu = null, lastResp = null;
        String tBp = "", tSpo2 = "", tTemp = "", tGlu = "", tResp = "";
        int hrMin = 999, hrMax = 0, hrSum = 0, hrN = 0;
        for (String k : sorted.keySet()) {
            JSONObject r = sorted.get(k);
            float m = Integer.parseInt(k.substring(0, 2)) * 60 + Integer.parseInt(k.substring(3, 5));
            if (r.has("hr")) {
                int v = r.optInt("hr");
                hr.add(new float[]{m, v});
                hrMin = Math.min(hrMin, v); hrMax = Math.max(hrMax, v); hrSum += v; hrN++;
            }
            if (r.has("sys")) {
                sys.add(new float[]{m, r.optInt("sys")});
                dia.add(new float[]{m, r.optInt("dia")});
                lastBp = r.optInt("sys") + "/" + r.optInt("dia"); tBp = k;
            }
            if (r.has("spo2")) { spo2.add(new float[]{m, r.optInt("spo2")}); lastSpo2 = r.optInt("spo2") + "%"; tSpo2 = k; }
            if (r.has("temp")) { temp.add(new float[]{m, (float) r.optDouble("temp")}); lastTemp = String.format(Locale.US, "%.1f °C", r.optDouble("temp")); tTemp = k; }
            if (r.has("glu")) { lastGlu = String.format(Locale.US, "%.2f", r.optDouble("glu")); tGlu = k; }
            if (r.has("resp")) { lastResp = r.optInt("resp") + "/min"; tResp = k; }
        }

        boolean today = isToday();
        String steps = today && !BleService.sSteps.equals("–") ? BleService.sSteps : (o.has("steps") ? String.valueOf(o.optLong("steps")) : "–");
        String dist = today && !BleService.sDist.equals("–") ? BleService.sDist : (o.has("m") ? String.format(Locale.US, "%.2f km", o.optLong("m") / 1000.0) : "–");
        String kcal = today && !BleService.sKcal.equals("–") ? BleService.sKcal : (o.has("kcal") ? o.optLong("kcal") + " kcal" : "–");
        set(0, steps, "cel: 30000");
        set(1, dist, "");
        set(2, kcal, "");

        String hrSub = hrN > 0 ? "śr. " + (hrSum / hrN) + "  min " + hrMin + "  max " + hrMax : "";
        if (today && BleService.sHr.contains("na żywo")) {
            set(3, strip(BleService.sHr), "pomiar trwa – dotknij, by zatrzymać");
        } else if (today) {
            set(3, BleService.sHr.equals("–") ? "–" : strip(BleService.sHr), hrRunning ? "czekam na wynik…" : (hrSub.isEmpty() ? "dotknij, by zmierzyć" : hrSub + "\ndotknij, by zmierzyć"));
        } else {
            set(3, hrN > 0 ? String.valueOf(hrSum / hrN) : "–", hrSub);
        }
        set(4, lastBp != null ? lastBp : "–", lastBp != null ? "o " + tBp + " (mmHg)" : "");
        set(5, lastSpo2 != null ? lastSpo2 : "–", lastSpo2 != null ? "o " + tSpo2 : "");
        set(6, lastTemp != null ? lastTemp : "–", lastTemp != null ? "o " + tTemp : "");
        set(7, lastGlu != null ? lastGlu : "–", lastGlu != null ? "mmol/L o " + tGlu + " – orientacyjnie" : "");
        set(8, lastResp != null ? lastResp : "–", lastResp != null ? "o " + tResp : "");
        set(9, today ? BleService.sBat : "–", today ? "skala zegarka" : "");

        List<ChartView.Series> l1 = new ArrayList<>();
        l1.add(ChartView.series(hr, Color.parseColor("#D81B60")));
        chHr.setData(l1, 40, 160, "");
        List<ChartView.Series> l2 = new ArrayList<>();
        l2.add(ChartView.series(sys, Color.parseColor("#FB8C00")));
        l2.add(ChartView.series(dia, Color.parseColor("#3949AB")));
        chBp.setData(l2, 50, 170, "");
        List<ChartView.Series> l3 = new ArrayList<>();
        l3.add(ChartView.series(spo2, Color.parseColor("#5E35B1")));
        chSpo2.setData(l3, 85, 100, "");
        List<ChartView.Series> l4 = new ArrayList<>();
        l4.add(ChartView.series(temp, Color.parseColor("#E53935")));
        chTemp.setData(l4, 34, 38, "");
    }

    // ---------------- akcje ----------------

    private void send(int... bytes) {
        BleService s = BleService.instance;
        if (s == null) { toast("Brak połączenia z zegarkiem"); return; }
        byte[] d = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) d[i] = (byte) bytes[i];
        if (!s.sendVeepoo(d)) toast("Zegarek jeszcze się łączy – spróbuj za chwilę");
    }

    private void toggleHr() {
        if (!isToday()) return;
        if (hrRunning || BleService.sHr.contains("na żywo")) {
            send(0xD0, 0x00);
            hrRunning = false;
            if (BleService.sHr.contains("na żywo")) BleService.sHr = BleService.sHr.replace("na żywo", "ostatni pomiar");
            toast("Pomiar tętna zatrzymany");
        } else {
            send(0xD0, 0x01);
            hrRunning = true;
            toast("Pomiar tętna – wynik po ok. 20–30 s");
        }
        refresh();
    }

    private void refreshWatch() {
        send(0xD8, 0x00);
        send(0x96, 0x01);
        send(0xDF, 0x00);
        toast("Pobieram dane z zegarka…");
    }

    private void askBattery() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            toast("Już ustawione: bateria bez limitu");
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    // ---------------- uprawnienia i usługa ----------------

    private void requestPerms() {
        List<String> p = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            p.add(Manifest.permission.BLUETOOTH_SCAN);
            p.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS);
        List<String> need = new ArrayList<>();
        for (String s : p) if (checkSelfPermission(s) != PackageManager.PERMISSION_GRANTED) need.add(s);
        if (need.isEmpty()) startBle();
        else requestPermissions(need.toArray(new String[0]), 1);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (hasConnect()) startBle();
        else toast("Bez uprawnienia „Urządzenia w pobliżu” aplikacja nie połączy się z zegarkiem");
    }

    private boolean hasConnect() {
        return Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void startBle() {
        if (!hasConnect()) return;
        startForegroundService(new Intent(this, BleService.class));
    }

    private void stopBle() {
        Intent i = new Intent(this, BleService.class);
        i.setAction(BleService.ACTION_STOP);
        startService(i);
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
        HistStore.flush();
        super.onPause();
    }

    @Override
    public void onLog(String line) { }

    @Override
    public void onStateChanged() {
        if (BleService.sHr.contains("na żywo")) hrRunning = false;
        tvState.removeCallbacks(refresher);
        tvState.postDelayed(refresher, 400);
    }

    private void toast(String t) {
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show();
    }
}
