package com.weaveapp.alias;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/* =====================================================================
   🔴 2026-11-21 와파 「진동만」 — 폰이 무음이어도 진동
     알림 채널의 진동은 폰이 무음이면 안드로이드가 막습니다.
     그래서 「진동만」 일 때는 와파가 직접(알람과 같은 종류로) 진동을 울립니다.
     받기 · 거절 · 끊김 · 알림 지움 때 stop(), 그래도 45초가 지나면 저절로 멈춤.
   ===================================================================== */
final class WafaVibe {
    private static Vibrator vib;
    private static String cur;
    private static final Handler H = new Handler(Looper.getMainLooper());

    private WafaVibe() {}

    static synchronized void start(Context cx, String callId) {
        stop(null);
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = cx.getSystemService(VibratorManager.class);
                v = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                v = (Vibrator) cx.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v == null || !v.hasVibrator()) return;
            long[] p = {0, 800, 500, 800, 1500};          // 징— 징— (쉬고) 되풀이
            if (Build.VERSION.SDK_INT >= 33) {
                v.vibrate(VibrationEffect.createWaveform(p, 0),
                          VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
            } else if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(p, 0),
                          new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                                  .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            } else {
                v.vibrate(p, 0);
            }
            vib = v;
            cur = callId;
            H.removeCallbacksAndMessages(null);
            H.postDelayed(() -> stop(null), 45000);
        } catch (Throwable e) {
            android.util.Log.w("Alias", "와파 진동 실패", e);
        }
    }

    /* callId 가 지금 울리는 전화와 다르면 그대로 둠. null 이면 무조건 멈춤 */
    static synchronized void stop(String callId) {
        if (callId != null && cur != null && !callId.equals(cur)) return;
        try { if (vib != null) vib.cancel(); } catch (Throwable e) { /* 그대로 */ }
        vib = null;
        cur = null;
        H.removeCallbacksAndMessages(null);
    }
}
