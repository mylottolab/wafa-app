package com.weaveapp.alias;

/* =====================================================================
   부름 — 폰에 "부름 전화" 를 알리는 창구 (안드로이드 Telecom · 자체 관리 통화)
   2026-10-05 신설 — 2단계 2-1 (버전 17): 블루투스 이어폰 · 차 핸들 단추로 받기 · 거절

   ⚠ 이 파일의 자리
       C:\alias-app\android\app\src\main\java\com\weaveapp\alias\CallConnectionService.java

   왜
     지금까지 부름 전화는 폰 입장에서 "알림" 일 뿐이라, 블루투스 이어폰이나 차의
     받기 단추가 부름 전화를 몰랐습니다. 폰(Telecom)에 "부름 통화가 왔다" 고 알리면
     이어폰 · 차가 그 전화를 보고, 받기 · 거절 단추가 부름으로 옵니다.

   🔴 안전 원칙 — 지금 잘 되는 것을 해치지 않습니다
     · 벨 · 큰 전화 화면 · 받기/거절은 지금처럼 CallMessagingService · IncomingCallActivity 가 합니다.
     · 이 파일은 그 옆에서 "폰에게도 알려 주는 일" 만 합니다.
     · 여기서 무엇이 실패하든(등록 실패 · 권한 · 폰이 거절) 모두 잡아서, 지금처럼 동작하게 둡니다.

   흐름
     전화 신호 도착   CallMessagingService → incoming(...) → 폰에 "전화 왔음"
     이어폰 · 차 [받기] → onAnswer → 벨 끄고 통화 화면(alias_call.html) 열기 → 통화 중 상태
     이어폰 · 차 [거절] → onReject → 벨 끄고 닫기
     화면에서 받음     IncomingCallActivity.answer → answered(callId) → 통화 중 상태
     화면에서 거절 · 45초 → rejected(callId)
     상대가 끊음(missed) → missed(callId)
     통화 끝(화면이 callEnded) → endAll()
   ⚠ 2-2(끊기) · 2-3(소리 길) · 2-4(일반 전화와 순서)는 다음 단계에서 하나씩 더합니다.
   ===================================================================== */

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import java.util.HashMap;
import java.util.Map;

public class CallConnectionService extends ConnectionService {

    private static final String TAG = "Alias";
    private static final String ACCOUNT_ID = "burum_calls";

    /* 지금 폰에 알려 둔 부름 통화들 — 통화 번호(callId)로 찾습니다 */
    private static final Map<String, BurumConnection> live = new HashMap<>();

    /* ── 폰에 "부름" 을 통화 앱으로 등록 (앱을 열 때 · 전화가 올 때) ───────── */
    static PhoneAccountHandle handle(Context c) {
        return new PhoneAccountHandle(new ComponentName(c, CallConnectionService.class), ACCOUNT_ID);
    }

