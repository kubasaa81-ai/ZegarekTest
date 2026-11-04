package pl.kuba.zegarektest;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.veepoo.protocol.listener.data.IECGDetectListener;
import com.veepoo.protocol.model.datas.EcgDetectInfo;
import com.veepoo.protocol.model.datas.EcgDetectResult;
import com.veepoo.protocol.model.datas.EcgDetectState;
import com.veepoo.protocol.model.datas.EcgDiagnosis;
import com.veepoo.protocol.model.enums.EBPDetectModel;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Pomiar na żądanie: ciśnienie, tlen, tętno, EKG. */
public class MeasureActivity extends Activity {

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView tvBig, tvInfo, tvLog;
    private ProgressBar pb;
    private Button bStop;
    private int kind = -1; // 0 ciśnienie, 1 tlen, 2 tętno, 3 EKG
    private int lastHr = 0, lastO2 = 0;
    private boolean saved = false;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable grad(String a, String b, int r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Color.parseColor(a), Color.parseColor(b)});
        g.setCornerRadius(dp(r));
        return g;
    }

    private Button btn(String t, String c1, String c2, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(grad(c1, c2, 18));
        b.setPadding(dp(10), dp(14), dp(10), dp(14));
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams lp(float w) {
        LinearLayout.LayoutParams p = w > 0 ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(4), dp(5), dp(4), dp(5));
        return p;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.parseColor("#BE123C"));
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.parseColor("#EEF2F8"));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(30), dp(20), dp(18));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#BE123C"), Color.parseColor("#7C3AED")});
        hg.setCornerRadii(new float[]{0, 0, 0, 0, dp(28), dp(28), dp(28), dp(28)});
        head.setBackground(hg);
        TextView back = new TextView(this);
        back.setText("‹  Wróć");
        back.setTextSize(15);
        back.setTextColor(Color.WHITE);
        back.setOnClickListener(v -> finish());
        head.addView(back);
        TextView title = new TextView(this);
        title.setText("Zmierz teraz 🩺");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        head.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Załóż zegarek ciasno nad nadgarstkiem, siedź spokojnie i nie mów.");
        sub.setTextSize(14);
        sub.setTextColor(Color.parseColor("#FFE4E6"));
        head.addView(sub);
        outer.addView(head);

        ScrollView sv = new ScrollView(this);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(12), dp(10), dp(12), dp(24));
        sv.addView(p);
        outer.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout r1 = new LinearLayout(this);
        r1.addView(btn("🩸 Ciśnienie", "#E11D48", "#BE123C", v -> start(0)), lp(1));
        r1.addView(btn("🫁 Tlen (SpO2)", "#0284C7", "#0369A1", v -> start(1)), lp(1));
        p.addView(r1);
        LinearLayout r2 = new LinearLayout(this);
        r2.addView(btn("❤️ Tętno", "#DB2777", "#9D174D", v -> start(2)), lp(1));
        r2.addView(btn("📈 EKG", "#7C3AED", "#5B21B6", v -> start(3)), lp(1));
        p.addView(r2);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable cg = new GradientDrawable();
        cg.setColor(Color.WHITE);
        cg.setCornerRadius(dp(22));
        card.setBackground(cg);
        tvBig = new TextView(this);
        tvBig.setTextSize(40);
        tvBig.setTypeface(Typeface.DEFAULT_BOLD);
        tvBig.setGravity(Gravity.CENTER);
        tvBig.setTextColor(Color.parseColor("#0F172A"));
        tvBig.setText("–");
        card.addView(tvBig);
        tvInfo = new TextView(this);
        tvInfo.setTextSize(15);
        tvInfo.setGravity(Gravity.CENTER);
        tvInfo.setTextColor(Color.parseColor("#475569"));
        tvInfo.setText("Wybierz pomiar powyżej.");
        card.addView(tvInfo);
        pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setVisibility(View.GONE);
        card.addView(pb, lp(0));
        p.addView(card, lp(0));

        bStop = btn("⏹  Zatrzymaj pomiar", "#475569", "#334155", v -> { stop(); tvInfo.setText("Pomiar zatrzymany."); });
        bStop.setVisibility(View.GONE);
        p.addView(bStop, lp(0));

        tvLog = new TextView(this);
        tvLog.setTextSize(13);
        tvLog.setTextColor(Color.parseColor("#64748B"));
        tvLog.setPadding(dp(8), dp(10), dp(8), 0);
        tvLog.setText("Wynik zapisuje się w historii (kafelki na Pulpicie, raport dla lekarza). "
                + "Pomiar z zegarka jest orientacyjny – nie zastępuje ciśnieniomierza.");
        p.addView(tvLog);

        setContentView(outer);
        BleService.busySdk = true;
    }

    private void info(String s) { main.post(() -> tvInfo.setText(s)); }

    private void big(String s) { main.post(() -> tvBig.setText(s)); }

    private void prog(int v) { main.post(() -> { pb.setVisibility(View.VISIBLE); pb.setProgress(Math.max(0, Math.min(100, v))); }); }

    private final Runnable timeout = () -> {
        if (kind == 2 && lastHr > 0) { save(); tvInfo.setText("✅ Gotowe"); }
        else if (kind == 1 && lastO2 > 0) { save(); tvInfo.setText("✅ Gotowe"); }
        else tvInfo.setText("⏳ Zegarek nie podał wyniku – popraw zegarek na ręce i spróbuj jeszcze raz.");
        stop();
    };

    private void start(int k) {
        if (!Sdk.ready) {
            tvInfo.setText(BleService.useSdk(this)
                    ? "⚠️ Zegarek nie jest teraz połączony – poczekaj chwilę."
                    : "⚠️ Pomiary działają przy połączeniu przez bibliotekę producenta (Urządzenie → Połączenie).");
            return;
        }
        stop();
        kind = k;
        saved = false;
        lastHr = lastO2 = 0;
        tvBig.setText("…");
        pb.setProgress(0);
        pb.setVisibility(View.VISIBLE);
        bStop.setVisibility(View.VISIBLE);
        main.removeCallbacks(timeout);
        try {
            if (k == 0) {
                tvInfo.setText("Mierzę ciśnienie… (ok. 1 min)");
                Sdk.m().startDetectBP(Sdk.wr, d -> {
                    String st = String.valueOf(d.getStatus());
                    if (st.contains("WEAR_OFF")) { info("Zegarek nie przylega do ręki"); return; }
                    if (st.contains("CHARGING")) { info("Zegarek się ładuje – pomiar niemożliwy"); return; }
                    if (st.contains("LOW_BATTERY")) { info("Za słaba bateria zegarka"); return; }
                    prog(d.getProgress());
                    if (d.getProgress() >= 100 && d.getHighPressure() > 0) {
                        lastHr = 0;
                        big(d.getHighPressure() + "/" + d.getLowPressure());
                        main.post(() -> { saveBp(d.getHighPressure(), d.getLowPressure()); tvInfo.setText("✅ mmHg – zapisano"); stop(); });
                    }
                }, EBPDetectModel.DETECT_MODEL_PUBLIC);
                main.postDelayed(timeout, 120000);
            } else if (k == 1) {
                tvInfo.setText("Mierzę tlen we krwi… (ok. 40 s)");
                Sdk.m().startDetectSPO2H(Sdk.wr, d -> {
                    if (d.getValue() > 0) { lastO2 = d.getValue(); big(d.getValue() + "%"); }
                    if (d.getRateValue() > 0) lastHr = d.getRateValue();
                    prog(d.getCheckingProgress());
                    if (d.getCheckingProgress() >= 100 && lastO2 > 0) main.post(() -> { save(); tvInfo.setText("✅ Tlen – zapisano"); stop(); });
                });
                main.postDelayed(timeout, 60000);
            } else if (k == 2) {
                tvInfo.setText("Mierzę tętno… (30 s)");
                final long t0 = System.currentTimeMillis();
                Sdk.m().startDetectHeart(Sdk.wr, d -> {
                    if (d.getData() > 0) { lastHr = d.getData(); big(d.getData() + " ♥"); }
                    prog((int) ((System.currentTimeMillis() - t0) / 300));
                });
                main.postDelayed(timeout, 30000);
            } else {
                tvInfo.setText("EKG: połóż palec drugiej ręki na zegarku i trzymaj (ok. 1 min)");
                Sdk.m().startDetectECG(code -> { }, true, ecg);
                main.postDelayed(timeout, 120000);
            }
        } catch (Throwable t) {
            tvInfo.setText("❌ Zegarek nie obsługuje tego pomiaru (" + t.getClass().getSimpleName() + ")");
            kind = -1;
            bStop.setVisibility(View.GONE);
        }
    }

    private final IECGDetectListener ecg = new IECGDetectListener() {
        @Override public void onEcgDetectInfoChange(EcgDetectInfo i) { }

        @Override
        public void onEcgDetectStateChange(EcgDetectState s) {
            prog(s.getProgress());
            if (s.getHr1() > 0) { lastHr = s.getHr1(); big(s.getHr1() + " ♥"); }
            if (s.getWear() != 0 && s.getProgress() == 0) info("Dotknij zegarka palcem drugiej ręki");
        }

        @Override
        public void onEcgDetectResultChange(EcgDetectResult r) {
            main.post(() -> {
                if (r.getAveHeart() > 0) lastHr = r.getAveHeart();
                tvBig.setText(lastHr > 0 ? lastHr + " ♥" : "–");
                tvInfo.setText(String.format(Locale.US, "✅ EKG: tętno %d, HRV %d, QT %d ms, oddech %d/min",
                        r.getAveHeart(), r.getAveHrv(), r.getAveQT(), r.getAveResRate()));
                save();
                stop();
            });
        }

        @Override public void onEcgDetectDiagnosisChange(EcgDiagnosis d) { }
        @Override public void onEcgADCChange(int[] a, int[] b) { }
    };

    private void stop() {
        main.removeCallbacks(timeout);
        try {
            if (kind == 0) Sdk.m().stopDetectBP(Sdk.wr, EBPDetectModel.DETECT_MODEL_PUBLIC);
            else if (kind == 1) Sdk.m().stopDetectSPO2H(Sdk.wr, d -> { });
            else if (kind == 2) Sdk.m().stopDetectHeart(Sdk.wr);
            else if (kind == 3) Sdk.m().stopDetectECG(code -> { }, true, ecg);
        } catch (Throwable ignored) { }
        kind = -1;
        bStop.setVisibility(View.GONE);
    }

    private String now() { return new SimpleDateFormat("HH:mm", Locale.US).format(new Date()); }

    private void saveBp(int sys, int dia) {
        if (saved) return;
        saved = true;
        try {
            JSONObject f = new JSONObject();
            f.put("sys", sys);
            f.put("dia", dia);
            HistStore.putRecord(this, BleService.today(), now(), f);
            HistStore.flush();
        } catch (Exception ignored) { }
        BleService.sBp = sys + "/" + dia + " mmHg (pomiar " + now() + ")";
        BleService s = BleService.instance;
        if (s != null) { s.checkRecord(now(), 0, sys, dia, 0, 0); s.notifyUi(); }
        tvLog.append("\n• " + now() + "  ciśnienie " + sys + "/" + dia);
    }

    private void save() {
        if (saved) return;
        saved = true;
        try {
            JSONObject f = new JSONObject();
            if (lastHr > 0) f.put("hr", lastHr);
            if (lastO2 > 0) f.put("spo2", lastO2);
            if (f.length() == 0) return;
            HistStore.putRecord(this, BleService.today(), now(), f);
            HistStore.flush();
        } catch (Exception ignored) { }
        if (lastHr > 0) BleService.sHr = lastHr + " (pomiar " + now() + ")";
        if (lastO2 > 0) BleService.sSpo2 = lastO2 + "% (pomiar " + now() + ")";
        BleService s = BleService.instance;
        if (s != null) { s.checkRecord(now(), lastHr, 0, 0, lastO2, 0); s.notifyUi(); }
        tvLog.append("\n• " + now() + (lastHr > 0 ? "  tętno " + lastHr : "") + (lastO2 > 0 ? "  tlen " + lastO2 + "%" : ""));
    }

    @Override
    protected void onDestroy() {
        stop();
        BleService.busySdk = false;
        super.onDestroy();
    }
}
