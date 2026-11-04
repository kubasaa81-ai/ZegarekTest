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
    private TextView tvRingSide;

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
        if (type == 0x00) tvNotif.postDelayed(() -> { BleService b = BleService.instance; if (b != null) b.stopCall(); }, 10000);
        toast(type == 0x00 ? "Zegarek powinien dzwonić ok. 10 s" : "Wysłano – spójrz na zegarek");
    }

    private void notifStatus() {
        boolean calls = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
        boolean contacts = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        boolean apps = NotifService.enabled(this);
        tvNotif.setText((calls ? "✅" : "⬜") + "  Połączenia – kto dzwoni" + (calls && !contacts ? " (bez kontaktów: sam numer)" : "")
                + "\n" + (apps ? "✅" : "⬜") + "  SMS, WhatsApp, Messenger i inne aplikacje"
                + "\n" + "✅  Alarmy zdrowia jako napis „ALARM”"
                + (apps ? "\n\nWyłącz powiadomienia w HBand albo ją odinstaluj, żeby nie przychodziły podwójnie." : ""));
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
        LinearLayout.LayoutParams chip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chip.setMargins(0, dp(8), 0, 0);
        head.addView(tvState, chip);
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
        for (int i = 0; i < 10; i += 2) p0.addView(row(tile(i), tile(i + 1)));
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
        p2.addView(section("Czas podświetlenia ekranu"));
        tvScreen = new TextView(this);
        tvScreen.setTextSize(16);
        tvScreen.setGravity(Gravity.CENTER);
        tvScreen.setTextColor(Color.parseColor("#263238"));
        tvScreen.setPadding(0, dp(10), 0, dp(10));
        tvScreen.setBackground(round(Color.WHITE, 14));
        p2.addView(row(
                btn("− 1 s", "#78909C", v -> changeScreen(-1)),
                tvScreen,
                btn("+ 1 s", "#78909C", v -> changeScreen(1))
        ));
        p2.addView(row(btn("Zapisz w zegarku", "#6A1B9A", v -> saveScreen())));
        p2.addView(section("Tarcza zegarka"));
        p2.addView(row(
                btn("1", "#3949AB", v -> setFace(0)),
                btn("2", "#5E35B1", v -> setFace(1)),
                btn("3", "#8E24AA", v -> setFace(2)),
                btn("4", "#C2185B", v -> setFace(3))
        ));
        p2.addView(row(btn("📳 Znajdź zegarek (wibracja)", "#F4511E", v -> { send(0xB5, 0x0A, 0x01); toast("Zegarek powinien zawibrować"); })));
        tvWrite = card("");
        tvWrite.setTextSize(13);
        addCard(p2, tvWrite);
        p2.addView(section("Powiadomienia na zegarek"));
        tvNotif = card("");
        tvNotif.setTextSize(13);
        addCard(p2, tvNotif);
        p2.addView(row(
                btn("📞 Kto dzwoni – włącz", "#16A34A", v -> requestPermissions(new String[]{
                        Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS}, 5)),
                btn("💬 Powiadomienia – włącz", "#2563EB", v -> {
                    try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
                    catch (Exception e) { toast("Otwórz: Ustawienia → Powiadomienia → Dostęp do powiadomień"); }
                })
        ));
        p2.addView(row(
                btn("Test wiadomości", "#7C3AED", v -> testMsg(0x01, "Mój Zegarek", "Test powiadomienia – działa! Zażółć gęślą jaźń")),
                btn("Test połączenia", "#DB2777", v -> testMsg(0x00, "Ania Test", ""))
        ));
        p2.addView(section("Połączenie"));
        p2.addView(row(
                btn("Odśwież dane", "#00897B", v -> refreshWatch()),
                btn("Połącz ponownie", "#2E7D32", v -> { stopBle(); tvState.postDelayed(this::startBle, 1500); })
        ));
        p2.addView(row(
                btn("Bateria bez limitu", "#EF6C00", v -> askBattery()),
                btn("Tryb testowy", "#455A64", v -> startActivity(new Intent(this, MainActivity.class)))
        ));
        TextView info = card("Wkrótce: zmiana tarczy, format godziny, ustawianie czasu – po rozpracowaniu komend z HBand.");
        info.setTextSize(13);
        info.setTextColor(Color.parseColor("#607D8B"));
        addCard(p2, info);
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
            getSharedPreferences("cele", MODE_PRIVATE).edit().putInt("kroki", st).putFloat("sen", sl).apply();
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
        set(9, today ? BleService.sBat : "–", today ? "" : "");
        if (screenWanted == 0 && BleService.screenSec > 0) screenWanted = BleService.screenSec;
        tvWrite.setText((BleService.faceIdx >= 0 ? "Aktualna tarcza: " + (BleService.faceIdx + 1) + "\n" : "")
                + (BleService.lastWrite.isEmpty() ? "Tu pojawi się potwierdzenie z zegarka." : BleService.lastWrite));
        tvScreen.setText("Podświetlenie:\n" + (screenWanted > 0 ? screenWanted + " s" : "–")
                + (BleService.screenSec > 0 && BleService.screenSec != screenWanted ? "\n(w zegarku " + BleService.screenSec + " s)" : ""));

        refreshWeek(goal);
        tvSportSum.setText("Kroki: " + steps + "   (cel " + goal + ", " + pct + "%)\nDystans: " + dist + "\nKalorie: " + kcal);
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
    }

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
