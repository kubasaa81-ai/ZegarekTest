package pl.kuba.zegarektest;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Zapis historii pomiarów w telefonie: jeden plik JSON na dzień (files/hist/RRRR-MM-DD.json). */
public class HistStore {

    private static final HashMap<String, JSONObject> cache = new HashMap<>();
    private static final Set<String> dirty = new HashSet<>();
    private static final Handler h = new Handler(Looper.getMainLooper());
    private static Context app;

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "hist");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    public static synchronized JSONObject load(Context c, String day) {
        JSONObject o = cache.get(day);
        if (o != null) return o;
        File f = new File(dir(c), day + ".json");
        o = new JSONObject();
        if (f.exists()) {
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] b = new byte[(int) f.length()];
                int off = 0;
                while (off < b.length) {
                    int r = in.read(b, off, b.length - off);
                    if (r < 0) break;
                    off += r;
                }
                o = new JSONObject(new String(b, StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
        }
        cache.put(day, o);
        return o;
    }

    private static synchronized void saveSoon(Context c, String day) {
        app = c.getApplicationContext();
        dirty.add(day);
        h.removeCallbacks(flusher);
        h.postDelayed(flusher, 2000);
    }

    private static final Runnable flusher = HistStore::flush;

    public static synchronized void flush() {
        if (app == null) return;
        for (String day : dirty) {
            JSONObject o = cache.get(day);
            if (o == null) continue;
            try (FileOutputStream out = new FileOutputStream(new File(dir(app), day + ".json"))) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
        }
        dirty.clear();
    }

    /** Dopisuje pola do wpisu z danej godziny (klucz "HH:mm"). */
    public static synchronized void putRecord(Context c, String day, String time, JSONObject fields) {
        try {
            JSONObject o = load(c, day);
            JSONObject recs = o.optJSONObject("r");
            if (recs == null) { recs = new JSONObject(); o.put("r", recs); }
            JSONObject r = recs.optJSONObject(time);
            if (r == null) { r = new JSONObject(); recs.put(time, r); }
            Iterator<String> it = fields.keys();
            while (it.hasNext()) { String k = it.next(); r.put(k, fields.get(k)); }
            saveSoon(c, day);
        } catch (Exception ignored) { }
    }

    /** Sen zapisany pod dniem pobudki. */
    public static synchronized void putSleep(Context c, String day, JSONObject sleep) {
        try {
            JSONObject o = load(c, day);
            o.put("sleep", sleep);
            saveSoon(c, day);
        } catch (Exception ignored) { }
    }

    /** Podsumowanie dnia (kroki, metry, kcal). */
    public static synchronized void putDay(Context c, String day, long steps, long meters, long kcal) {
        try {
            JSONObject o = load(c, day);
            o.put("steps", steps);
            o.put("m", meters);
            o.put("kcal", kcal);
            saveSoon(c, day);
        } catch (Exception ignored) { }
    }
}
