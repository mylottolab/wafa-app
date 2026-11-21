package com.weaveapp.alias;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Map;

/* =====================================================================
   🔴 2026-11-22 신설 — 알림에서 바로 [읽음] [답장]

   다른 화면을 보고 있을 때 메시지가 오면 화면 위에 툭 뜨고(카톡처럼),
   그 자리에서 [읽음] 또는 [답장](글 쓰는 칸)을 누릅니다.

   · 서버(alias-temp-push)가 이 앱(caps=qr1)에는 data 만 보냅니다. 그래서 알림을 여기서 직접 만듭니다.
   · 채널 "msg_pop" 은 중요도 「높음」 — 이래야 위에 뜹니다(예전 "messages" 채널은 「보통」이라 안 뜸).
   · 답장 · 읽음은 QuickReplyReceiver 가 서버(alias-quick-reply)에 보냅니다. 앱을 안 열어도 됩니다.
   · 알림을 누르면 그 대화방(alias_chat.html?link=…)이 열립니다.
   ===================================================================== */
public final class QuickReply {
    static final String CH = "msg_pop";
    static final String KEY_TEXT = "qr_text";
    static final String ACT_REPLY = "com.weaveapp.alias.QR_REPLY";
    static final String ACT_READ = "com.weaveapp.alias.QR_READ";

    static boolean ko() {
        return !"en".equals(java.util.Locale.getDefault().getLanguage());
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CH) != null) return;
        NotificationChannel ch = new NotificationChannel(CH, ko() ? "메시지 (위에 띄우기)" : "Messages (pop-up)", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(ko() ? "새 메시지를 화면 위에 띄우고 바로 답장" : "Show new messages on top and reply right away");
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
    }

    static int idOf(String link) { return ("link-" + link).hashCode(); }

    /** 이 앱이 만들 메시지 알림이면 만들고 true. 아니면 false(예전대로 처리). */
    static boolean handle(Context c, Map<String, String> d) {
        if (d == null || !"message".equals(d.get("kind"))) return false;
        String qr = d.get("qr"), qrUrl = d.get("qr_url"), link = d.get("link_id");
        if (qr == null || qr.isEmpty() || qrUrl == null || link == null || link.isEmpty()) return false;
        String title = d.get("title") == null ? "" : d.get("title");
        String body = d.get("body") == null ? "" : d.get("body");
        String url = d.get("url") == null ? ("alias_chat.html?link=" + link) : d.get("url");
        show(c, link, title, body, url, qr, qrUrl);
        return true;
    }

    static void show(Context c, String link, String title, String body, String url, String qr, String qrUrl) {
        ensureChannel(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        int id = idOf(link);
        int flagsI = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra("nureum_page", url);
        PendingIntent openPi = PendingIntent.getActivity(c, id, open, flagsI);

        Intent read = new Intent(c, QuickReplyReceiver.class).setAction(ACT_READ)
                .putExtra("qr", qr).putExtra("qr_url", qrUrl).putExtra("link", link);
        PendingIntent readPi = PendingIntent.getBroadcast(c, id + 1, read, flagsI);

        Intent reply = new Intent(c, QuickReplyReceiver.class).setAction(ACT_REPLY)
                .putExtra("qr", qr).putExtra("qr_url", qrUrl).putExtra("link", link)
                .putExtra("title", title).putExtra("url", url);
        int flagsM = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        PendingIntent replyPi = PendingIntent.getBroadcast(c, id + 2, reply, flagsM);

        android.app.RemoteInput ri = new android.app.RemoteInput.Builder(KEY_TEXT)
                .setLabel(ko() ? "답장 입력" : "Reply").build();
        Notification.Action replyAct = new Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(c, android.R.drawable.ic_menu_send),
                ko() ? "답장" : "Reply", replyPi).addRemoteInput(ri).setAllowGeneratedReplies(true).build();
        Notification.Action readAct = new Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(c, android.R.drawable.ic_menu_view),
                ko() ? "읽음" : "Mark read", readPi).build();

        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH) : new Notification.Builder(c);
        b.setSmallIcon(android.R.drawable.sym_action_chat)
         .setContentTitle(title)
         .setContentText(body)
         .setStyle(new Notification.BigTextStyle().bigText(body))
         .setContentIntent(openPi)
         .setAutoCancel(true)
         .setCategory(Notification.CATEGORY_MESSAGE)
         .setShowWhen(true)
         .addAction(readAct)
         .addAction(replyAct);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        try { nm.notify("link-" + link, id, b.build()); } catch (Exception e) { android.util.Log.w("Alias", "답장 알림 실패", e); }
    }

    /** 답장 뒤 — 알림을 「보냈어요」로 바꿨다가 잠시 뒤 지웁니다(안 바꾸면 답장 칸이 계속 돎). */
    static void done(Context c, String link, String title, String line, String url, boolean ok) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        int id = idOf(link);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, "silent") : new Notification.Builder(c);
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        open.putExtra("nureum_page", url);
        b.setSmallIcon(android.R.drawable.sym_action_chat).setContentTitle(title).setContentText(line)
         .setContentIntent(PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
         .setAutoCancel(true).setOnlyAlertOnce(true);
        if (ok && Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(2500);
        try { nm.notify("link-" + link, id, b.build()); } catch (Exception e) {}
    }
}
