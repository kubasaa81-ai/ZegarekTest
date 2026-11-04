package pl.kuba.zegarektest;

import android.content.Context;
import android.content.SharedPreferences;

import com.veepoo.protocol.model.datas.TimeData;
import com.veepoo.protocol.model.datas.weather.WeatherData;
import com.veepoo.protocol.model.datas.weather.WeatherEvery3Hour;
import com.veepoo.protocol.model.datas.weather.WeatherEveryDay;
import com.veepoo.protocol.model.enums.EWeatherType;
import com.veepoo.protocol.model.settings.WeatherStatusSetting;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pogoda z Open-Meteo (bez klucza) wysyłana na zegarek co 3 godziny. */
public class Weather {

    static SharedPreferences sp(Context c) { return c.getSharedPreferences("pogoda", Context.MODE_PRIVATE); }

    public static boolean on(Context c) { return sp(c).getBoolean("on", true); }

    public static String city(Context c) { return sp(c).getString("miasto", "Łódź"); }

    private static volatile boolean running = false;

    private static String get(String u) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(15000);
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            h.disconnect();
        }
    }

    /** Ustawia miasto (szuka współrzędnych). Wywoływać poza wątkiem głównym. */
    public static String setCity(Context c, String name) throws Exception {
        JSONObject o = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=pl&name="
                + URLEncoder.encode(name, "UTF-8")));
        JSONArray r = o.optJSONArray("results");
        if (r == null || r.length() == 0) throw new Exception("Nie znaleziono miasta „" + name + "”");
        JSONObject f = r.getJSONObject(0);
        String n = f.optString("name", name);
        sp(c).edit().putString("miasto", n)
                .putFloat("lat", (float) f.getDouble("latitude"))
                .putFloat("lon", (float) f.getDouble("longitude"))
                .putLong("ost", 0).apply();
        return n;
    }

    /** Kod pogody WMO → kod zegarka Veepoo. */
    static int code(int w) {
        if (w == 0) return 2;
        if (w <= 2) return 8;
        if (w == 3 || w == 45 || w == 48) return 14;
        if (w >= 51 && w <= 57) return 36;
        if (w == 61 || w == 80) return 36;
        if (w == 63 || w == 81 || w == 66) return 44;
        if (w == 65 || w == 82 || w == 67) return 52;
        if (w == 71 || w == 77 || w == 85) return 78;
        if (w == 73 || w == 75 || w == 86) return 90;
        if (w >= 95) return 22;
        return 14;
    }

    public static String describe(int w) {
        if (w == 0) return "słonecznie";
        if (w <= 2) return "małe zachmurzenie";
        if (w == 3) return "pochmurno";
        if (w == 45 || w == 48) return "mgła";
        if (w >= 51 && w <= 67) return "deszcz";
        if (w >= 71 && w <= 77) return "śnieg";
        if (w >= 80 && w <= 82) return "przelotny deszcz";
        if (w >= 85 && w <= 86) return "przelotny śnieg";
        if (w >= 95) return "burza";
        return "";
    }

    private static int f(double c) { return (int) Math.round(c * 9 / 5 + 32); }

    private static TimeData td(String iso) {
        // "2026-11-04T15:00" albo "2026-11-04"
        int y = Integer.parseInt(iso.substring(0, 4)), mo = Integer.parseInt(iso.substring(5, 7)), d = Integer.parseInt(iso.substring(8, 10));
        int h = iso.length() >= 13 ? Integer.parseInt(iso.substring(11, 13)) : 0;
        return new TimeData(y, mo, d, h, 0);
    }

    /** Pobiera pogodę i wysyła na zegarek (co 3 h, albo od razu gdy force). */
    public static void maybeSend(Context ctx, Sdk.Out out, boolean force) {
        final Context c = ctx.getApplicationContext();
        if (!on(c) || !Sdk.ready || running) return;
        if (!force && System.currentTimeMillis() - sp(c).getLong("ost", 0) < 3 * 3600 * 1000L) return;
        running = true;
        new Thread(() -> {
            try {
                float lat = sp(c).getFloat("lat", 51.759f), lon = sp(c).getFloat("lon", 19.456f);
                JSONObject o = new JSONObject(get(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                                + "&hourly=temperature_2m,weather_code,uv_index&daily=weather_code,temperature_2m_max,temperature_2m_min,uv_index_max"
                                + "&current=temperature_2m,weather_code&timezone=auto&forecast_days=3", lat, lon)));
                JSONObject hr = o.getJSONObject("hourly");
                JSONArray ht = hr.getJSONArray("time"), hT = hr.getJSONArray("temperature_2m"), hW = hr.getJSONArray("weather_code");
                JSONArray hU = hr.optJSONArray("uv_index");
                List<WeatherEvery3Hour> l3 = new ArrayList<>();
                java.util.Calendar now = java.util.Calendar.getInstance();
                String nowKey = String.format(Locale.US, "%04d-%02d-%02dT%02d", now.get(java.util.Calendar.YEAR),
                        now.get(java.util.Calendar.MONTH) + 1, now.get(java.util.Calendar.DAY_OF_MONTH), now.get(java.util.Calendar.HOUR_OF_DAY));
                int start = 0;
                for (int i = 0; i < ht.length(); i++) if (ht.getString(i).startsWith(nowKey)) { start = i; break; }
                for (int i = start; i < ht.length() && l3.size() < 8; i += 3) {
                    double t = hT.getDouble(i);
                    int uv = hU == null ? 0 : (int) Math.round(hU.optDouble(i, 0));
                    l3.add(new WeatherEvery3Hour(td(ht.getString(i)), f(t), (int) Math.round(t), uv, code(hW.getInt(i)), "2", 10.0));
                }
                JSONObject dl = o.getJSONObject("daily");
                JSONArray dt = dl.getJSONArray("time"), dW = dl.getJSONArray("weather_code"),
                        dMax = dl.getJSONArray("temperature_2m_max"), dMin = dl.getJSONArray("temperature_2m_min");
                JSONArray dU = dl.optJSONArray("uv_index_max");
                List<WeatherEveryDay> ld = new ArrayList<>();
                for (int i = 0; i < dt.length(); i++) {
                    double mx = dMax.getDouble(i), mn = dMin.getDouble(i);
                    int w = code(dW.getInt(i));
                    int uv = dU == null ? 0 : (int) Math.round(dU.optDouble(i, 0));
                    ld.add(new WeatherEveryDay(td(dt.getString(i)), f(mx), f(mn), (int) Math.round(mx), (int) Math.round(mn), uv, w, w, "2", 10.0));
                }
                JSONObject cur = o.optJSONObject("current");
                String desc = cur == null ? "" : String.format(Locale.US, "%.0f °C, %s", cur.optDouble("temperature_2m"), describe(cur.optInt("weather_code")));
                WeatherData wd = new WeatherData(0, city(c), 0, td(nowKey), l3, ld);
                Sdk.m().settingWeatherStatusInfo(Sdk.wr, new WeatherStatusSetting(0, true, EWeatherType.C), d -> { });
                Thread.sleep(800);
                Sdk.m().settingWeatherData(Sdk.wr, wd, d -> out.line("Pogoda: zegarek odpowiedział " + d));
                sp(c).edit().putLong("ost", System.currentTimeMillis()).putString("opis", desc).apply();
                out.line("Pogoda wysłana: " + city(c) + " – " + desc);
            } catch (Throwable t) {
                out.line("Pogoda – błąd: " + t.getMessage());
            } finally {
                running = false;
            }
        }).start();
    }
}