    static void register(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            TelecomManager tm = c.getSystemService(TelecomManager.class);
            if (tm == null) return;
            PhoneAccount acc = PhoneAccount.builder(handle(c), "와파")
                    .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                    .build();
            tm.registerPhoneAccount(acc);
        } catch (Throwable e) {
            android.util.Log.w(TAG, "[telecom] 등록 실패(지금처럼 동작)", e);
        }
    }

    /* ── 전화 신호가 왔을 때 — 폰에게도 알립니다 ─────────────────────────── */
    static void incoming(Context c, String callId, String who, String callUrl, String type) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || callId == null || callId.isEmpty()) return;
        try {
            register(c);
            TelecomManager tm = c.getSystemService(TelecomManager.class);
            if (tm == null) return;
            if (!tm.isIncomingCallPermitted(handle(c))) {
                android.util.Log.i(TAG, "[telecom] 지금은 폰이 새 전화를 받을 수 없다고 합니다(다른 통화 중 등) — 알림으로만");
                return;
            }
            Bundle inner = new Bundle();
            inner.putString("call_id", callId);
            inner.putString("who", who == null ? "" : who);
            inner.putString("call_url", callUrl == null ? "" : callUrl);
            inner.putString("call_type", type == null ? "voice" : type);
            Bundle extras = new Bundle();
            extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, Uri.fromParts("burum", callId, null));
            extras.putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, inner);
            tm.addNewIncomingCall(handle(c), extras);
            android.util.Log.i(TAG, "[telecom] 폰에 부름 전화를 알렸습니다: " + callId);
        } catch (Throwable e) {
            android.util.Log.w(TAG, "[telecom] 폰에 알리기 실패(지금처럼 알림으로만)", e);
        }
    }

    /* ── 🔴 2026-10-05 (버전 21 · 2-4) 내가 거는 부름 통화도 폰에 알립니다 ──────────
         그래야 통화 중 일반 전화가 오면 폰이 "대기" 로 다룹니다. */
    static void outgoing(Context c, String callId, String who) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || callId == null || callId.isEmpty()) return;
        try {
            register(c);
            TelecomManager tm = c.getSystemService(TelecomManager.class);
            if (tm == null) return;
            if (!tm.isOutgoingCallPermitted(handle(c))) {
                android.util.Log.i(TAG, "[telecom] 지금은 폰이 새 통화를 허락하지 않습니다 — 폰에 알리지 않고 통화만");
                return;
            }
            Bundle inner = new Bundle();
            inner.putString("call_id", callId);
            inner.putString("who", who == null ? "" : who);
            Bundle extras = new Bundle();
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle(c));
            extras.putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, inner);
            tm.placeCall(Uri.fromParts("burum", callId, null), extras);
            android.util.Log.i(TAG, "[telecom] 폰에 내가 거는 부름 통화를 알렸습니다: " + callId);
        } catch (Throwable e) {
            android.util.Log.w(TAG, "[telecom] 거는 통화 알리기 실패(통화는 그대로)", e);
        }
    }

    /* ── 화면에서 받았을 때 · 거절했을 때 · 상대가 끊었을 때 · 통화가 끝났을 때 ── */
    static void answered(String callId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        BurumConnection x = take(callId, false);
        if (x != null) { try { x.setActive(); } catch (Throwable e) { /* 그대로 */ } }
    }

    static void rejected(String callId) { end(callId, DisconnectCause.REJECTED); }

    /* 화면이 벨을 끌 때(받기 · 거절 모두 cancelCall 을 부름) — 아직 "울리는 중" 일 때만 끝냅니다.
       이어폰 · 차로 받아 이미 "통화 중" 이면 그대로 둡니다. */
    static void endIfRinging(String callId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        BurumConnection x = take(callId, false);
        if (x == null || x.getState() != Connection.STATE_RINGING) return;
        end(callId, DisconnectCause.LOCAL);
    }

    /* 통화가 실제로 붙었을 때(화면이 callOngoing) — 남은 연결을 "통화 중" 으로 */
    static void activateAll() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        BurumConnection[] xs;
        synchronized (live) { xs = live.values().toArray(new BurumConnection[0]); }
        for (BurumConnection x : xs) {
            try {
                int st = x.getState();
                if (st == Connection.STATE_RINGING || st == Connection.STATE_DIALING) x.setActive();
            } catch (Throwable e) { /* 그대로 */ }
        }
    }

    /* 🔴🔴 2026-10-18 (버전 22) — 스피커 · 수화기 바꾸기를 폰(Telecom)에 직접 시킵니다.
         왜: 부름 통화를 폰에 "통화" 로 알린 뒤(self-managed)에는 소리 길을 폰이 쥡니다.
             AudioManager 로 스피커를 켜도 폰이 수화기로 되돌려, 스피커를 눌러도 소리가
             커지지 않았습니다(손님 제보 2026-10-18).
         어떻게: 살아 있는 부름 연결마다 setAudioRoute(스피커 / 블루투스 / 수화기·유선).
         돌려주는 값: 폰에 시켰으면 true — 부르는 쪽은 AudioManager 를 건드리지 않습니다. */
    static boolean routeAll(boolean speaker) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
        BurumConnection[] xs;
        synchronized (live) { xs = live.values().toArray(new BurumConnection[0]); }
        boolean any = false;
        for (BurumConnection x : xs) {
            try {
                int st = x.getState();
                if (st == Connection.STATE_DISCONNECTED) continue;
                int route;
                if (speaker) route = CallAudioState.ROUTE_SPEAKER;
                else {
                    CallAudioState cs = x.getCallAudioState();
                    int mask = cs == null ? 0 : cs.getSupportedRouteMask();
                    route = (mask & CallAudioState.ROUTE_BLUETOOTH) != 0
                            ? CallAudioState.ROUTE_BLUETOOTH : CallAudioState.ROUTE_WIRED_OR_EARPIECE;
                }
                x.setAudioRoute(route);
                any = true;
                /* 🔴 2026-10-19 (버전 23) — 0.8초 뒤 폰이 정말 바꿨는지 봅니다. 안 바뀌었으면 옛 방법(AudioManager)으로. */
                final BurumConnection fx = x; final boolean want = speaker;
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        CallAudioState now = fx.getCallAudioState();
                        boolean isSp = now != null && now.getRoute() == CallAudioState.ROUTE_SPEAKER;
                        if (isSp != want) {
                            android.util.Log.w(TAG, "[telecom] 폰이 소리 길을 안 바꿈 → 옛 방법으로");
                            MainActivity.routeAudioDirect(fx.ctx, want);
                        }
                        MainActivity.reportRoute(fx.ctx);
                    } catch (Throwable e) { /* 그대로 */ }
                }, 800);
                android.util.Log.i(TAG, "[telecom] 소리 길을 폰에 시켰습니다: " + route);
            } catch (Throwable e) { android.util.Log.w(TAG, "[telecom] 소리 길 바꾸기 실패", e); }
        }
        return any;
    }

    static void missed(String callId) { end(callId, DisconnectCause.MISSED); }

    static void endAll() {
        String[] ids;
        synchronized (live) { ids = live.keySet().toArray(new String[0]); }
        for (String id : ids) end(id, DisconnectCause.LOCAL);
    }

    private static void end(String callId, int cause) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        BurumConnection x = take(callId, true);
        if (x == null) return;
        try { x.setDisconnected(new DisconnectCause(cause)); x.destroy(); } catch (Throwable e) { /* 그대로 */ }
    }

    private static BurumConnection take(String callId, boolean remove) {
        if (callId == null) return null;
        synchronized (live) { return remove ? live.remove(callId) : live.get(callId); }
    }

    /* ── 폰(Telecom)이 "그 전화를 만들어 달라" 고 부르는 곳 ──────────────── */
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.O)   // 안드로이드 8.0 이상에서만
    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle acc, ConnectionRequest req) {
        Bundle ex = req.getExtras() == null ? null : req.getExtras().getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS);
        if (ex == null) ex = new Bundle();
        BurumConnection x = new BurumConnection(getApplicationContext(),
                ex.getString("call_id", ""), ex.getString("who", ""),
                ex.getString("call_url", ""), ex.getString("call_type", "voice"));
        synchronized (live) { live.put(x.callId, x); }
        x.setRinging();
        /* 45초 안에 받지 않으면 폰에서도 끝냅니다(벨 알림의 45초와 짝) */
        final String id = x.callId;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            BurumConnection y = take(id, false);
            if (y != null && y.getState() == Connection.STATE_RINGING) missed(id);
        }, 45000L);
        android.util.Log.i(TAG, "[telecom] 부름 전화가 폰에 들어왔습니다(울리는 중): " + x.callId);
        return x;
    }

    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle acc, ConnectionRequest req) {
        Bundle ex = req.getExtras() == null ? null : req.getExtras().getBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS);
        if (ex == null) ex = req.getExtras() == null ? new Bundle() : req.getExtras();
        String id = ex.getString("call_id", "");
        if (id.isEmpty() && req.getAddress() != null) id = req.getAddress().getSchemeSpecificPart();
        BurumConnection x = new BurumConnection(getApplicationContext(), id, ex.getString("who", ""), "", "voice");
        synchronized (live) { live.put(x.callId, x); }
        x.setDialing();   // 상대가 받으면(화면이 callOngoing) "통화 중" 으로
        android.util.Log.i(TAG, "[telecom] 내가 거는 부름 통화가 폰에 들어왔습니다: " + x.callId);
        return x;
    }

    @Override
    public void onCreateOutgoingConnectionFailed(PhoneAccountHandle acc, ConnectionRequest req) {
        android.util.Log.w(TAG, "[telecom] 폰이 거는 통화를 받지 않았습니다 — 통화는 그대로");
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle acc, ConnectionRequest req) {
        android.util.Log.w(TAG, "[telecom] 폰이 이 전화를 받지 않았습니다 — 알림으로만 계속");
    }

    /* ── 부름 통화 하나 ──────────────────────────────────────────────── */
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.O)   // 안드로이드 8.0 이상에서만
    static class BurumConnection extends Connection {
        final Context ctx;
        final String callId, callUrl;

        BurumConnection(Context ctx, String callId, String who, String callUrl, String type) {
            this.ctx = ctx;
            this.callId = callId;
            this.callUrl = callUrl;
            setConnectionProperties(PROPERTY_SELF_MANAGED);
            setAudioModeIsVoip(true);
            setAddress(Uri.fromParts("burum", callId, null), TelecomManager.PRESENTATION_ALLOWED);
            /* 이어폰 · 차 화면에 보일 이름 — "📞" 같은 그림 글자는 뺍니다 */
            String name = (who == null ? "" : who).replaceFirst("^📞\\s*", "");
            setCallerDisplayName(name.isEmpty() ? "와파" : name, TelecomManager.PRESENTATION_ALLOWED);
            setConnectionCapabilities(CAPABILITY_HOLD | CAPABILITY_SUPPORT_HOLD | CAPABILITY_MUTE);
        }

        /* 이어폰 · 차의 [받기] */
        @Override
        public void onAnswer() {
            android.util.Log.i(TAG, "[telecom] 이어폰 · 차에서 받기: " + callId);
            stopRing();
            try {
                Intent open = new Intent(ctx, MainActivity.class);
                open.setAction(Intent.ACTION_VIEW);
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                /* 이어폰 · 차의 [받기] 도 "사람이 직접 받았음"(answered=1) — 통화 화면이 바로 연결합니다 */
                open.putExtra("alias_call_url", callUrl + (callUrl.contains("answered=1") ? "" : "&answered=1"));
                open.putExtra("alias_call_id", callId);
                ctx.startActivity(open);
            } catch (Throwable e) {
                android.util.Log.w(TAG, "[telecom] 통화 화면 열기 실패", e);
            }
            IncomingCallActivity.dismissIfShowing(callId);   // ⚠ "끝난 전화" 로 적지 않고 닫기만
            try { setActive(); } catch (Throwable e) { /* 그대로 */ }
        }

        @Override
        public void onAnswer(int videoState) { onAnswer(); }

        /* 🔴 2026-10-19 (버전 23) — 소리 길이 바뀔 때마다 화면에 알려 줍니다(스피커 단추 옆 글자). */
        @Override
        public void onCallAudioStateChanged(CallAudioState state) {
            try {
                int r = state == null ? -1 : state.getRoute();
                String n = r == CallAudioState.ROUTE_SPEAKER ? "speaker"
                         : r == CallAudioState.ROUTE_EARPIECE ? "receiver"
                         : r == CallAudioState.ROUTE_BLUETOOTH ? "bt"
                         : r == CallAudioState.ROUTE_WIRED_HEADSET ? "headset" : "other";
                android.util.Log.i(TAG, "[telecom] 소리 길: " + n);
                MainActivity.runJs("try{ window.AL && AL.onAudioRoute && AL.onAudioRoute('" + n + "'); }catch(e){}");
            } catch (Throwable e) { /* 그대로 */ }
        }

        /* 이어폰 · 차의 [거절]
           🔴 2026-10-05 (버전 19) — 서버에도 "거절" 을 보내 거는 쪽 신호도 멈춥니다.
             앱 화면이 떠 있으면 화면에 시키고, 아니면 통화 화면을 "거절하러" 엽니다(decline=1). */
        @Override
        public void onReject() {
            android.util.Log.i(TAG, "[telecom] 이어폰 · 차에서 거절: " + callId);
            stopRing();
            IncomingCallActivity.dismissIfShowing(callId);
            synchronized (live) { live.remove(callId); }
            setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
            destroy();
            String id = callId.replace("'", "");
            boolean sent = MainActivity.runJs(
                    "(function(){try{" +
                    "if (window.ALIAS_DECLINE_NOW) { ALIAS_DECLINE_NOW('" + id + "'); return; }" +
                    "if (window.AL && AL.declineCall) { AL.declineCall('" + id + "'); }" +
                    "}catch(e){}})()");
            if (!sent && callUrl != null && !callUrl.isEmpty()) {
                try {
                    Intent open = new Intent(ctx, MainActivity.class);
                    open.setAction(Intent.ACTION_VIEW);
                    open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    open.putExtra("alias_call_url", callUrl + "&decline=1");
                    open.putExtra("alias_call_id", callId);
                    ctx.startActivity(open);
                } catch (Throwable e) { android.util.Log.w(TAG, "[telecom] 거절하러 열기 실패", e); }
            }
            CallMessagingService.markDone(ctx, callId);
        }

        /* 통화 중 이어폰 · 차의 [끊기]
           🔴 2026-10-05 (버전 19 · 2-2) — 부름 통화도 실제로 끊습니다(양쪽 모두 종료).
             ⚠ 아직 울리는 중에 끊기가 오면(일부 이어폰) 거절로 봅니다. */
        @Override
        public void onDisconnect() {
            if (getState() == STATE_RINGING) { onReject(); return; }
            android.util.Log.i(TAG, "[telecom] 이어폰 · 차에서 끊기: " + callId);
            stopRing();
            synchronized (live) { live.remove(callId); }
            setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            destroy();
            MainActivity.runJs("(function(){try{" +
                    "if (window.ALIAS_HANGUP_NOW) { ALIAS_HANGUP_NOW(); return; }" +
                    "if (window.AL && AL.endCall) { AL.endCall('completed'); }" +
                    "}catch(e){}})()");
        }

        @Override
        public void onAbort() { onDisconnect(); }

        /* 🔴 2026-10-05 (버전 21 · 2-4) — 일반 전화를 "대기 후 받기" 하면 폰이 부릅니다.
             부름 통화를 대기로 — 서로 소리가 안 가게(마이크 · 상대 소리 끔). */
        @Override
        public void onHold() {
            android.util.Log.i(TAG, "[telecom] 부름 통화를 대기로: " + callId);
            try { setOnHold(); } catch (Throwable e) { /* 그대로 */ }
            MainActivity.runJs("(function(){try{ if (window.ALIAS_HOLD) ALIAS_HOLD(true); }catch(e){}})()");
        }

        /* 일반 통화가 끝나 부름 통화로 돌아올 때 */
        @Override
        public void onUnhold() {
            android.util.Log.i(TAG, "[telecom] 부름 통화로 돌아옵니다: " + callId);
            try { setActive(); } catch (Throwable e) { /* 그대로 */ }
            MainActivity.audioCall(ctx, true, false);   // 통화 모드 · 수화기(이어폰)로 다시
            MainActivity.runJs("(function(){try{ if (window.ALIAS_HOLD) ALIAS_HOLD(false); }catch(e){}})()");
        }

        /* 벨(전화 알림)을 끕니다 */
        private void stopRing() {
            try {
                android.app.NotificationManager nm = ctx.getSystemService(android.app.NotificationManager.class);
                if (nm != null) nm.cancel("call-" + callId, CallMessagingService.CALL_NOTI_ID);
            } catch (Throwable e) { /* 그대로 */ }
        }
    }
}
