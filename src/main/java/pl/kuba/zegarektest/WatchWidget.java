package pl.kuba.zegarektest;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Widżet na ekran główny: kroki, tętno, bateria, ostatni sen. */
public class WatchWidget extends AppWidgetProvider {

    private static long last = 0;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        render(c, m, ids);
    }

    /** Odświeżenie z aplikacji (najwyżej co 30 s). */
    public static void update(Context c) {
        long now = System.currentTimeMillis();
        if (now - last < 30000) return;
        last = now;
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, WatchWidget.class));
            if (ids != null && ids.length > 0) render(c, m, ids);
        } catch (Exception ignored) { }
    }

    private static String strip(String s) {
        int i = s.indexOf(" (");
        return i > 0 ? s.substring(0, i) : s;
    }

    private static void render(Context c, AppWidgetManager m, int[] ids) {
        String steps = BleService.sSteps;
        String today = BleService.today();
        if (steps.equals("–")) {
            long st = HistStore.load(c, today).optLong("steps", 0);
            if (st > 0) steps = String.valueOf(st);
        }
        int goal = c.getSharedPreferences("cele", Context.MODE_PRIVATE).getInt("kroki", 10000);
        String pct = "";
        try { pct = "  (" + Long.parseLong(steps) * 100 / Math.max(1, goal) + "%)"; } catch (Exception ignored) { }

        String sleep = "😴 brak danych snu";
        Calendar cal = Calendar.getInstance();
        for (int k = 0; k < 3; k++, cal.add(Calendar.DAY_OF_MONTH, -1)) {
            JSONObject s = HistStore.load(c, new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.getTime())).optJSONObject("sleep");
            if (s == null) continue;
            int all = s.optInt("min");
            sleep = String.format(Locale.US, "😴 %dh %02dmin  (%s–%s)", all / 60, all % 60, s.optString("od"), s.optString("do"));
            break;
        }
        boolean conn = BleService.state.startsWith("Połączony");

        PendingIntent pi = PendingIntent.getActivity(c, 7, new Intent(c, DashActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        for (int id : ids) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
            v.setTextViewText(R.id.w_title, "⌚ Mój zegarek" + (conn ? "  ●" : "  ○"));
            v.setTextViewText(R.id.w_steps, "👣 " + steps + pct);
            v.setTextViewText(R.id.w_line2, "❤️ " + strip(BleService.sHr) + "   🩺 " + strip(BleService.sBp) + "   🔋 " + BleService.sBat);
            v.setTextViewText(R.id.w_line3, sleep);
            v.setOnClickPendingIntent(R.id.w_root, pi);
            m.updateAppWidget(id, v);
        }
    }
}
