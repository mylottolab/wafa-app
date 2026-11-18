package com.weaveapp.alias;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.capacitorjs.plugins.pushnotifications.MessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/* =====================================================================
   Alias Next-Gen — 걸려온 전화를 잠금화면에 띄웁니다
   2026-09-10
   2026-09-11  ⚠ CallStyle · 거절단추 시도 → 되돌림 (아래 설명)
   2026-09-11  🔴 취소 푸시가 알림을 새로 만들던 문제 · 끝난 전화 명단
   2026-09-10  🔴 유령 전화 고침 (아래 "알림이 안 지워지던 문제")
   2026-10-05  🔴 버전 16 — 알림의 작은 [받기] 단추 없앰 · 부름을 보는 중이면 곧바로 큰 전화 화면 ·
               전화 채널이 없으면 스스로 만듦 (에일리어스콜 버전 11 · 14 와 같은 것)
   2026-10-04  🔴 화면 가득 "전화가 왔습니다" (IncomingCallActivity) — 버전 14
               잠긴 폰이면 화면이 켜지며 곧바로, 쓰는 중이면 배너를 누르면 — 이름 · [거절] [받기].
               상대가 끊으면(missed) 그 화면도 닫습니다.
   2026-10-03  🔴 받을 때까지 계속 울립니다 (FLAG_INSISTENT) — 버전 13
               전에는 벨소리를 한 번만 틀어 "따르릉 × 3" 만에 멎었습니다.
               이제 알림이 사라질 때까지 반복합니다 — 받기 · 상대가 끊음(missed) · 45초 · 손으로 밀기.
               ⚠ 통화화면이 받거나 거절하거나 끝날 때 AliasNative.cancelCall() 로 알림을
                 지우므로(2026-09-12) 통화 중에는 울리지 않습니다.

   🔴 왜 필요한가
     구글이 대신 만들어주는 알림은 "아이콘 한 줄" 이 전부입니다.
     소리도 한 번만 나고, 화면도 안 켜집니다.
     전화처럼 화면을 통째로 띄우려면 앱이 직접 알림을 만들어야 합니다.

   🔴 어떻게 하는가
     전체화면 알림(setFullScreenIntent)을 씁니다.
     폰이 잠겨 있으면 → 통화화면이 통째로 뜹니다
     폰을 쓰고 있으면 → 위에 배너로 뜹니다 (하던 일을 안 가로챕니다)

   ⚠ Capacitor 의 창구를 물려받습니다. 그래야 기기 번호 등록 같은
     원래 기능이 그대로 돕니다. 우리는 "전화" 만 가로채고
     나머지는 부모에게 넘깁니다.

   ⚠ Edge Function 이 notification 없이 data 만 보내야 여기가 불립니다.
     notification 을 함께 보내면 앱이 잠들어 있을 때 구글이 알림을
     직접 만들어버려서 이 코드가 아예 안 돕니다.

   🔴🔴 2026-09-10 — 알림이 안 지워지던 문제 (유령 전화의 원인)
     예전 코드는 알림을 지우는 곳이 "missed 푸시가 올 때" 하나뿐이었습니다.
     그런데 정상적으로 받고 끊으면 missed 는 오지 않습니다.
     게다가 setOngoing(true) 는 "손으로 못 지우는 알림" 이라는 뜻이라
     setAutoCancel(true) 를 무력화시켰고, 받기 단추로 받을 때는
     setAutoCancel 이 아예 안 돕니다.
     결과 — 통화가 끝나도 잠금화면에 "전화가 왔습니다" 가 남고,
     그걸 다시 누르면 끝난 통화가 되살아났습니다.

     고친 것 세 가지
       ① setOngoing 을 뺐습니다 (손으로도 지울 수 있게)
       ② setTimeoutAfter 로 45초 뒤 스스로 사라지게 했습니다
          (화면 쪽 RING_MAX_MS 45초와 짝을 맞췄습니다)
       ③ 통화 번호를 쪽지에 같이 담습니다 — MainActivity 가 이걸로
          알림을 정확히 찾아 지웁니다
   ===================================================================== */
