package pl.kuba.zegarektest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements BleService.Listener {

    private TextView tvState, tvLog, tvSum;
    private ScrollView scroll;
    private EditText etAddr, etHex, etNote;
    private Spinner spChar;
    private final List<BluetoothGattCharacteristic> chars = new ArrayList<>();
    private ArrayAdapter<String> charAdapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        requestPerms();
    }

    // ---------------- wygląd ----------------

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private Button btn(String text, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(dp(46));
        b.setMinimumHeight(dp(46));
        b.setPadding(dp(6), dp(6), dp(6), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout row(View... vs) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (View v : vs) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            r.addView(v, lp);
        }
        return r;
    }

    private LinearLayout rowWeighted(View a, float wa, View b2, float wb) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, wa);
        la.setMargins(dp(3), dp(3), dp(3), dp(3));
        LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, wb);
        lb.setMargins(dp(3), dp(3), dp(3), dp(3));
        r.addView(a, la);
        r.addView(b2, lb);
        return r;
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(15);
        return e;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(28), dp(10), dp(10));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Zegarek Test 0.6 – JS300");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.parseColor("#0D47A1"));
        root.addView(title);

        tvState = new TextView(this);
        tvState.setTextSize(16);
        tvState.setTypeface(Typeface.DEFAULT_BOLD);
        tvState.setPadding(0, dp(4), 0, dp(4));
        root.addView(tvState);

        tvSum = new TextView(this);
        tvSum.setTextSize(15);
        tvSum.setTextColor(Color.parseColor("#0D47A1"));
        tvSum.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable sumBg = new GradientDrawable();
        sumBg.setColor(Color.parseColor("#E3F2FD"));
        sumBg.setCornerRadius(dp(12));
        tvSum.setBackground(sumBg);
        LinearLayout.LayoutParams sumLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sumLp.setMargins(dp(3), dp(2), dp(3), dp(4));
        root.addView(tvSum, sumLp);

        root.addView(row(
                btn("Odśwież dane", Color.parseColor("#00897B"), v -> quick("Odśwież dane", 0xD8, 0x00)),
                btn("Tętno start", Color.parseColor("#AD1457"), v -> quick("Tętno start", 0xD0, 0x01)),
                btn("Tętno stop", Color.parseColor("#5D4037"), v -> { quick("Tętno stop", 0xD0, 0x00); tvSum.postDelayed(() -> quick("Tętno stop 2", 0xD0, 0x02), 800); })
        ));
        root.addView(row(
                btn("Hasło 0000", Color.parseColor("#283593"), v -> quick("Hasło 0000", 0xA1, 0x00, 0x00, 0x00, 0x00)),
                btn("Bateria", Color.parseColor("#F9A825"), v -> quick("Bateria", 0x96, 0x01)),
                btn("Pobierz historię", Color.parseColor("#7B1FA2"), v -> quick("Pobierz historię", 0xDF, 0x00))
        ));

        root.addView(row(
                btn("Połącz", Color.parseColor("#2E7D32"), v -> startBle()),
                btn("Rozłącz", Color.parseColor("#C62828"), v -> stopBle()),
                btn("Bateria bez limitu", Color.parseColor("#EF6C00"), v -> askBattery())
        ));

        etAddr = edit("Adres zegarka");
        etAddr.setText(getSharedPreferences("ust", MODE_PRIVATE).getString("adres", BleService.DEFAULT_ADDR));
        etAddr.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        root.addView(rowWeighted(etAddr, 2f,
                btn("Zapisz adres", Color.parseColor("#3949AB"), v -> saveAddr()), 1f));

        TextView lbl = new TextView(this);
        lbl.setText("Komenda do zegarka (hex) – wybierz kanał:");
        lbl.setTextSize(14);
        lbl.setPadding(dp(3), dp(6), 0, 0);
        root.addView(lbl);

        spChar = new Spinner(this);
        charAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());
        spChar.setAdapter(charAdapter);
        root.addView(spChar);

        etHex = edit("np. A1 00 00 00 00");
        etHex.setTypeface(Typeface.MONOSPACE);
        root.addView(rowWeighted(etHex, 2f,
                btn("Wyślij", Color.parseColor("#1565C0"), v -> send()), 1f));

        etNote = edit("notatka, np. teraz tętno w HBand");
        root.addView(rowWeighted(etNote, 2f,
                btn("Wstaw notatkę", Color.parseColor("#6D4C41"), v -> addNote()), 1f));

        root.addView(row(
                btn("Czytaj wszystko", Color.parseColor("#6A1B9A"), v -> readAll()),
                btn("Zapisz log", Color.parseColor("#00838F"), v -> saveLog()),
                btn("Wyczyść log", Color.parseColor("#546E7A"), v -> clearLog())
        ));

        scroll = new ScrollView(this);
        tvLog = new TextView(this);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setTextSize(11);
        tvLog.setTextColor(Color.parseColor("#212121"));
        tvLog.setTextIsSelectable(true);
        tvLog.setPadding(dp(6), dp(6), dp(6), dp(6));
        scroll.setBackgroundColor(Color.parseColor("#F1F3F4"));
        scroll.addView(tvLog);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.setMargins(dp(3), dp(6), dp(3), 0);
        root.addView(scroll, lp);

        setContentView(root);
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
        if (!hasConnect()) { requestPerms(); return; }
        startForegroundService(new Intent(this, BleService.class));
    }

    private void stopBle() {
        Intent i = new Intent(this, BleService.class);
        i.setAction(BleService.ACTION_STOP);
        startService(i);
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

    private void saveAddr() {
        String a = etAddr.getText().toString().trim().toUpperCase(Locale.US);
        if (!a.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")) {
            toast("Zły format adresu, np. D4:1A:DB:29:D3:35");
            return;
        }
        getSharedPreferences("ust", MODE_PRIVATE).edit().putString("adres", a).apply();
        toast("Zapisano. Łączę od nowa…");
        stopBle();
        etAddr.postDelayed(this::startBle, 1200);
    }

    // ---------------- komendy ----------------

    private void send() {
        BleService s = BleService.instance;
        if (s == null || chars.isEmpty()) { toast("Najpierw połącz z zegarkiem"); return; }
        byte[] data = parseHex(etHex.getText().toString());
        if (data == null) { toast("Zły format. Przykład: A1 00 00 00 00"); return; }
        int idx = spChar.getSelectedItemPosition();
        if (idx < 0 || idx >= chars.size()) return;
        s.write(chars.get(idx), data);
    }

    private void quick(String label, int... bytes) {
        BleService s = BleService.instance;
        if (s == null) { toast("Najpierw połącz z zegarkiem"); return; }
        byte[] d = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) d[i] = (byte) bytes[i];
        BleService.appendLog(this, "=== TEST: " + label + " ===");
        if (!s.sendVeepoo(d)) toast("Brak kanału f0080003 – poczekaj na „Stan: Połączony”");
    }

    private void historyProbe() {
        int[][] cmds = {
                {0xD3, 0x01, 0x01},
                {0xD3, 0x00},
                {0xDF, 0x00},
                {0xDF, 0x01},
                {0xDF, 0x01, 0x00, 0x00, 0x01},
                {0xD3, 0x01, 0x00}
        };
        toast("Wysyłam " + cmds.length + " prób co 6 s – nie ruszaj HBand");
        for (int k = 0; k < cmds.length; k++) {
            final int[] c = cmds[k];
            final int n = k + 1;
            StringBuilder lbl = new StringBuilder("Historia próba " + n + ":");
            for (int b : c) lbl.append(String.format(Locale.US, " %02X", b));
            tvSum.postDelayed(() -> quick(lbl.toString(), c), k * 6000L);
        }
    }

    private void readAll() {
        BleService s = BleService.instance;
        if (s == null) { toast("Najpierw połącz z zegarkiem"); return; }
        s.readAll();
    }

    private void addNote() {
        String n = etNote.getText().toString().trim();
        if (n.isEmpty()) { toast("Wpisz treść notatki"); return; }
        BleService.appendLog(this, "=== NOTATKA: " + n + " ===");
        etNote.setText("");
    }

    static byte[] parseHex(String s) {
        s = s.replace("0x", "").replace("0X", "").replaceAll("[^0-9A-Fa-f]", "");
        if (s.isEmpty() || s.length() % 2 != 0) return null;
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return b;
    }

    // ---------------- dziennik ----------------

    private void saveLog() {
        String content = BleService.readLog(this);
        if (content.isEmpty()) { toast("Log jest pusty"); return; }
        if (Build.VERSION.SDK_INT < 29) { toast("Zapis do Pobranych wymaga Androida 10+"); return; }
        String name = "zegarek_log_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt";
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("brak miejsca");
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                os.write(content.getBytes(StandardCharsets.UTF_8));
            }
            new AlertDialog.Builder(this)
                    .setTitle("Zapisano")
                    .setMessage("Plik jest w folderze Pobrane:\n" + name)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception e) {
            toast("Błąd zapisu: " + e.getMessage());
        }
    }

    private void clearLog() {
        new AlertDialog.Builder(this)
                .setTitle("Wyczyścić log?")
                .setMessage("Usunie cały zapisany dziennik.")
                .setPositiveButton("Wyczyść", (d, w) -> {
                    BleService.clearLog(this);
                    tvLog.setText("");
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    private static String tail(String s) {
        return s.length() > 60000 ? s.substring(s.length() - 60000) : s;
    }

    // ---------------- odświeżanie ekranu ----------------

    @Override
    protected void onResume() {
        super.onResume();
        BleService.listener = this;
        tvLog.setText(tail(BleService.readLog(this)));
        onStateChanged();
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    protected void onPause() {
        BleService.listener = null;
        super.onPause();
    }

    @Override
    public void onLog(String line) {
        tvLog.append(line + "\n");
        if (tvLog.length() > 80000) tvLog.setText(tail(tvLog.getText().toString()));
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onStateChanged() {
        String st = BleService.state;
        tvState.setText("Stan: " + st);
        tvSum.setText("Kroki: " + BleService.sSteps
                + "\nDystans: " + BleService.sDist + "    Kalorie: " + BleService.sKcal
                + "\nTętno: " + BleService.sHr
                + "\nCiśnienie: " + BleService.sBp
                + "\nTlen (SpO2): " + BleService.sSpo2
                + "\nTemperatura: " + BleService.sTemp
                + "\nGlukoza (szac.): " + BleService.sGlu
                + "\nOddech: " + BleService.sResp
                + "\nBateria: " + BleService.sBat + "    Ostatni wpis: " + BleService.sHist);
        if (st.startsWith("Połączony")) tvState.setTextColor(Color.parseColor("#2E7D32"));
        else if (st.contains("Czekam") || st.startsWith("Rozłączony")) tvState.setTextColor(Color.parseColor("#EF6C00"));
        else tvState.setTextColor(Color.parseColor("#C62828"));
        refreshChars();
    }

    private void refreshChars() {
        BleService s = BleService.instance;
        List<BluetoothGattCharacteristic> now = s == null ? new ArrayList<>() : new ArrayList<>(s.writable);
        if (now.size() == chars.size() && chars.containsAll(now)) return;
        chars.clear();
        chars.addAll(now);
        List<String> labels = new ArrayList<>();
        int def = 0;
        for (int i = 0; i < chars.size(); i++) {
            BluetoothGattCharacteristic c = chars.get(i);
            String svc = BleService.shortUuid(c.getService().getUuid());
            labels.add(BleService.shortUuid(c.getUuid()) + "  (usługa " + svc + ")");
            if (def == 0 && svc.startsWith("f008")) def = i;
        }
        charAdapter.clear();
        charAdapter.addAll(labels);
        charAdapter.notifyDataSetChanged();
        if (!chars.isEmpty()) spChar.setSelection(def);
    }

    private void toast(String t) {
        Toast.makeText(this, t, Toast.LENGTH_LONG).show();
    }
}
