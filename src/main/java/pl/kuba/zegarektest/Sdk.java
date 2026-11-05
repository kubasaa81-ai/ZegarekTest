package pl.kuba.zegarektest;

import android.content.Context;

import com.inuker.bluetooth.library.BluetoothClient;
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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Calendar;

/** Wspólne połączenie przez bibliotekę producenta (HBand SDK). */
public class Sdk {

    public interface Out { void line(String s); }

    public static volatile boolean ready = false;
    public static volatile int watchDays = 3;
    public static volatile FunctionDeviceSupportData func;
    public static volatile String version = "";

    public static final IBleWriteResponse wr = code -> { };

    public static VPOperateManager m() { return VPOperateManager.getInstance(); }

    public static DeviceTimeSetting now() {
        Calendar c = Calendar.getInstance();
        return new DeviceTimeSetting(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND), ETimeMode.MODE_24);
    }

    /** Hasło 0000 + ustawienie godziny. ok/fail wywoływane raz. */
    public static void confirm(Out out, Runnable ok, Runnable fail) {
        final boolean[] done = {false};
        m().confirmDevicePwd(wr, new IPwdDataListener() {
            @Override
            public void onPwdDataChange(PwdData d) {
                version = String.valueOf(d.getDeviceVersion());
                if (!done[0]) { done[0] = true; ok.run(); }
            }

            @Override
            public void onConnectionConfirmTimeout() {
                out.line("Zegarek nie potwierdził hasła");
                if (!done[0]) { done[0] = true; fail.run(); }
            }
        }, new IDeviceFuctionDataListener() {
            @Override public void onFunctionSupportDataChange(FunctionDeviceSupportData f) {
                func = f;
                watchDays = Math.max(1, f.getWathcDay());
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

    /** Klient Bluetooth biblioteki – przez niego wysyłamy własne komendy tym samym połączeniem. */
    public static BluetoothClient client() {
        try {
            for (Field f : VPOperateManager.class.getDeclaredFields()) {
                if (f.getType() == BluetoothClient.class) {
                    f.setAccessible(true);
                    Object o = Modifier.isStatic(f.getModifiers()) ? f.get(null) : f.get(m());
                    if (o != null) return (BluetoothClient) o;
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /** Pobiera sen z ostatnich nocy i zapisuje w historii. */
    public static void readSleep(Context ctx, Out out, Runnable done) {
        final Context c = ctx.getApplicationContext();
        final int[] n = {0};
        m().readSleepData(wr, new ISleepDataListener() {
            @Override
            public void onSleepDataChange(String day, SleepData s) {
                try {
                    String down = s.getSleepDown() == null ? "?" : s.getSleepDown().getClock();
                    String up = s.getSleepUp() == null ? "?" : s.getSleepUp().getClock();
                    String date = s.getDate() != null ? s.getDate() : day;
                    int all = s.getAllSleepTime(), deep = s.getDeepSleepTime(), light = s.getLowSleepTime();
                    JSONObject j = new JSONObject();
                    j.put("od", down);
                    j.put("do", up);
                    j.put("min", all);
                    j.put("gleboki", deep);
                    j.put("plytki", light);
                    j.put("pobudki", s.getWakeCount());
                    j.put("jakosc", s.getSleepQulity());
                    j.put("linia", s.getSleepLine());
                    HistStore.putSleep(c, date, j);
                    n[0]++;
                    out.line(String.format("Sen – noc do %s: %s → %s, %dh %02dmin", date, down, up, all / 60, all % 60));
                } catch (Exception e) {
                    out.line("Sen (surowo): " + s);
                }
            }

            @Override public void onSleepProgress(float progress) { }
            @Override public void onSleepProgressDetail(String day, int k) { }

            @Override
            public void onReadSleepComplete() {
                HistStore.flush();
                out.line("Sen pobrany (nocy: " + n[0] + ")");
                if (done != null) done.run();
            }
        }, watchDays);
    }

    public static boolean supported(Object status) {
        return status != null && String.valueOf(status).equals("SUPPORT");
    }

    /** Godziny działania „ruch ręki zapala ekran”: {od, do}; {0,23} = cały dzień. */
    public static final int[][] WRIST = {{0, 23}, {7, 22}, {6, 23}, {8, 21}};

    /** Ustawia w zegarku podświetlanie ruchem ręki wg ustawień aplikacji (czułość zostaje z zegarka). */
    public static void wrist(Context c, Out out) {
        if (!ready) { out.line("⚠️ Zegarek nie jest teraz połączony"); return; }
        android.content.SharedPreferences us = c.getSharedPreferences("ust", Context.MODE_PRIVATE);
        boolean on = us.getBoolean("nadgOn", true);
        int[] h = WRIST[Math.min(WRIST.length - 1, us.getInt("nadgGodz", 0))];
        try {
            m().readNightTurnWriste(wr, d -> {
                int lvl = d.getLevel() > 0 ? d.getLevel() : (d.getDefaultLevel() > 0 ? d.getDefaultLevel() : 5);
                try {
                    m().settingNightTurnWriste(wr, x -> out.line("✅ Ruch ręki: " + (x.isNightTureWirsteStatusOpen() ? "włączony" : "wyłączony")
                                    + " (" + x.getOprateStauts() + ")"),
                            new com.veepoo.protocol.model.settings.NightTurnWristSetting(on,
                                    new com.veepoo.protocol.model.datas.TimeData(h[0], 0),
                                    new com.veepoo.protocol.model.datas.TimeData(h[1], h[1] == 23 ? 59 : 0), lvl));
                } catch (Throwable t) { out.line("Ruch ręki: " + t.getMessage()); }
            });
        } catch (Throwable t) {
            out.line("Ruch ręki: " + t.getMessage());
        }
    }
}
