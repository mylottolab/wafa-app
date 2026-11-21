package com.weaveapp.alias;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.app.DownloadManager;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;

import com.getcapacitor.BridgeActivity;

/* =====================================================================
   Alias Next-Gen — 앱의 첫 화면
   2026-09-09  알림 채널 추가
   2026-09-10  잠금화면 위에 뜨기 추가
   2026-09-11  ⚠ 알림을 늦게 지우게 바꿨다가 되돌림 (소리가 안 들림)
   2026-09-11  🔴 차가운 시작 때 통화화면이 연락처로 튕기던 문제
   2026-09-11  🔴 끝난 전화 명단을 스무 개까지 기억 (연속 통화 대비)
   2026-09-10  🔴 유령 전화 고침 (아래 "쪽지" 설명을 읽어주세요)
   2026-09-15  🔴 인쇄 창구 추가 (초대장을 종이·PDF 로)
   2026-09-18  🔴 뒤로 가기 — 앱이 통째로 닫히던 문제
   2026-09-19  🔴 내려받기 창구 (녹음 파일을 폰에 저장)
   2026-09-19  🔴 보내기 창구 (초대 링크를 다른 앱으로)
   2026-09-20  🔴 상용 준비 — 앱 속 들여다보기를 막습니다
   2026-09-22  🔴 통화 중 알림줄 (CallOngoingService) — 마이크 지키기
   2026-09-24  🔴 구글 결제 (BillingBridge)
   2026-10-01  🔴 결제 수리판 — storeFlush · storeRecover 창구 (버전 12)

   ⚠ 안드로이드 8부터 알림은 반드시 "채널" 에 속해야 합니다.
     채널이 없으면 알림이 기본 채널로 떨어지고, 기본 채널은 대개
     소리도 안 나고 화면도 안 켭니다.

   ⚠ 채널은 앱을 처음 깔 때 정해지고 그 뒤로는 못 바꿉니다.
     소리나 중요도를 고치려면 앱을 지웠다 다시 깔아야 합니다.

   ⚠ Edge Function 의 channel_id 와 이름이 똑같아야 합니다.
       calls     전화 — 소리 크게, 화면 켜기
       messages  메시지 — 보통
       silent    부재중처럼 조용히 덮어쓸 때

   🔴🔴 2026-09-10 — 유령 전화의 원인이었던 "쪽지" 이야기
     알림을 누르면 안드로이드가 앱에게 "이 주소를 열어라" 라는 쪽지(Intent)를
     건네줍니다. 예전 코드는 그 쪽지를 다 쓰고 removeExtra 로 지웠는데,
     그건 **손에 든 사본만** 지우는 것이었습니다.
     안드로이드는 원본을 작업 기록에 따로 보관하고 있다가, 앱이 죽었다
     되살아나면 **원본을 다시 건네줍니다.**
     그래서 이미 끝난 통화가 또 열렸습니다.

     그래서 이제 세 겹으로 막습니다.
       ① 되살아난 경우(savedInstanceState 가 있음)에는 쪽지를 아예 안 봅니다
       ② 쪽지를 쓰고 나면 setIntent 로 빈 쪽지를 덮어씌웁니다
       ③ 한 번 연 통화 번호를 폰에 적어두고, 같은 번호면 두 번 안 엽니다
          (SharedPreferences 라서 앱이 죽어도 남습니다)
   ===================================================================== */
public class MainActivity extends BridgeActivity {

    /* 🔴 2026-10-05 (버전 19 · 2-2) — 이어폰 · 차의 [끊기] · [거절] 을 화면(웹)에 전하는 길.
         앱이 떠 있으면 화면에 곧바로 시키고, 없으면 false 를 돌려줍니다(부르는 쪽이 다른 길로). */
    private static java.lang.ref.WeakReference<MainActivity> self = new java.lang.ref.WeakReference<>(null);