public class CallMessagingService extends MessagingService {

    /* MainActivity 의 CALL_NOTI_ID 와 똑같아야 합니다. */
    static final int CALL_NOTI_ID = 1001;   // IncomingCallActivity 도 씁니다

    /* 화면 쪽 alias_call.html 의 RING_MAX_MS 와 짝입니다. */
    private static final long RING_MAX_MS = 45000L;

    /* =================================================================
       🔴🔴 2026-09-11 신설 — "끝난 전화" 명단

       왜 필요한가
         전화를 연속으로 걸면, 앞 통화의 푸시가 뒤늦게 한 번 더 도착해
         이미 끝난 전화의 벨이 다시 울렸습니다.
         (FCM 은 늦더라도 반드시 배달하려고 몇 번 다시 시도합니다)

       그래서 한 번 열었거나 취소된 통화 번호를 폰에 적어둡니다.
       앱이 죽었다 살아나도 남습니다. 스무 개까지 기억합니다.

       ⚠ MainActivity 도 이 명단을 씁니다. 같은 앱 안이라 그대로 통합니다.
       ================================================================= */
    private static final String DONE_KEY = "done_calls";
    private static final int DONE_MAX = 20;

    static boolean isDone(android.content.Context ctx, String callId) {
        if (ctx == null || callId == null || callId.isEmpty()) return false;
        try {
            String raw = ctx.getSharedPreferences("alias", android.content.Context.MODE_PRIVATE)
                    .getString(DONE_KEY, "");
            return ("," + raw + ",").contains("," + callId + ",");
        } catch (Exception e) { return false; }
    }

