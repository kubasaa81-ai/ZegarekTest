package pl.kuba.zegarektest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.veepoo.protocol.model.datas.TimeData;
import com.veepoo.protocol.model.enums.EAllSetType;
import com.veepoo.protocol.model.enums.EFunctionStatus;
import com.veepoo.protocol.model.settings.AllSetSetting;
import com.veepoo.protocol.model.settings.CustomSetting;
import com.veepoo.protocol.model.settings.CustomSettingData;
import com.veepoo.protocol.model.settings.NightTurnWristSetting;

import java.util.Calendar;
import java.util.Locale;

/** Ustawienia zegarka: dźwięk i połączenia, pomiary automatyczne, noc, pogoda. */
public class WatchSettingsActivity extends Activity {

    // ---------------- cisza nocna (używana przez usługę i powiadomienia) ----------------

    static final int[][] QUIET = {{22, 7}, {23, 6}, {21, 7}, {22, 8}, {0, 7}};

    static SharedPreferences quiet(Context c) { return c.getSharedPreferences("cisza", MODE_PRIVATE); }

    public static boolean quietNow(Context c) {
        SharedPreferences s = quiet(c);
        if (!s.getBoolean("on", false)) return false;
        int[] q = QUIET[Math.min(QUIET.length - 1, s.getInt("godz", 0))];
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return q[0] > q[1] ? (h >= q[0] || h < q[1]) : (h >= q[0] && h < q[1]);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout autoBox;
    private TextView tvVol, tvInfo, tvWeather, tvQuiet, tvNight;
    private CustomSettingData cs;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable grad(String a, String b, int r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor(a), Color.parseColor(b)});
        g.setCornerRadius(dp(r));
        return g;
    }

    private GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
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

    private LinearLayout group(LinearLayout p) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(round(Color.WHITE, 22));
        g.setElevation(dp(2));
        g.setPadding(0, dp(4), 0, dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(2), dp(4), dp(4));
        p.addView(g, lp);
        return g;
    }

    private void divider(LinearLayout g) {
        if (g.getChildCount() == 0) return;
        View d = new View(this);
        d.setBackgroundColor(Color.parseColor("#EEF1F6"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(dp(66), 0, dp(14), 0);
        g.addView(d, lp);
    }

    /** Wiersz z ikoną; zwraca wiersz, podpis w getTag(). */
    private LinearLayout row(LinearLayout g, String ico, String color, String title, String sub) {
        divider(g);
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(12), dp(14), dp(12));
        r.setMinimumHeight(dp(64));
        TextView ic = new TextView(this);
        ic.setText(ico);
        ic.setTextSize(19);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(grad(color, color, 14));
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
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextSize(13);
        s.setTextColor(Color.parseColor("#64748B"));
        if (sub.isEmpty()) s.setVisibility(View.GONE);
        col.addView(s);
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.setTag(s);
        g.addView(r);
        return r;
    }

    private Switch sw(LinearLayout r, boolean on, android.widget.CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(this);
        s.setChecked(on);
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{Color.parseColor("#16A34A"), Color.parseColor("#F1F5F9")}));
        s.setTrackTintList(new ColorStateList(st, new int[]{Color.parseColor("#86EFAC"), Color.parseColor("#CBD5E1")}));
        s.setOnCheckedChangeListener(l);
        r.addView(s);
        return s;
    }

    private TextView pill(LinearLayout r, String text, String bg, String fg, View.OnClickListener l) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(Color.parseColor(fg));
        b.setBackground(round(Color.parseColor(bg), 19));
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setMinWidth(dp(38));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        lp.setMargins(dp(6), 0, 0, 0);
        r.addView(b, lp);
        return b;
    }

    private static void sub(TextView t, String s) {
        t.setText(s);
        t.setVisibility(s.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void info(String s) { main.post(() -> tvInfo.setText(s)); }

    private boolean need() {
        if (Sdk.ready) return true;
        info("⚠️ Zegarek nie jest teraz połączony – spróbuj za chwilę.");
        return false;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0F766E"));
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.parseColor("#EEF2F8"));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(30), dp(20), dp(18));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#0F766E"), Color.parseColor("#1D4ED8")});
        hg.setCornerRadii(new float[]{0, 0, 0, 0, dp(28), dp(28), dp(28), dp(28)});
        head.setBackground(hg);
        TextView back = new TextView(this);
        back.setText("‹  Wróć");
        back.setTextSize(15);
        back.setTextColor(Color.WHITE);
        back.setOnClickListener(v -> finish());
        head.addView(back);
        TextView title = new TextView(this);
        title.setText("Ustawienia zegarka ⚙️");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        head.addView(title);
        tvInfo = new TextView(this);
        tvInfo.setTextSize(14);
        tvInfo.setTextColor(Color.parseColor("#CCFBF1"));
        tvInfo.setText(Sdk.ready ? "Zmiany zapisują się od razu w zegarku." : "Czekam na połączenie z zegarkiem…");
        head.addView(tvInfo);
        outer.addView(head);

        ScrollView sv = new ScrollView(this);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(10), dp(4), dp(10), dp(24));
        sv.addView(p);
        outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- dźwięk i połączenia ----
        section(p, "Dźwięk i połączenia");
        LinearLayout gS = group(p);
        LinearLayout rv = row(gS, "🔊", "#F59E0B", "Głośność zegarka", "");
        tvVol = (TextView) rv.getTag();
        pill(rv, "−", "#FEF3C7", "#B45309", v -> volume(-2));
        pill(rv, "+", "#FEF3C7", "#B45309", v -> volume(2));
        volLabel();
        SharedPreferences pw = getSharedPreferences("powiad", MODE_PRIVATE);
        LinearLayout rc = row(gS, "📞", "#16A34A", "Ekran dzwonienia na zegarku",
                "Przy połączeniu zegarek dzwoni i pokazuje przyciski odrzuć / wycisz. Wyłącz, by był tylko napis.");
        sw(rc, pw.getBoolean("ekranPol", true), (c, on) -> pw.edit().putBoolean("ekranPol", on).apply());
        LinearLayout rr = row(gS, "📵", "#DC2626", "Odrzucanie z zegarka",
                checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ? "✅ Zgoda udzielona – „odrzuć” na zegarku rozłącza" : "Dotknij, by pozwolić rozłączać połączenie z zegarka");
        rr.setOnClickListener(v -> requestPermissions(new String[]{android.Manifest.permission.ANSWER_PHONE_CALLS}, 7));
        LinearLayout rt = row(gS, "🔔", "#7C3AED", "Test połączenia", "Zegarek zachowa się jak przy prawdziwym połączeniu (10 s)");
        rt.setOnClickListener(v -> {
            BleService s = BleService.instance;
            if (s == null || !s.linked()) { info("⚠️ Zegarek nie jest połączony"); return; }
            info("Sprawdzam ustawienia powiadomień w zegarku…");
            Sdk.enableMsgs(this::info, () -> {
                BleService x0 = BleService.instance;
                if (x0 == null) return;
                x0.testCall("Mój Zegarek");
                main.postDelayed(() -> { BleService x = BleService.instance; if (x != null) x.stopCall(); }, 10000);
                main.postDelayed(() -> info("Wysłano test połączenia (połączenia w zegarku: " + Sdk.phoneStatus
                        + ") – spójrz i posłuchaj zegarka"), 1500);
            });
        });
        TextView note = new TextView(this);
        note.setText("Zegarek ma jeden wbudowany dźwięk – nie da się wgrać innej melodii. Możesz zmienić głośność, "
                + "a połączenia przez ekran dzwonienia dzwonią dłużej niż krótki sygnał przy SMS.");
        note.setTextSize(12);
        note.setTextColor(Color.parseColor("#64748B"));
        note.setPadding(dp(12), dp(6), dp(12), 0);
        p.addView(note);

        // ---- pomiary automatyczne ----
        section(p, "Pomiary automatyczne i funkcje zegarka");
        autoBox = group(p);
        LinearLayout wait = row(autoBox, "⏳", "#94A3B8", "Czytam ustawienia z zegarka…", "");
        wait.setTag(null);
        LinearLayout gO = group(p);
        LinearLayout ro = row(gO, "🫁", "#0284C7", "Tlen w nocy", "Zegarek mierzy tlen podczas snu (0:00–7:00)");
        Switch so = sw(ro, getSharedPreferences("ust", MODE_PRIVATE).getBoolean("tlenNoc", false), (c, on) -> spo2Night(on));

        // ---- noc ----
        section(p, "Noc");
        LinearLayout gN = group(p);
        SharedPreferences us = getSharedPreferences("ust", MODE_PRIVATE);
        LinearLayout rn = row(gN, "✋", "#4338CA", "Ekran zapala się po ruchu ręki", "");
        tvNight = (TextView) rn.getTag();
        swWrist = sw(rn, us.getBoolean("nadgOn", true), (c, on) -> {
            us.edit().putBoolean("nadgOn", on).apply();
            Sdk.wrist(this, x -> { info(x); main.post(this::wristLabel); });
            wristLabel();
        });
        LinearLayout rnh = row(gN, "🕗", "#64748B", "Kiedy działa ruch ręki", "");
        tvWristH = (TextView) rnh.getTag();
        rnh.setOnClickListener(v -> {
            us.edit().putInt("nadgGodz", (us.getInt("nadgGodz", 0) + 1) % Sdk.WRIST.length).apply();
            wristLabel();
            Sdk.wrist(this, x -> { info(x); main.post(this::wristLabel); });
        });
        wristLabel();
        SharedPreferences q = quiet(this);
        LinearLayout rq = row(gN, "🔕", "#334155", "Cisza nocna", "");
        tvQuiet = (TextView) rq.getTag();
        sw(rq, q.getBoolean("on", false), (c, on) -> { q.edit().putBoolean("on", on).apply(); quietLabel(); });
        LinearLayout rqh = row(gN, "🕙", "#64748B", "Godziny ciszy", "");
        rqh.setOnClickListener(v -> { q.edit().putInt("godz", (q.getInt("godz", 0) + 1) % QUIET.length).apply(); quietLabel(); });
        rqh.setTag(rqh.getTag());
        tvQuietH = (TextView) rqh.getTag();
        LinearLayout rqc = row(gN, "📞", "#64748B", "W ciszy także połączenia", "Wyłączone = połączenia przychodzą zawsze");
        sw(rqc, q.getBoolean("polaczenia", false), (c, on) -> q.edit().putBoolean("polaczenia", on).apply());
        quietLabel();

        // ---- pogoda ----
        section(p, "Pogoda na zegarku");
        LinearLayout gW = group(p);
        LinearLayout rw = row(gW, "🌤️", "#0EA5E9", "Wysyłaj pogodę", "");
        tvWeather = (TextView) rw.getTag();
        sw(rw, Weather.on(this), (c, on) -> { Weather.sp(this).edit().putBoolean("on", on).apply(); weatherLabel(); });
        LinearLayout rcity = row(gW, "📍", "#0369A1", "Miasto", Weather.city(this));
        rcity.setOnClickListener(v -> askCity((TextView) rcity.getTag()));
        LinearLayout rsend = row(gW, "📤", "#2563EB", "Wyślij pogodę teraz", "Potem co 3 godziny automatycznie");
        rsend.setOnClickListener(v -> {
            if (!need()) return;
            info("Pobieram pogodę…");
            Weather.maybeSend(this, x -> { info(x); main.post(this::weatherLabel); }, true);
        });
        weatherLabel();

        // ---- muzyka ----
        section(p, "Muzyka");
        LinearLayout gM = group(p);
        row(gM, "🎵", "#DB2777", "Sterowanie muzyką z zegarka",
                "Działa samo: odtwarzaj / pauza / następny / głośność z menu muzyki w zegarku");

        setContentView(outer);
        readCustom();
    }

    private TextView tvQuietH;

    private void quietLabel() {
        SharedPreferences q = quiet(this);
        int[] h = QUIET[Math.min(QUIET.length - 1, q.getInt("godz", 0))];
        sub(tvQuiet, q.getBoolean("on", false)
                ? "Włączona – w tych godzinach powiadomienia i przypomnienia nie idą na zegarek"
                : "Wyłączona");
        sub(tvQuietH, String.format(Locale.US, "%d:00 – %d:00  (dotknij, by zmienić)", h[0], h[1]));
    }

    private void weatherLabel() {
        SharedPreferences w = Weather.sp(this);
        long ost = w.getLong("ost", 0);
        sub(tvWeather, !Weather.on(this) ? "Wyłączone"
                : ost == 0 ? "Jeszcze nie wysłano"
                : "Ostatnio " + new java.text.SimpleDateFormat("HH:mm", Locale.US).format(new java.util.Date(ost))
                + ": " + w.getString("opis", ""));
    }

    private void askCity(TextView label) {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setText(Weather.city(this));
        new AlertDialog.Builder(this).setTitle("Miasto do pogody").setView(et)
                .setPositiveButton("Zapisz", (d, w) -> {
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) return;
                    info("Szukam miasta…");
                    new Thread(() -> {
                        try {
                            String found = Weather.setCity(this, n);
                            main.post(() -> { label.setText(found); info("Miasto: " + found); });
                            Weather.maybeSend(this, this::info, true);
                        } catch (Exception e) {
                            info("❌ " + e.getMessage());
                        }
                    }).start();
                })
                .setNegativeButton("Anuluj", null).show();
    }

    // ---------------- głośność ----------------

    private void volLabel() {
        int v = getSharedPreferences("ust", MODE_PRIVATE).getInt("glosnosc", 16);
        sub(tvVol, v == 0 ? "Wyciszony" : v + " / 16" + (v >= 16 ? "  (maksimum)" : ""));
    }

    private void volume(int d) {
        if (!need()) return;
        SharedPreferences s = getSharedPreferences("ust", MODE_PRIVATE);
        int v = Math.max(0, Math.min(16, s.getInt("glosnosc", 16) + d));
        s.edit().putInt("glosnosc", v).apply();
        volLabel();
        try {
            Sdk.m().settingVolume(v, Sdk.wr, new com.veepoo.protocol.listener.data.IMusicControlListener() {
                @Override public void nextMusic() { }
                @Override public void previousMusic() { }
                @Override public void pauseAndPlayMusic() { }
                @Override public void pauseMusic() { }
                @Override public void playMusic() { }
                @Override public void voiceUp() { }
                @Override public void voiceDown() { }
                @Override public void oprateMusicSuccess() { info("✅ Głośność ustawiona: " + v); }
                @Override public void oprateMusicFail() { info("Zegarek nie przyjął głośności"); }
            });
        } catch (Throwable t) {
            info("❌ " + t.getMessage());
        }
    }

    // ---------------- ustawienia funkcji z zegarka ----------------

    interface Get { EFunctionStatus g(CustomSettingData d); }

    interface Set { void s(CustomSettingData d, EFunctionStatus v); }

    private void readCustom() {
        if (!Sdk.ready) { main.postDelayed(this::readCustom, 2000); return; }
        try {
            Sdk.m().readCustomSetting(Sdk.wr, d -> main.post(() -> { cs = d; buildAuto(); }));
        } catch (Throwable t) {
            info("Ustawienia zegarka: " + t.getMessage());
        }
    }

    private void addAuto(String ico, String color, String title, String sub, Get g, Set s) {
        EFunctionStatus st;
        try { st = g.g(cs); } catch (Throwable t) { return; }
        if (st == null || st == EFunctionStatus.UNSUPPORT || st == EFunctionStatus.UNKONW) return;
        LinearLayout r = row(autoBox, ico, color, title, sub);
        sw(r, st == EFunctionStatus.SUPPORT_OPEN, (c, on) -> {
            if (!need()) return;
            try {
                s.s(cs, on ? EFunctionStatus.SUPPORT_OPEN : EFunctionStatus.SUPPORT_CLOSE);
                Sdk.m().changeCustomSetting(Sdk.wr, d -> main.post(() -> { info("✅ Zapisano w zegarku: " + title + (on ? " – wł." : " – wył.")); }),
                        new CustomSetting(cs));
            } catch (Throwable t) {
                info("❌ " + t.getMessage());
            }
        });
    }

    private void buildAuto() {
        autoBox.removeAllViews();
        addAuto("❤️", "#DB2777", "Tętno automatycznie", "Pomiar co kilka minut przez cały dzień",
                CustomSettingData::getAutoHeartDetect, (d, v) -> { d.setAutoHeartDetect(v); d.setOpenAutoHeartDetect(v == EFunctionStatus.SUPPORT_OPEN); });
        addAuto("🩸", "#E11D48", "Ciśnienie automatycznie", "Pomiar co kilka minut",
                CustomSettingData::getAutoBpDetect, (d, v) -> { d.setAutoBpDetect(v); d.setOpenAutoBpDetect(v == EFunctionStatus.SUPPORT_OPEN); });
        addAuto("🌡️", "#F59E0B", "Temperatura automatycznie", "",
                CustomSettingData::getAutoTemperatureDetect, CustomSettingData::setAutoTemperatureDetect);
        addAuto("📉", "#0284C7", "Ostrzeżenie o niskim tlenie", "Zegarek sam ostrzeże",
                CustomSettingData::getLowSpo2hRemain, CustomSettingData::setLowSpo2hRemain);
        addAuto("📳", "#EA580C", "Wibracja przy rozłączeniu", "Gdy telefon się oddali",
                CustomSettingData::getDisconnectRemind, CustomSettingData::setDisconnectRemind);
        addAuto("💡", "#CA8A04", "Ekran zapala się przy wiadomości", "",
                CustomSettingData::getMessageScreenLight, CustomSettingData::setMessageScreenLight);
        addAuto("🎵", "#DB2777", "Menu muzyki w zegarku", "",
                CustomSettingData::getMusicControl, CustomSettingData::setMusicControl);
        addAuto("⏱️", "#475569", "Sekundnik na tarczy", "",
                CustomSettingData::getSecondsWatch, CustomSettingData::setSecondsWatch);
        if (autoBox.getChildCount() == 0) row(autoBox, "ℹ️", "#94A3B8", "Zegarek nie podał żadnych ustawień", "");
    }

    private void spo2Night(boolean on) {
        getSharedPreferences("ust", MODE_PRIVATE).edit().putBoolean("tlenNoc", on).apply();
        if (!need()) return;
        try {
            Sdk.m().settingSpo2hAutoDetect(Sdk.wr, d -> info("Tlen w nocy: " + (d.getIsOpen() == 1 || on ? "zapisane" : "wyłączone") + " (" + d.getOprateResult() + ")"),
                    new AllSetSetting(EAllSetType.SPO2H_NIGHT_AUTO_DETECT, 0, 0, 7, 0, 1, on ? 1 : 0));
        } catch (Throwable t) {
            info("❌ Tlen w nocy: " + t.getMessage());
        }
    }

    private Switch swWrist;
    private TextView tvWristH;

    private void wristLabel() {
        SharedPreferences us = getSharedPreferences("ust", MODE_PRIVATE);
        int[] h = Sdk.WRIST[Math.min(Sdk.WRIST.length - 1, us.getInt("nadgGodz", 0))];
        sub(tvNight, us.getBoolean("nadgOn", true) ? "Włączone ✓" : "Wyłączone – ekran zapalisz tylko przyciskiem");
        sub(tvWristH, (h[0] == 0 && h[1] == 23 ? "Cały dzień i noc" : String.format(Locale.US, "%d:00–%d:00 (w nocy nie)", h[0], h[1]))
                + "  ·  dotknij, by zmienić");
    }
}
