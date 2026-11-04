package pl.kuba.zegarektest;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Kopia zapasowa całej historii i ustawień do jednego pliku ZIP (folder Pobrane). */
public class Backup {

    static final String[] PREFS = {"cele", "alarmy", "przyp", "ust", "powiad", "cisza", "pogoda"};

    static JSONObject prefsJson(Context c) throws Exception {
        JSONObject all = new JSONObject();
        for (String n : PREFS) {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, ?> e : c.getSharedPreferences(n, Context.MODE_PRIVATE).getAll().entrySet()) {
                Object v = e.getValue();
                JSONArray t = new JSONArray();
                if (v instanceof Boolean) t.put("b"); else if (v instanceof Integer) t.put("i");
                else if (v instanceof Long) t.put("l"); else if (v instanceof Float) t.put("f"); else if (v instanceof String) t.put("s");
                else continue;
                t.put(String.valueOf(v));
                o.put(e.getKey(), t);
            }
            all.put(n, o);
        }
        return all;
    }

    public static void export(Activity a) {
        try {
            HistStore.flush();
            String name = "zegarek_kopia_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".zip";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, name);
            v.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
            Uri uri = a.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("brak miejsca");
            int n = 0;
            try (OutputStream os = a.getContentResolver().openOutputStream(uri); ZipOutputStream z = new ZipOutputStream(os)) {
                File[] files = HistStore.folder(a).listFiles();
                if (files != null) for (File f : files) {
                    z.putNextEntry(new ZipEntry("hist/" + f.getName()));
                    try (FileInputStream in = new FileInputStream(f)) {
                        byte[] buf = new byte[8192];
                        int k;
                        while ((k = in.read(buf)) > 0) z.write(buf, 0, k);
                    }
                    z.closeEntry();
                    n++;
                }
                z.putNextEntry(new ZipEntry("ustawienia.json"));
                z.write(prefsJson(a).toString(1).getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
            Toast.makeText(a, "✅ Kopia zapisana w Pobranych: " + name + " (dni: " + n + ")", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(a, "❌ Kopia: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void restore(Activity a, Uri uri) {
        int n = 0;
        try (InputStream is = a.getContentResolver().openInputStream(uri); ZipInputStream z = new ZipInputStream(is)) {
            HistStore.clearCache();
            File dir = HistStore.folder(a);
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String nm = e.getName();
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int k;
                while ((k = z.read(buf)) > 0) b.write(buf, 0, k);
                if (nm.startsWith("hist/") && nm.endsWith(".json") && !nm.contains("..")) {
                    File f = new File(dir, nm.substring(5));
                    // nie nadpisuj nowszych danych pustymi – połącz rekordy
                    JSONObject in = new JSONObject(b.toString("UTF-8"));
                    if (f.exists()) {
                        JSONObject cur = new JSONObject(new String(read(f), StandardCharsets.UTF_8));
                        merge(cur, in);
                        in = cur;
                    }
                    try (FileOutputStream out = new FileOutputStream(f)) { out.write(in.toString().getBytes(StandardCharsets.UTF_8)); }
                    n++;
                } else if (nm.equals("ustawienia.json")) {
                    JSONObject all = new JSONObject(b.toString("UTF-8"));
                    for (String p : PREFS) {
                        JSONObject o = all.optJSONObject(p);
                        if (o == null) continue;
                        SharedPreferences.Editor ed = a.getSharedPreferences(p, Context.MODE_PRIVATE).edit();
                        Iterator<String> it = o.keys();
                        while (it.hasNext()) {
                            String key = it.next();
                            JSONArray t = o.getJSONArray(key);
                            String ty = t.getString(0), val = t.getString(1);
                            switch (ty) {
                                case "b": ed.putBoolean(key, Boolean.parseBoolean(val)); break;
                                case "i": ed.putInt(key, Integer.parseInt(val)); break;
                                case "l": ed.putLong(key, Long.parseLong(val)); break;
                                case "f": ed.putFloat(key, Float.parseFloat(val)); break;
                                default: ed.putString(key, val);
                            }
                        }
                        ed.apply();
                    }
                }
            }
            HistStore.clearCache();
            Toast.makeText(a, "✅ Przywrócono kopię (dni: " + n + ")", Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(a, "❌ Przywracanie: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static byte[] read(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int k;
            while ((k = in.read(buf)) > 0) b.write(buf, 0, k);
            return b.toByteArray();
        }
    }

    /** Dopisuje do cur to, czego w nim brakuje (rekordy godzinowe, sen, treningi, podsumowanie). */
    private static void merge(JSONObject cur, JSONObject in) throws Exception {
        Iterator<String> it = in.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (k.equals("r") && cur.optJSONObject("r") != null && in.optJSONObject("r") != null) {
                JSONObject cr = cur.getJSONObject("r"), ir = in.getJSONObject("r");
                Iterator<String> t = ir.keys();
                while (t.hasNext()) { String tk = t.next(); if (!cr.has(tk)) cr.put(tk, ir.get(tk)); }
            } else if (!cur.has(k)) {
                cur.put(k, in.get(k));
            }
        }
    }
}