    static void markDone(android.content.Context ctx, String callId) {
        if (ctx == null || callId == null || callId.isEmpty()) return;
        try {
            android.content.SharedPreferences sp =
                    ctx.getSharedPreferences("alias", android.content.Context.MODE_PRIVATE);
            String raw = sp.getString(DONE_KEY, "");
            if (("," + raw + ",").contains("," + callId + ",")) return;

            java.util.ArrayList<String> list = new java.util.ArrayList<String>();
            if (!raw.isEmpty()) {
                for (String one : raw.split(",")) {
                    if (!one.isEmpty()) list.add(one);
                }
            }
            list.add(callId);
            while (list.size() > DONE_MAX) list.remove(0);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(list.get(i));
            }
            sp.edit().putString(DONE_KEY, sb.toString()).apply();
        } catch (Exception e) {
            android.util.Log.w("Alias", "끝난 전화 적기 실패", e);
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        Map<String, String> d = msg.getData();
        String kind = d.get("kind");

        /* 🔴 2026-09-10 — A 가 끊으면 벨을 즉시 멈춥니다.
           알림 채널이 울리는 벨은 화면 쪽 코드로는 못 멈춥니다.
           B 가 통화화면에 들어가지 않았으면 그 코드가 아예 안 돌기 때문입니다.
           그래서 여기서 앞의 전화 알림을 직접 지웁니다. */
        if ("missed".equals(kind)) {
            String gone = str(d.get("call_id"));
            NotificationManager nmx = getSystemService(NotificationManager.class);
            if (nmx != null && !gone.isEmpty()) {
                nmx.cancel("call-" + gone, CALL_NOTI_ID);
            }
            /* 🔴 2026-09-11 — 이 전화는 끝났다고 적어둡니다.
               이제 같은 번호의 푸시가 뒤늦게 와도 벨이 안 울립니다. */
            markDone(getApplicationContext(), gone);
            IncomingCallActivity.closeIfShowing(gone);   // 🔴 2026-10-04 — 화면 가득 전화 화면도 닫음
            CallConnectionService.missed(gone);           // 🔴 2-1 — 폰(이어폰 · 차)에서도 끝냄

            /* 🔴🔴 2026-09-11 고침 — super 를 부르지 않습니다.
               전에는 알림을 지운 바로 다음 줄에서 Capacitor 에게 넘겼는데,
               Capacitor 가 그걸로 **알림을 새로 만들었습니다.**
               지운 자리에 도로 하나 놓는 꼴이라, 끊은 뒤에 벨이 한 번
               더 울렸습니다. 취소 신호는 조용히 끝나야 합니다. */
            return;
        }

        // 1:1 전화만 가로챕니다. 나머지는 원래대로 Capacitor 가 처리합니다.
        if (!"call".equals(kind)) {
            super.onMessageReceived(msg);
            return;
        }

        String linkId = str(d.get("link_id"));
        String callId = str(d.get("call_id"));
        String token = str(d.get("session_token"));
        String type = str(d.get("call_type"));
        if (type.isEmpty()) type = "voice";
        String who = str(d.get("who"));
        if (who.isEmpty()) who = "전화가 왔습니다";

        /* 🔴 통화 번호가 없으면 열 수가 없습니다. 억지로 열면 화면이
           "거는 쪽" 으로 잘못 갈라져 엉뚱한 전화가 걸립니다. 그냥 넘깁니다. */
        if (callId.isEmpty() || linkId.isEmpty()) {
            android.util.Log.w("Alias", "전화 푸시에 번호가 없습니다. 무시합니다.");
            super.onMessageReceived(msg);
            return;
        }

        /* 🔴🔴 2026-09-11 — 이미 끝난 전화면 알림을 아예 안 만듭니다.
           FCM 은 늦더라도 배달하려고 몇 번 다시 보냅니다. 연속으로 걸었을 때
           앞 통화의 푸시가 뒤늦게 도착해 벨이 다시 울리던 것이 이것입니다. */
        if (isDone(getApplicationContext(), callId)) {
            android.util.Log.i("Alias", "이미 끝난 전화입니다. 알림을 만들지 않습니다: " + callId);
            NotificationManager nmd = getSystemService(NotificationManager.class);
            if (nmd != null) nmd.cancel("call-" + callId, CALL_NOTI_ID);
            return;
        }

        /* 🔴🔴 2026-09-11 — 오후에 두 가지를 시도했다가 둘 다 되돌렸습니다.

           ① CallStyle 알림 (잠금화면에 초록·빨강 큰 단추)
              targetSdkVersion 이 36 이라, 안드로이드 14+ 규칙상 CallStyle 은
              "전화 종류의 포그라운드 서비스" 를 함께 돌려야 인정됩니다.
              없으면 알림 만들기가 통째로 실패해 **벨이 아예 안 울립니다.**
              세 폰 모두 그랬습니다. 3-3 을 다시 하실 분은 인수인계서
              3절의 실패 기록을 꼭 먼저 읽으세요.

           ② 알림에 거절 단추 달기
              그 자체는 문제가 없었지만, 아침에 확실히 되던 자리로 완전히
              돌아가기 위해 같이 뺐습니다. 하나씩 넣고 하나씩 확인하는 게
              오늘 배운 것입니다. 나중에 이것만 따로 넣어 보세요.

           지금은 2026-09-11 아침에 세 폰 모두 통과한 그 모양입니다. */
        Intent open = new Intent(this, MainActivity.class);
        open.setAction(Intent.ACTION_VIEW);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra("alias_call_url",
                "alias_call.html?call=" + callId +
                        "&token=" + token +
                        "&link=" + linkId +
                        "&type=" + type +
                        "&once=" + callId);
        /* 🔴 MainActivity 가 이 번호로 알림을 찾아 지웁니다. */
        open.putExtra("alias_call_id", callId);

        PendingIntent pi = PendingIntent.getActivity(
                this, callId.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        /* 🔴 2026-10-04 (버전 14) — 잠금화면 · 배너를 누르면 "화면 가득 전화 오는 모습" 으로.
             알림 안의 [받기] 단추는 지금처럼 곧바로 통화 화면으로 갑니다. */
        Intent ring = new Intent(this, IncomingCallActivity.class);
        ring.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ring.putExtra("ac_call_id", callId);
        ring.putExtra("ac_call_url", open.getStringExtra("alias_call_url"));
        ring.putExtra("ac_who", who);
        ring.putExtra("ac_call_type", type);
        PendingIntent ringPi = PendingIntent.getActivity(
                this, ("ring" + callId).hashCode(), ring,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        ensureCallChannel();   // 🔴 버전 16 — 업데이트 뒤 앱을 아직 안 열었어도 벨이 울리게
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, "calls")
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(who)
                .setContentText("Alias 전화")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                /* 🔴 setOngoing 은 넣지 마세요 — 함정 (54) */
                /* 🔴 45초 뒤 스스로 사라집니다. 아무도 안 지워도 남지 않습니다. */
                .setTimeoutAfter(RING_MAX_MS)
                .setOnlyAlertOnce(true)
                .setContentIntent(ringPi)
                // 🔴 이 한 줄이 잠금화면 위에 화면을 띄웁니다. (2026-10-04 — 화면 가득 전화 화면으로)
                .setFullScreenIntent(ringPi, true);
        /* 🔴 2026-10-05 (버전 16) — 알림 안의 작은 [받기] 단추를 없앴습니다(에일리어스콜 버전 14 와 같음).
             이제 알림 어디를 눌러도 화면 가득 큰 전화 화면 → 거기서 큰 [받기] / [거절]. */

        /* 🔴 2026-09-11 — 알림 만들기가 실패해도 앱이 무너지지 않게 감쌉니다.
           CallStyle 사고 때 여기서 터지면서 벨도 안 울리고 이상한 창이
           떴습니다. 알림은 덤이고 통화가 본체입니다. */
        /* 🔴 2026-10-05 (버전 16) — 부름을 보고 있는 중이면 알림 없이 곧바로 큰 전화 화면.
             (다른 앱을 쓰는 중에는 안드로이드 규칙상 위쪽 알림으로만 뜹니다 — 일반 전화 앱도 같음.) */
        if (appInForeground()) {
            try { startActivity(ring); } catch (Exception e) { android.util.Log.w("Alias", "전화 화면 바로 열기 실패", e); }
        }

        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                Notification n = b.build();
                n.flags |= Notification.FLAG_INSISTENT;   // 🔴 2026-10-03 — 받을 때까지 반복
                nm.notify("call-" + callId, CALL_NOTI_ID, n);
            }
        } catch (Exception e) {
            android.util.Log.e("Alias", "전화 알림 만들기 실패", e);
        }

