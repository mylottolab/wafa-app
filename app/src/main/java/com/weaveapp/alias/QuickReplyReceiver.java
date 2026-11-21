package com.weaveapp.alias;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/* 🔴 2026-11-22 — 알림의 [읽음] · [답장] 을 받아 서버(alias-quick-reply)로 보냅니다. 앱을 열지 않습니다. */
public class QuickReplyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context ctx, final Intent in) {
        final Context c = ctx.getApplicationContext();
        final String act = in.getAction();
        final String qr = in.getStringExtra("qr"), url = in.getStringExtra("qr_url"), link = in.getStringExtra("link");
        final String title = in.getStringExtra("title") == null ? "" : in.getStringExtra("title");
        final String page = in.getStringExtra("url") == null ? ("alias_chat.html?link=" + link) : in.getStringExtra("url");
        if (qr == null || url == null || link == null) return;
        // 우리 서버 주소로만 보냅니다
        String host = android.net.Uri.parse(url).getHost();
        if (!url.startsWith("https://") || host == null || !host.endsWith(".supabase.co")) return;

        String text = null;
        if (QuickReply.ACT_REPLY.equals(act)) {
            Bundle r = android.app.RemoteInput.getResultsFromIntent(in);
            CharSequence cs = r == null ? null : r.getCharSequence(QuickReply.KEY_TEXT);
            text = cs == null ? "" : cs.toString().trim();
            if (text.isEmpty()) { QuickReply.done(c, link, title, QuickReply.ko() ? "보낼 글이 없어요" : "Nothing to send", page, false); return; }
        } else if (QuickReply.ACT_READ.equals(act)) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel("link-" + link, QuickReply.idOf(link));
        } else return;

        final String sendText = text;
        final PendingResult pr = goAsync();
        new Thread(new Runnable() { @Override public void run() {
            boolean ok = false;
            try {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("t", qr); o.put("action", sendText == null ? "read" : "reply");
                if (sendText != null) o.put("text", sendText);
                java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                h.setConnectTimeout(10000); h.setReadTimeout(15000);
                h.setRequestMethod("POST"); h.setDoOutput(true);
                h.setRequestProperty("Content-Type", "application/json");
                byte[] bytes = o.toString().getBytes("UTF-8");
                h.getOutputStream().write(bytes); h.getOutputStream().close();
                ok = h.getResponseCode() == 200;
                h.disconnect();
            } catch (Exception e) {
                android.util.Log.w("Alias", "알림 답장 보내기 실패", e);
            }
            if (sendText != null) {
                QuickReply.done(c, link, title, ok
                        ? ((QuickReply.ko() ? "✓ 보냈어요: " : "✓ Sent: ") + sendText)
                        : (QuickReply.ko() ? "보내지 못했어요. 눌러서 대화방에서 보내 주세요." : "Couldn't send. Tap to open the chat."), page, ok);
            }
            pr.finish();
        }}).start();
    }
}
