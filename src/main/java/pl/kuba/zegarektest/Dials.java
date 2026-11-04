package pl.kuba.zegarektest;

import android.os.Handler;
import android.os.Looper;

import com.inuker.bluetooth.library.connect.response.BleNotifyResponse;
import com.inuker.bluetooth.library.jieli.dial.JLWatchFaceManager;
import com.inuker.bluetooth.library.jieli.response.RcspAuthResponse;
import com.jieli.jl_fatfs.model.FatFile;
import com.veepoo.protocol.VPOperateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Lista wszystkich tarcz w zegarku (wbudowane, wgrane, ze zdjęcia) i przełączanie. */
public class Dials {

    public static final List<FatFile> sys = new ArrayList<>();
    public static final List<FatFile> srv = new ArrayList<>();
    public static volatile boolean hasPic = false;
    public static volatile boolean loaded = false;
    public static volatile boolean loading = false;
    /** "s0", "w1", "p" – ostatnio wybrana */
    public static volatile String current = "";

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final List<Runnable> waiting = new ArrayList<>();
    private static boolean step;

    private static void finish(Sdk.Out out) {
        loading = false;
        List<Runnable> w = new ArrayList<>(waiting);
        waiting.clear();
        for (Runnable r : w) main.post(r);
    }

    /** Otwiera kanał tarcz, autoryzuje i czyta listę. then – po zakończeniu (także przy błędzie). */
    public static void load(Sdk.Out out, Runnable then) {
        if (then != null) waiting.add(then);
        if (loading) return;
        if (!Sdk.ready) { finish(out); return; }
        loading = true;
        try {
            VPOperateManager m = VPOperateManager.getInstance();
            if (m.isJLNotifyOpened()) { auth(out); return; }
            m.openJLDataNotify(new BleNotifyResponse() {
                @Override public void onNotify(UUID s, UUID c, byte[] v) { }

                @Override
                public void onResponse(int code) {
                    try { m.changeMTU(247, mtu -> { }); } catch (Throwable ignored) { }
                    main.postDelayed(() -> auth(out), 1500);
                }
            });
        } catch (Throwable t) {
            out.line("Tarcze: " + t);
            finish(out);
        }
    }

    private static void auth(Sdk.Out out) {
        try {
            if (com.inuker.bluetooth.library.jieli.RcspAuthManager.getInstance().isAuthPass()) { list(out); return; }
            step = false;
            VPOperateManager.getInstance().startJLDeviceAuth(new RcspAuthResponse() {
                @Override public void onRcspAuthStart() { }
                @Override public void onRcspAuthSuccess() { if (!step) { step = true; main.post(() -> list(out)); } }
                @Override public void onRcspAuthFailed() { if (!step) { step = true; out.line("Zegarek nie przyjął autoryzacji tarcz"); main.post(() -> finish(out)); } }
            });
            main.postDelayed(() -> { if (!step) { step = true; list(out); } }, 10000);
        } catch (Throwable t) {
            out.line("Tarcze: " + t);
            finish(out);
        }
    }

    private static boolean fs;

    private static void list(Sdk.Out out) {
        fs = false;
        try {
            VPOperateManager.getInstance().listJLWatchList(new JLWatchFaceManager.OnWatchDialInfoGetListener() {
                @Override public void onGettingWatchDialInfo() { }
                @Override public void onWatchDialInfoGetStart() { }
                @Override public void onWatchDialInfoGetComplete() { }

                @Override
                public void onWatchDialInfoGetSuccess(List<FatFile> s, List<FatFile> w, FatFile pic) {
                    if (fs) return;
                    fs = true;
                    main.post(() -> {
                        sys.clear();
                        srv.clear();
                        if (s != null) sys.addAll(s);
                        if (w != null) srv.addAll(w);
                        hasPic = pic != null;
                        loaded = true;
                        out.line("Tarcze w zegarku: wbudowane " + sys.size() + ", wgrane " + srv.size() + (hasPic ? ", ze zdjęcia" : ""));
                        finish(out);
                    });
                }

                @Override
                public void onWatchDialInfoGetFailed(com.jieli.jl_rcsp.model.base.BaseError e) {
                    if (fs) return;
                    fs = true;
                    out.line("Lista tarcz niedostępna: " + e);
                    main.post(() -> finish(out));
                }
            });
            main.postDelayed(() -> { if (!fs) { fs = true; out.line("Brak listy tarcz"); finish(out); } }, 12000);
        } catch (Throwable t) {
            out.line("Tarcze: " + t);
            finish(out);
        }
    }

    /** Przełącza tarczę: kind 's' wbudowana, 'w' wgrana, 'p' ze zdjęcia. */
    public static boolean select(char kind, int idx) {
        try {
            JLWatchFaceManager jm = JLWatchFaceManager.getInstance();
            if (kind == 'p') JLWatchFaceManager.switch2PicDial();
            else if (kind == 'w') {
                FatFile f = srv.get(idx);
                jm.setCurrentServerDialName(f.getName());
                jm.setDial(f.getPath());
            } else jm.setDial(sys.get(idx).getPath());
            current = kind == 'p' ? "p" : String.valueOf(kind) + idx;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Nazwa wgranej tarczy do pokazania (bez ścieżki i rozszerzenia). */
    public static String label(FatFile f, int i) {
        String n = f.getName() == null ? "" : f.getName();
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        n = n.replace("WATCH", "").replace("watch", "").trim();
        return n.isEmpty() || n.length() > 8 ? String.valueOf(i + 1) : n;
    }

    public static void reset() {
        loaded = false;
        loading = false;
        sys.clear();
        srv.clear();
        hasPic = false;
        waiting.clear();
    }
}