        /* 🔴 2026-10-05 (버전 17 · 2-1) — 폰에게도 "부름 전화가 왔다" 고 알립니다.
             → 블루투스 이어폰 · 차 핸들의 받기 · 거절 단추가 부름 전화를 다룹니다.
             ⚠ 실패해도 위의 벨 · 큰 화면은 그대로입니다(모두 잡아 둠). */
        CallConnectionService.incoming(getApplicationContext(), callId, who,
                open.getStringExtra("alias_call_url"), type);
    }

    private String str(String v) {
        return v == null ? "" : v;
    }

    /* 부름이 지금 화면 맨 앞에 떠 있는가 */
    private boolean appInForeground() {
        try {
            android.app.ActivityManager.RunningAppProcessInfo info = new android.app.ActivityManager.RunningAppProcessInfo();
            android.app.ActivityManager.getMyMemoryState(info);
            return info.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        } catch (Exception e) { return false; }
    }

    /* 전화 채널("calls")이 없으면 여기서 만듭니다 — MainActivity.makeChannels 와 같은 설정 */
    private void ensureCallChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null || nm.getNotificationChannel("calls") != null) return;
            android.app.NotificationChannel ch = new android.app.NotificationChannel(
                    "calls", "전화", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("전화가 걸려왔을 때 알려줍니다");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 500, 300, 500, 300, 500});
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ch.setBypassDnd(true);
            ch.setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE),
                    new android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
            nm.createNotificationChannel(ch);
        } catch (Exception e) { android.util.Log.w("Alias", "전화 채널 만들기 실패", e); }
    }
}
