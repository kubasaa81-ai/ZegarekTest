package pl.kuba.zegarektest;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.inuker.bluetooth.library.connect.response.BleNotifyResponse;
import com.inuker.bluetooth.library.jieli.dial.JLWatchFaceManager;
import com.inuker.bluetooth.library.jieli.response.RcspAuthResponse;
import com.veepoo.protocol.VPOperateManager;
import com.veepoo.protocol.listener.base.IBleWriteResponse;
import com.veepoo.protocol.listener.data.ICustomSettingDataListener;
import com.veepoo.protocol.listener.data.IDeviceFuctionDataListener;
import com.veepoo.protocol.listener.data.IPwdDataListener;
import com.veepoo.protocol.listener.data.ISleepDataListener;
import com.veepoo.protocol.listener.data.ISocialMsgDataListener;
import com.veepoo.protocol.model.datas.DeviceFunctionPackage1;
import com.veepoo.protocol.model.datas.DeviceFunctionPackage2;
import com.veepoo.protocol.model.datas.DeviceFunctionPackage3;
import com.veepoo.protocol.model.datas.DeviceFunctionPackage4;
import com.veepoo.protocol.model.datas.DeviceFunctionPackage5;
import com.veepoo.protocol.model.datas.FunctionDeviceSupportData;
import com.veepoo.protocol.model.datas.FunctionSocailMsgData;
import com.veepoo.protocol.model.datas.PwdData;
import com.veepoo.protocol.model.datas.SleepData;
import com.veepoo.protocol.model.enums.ETimeMode;
import com.veepoo.protocol.model.settings.CustomSettingData;
import com.veepoo.protocol.model.settings.DeviceTimeSetting;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;
import java.util.UUID;

/**
 * Funkcje z oficjalnej biblioteki HBand (beta): ustawienie godziny, sen, tarcza ze zdjęcia.
 * Na czas pracy tego ekranu zwykłe połączenie aplikacji jest wstrzymane.
 */
public class SdkActivity extends Activity {

