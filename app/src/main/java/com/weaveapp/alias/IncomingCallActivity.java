package com.weaveapp.alias;

/* =====================================================================
   부름 — 화면 가득 "전화가 왔습니다"
   2026-10-04 신설 (버전 14) — 에일리어스콜 버전 10 과 같은 화면

   ⚠ 이 파일의 자리
       C:\alias-app\android\app\src\main\java\com\weaveapp\alias\IncomingCallActivity.java

   무엇을 하나
     잠긴 폰이면 화면이 켜지면서 곧바로, 쓰는 중이면 위 배너를 누르면 — 일반 전화처럼
     화면 가득 전화 오는 모습을 보여줍니다.
         📞  주차장 관리인
             내 차 · 음성통화
         [ 거절 ]        [ 받기 ]
     [받기] → 벨 끄고 통화 화면(alias_call.html — CallMessagingService 가 준 주소)으로
     [거절] → 벨 끄고 닫음
     발견자가 끊거나(CallMessagingService 가 missed 를 받으면) 45초가 지나면 저절로 닫힘

   🔴 웹 화면이 아니라 자바로 그립니다 — 웹을 읽는 동안 기다리지 않고 벨과 동시에 뜹니다.
   ⚠ 안드로이드 규칙상, 폰을 쓰는 중에는 앱이 화면을 가로챌 수 없습니다(일반 전화 앱도 배너).
     그때는 배너를 누르면 이 화면이 열립니다.
   ===================================================================== */

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Locale;

public class IncomingCallActivity extends Activity {

    /* 지금 떠 있는 전화 화면 — CallMessagingService 가 "끝난 전화" 를 받으면 닫습니다 */
    private static WeakReference<IncomingCallActivity> current = new WeakReference<>(null);
    private static final long RING_MAX_MS = 45000L;

    private String callId = "";
    private String callUrl = "";
    private final Handler main = new Handler(Looper.getMainLooper());

    /** 이어폰 · 차로 받았을 때 — "끝난 전화" 로 적지 않고 화면만 닫습니다(통화 화면은 MainActivity 가 엶) */
    static void dismissIfShowing(final String id) {
        final IncomingCallActivity a = current.get();
        if (a == null || id == null) return;
        a.runOnUiThread(() -> {
            if (!id.equals(a.callId)) return;
            current = new WeakReference<>(null);
            a.main.removeCallbacksAndMessages(null);
            a.finish();
        });
    }

    /** 발견자가 끊었을 때 — 그 전화의 화면이면 닫습니다 */
    static void closeIfShowing(final String gone) {
        final IncomingCallActivity a = current.get();
        if (a == null || gone == null) return;
        a.runOnUiThread(() -> { if (gone.equals(a.callId)) a.finishQuietly(); });
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        show(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        show(intent);
    }

    private void show(Intent it) {
        callId = str(it.getStringExtra("ac_call_id"));
        callUrl = str(it.getStringExtra("ac_call_url"));
        String who = str(it.getStringExtra("ac_who"));
        String type = str(it.getStringExtra("ac_call_type"));
        boolean en = Locale.getDefault().getLanguage().startsWith("en");
        if (callId.isEmpty() || CallMessagingService.isDone(getApplicationContext(), callId)) { finishQuietly(); return; }
        current = new WeakReference<>(this);

        /* ── 화면 그리기 (짙은 바탕 · 큰 글씨 · 큰 둥근 단추) ── */
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.parseColor("#0E1621"));
        root.setPadding(dp(24), dp(90), dp(24), dp(60));

        TextView icon = text("video".equals(type) ? "🎥" : "📞", 64, false);
        TextView title = text(who.isEmpty() ? (en ? "Incoming call" : "전화가 왔습니다") : who.replaceFirst("^📞\\s*", ""), 26, true);
        TextView sub = text("video".equals(type) ? (en ? "WAFA video call" : "와파 영상통화")
                                                 : (en ? "WAFA voice call" : "와파 음성통화"), 16, false);
        sub.setAlpha(0.8f);
        TextView note = text(en ? "Your number is never shown to the caller." : "내 전화번호는 상대에게 보이지 않습니다.", 13, false);
        note.setAlpha(0.6f);

        View spacer = new View(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(1, 0, 1f);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        Button decline = round(en ? "Decline" : "거절", "#C1483A");
        Button accept = round(en ? "Answer" : "받기", "#2E9E5B");
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(110), dp(110));
        bp.setMargins(dp(26), 0, dp(26), 0);
        row.addView(decline, bp);
        row.addView(accept, bp);

        root.addView(icon);
        root.addView(title, mt(18));
        root.addView(sub, mt(8));
        root.addView(note, mt(14));
        root.addView(spacer, sp);
        root.addView(row);
        setContentView(root);

        accept.setOnClickListener(v -> answer());
        decline.setOnClickListener(v -> finishQuietly());

        /* 45초가 지나면 저절로 닫힘 (벨 알림의 setTimeoutAfter 와 짝) */
        main.removeCallbacksAndMessages(null);
        main.postDelayed(this::finishQuietly, RING_MAX_MS);
    }

    /* [받기] — 벨을 끄고 받는 화면으로(autoaccept) — 다시 누를 필요 없이 바로 연결 */
    private void answer() {
        stopRing();
        CallConnectionService.answered(callId);   // 🔴 2-1 — 폰(이어폰 · 차)에도 "받았음"
        /* ⚠ 여기서 "끝난 전화" 로 적지 않습니다 — MainActivity 가 받는 화면을 연 뒤 스스로 적습니다.
             미리 적으면 MainActivity 가 "이미 끝난 전화" 로 보고 열지 않습니다. */
        Intent open = new Intent(this, MainActivity.class);
        open.setAction(Intent.ACTION_VIEW);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        /* 부름 MainActivity 가 원래 받던 쪽지 이름 그대로 — 유령 전화 세 겹 방지도 그대로 돕니다 */
        /* 🔴 2026-10-05 — "사람이 직접 받았음" 표시(answered=1). 통화 화면이 [받기] 를 다시 묻지 않고 바로 연결합니다. */
        open.putExtra("alias_call_url", callUrl + (callUrl.contains("answered=1") ? "" : "&answered=1"));
        open.putExtra("alias_call_id", callId);
        startActivity(open);
        current = new WeakReference<>(null);
        finish();
    }

    /* [거절] · 발견자가 끊음 · 45초 — 벨을 끄고 닫습니다 */
    private void finishQuietly() {
        stopRing();
        CallConnectionService.rejected(callId);   // 🔴 2-1 — 폰(이어폰 · 차)에서도 끝냄
        CallMessagingService.markDone(getApplicationContext(), callId);
        current = new WeakReference<>(null);
        main.removeCallbacksAndMessages(null);
        finish();
    }

    private void stopRing() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && !callId.isEmpty()) nm.cancel("call-" + callId, CallMessagingService.CALL_NOTI_ID);
        } catch (Exception e) { /* 그대로 */ }
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (current.get() == this) current = new WeakReference<>(null);
        super.onDestroy();
    }

    /* ── 그리기 도구 ── */
    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
    private Button round(String s, String color) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setTextSize(17);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.parseColor(color));
        b.setBackground(g);
        return b;
    }
    private LinearLayout.LayoutParams mt(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        return p;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private static String str(String v) { return v == null ? "" : v; }
}