    /* =================================================================
       🔴🔴 2026-10-05 (버전 20 · 2-3) — 통화 소리 길(통화 모드 · 수화기 · 스피커 · 이어폰)

       왜
         지금까지 통화 소리가 "음악 · 동영상" 처럼 나가 대개 스피커로 크게 났고,
         그 소리가 다시 마이크로 들어가 하울링 · 울림이 생기기 쉬웠습니다.
       어떻게
         통화가 붙으면 폰을 "통화 모드"(MODE_IN_COMMUNICATION)로 — 폰의 통화용 메아리 제거 ·
         잡음 줄이기가 켜집니다. 소리는 블루투스 이어폰 → 유선 이어폰 → (음성) 수화기 /
         (영상) 스피커 순으로 고릅니다. 통화가 끝나면 원래대로 돌려놓습니다.
       ⚠ 무엇이 실패해도 통화는 그대로 갑니다(모두 잡아 둠).
       ================================================================= */
    static void audioCall(android.content.Context c, boolean on, boolean video) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) c.getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            if (on) {
                am.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);
                routeAudio(c, video);   // 영상이면 스피커, 음성이면 수화기 (이어폰이 있으면 이어폰)
            } else {
                if (Build.VERSION.SDK_INT >= 31) { try { am.clearCommunicationDevice(); } catch (Throwable e) { /* 그대로 */ } }
                else { am.setSpeakerphoneOn(false); try { am.stopBluetoothSco(); am.setBluetoothScoOn(false); } catch (Throwable e) { /* 그대로 */ } }
                am.setMode(android.media.AudioManager.MODE_NORMAL);
            }
        } catch (Throwable e) { android.util.Log.w("Alias", "[audio] 통화 모드 바꾸기 실패", e); }
    }

    /* speaker=true 면 스피커, false 면 수화기. 이어폰(블루투스 · 유선)이 있으면 스피커를 고르지 않은 한 이어폰. */
    static void routeAudio(android.content.Context c, boolean speaker) {
        /* 🔴 2026-10-18 (버전 22) — 부름 통화가 폰에 "통화" 로 올라가 있으면 폰(Telecom)에 시킵니다.
             AudioManager 로 바꾸면 폰이 되돌려 스피커가 안 켜졌습니다. */
        try { if (CallConnectionService.routeAll(speaker)) return; } catch (Throwable e) { /* 아래 옛 방법으로 */ }
        routeAudioDirect(c, speaker);
        reportRoute(c);
    }

    /* 🔴 2026-10-19 (버전 23) — 지금 소리가 실제로 나가는 곳을 화면에 알려 줍니다. */
    static void reportRoute(android.content.Context c) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) c.getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            String n = "other";
            if (Build.VERSION.SDK_INT >= 31) {
                android.media.AudioDeviceInfo d = am.getCommunicationDevice();
                int t = d == null ? -1 : d.getType();
                n = t == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ? "speaker"
                  : t == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ? "receiver"
                  : (t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO || t == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET) ? "bt"
                  : (t == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET || t == android.media.AudioDeviceInfo.TYPE_USB_HEADSET) ? "headset" : "other";
            } else {
                n = am.isSpeakerphoneOn() ? "speaker" : (am.isBluetoothScoOn() ? "bt" : "receiver");
            }
            runJs("try{ window.AL && AL.onAudioRoute && AL.onAudioRoute('" + n + "'); }catch(e){}");
        } catch (Throwable e) { /* 그대로 */ }
    }

    /* AudioManager 로 직접 바꾸기(폰에 "통화" 로 안 올라가 있거나, 폰이 안 바꿔 줄 때) */
    static void routeAudioDirect(android.content.Context c, boolean speaker) {
        try {
            android.media.AudioManager am = (android.media.AudioManager) c.getSystemService(AUDIO_SERVICE);
            if (am == null) return;
            if (Build.VERSION.SDK_INT >= 31) {
                java.util.List<android.media.AudioDeviceInfo> ds = am.getAvailableCommunicationDevices();
                android.media.AudioDeviceInfo pick = null;
                int[] order = speaker
                        ? new int[]{ android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        : new int[]{ android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
                                     android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                                     android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                                     android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                                     android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE };
                for (int t : order) {
                    for (android.media.AudioDeviceInfo d : ds) { if (d.getType() == t) { pick = d; break; } }
                    if (pick != null) break;
                }
                if (pick != null) am.setCommunicationDevice(pick);
                android.util.Log.i("Alias", "[audio] 소리 길: " + (pick == null ? "기본" : pick.getType()));
            } else {
                boolean bt = am.isBluetoothScoAvailableOffCall() && !speaker;
                if (bt) { try { am.startBluetoothSco(); am.setBluetoothScoOn(true); } catch (Throwable e) { /* 그대로 */ } }
                am.setSpeakerphoneOn(speaker);
            }
        } catch (Throwable e) { android.util.Log.w("Alias", "[audio] 소리 길 바꾸기 실패", e); }
    }

    static boolean runJs(final String js) {
        final MainActivity a = self.get();
        if (a == null || a.isFinishing()) return false;
        a.runOnUiThread(() -> {
            try { a.getBridge().getWebView().evaluateJavascript(js, null); }
            catch (Throwable e) { android.util.Log.w("Alias", "[telecom] 화면에 전하기 실패", e); }
        });
        return true;
    }

    /* CallMessagingService 가 알림을 달 때 쓴 번호와 똑같아야 합니다. */
    private static final int CALL_NOTI_ID = 1001;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        self = new java.lang.ref.WeakReference<>(this);   // 🔴 2-2
        makeChannels();
        showOverLockScreen();

        /* 🔴 savedInstanceState 가 있다 = 앱이 죽었다가 되살아난 것입니다.
           이때 손에 들린 쪽지는 옛날 것이므로 보지 않고 버립니다. */
        if (savedInstanceState == null) {
            openCallIfAsked(getIntent());
        } else {
            setIntent(new android.content.Intent());
        }

        openPhoneIfAsked(getIntent());   // 🔴 2026-09-12 — "부름" 아이콘으로 들어왔나
        nureumCold = true;
        openNureumIfAsked(getIntent());  // 🔴 2026-10-30 — 누름 라인 아이콘으로 들어왔나
        nureumCold = false;
        addNativeBridge();               // 🔴 2026-09-12 — 웹이 알림을 끌 수 있게
        handleBackButton();              // 🔴 2026-09-18 — 뒤로 가기
        lockDownWebView();               // 🔴 2026-09-20 — 앱 속 들여다보기 막기
        setupBilling();                  // 🔴 2026-09-24 — 구글 결제
        CallConnectionService.register(this);   // 🔴 2026-10-05 2-1 — 폰에 "부름" 을 통화 앱으로 등록
        watchRenderer();                 // 🔴 2026-10-27 (버전 24) — 화면 엔진이 죽어도 앱은 안 꺼지게

        askFullScreenPermission();
    }

    private void makeChannels() {
        // 안드로이드 8 미만은 채널이라는 개념이 없습니다.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        // ── 전화 ──
        // IMPORTANCE_HIGH 여야 화면이 켜지고 위에 툭 튀어나옵니다.
        NotificationChannel calls = new NotificationChannel(
                "calls", "전화", NotificationManager.IMPORTANCE_HIGH);
        calls.setDescription("전화가 걸려왔을 때 알려줍니다");
        calls.enableVibration(true);
        calls.setVibrationPattern(new long[]{0, 500, 300, 500, 300, 500});
        calls.enableLights(true);
        calls.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        calls.setBypassDnd(true);   // 방해금지 중에도 전화는 알립니다

        // 벨소리를 폰 기본 벨소리로 씁니다. 알림음보다 깁니다.
        Uri ring = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build();
        calls.setSound(ring, attrs);

        nm.createNotificationChannel(calls);

        // ── 메시지 ──
        NotificationChannel msgs = new NotificationChannel(
                "messages", "메시지", NotificationManager.IMPORTANCE_DEFAULT);
        msgs.setDescription("새 메시지가 왔을 때 알려줍니다");
        msgs.enableVibration(true);
        msgs.setLockscreenVisibility(android.app.Notification.VISIBILITY_PRIVATE);
        nm.createNotificationChannel(msgs);

        // ── 조용한 알림 ──
        // ⚠ 2026-09-09 추가. "부재중 전화" 처럼 앞 알림을 조용히 덮어쓸 때 씁니다.
        //   IMPORTANCE_LOW 라야 소리도 진동도 안 냅니다.
        NotificationChannel silent = new NotificationChannel(
                "silent", "조용한 알림", NotificationManager.IMPORTANCE_LOW);
        silent.setDescription("부재중 전화처럼 조용히 알려줍니다");
        silent.enableVibration(false);
        silent.setSound(null, null);
        nm.createNotificationChannel(silent);
    }

    /* 🔴 2026-09-10 — 잠금화면 위에 뜨고 화면을 켭니다.
       이게 없으면 전체화면 알림을 띄워도 잠긴 화면에 가려집니다. */
    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    /* 🔴 2026-09-10 — 알림을 눌러 들어온 경우 통화화면으로 보냅니다.
       CallMessagingService 가 주소를 담아 보내면 여기서 받아 엽니다.
       ⚠ 앱이 이미 떠 있을 때는 onCreate 가 아니라 onNewIntent 가 불립니다.
         둘 다 처리해야 어느 쪽에서든 통화화면이 열립니다. */
    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openCallIfAsked(intent);
        openHomeIfIconTapped(intent);   // 🔴 2026-09-12 — 아이콘마다 제 집으로
        openNureumIfAsked(intent);      // 🔴 2026-10-30 — 누름 라인 아이콘
    }

    /* =================================================================
       🔴🔴 2026-10-30 신설 — 누름 라인 아이콘 (바탕화면 바로가기)

       누름 화면에서 [바탕화면에 아이콘 놓기] 를 누르면 웹이
       AliasNative.pinShortcut(...) 을 부릅니다. 그러면 휴대폰이
       "홈 화면에 추가할까요? [추가]" 창을 띄우고, [추가] 를 누르면
       그 사람 이름 · 그림으로 아이콘이 하나 생깁니다(라인마다 하나).

       그 아이콘을 누르면 쪽지에 "nureum_page" 가 담겨 옵니다.
         → 그 라인 화면(nureum_line.html?line=…)을 엽니다.

       ⚠ 우리 누름 라인 화면 주소만 엽니다. 쪽지에 아무 주소나 담겨
         오더라도 다른 곳으로는 가지 않습니다.
       ⚠ 전화 알림으로 들어온 경우가 먼저입니다.
       ================================================================= */
    private static final java.util.regex.Pattern NUREUM_PAGE =
            java.util.regex.Pattern.compile("^(nureum_line\\.html\\?line=[0-9a-f-]{36}|nureum\\.html)$");

    private boolean nureumCold = false;
    /* 🔴 v27 — 마지막으로 무엇으로 놓았나(widget · shortcut · none · 위젯 실패 이유). 화면이 안내 문구를 고르려고 묻습니다 */
    private volatile String lastPinKind = "none";
    private volatile String lastPinWhy = "";

    private void openNureumIfAsked(android.content.Intent intent) {
        if (intent == null) return;
        if (intent.getStringExtra("alias_call_url") != null) return;   // 전화가 우선
        String page0 = intent.getStringExtra("nureum_page");
        /* 🔴 v29 (2026-11-01) — QR 카드를 휴대폰 카메라로 찍으면 burumnureum://line/<번호> 로 들어옵니다 */
        android.net.Uri u = intent.getData();
        if (page0 == null && u != null && "wafa".equals(u.getScheme())) {
            String id = u.getLastPathSegment();
            if ("line".equals(u.getHost()) && id != null && id.matches("^[0-9a-f-]{36}$")) page0 = "nureum_line.html?line=" + id;
            else page0 = "nureum.html";
            intent.setData(null);
        }
        final String page = page0;
        if (page == null || !NUREUM_PAGE.matcher(page).matches()) return;
        intent.removeExtra("nureum_page");
        setIntent(new android.content.Intent());
        android.util.Log.i("Alias", "누름 라인 아이콘으로 들어왔습니다: " + page);
        /* 🔴 v26 — 앱이 꺼져 있다가 켜질 때는 부름 첫 화면을 거치지 않고 곧바로 그 라인 화면을 엽니다
           (전에는 부름 화면이 잠깐 보였다가 넘어감). 그래도 안 넘어갔으면 아래가 한 번 더 옮깁니다. */
        if (nureumCold) {
            try { getBridge().getWebView().loadUrl("https://wafa.kr/" + page); } catch (Exception e) {}
        }
        openNureumLater(page, 20);
    }

    /* openPageLater 와 같지만 "?line=" 까지 같아야 같은 화면으로 봅니다.
       (엄마 라인을 보다가 아들 아이콘을 누르면 아들 라인으로 가야 하니까요) */
    private void openNureumLater(final String path, final int triesLeft) {
        if (triesLeft <= 0) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    String js = "(function(){" +
                            "  var here = location.pathname.split('/').pop() + location.search;" +
                            "  if (here === '" + path + "') return 'done';" +
                            "  if (document.readyState !== 'complete') return 'wait';" +
                            "  location.replace('" + path + "');" +
                            "  return 'go';" +
                            "})()";
                    getBridge().getWebView().evaluateJavascript(js, new android.webkit.ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String v) {
                            if (v != null && v.contains("wait")) openNureumLater(path, triesLeft - 1);
                        }
                    });
                } catch (Exception e) {
                    openNureumLater(path, triesLeft - 1);
                }
            }
        }, 300);
    }

    /* 🔴 v26 — 1칸 위젯으로 놓기. 휴대폰이 [추가] 창을 띄우면 true */
    private boolean pinNureumWidget(String name, String page, String iconB64) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
            android.appwidget.AppWidgetManager mgr = android.appwidget.AppWidgetManager.getInstance(this);
            if (mgr == null || !mgr.isRequestPinAppWidgetSupported()) { lastPinWhy = "widget_not_supported"; return false; }
            android.content.ComponentName provider = new android.content.ComponentName(this, NureumWidget.class);

            NureumWidget.setPending(this, name, page, iconB64);   // 🔴 v27 — 알림이 안 와도 새 위젯이 가져가게
            android.content.Intent cb = new android.content.Intent(this, NureumWidget.class);
            cb.setAction(NureumWidget.ACTION_PINNED);
            cb.putExtra("name", name);
            cb.putExtra("page", page);
            cb.putExtra("icon", iconB64);
            /* ⚠ 휴대폰이 위젯 번호를 넣어 보내야 하므로 MUTABLE 이어야 합니다 */
            int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= android.app.PendingIntent.FLAG_MUTABLE;
            android.app.PendingIntent ok = android.app.PendingIntent.getBroadcast(this,
                    (int) (System.currentTimeMillis() & 0xffff), cb, flags);
            return mgr.requestPinAppWidget(provider, null, ok);
        } catch (Exception e) {
            android.util.Log.w("Alias", "누름 위젯을 못 놓았습니다", e);
            lastPinWhy = String.valueOf(e.getMessage());
            return false;
        }
    }

    /* 바탕화면에 누름 라인 아이콘 하나 놓기 — 성공하면 true(휴대폰이 [추가] 창을 띄움) */
    private boolean pinNureum(String json) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            String id = o.optString("id", "");
            String name = o.optString("name", "와파");
            String url = o.optString("url", "");
            String icon = o.optString("icon", "");
            /* 주소에서 라인 화면 부분만 떼어 검사합니다 */
            String page = url.replaceFirst("^https://burum\\.kr/", "");
            if (!NUREUM_PAGE.matcher(page).matches()) return false;
            if (!id.matches("^nureum_[0-9a-f-]{36}$")) return false;
            if (name.length() > 20) name = name.substring(0, 20);
            /* 🔴 v26 — 먼저 1칸 위젯으로(부름 표시가 안 붙음). 안 되는 휴대폰이면 바로가기 아이콘으로. */
            lastPinKind = "none";
            if (pinNureumWidget(name, page, icon)) { lastPinKind = "widget"; return true; }
            if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(this)) return false;

            androidx.core.graphics.drawable.IconCompat ic;
            try {
                byte[] b = android.util.Base64.decode(icon, android.util.Base64.DEFAULT);
                android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeByteArray(b, 0, b.length);
                ic = (bm != null) ? androidx.core.graphics.drawable.IconCompat.createWithBitmap(bm)
                                  : androidx.core.graphics.drawable.IconCompat.createWithResource(this, R.mipmap.ic_launcher);
            } catch (Exception e) {
                ic = androidx.core.graphics.drawable.IconCompat.createWithResource(this, R.mipmap.ic_launcher);
            }

            android.content.Intent go = new android.content.Intent(this, MainActivity.class);
            go.setAction(android.content.Intent.ACTION_VIEW);
            go.putExtra("nureum_page", page);
            go.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

            androidx.core.content.pm.ShortcutInfoCompat info =
                    new androidx.core.content.pm.ShortcutInfoCompat.Builder(this, id)
                            .setShortLabel(name)
                            .setLongLabel(name)
                            .setIcon(ic)
                            .setIntent(go)
                            .build();
            boolean ok = androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(this, info, null);
            if (ok) lastPinKind = "shortcut";
            return ok;
        } catch (Exception e) {
            android.util.Log.w("Alias", "누름 아이콘을 못 놓았습니다", e);
            return false;
        }
    }

    /* =================================================================
       🔴🔴 2026-09-12 신설 — "부름" 아이콘

       홈 화면에 아이콘을 하나 더 냅니다. 누르면 곧바로 부름 화면이
       열립니다. 보통 폰의 초록 전화 아이콘과 같은 자리입니다.

       앱을 두 개 만드는 게 아닙니다. 같은 앱에 **입구를 하나 더**
       내는 것입니다. 안드로이드에서 activity-alias 라고 부릅니다.

       ⚠ activity-alias 는 쪽지에 값을 담아 보낼 수 없습니다.
         대신 "어느 입구로 들어왔는지" 가 쪽지에 적혀 옵니다.
         그 이름을 보고 가릅니다.

       ⚠ 전화 알림으로 들어온 경우가 먼저입니다. 전화를 받으러 왔는데
         부름 화면이 열리면 전화를 놓칩니다.
       ================================================================= */
    private void openPhoneIfAsked(android.content.Intent intent) {
        if (intent == null) return;

        // 전화가 우선입니다.
        if (intent.getStringExtra("alias_call_url") != null) return;

        android.content.ComponentName c = intent.getComponent();
        if (c == null) return;
        if (!c.getClassName().endsWith("PhoneAlias")) return;

        android.util.Log.i("Alias", "부름 아이콘으로 들어왔습니다");
        openPageLater("alias_phone.html", 15);
    }

    /* =================================================================
       🔴🔴 2026-09-12 신설 — 아이콘마다 제 집으로

       무슨 일이 났나
         "부름" 아이콘과 "Alias" 아이콘을 번갈아 누르니, 나중에는
         Alias 를 눌러도 부름 화면이 나왔습니다.

       왜 그런가
         이 앱은 launchMode="singleTask" 입니다. 이미 떠 있으면 화면을
         **그대로 둔 채 앞으로 꺼내기만** 합니다. 웹 화면은 부름 화면에
         머물러 있고요. "부름" 아이콘은 우리가 옮기라고 시켰지만
         "Alias" 아이콘은 아무 말도 안 해서 그대로 있었던 것입니다.

       그래서 아이콘을 누르면 **그 아이콘의 집**으로 보냅니다.
         Alias 아이콘  → 연락처
         부름  아이콘  → 부름

       ⚠ 아이콘을 눌렀을 때만입니다. 최근앱에서 꺼낸 경우는 쪽지가
         새로 오지 않으므로 하던 화면 그대로입니다. 보던 대화가
         끊기지 않습니다.
       ⚠ 전화 알림으로 들어온 경우는 건드리지 않습니다. 전화가 먼저입니다.
       ================================================================= */
    private void openHomeIfIconTapped(android.content.Intent intent) {
        if (intent == null) return;
        if (intent.getStringExtra("alias_call_url") != null) return;   // 전화가 우선

        if (!android.content.Intent.ACTION_MAIN.equals(intent.getAction())) return;
        java.util.Set<String> cats = intent.getCategories();
        if (cats == null ||
                !cats.contains(android.content.Intent.CATEGORY_LAUNCHER)) return;

        android.content.ComponentName c = intent.getComponent();
        boolean phone = (c != null && c.getClassName().endsWith("PhoneAlias"));
        openPageLater(phone ? "alias_phone.html" : "wafa_dial.html", 15);   // 🔴 2026-11-21 와파: 아이콘을 누르면 번호판(첫 화면)으로
    }

    private void openCallIfAsked(android.content.Intent intent) {
        if (intent == null) return;

        final String path = intent.getStringExtra("alias_call_url");
        if (path == null || path.isEmpty()) return;

        String cid = intent.getStringExtra("alias_call_id");
        if (cid == null) cid = "";
        final String callId = cid;

        /* ── ① 쪽지를 확실히 버립니다 ──────────────────────────────
           removeExtra 만으로는 부족합니다. 빈 쪽지를 덮어씌워야
           나중에 getIntent() 가 옛 것을 못 내놓습니다. */
        intent.removeExtra("alias_call_url");
        intent.removeExtra("alias_call_id");
        setIntent(new android.content.Intent());

        /* ── ② 같은 전화를 두 번 열지 않습니다 ─────────────────────
           앱이 죽었다 살아나도 남도록 폰에 적어둡니다.
           (화면 쪽 sessionStorage 는 앱이 죽으면 지워져서 못 믿습니다) */
        /* 🔴 2026-09-11 고침 — 전에는 "직전 한 통" 만 기억했습니다.
           연속으로 걸면 번호가 덮어써져서, 두 번 전 통화의 늦은 푸시를
           못 막았습니다. 이제 CallMessagingService 의 명단(스무 개)을
           같이 씁니다. */
        if (!callId.isEmpty()) {
            if (CallMessagingService.isDone(getApplicationContext(), callId)) {
                android.util.Log.i("Alias", "이미 끝난 전화입니다. 다시 안 엽니다: " + callId);
                cancelCallNotice(callId);   // 남아 있던 알림도 같이 치웁니다
                return;
            }
            CallMessagingService.markDone(getApplicationContext(), callId);
        }

        /* ── ③ 전화 알림을 지웁니다 ────────────────────────────────

           🔴🔴 2026-09-11 — 잠깐 뺐다가 도로 넣었습니다. 읽어주세요.

           "A 벨이 서너 번 만에 멎는다" 를 고치려고 이 줄을 뺐습니다.
           전화벨은 알림이 떠 있는 동안만 울리니, 안 지우면 계속 울릴
           거라고 본 것입니다.

           그런데 **더 큰 사고가 났습니다.**
           안드로이드에서 전화벨은 "소리 우선권(audio focus)" 을 붙잡습니다.
           통화 소리와는 다른 길로 나가는 소리입니다. 알림을 안 지우니
           우선권이 안 풀렸고, **두 번째 통화부터 소리가 아예 안 들렸습니다.**

           벨이 서너 번에 멎는 것보다 소리가 안 들리는 게 훨씬 큰 문제입니다.
           게다가 그때 화면에는 이미 통화화면이 떠 있어서 손님이 못 받을
           상황은 아닙니다.

           → 되돌립니다. 벨 문제는 다른 방법으로 풀어야 합니다.
             (화면 쪽에서 소리를 내는 방향 — 다만 함정 (59) 를 조심하세요) */
        /* 🔴🔴 2026-09-12 — 여기서는 지우지 않습니다.

           전화벨은 알림이 떠 있는 동안만 울립니다(함정 65). 여기서 지우면
           통화화면이 뜨는 순간 벨이 뚝 끊깁니다.

           그래서 예전에는 화면(웹)이 대신 울리게 했는데, 웹오디오는 폰에서
           들쭉날쭉합니다. 연속으로 걸면 세 번째부터 소리가 안 났습니다.

           → 소리는 안드로이드에게 맡깁니다. 받거나 거절하거나 끝날 때
             화면이 AliasNative.cancelCall() 로 확실하게 끕니다. */

        openPageLater(path, 15);
    }

    private void openPageLater(final String path, final int triesLeft) {
        if (triesLeft <= 0) {
            android.util.Log.w("Alias", "화면이 끝내 준비되지 않았습니다. 포기합니다.");
            return;
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            /* 🔴 href 가 아니라 replace 를 씁니다.
                               href 로 가면 뒤로 가기 기록에 통화화면이 쌓여서
                               뒤로 눌렀을 때 전화가 되살아납니다. */
                            /* 목적지 파일 이름만 떼어내 "이미 그 화면인가" 를 봅니다.
                               같은 화면을 또 열면 하던 일이 끊깁니다. */
                            String page = path.split("\\?")[0];
                            String js =
                                    "(function(){" +
                                            /* 🔴 2026-10-05 — 이미 그 통화 화면이면, "직접 받았음" 일 때 곧바로 연결하라고 알립니다 */
                                            "  if (location.pathname.indexOf('" + page + "') >= 0) {" +
                                            (path.contains("answered=1")
                                                ? "    try { if (window.ALIAS_ANSWER_NOW) ALIAS_ANSWER_NOW(); } catch (e) {}"
                                                : "") +
                                            "    return 'done'; }" +
                                            "  if (document.readyState !== 'complete') return 'wait';" +
                                            "  if (!window.AL || !AL.sb) return 'wait';" +
                                            "  location.replace('" + path.replace("'", "\\'") + "');" +
                                            "  return 'go';" +
                                            "})()";
                            getBridge().getWebView().evaluateJavascript(js,
                                    new android.webkit.ValueCallback<String>() {
                                        @Override
                                        public void onReceiveValue(String v) {
                                            if (v != null && v.contains("wait")) {
                                                openPageLater(path, triesLeft - 1);
                                            }
                                        }
                                    });
                        } catch (Exception e) {
                            android.util.Log.e("Alias", "통화화면 열기 실패", e);
                            openPageLater(path, triesLeft - 1);
                        }
                    }
                }, 400);
    }

    /* =================================================================
       🔴🔴 2026-09-12 신설 — 웹 화면이 알림을 끌 수 있는 창구

       왜 필요한가
         전화벨은 안드로이드 알림이 냅니다. 그게 제일 확실합니다.
         잠금화면에서도, 앱이 잠들어 있어도 울립니다.

         그런데 **끄는 일**은 화면이 해야 합니다. 손님이 받았는지
         거절했는지는 화면만 알거든요.

         전에는 Capacitor 의 removeAllDeliveredNotifications() 를 썼습니다.
         그런데 그건 **Capacitor 가 만든 알림만** 지웁니다. 우리 알림은
         우리 자바가 만든 것이라 안 지워졌을 가능성이 큽니다.
         지운 줄 알고 있었는데 안 지워졌던 것입니다.
         2026-09-11 에 "받아도 소리가 안 난다" 던 사고의 원인으로 보입니다.

       이제 웹에서 이렇게 부릅니다.
         if (window.AliasNative) AliasNative.cancelCall(callId);

       🔴 2026-09-15 — 인쇄를 하나 더 열었습니다.
       🔴 2026-09-19 — 내려받기와 보내기를 더 열었습니다.
       🔴 2026-09-24 — 구글 결제(storeBuy · storeConsume)를 더했습니다.
       🔴 2026-09-22 — 통화 중 알림줄, 알림 상태 묻기를 더했습니다.
       🔴 2026-10-01 — 결제 storeFlush · storeRecover 를 더했습니다.
         이 창구로 할 수 있는 일은 **알림 끄기 · 인쇄 · 내려받기 ·
         보내기 · 통화 중 알림줄 · 알림 상태 묻기와 설정 열기 · 결제** 뿐입니다.
         다른 것은 열어주지 않습니다.
       ================================================================= */
    /* ── 🔴 2026-11-21 기본 음성(TTS) ─────────────────────────────── */
    private android.speech.tts.TextToSpeech tts;
    private volatile boolean ttsOk = false;
    private void ttsInit() {
        try {
            tts = new android.speech.tts.TextToSpeech(getApplicationContext(), status -> {
                ttsOk = (status == android.speech.tts.TextToSpeech.SUCCESS);
                if (!ttsOk) return;
                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) { ttsDone(id); }
                    @Override public void onError(String id) { ttsDone(id); }
                });
            });
        } catch (Exception e) { ttsOk = false; }
    }
    private void ttsDone(final String id) {
        runOnUiThread(() -> {
            try { getBridge().getWebView().evaluateJavascript(
                "try{window.__wafaSpoke&&window.__wafaSpoke(" + org.json.JSONObject.quote(id == null ? "" : id) + ")}catch(e){}", null); } catch (Exception e) {}
        });
    }
    private void wafaSpeak(String text, String lang, String id) {
        if (tts == null || !ttsOk || text == null) { ttsDone(id); return; }
        try {
            java.util.Locale loc = "en".equals(lang) ? java.util.Locale.US : java.util.Locale.KOREAN;
            int r = tts.setLanguage(loc);
            if (r == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || r == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) { ttsDone(id); return; }
            android.os.Bundle b = new android.os.Bundle();
            b.putInt(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC);
            tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, b, id == null ? "wafa" : id);
        } catch (Exception e) { ttsDone(id); }
    }

    private void addNativeBridge() {
        ttsInit();
        try {
            getBridge().getWebView().addJavascriptInterface(new Object() {
                /* 🔴 2026-11-21 와파 음성사서함 안내 — 안드로이드 기본 음성(TextToSpeech)으로 읽어 줌.
                     다 읽으면 화면의 window.__wafaSpoke(id) 를 부릅니다. 없으면 화면이 9초 뒤 알아서 넘어갑니다. */
                @android.webkit.JavascriptInterface
                public boolean ttsReady() { return ttsOk; }
                @android.webkit.JavascriptInterface
                public void speak(final String text, final String lang, final String id) { wafaSpeak(text, lang, id); }
                @android.webkit.JavascriptInterface
                public void stopSpeak() { try { if (tts != null) tts.stop(); } catch (Exception e) {} }
                @android.webkit.JavascriptInterface
                public void cancelCall(String callId) {
                    cancelCallNotice(callId);
                    CallConnectionService.endIfRinging(callId);   // 🔴 2-1 — 화면에서 받거나 거절함 → 폰의 "울리는 중" 정리
                }
                @android.webkit.JavascriptInterface
                public void cancelAllCalls() {
                    try {
                        NotificationManager nm = getSystemService(NotificationManager.class);
                        if (nm == null) return;
                        for (android.service.notification.StatusBarNotification sb : nm.getActiveNotifications()) {
                            if (sb.getId() == CALL_NOTI_ID) nm.cancel(sb.getTag(), sb.getId());
                        }
                    } catch (Exception e) {
                        android.util.Log.w("Alias", "알림 전체 끄기 실패", e);
                    }
                }

                /* =========================================================
                   🔴🔴 2026-09-15 신설 — 인쇄

                   왜 필요한가
                     안드로이드 앱 화면(웹뷰)은 window.print() 를 **아예 못
                     합니다.** 오류도 안 나고 아무 일도 안 일어납니다.
                     손님은 단추가 고장난 줄 압니다(함정 98).

                   어떻게
                     안드로이드에 인쇄 기능이 이미 들어 있습니다(PrintManager).
                     **웹뷰를 통째로 넘기면 알아서 A4 로 나눠 인쇄**해줍니다.
                     화면의 @media print 규칙도 그대로 적용됩니다.

                   ⚠ PDF 를 우리가 만들 필요가 없습니다. 안드로이드 인쇄 창에
                     "PDF 로 저장" 이 이미 있습니다. 한글 글꼴 걱정도 없습니다.
                     웹뷰가 이미 그리고 있는 그대로 넘어가니까요.

                   ⚠ 반드시 메인 스레드에서 불러야 합니다. 웹이 부른 것이라
                     다른 스레드에 있습니다. runOnUiThread 로 옮기지 않으면
                     **조용히 아무 일도 안 일어납니다.**
                   ========================================================= */
                @android.webkit.JavascriptInterface
                public void printPage(final String title) {
                    final String name = (title == null || title.isEmpty())
                            ? "Alias" : title;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                PrintManager pm =
                                        (PrintManager) getSystemService(PRINT_SERVICE);
                                if (pm == null) {
                                    android.util.Log.w("Alias", "인쇄 기능이 없는 폰입니다");
                                    return;
                                }
                                android.webkit.WebView wv = getBridge().getWebView();
                                PrintDocumentAdapter adapter =
                                        wv.createPrintDocumentAdapter(name);
                                pm.print(name, adapter,
                                        new PrintAttributes.Builder()
                                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                                .build());
                                android.util.Log.i("Alias", "인쇄 창을 열었습니다: " + name);
                            } catch (Exception e) {
                                android.util.Log.e("Alias", "인쇄 실패", e);
                            }
                        }
                    });
                }

                /* =========================================================
                   🔴🔴 2026-09-19 신설 — 내려받기

                   왜 필요한가
                     앱 화면(웹뷰)은 파일 내려받기가 막혀 있습니다.
                     <a download> 을 눌러도 **아무 일도 안 일어납니다.**
                     인쇄와 같은 자리입니다(함정 98).

                   어떻게
                     안드로이드에 내려받기 기능이 이미 들어 있습니다
                     (DownloadManager). 주소를 넘기면 알아서 받아
                     **다운로드 폴더**에 넣고, 알림으로 알려줍니다.

                   ⚠ 주소는 시한부입니다(한 시간). 그 안에 받아야 합니다.
                     받고 나면 파일은 폰에 그대로 남습니다.
                   ========================================================= */
                @android.webkit.JavascriptInterface
                public void download(final String url, final String name) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (url == null || url.isEmpty()) return;
                                String fname = (name == null || name.isEmpty())
                                        ? "Alias.bin" : name;

                                DownloadManager.Request req =
                                        new DownloadManager.Request(android.net.Uri.parse(url));
                                req.setTitle(fname);
                                req.setDescription("Alias");
                                req.setNotificationVisibility(
                                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                req.setDestinationInExternalPublicDir(
                                        android.os.Environment.DIRECTORY_DOWNLOADS, fname);

                                DownloadManager dm =
                                        (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                                if (dm == null) {
                                    android.util.Log.w("Alias", "내려받기 기능이 없는 폰입니다");
                                    return;
                                }
                                dm.enqueue(req);
                                android.util.Log.i("Alias", "내려받기를 시작했습니다: " + fname);
                            } catch (Exception e) {
                                android.util.Log.e("Alias", "내려받기 실패", e);
                            }
                        }
                    });
                }

                /* =========================================================
                   🔴🔴 2026-09-19 신설 — 다른 앱으로 보내기

                   왜 필요한가
                     앱 화면(웹뷰)에서 navigator.share 가 **안 됩니다.**
                     공유창이 안 뜨고 조용히 넘어가서, 손님은 "왜 아무
                     일도 안 일어나지" 하게 됩니다.
                     인쇄·내려받기와 같은 자리입니다(함정 98).

                   ⚠ 이건 초대 링크를 보내는 길입니다. **이 앱에서 제일
                     중요한 길**입니다. 사람을 데려오는 유일한 방법이니까요.
                     여기가 막히면 손님이 안 늘어납니다.

                   ⚠ 반드시 메인 스레드에서 불러야 합니다.
                   ========================================================= */
                @android.webkit.JavascriptInterface
                public void shareText(final String text, final String title) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (text == null || text.isEmpty()) return;
                                android.content.Intent i =
                                        new android.content.Intent(android.content.Intent.ACTION_SEND);
                                i.setType("text/plain");
                                i.putExtra(android.content.Intent.EXTRA_TEXT, text);
                                if (title != null && !title.isEmpty()) {
                                    i.putExtra(android.content.Intent.EXTRA_SUBJECT, title);
                                }
                                /* 고르는 창을 띄웁니다. 기본 앱으로 곧장 가지 않게요. */
                                android.content.Intent c =
                                        android.content.Intent.createChooser(
                                                i, (title == null || title.isEmpty())
                                                        ? "보내기" : title);
                                c.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(c);
                                android.util.Log.i("Alias", "보내기 창을 열었습니다");
                            } catch (Exception e) {
                                android.util.Log.e("Alias", "보내기 실패", e);
                            }
                        }
                    });
                }

                @android.webkit.JavascriptInterface
                public boolean canShare() { return true; }

                /* 화면이 "이 앱에서 내려받기가 되는가" 를 미리 물어볼 수 있게 합니다.
                   ⚠ 옛 APK 에는 이 창구가 없습니다. 화면이 있는지부터 봐야 합니다. */
                @android.webkit.JavascriptInterface
                public boolean canDownload() {
                    try {
                        return getSystemService(DOWNLOAD_SERVICE) != null;
                    } catch (Exception e) {
                        return false;
                    }
                }

                /* 화면이 "이 앱에서 인쇄가 되는가" 를 미리 물어볼 수 있게 합니다.
                   되면 [인쇄] 단추를, 안 되면 안내를 보여주려고요.
                   ⚠ 옛 APK 에는 이 창구가 없습니다. 화면 쪽에서 있는지부터
                     확인해야 합니다. */
                /* =========================================================
                   🔴🔴 2026-09-22 신설 — 통화 중 알림줄 (CallOngoingService)

                   통화가 붙으면 켜고, 끝나면 끕니다.
                   ⚠ 알림줄이 떠 있어야 일반 전화 화면에 밀려도 마이크를
                     계속 씁니다. 자세한 것은 CallOngoingService.java 에.
                   ========================================================= */
                @android.webkit.JavascriptInterface
                public void callOngoing(final String peer, final boolean video) {
                    CallOngoingService.start(MainActivity.this, peer, video);
                    keepWebAlive(true);   // 🔴 2026-10-27 (버전 24) — 다른 앱을 봐도 통화 화면이 살아 있게
                    inCall = true;        // 🔴 2026-10-27 (버전 24) — 통화 중 [뒤로] = 앱만 뒤로(통화 유지)
                    audioCall(MainActivity.this, true, video);   // 🔴 2-3 — 통화 모드 · 소리 길
                    CallConnectionService.activateAll();   // 🔴 2-1 — 폰에도 "통화 중"
                }

                /* 🔴 2026-10-27 (버전 24) — 화면이 "앱을 뒤로 숨겨 달라" 고 할 때(통화 중 뒤로 가기) */
                @android.webkit.JavascriptInterface
                public void moveToBack() {
                    runOnUiThread(() -> { try { moveTaskToBack(true); } catch (Throwable e) { /* 그대로 */ } });
                }

                @android.webkit.JavascriptInterface
                public void callEnded() {
                    CallOngoingService.stop(MainActivity.this);
                    keepWebAlive(false);  // 🔴 2026-10-27 (버전 24) — 원래대로
                    inCall = false;
                    audioCall(MainActivity.this, false, false);   // 🔴 2-3 — 원래대로
                    CallConnectionService.endAll();   // 🔴 2-1 — 폰에서도 통화 끝
                }

                /* =========================================================
                   🔴🔴 2026-09-22 신설 — 알림이 켜져 있는지 묻기

                   무슨 일이 났나
                     앱을 깔고 손님이 알림을 꺼두면 **전화벨이 안 울립니다.**
                     그런데 본인은 모릅니다. 그래서 9/15 에 만든 빨간 띠가
                     있는데, 그 띠는 "서버에 기기 번호가 있는가" 만 봤습니다.
                     알림을 꺼도 번호는 그대로 남아 있어서 **띠가 안 떴습니다**
                     (2026-09-22 시험에서 발견).

                   그래서 폰에 직접 묻습니다.
                     notificationsOn()  앱 알림이 켜져 있는가
                     callChannelOn()    그중 "전화" 알림이 켜져 있는가
                                        (앱 알림은 켜두고 전화만 끌 수 있습니다)
                   ========================================================= */
                @android.webkit.JavascriptInterface
                public boolean notificationsOn() {
                    try {
                        return androidx.core.app.NotificationManagerCompat
                                .from(MainActivity.this).areNotificationsEnabled();
                    } catch (Exception e) {
                        return true;   // 모르면 조용히 둡니다. 헛경고가 더 나쁩니다.
                    }
                }

                @android.webkit.JavascriptInterface
                public boolean callChannelOn() {
                    try {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
                        NotificationManager nm = getSystemService(NotificationManager.class);
                        if (nm == null) return true;
                        NotificationChannel ch = nm.getNotificationChannel("calls");
                        if (ch == null) return true;   // 아직 안 만들어졌으면 모릅니다
                        return ch.getImportance() != NotificationManager.IMPORTANCE_NONE;
                    } catch (Exception e) {
                        return true;
                    }
                }

                /* 손님을 알림 설정 화면으로 데려다줍니다.
                   ⚠ "설정 → 애플리케이션 → 부름 → 알림" 을 말로만 알려드리면
                     대부분 못 찾습니다. 바로 열어드립니다. */
                @android.webkit.JavascriptInterface
                public void openNotificationSettings() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                android.content.Intent i;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    i = new android.content.Intent(
                                            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                                    i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,
                                            getPackageName());
                                } else {
                                    i = new android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    i.setData(android.net.Uri.parse("package:" + getPackageName()));
                                }
                                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(i);
                            } catch (Exception e) {
                                android.util.Log.w("Alias", "알림 설정 열기 실패", e);
                            }
                        }
                    });
                }

                /* =========================================================
                   🔴🔴 2026-09-24 신설 — 구글 결제

                   storeBuy(상품번호)    결제창을 띄웁니다
                   storeConsume(영수증)  **서버가 확인한 뒤** 화면이 부릅니다

                   ⚠ 여기서 이용권을 늘리지 않습니다. 앱은 손님 폰에 있어
                     뜯어볼 수 있습니다. 서버가 구글에 직접 물어본 뒤에만
                     늘립니다(alias-plan-google-verify).
                   ⚠ storeConsume 을 빼면 손님이 **같은 이용권을 두 번 못
                     삽니다.** 구글이 "이미 가진 물건" 으로 여깁니다.
                   ========================================================= */
                /* 🔴 2026-10-05 (2-4) — 내가 부름 전화를 걸었을 때 폰에도 알림(일반 전화가 오면 대기로) */
                @android.webkit.JavascriptInterface
                public void callStarted(final String callId, final String who) {
                    CallConnectionService.outgoing(MainActivity.this, callId, who);
                }

                /* 🔴 2026-10-05 (2-3) — 통화 화면의 [🔊 스피커] 단추 */
                @android.webkit.JavascriptInterface
                public void setSpeaker(final boolean on) { routeAudio(MainActivity.this, on); }

                @android.webkit.JavascriptInterface
                public void storeBuy(final String sku) {
                    if (billing == null) {
                        android.util.Log.w("Alias", "결제 창구가 없습니다");
                        return;
                    }
                    runOnUiThread(new Runnable() {
                        @Override public void run() { billing.buy(sku); }
                    });
                }

                @android.webkit.JavascriptInterface
                public void storeSubscribe(final String productId, final String basePlanId, final String accountId) {
                    /* 와파 WAFA — 자동결제 구독(wafa_plan · wafa-1m …). 결과는 storeBuy 와 같이 AL.onStoreEvent 로 */
                    if (billing == null) return;
                    runOnUiThread(new Runnable() {
                        @Override public void run() { billing.subscribe(productId, basePlanId, accountId); }
                    });
                }

                @android.webkit.JavascriptInterface
                public void storeConsume(final String purchaseToken) {
                    if (billing == null) return;
                    billing.consume(purchaseToken);
                }

                /* =========================================================
                   🔴🔴 2026-10-01 신설 — 결제 수리판

                   storeFlush()    화면이 "이제 받을 수 있다" 고 알립니다.
                                   줄에 서 있던 영수증을 바로 넘겨받습니다.
                   storeRecover()  "못 끝낸 영수증이 있나" 다시 찾아 달라고 합니다.
                                   구매 화면이 열릴 때마다 부릅니다.
                   ⚠ 옛 앱(버전 11)에는 이 창구가 없습니다.
                     화면은 typeof 로 있는지 보고 부릅니다.
                   ========================================================= */
                @android.webkit.JavascriptInterface
                public void storeFlush() {
                    if (billing != null) billing.flush();
                }

                @android.webkit.JavascriptInterface
                public void storeRecover() {
                    if (billing == null) return;
                    runOnUiThread(new Runnable() {
                        @Override public void run() { billing.recover(); }
                    });
                }

                /* 🔴 2026-10-30 — 누름 라인 아이콘을 바탕화면에 (휴대폰이 [추가] 창을 띄움) */
                @android.webkit.JavascriptInterface
                public boolean pinShortcut(final String json) {
                    final boolean[] ok = { false };
                    final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try { ok[0] = pinNureum(json); } finally { done.countDown(); }
                        }
                    });
                    try { done.await(3, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) {}
                    return ok[0];
                }
                @android.webkit.JavascriptInterface
                public String lastPinKind() { return lastPinKind + (lastPinWhy.isEmpty() ? "" : ":" + lastPinWhy); }

                @android.webkit.JavascriptInterface
                public boolean canPinShortcut() {
                    try {
                        return androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(MainActivity.this);
                    } catch (Exception e) { return false; }
                }

                @android.webkit.JavascriptInterface
                public boolean canPrint() {
                    try {
                        return getSystemService(PRINT_SERVICE) != null;
                    } catch (Exception e) {
                        return false;
                    }
                }
            }, "AliasNative");
            android.util.Log.i("Alias", "웹 창구(AliasNative)를 열었습니다");
        } catch (Exception e) {
            android.util.Log.w("Alias", "웹 창구 열기 실패", e);
        }
    }

    /* =================================================================
       🔴🔴 2026-09-24 신설 — 구글 결제 창구 만들기

       ⚠ 앱이 켜질 때 미리 만들어 둡니다. 결제를 누른 뒤에 만들면
         구글과 연결하는 데 시간이 걸려 손님이 기다리게 됩니다.
       ⚠ 결제 라이브러리가 없으면(옛 판) 조용히 넘어갑니다.
       ================================================================= */
    private BillingBridge billing = null;

    private void setupBilling() {
        try {
            billing = new BillingBridge(this, getBridge().getWebView());
            android.util.Log.i("Alias", "구글 결제 창구를 만들었습니다");
        } catch (Throwable e) {
            android.util.Log.w("Alias", "구글 결제 창구를 못 만들었습니다", e);
        }
    }

    /* 🔴 2026-09-22 — 앱이 닫히면 통화 중 알림도 끕니다.
       ⚠ 통화 없이 알림줄과 마이크 표시만 남으면 손님이 불안해합니다. */
    @Override
    public void onDestroy() {
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Exception e) {}   // 🔴 2026-11-21
        try { CallOngoingService.stop(this); } catch (Exception e) { /* 그대로 */ }
        try { CallConnectionService.endAll(); } catch (Throwable e) { /* 그대로 */ }
        super.onDestroy();
    }

    /* =================================================================
       🔴🔴 2026-09-20 신설 — 앱 속 들여다보기를 막습니다

       무엇을 막나
         지금까지는 PC 에 폰을 꽂고 크롬에서 chrome://inspect 를 열면
         **앱 화면의 코드와 주고받는 것을 다 들여다볼 수** 있었습니다.
         만드는 동안에는 이게 있어야 고칠 수 있었습니다.

       ⚠ 이제 손님이 생겼습니다. 열어두면 이런 일이 됩니다.
           남의 폰을 잠깐 빌려 대화 내용을 꺼내볼 수 있습니다
           로그인한 채로 두면 열쇠까지 들여다봅니다

       ⚠ 고칠 일이 생기면 **BuildConfig.DEBUG** 판에서는 그대로 열립니다.
         개발용으로 만든 APK 에서는 예전처럼 들여다볼 수 있습니다.
         상용(release) 판에서만 막힙니다.
       ================================================================= */
    /* =================================================================
       🔴🔴 2026-10-27 (버전 24) — 통화 중 다른 앱을 보면 통화가 끊기던 문제

       무슨 일이 났나
         부름 통화 중에 홈 단추를 누르고 다른 앱(지도 · 갤러리 · 카톡 등)을
         보다 돌아오면 통화가 끊겨 있었습니다(손님 제보 2026-10-27).

       왜 그런가
         부름 화면은 웹뷰가 그립니다. 웹뷰의 "화면 엔진"(렌더러)은 앱과 따로
         도는 작은 프로그램인데, 안드로이드는 **화면에 안 보이는 웹뷰의 엔진을
         덜 중요하게** 봅니다. 다른 앱이 메모리를 많이 쓰면 이 엔진을 먼저
         정리해 버리고, 그 순간 통화 화면이 사라져 통화가 끝납니다.
         (통화 중 알림줄 덕분에 앱 자체는 살아 있어도, 엔진은 따로 정리됩니다)

       어떻게
         ① 통화하는 동안만 엔진을 "보이지 않아도 중요" 로 올립니다.
            (setRendererPriorityPolicy · 안드로이드 8 이상)
            통화가 끝나면 원래대로(보이지 않으면 덜 중요) 돌려 놓습니다.
         ② 그래도 엔진이 정리되면, 예전에는 앱이 통째로 꺼졌습니다.
            이제는 앱은 그대로 두고 화면만 다시 엽니다.
       ================================================================= */
    static volatile boolean inCall = false;   // 🔴 2026-10-27 (버전 24)

    void keepWebAlive(final boolean on) {
        runOnUiThread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    getBridge().getWebView().setRendererPriorityPolicy(
                            android.webkit.WebView.RENDERER_PRIORITY_IMPORTANT, !on);
                    android.util.Log.i("Alias", "[call] 화면 엔진 " + (on ? "통화 중 — 항상 중요" : "원래대로"));
                }
            } catch (Throwable e) { android.util.Log.w("Alias", "[call] 화면 엔진 설정 실패", e); }
        });
    }

    private void watchRenderer() {
        try {
            getBridge().addWebViewListener(new com.getcapacitor.WebViewListener() {
                @Override
                public boolean onRenderProcessGone(android.webkit.WebView view, android.webkit.RenderProcessGoneDetail detail) {
                    android.util.Log.w("Alias", "[call] 화면 엔진이 정리되었습니다 — 앱은 두고 화면만 다시 엽니다");
                    try { CallOngoingService.stop(MainActivity.this); } catch (Throwable e) { /* 그대로 */ }
                    try { audioCall(MainActivity.this, false, false); } catch (Throwable e) { /* 그대로 */ }
                    try { CallConnectionService.endAll(); } catch (Throwable e) { /* 그대로 */ }
                    runOnUiThread(MainActivity.this::recreate);
                    return true;   // true = 앱을 끄지 마라
                }
            });
        } catch (Throwable e) { android.util.Log.w("Alias", "[call] 엔진 감시 설정 실패", e); }
    }

    private void lockDownWebView() {
        try {
            /* 개발용 판이면 그대로 둡니다. 고칠 때 필요합니다. */
            boolean isDebug = false;
            try {
                isDebug = (getApplicationInfo().flags
                        & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            } catch (Exception e) { /* 모르면 막는 쪽으로 */ }

            if (!isDebug) {
                android.webkit.WebView.setWebContentsDebuggingEnabled(false);
                android.util.Log.i("Alias", "앱 속 들여다보기를 막았습니다");
            } else {
                android.util.Log.i("Alias", "개발용 판입니다. 들여다보기를 열어둡니다");
            }
        } catch (Exception e) {
            android.util.Log.w("Alias", "들여다보기 막기 실패", e);
        }
    }

    /* =================================================================
       🔴🔴 2026-09-18 신설 — 뒤로 가기

       무슨 일이 났나
         대화 · 관계 상세 · 초대 만들기 어디서든 폰의 뒤로 가기(◁)를
         누르면 **앱이 통째로 닫혀** 바탕화면으로 갔습니다.

       왜 그런가
         화면 쪽(웹)에는 돌아갈 자리를 만들어 두었는데, 앱이 **웹에게
         물어보지도 않고** 곧장 닫고 있었습니다. 웹 코드를 아무리
         고쳐도 소용이 없었습니다.

         확인하는 법이 있습니다 — 삼성 인터넷으로 같은 주소를 열어
         뒤로 가기를 눌러보면 됩니다. 브라우저에서 잘 돌아가면
         **웹은 결백하고 앱이 문제**입니다.

       어떻게
         뒤로 가기를 우리가 받아서, 웹에 돌아갈 자리가 있으면
         웹을 한 걸음 물리고, 없으면 그때 닫습니다.

       ⚠ 통화화면은 웹 쪽에서 돌아갈 자리를 안 만듭니다. 그래서 여기서
         goBack 을 해도 통화화면으로 되돌아가지 않습니다. 일부러 그렇게
         해둔 것입니다 — 끊은 전화가 되살아나면 안 됩니다(2026-09-10).
       ================================================================= */
    private void handleBackButton() {
        try {
            getOnBackPressedDispatcher().addCallback(this,
                    new androidx.activity.OnBackPressedCallback(true) {
                        @Override
                        public void handleOnBackPressed() {
                            /* 🔴🔴 2026-10-27 (버전 24) — 통화 중 [뒤로] 를 누르면 통화가 끊기던 문제(손님 제보).
                               뒤로 가면 통화 화면이 닫히며 통화가 끝났습니다. 이제 통화 중에는
                               일반 전화 앱처럼 **앱만 뒤로 숨기고 통화는 그대로** 둡니다.
                               (알림줄의 "부름 통화 중" 을 누르면 통화 화면으로 돌아옵니다) */
                            if (inCall) {
                                try { moveTaskToBack(true); } catch (Throwable e) { /* 그대로 */ }
                                return;
                            }
                            try {
                                android.webkit.WebView wv =
                                        (getBridge() != null) ? getBridge().getWebView() : null;
                                if (wv != null && wv.canGoBack()) {
                                    wv.goBack();
                                    return;
                                }
                            } catch (Exception e) {
                                android.util.Log.w("Alias", "뒤로 가기 처리 실패", e);
                            }
                            /* 더 갈 데가 없으면 그때 닫습니다. */
                            finish();
                        }
                    });
            android.util.Log.i("Alias", "뒤로 가기를 우리가 받습니다");
        } catch (Exception e) {
            android.util.Log.w("Alias", "뒤로 가기 달기 실패", e);
        }
    }

    /* 전화 알림 하나를 지웁니다. CallMessagingService 가 단 이름표와 똑같아야 합니다. */
    private void cancelCallNotice(String callId) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null || callId == null || callId.isEmpty()) return;
            nm.cancel("call-" + callId, CALL_NOTI_ID);
        } catch (Exception e) {
            android.util.Log.w("Alias", "알림 지우기 실패", e);
        }
    }

    /* 🔴 2026-09-10 — 안드로이드 14부터는 전체화면 알림을 손님이 허락해야 합니다.
       🔴 2026-10-04 (버전 15) — 예전엔 처음 한 번만 물어서, 그때 넘어간 손님은 영영 알림만 받았습니다
         (안드로이드 16 시험에서 발견). 이제 앱을 열 때 꺼져 있으면 알려 드리고 [켜기] 로 설정을 바로 엽니다.
         [나중에] 를 누르면 하루 뒤에 다시 묻습니다.
       ⚠ 13 이하에서는 아무 일도 안 합니다. 그 판은 권한이 필요 없습니다. */
    private boolean fsiDialogShown = false;
    private void askFullScreenPermission() {
        if (Build.VERSION.SDK_INT < 34 || fsiDialogShown) return;
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null || nm.canUseFullScreenIntent()) return;
            final android.content.SharedPreferences sp = getSharedPreferences("alias", MODE_PRIVATE);
            long later = sp.getLong("fsi_later_at", 0L);
            if (System.currentTimeMillis() - later < 24L * 3600 * 1000) return;
            boolean en = java.util.Locale.getDefault().getLanguage().startsWith("en");
            fsiDialogShown = true;
            new android.app.AlertDialog.Builder(this)
                    .setTitle(en ? "Full-screen call alerts are off" : "전체 화면 알림이 꺼져 있어요")
                    .setMessage(en ? "To answer calls from the lock screen like a regular phone, please turn on \"full-screen notifications\" for WAFA."
                                   : "잠긴 폰에서도 일반 전화처럼 받으시려면 와파의 \"전체 화면 알림\" 을 켜 주세요.")
                    .setPositiveButton(en ? "Turn on" : "켜기", (d, w) -> {
                        try {
                            startActivity(new android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    android.net.Uri.parse("package:" + getPackageName())));
                        } catch (Exception e) { android.util.Log.w("Alias", "전체 화면 설정 열기 실패", e); }
                    })
                    .setNegativeButton(en ? "Later" : "나중에", (d, w) ->
                            sp.edit().putLong("fsi_later_at", System.currentTimeMillis()).apply())
                    .setOnDismissListener(d -> fsiDialogShown = false)
                    .show();
        } catch (Exception e) {
            android.util.Log.w("Alias", "전체화면 권한 안내 실패", e);
        }
    }

    /* 🔴 2026-10-04 (버전 15) — 설정에서 켜고 돌아오거나, 앱으로 돌아올 때마다 다시 확인합니다 */
    @Override
    public void onResume() {
        super.onResume();
        askFullScreenPermission();
    }
}