    private static final int PICK = 42;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView tvStatus, tvOut;
    private ProgressBar pb;
    private boolean ready = false;
    private int watchDays = 3;
    private String photoPath = null;
    private Uri photoUri = null;
    private int dialW = 466, dialH = 466;
    private boolean retried = false;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

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
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setMinHeight(dp(52));
        b.setMinimumHeight(dp(52));
        b.setStateListAnimator(null);
        b.setBackground(grad(c1, c2, 24));
        b.setOnClickListener(l);
        return b;
    }

    private void add(LinearLayout p, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(5), dp(4), dp(5));
        p.addView(v, lp);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#4F46E5"));
        VPOperateManager.getInstance().init(getApplicationContext());
        try { VPOperateManager.getInstance().setAutoConnectBTBySdk(false); } catch (Throwable ignored) { }

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.parseColor("#EEF2F8"));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(34), dp(20), dp(18));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#4F46E5"), Color.parseColor("#7C3AED")});
        hg.setCornerRadii(new float[]{0, 0, 0, 0, dp(28), dp(28), dp(28), dp(28)});
        head.setBackground(hg);
        TextView back = new TextView(this);
        back.setText("‹  Wróć");
        back.setTextSize(15);
        back.setTextColor(Color.WHITE);
        back.setOnClickListener(v -> finish());
        head.addView(back);
        TextView title = new TextView(this);
        title.setText("Sen, godzina i tarcze ✨");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        head.addView(title);
        tvStatus = new TextView(this);
        tvStatus.setTextSize(14);
        tvStatus.setTextColor(Color.parseColor("#E0E7FF"));
        tvStatus.setText("Najpierw stuknij „Połącz”.");
        head.addView(tvStatus);
        outer.addView(head);

        ScrollView sv = new ScrollView(this);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(12), dp(10), dp(12), dp(24));
        sv.addView(p);
        outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        add(p, btn("🔗  1. Połącz (biblioteka HBand)", "#16A34A", "#15803D", v -> connect()));
        add(p, btn("🕒  Ustaw godzinę i datę z telefonu", "#2563EB", "#1D4ED8", v -> setTime()));
        add(p, btn("😴  Pobierz sen (ostatnie noce)", "#4F46E5", "#4338CA", v -> readSleep()));
        add(p, btn("🖼️  Tarcza ze zdjęcia – wybierz zdjęcie", "#DB2777", "#BE185D", v -> pickPhoto()));
        LinearLayout mr = new LinearLayout(this);
        String[][] lv = {{"Wibracja: słaba", "#0EA5A4", "WEAK"}, {"średnia", "#0284C7", "MEDIUM"}, {"mocna", "#DC2626", "STRONG"}};
        for (String[] x : lv) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            mr.addView(btn(x[0], x[1], x[1], v -> setMotor(x[2])), lp);
        }
        add(p, mr);
        LinearLayout vr = new LinearLayout(this);
        String[][] vol = {{"🔉 Głośność cicho", "4"}, {"🔊 średnio", "10"}, {"📢 max", "16"}};
        for (String[] x : vol) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            vr.addView(btn(x[0], "#F59E0B", "#D97706", v -> setVolume(Integer.parseInt(x[1]))), lp);
        }
        add(p, vr);
        TextView h1 = new TextView(this);
        h1.setText("Przełącz tarczę (także dodane)");
        h1.setTextSize(15);
        h1.setTypeface(Typeface.DEFAULT_BOLD);
        h1.setPadding(dp(8), dp(14), 0, dp(2));
        p.addView(h1);
        LinearLayout sw = new LinearLayout(this);
        String[][] sws = {{"🖼️ Zdjęcie", "#DB2777"}, {"⬇️ Pobrana", "#7C3AED"}, {"⌚ Wbudowane", "#2563EB"}};
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            sw.addView(btn(sws[k][0], sws[k][1], sws[k][1], v -> switchDial(kk)), lp);
        }
        add(p, sw);

        TextView h2 = new TextView(this);
        h2.setText("Wygląd tarczy ze zdjęcia");
        h2.setTextSize(15);
        h2.setTypeface(Typeface.DEFAULT_BOLD);
        h2.setPadding(dp(8), dp(14), 0, dp(2));
        p.addView(h2);
        bPos = btn("", "#0EA5A4", "#0F766E", v -> { iPos = (iPos + 1) % POS.length; styleLabels(); });
        bUp = btn("", "#F59E0B", "#D97706", v -> { iUp = (iUp + 1) % EL.length; styleLabels(); });
        bDown = btn("", "#F59E0B", "#D97706", v -> { iDown = (iDown + 1) % EL.length; styleLabels(); });
        bCol = btn("", "#334155", "#1E293B", v -> { iCol = (iCol + 1) % COL.length; styleLabels(); });
        add(p, bPos);
        add(p, bUp);
        add(p, bDown);
        add(p, bCol);
        add(p, btn("✅  Zastosuj wygląd na zegarku", "#16A34A", "#15803D", v -> applyStyle()));
        add(p, btn("🇵🇱  Język zegarka: polski (data dzień.miesiąc)", "#DC2626", "#B91C1C", v -> setPolish()));
        styleLabels();

        add(p, btn("↩️  Zakończ i wróć do zwykłego połączenia", "#64748B", "#475569", v -> finish()));

        pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setVisibility(View.GONE);
        add(p, pb);

        tvOut = new TextView(this);
        tvOut.setTextSize(14);
        tvOut.setTextColor(Color.parseColor("#263238"));
        tvOut.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(Color.WHITE);
        cg.setCornerRadius(dp(22));
        tvOut.setBackground(cg);
        tvOut.setLineSpacing(dp(3), 1f);
        tvOut.setText("Ten ekran używa oficjalnej biblioteki producenta zegarka. Na czas pracy tutaj zwykłe połączenie aplikacji jest wstrzymane – wróci po wyjściu.");
        add(p, tvOut);

        setContentView(outer);
    }

    // ---------------- pomocnicze ----------------

    private void out(String s) {
        main.post(() -> {
            tvOut.append("\n" + s);
            BleService.appendLog(this, "SDK: " + s);
        });
    }

    private void status(String s) {
        main.post(() -> tvStatus.setText(s));
    }

    private final IBleWriteResponse wr = code -> { };

    private String mac() {
        return getSharedPreferences("ust", MODE_PRIVATE).getString("adres", BleService.DEFAULT_ADDR);
    }

    private DeviceTimeSetting now() {
        Calendar c = Calendar.getInstance();
        return new DeviceTimeSetting(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND), ETimeMode.MODE_24);
    }

    private boolean need() {
        if (!ready) { out("⚠️ Najpierw połącz (przycisk 1)."); return false; }
        return true;
    }

    // ---------------- połączenie ----------------

    private void connect() {
        status("Wstrzymuję zwykłe połączenie…");
        Intent i = new Intent(this, BleService.class);
        i.setAction(BleService.ACTION_STOP);
        startService(i);
        main.postDelayed(() -> {
            status("Łączę z zegarkiem…");
            main.postDelayed(() -> { if (!ready) out("⏳ Długo to trwa – sprawdź, czy zegarek jest blisko. Możesz stuknąć „Połącz” jeszcze raz."); }, 25000);
            try {
                VPOperateManager.getInstance().connectDevice(mac(), "JS300",
                        (code, profile, oad) -> out("Połączenie: kod " + code + (oad ? " (tryb aktualizacji)" : "")),
                        state -> {
                            out("Nasłuch: kod " + state);
                            if (state == 0) main.postDelayed(this::confirm, 500);
                        });
            } catch (Throwable t) {
                out("Błąd łączenia: " + t);
            }
        }, 2500);
    }

    private void confirm() {
        status("Potwierdzam hasło i ustawiam godzinę…");
        VPOperateManager.getInstance().confirmDevicePwd(wr, new IPwdDataListener() {
            @Override
            public void onPwdDataChange(PwdData d) {
                out("✅ Zegarek: wersja " + d.getDeviceVersion() + ", numer " + d.getDeviceNumber());
                ready = true;
                status("Połączono – możesz korzystać z przycisków");
            }

            @Override
            public void onConnectionConfirmTimeout() {
                out("❌ Zegarek nie potwierdził połączenia – spróbuj jeszcze raz.");
            }
        }, new IDeviceFuctionDataListener() {
            @Override public void onFunctionSupportDataChange(FunctionDeviceSupportData f) {
                watchDays = Math.max(1, f.getWathcDay());
                out("Zegarek pamięta dni: " + f.getWathcDay());
            }
            @Override public void onDeviceFunctionPackage1Report(DeviceFunctionPackage1 p) { }
            @Override public void onDeviceFunctionPackage2Report(DeviceFunctionPackage2 p) { }
            @Override public void onDeviceFunctionPackage3Report(DeviceFunctionPackage3 p) { }
            @Override public void onDeviceFunctionPackage4Report(DeviceFunctionPackage4 p) { }
            @Override public void onDeviceFunctionPackage5Report(DeviceFunctionPackage5 p) { }
        }, new ISocialMsgDataListener() {
            @Override public void onSocialMsgSupportDataChange(FunctionSocailMsgData d) { }
            @Override public void onSocialMsgSupportDataChange2(FunctionSocailMsgData d) { }
        }, new ICustomSettingDataListener() {
            @Override public void OnSettingDataChange(CustomSettingData d) { }
        }, "0000", true, now());
    }

    // ---------------- godzina ----------------

    private void setTime() {
        if (!need()) return;
        VPOperateManager.getInstance().settingTime(wr, state -> out("🕒 Godzina ustawiona (odpowiedź " + state + ")"), now());
    }

    // ---------------- przełączanie i wygląd tarczy ----------------

    private static final String[][] POS = {{"CENTER_CENTER", "środek"}, {"CENTER_TOP", "góra"}, {"CENTER_BOTTOM", "dół"},
            {"LEFT_TOP", "lewa góra"}, {"RIGHT_TOP", "prawa góra"}, {"LEFT_BOTTOM", "lewy dół"}, {"RIGHT_BOTTOM", "prawy dół"}};
    private static final String[][] EL = {{"DATE_STYLE_1", "data"}, {"DATE_STYLE_2", "data (styl 2)"}, {"BLUETOOTH_BATTERY", "bateria"},
            {"STEP", "kroki"}, {"HEART", "tętno"}, {"KCAL", "kalorie"}, {"DISTANCE", "dystans"}, {"SLEEP", "sen"}, {"NONE", "nic"}};
    private static final int[] COL = {0xFFFFFF, 0x000000, 0xFFD700, 0x00E5FF, 0xFF4081, 0x76FF03};
    private static final String[] COLN = {"biały", "czarny", "złoty", "turkusowy", "różowy", "zielony"};
    private int iPos = 0, iUp = 0, iDown = 2, iCol = 0;
    private Button bPos, bUp, bDown, bCol;

    private void styleLabels() {
        bPos.setText("Godzina: " + POS[iPos][1]);
        bUp.setText("Nad godziną: " + EL[iUp][1]);
        bDown.setText("Pod godziną: " + EL[iDown][1]);
        bCol.setText("Kolor napisów: " + COLN[iCol]);
    }

    private void applyStyle() {
        if (!need()) return;
        try {
            com.veepoo.protocol.model.datas.UICustomSetData d = new com.veepoo.protocol.model.datas.UICustomSetData(false,
                    com.veepoo.protocol.model.enums.EWatchUIElementPosition.valueOf(POS[iPos][0]),
                    com.veepoo.protocol.model.enums.EWatchUIElementType.valueOf(EL[iUp][0]),
                    com.veepoo.protocol.model.enums.EWatchUIElementType.valueOf(EL[iDown][0]), COL[iCol]);
            VPOperateManager.getInstance().setCustomWacthUi(wr, d, info -> out("✅ Zegarek przyjął wygląd: " + info));
            out("Wysłano wygląd: godzina " + POS[iPos][1] + ", nad: " + EL[iUp][1] + ", pod: " + EL[iDown][1] + ", kolor " + COLN[iCol]);
        } catch (Throwable t) {
            out("❌ Wygląd – błąd: " + t.getMessage());
        }
    }

    private void setPolish() {
        if (!need()) return;
        try {
            VPOperateManager.getInstance().settingDeviceLanguage(wr,
                    d -> out("🇵🇱 Zegarek: " + d),
                    com.veepoo.protocol.model.enums.ELanguage.POLISH);
            out("Wysłano: język polski – data i dni tygodnia po polsku");
        } catch (Throwable t) {
            out("❌ Język – błąd: " + t.getMessage());
        }
    }

    private int sysIdx = 0;

    private void switchDial(int kind) {
        ensureJL(() -> {
            try {
                if (kind == 0) { JLWatchFaceManager.switch2PicDial(); out("Przełączam na tarczę ze zdjęcia"); }
                else if (kind == 1) { JLWatchFaceManager.switch2ServerDial(); out("Przełączam na pobraną tarczę"); }
                else {
                    int n = Math.max(1, systemDials);
                    JLWatchFaceManager.switch2SystemDial(sysIdx % n);
                    out("Przełączam na wbudowaną tarczę " + (sysIdx % n + 1) + " z " + n + " (stuknij znowu – następna)");
                    sysIdx++;
                }
            } catch (Throwable t) {
                out("❌ Przełączanie – błąd: " + t.getMessage());
            }
        });
    }

    // ---------------- wibracja i głośność ----------------

    private void setMotor(String level) {
        if (!need()) return;
        try {
            VPOperateManager.getInstance().setMotorLevel(com.veepoo.protocol.model.enums.EMotorLevel.valueOf(level), wr,
                    new com.veepoo.protocol.listener.data.IMotorOperateListener() {
                        @Override public void onMotorLevelRead(com.veepoo.protocol.model.datas.MotorLevelData d) { }
                        @Override public void onMotorLevelSetting(com.veepoo.protocol.model.datas.MotorLevelData d) {
                            out(d.isSuccess() ? "📳 Siła wibracji ustawiona: " + d.getLevel() : "❌ Zegarek nie obsługuje zmiany siły wibracji");
                        }
                        @Override public void onMotorLevelReport(com.veepoo.protocol.model.datas.MotorLevelData d) { }
                    });
            out("Wysłano ustawienie wibracji: " + level);
        } catch (Throwable t) {
            out("Wibracja – błąd: " + t.getMessage());
        }
    }

    private void setVolume(int v) {
        if (!need()) return;
        try {
            VPOperateManager.getInstance().settingVolume(v, wr, new com.veepoo.protocol.listener.data.IMusicControlListener() {
                @Override public void nextMusic() { }
                @Override public void previousMusic() { }
                @Override public void pauseAndPlayMusic() { }
                @Override public void pauseMusic() { }
                @Override public void playMusic() { }
                @Override public void voiceUp() { }
                @Override public void voiceDown() { }
                @Override public void oprateMusicSuccess() { out("🔊 Głośność ustawiona: " + v); }
                @Override public void oprateMusicFail() { out("❌ Zegarek nie przyjął zmiany głośności"); }
            });
            out("Wysłano głośność: " + v);
        } catch (Throwable t) {
            out("Głośność – błąd: " + t.getMessage());
        }
    }

    // ---------------- sen ----------------

    private void readSleep() {
        if (!need()) return;
        out("😴 Pobieram sen…");
        VPOperateManager.getInstance().readSleepData(wr, new ISleepDataListener() {
            @Override
            public void onSleepDataChange(String day, SleepData s) {
                try {
                    String down = s.getSleepDown() == null ? "?" : s.getSleepDown().getClock();
                    String up = s.getSleepUp() == null ? "?" : s.getSleepUp().getClock();
                    String date = s.getDate() != null ? s.getDate() : day;
                    int all = s.getAllSleepTime(), deep = s.getDeepSleepTime(), light = s.getLowSleepTime();
                    out(String.format("Noc do %s: %s → %s, razem %dh %02dmin (głęboki %dh %02dmin, płytki %dh %02dmin), pobudki: %d, jakość: %d/5",
                            date, down, up, all / 60, all % 60, deep / 60, deep % 60, light / 60, light % 60, s.getWakeCount(), s.getSleepQulity()));
                    JSONObject j = new JSONObject();
                    j.put("od", down);
                    j.put("do", up);
                    j.put("min", all);
                    j.put("gleboki", deep);
                    j.put("plytki", light);
                    j.put("pobudki", s.getWakeCount());
                    j.put("jakosc", s.getSleepQulity());
                    j.put("linia", s.getSleepLine());
                    HistStore.putSleep(SdkActivity.this, date, j);
                } catch (Exception e) {
                    out("Sen (surowo): " + s);
                }
            }

            @Override public void onSleepProgress(float progress) { }
            @Override public void onSleepProgressDetail(String day, int n) { }

            @Override
            public void onReadSleepComplete() {
                HistStore.flush();
                out("😴 Sen pobrany.");
            }
        }, watchDays);
    }

    // ---------------- tarcza ze zdjęcia ----------------

    private void pickPhoto() {
        if (!need()) return;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Wybierz zdjęcie na tarczę"), PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            photoUri = data.getData();
            retried = false;
            photoPath = copyPhoto(photoUri);
            out("🖼️ Zdjęcie gotowe, wysyłam na zegarek…");
            sendPhotoDial();
        } catch (Exception e) {
            out("Nie udało się odczytać zdjęcia: " + e.getMessage());
        }
    }

    /** Kopiuje zdjęcie do pliku aplikacji (kwadrat, max 480 px). */
    private String copyPhoto(Uri uri) throws Exception {
        Bitmap src;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            src = BitmapFactory.decodeStream(in);
        }
        if (src == null) throw new Exception("nieznany format");
        int side = Math.min(src.getWidth(), src.getHeight());
        Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
        Bitmap sc = Bitmap.createScaledBitmap(sq, dialW, dialH, true);
        File dir = getExternalFilesDir("tarcze");
        if (dir == null) dir = getFilesDir();
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File f = new File(dir, "tarcza.png");
        try (FileOutputStream fo = new FileOutputStream(f)) {
            sc.compress(Bitmap.CompressFormat.PNG, 100, fo);
        }
        return f.getAbsolutePath();
    }

    private boolean stepDone = false;

    private Runnable afterJL = null;
    private int systemDials = 0;
    private boolean jlReady = false;

    private void ensureJL(Runnable then) {
        if (!need()) return;
        if (jlReady) { then.run(); return; }
        afterJL = then;
        startJL();
    }

    private void sendPhotoDial() {
        pb.setVisibility(View.VISIBLE);
        pb.setProgress(0);
        if (jlReady) { uploadPhoto(); return; }
        afterJL = this::uploadPhoto;
        startJL();
    }

    private void startJL() {
        if (VPOperateManager.getInstance().isJLNotifyOpened()) {
            out("Kanał tarcz już otwarty");
            auth();
            return;
        }
        VPOperateManager.getInstance().openJLDataNotify(new BleNotifyResponse() {
            @Override public void onNotify(UUID s, UUID c, byte[] v) { }

            @Override
            public void onResponse(int code) {
                out("Kanał tarcz: kod " + code);
                VPOperateManager.getInstance().changeMTU(247, mtu -> out("MTU: " + mtu));
                main.postDelayed(() -> auth(), 1500);
            }
        });
    }

    private void auth() {
        if (com.inuker.bluetooth.library.jieli.RcspAuthManager.getInstance().isAuthPass()) {
            out("Autoryzacja tarcz: już OK");
            fileSystem();
            return;
        }
        out("Autoryzacja tarcz…");
        stepDone = false;
        VPOperateManager.getInstance().startJLDeviceAuth(new RcspAuthResponse() {
            @Override public void onRcspAuthStart() { out("…trwa"); }
            @Override public void onRcspAuthSuccess() { if (!stepDone) { stepDone = true; out("Autoryzacja OK"); main.post(() -> fileSystem()); } }
            @Override public void onRcspAuthFailed() { if (!stepDone) { stepDone = true; out("❌ Zegarek nie przyjął autoryzacji tarcz."); main.post(() -> pb.setVisibility(View.GONE)); } }
        });
        main.postDelayed(() -> {
            if (!stepDone) {
                stepDone = true;
                out("Brak odpowiedzi autoryzacji – próbuję dalej");
                fileSystem();
            }
        }, 10000);
    }

    private boolean fsDone = false;

    private void fileSystem() {
        fsDone = false;
        out("Odczytuję tarcze w zegarku…");
        VPOperateManager.getInstance().listJLWatchList(new JLWatchFaceManager.OnWatchDialInfoGetListener() {
            @Override public void onGettingWatchDialInfo() { }
            @Override public void onWatchDialInfoGetStart() { }
            @Override public void onWatchDialInfoGetComplete() { }
            @Override public void onWatchDialInfoGetSuccess(java.util.List<com.jieli.jl_fatfs.model.FatFile> sys,
                                                             java.util.List<com.jieli.jl_fatfs.model.FatFile> srv,
                                                             com.jieli.jl_fatfs.model.FatFile pic) {
                if (fsDone) return;
                fsDone = true;
                out("Tarcze w zegarku: wbudowane " + (sys == null ? 0 : sys.size()) + ", pobrane " + (srv == null ? 0 : srv.size()));
                systemDials = sys == null ? 0 : sys.size();
                jlReady = true;
                main.post(() -> { if (afterJL != null) afterJL.run(); });
            }
            @Override public void onWatchDialInfoGetFailed(com.jieli.jl_rcsp.model.base.BaseError e) {
                if (fsDone) return;
                fsDone = true;
                out("Lista tarcz niedostępna (" + e + ") – próbuję dalej");
                jlReady = true;
                main.post(() -> { if (afterJL != null) afterJL.run(); });
            }
        });
        main.postDelayed(() -> { if (!fsDone) { fsDone = true; jlReady = true; out("Brak listy tarcz – próbuję dalej"); if (afterJL != null) afterJL.run(); } }, 12000);
    }

    private void uploadPhoto() {
        VPOperateManager.getInstance().setJLWatchPhotoDial(photoPath, new JLWatchFaceManager.JLTransferPicDialListener() {
            @Override public void onLowPower() { out("❌ Za słaba bateria zegarka do wgrania tarczy."); }
            @Override public void onJLTransferPicDialStart() { out("Wysyłanie tarczy…"); }
            @Override public void onTransferPicDialProgress(int p) { main.post(() -> { pb.setProgress(p); tvStatus.setText("Wgrywanie tarczy: " + p + "%"); }); }
            @Override public void onScaleBGPFileTransferComplete() { }
            @Override public void onAIPreviewTransferComplete() { }
            @Override public void onBigBGPFileTransferComplete() { }
            @Override public void onTransferComplete() {
                out("✅ Tarcza ze zdjęcia wgrana – sprawdź zegarek.");
                try { JLWatchFaceManager.switch2PicDial(); } catch (Throwable ignored) { }
                main.post(() -> pb.setVisibility(View.GONE));
            }
            @Override public void onTransferError(int code, String msg) {
                // zegarek podaje wymagany rozmiar, np. „466 / 466” – dopasuj i spróbuj raz jeszcze
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{3})\\s*/\\s*(\\d{3})").matcher(msg == null ? "" : msg);
                if (!retried && m.find() && photoUri != null) {
                    retried = true;
                    dialW = Integer.parseInt(m.group(1));
                    dialH = Integer.parseInt(m.group(2));
                    out("Dopasowuję zdjęcie do ekranu " + dialW + "×" + dialH + " i wysyłam ponownie…");
                    main.post(() -> {
                        try { photoPath = copyPhoto(photoUri); uploadPhoto(); }
                        catch (Exception e) { out("❌ " + e.getMessage()); }
                    });
                    return;
                }
                out("❌ Błąd wgrywania tarczy (" + code + ")");
                main.post(() -> pb.setVisibility(View.GONE));
            }
        });
    }

    @Override
    protected void onDestroy() {
        try { VPOperateManager.getInstance().disconnectWatch(wr); } catch (Throwable ignored) { }
        main.postDelayed(() -> {
            try { startForegroundService(new Intent(getApplicationContext(), BleService.class)); } catch (Exception ignored) { }
        }, 2000);
        super.onDestroy();
    }
}
