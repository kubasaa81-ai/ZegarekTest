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

    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private boolean wantConnected = false;
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private boolean busy = false;
    private int opId = 0;
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
        startFg();
        IntentFilter f = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(btReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(btReceiver, f);
        }
    }

    private Notification buildNotif() {
        Intent open = new Intent(this, MainActivity.class);
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
                    main.postDelayed(() -> { if (gatt == g) g.discoverServices(); }, 600);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    log("ROZŁĄCZONO (status " + status + ")");
                    closeGatt();
                    setState("Rozłączony – czekam na zegarek…");
                    if (wantConnected) main.postDelayed(BleService.this::connect, 2000);
                }
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
            main.post(() -> log("← " + u + "  " + hex(val) + ascii(val)));
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
    }

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
