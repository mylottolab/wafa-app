package com.weaveapp.alias;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.RemoteViews;

/* =====================================================================
   🔴🔴 2026-10-30 신설 — 누름 라인 위젯 (1칸)

   왜 위젯인가
     앱이 만드는 바로가기 아이콘에는 휴대폰이 구석에 "부름" 표시를
     자동으로 붙입니다(빼는 방법이 없음). 위젯에는 그 표시가 붙지 않습니다.
     그래서 누름 라인은 1칸짜리 위젯으로 놓습니다. 생김새는 아이콘과 같습니다
     (그 사람 색 · 글자 그림 + 아래 이름).

   흐름
     ① 웹이 AliasNative.pinShortcut(...) → MainActivity.pinNureum()
     ② 휴대폰이 "홈 화면에 추가" 창을 띄움 → 손님이 [추가]
     ③ 휴대폰이 이 클래스로 "놓았다"(ACTION_PINNED) + 위젯 번호를 보내 줌
        → 그 번호에 이름 · 라인 주소 · 그림을 저장하고 그림
     ④ 위젯을 누르면 MainActivity 가 "nureum_page" 쪽지를 받아 그 라인 화면을 엶

   ⚠ 위젯 고르개에서 손으로 놓은 위젯(라인 정보 없음)은 "누름" 으로 보이고,
     누르면 누름 첫 화면이 열립니다.
   ===================================================================== */
public class NureumWidget extends AppWidgetProvider {

    public static final String ACTION_PINNED = "com.weaveapp.wafa.NUREUM_PINNED";
    private static final String PREFS = "nureum_widgets";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ACTION_PINNED.equals(intent.getAction())) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                save(ctx, id, intent.getStringExtra("name"), intent.getStringExtra("page"), intent.getStringExtra("icon"));
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .remove("pend_name").remove("pend_page").remove("pend_icon").remove("pend_at").apply();
                draw(ctx, AppWidgetManager.getInstance(ctx), id);
            }
            return;
        }
        super.onReceive(ctx, intent);
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        /* 🔴 v27 — 어떤 휴대폰(삼성 등)은 "놓았다" 알림(ACTION_PINNED)을 늦게 주거나 안 줍니다.
           그래서 [추가] 직전에 적어 둔 "기다리는 라인"을, 정보가 없는 새 위젯이 가져갑니다(2분 안). */
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (int id : ids) {
            if (p.getString("page_" + id, null) == null) claimPending(ctx, id);
            draw(ctx, mgr, id);
        }
    }

    /* MainActivity 가 [추가] 창을 띄우기 직전에 부릅니다 */
    static void setPending(Context ctx, String name, String page, String iconB64) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("pend_name", name).putString("pend_page", page).putString("pend_icon", iconB64)
                .putLong("pend_at", System.currentTimeMillis()).apply();
    }

    private static void claimPending(Context ctx, int id) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long at = p.getLong("pend_at", 0);
        if (at == 0 || System.currentTimeMillis() - at > 120000) return;
        save(ctx, id, p.getString("pend_name", null), p.getString("pend_page", null), p.getString("pend_icon", null));
        p.edit().remove("pend_name").remove("pend_page").remove("pend_icon").remove("pend_at").apply();
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) {
            e.remove("name_" + id).remove("page_" + id);
            try { new java.io.File(ctx.getFilesDir(), "nureum_w_" + id + ".png").delete(); } catch (Exception ignored) {}
        }
        e.apply();
    }

    private static void save(Context ctx, int id, String name, String page, String iconB64) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("name_" + id, name == null ? "와파" : name)
                .putString("page_" + id, page == null ? "nureum.html" : page)
                .apply();
        if (iconB64 != null && !iconB64.isEmpty()) {
            try {
                byte[] b = android.util.Base64.decode(iconB64, android.util.Base64.DEFAULT);
                java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(ctx.getFilesDir(), "nureum_w_" + id + ".png"));
                out.write(b); out.close();
            } catch (Exception e) { android.util.Log.w("Alias", "누름 위젯 그림 저장 실패", e); }
        }
    }

    static void draw(Context ctx, AppWidgetManager mgr, int id) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String name = p.getString("name_" + id, "와파");
        String page = p.getString("page_" + id, "nureum.html");

        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.nureum_widget);
        v.setTextViewText(R.id.nureum_label, name);
        Bitmap bm = null;
        try { bm = BitmapFactory.decodeFile(new java.io.File(ctx.getFilesDir(), "nureum_w_" + id + ".png").getAbsolutePath()); } catch (Exception ignored) {}
        if (bm != null) v.setImageViewBitmap(R.id.nureum_icon, bm);
        else v.setImageViewResource(R.id.nureum_icon, R.drawable.nureum_widget_default);

        Intent go = new Intent(ctx, MainActivity.class);
        go.setAction(Intent.ACTION_VIEW);
        go.setData(Uri.parse("nureum://widget/" + id));          // 위젯마다 다른 쪽지가 되게
        go.putExtra("nureum_page", page);
        go.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 70000 + id, go,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.nureum_root, pi);
        mgr.updateAppWidget(id, v);
    }
}
