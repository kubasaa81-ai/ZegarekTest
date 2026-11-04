package pl.kuba.zegarektest;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;

/** Przekazuje powiadomienia z telefonu (SMS, komunikatory, inne) na ekran zegarka. */
public class NotifService extends NotificationListenerService {

    private static final HashMap<String, Integer> TYPES = new HashMap<>();
    static {
        TYPES.put("com.samsung.android.messaging", 0x01);
        TYPES.put("com.google.android.apps.messaging", 0x01);
        TYPES.put("com.android.mms", 0x01);
        TYPES.put("com.whatsapp", 0x09);
        TYPES.put("com.whatsapp.w4b", 0x09);
        TYPES.put("com.facebook.orca", 0x17);
        TYPES.put("com.facebook.katana", 0x05);
        TYPES.put("com.instagram.android", 0x0B);
        TYPES.put("org.telegram.messenger", 0x13);
        TYPES.put("com.viber.voip", 0x19);
        TYPES.put("com.google.android.gm", 0x0E);
        TYPES.put("com.skype.raider", 0x0D);
        TYPES.put("com.snapchat.android", 0x0C);
        TYPES.put("com.twitter.android", 0x06);
        TYPES.put("com.zhiliaoapp.musically", 0x12);
        TYPES.put("com.linkedin.android", 0x08);
    }

    private static final String[] SKIP = {
            "com.samsung.android.incallui", "com.android.incallui", "com.google.android.dialer",
            "com.samsung.android.dialer", "com.android.server.telecom", "com.android.systemui",
            "android", "com.veepoo.hband", "com.android.vending"
    };

    private final HashMap<String, Long> recent = new HashMap<>();

    public static boolean enabled(android.content.Context c) {
        String s = android.provider.Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(c.getPackageName());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (!getSharedPreferences("powiad", MODE_PRIVATE).getBoolean("aplikacje", true)) return;
            String pkg = sbn.getPackageName();
            if (pkg.equals(getPackageName())) return;
            for (String s : SKIP) if (s.equals(pkg)) return;
            Notification n = sbn.getNotification();
            if (sbn.isOngoing() || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            if (Notification.CATEGORY_CALL.equals(n.category) || Notification.CATEGORY_TRANSPORT.equals(n.category)
                    || Notification.CATEGORY_PROGRESS.equals(n.category) || Notification.CATEGORY_SERVICE.equals(n.category)) return;
            Bundle ex = n.extras;
            CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (text == null) text = ex.getCharSequence(Notification.EXTRA_TEXT);
            String t = title == null ? "" : title.toString().trim();
            String c = text == null ? "" : text.toString().trim();
            if (t.isEmpty() && c.isEmpty()) return;
            String key = pkg + "|" + t + "|" + c;
            long now = System.currentTimeMillis();
            Long last = recent.get(key);
            if (last != null && now - last < 15000) return;
            recent.put(key, now);
            if (recent.size() > 50) recent.clear();

            Integer type = TYPES.get(pkg);
            if (type == null) {
                type = 0x11;
                String app = label(pkg);
                if (!app.isEmpty()) t = app + (t.isEmpty() ? "" : ": " + t);
            }
            BleService s = BleService.instance;
            if (s != null) s.sendMessage(type, t, c);
        } catch (Exception ignored) { }
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return "";
        }
    }
}
