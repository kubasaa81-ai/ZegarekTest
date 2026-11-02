package pl.kuba.zegarektest;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@SuppressLint("MissingPermission")
public class BleService extends Service {

    public static final String DEFAULT_ADDR = "D4:1A:DB:29:D3:35";
    public static final String ACTION_STOP = "pl.kuba.zegarektest.STOP";
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String CH_ID = "ble";
    private static final Object LOCK = new Object();

    public interface Listener {
        void onLog(String line);
        void onStateChanged();
    }

    public static volatile BleService instance;
    public static volatile Listener listener;
    public static volatile String state = "Zatrzymany";
    public static volatile String sSteps = "–", sDist = "–", sKcal = "–", sHr = "–", sHist = "–", sBp = "–", sBat = "–",
            sSpo2 = "–", sTemp = "–", sGlu = "–", sResp = "–";
    public static volatile int screenSec = 0;
    public static volatile int faceIdx = -1;
    public static volatile String lastWrite = "";

    // ---------------- alarmy ----------------
    public static final String[][] TH = {
            {"hrHi", "Tętno – max", "120"},
            {"hrLo", "Tętno – min", "45"},
            {"sysHi", "Ciśnienie skurczowe – max", "140"},
            {"diaHi", "Ciśnienie rozkurczowe – max", "90"},
            {"sysLo", "Ciśnienie skurczowe – min", "90"},
            {"spo2Lo", "Tlen – min (%)", "92"},
            {"tempHi", "Temperatura – max (°C)", "37.5"},
            {"gluHi", "Glukoza – max (mmol/L)", "10"},
            {"gluLo", "Glukoza – min (mmol/L)", "3.5"}
    };
    private final java.util.HashMap<String, Long> lastAlarm = new java.util.HashMap<>();
    private int liveHigh = 0, liveLow = 0;

    public static float th(Context c, String key) {
        for (String[] t : TH) if (t[0].equals(key))
            return c.getSharedPreferences("alarmy", MODE_PRIVATE).getFloat(key, Float.parseFloat(t[2]));
        return 0;
    }

    public static boolean alarmsOn(Context c) {
        return c.getSharedPreferences("alarmy", MODE_PRIVATE).getBoolean("on", true);
    }

