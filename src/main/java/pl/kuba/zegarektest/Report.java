package pl.kuba.zegarektest;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.provider.MediaStore;

import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/** Raport dla lekarza: PDF z podsumowaniem dni + CSV ze wszystkimi pomiarami. */
public class Report {

    static class Day {
        String date;
        long steps;
        int hrN, hrSum, hrMin = 999, hrMax;
        int bpN, sysSum, diaSum, sysMax, diaMax, sysMin = 999;
        int o2N, o2Sum, o2Min = 999;
        double tMax;
        String sleep = "";
    }

    private static String fmt(double v) { return String.format(Locale.US, "%.1f", v); }

    static List<Day> collect(Activity a, int days, StringBuilder csv) {
        List<Day> out = new ArrayList<>();
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -(days - 1));
        SimpleDateFormat key = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        csv.append("data;godzina;kroki;tetno;skurczowe;rozkurczowe;tlen_proc;temperatura;glukoza_szac;oddech\n");
        for (int i = 0; i < days; i++, c.add(Calendar.DAY_OF_MONTH, 1)) {
            String dk = key.format(c.getTime());
            JSONObject o = HistStore.load(a, dk);
            Day d = new Day();
            d.date = new SimpleDateFormat("dd.MM.yyyy", Locale.US).format(c.getTime());
            JSONObject recs = o.optJSONObject("r");
            long st = 0;
            if (recs != null) {
                TreeMap<String, JSONObject> sorted = new TreeMap<>();
                Iterator<String> it = recs.keys();
                while (it.hasNext()) { String k = it.next(); JSONObject r = recs.optJSONObject(k); if (r != null) sorted.put(k, r); }
                for (String k : sorted.keySet()) {
                    JSONObject r = sorted.get(k);
                    st += r.optInt("st", 0);
                    int hr = r.optInt("hr", 0);
                    if (hr > 0) { d.hrN++; d.hrSum += hr; d.hrMin = Math.min(d.hrMin, hr); d.hrMax = Math.max(d.hrMax, hr); }
                    int sys = r.optInt("sys", 0), dia = r.optInt("dia", 0);
                    if (sys > 0) { d.bpN++; d.sysSum += sys; d.diaSum += dia; d.sysMax = Math.max(d.sysMax, sys); d.diaMax = Math.max(d.diaMax, dia); d.sysMin = Math.min(d.sysMin, sys); }
                    int o2 = r.optInt("spo2", 0);
                    if (o2 > 0) { d.o2N++; d.o2Sum += o2; d.o2Min = Math.min(d.o2Min, o2); }
                    double t = r.optDouble("temp", 0);
                    if (t > 0) d.tMax = Math.max(d.tMax, t);
                    csv.append(dk).append(';').append(k).append(';')
                            .append(r.has("st") ? r.optInt("st") : "").append(';')
                            .append(hr > 0 ? hr : "").append(';')
                            .append(sys > 0 ? sys : "").append(';').append(sys > 0 ? dia : "").append(';')
                            .append(o2 > 0 ? o2 : "").append(';')
                            .append(t > 0 ? fmt(t).replace('.', ',') : "").append(';')
                            .append(r.has("glu") ? fmt(r.optDouble("glu")).replace('.', ',') : "").append(';')
                            .append(r.has("resp") ? r.optInt("resp") : "").append('\n');
                }
            }
            d.steps = Math.max(st, o.optLong("steps", 0));
            JSONObject sl = o.optJSONObject("sleep");
            if (sl != null) {
                int m = sl.optInt("min");
                d.sleep = String.format(Locale.US, "%dh%02d (gł. %dh%02d)", m / 60, m % 60, sl.optInt("gleboki") / 60, sl.optInt("gleboki") % 60);
            }
            out.add(d);
        }
        return out;
    }

    static void make(Activity a, int days) {
        StringBuilder csv = new StringBuilder();
        List<Day> list = collect(a, days, csv);
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());

        PdfDocument doc = new PdfDocument();
        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setColor(Color.parseColor("#0B3D91"));
        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        txt.setTextSize(9);
        txt.setColor(Color.BLACK);
        Paint bold = new Paint(txt);
        bold.setTypeface(Typeface.DEFAULT_BOLD);
        Paint grid = new Paint();
        grid.setColor(Color.parseColor("#CBD5E1"));
        Paint warn = new Paint(txt);
        warn.setColor(Color.parseColor("#C62828"));
        warn.setTypeface(Typeface.DEFAULT_BOLD);

        String[] head = {"Data", "Kroki", "Tętno śr (min–max)", "Ciśnienie śr", "Ciśn. max", "Tlen śr / min", "Temp. max", "Sen"};
        float[] x = {28, 92, 140, 238, 310, 370, 445, 492};
        float sysHi = BleService.th(a, "sysHi"), diaHi = BleService.th(a, "diaHi");

        int page = 1, y = 0;
        PdfDocument.Page pg = null;
        Canvas c = null;
        int[] totals = new int[6]; // bpN, sysSum, diaSum, hrN, hrSum, days with data
        for (int i = 0; i <= list.size(); i++) {
            if (pg == null || y > 800) {
                if (pg != null) doc.finishPage(pg);
                pg = doc.startPage(new PdfDocument.PageInfo.Builder(595, 842, page++).create());
                c = pg.getCanvas();
                y = 40;
                if (page == 2) {
                    c.drawText("Raport z zegarka – ostatnie " + days + " dni", 28, y, title);
                    y += 16;
                    c.drawText("Wygenerowano: " + new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(new Date())
                            + "   ·   Zegarek JS300 (pomiary orientacyjne, nie jest to urządzenie medyczne)", 28, y, txt);
                    y += 20;
                }
                for (int k = 0; k < head.length; k++) c.drawText(head[k], x[k], y, bold);
                y += 4;
                c.drawLine(28, y, 567, y, grid);
                y += 12;
            }
            if (i == list.size()) break;
            Day d = list.get(i);
            boolean any = d.steps > 0 || d.hrN > 0 || d.bpN > 0 || !d.sleep.isEmpty();
            if (!any) continue;
            totals[5]++;
            c.drawText(d.date, x[0], y, txt);
            c.drawText(d.steps > 0 ? String.valueOf(d.steps) : "–", x[1], y, txt);
            c.drawText(d.hrN > 0 ? (d.hrSum / d.hrN) + " (" + d.hrMin + "–" + d.hrMax + ")" : "–", x[2], y, txt);
            if (d.bpN > 0) {
                c.drawText((d.sysSum / d.bpN) + "/" + (d.diaSum / d.bpN), x[3], y, txt);
                boolean hi = d.sysMax >= sysHi || d.diaMax >= diaHi;
                c.drawText(d.sysMax + "/" + d.diaMax, x[4], y, hi ? warn : txt);
                totals[0] += d.bpN; totals[1] += d.sysSum; totals[2] += d.diaSum;
            } else {
                c.drawText("–", x[3], y, txt);
                c.drawText("–", x[4], y, txt);
            }
            c.drawText(d.o2N > 0 ? (d.o2Sum / d.o2N) + "% / " + d.o2Min + "%" : "–", x[5], y, txt);
            c.drawText(d.tMax > 0 ? fmt(d.tMax) + " °C" : "–", x[6], y, txt);
            c.drawText(d.sleep.isEmpty() ? "–" : d.sleep, x[7], y, txt);
            if (d.hrN > 0) { totals[3] += d.hrN; totals[4] += d.hrSum; }
            y += 4;
            c.drawLine(28, y, 567, y, grid);
            y += 12;
        }
        y += 10;
        if (totals[0] > 0)
            c.drawText("Średnie ciśnienie z okresu: " + (totals[1] / totals[0]) + "/" + (totals[2] / totals[0]) + " mmHg  (pomiarów: " + totals[0] + ")", 28, y, bold);
        y += 14;
        if (totals[3] > 0) c.drawText("Średnie tętno z okresu: " + (totals[4] / totals[3]) + " ud./min", 28, y, bold);
        y += 14;
        c.drawText("Na czerwono: wartości od progu alarmu " + Math.round(sysHi) + "/" + Math.round(diaHi) + " mmHg (ustawienia w aplikacji).", 28, y, txt);
        doc.finishPage(pg);

        try {
            Uri pdf = save(a, "raport_zegarek_" + stamp + ".pdf", "application/pdf", null, doc);
            doc.close();
            save(a, "pomiary_zegarek_" + stamp + ".csv", "text/csv", ("﻿" + csv).getBytes(StandardCharsets.UTF_8), null);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM, pdf);
            share.putExtra(Intent.EXTRA_SUBJECT, "Raport z zegarka – " + days + " dni");
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(share, "Wyślij raport (PDF i CSV są też w Pobranych)"));
        } catch (Exception e) {
            android.widget.Toast.makeText(a, "Błąd raportu: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private static Uri save(Activity a, String name, String mime, byte[] bytes, PdfDocument doc) throws Exception {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Downloads.DISPLAY_NAME, name);
        v.put(MediaStore.Downloads.MIME_TYPE, mime);
        Uri uri = a.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new Exception("brak miejsca");
        try (OutputStream os = a.getContentResolver().openOutputStream(uri)) {
            if (doc != null) doc.writeTo(os); else os.write(bytes);
        }
        return uri;
    }
}
