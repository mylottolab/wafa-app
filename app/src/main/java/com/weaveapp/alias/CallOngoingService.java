package com.weaveapp.alias;

/* 와파 — 통화 중 알림줄 (2026-11-05)
   통화가 붙으면 켜고(start), 끝나면 끕니다(stop). MainActivity.callOngoing / callEnded 가 부릅니다.
   알림줄이 떠 있어야 다른 앱을 보거나 화면을 꺼도 마이크 · 카메라를 계속 씁니다.
   ⚠ AndroidManifest 의 foregroundServiceType="microphone|camera" 와 짝입니다. */

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

public class CallOngoingService extends Service {
    private static final String CH = "call_ongoing";
    private static final int ID = 4242;

    public static void start(Context c, String peer, boolean video) {
        try {
            Intent i = new Intent(c, CallOngoingService.class);
            i.putExtra("peer", peer == null ? "" : peer);
            i.putExtra("video", video);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i);
            else c.startService(i);
        } catch (Throwable e) {
            android.util.Log.w("Alias", "통화 중 알림줄을 켜지 못했습니다", e);
        }
    }

    public static void stop(Context c) {
        try { c.stopService(new Intent(c, CallOngoingService.class)); } catch (Throwable e) { /* 그대로 */ }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String peer = intent == null ? "" : intent.getStringExtra("peer");
        boolean video = intent != null && intent.getBooleanExtra("video", false);
        boolean en = !java.util.Locale.getDefault().getLanguage().equals("ko");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CH) == null) {
                NotificationChannel ch = new NotificationChannel(CH, en ? "Call in progress" : "통화 중", NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = en ? (video ? "WAFA video call" : "WAFA call") : (video ? "와파 영상통화 중" : "와파 통화 중");
        String text = (peer == null || peer.isEmpty()) ? (en ? "Tap to return to the call" : "누르면 통화 화면으로 돌아갑니다") : peer;

        Notification n = new NotificationCompat.Builder(this, CH)
                .setSmallIcon(android.R.drawable.sym_call_outgoing)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(pi)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                if (video) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
                startForeground(ID, n, type);
            } else {
                startForeground(ID, n);
            }
        } catch (Throwable e) {
            /* 카메라 권한이 아직 없으면 영상 종류로 켜다 막힐 수 있어, 마이크만으로 한 번 더 */
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                else startForeground(ID, n);
            } catch (Throwable e2) {
                android.util.Log.w("Alias", "통화 중 알림줄 실패", e2);
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        try { stopForeground(true); } catch (Throwable e) { /* 그대로 */ }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