    public void alarm(String key, String msg, boolean force) {
        if (!force && !alarmsOn(this)) return;
        long now = System.currentTimeMillis();
        Long last = lastAlarm.get(key);
        if (!force && last != null && now - last < 60 * 60 * 1000) return;
        lastAlarm.put(key, now);
        log("ALARM: " + msg);
        if (gatt != null) sendVeepoo(new byte[]{(byte) 0xB5, 0x0A, 0x01});
        Intent open = new Intent(this, DashActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, "alarm")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Uwaga – zegarek")
                .setContentText(msg)
                .setStyle(new Notification.BigTextStyle().bigText(msg))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            getSystemService(NotificationManager.class).notify(100 + Math.abs(key.hashCode() % 50), n);
        } catch (Exception ignored) { }
    }

    private boolean fresh(String dKey, String tKey) {
        if (dKey == null || tKey == null || !today().equals(dKey)) return false;
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int nowM = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
            int recM = Integer.parseInt(tKey.substring(0, 2)) * 60 + Integer.parseInt(tKey.substring(3, 5));
            return nowM - recM >= 0 && nowM - recM <= 30;
        } catch (Exception e) {
            return false;
        }
    }

    private void checkRecord(String tKey, int hr, int sys, int dia, int spo2, double glu) {
        String at = " (o " + tKey + ")";
        if (hr > 0 && hr > th(this, "hrHi")) alarm("hrHi", "Wysokie tętno: " + hr + " uderzeń/min" + at, false);
        if (hr > 0 && hr < th(this, "hrLo")) alarm("hrLo", "Niskie tętno: " + hr + " uderzeń/min" + at, false);
        if (sys > 0 && (sys >= th(this, "sysHi") || dia >= th(this, "diaHi")))
            alarm("bpHi", "Wysokie ciśnienie: " + sys + "/" + dia + " mmHg" + at, false);
        if (sys > 0 && sys < th(this, "sysLo")) alarm("bpLo", "Niskie ciśnienie: " + sys + "/" + dia + " mmHg" + at, false);
        if (spo2 > 0 && spo2 < th(this, "spo2Lo")) alarm("spo2", "Niski poziom tlenu: " + spo2 + "%" + at, false);
        if (glu > 0 && glu > th(this, "gluHi")) alarm("gluHi", String.format(Locale.US, "Wysoka glukoza (szac.): %.2f mmol/L", glu) + at, false);
        if (glu > 0 && glu < th(this, "gluLo")) alarm("gluLo", String.format(Locale.US, "Niska glukoza (szac.): %.2f mmol/L", glu) + at, false);
    }
    private final java.util.HashMap<Integer, String[]> histWhen = new java.util.HashMap<>();

    public static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static String dayKey(int month, int day) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int year = cal.get(java.util.Calendar.YEAR);
        if (month > cal.get(java.util.Calendar.MONTH) + 2) year--;
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private boolean wantConnected = false;
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private boolean busy = false;
    private int opId = 0;
    private boolean discoverStarted = false;
    private String lastFea1 = "";
    public final List<BluetoothGattCharacteristic> writable = new ArrayList<>();
    private final List<BluetoothGattCharacteristic> readable = new ArrayList<>();

    private final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int s = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
            if (s == BluetoothAdapter.STATE_ON) {
                log("Bluetooth włączony");
                if (wantConnected) main.postDelayed(BleService.this::connect, 1500);
            } else if (s == BluetoothAdapter.STATE_OFF) {
                log("Bluetooth wyłączony");
                closeGatt();
                setState("Bluetooth wyłączony");
            }
        }
    };

    // ---------------- cykl życia usługi ----------------

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_ID, "Połączenie z zegarkiem", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel al = new NotificationChannel("alarm", "Alarmy zdrowia", NotificationManager.IMPORTANCE_HIGH);
        al.enableVibration(true);
        al.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 400});
        nm.createNotificationChannel(al);
        startFg();
        IntentFilter f = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(btReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(btReceiver, f);
        }
    }

    private Notification buildNotif() {
        Intent open = new Intent(this, DashActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CH_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Zegarek JS300")
                .setContentText(state)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void startFg() {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, buildNotif(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(1, buildNotif());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            wantConnected = false;
            closeGatt();
            setState("Zatrzymany");
            log("Zatrzymano połączenie");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        wantConnected = true;
        connect();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(btReceiver); } catch (Exception ignored) { }
        closeGatt();
        instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    // ---------------- połączenie ----------------

    public void connect() {
        if (!wantConnected || gatt != null) return;
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
        if (ad == null || !ad.isEnabled()) {
            setState("Bluetooth wyłączony");
            return;
        }
        String addr = getSharedPreferences("ust", MODE_PRIVATE).getString("adres", DEFAULT_ADDR);
        BluetoothDevice dev;
        try {
            dev = ad.getRemoteDevice(addr);
        } catch (IllegalArgumentException e) {
            log("Zły adres: " + addr);
            setState("Zły adres zegarka");
            return;
        }
        log("Czekam na zegarek " + addr + " (łączenie automatyczne)");
        setState("Czekam na zegarek…");
        // autoConnect = true: Android sam połączy, gdy tylko zegarek będzie w zasięgu
        gatt = dev.connectGatt(this, true, cb, BluetoothDevice.TRANSPORT_LE);
    }

    private void closeGatt() {
        main.removeCallbacks(poll);
        queue.clear();
        busy = false;
        if (gatt != null) {
            try {
                gatt.disconnect();
                gatt.close();
            } catch (Exception ignored) { }
            gatt = null;
        }
        writable.clear();
        readable.clear();
    }

    private final BluetoothGattCallback cb = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            main.post(() -> {
                if (g != gatt) return;
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    log("POŁĄCZONO (status " + status + ")");
                    setState("Połączony – odczyt usług…");
                    discoverStarted = false;
                    main.postDelayed(() -> {
                        if (gatt != g) return;
                        if (!g.requestMtu(247)) startDiscover(g);
                    }, 600);
                    main.postDelayed(() -> { if (gatt == g) startDiscover(g); }, 4000);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    log("ROZŁĄCZONO (status " + status + ")");
                    closeGatt();
                    setState("Rozłączony – czekam na zegarek…");
                    if (wantConnected) main.postDelayed(BleService.this::connect, 2000);
                }
            });
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            main.post(() -> {
                if (g != gatt) return;
                log("MTU " + mtu + " (status " + status + ")");
                startDiscover(g);
            });
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            main.post(() -> {
                if (g != gatt) return;
                handleServices(g, status);
            });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
            byte[] v = ch.getValue();
            final byte[] val = v == null ? new byte[0] : v.clone();
            final String u = shortUuid(ch.getUuid());
            main.post(() -> handleNotify(u, val));
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic ch, int status) {
            byte[] v = ch.getValue();
            final byte[] val = v == null ? new byte[0] : v.clone();
            final String u = shortUuid(ch.getUuid());
            main.post(() -> {
                log("R " + u + "  " + (status == BluetoothGatt.GATT_SUCCESS ? hex(val) + ascii(val) : "błąd " + status));
                next();
            });
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic ch, int status) {
            final String u = shortUuid(ch.getUuid());
            main.post(() -> {
                if (status != BluetoothGatt.GATT_SUCCESS) log("Zapis " + u + " błąd " + status);
                next();
            });
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            final String u = shortUuid(d.getCharacteristic().getUuid());
            main.post(() -> {
                log("Nasłuch " + u + (status == BluetoothGatt.GATT_SUCCESS ? " włączony" : " błąd " + status));
                next();
            });
        }
    };

    private void startDiscover(BluetoothGatt g) {
        if (discoverStarted) return;
        discoverStarted = true;
        g.discoverServices();
    }

    // ---------------- dekodowanie danych z zegarka ----------------

    private void handleNotify(String u, byte[] v) {
        String h = hex(v);
        if (u.equals("FEA1")) {
            if (h.equals(lastFea1)) { decode(u, v); return; }
            lastFea1 = h;
        }
        log("← " + u + "  " + h + ascii(v));
        String d = decode(u, v);
        if (d != null) log("   ⇒ " + d);
    }

    private static int u8(byte[] v, int i) { return v[i] & 0xFF; }

    private static long le32(byte[] v, int i) {
        return (long) u8(v, i) | ((long) u8(v, i + 1) << 8) | ((long) u8(v, i + 2) << 16) | ((long) u8(v, i + 3) << 24);
    }

    private void notifyUi() {
        Listener l = listener;
        if (l != null) main.post(l::onStateChanged);
    }

    private String decode(String u, byte[] v) {
        try {
            if (u.equals("FEA1") || u.equals("FEA2")) {
                if (v.length >= 4 && (v[0] & 1) != 0) {
                    int st = u8(v, 1) | (u8(v, 2) << 8) | (u8(v, 3) << 16);
                    if (u.equals("FEA1") && !String.valueOf(st).equals(sSteps)) { sSteps = String.valueOf(st); notifyUi(); }
                    return "kroki: " + st;
                }
                return null;
            }
            if (!u.equals("f0080002") || v.length == 0) return null;
            int op = u8(v, 0);
            if (op == 0xD8 && v.length >= 14) {
                long st = le32(v, 2), di = le32(v, 6), kc = le32(v, 10);
                sSteps = String.valueOf(st);
                sDist = String.format(Locale.US, "%.2f km", di / 1000.0);
                sKcal = (kc / 10) + " kcal";
                HistStore.putDay(this, today(), st, di, kc / 10);
                notifyUi();
                return "dziś: " + st + " kroków, " + sDist + ", " + sKcal;
            }
            if (op == 0xDF) return decodeHist(v);
            if (op == 0xD0 && v.length >= 2) {
                int x = u8(v, 1);
                if (x >= 35 && x <= 220) {
                    liveHigh = x > th(this, "hrHi") ? liveHigh + 1 : 0;
                    liveLow = x < th(this, "hrLo") ? liveLow + 1 : 0;
                    if (liveHigh >= 10) alarm("hrHi", "Wysokie tętno: " + x + " uderzeń/min (pomiar na żywo)", false);
                    if (liveLow >= 10) alarm("hrLo", "Niskie tętno: " + x + " uderzeń/min (pomiar na żywo)", false);
                    sHr = x + " (na żywo)";
                    notifyUi();
                    return "tętno na żywo: " + x;
                }
                return null;
            }
            if (op == 0xB4 && v.length >= 4) {
                boolean ok = u8(v, 1) == 1;
                screenSec = u8(v, 3);
                if (u8(v, 2) == 1) lastWrite = ok ? "Zegarek przyjął: podświetlenie " + screenSec + " s" : "Zegarek odrzucił zmianę podświetlenia";
                notifyUi();
                return (ok ? "" : "ODRZUCONE – ") + "czas podświetlenia: " + screenSec + " s";
            }
            if (op == 0xC7 && v.length >= 4 && u8(v, 1) <= 2) {
                boolean ok = u8(v, 2) == 1;
                if (ok) faceIdx = u8(v, 3);
                if (u8(v, 1) == 1) lastWrite = ok ? "Zegarek przyjął: tarcza " + (faceIdx + 1) : "Zegarek odrzucił zmianę tarczy";
                notifyUi();
                return "tarcza: " + (u8(v, 3) + 1) + (ok ? "" : " (odrzucone)");
            }
            if (op == 0xB5 && v.length >= 3) {
                return "znajdź zegarek: " + hex(new byte[]{v[1], v[2]});
            }
            if (op == 0x96 && v.length >= 3 && u8(v, 1) == 1) {
                sBat = "poziom " + u8(v, 2);
                notifyUi();
                return "bateria zegarka: " + sBat;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private String decodeHist(byte[] v) {
        if (v.length < 6) return null;
        int cnt = u8(v, 1);
        int part = u8(v, 3);
        if (part == 2) {
            // druga część wpisu: temperatura (C3 04: skóra, ciało w 0,1 °C)
            String[] wk = histWhen.get(cnt);
            String when = wk == null ? null : wk[2];
            for (int i = 4; i + 5 < v.length; i++) {
                if (u8(v, i) == 0xC3 && u8(v, i + 1) == 4) {
                    int body = u8(v, i + 4) | (u8(v, i + 5) << 8);
                    if (body > 200 && body < 450) {
                        if (wk != null) {
                            try {
                                org.json.JSONObject f = new org.json.JSONObject();
                                f.put("temp", body / 10.0);
                                HistStore.putRecord(this, wk[0], wk[1], f);
                            } catch (Exception ignored) { }
                            if (fresh(wk[0], wk[1]) && body / 10.0 >= th(this, "tempHi"))
                                alarm("temp", String.format(Locale.US, "Wysoka temperatura: %.1f °C (o %s)", body / 10.0, wk[1]), false);
                        }
                        sTemp = String.format(Locale.US, "%.1f °C", body / 10.0) + (when != null ? " (" + when + ")" : "");
                        notifyUi();
                        return "temperatura ciała " + (body / 10.0) + " °C" + (when != null ? " – " + when : "");
                    }
                    break;
                }
            }
            return null;
        }
        if (part != 1) return null;
        String when = null, bp = null, spo2 = null, glu = null, resp = null;
        String dKey = null, tKey = null;
        StringBuilder hr = new StringBuilder();
        int lastHr = 0, hrSum = 0, hrN = 0, sys = 0, dia = 0, spo2v = 0, respV = 0, steps5 = -1;
        double gluV = 0;
        int i = 4;
        while (i + 1 < v.length) {
            int tag = u8(v, i), len = u8(v, i + 1);
            if (tag < 0xB0 || tag > 0xCF || i + 2 + len > v.length) break;
            int d = i + 2;
            if (tag == 0xB1 && len >= 4) {
                when = String.format(Locale.US, "%02d.%02d %02d:%02d", u8(v, d + 1), u8(v, d), u8(v, d + 2), u8(v, d + 3));
                dKey = dayKey(u8(v, d), u8(v, d + 1));
                tKey = String.format(Locale.US, "%02d:%02d", u8(v, d + 2), u8(v, d + 3));
            } else if (tag == 0xB2 && len >= 2) {
                steps5 = (u8(v, d) << 8) | u8(v, d + 1);
            } else if (tag == 0xB4) {
                for (int k = 0; k < len; k++) {
                    int x = u8(v, d + k);
                    if (hr.length() > 0) hr.append(",");
                    hr.append(x);
                    if (x > 0 && x < 255) { lastHr = x; hrSum += x; hrN++; }
                }
            } else if (tag == 0xB8 && len >= 2 && u8(v, d) > 0) {
                bp = u8(v, d) + "/" + u8(v, d + 1);
                sys = u8(v, d);
                dia = u8(v, d + 1);
            } else if (tag == 0xB9 && len >= 5) {
                int last = 0;
                for (int k = 0; k < 5; k++) { int x = u8(v, d + k); if (x >= 70 && x <= 100) last = x; }
                if (last > 0) { spo2 = last + "%"; spo2v = last; }
            } else if (tag == 0xBE && len >= 2) {
                int g = u8(v, d) | (u8(v, d + 1) << 8);
                if (g > 0 && g < 3000) { glu = String.format(Locale.US, "%.2f mmol/L", g / 100.0); gluV = g / 100.0; }
            } else if (tag == 0xB6 && len >= 1) {
                int last = 0;
                for (int k = 0; k < len; k++) { int x = u8(v, d + k); if (x > 0 && x < 60) last = x; }
                if (last > 0) { resp = last + "/min"; respV = last; }
            }
            i += 2 + len;
        }
        if (when == null) return null;
        histWhen.put(cnt, new String[]{dKey, tKey, when});
        try {
            org.json.JSONObject f = new org.json.JSONObject();
            if (hrN > 0) f.put("hr", Math.round(hrSum / (float) hrN));
            if (steps5 >= 0 && steps5 < 5000) f.put("st", steps5);
            if (sys > 0) { f.put("sys", sys); f.put("dia", dia); }
            if (spo2v > 0) f.put("spo2", spo2v);
            if (gluV > 0) f.put("glu", gluV);
            if (respV > 0) f.put("resp", respV);
            if (f.length() > 0) HistStore.putRecord(this, dKey, tKey, f);
        } catch (Exception ignored) { }
        if (fresh(dKey, tKey)) checkRecord(tKey, lastHr, sys, dia, spo2v, gluV);
        StringBuilder out = new StringBuilder(when).append("  tętno: ").append(hr.length() == 0 ? "brak" : hr);
        String tag = " (" + when + ")";
        if (bp != null) { out.append("  ciśnienie: ").append(bp); sBp = bp + tag; }
        if (spo2 != null) { out.append("  SpO2: ").append(spo2); sSpo2 = spo2 + tag; }
        if (glu != null) { out.append("  glukoza: ").append(glu); sGlu = glu + tag; }
        if (resp != null) { out.append("  oddech: ").append(resp); sResp = resp + tag; }
        sHist = when;
        if (lastHr > 0) sHr = lastHr + tag;
        notifyUi();
        return "historia " + out;
    }

    /** Wysyła komendę do głównego kanału Veepoo (f0080003), dopełnioną zerami do 20 bajtów. */
    public boolean sendVeepoo(byte[] d) {
        BluetoothGattCharacteristic target = null;
        for (BluetoothGattCharacteristic c : writable) {
            if (c.getUuid().toString().startsWith("f0080003")) { target = c; break; }
        }
        if (target == null) return false;
        byte[] p = new byte[Math.max(20, d.length)];
        System.arraycopy(d, 0, p, 0, d.length);
        write(target, p);
        return true;
    }

    private void handleServices(BluetoothGatt g, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            log("Błąd odczytu usług: " + status);
            return;
        }
        writable.clear();
        readable.clear();
        List<BluetoothGattCharacteristic> notif = new ArrayList<>();
        StringBuilder sb = new StringBuilder("USŁUGI ZEGARKA:");
        for (BluetoothGattService s : g.getServices()) {
            sb.append("\n■ ").append(s.getUuid());
            for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                int p = c.getProperties();
                sb.append("\n   • ").append(c.getUuid()).append("  [").append(props(p)).append("]");
                if ((p & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) writable.add(c);
                if ((p & BluetoothGattCharacteristic.PROPERTY_READ) != 0) readable.add(c);
                if ((p & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) notif.add(c);
            }
        }
        log(sb.toString());
        setState("Połączony");
        for (BluetoothGattCharacteristic c : notif) enqueue(() -> enableNotify(c));
        main.postDelayed(() -> {
            if (gatt != g) return;
            log("Automatycznie: hasło 0000, dane dnia, bateria, historia dnia");
            sendVeepoo(new byte[]{(byte) 0xA1, 0, 0, 0, 0});
            sendVeepoo(new byte[]{(byte) 0xD8, 0});
            sendVeepoo(new byte[]{(byte) 0x96, 1});
            sendVeepoo(new byte[]{(byte) 0xDF, 0});
            sendVeepoo(new byte[]{(byte) 0xB4, 0x02});
            sendVeepoo(new byte[]{(byte) 0xC7, 0x02});
        }, 1500);
        main.removeCallbacks(poll);
        main.postDelayed(poll, 5 * 60 * 1000);
    }

    private int pollCount = 0;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (gatt == null) return;
            sendVeepoo(new byte[]{(byte) 0xD8, 0});
            if (++pollCount % 3 == 0) sendVeepoo(new byte[]{(byte) 0xDF, 0});
            main.postDelayed(this, 5 * 60 * 1000);
        }
    };

    private void enableNotify(BluetoothGattCharacteristic c) {
        BluetoothGatt g = gatt;
        if (g == null) { main.post(this::next); return; }
        g.setCharacteristicNotification(c, true);
        BluetoothGattDescriptor d = c.getDescriptor(CCCD);
        if (d == null) {
            log("Nasłuch " + shortUuid(c.getUuid()) + " (bez deskryptora)");
            main.post(this::next);
            return;
        }
        boolean ind = (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0;
        d.setValue(ind ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (!g.writeDescriptor(d)) {
            log("Nie udało się włączyć nasłuchu " + shortUuid(c.getUuid()));
            main.post(this::next);
        }
    }

    // ---------------- kolejka operacji (BLE robi jedną rzecz naraz) ----------------

    private void enqueue(Runnable op) {
        queue.add(op);
        if (!busy) next();
    }

    private void next() {
        busy = false;
        Runnable op = queue.poll();
        if (op == null) return;
        busy = true;
        final int my = ++opId;
        op.run();
        main.postDelayed(() -> {
            if (busy && opId == my) {
                log("(brak odpowiedzi – dalej)");
                next();
            }
        }, 4000);
    }

    // ---------------- operacje wywoływane z ekranu ----------------

    public void write(BluetoothGattCharacteristic c, byte[] data) {
        main.post(() -> enqueue(() -> {
            BluetoothGatt g = gatt;
            if (g == null) { log("Brak połączenia"); main.post(this::next); return; }
            int p = c.getProperties();
            int type = (p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                    ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
            c.setWriteType(type);
            c.setValue(data);
            log("→ " + shortUuid(c.getUuid()) + "  " + hex(data));
            if (!g.writeCharacteristic(c)) {
                log("Zapis nie wystartował");
                main.post(this::next);
            }
        }));
    }

    public void readAll() {
        main.post(() -> {
            if (gatt == null) { log("Brak połączenia"); return; }
            log("Czytam wszystkie charakterystyki do odczytu (" + readable.size() + ")");
            for (BluetoothGattCharacteristic c : new ArrayList<>(readable)) {
                enqueue(() -> {
                    BluetoothGatt g = gatt;
                    if (g == null || !g.readCharacteristic(c)) main.post(this::next);
                });
            }
        });
    }

    private void setState(String s) {
        state = s;
        try {
            getSystemService(NotificationManager.class).notify(1, buildNotif());
        } catch (Exception ignored) { }
        Listener l = listener;
        if (l != null) main.post(l::onStateChanged);
    }

    // ---------------- dziennik ----------------

    private void log(String m) {
        appendLog(this, m);
    }

    public static void appendLog(Context c, String msg) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date()) + "  " + msg;
        synchronized (LOCK) {
            try (FileOutputStream fo = new FileOutputStream(logFile(c), true)) {
                fo.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
        }
        Listener l = listener;
        if (l != null) new Handler(Looper.getMainLooper()).post(() -> l.onLog(line));
    }

    public static File logFile(Context c) {
        return new File(c.getFilesDir(), "log.txt");
    }

    public static String readLog(Context c) {
        synchronized (LOCK) {
            File f = logFile(c);
            if (!f.exists()) return "";
            byte[] b = new byte[(int) f.length()];
            try (FileInputStream fi = new FileInputStream(f)) {
                int off = 0;
                while (off < b.length) {
                    int r = fi.read(b, off, b.length - off);
                    if (r < 0) break;
                    off += r;
                }
            } catch (Exception e) {
                return "";
            }
            return new String(b, StandardCharsets.UTF_8);
        }
    }

    public static void clearLog(Context c) {
        synchronized (LOCK) {
            //noinspection ResultOfMethodCallIgnored
            logFile(c).delete();
        }
    }

    // ---------------- pomocnicze ----------------

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format(Locale.US, "%02X ", x));
        return sb.toString().trim();
    }

    private static String ascii(byte[] b) {
        if (b.length < 3) return "";
        for (byte x : b) if (x < 32 || x > 126) return "";
        return "   \"" + new String(b, StandardCharsets.US_ASCII) + "\"";
    }

    public static String shortUuid(UUID u) {
        String s = u.toString();
        if (s.endsWith("-0000-1000-8000-00805f9b34fb") && s.startsWith("0000")) return s.substring(4, 8).toUpperCase(Locale.US);
        return s.substring(0, 8);
    }

    private static String props(int p) {
        StringBuilder sb = new StringBuilder();
        if ((p & BluetoothGattCharacteristic.PROPERTY_READ) != 0) sb.append("odczyt ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) sb.append("zapis ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) sb.append("zapis-bez-odp ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) sb.append("powiadom ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) sb.append("indykacja ");
        return sb.toString().trim();
    }
}
