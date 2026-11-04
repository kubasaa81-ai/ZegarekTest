package pl.kuba.zegarektest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.veepoo.protocol.model.settings.LongSeatSetting;
import com.veepoo.protocol.model.settings.TextAlarm2Setting;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/** Przypomnienia: ruch (wbudowane w zegarek) + woda / leki / własne o stałej godzinie. */
public class RemindActivity extends Activity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView tvMove, tvInfo;
    private LinearLayout list;
    private Button bMoveOn, bEvery, bHours;
    private static final int[] EVERY = {30, 45, 60, 90, 120};
    private static final int[][] HOURS = {{8, 20}, {7, 22}, {9, 17}, {6, 18}, {8, 16}};

    // ---------------- zapis listy przypomnień ----------------

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("przyp", MODE_PRIVATE); }

    static JSONArray load(Context c) {
        try { return new JSONArray(sp(c).getString("lista", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    static void store(Context c, JSONArray a) { sp(c).edit().putString("lista", a.toString()).apply(); }

    // ---------------- wygląd ----------------

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable grad(String a, String b, int r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor(a), Color.parseColor(b)});
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
        b.setBackground(grad(c1, c2, 16));
        b.setPadding(dp(8), dp(12), dp(8), dp(12));
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams lp(float w) {
        LinearLayout.LayoutParams p = w > 0 ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        return p;
    }

    private TextView section(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#0F172A"));
        t.setPadding(dp(8), dp(16), dp(8), dp(4));
        return t;
    }

    private TextView card(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(Color.parseColor("#334155"));
        t.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(dp(18));
        t.setBackground(g);
        return t;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#0D9488"));
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.parseColor("#EEF2F8"));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(30), dp(20), dp(18));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#0D9488"), Color.parseColor("#2563EB")});
        hg.setCornerRadii(new float[]{0, 0, 0, 0, dp(28), dp(28), dp(28), dp(28)});
        head.setBackground(hg);
        TextView back = new TextView(this);
        back.setText("‹  Wróć");
        back.setTextSize(15);
        back.setTextColor(Color.WHITE);
        back.setOnClickListener(v -> finish());
        head.addView(back);
        TextView title = new TextView(this);
        title.setText("Przypomnienia ⏰");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        head.addView(title);
        outer.addView(head);

        ScrollView sv = new ScrollView(this);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(12), dp(6), dp(12), dp(24));
        sv.addView(p);
        outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- ruch ----
        p.addView(section("🚶 Ruszaj się – gdy długo siedzisz"));
        tvMove = card("");
        p.addView(tvMove, lp(0));
        LinearLayout r = new LinearLayout(this);
        bMoveOn = btn("", "#16A34A", "#15803D", v -> { SharedPreferences s = sp(this); s.edit().putBoolean("ruch", !s.getBoolean("ruch", false)).apply(); moveLabels(); });
        bEvery = btn("", "#0EA5A4", "#0F766E", v -> { SharedPreferences s = sp(this); s.edit().putInt("coIle", (s.getInt("coIle", 2) + 1) % EVERY.length).apply(); moveLabels(); });
        bHours = btn("", "#F59E0B", "#D97706", v -> { SharedPreferences s = sp(this); s.edit().putInt("godz", (s.getInt("godz", 0) + 1) % HOURS.length).apply(); moveLabels(); });
        r.addView(bMoveOn, lp(1));
        r.addView(bEvery, lp(1));
        r.addView(bHours, lp(1));
        p.addView(r);
        p.addView(btn("✅  Zapisz w zegarku", "#2563EB", "#1D4ED8", v -> saveMove()), lp(0));
        moveLabels();

        // ---- budzik ----
        p.addView(section("⏰ Budzik w zegarku"));
        LinearLayout ra = new LinearLayout(this);
        ra.addView(btn("➕ Codziennie", "#EA580C", "#C2410C", v -> pickAlarm(true)), lp(1));
        ra.addView(btn("➕ Jednorazowo", "#F59E0B", "#D97706", v -> pickAlarm(false)), lp(1));
        p.addView(ra);
        alarms = new LinearLayout(this);
        alarms.setOrientation(LinearLayout.VERTICAL);
        p.addView(alarms);

        // ---- o godzinie ----
        p.addView(section("💧 💊 Woda, leki i inne – o stałej godzinie"));
        LinearLayout r2 = new LinearLayout(this);
        r2.addView(btn("💧 Woda", "#0284C7", "#0369A1", v -> pickTime("💧 Woda – napij się")), lp(1));
        r2.addView(btn("💊 Leki", "#DB2777", "#9D174D", v -> askText("💊 Leki", true)), lp(1));
        r2.addView(btn("✏️ Inne", "#7C3AED", "#5B21B6", v -> askText("", false)), lp(1));
        p.addView(r2);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        p.addView(list);

        tvInfo = new TextView(this);
        tvInfo.setTextSize(13);
        tvInfo.setTextColor(Color.parseColor("#64748B"));
        tvInfo.setPadding(dp(8), dp(12), dp(8), 0);
        tvInfo.setText("Przypomnienie pokaże się na zegarku (z wibracją, jeśli zegarek obsługuje alarmy z tekstem) "
                + "i jako powiadomienie w telefonie. Dotknij przypomnienia, by je usunąć.");
        p.addView(tvInfo);

        setContentView(outer);
        refreshList();
        readAlarms();
    }

    private void info(String s) { main.post(() -> tvInfo.setText(s)); }

    // ---------------- ruch ----------------

    private void moveLabels() {
        SharedPreferences s = sp(this);
        boolean on = s.getBoolean("ruch", false);
        int e = EVERY[s.getInt("coIle", 2)];
        int[] h = HOURS[s.getInt("godz", 0)];
        bMoveOn.setText(on ? "Włączone ✓" : "Wyłączone");
        bMoveOn.setBackground(on ? grad("#16A34A", "#15803D", 16) : grad("#64748B", "#475569", 16));
        bEvery.setText("co " + e + " min");
        bHours.setText(h[0] + ":00–" + h[1] + ":00");
        tvMove.setText(on
                ? String.format(Locale.US, "Zegarek zawibruje, gdy przez %d min nie zrobisz kroków, w godzinach %d:00–%d:00.", e, h[0], h[1])
                : "Wyłączone. Włącz, wybierz co ile minut i w jakich godzinach, potem „Zapisz w zegarku”.");
    }

    private void saveMove() {
        if (!Sdk.ready) { info("⚠️ Zegarek nie jest teraz połączony – spróbuj za chwilę."); return; }
        SharedPreferences s = sp(this);
        boolean on = s.getBoolean("ruch", false);
        int e = EVERY[s.getInt("coIle", 2)];
        int[] h = HOURS[s.getInt("godz", 0)];
        try {
            Sdk.m().settingLongSeat(Sdk.wr, new LongSeatSetting(h[0], 0, h[1], 0, e, on),
                    d -> info("✅ Zegarek: przypomnienie o ruchu " + (d.isOpen() ? "włączone, co " + d.getThreshold() + " min" : "wyłączone")
                            + " (" + d.getStatus() + ")"));
            info("Wysłano do zegarka…");
        } catch (Throwable t) {
            info("❌ Błąd: " + t.getMessage());
        }
    }

    // ---------------- przypomnienia o godzinie ----------------

    private void askText(String start, boolean meds) {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setText(start.isEmpty() ? "" : start + " ");
        et.setSelection(et.getText().length());
        et.setHint(meds ? "np. Leki – ciśnienie" : "Treść przypomnienia");
        new AlertDialog.Builder(this)
                .setTitle(meds ? "Jakie leki? (krótko)" : "Treść przypomnienia")
                .setView(et)
                .setPositiveButton("Dalej – godzina", (d, w) -> {
                    String t = et.getText().toString().trim();
                    if (t.isEmpty()) t = meds ? "💊 Leki" : "Przypomnienie";
                    pickTime(t);
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    private void pickTime(String text) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        new TimePickerDialog(this, (v, h, m) -> add(text, h, m), c.get(java.util.Calendar.HOUR_OF_DAY), 0, true).show();
    }

    private static String plain(String s) {
        // zegarek może nie mieć emoji – zostawiamy litery, cyfry i znaki
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp < 0x2000 || (cp >= 0x2010 && cp <= 0x2027)) b.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return b.toString().trim().replaceAll("\\s+", " ");
    }

    private void add(String text, int h, int m) {
        try {
            JSONArray a = load(this);
            JSONObject o = new JSONObject();
            o.put("h", h);
            o.put("m", m);
            o.put("t", text);
            o.put("zeg", false);
            a.put(o);
            store(this, a);
            refreshList();
            sendToWatch(a.length() - 1);
        } catch (Exception e) {
            info("❌ " + e.getMessage());
        }
    }

    private boolean textAlarmsSupported() {
        try { return Sdk.func == null || Sdk.supported(Sdk.func.getTextAlarm()); } catch (Throwable t) { return true; }
    }

    private void sendToWatch(int idx) {
        if (!Sdk.ready) { info("Zapisane. Zegarek niepołączony – przypomnienie przyjdzie z telefonu."); return; }
        if (!textAlarmsSupported()) { info("Zapisane. Zegarek nie ma alarmów z tekstem – przypomnienie wyśle telefon."); return; }
        try {
            JSONObject o = load(this).getJSONObject(idx);
            TextAlarm2Setting s = new TextAlarm2Setting();
            s.alarmHour = o.getInt("h");
            s.alarmMinute = o.getInt("m");
            s.repeatStatus = "1111111";
            s.unRepeatDate = "0000-00-00";
            s.scene = 0;
            s.isOpen = true;
            s.setContent(plain(o.getString("t")));
            final boolean[] got = {false};
            Sdk.m().addTextAlarm(Sdk.wr, d -> main.post(() -> {
                if (got[0]) return;
                got[0] = true;
                boolean ok = String.valueOf(d.getOprate()).equals("SETTING_SUCCESS");
                mark(idx, ok);
                info(ok ? "✅ Zapisane w zegarku – zawibruje o tej godzinie."
                        : "Zegarek nie przyjął alarmu (" + d.getOprate() + ") – przypomnienie wyśle telefon.");
            }), s);
            main.postDelayed(() -> {
                if (!got[0]) { got[0] = true; info("Zegarek nie odpowiedział – przypomnienie wyśle telefon."); }
            }, 6000);
        } catch (Throwable t) {
            info("Przypomnienie wyśle telefon (" + t.getMessage() + ")");
        }
    }

    private void mark(int idx, boolean zeg) {
        try {
            JSONArray a = load(this);
            a.getJSONObject(idx).put("zeg", zeg);
            store(this, a);
            refreshList();
        } catch (Exception ignored) { }
    }

    private void remove(int idx) {
        try {
            JSONArray a = load(this);
            JSONObject o = a.getJSONObject(idx);
            if (o.optBoolean("zeg") && Sdk.ready) deleteOnWatch(o.getInt("h"), o.getInt("m"));
            JSONArray n = new JSONArray();
            for (int i = 0; i < a.length(); i++) if (i != idx) n.put(a.get(i));
            store(this, n);
            refreshList();
            info("Usunięto.");
        } catch (Exception e) {
            info("❌ " + e.getMessage());
        }
    }

    private void deleteOnWatch(int h, int m) {
        try {
            Sdk.m().readTextAlarm(Sdk.wr, d -> main.post(() -> {
                List<TextAlarm2Setting> l = d.getTextAlarm2SettingList();
                if (l == null) return;
                for (TextAlarm2Setting s : l) {
                    if (s.alarmHour == h && s.alarmMinute == m) {
                        try { Sdk.m().deleteTextAlarm(Sdk.wr, x -> info("Usunięto także z zegarka."), s); } catch (Throwable ignored) { }
                        break;
                    }
                }
            }));
        } catch (Throwable ignored) { }
    }

    // ---------------- budzik (alarmy w zegarku) ----------------

    private LinearLayout alarms;

    private void pickAlarm(boolean daily) {
        if (!Sdk.ready) { info("⚠️ Zegarek nie jest teraz połączony – spróbuj za chwilę."); return; }
        java.util.Calendar c = java.util.Calendar.getInstance();
        new TimePickerDialog(this, (v, h, m) -> addAlarm(h, m, daily), 6, 30, true).show();
    }

    private void addAlarm(int h, int m, boolean daily) {
        try {
            String date = "0000-00-00";
            if (!daily) {
                java.util.Calendar c = java.util.Calendar.getInstance();
                int now = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
                if (h * 60 + m <= now) c.add(java.util.Calendar.DAY_OF_MONTH, 1);
                date = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
            }
            com.veepoo.protocol.model.settings.Alarm2Setting a =
                    new com.veepoo.protocol.model.settings.Alarm2Setting(h, m, daily ? "1111111" : "0000000", 0, date, true);
            Sdk.m().addAlarm2(Sdk.wr, d -> main.post(() -> {
                info(String.valueOf(d.getOprate()).contains("SUCCESS") ? String.format(Locale.US, "✅ Budzik %02d:%02d zapisany w zegarku", h, m)
                        : String.valueOf(d.getOprate()).equals("ALARM_FULL") ? "Zegarek ma już komplet budzików – usuń któryś"
                        : "Zegarek: " + d.getOprate());
                showAlarms(d.getAlarm2SettingList());
            }), a);
            info("Wysyłam budzik…");
        } catch (Throwable t) {
            info("❌ Budzik: " + t.getMessage());
        }
    }

    private void readAlarms() {
        if (!Sdk.ready) { alarms.removeAllViews(); alarms.addView(card("Połącz zegarek, by zobaczyć budziki."), lp(0)); return; }
        try {
            Sdk.m().readAlarm2(Sdk.wr, d -> main.post(() -> showAlarms(d.getAlarm2SettingList())));
        } catch (Throwable t) {
            info("Budziki: " + t.getMessage());
        }
    }

    private void showAlarms(List<com.veepoo.protocol.model.settings.Alarm2Setting> l) {
        if (l == null) { readAlarms(); return; }
        alarms.removeAllViews();
        if (l.isEmpty()) { alarms.addView(card("Brak budzików w zegarku."), lp(0)); return; }
        for (com.veepoo.protocol.model.settings.Alarm2Setting a : l) {
            String rep = a.getRepeatStatus() == null || a.getRepeatStatus().replace("0", "").isEmpty()
                    ? "jednorazowo" + (a.getUnRepeatDate() != null && !a.getUnRepeatDate().startsWith("0000") ? " " + a.getUnRepeatDate() : "")
                    : a.getRepeatStatus().equals("1111111") ? "codziennie" : "wybrane dni";
            TextView t = card(String.format(Locale.US, "⏰  %02d:%02d\n%s · %s", a.getAlarmHour(), a.getAlarmMinute(), rep,
                    a.isOpen() ? "włączony" : "wyłączony"));
            t.setTextSize(15);
            t.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(String.format(Locale.US, "Budzik %02d:%02d", a.getAlarmHour(), a.getAlarmMinute()))
                    .setPositiveButton("Usuń", (dd, w) -> {
                        try { Sdk.m().deleteAlarm2(Sdk.wr, d -> main.post(() -> { info("Usunięto budzik"); showAlarms(d.getAlarm2SettingList()); }), a); }
                        catch (Throwable e) { info("❌ " + e.getMessage()); }
                    })
                    .setNeutralButton(a.isOpen() ? "Wyłącz" : "Włącz", (dd, w) -> {
                        try {
                            a.isOpen = !a.isOpen();
                            Sdk.m().modifyAlarm2(Sdk.wr, d -> main.post(() -> showAlarms(d.getAlarm2SettingList())), a);
                        } catch (Throwable e) { info("❌ " + e.getMessage()); }
                    })
                    .setNegativeButton("Zamknij", null).show());
            alarms.addView(t, lp(0));
        }
    }

    private void refreshList() {
        list.removeAllViews();
        JSONArray a = load(this);
        if (a.length() == 0) {
            list.addView(card("Brak przypomnień. Dodaj przyciskiem powyżej."), lp(0));
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            final int idx = i;
            TextView t = card(String.format(Locale.US, "%02d:%02d   %s\n%s", o.optInt("h"), o.optInt("m"), o.optString("t"),
                    o.optBoolean("zeg") ? "⌚ zapisane w zegarku · codziennie" : "📱 z telefonu · codziennie"));
            t.setTextSize(15);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Usunąć przypomnienie?")
                    .setMessage(o.optString("t"))
                    .setPositiveButton("Usuń", (d, w) -> remove(idx))
                    .setNegativeButton("Zostaw", null)
                    .show());
            list.addView(t, lp(0));
        }
    }
}
