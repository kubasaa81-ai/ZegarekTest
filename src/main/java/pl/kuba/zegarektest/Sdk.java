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

    /** Jeden kawałek snu z zegarka. */
    static class Seg {
        long start, end; // minuty od epoki
        int all, deep, light, wakes, quality;
        String line;
        java.util.Calendar endCal;
    }

    private static long minutes(com.veepoo.protocol.model.datas.TimeData t, java.util.Calendar out) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.clear();
        c.set(t.getYear(), t.getMonth() - 1, t.getDay(), t.getHour(), t.getMinute(), 0);
        if (out != null) out.setTimeInMillis(c.getTimeInMillis());
        return c.getTimeInMillis() / 60000L;
    }

    /** Linia faz w zapisie co 5 min: 0 płytki, 1 głęboki, 2 czuwanie. */
    private static String to5min(String line, long span) {
        if (line == null) line = "";
        boolean precise = line.contains("3") || line.contains("4") || (span > 0 && line.length() >= span * 0.8);
        if (!precise) return line;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < line.length(); i += 5) {
            char ch = line.charAt(i);
            b.append(ch == '0' ? '1' : (ch == '3' || ch == '4') ? '2' : '0');
        }
        return b.toString();
    }

    private static final java.text.SimpleDateFormat DAY = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
    private static final java.text.SimpleDateFormat HM = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);

    /** Pobiera sen z ostatnich nocy, skleja kawałki jednej nocy i zapisuje w historii. */
    public static void readSleep(Context ctx, Out out, Runnable done) {
        final Context c = ctx.getApplicationContext();
        final java.util.List<Seg> segs = new java.util.ArrayList<>();
        final java.util.Set<String> days = new java.util.HashSet<>();
        m().readSleepData(wr, new ISleepDataListener() {
            @Override
            public void onSleepDataChange(String day, SleepData s) {
                try {
                    if (s.getDate() != null) days.add(s.getDate());
                    if (s.getSleepDown() == null || s.getSleepUp() == null) return;
                    Seg g = new Seg();
                    g.endCal = java.util.Calendar.getInstance();
                    g.start = minutes(s.getSleepDown(), null);
                    g.end = minutes(s.getSleepUp(), g.endCal);
                    if (g.end <= g.start) return;
                    g.all = s.getAllSleepTime();
                    g.deep = s.getDeepSleepTime();
                    g.light = s.getLowSleepTime();
                    g.wakes = s.getWakeCount();
                    g.quality = s.getSleepQulity();
                    g.line = s.getSleepLine();
                    synchronized (segs) {
                        for (Seg o : segs) if (o.start == g.start) return; // duplikat
                        segs.add(g);
                    }
                    out.line(String.format(java.util.Locale.US, "Sen – kawałek %s → %s (%d min, zegarek: dzień %s)",
                            s.getSleepDown().getClock(), s.getSleepUp().getClock(), g.all, s.getDate()));
                } catch (Exception e) {
                    out.line("Sen (surowo): " + s);
                }
            }

            @Override public void onSleepProgress(float progress) { }
            @Override public void onSleepProgressDetail(String day, int k) { }

            @Override
            public void onReadSleepComplete() {
                int nights = 0;
                try {
                    java.util.List<Seg> l;
                    synchronized (segs) { l = new java.util.ArrayList<>(segs); }
                    java.util.Collections.sort(l, (a, b) -> Long.compare(a.start, b.start));
                    java.util.Set<String> written = new java.util.HashSet<>();
                    int i = 0;
                    while (i < l.size()) {
                        java.util.List<Seg> night = new java.util.ArrayList<>();
                        night.add(l.get(i));
                        int k = i + 1;
                        while (k < l.size() && l.get(k).start - night.get(night.size() - 1).end <= 180) night.add(l.get(k++));
                        i = k;
                        Seg first = night.get(0), last = night.get(night.size() - 1);
                        int all = 0, deep = 0, light = 0, wakes = 0, q = 0;
                        StringBuilder line = new StringBuilder();
                        for (int n = 0; n < night.size(); n++) {
                            Seg g = night.get(n);
                            all += g.all; deep += g.deep; light += g.light; wakes += g.wakes; q = Math.max(q, g.quality);
                            if (night.size() == 1) { line.append(g.line == null ? "" : g.line); continue; }
                            if (n > 0) {
                                long gap = g.start - night.get(n - 1).end;
                                for (long x = 0; x < gap / 5; x++) line.append('2');
                                wakes++;
                            }
                            line.append(to5min(g.line, g.end - g.start));
                        }
                        String day = DAY.format(last.endCal.getTime());
                        JSONObject j = new JSONObject();
                        j.put("od", HM.format(new java.util.Date(first.start * 60000L)));
                        j.put("do", HM.format(new java.util.Date(last.end * 60000L)));
                        j.put("min", all);
                        j.put("gleboki", deep);
                        j.put("plytki", light);
                        j.put("pobudki", wakes);
                        j.put("jakosc", q);
                        j.put("linia", line.toString());
                        if (night.size() > 1) j.put("czesci", night.size());
                        HistStore.putSleep(c, day, j);
                        written.add(day);
                        nights++;
                        out.line(String.format(java.util.Locale.US, "Noc do %s: %s → %s, sen %dh %02dmin%s", day,
                                j.getString("od"), j.getString("do"), all / 60, all % 60,
                                night.size() > 1 ? " (sklejone " + night.size() + " części)" : ""));
                    }
                    // dni, które zegarek zgłosił, a których noc trafiła pod inny dzień – usuń stare, błędne wpisy
                    for (String d : days) if (!written.contains(d)) HistStore.removeSleep(c, d);
                } catch (Exception e) {
                    out.line("Sen – błąd sklejania: " + e);
                }
                HistStore.flush();
                out.line("Sen pobrany (nocy: " + nights + ")");
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

    public static volatile String phoneStatus = "";

    private static boolean closed(com.veepoo.protocol.model.enums.EFunctionStatus st) {
        return st == com.veepoo.protocol.model.enums.EFunctionStatus.SUPPORT_CLOSE;
    }

    /** Włącza w zegarku powiadomienia: połączenia, SMS, WhatsApp, Messenger, Facebook, inne. */
    public static void enableMsgs(Out out, Runnable then) {
        if (!ready) { out.line("⚠️ Zegarek nie jest teraz połączony"); return; }
        final boolean[] done = {false};
        final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable fin = () -> { if (!done[0]) { done[0] = true; if (then != null) h.post(then); } };
        try {
            m().readSocialMsg(wr, new com.veepoo.protocol.listener.data.ISocialMsgDataListener() {
                @Override
                public void onSocialMsgSupportDataChange(com.veepoo.protocol.model.datas.FunctionSocailMsgData d) {
                    if (done[0]) return;
                    phoneStatus = String.valueOf(d.getPhone());
                    boolean ch = false;
                    com.veepoo.protocol.model.enums.EFunctionStatus open = com.veepoo.protocol.model.enums.EFunctionStatus.SUPPORT_OPEN;
                    if (closed(d.getPhone())) { d.setPhone(open); ch = true; }
                    if (closed(d.getMsg())) { d.setMsg(open); ch = true; }
                    if (closed(d.getWhats())) { d.setWhats(open); ch = true; }
                    if (closed(d.getMessenger())) { d.setMessenger(open); ch = true; }
                    if (closed(d.getFacebook())) { d.setFacebook(open); ch = true; }
                    if (closed(d.getOther())) { d.setOther(open); ch = true; }
                    if (!ch) {
                        out.line("Powiadomienia w zegarku: połączenia " + phoneStatus);
                        fin.run();
                        return;
                    }
                    out.line("Włączam w zegarku powiadomienia (połączenia były: " + phoneStatus + ")");
                    try {
                        m().settingSocialMsg(wr, new com.veepoo.protocol.listener.data.ISocialMsgDataListener() {
                            @Override public void onSocialMsgSupportDataChange(com.veepoo.protocol.model.datas.FunctionSocailMsgData x) {
                                phoneStatus = String.valueOf(x.getPhone());
                                out.line("✅ Powiadomienia w zegarku włączone (połączenia: " + phoneStatus + ")");
                                fin.run();
                            }
                            @Override public void onSocialMsgSupportDataChange2(com.veepoo.protocol.model.datas.FunctionSocailMsgData x) { }
                        }, d);
                    } catch (Throwable t) { out.line("Powiadomienia: " + t.getMessage()); fin.run(); }
                }

                @Override public void onSocialMsgSupportDataChange2(com.veepoo.protocol.model.datas.FunctionSocailMsgData d) { }
            });
        } catch (Throwable t) {
            out.line("Powiadomienia: " + t.getMessage());
            fin.run();
        }
        h.postDelayed(fin, 6000);
    }
}
