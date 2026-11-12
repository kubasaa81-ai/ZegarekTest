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
    private int screenWanted = 0;
    private TextView tvScreen;
    private final android.widget.EditText[] thEdit = new android.widget.EditText[BleService.TH.length];
    private Button btnAlarmOn;
    private final Runnable refresher = this::refresh;

    private static final String[] TITLES = {"Kroki", "Dystans", "Kalorie", "Tętno", "Ciśnienie",
            "Tlen (SpO2)", "Temperatura", "Glukoza (szac.)", "Oddech", "Bateria zegarka"};
    private static final String[] COLORS = {"#2F80ED", "#4F46E5", "#FF6B3D", "#FF3D77", "#FF9F1C",
            "#7C3AED", "#F43F5E", "#E11D74", "#0EA5A4", "#22C55E"};
    private static final String[] COLORS2 = {"#56CCF2", "#818CF8", "#FFA36C", "#FF8FB1", "#FFC857",
            "#A78BFA", "#FB7185", "#F472B6", "#5EEAD4", "#86EFAC"};
    private static final String[] ICONS = {"👣", "📍", "🔥", "❤️", "🩺", "🫁", "🌡️", "🩸", "🌬️", "🔋"};
    private RingView ring;
    private TextView tvRingSide, tvSleep;

    private void refreshSleep() {
        Calendar c = (Calendar) day.clone();
        for (int k = 0; k < 7; k++, c.add(Calendar.DAY_OF_MONTH, -1)) {
            String dk = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
            JSONObject sl = HistStore.load(this, dk).optJSONObject("sleep");
            if (sl == null) continue;
            int all = sl.optInt("min"), deep = sl.optInt("gleboki"), light = sl.optInt("plytki");
            int q = sl.optInt("jakosc");
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < 5; i++) stars.append(i < q ? "★" : "☆");
            float goal = getSharedPreferences("cele", MODE_PRIVATE).getFloat("sen", 7f);
            tvSleep.setText("😴  Sen " + (k == 0 ? "(ostatnia noc)" : "z " + new SimpleDateFormat("dd.MM", Locale.US).format(c.getTime()))
                    + "\n" + String.format(Locale.US, "%dh %02dmin", all / 60, all % 60) + "   " + sl.optString("od") + " → " + sl.optString("do")
                    + "   " + stars
                    + String.format(Locale.US, "\nGłęboki %dh %02dmin · Płytki %dh %02dmin · Pobudki: %d", deep / 60, deep % 60, light / 60, light % 60, sl.optInt("pobudki"))
                    + (all < goal * 60 ? String.format(Locale.US, "\nDo celu %.0f h zabrakło %d min", goal, Math.round(goal * 60) - all) : "\n✅ Cel snu osiągnięty")
                    + "\n👉 Dotknij – wykres faz i tydzień");
            return;
        }
        tvSleep.setText("😴  Sen: brak danych\nPobierze się sam rano · 👉 Dotknij, by otworzyć zakładkę Sen");
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        requestPerms();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable grad(String c1, String c2, int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor(c1), Color.parseColor(c2)});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private static String darker(String hex) {
        int c = Color.parseColor(hex);
        return String.format("#%02X%02X%02X", (int) (Color.red(c) * 0.75), (int) (Color.green(c) * 0.75), (int) (Color.blue(c) * 0.75));
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
        b.setBackground(grad(color, darker(color), 24));
        b.setElevation(dp(2));
        b.setStateListAnimator(null);
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
        t.setBackground(grad(COLORS[i], COLORS2[i], 22));
        t.setElevation(dp(3));
        t.setMinimumHeight(dp(104));
        TextView title = new TextView(this);
        title.setText(ICONS[i] + "  " + TITLES[i]);
        title.setTextColor(Color.parseColor("#E8FFFFFF"));
        title.setTextSize(13);
        TextView val = new TextView(this);
        val.setTextColor(Color.WHITE);
        val.setTextSize(26);
        val.setPadding(0, dp(4), 0, 0);
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
        final String[] metric = {"steps", "steps", "steps", "hr", "bp", "spo2", "temp", "glu", "resp", null};
        if (metric[i] != null) t.setOnClickListener(v -> startActivity(
                new Intent(this, DetailActivity.class).putExtra(DetailActivity.EXTRA, metric[i])));
        return t;
    }

    private TextView section(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextColor(Color.parseColor("#3D4B63"));
        t.setLetterSpacing(0.02f);
        t.setPadding(dp(8), dp(18), 0, dp(6));
        return t;
    }

    private ChartView chart(LinearLayout root) {
        ChartView c = new ChartView(this);
        c.setBackground(round(Color.WHITE, 22));
        c.setElevation(dp(2));
        c.setPadding(dp(6), dp(8), dp(6), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180));
        lp.setMargins(dp(4), 0, dp(4), dp(4));
        root.addView(c, lp);
        return c;
    }

    private final LinearLayout[] pages = new LinearLayout[4];
    private final Button[] navBtn = new Button[4];
    private ScrollView sv;
    private TextView tvDate2, tvSportSum, tvSportList;
    private ChartView chSteps;
    private android.widget.EditText etGoalSteps, etGoalSleep;
    private int page = 0;
    private TextView tvWrite, tvHc, tvWeek, tvNotif;

    private void testMsg(int type, String name, String content) {
        BleService s = BleService.instance;
        if (s == null) { toast("Brak połączenia z zegarkiem"); return; }
        s.sendMessage(type, name, content);
        if (type == 0x00) tvState.postDelayed(() -> { BleService b = BleService.instance; if (b != null) b.stopCall(); }, 10000);
        toast(type == 0x00 ? "Zegarek powinien dzwonić ok. 10 s" : "Wysłano – spójrz na zegarek");
    }

    private void notifStatus() {
        boolean calls = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
        boolean contacts = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        boolean apps = NotifService.enabled(this);
        sub(tvCalls, calls ? (contacts ? "✅ Włączone – imię z kontaktów" : "✅ Włączone (bez kontaktów: sam numer)") : "⬜ Wyłączone – dotknij, by włączyć");
        sub(tvApps, apps ? "✅ Włączone" : "⬜ Wyłączone – dotknij, by włączyć");
        tvNotif.setText(apps ? "Wyłącz powiadomienia w HBand albo ją odinstaluj, żeby nie przychodziły podwójnie." : "");
        tvNotif.setVisibility(apps ? View.VISIBLE : View.GONE);
        PowerManager pm = getSystemService(PowerManager.class);
        boolean unl = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        sub(tvBatOpt, unl ? "✅ Ustawione – Android nie usypia połączenia" : "⚠️ Dotknij – inaczej telefon może zrywać połączenie");
    }

    private ChartView chWeek;

    private long daySteps(String dk) {
        JSONObject o = HistStore.load(this, dk);
        long sum = 0;
        JSONObject recs = o.optJSONObject("r");
        if (recs != null) {
            Iterator<String> it = recs.keys();
            while (it.hasNext()) { JSONObject r = recs.optJSONObject(it.next()); if (r != null) sum += r.optInt("st", 0); }
        }
        return Math.max(sum, o.optLong("steps", 0));
    }

    private void refreshWeek(int goal) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -6);
        String[] wd = {"nd", "pn", "wt", "śr", "cz", "pt", "sb"};
        String[] lbl = new String[7];
        List<float[]> pts = new ArrayList<>();
        long top = 0, sum = 0;
        int n = 0, hit = 0, streak = 0;
        for (int i = 0; i < 7; i++, c.add(Calendar.DAY_OF_MONTH, 1)) {
            lbl[i] = wd[c.get(Calendar.DAY_OF_WEEK) - 1];
            long st = daySteps(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime()));
            if (i == 6 && !BleService.sSteps.equals("–")) {
                try { st = Math.max(st, Long.parseLong(BleService.sSteps)); } catch (Exception ignored) { }
            }
            if (st > 0) { pts.add(new float[]{i + 0.5f, st}); n++; sum += st; }
            top = Math.max(top, st);
            if (st >= goal) { hit++; streak++; } else if (i < 6) streak = 0;
        }
        chWeek.setSlots(lbl);
        chWeek.setGoal(goal);
        List<ChartView.Series> ls = new ArrayList<>();
        ls.add(ChartView.series(pts, Color.parseColor("#2F80ED")));
        chWeek.setData(ls, 0, Math.max(goal * 1.1f, top * 1.1f), "");
        tvWeek.setText(n == 0 ? "Historia tygodnia zbiera się od dnia instalacji."
                : "Średnio: " + (sum / n) + " kroków dziennie\nCel osiągnięty: " + hit + " z 7 dni"
                + (streak > 1 ? "\n🔥 Seria: " + streak + " dni z rzędu" : "")
                + "\nDotknij kafelka Kroki na Pulpicie, by zobaczyć miesiąc.");
    }

    private void hcStatus() {
        if (!HcSync.available(this)) {
            tvHc.setText("Health Connect nie jest dostępny na tym telefonie.");
            return;
        }
        BleService.appendLog(this, "Health Connect: status " + HcSync.statusText(this));
        HcSync.grantedText(this, t -> BleService.appendLog(this, "Health Connect: " + t));
        HcSync.checkGranted(this, ok -> {
            getSharedPreferences("hc", MODE_PRIVATE).edit().putBoolean("on", ok).apply();
            tvHc.setText(ok
                    ? "✅ Połączono. Dane z zegarka (kroki, tętno, ciśnienie, tlen, temperatura) wysyłają się automatycznie po każdym pobraniu historii.\nW Samsung Health włącz odczyt: Ustawienia → Health Connect → Mój Zegarek."
                    : "Nie połączono. Stuknij „Połącz z Health Connect” i zaznacz wszystkie zgody.\nJeśli okno się nie otworzy: Ustawienia telefonu → Health Connect → Uprawnienia aplikacji → Mój Zegarek → Zezwól na wszystko.");
        });
    }

    private void hcConnect() {
        if (!HcSync.available(this)) { toast("Health Connect niedostępny"); return; }
        try {
            Intent i = HcSync.permissionIntent(this);
            BleService.appendLog(this, "Health Connect: otwieram okno zgód " + i.getAction() + " / " + i.getPackage());
            startActivityForResult(i, 77);
        } catch (Exception e) {
            BleService.appendLog(this, "Health Connect: nie otworzył się – " + e);
            toast("Nie udało się otworzyć okna zgód – nadaj je ręcznie w Health Connect (opis poniżej)");
            try {
                startActivity(new Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
                        .putExtra(Intent.EXTRA_PACKAGE_NAME, getPackageName()));
            } catch (Exception e2) {
                try { startActivity(new Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")); } catch (Exception ignored) { }
            }
        }
    }

    private void hcSend() {
        HistStore.flush();
        toast("Wysyłam…");
        HcSync.syncRecent(this, msg -> { toast(msg); hcStatus(); });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 78 && res == RESULT_OK && data != null && data.getData() != null) {
            Backup.restore(this, data.getData());
            refresh();
            return;
        }
        if (req == 77) {
            BleService.appendLog(this, "Health Connect: okno zgód zamknięte, kod " + res);
            hcStatus();
            tvHc.postDelayed(this::hcSend, 800);
        }
    }

    private void setFace(int idx) {
        send(0xC7, 0x01, idx);
        toast("Zmieniam tarczę na " + (idx + 1));
    }

    private LinearLayout page() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(10), dp(4), dp(10), dp(20));
        return p;
    }

    private LinearLayout dateRow(boolean second) {
        Button prev = btn("‹", "#546E7A", v -> shiftDay(-1));
        Button next = btn("›", "#546E7A", v -> shiftDay(1));
        TextView t = new TextView(this);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.parseColor("#263238"));
        if (second) tvDate2 = t; else tvDate = t;
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT);
        l1.setMargins(dp(4), dp(4), dp(4), dp(4));
        r.addView(prev, l1);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT);
        l2.setMargins(dp(4), dp(4), dp(4), dp(4));
        r.addView(next, l2);
        return r;
    }

    private TextView tvBatChip;
    private android.widget.EditText etWeight;
    private LinearLayout dialChips;
    private String dialMsg = "";

    private TextView dialChip(String text, String color, boolean sel, View.OnClickListener l) {
        TextView c = new TextView(this);
        c.setText(text);
        c.setTextSize(15);
        c.setTypeface(Typeface.DEFAULT_BOLD);
        c.setTextColor(Color.WHITE);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(14), 0, dp(14), 0);
        c.setMinWidth(dp(44));
        GradientDrawable g = grad(color, darker(color), 22);
        if (sel) g.setStroke(dp(3), Color.parseColor("#FBBF24"));
        c.setBackground(g);
        c.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        lp.setMargins(0, 0, dp(6), 0);
        dialChips.addView(c, lp);
        return c;
    }

    private void buildDialChips() {
        if (dialChips == null) return;
        dialChips.removeAllViews();
        String cur = Dials.current;
        if (Dials.loaded) {
            for (int k = 0; k < Dials.sys.size(); k++) {
                final int kk = k;
                dialChip(String.valueOf(k + 1), "#3949AB", cur.equals("s" + k), v -> pickDial('s', kk, "wbudowana " + (kk + 1)));
            }
            for (int k = 0; k < Dials.srv.size(); k++) {
                final int kk = k;
                dialChip("⬇️ " + Dials.label(Dials.srv.get(k), k), "#7C3AED", cur.equals("w" + k), v -> pickDial('w', kk, "wgrana " + (kk + 1)));
            }
            if (Dials.hasPic) dialChip("🖼️ Zdjęcie", "#DB2777", cur.equals("p"), v -> pickDial('p', 0, "ze zdjęcia"));
        } else {
            for (int k = 0; k < 4; k++) {
                final int kk = k;
                dialChip(String.valueOf(k + 1), "#3949AB", BleService.faceIdx == k, v -> setFace(kk));
            }
            dialChip("🖼️ Zdjęcie", "#DB2777", false, v -> pickDial('p', 0, "ze zdjęcia"));
        }
    }

    private void loadDials(boolean force) {
        if (!Sdk.ready) { if (force) toast("Zegarek nie jest teraz połączony"); return; }
        if (Dials.loaded && !force) return;
        if (force) Dials.reset();
        dialMsg = "Wczytuję tarcze z zegarka…";
        refresh();
        Dials.load(x -> BleService.appendLog(this, "Tarcze: " + x), () -> {
            dialMsg = Dials.loaded
                    ? "Wbudowane: " + Dials.sys.size() + " · wgrane: " + Dials.srv.size() + (Dials.hasPic ? " · ze zdjęcia" : "")
                    : "Nie udało się wczytać listy – dotknij ↻";
            buildDialChips();
            refresh();
        });
    }

    private void pickDial(char kind, int idx, String name) {
        if (!Sdk.ready) { toast("Zegarek nie jest teraz połączony"); return; }
        if (!Dials.loaded) {
            toast("Wczytuję tarcze…");
            Dials.load(x -> { }, () -> { buildDialChips(); if (Dials.loaded) pickDial(kind, idx, name); });
            return;
        }
        if (Dials.select(kind, idx)) {
            dialMsg = "Teraz: tarcza " + name;
            toast("Zmieniam tarczę: " + name);
        } else toast("Nie udało się zmienić tarczy");
        buildDialChips();
        refresh();
    }
    private TextView tvHeroState, tvHeroBat, tvCalls, tvApps, tvBatOpt, tvMode;

    private LinearLayout group(LinearLayout p) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(round(Color.WHITE, 22));
        g.setElevation(dp(2));
        g.setPadding(0, dp(4), 0, dp(4));
        addCard(p, g);
        return g;
    }

    private void divider(LinearLayout g) {
        View d = new View(this);
        d.setBackgroundColor(Color.parseColor("#EEF1F6"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(dp(66), 0, dp(14), 0);
        g.addView(d, lp);
    }

    /** Wiersz: kolorowa ikona, tytuł, podpis. Zwraca wiersz; podpis w getTag(). */
    private LinearLayout rowBox(LinearLayout g, String ico, String color, String title) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(12), dp(14), dp(12));
        r.setMinimumHeight(dp(64));
        TextView ic = new TextView(this);
        ic.setText(ico);
        ic.setTextSize(19);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(grad(color, darker(color), 14));
        r.addView(ic, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(8), 0);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTextColor(Color.parseColor("#0F172A"));
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        col.addView(t);
        TextView sub = new TextView(this);
        sub.setTextSize(13);
        sub.setTextColor(Color.parseColor("#64748B"));
        col.addView(sub);
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.setTag(sub);
        g.addView(r);
        return r;
    }

    private TextView setRow(LinearLayout g, String ico, String color, String title, String sub, View.OnClickListener l) {
        if (g.getChildCount() > 0) divider(g);
        LinearLayout r = rowBox(g, ico, color, title);
        TextView s = (TextView) r.getTag();
        s.setText(sub);
        if (sub.isEmpty()) s.setVisibility(View.GONE);
        TextView ch = new TextView(this);
        ch.setText("›");
        ch.setTextSize(24);
        ch.setTextColor(Color.parseColor("#94A3B8"));
        r.addView(ch);
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        r.setForeground(getDrawable(tv.resourceId));
        r.setOnClickListener(l);
        return s;
    }

    private TextView stepBtn(String t, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(t);
        b.setTextSize(20);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(Color.parseColor("#B45309"));
        b.setBackground(round(Color.parseColor("#FEF3C7"), 19));
        b.setOnClickListener(l);
        return b;
    }

    private void sub(TextView t, String s) {
        if (t == null) return;
        t.setText(s);
        t.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void modeLabel() {
        sub(tvMode, BleService.useSdk(this) ? "Jedno połączenie (zalecane) ✓ · dotknij, by zmienić" : "Stare połączenie · dotknij, by włączyć zalecane");
    }

    private TextView card(String txt) {
        TextView t = new TextView(this);
        t.setText(txt);
        t.setTextSize(15);
        t.setTextColor(Color.parseColor("#263238"));
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setBackground(round(Color.WHITE, 22));
        t.setElevation(dp(2));
        t.setLineSpacing(dp(3), 1f);
        return t;
    }

    private void addCard(LinearLayout p, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(2), dp(4), dp(4));
        p.addView(v, lp);
    }

    private android.widget.EditText numEdit(String val) {
        android.widget.EditText e = new android.widget.EditText(this);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setTextSize(16);
        e.setGravity(Gravity.CENTER);
        e.setSingleLine(true);
        e.setText(val);
        return e;
    }

    private LinearLayout labelRow(String label, View right) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(15);
        l.setTextColor(Color.parseColor("#263238"));
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.2f));
        r.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return r;
    }

    private void buildUi() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.parseColor("#EEF2F8"));
        getWindow().setStatusBarColor(Color.parseColor("#0B3D91"));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(34), dp(20), dp(16));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#0B3D91"), Color.parseColor("#1565C0"), Color.parseColor("#00A3C4")});
        hg.setCornerRadii(new float[]{0, 0, 0, 0, dp(28), dp(28), dp(28), dp(28)});
        head.setBackground(hg);
        head.setElevation(dp(4));
        TextView hello = new TextView(this);
        hello.setText("Witaj, Kuba 👋");
        hello.setTextSize(14);
        hello.setTextColor(Color.parseColor("#CFE8FF"));
        head.addView(hello);
        TextView title = new TextView(this);
        title.setText("Mój zegarek JS300");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        head.addView(title);
        tvState = new TextView(this);
        tvState.setTextSize(13);
        tvState.setTypeface(Typeface.DEFAULT_BOLD);
        tvState.setPadding(dp(12), dp(5), dp(12), dp(5));
        LinearLayout chips0 = new LinearLayout(this);
        chips0.setOrientation(LinearLayout.HORIZONTAL);
        chips0.addView(tvState);
        tvBatChip = new TextView(this);
        tvBatChip.setTextSize(13);
        tvBatChip.setTypeface(Typeface.DEFAULT_BOLD);
        tvBatChip.setTextColor(Color.WHITE);
        tvBatChip.setPadding(dp(12), dp(5), dp(12), dp(5));
        LinearLayout.LayoutParams bc = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bc.setMargins(dp(8), 0, 0, 0);
        chips0.addView(tvBatChip, bc);
        LinearLayout.LayoutParams chip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chip.setMargins(0, dp(8), 0, 0);
        head.addView(chips0, chip);
        outer.addView(head);

        sv = new ScrollView(this);
        outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- Pulpit ----
        LinearLayout p0 = page();
        p0.addView(dateRow(false));
        LinearLayout ringCard = new LinearLayout(this);
        ringCard.setGravity(Gravity.CENTER_VERTICAL);
        ringCard.setPadding(dp(10), dp(10), dp(16), dp(10));
        ringCard.setBackground(round(Color.WHITE, 26));
        ringCard.setElevation(dp(3));
        ring = new RingView(this);
        ringCard.addView(ring, new LinearLayout.LayoutParams(dp(170), dp(170)));
        tvRingSide = new TextView(this);
        tvRingSide.setTextSize(15);
        tvRingSide.setTextColor(Color.parseColor("#3D4B63"));
        tvRingSide.setLineSpacing(dp(6), 1f);
        LinearLayout.LayoutParams rs = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rs.setMargins(dp(12), 0, 0, 0);
        ringCard.addView(tvRingSide, rs);
        addCard(p0, ringCard);
        tvSleep = card("");
        tvSleep.setTextSize(15);
        tvSleep.setOnClickListener(v -> startActivity(new Intent(this, SleepActivity.class)));
        addCard(p0, tvSleep);
        p0.addView(row(
                btn("🩺 Zmierz teraz", "#E11D48", v -> startActivity(new Intent(this, MeasureActivity.class))),
                btn("⏰ Przypomnienia", "#0D9488", v -> startActivity(new Intent(this, RemindActivity.class)))
        ));
        for (int i = 0; i < 8; i += 2) p0.addView(row(tile(i), tile(i + 1)));
        p0.addView(row(tile(8)));
        p0.addView(section("Tętno w ciągu dnia"));
        chHr = chart(p0);
        p0.addView(section("Ciśnienie (skurczowe / rozkurczowe)"));
        chBp = chart(p0);
        p0.addView(section("Tlen we krwi"));
        chSpo2 = chart(p0);
        p0.addView(section("Temperatura ciała"));
        chTemp = chart(p0);
        pages[0] = p0;

        // ---- Sport ----
        LinearLayout p1 = page();
        p1.addView(dateRow(true));
        tvSportSum = card("");
        tvSportSum.setTextSize(17);
        addCard(p1, tvSportSum);
        p1.addView(row(
                btn("🏃 Trening", "#EA580C", v -> startActivity(new Intent(this, TrainingActivity.class))),
                btn("📈 30 dni", "#1D4ED8", v -> startActivity(new Intent(this, TrendActivity.class)))
        ));
        p1.addView(section("Kroki co 30 minut"));
        chSteps = chart(p1);
        chSteps.setBars(true);
        p1.addView(section("Ostatnie 7 dni"));
        chWeek = chart(p1);
        chWeek.setBars(true);
        tvWeek = card("");
        tvWeek.setTextSize(14);
        addCard(p1, tvWeek);
        p1.addView(section("Szczegóły"));
        tvSportList = card("");
        tvSportList.setTypeface(Typeface.MONOSPACE);
        addCard(p1, tvSportList);
        pages[1] = p1;

        // ---- Urządzenie ----
        LinearLayout p2 = page();

        // karta zegarka
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(grad("#4F46E5", "#7C3AED", 26));
        hero.setElevation(dp(4));
        TextView watchIco = new TextView(this);
        watchIco.setText("⌚");
        watchIco.setTextSize(40);
        watchIco.setGravity(Gravity.CENTER);
        watchIco.setBackground(round(Color.parseColor("#26FFFFFF"), 40));
        hero.addView(watchIco, new LinearLayout.LayoutParams(dp(74), dp(74)));
        LinearLayout hcol = new LinearLayout(this);
        hcol.setOrientation(LinearLayout.VERTICAL);
        hcol.setPadding(dp(16), 0, 0, 0);
        TextView hName = new TextView(this);
        hName.setText("JS300");
        hName.setTextSize(22);
        hName.setTypeface(Typeface.DEFAULT_BOLD);
        hName.setTextColor(Color.WHITE);
        hcol.addView(hName);
        tvHeroState = new TextView(this);
        tvHeroState.setTextSize(14);
        tvHeroState.setTextColor(Color.parseColor("#E0E7FF"));
        hcol.addView(tvHeroState);
        tvHeroBat = new TextView(this);
        tvHeroBat.setTextSize(14);
        tvHeroBat.setTextColor(Color.WHITE);
        tvHeroBat.setPadding(0, dp(4), 0, 0);
        hcol.addView(tvHeroBat);
        hero.addView(hcol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addCard(p2, hero);

        // Zdrowie
        p2.addView(section("Zdrowie"));
        LinearLayout gH = group(p2);
        setRow(gH, "🩺", "#E11D48", "Zmierz teraz", "Ciśnienie, tlen, tętno, temperatura",
                v -> startActivity(new Intent(this, MeasureActivity.class)));
        setRow(gH, "⏰", "#0D9488", "Przypomnienia i budzik", "Budzik, ruch, woda, leki",
                v -> startActivity(new Intent(this, RemindActivity.class)));
        setRow(gH, "🏃", "#EA580C", "Trening", "Spacer, bieg, rower – czas, tętno, kroki",
                v -> startActivity(new Intent(this, TrainingActivity.class)));
        setRow(gH, "📈", "#1D4ED8", "Ostatnie 30 dni", "Czy ciśnienie i tętno idą w górę, czy w dół",
                v -> startActivity(new Intent(this, TrendActivity.class)));

        // Zegarek
        p2.addView(section("Zegarek"));
        LinearLayout gW = group(p2);
        setRow(gW, "⚙️", "#0F766E", "Ustawienia zegarka", "Dźwięk, połączenia, pomiary automatyczne, noc, pogoda, muzyka",
                v -> startActivity(new Intent(this, WatchSettingsActivity.class)));
        setRow(gW, "🖼️", "#4F46E5", "Tarcza ze zdjęcia i wygląd", "Zdjęcie, data, bateria, kolor, język, głośność",
                v -> startActivity(new Intent(this, SdkActivity.class)));
        LinearLayout faceRow = rowBox(gW, "🎨", "#8E24AA", "Tarcze w zegarku");
        tvWrite = (TextView) faceRow.getTag();
        TextView reload = stepBtn("↻", v -> loadDials(true));
        reload.setTextColor(Color.parseColor("#6D28D9"));
        reload.setBackground(round(Color.parseColor("#EDE9FE"), 19));
        faceRow.addView(reload, new LinearLayout.LayoutParams(dp(38), dp(38)));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        dialChips = new LinearLayout(this);
        dialChips.setPadding(dp(62), 0, dp(14), dp(12));
        hs.addView(dialChips);
        gW.addView(hs);
        buildDialChips();
        divider(gW);
        LinearLayout scrRow = rowBox(gW, "💡", "#F59E0B", "Podświetlenie ekranu");
        tvScreen = (TextView) scrRow.getTag();
        LinearLayout stepper = new LinearLayout(this);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = stepBtn("−", v -> changeScreen(-1));
        TextView plus = stepBtn("+", v -> changeScreen(1));
        stepper.addView(minus, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(38), dp(38));
        pl.setMargins(dp(6), 0, 0, 0);
        stepper.addView(plus, pl);
        scrRow.addView(stepper);
        divider(gW);
        setRow(gW, "📳", "#F4511E", "Znajdź zegarek", "Zegarek zawibruje",
                v -> { send(0xB5, 0x0A, 0x01); toast("Zegarek powinien zawibrować"); });

        // Powiadomienia
        p2.addView(section("Powiadomienia na zegarek"));
        LinearLayout gN = group(p2);
        tvCalls = setRow(gN, "📞", "#16A34A", "Kto dzwoni", "", v -> requestPermissions(new String[]{
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS,
                Manifest.permission.ANSWER_PHONE_CALLS}, 5));
        tvApps = setRow(gN, "💬", "#2563EB", "SMS, WhatsApp, Messenger i inne", "", v -> {
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (Exception e) { toast("Otwórz: Ustawienia → Powiadomienia → Dostęp do powiadomień"); }
        });
        setRow(gN, "🔔", "#7C3AED", "Wyślij test powiadomienia", "Sprawdź, czy napis pojawi się na zegarku",
                v -> testMsg(0x01, "Mój Zegarek", "Test powiadomienia – działa! Zażółć gęślą jaźń"));
        setRow(gN, "🚨", "#DB2777", "Test alarmu zdrowia", "Na zegarku pojawi się napis ALARM", v -> {
            BleService b = BleService.instance;
            if (b == null) { toast("Brak połączenia z zegarkiem"); return; }
            b.alarm("test", "Test: wysokie ciśnienie 150/95", true);
            toast("Na zegarku powinien pojawić się napis ALARM");
        });
        tvNotif = new TextView(this);
        tvNotif.setTextSize(12);
        tvNotif.setTextColor(Color.parseColor("#64748B"));
        tvNotif.setPadding(dp(12), dp(6), dp(12), 0);
        p2.addView(tvNotif);

        // Połączenie
        p2.addView(section("Połączenie"));
        LinearLayout gC = group(p2);
        setRow(gC, "🔄", "#00897B", "Odśwież dane", "Kroki, bateria i historia z zegarka", v -> refreshWatch());
        setRow(gC, "🔗", "#2E7D32", "Połącz ponownie", "Gdy coś przestało działać",
                v -> { stopBle(); tvState.postDelayed(this::startBle, 1500); });
        tvBatOpt = setRow(gC, "🔋", "#EF6C00", "Bateria bez limitu", "", v -> askBattery());
        tvMode = setRow(gC, "⚙️", "#334155", "Tryb połączenia", "", v -> {
            getSharedPreferences("ust", MODE_PRIVATE).edit().putBoolean("sdk", !BleService.useSdk(this)).apply();
            modeLabel();
            stopBle();
            tvState.postDelayed(this::startBle, 2500);
            toast("Łączę ponownie w nowym trybie…");
        });
        setRow(gC, "🛠️", "#64748B", "Tryb testowy", "Log połączenia (do wysłania w razie problemu)",
                v -> startActivity(new Intent(this, MainActivity.class)));
        modeLabel();
        TextView info = new TextView(this);
        info.setText("Godzina i data ustawiają się same przy każdym połączeniu, a sen pobiera się sam każdego ranka.");
        info.setTextSize(12);
        info.setTextColor(Color.parseColor("#64748B"));
        info.setPadding(dp(12), dp(8), dp(12), dp(8));
        p2.addView(info);
        pages[2] = p2;

        // ---- Profil ----
        LinearLayout p3 = page();
        p3.addView(section("Cele"));
        LinearLayout goals = new LinearLayout(this);
        goals.setOrientation(LinearLayout.VERTICAL);
        goals.setPadding(dp(12), dp(6), dp(12), dp(6));
        goals.setBackground(round(Color.WHITE, 14));
        etGoalSteps = numEdit(String.valueOf(getSharedPreferences("cele", MODE_PRIVATE).getInt("kroki", 10000)));
        etGoalSleep = numEdit(String.valueOf(getSharedPreferences("cele", MODE_PRIVATE).getFloat("sen", 7f)));
        goals.addView(labelRow("Kroki dziennie", etGoalSteps));
        goals.addView(labelRow("Sen (godziny)", etGoalSleep));
        etWeight = numEdit(String.valueOf(Math.round(getSharedPreferences("cele", MODE_PRIVATE).getFloat("waga", 80f))));
        goals.addView(labelRow("Waga (kg) – do kalorii", etWeight));
        addCard(p3, goals);
        p3.addView(row(btn("Zapisz cele", "#1565C0", v -> saveGoals())));

        p3.addView(section("Alarmy – kiedy mam dostać powiadomienie"));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(6), dp(12), dp(6));
        box.setBackground(round(Color.WHITE, 14));
        for (int i = 0; i < BleService.TH.length; i++) {
            float v = BleService.th(this, BleService.TH[i][0]);
            thEdit[i] = numEdit(v == Math.round(v) ? String.valueOf(Math.round(v)) : String.valueOf(v));
            box.addView(labelRow(BleService.TH[i][1], thEdit[i]));
        }
        addCard(p3, box);
        btnAlarmOn = btn("", "#2E7D32", v -> toggleAlarms());
        p3.addView(row(btnAlarmOn, btn("Zapisz progi", "#1565C0", v -> saveThresholds())));
        p3.addView(row(btn("Test alarmu", "#C62828", v -> testAlarm())));
        p3.addView(row(
                btn("📄 Raport 7 dni", "#0B3D91", v -> Report.make(this, 7)),
                btn("📄 Raport 30 dni", "#0E7490", v -> Report.make(this, 30))
        ));
        p3.addView(section("Kopia zapasowa"));
        p3.addView(row(
                btn("💾 Zrób kopię", "#15803D", v -> Backup.export(this)),
                btn("♻️ Przywróć", "#B45309", v -> {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, 78);
                })
        ));
        TextView note = new TextView(this);
        note.setText("Alarm przychodzi dla świeżego pomiaru (z ostatnich 30 min), najwyżej raz na godzinę dla danego parametru. Zegarek nie jest urządzeniem medycznym – przy złym samopoczuciu nie czekaj na alarm.");
        note.setTextSize(12);
        note.setTextColor(Color.parseColor("#607D8B"));
        note.setPadding(dp(8), dp(4), dp(8), dp(8));
        p3.addView(note);
        pages[3] = p3;
        updateAlarmBtn();

        // ---- dolny pasek ----
        LinearLayout nav = new LinearLayout(this);
        GradientDrawable ng = new GradientDrawable();
        ng.setColor(Color.WHITE);
        ng.setCornerRadii(new float[]{dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
        nav.setBackground(ng);
        nav.setElevation(dp(10));
        nav.setPadding(dp(8), dp(8), dp(8), dp(10));
        String[] names = {"🏠\nPulpit", "👟\nSport", "⌚\nUrządzenie", "👤\nProfil"};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            Button b = new Button(this);
            b.setText(names[i]);
            b.setAllCaps(false);
            b.setTextSize(12);
            b.setStateListAnimator(null);
            b.setPadding(0, dp(4), 0, dp(4));
            b.setOnClickListener(v -> showPage(k));
            navBtn[i] = b;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            nav.addView(b, lp);
        }
        outer.addView(nav);

        setContentView(outer);
        showPage(0);
    }

    private void showPage(int i) {
        page = i;
        if (i == 2) loadDials(false);
        sv.removeAllViews();
        sv.addView(pages[i]);
        sv.scrollTo(0, 0);
        for (int k = 0; k < 4; k++) {
            boolean on = k == i;
            navBtn[k].setBackground(on ? grad("#DCEBFF", "#E9E3FF", 20) : round(Color.WHITE, 20));
            navBtn[k].setTextColor(Color.parseColor(on ? "#1D4ED8" : "#8A97AB"));
            navBtn[k].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        if (tvState != null) refresh();
    }

    private void saveGoals() {
        try {
            int st = Integer.parseInt(etGoalSteps.getText().toString().trim());
            float sl = Float.parseFloat(etGoalSleep.getText().toString().replace(',', '.').trim());
            float kg = Float.parseFloat(etWeight.getText().toString().replace(',', '.').trim());
            getSharedPreferences("cele", MODE_PRIVATE).edit().putInt("kroki", st).putFloat("sen", sl).putFloat("waga", kg).apply();
            toast("Cele zapisane");
            refresh();
        } catch (Exception e) {
            toast("Wpisz poprawne liczby");
        }
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
        boolean conn = st.startsWith("Połączony");
        boolean wait = st.contains("Czekam") || st.startsWith("Rozłączony");
        tvState.setText((conn ? "● " : wait ? "◌ " : "○ ") + st);
        tvState.setTextColor(Color.WHITE);
        tvState.setBackground(round(Color.parseColor(conn ? "#3322C55E" : wait ? "#33FF9F1C" : "#33F43F5E"), 20));
        String dTxt = (isToday() ? "Dziś, " : "") + new SimpleDateFormat("dd.MM.yyyy", Locale.US).format(day.getTime());
        tvDate.setText(dTxt);
        tvDate2.setText(dTxt);

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
        int[] slots = new int[48];
        boolean anySteps = false;
        for (String k : sorted.keySet()) {
            JSONObject r = sorted.get(k);
            float m = Integer.parseInt(k.substring(0, 2)) * 60 + Integer.parseInt(k.substring(3, 5));
            if (r.has("st")) { slots[Math.min(47, (int) (m / 30))] += r.optInt("st"); anySteps = true; }
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
        int goal = getSharedPreferences("cele", MODE_PRIVATE).getInt("kroki", 10000);
        int pct = 0;
        try { pct = (int) (Long.parseLong(steps) * 100 / Math.max(1, goal)); } catch (Exception ignored) { }
        set(0, steps, "cel " + goal + " (" + pct + "%)");
        ring.set(pct / 100f, steps, "z " + goal + " kroków");
        refreshSleep();
        tvRingSide.setText("🎯  " + pct + "% celu\n📍  " + dist + "\n🔥  " + kcal + "\n❤️  " + (BleService.sHr.equals("–") ? "–" : strip(BleService.sHr)));
        set(1, dist, "");
        set(2, kcal, "");

        String hrSub = hrN > 0 ? "śr. " + (hrSum / hrN) + "  min " + hrMin + "  max " + hrMax : "";
        if (today && BleService.sHr.contains("na żywo")) {
            set(3, strip(BleService.sHr), "pomiar trwa");
        } else if (today) {
            set(3, BleService.sHr.equals("–") ? "–" : strip(BleService.sHr), hrRunning ? "czekam na wynik…" : (hrSub.isEmpty() ? "dotknij – szczegóły" : hrSub + "\ndotknij – szczegóły"));
        } else {
            set(3, hrN > 0 ? String.valueOf(hrSum / hrN) : "–", hrSub);
        }
        set(4, lastBp != null ? lastBp : "–", lastBp != null ? "o " + tBp + " (mmHg)" : "");
        set(5, lastSpo2 != null ? lastSpo2 : "–", lastSpo2 != null ? "o " + tSpo2 : "");
        set(6, lastTemp != null ? lastTemp : "–", lastTemp != null ? "o " + tTemp : "");
        set(7, lastGlu != null ? lastGlu : "–", lastGlu != null ? "mmol/L o " + tGlu + " – orientacyjnie" : "");
        set(8, lastResp != null ? lastResp : "–", lastResp != null ? "o " + tResp : "");
        String bat = BleService.sBat;
        int bp = -1;
        try { bp = Integer.parseInt(bat.replaceAll("[^0-9]", "")); } catch (Exception ignored) { }
        if (bp < 0 || bp > 100) {
            tvBatChip.setVisibility(View.GONE);
        } else {
            tvBatChip.setVisibility(View.VISIBLE);
            tvBatChip.setText((bp <= 15 ? "🪫 " : "🔋 ") + bp + "%");
            tvBatChip.setBackground(round(Color.parseColor(bp > 30 ? "#3322C55E" : bp > 15 ? "#33FF9F1C" : "#66F43F5E"), 20));
        }
        if (screenWanted == 0 && BleService.screenSec > 0) screenWanted = BleService.screenSec;
        sub(tvWrite, !dialMsg.isEmpty() ? dialMsg : (BleService.lastWrite.isEmpty()
                ? (BleService.faceIdx >= 0 ? "Teraz: tarcza " + (BleService.faceIdx + 1) : "Dotknij tarczę poniżej")
                : BleService.lastWrite));
        sub(tvScreen, (screenWanted > 0 ? screenWanted + " s" : "–")
                + (BleService.screenSec > 0 && BleService.screenSec != screenWanted ? "  ·  zapisuję…" : (BleService.screenSec > 0 ? "  ·  w zegarku ✓" : "")));
        tvHeroState.setText((conn ? "● Połączony" : wait ? "◌ Czekam na zegarek…" : "○ " + st)
                + (conn && !Sdk.version.isEmpty() && !Sdk.version.equals("null") ? "  ·  wersja " + Sdk.version : ""));
        tvHeroBat.setText(BleService.sBat.equals("–") ? "🔋 bateria: –" : "🔋 bateria: " + BleService.sBat);

        refreshWeek(goal);
        StringBuilder trs = new StringBuilder();
        org.json.JSONArray ta = o.optJSONArray("treningi");
        if (ta != null) for (int ti = 0; ti < ta.length(); ti++) {
            JSONObject t = ta.optJSONObject(ti);
            if (t != null) trs.append("\n").append(TrainingActivity.describe(t));
        }
        tvSportSum.setText("Kroki: " + steps + "   (cel " + goal + ", " + pct + "%)\nDystans: " + dist + "\nKalorie: " + kcal
                + (trs.length() > 0 ? "\n\nTreningi:" + trs : ""));
        List<float[]> bars = new ArrayList<>();
        StringBuilder list = new StringBuilder();
        int maxSlot = 0;
        for (int i = 47; i >= 0; i--) {
            if (slots[i] > 0) {
                bars.add(new float[]{i * 30, slots[i]});
                list.append(String.format(Locale.US, "%02d:%02d   %5d kroków\n", i / 2, (i % 2) * 30, slots[i]));
                maxSlot = Math.max(maxSlot, slots[i]);
            }
        }
        tvSportList.setText(list.length() > 0 ? list.toString().trim() : (anySteps ? "Brak kroków tego dnia" : "Brak danych – pojawią się po pobraniu historii"));
        List<ChartView.Series> ls = new ArrayList<>();
        ls.add(ChartView.series(bars, Color.parseColor("#1E88E5")));
        chSteps.setData(ls, 0, Math.max(100, maxSlot * 1.15f), "");

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

    private void updateAlarmBtn() {
        boolean on = BleService.alarmsOn(this);
        btnAlarmOn.setText(on ? "Alarmy: WŁĄCZONE" : "Alarmy: WYŁĄCZONE");
        btnAlarmOn.setBackground(round(Color.parseColor(on ? "#2E7D32" : "#757575"), 14));
    }

    private void toggleAlarms() {
        boolean on = !BleService.alarmsOn(this);
        getSharedPreferences("alarmy", MODE_PRIVATE).edit().putBoolean("on", on).apply();
        updateAlarmBtn();
    }

    private void saveThresholds() {
        android.content.SharedPreferences.Editor ed = getSharedPreferences("alarmy", MODE_PRIVATE).edit();
        for (int i = 0; i < BleService.TH.length; i++) {
            try {
                ed.putFloat(BleService.TH[i][0], Float.parseFloat(thEdit[i].getText().toString().replace(',', '.').trim()));
            } catch (Exception e) {
                toast("Zła liczba: " + BleService.TH[i][1]);
                return;
            }
        }
        ed.apply();
        toast("Progi alarmów zapisane");
    }

    private void testAlarm() {
        BleService s = BleService.instance;
        if (s == null) { toast("Usługa zegarka nie działa – stuknij Połącz ponownie"); return; }
        s.alarm("test", "To jest test alarmu – tak będzie wyglądać ostrzeżenie np. o wysokim ciśnieniu.", true);
    }

    private void changeScreen(int d) {
        if (screenWanted == 0) screenWanted = BleService.screenSec > 0 ? BleService.screenSec : 10;
        screenWanted = Math.max(5, Math.min(60, screenWanted + d));
        refresh();
        tvState.removeCallbacks(screenSaver);
        tvState.postDelayed(screenSaver, 1500);
    }

    private final Runnable screenSaver = this::saveScreen;

    private void saveScreen() {
        if (screenWanted < 5) { toast("Najpierw wybierz czas"); return; }
        send(0xB4, 0x01, screenWanted, 0x05, 0x3C, 0x03);
        toast("Wysłano: " + screenWanted + " s – sprawdź na zegarku");
    }

    private void refreshWatch() {
        send(0xD8, 0x00);
        send(0xA0, 0x00);
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
        notifStatus();
        BleService b = BleService.instance;
        if (b != null) b.autoSleepNow();
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
