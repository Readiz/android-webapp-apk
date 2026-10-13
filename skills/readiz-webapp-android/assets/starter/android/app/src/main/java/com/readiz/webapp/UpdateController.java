package com.readiz.webapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import org.json.JSONObject;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Foreground entry checks and automatic downloads; the system owns install confirmation.
class UpdateController {
    static final long CHECK_INTERVAL = 15 * 60_000L, DEFER_INTERVAL = 24 * 3600_000L;
    private static WeakReference<UpdateController> visible = new WeakReference<>(null);
    final Activity activity;
    final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    boolean resumed, closed, pageReady, busy, waitingPermission;
    JSONObject ready;
    AlertDialog dialog;
    UpdateController(Activity activity) {
        this.activity = activity;
        long version = AppUpdates.installed(activity);
        if (AppUpdates.prefs(activity).getLong("installed", -1) != version) {
            AppUpdates.cleanup(activity);
            AppUpdates.prefs(activity).edit().putLong("installed",version).remove("checked").remove("deferred").apply();
        }
    }
    void onResume() {
        resumed = true; visible = new WeakReference<>(this);
        if (openConfirmation()) return;
        if (waitingPermission) {
            waitingPermission = false;
            if (canInstall()) installReady(); else defer();
        } else if (ready != null) installReady(); else check();
    }
    void onPageFinished() { pageReady = true; check(); }
    void onPause() { resumed = false; if (visible.get() == this) visible.clear(); dismiss(); }
    static boolean due(long now, long checked, long deferred) {
        return now >= deferred && (checked == 0 || now < checked || now - checked >= CHECK_INTERVAL);
    }
    void check() {
        if (!resumed || closed || !pageReady || busy || ready != null) return;
        long now = System.currentTimeMillis();
        if (!due(now, AppUpdates.prefs(activity).getLong("checked", 0), AppUpdates.prefs(activity).getLong("deferred", 0))) return;
        AppUpdates.prefs(activity).edit().putLong("checked", now).apply(); busy = true;
        execute(() -> {
            JSONObject release = null;
            try { JSONObject next = checkRelease(); if (AppUpdates.newer(activity, next)) { download(next); release = next; } }
            catch (Exception ignored) { /* Offline checks never block the WebView. */ }
            final JSONObject result = release;
            handler.post(() -> { busy = false; if (closed) return; ready = result; if (resumed && ready != null) installReady(); });
        });
    }
    void execute(Runnable task) { worker.execute(task); }
    JSONObject checkRelease() throws Exception { return AppUpdates.check(activity); }
    void download(JSONObject release) throws Exception { AppUpdates.download(activity, release); }
    boolean canInstall() { return Build.VERSION.SDK_INT < 26 || activity.getPackageManager().canRequestPackageInstalls(); }
    void installReady() {
        if (!resumed || closed || ready == null || busy || dialog != null) return;
        if (!canInstall()) {
            dialog = new AlertDialog.Builder(activity).setTitle(BuildConfig.APP_NAME + " 업데이트")
                .setMessage("새 버전 " + ready.optString("versionName") + "을 받았습니다. 다음 화면에서 이 앱의 설치를 허용하면 Android 설치 확인으로 이어집니다.")
                .setPositiveButton("설정 열기", (d, which) -> {
                    dialog = null; waitingPermission = true;
                    try { activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()))); }
                    catch (Exception ignored) { waitingPermission = false; defer(); }
                }).setNegativeButton("나중에", (d, which) -> defer()).setOnCancelListener(d -> defer()).create();
            dialog.show(); return;
        }
        JSONObject release = ready; ready = null; busy = true;
        // Never immediately reopen installation after the user cancels or returns from it.
        AppUpdates.prefs(activity).edit().putLong("deferred", System.currentTimeMillis() + DEFER_INTERVAL).apply();
        execute(() -> {
            boolean failed = false; try { stageInstall(release); } catch (Exception ignored) { failed = true; }
            final boolean error = failed;
            handler.post(() -> { busy = false; if (closed || !resumed) return; if (error) Toast.makeText(activity,"업데이트 설치를 준비하지 못했습니다. 다음에 다시 확인합니다.",Toast.LENGTH_SHORT).show(); else openConfirmation(); });
        });
    }
    void stageInstall(JSONObject release) throws Exception { AppUpdates.install(activity, release); }
    void defer() { ready = null; AppUpdates.prefs(activity).edit().putLong("deferred",System.currentTimeMillis()+DEFER_INTERVAL).apply(); dismiss(); }
    private void dismiss() { if (dialog != null) { dialog.dismiss(); dialog = null; } }
    boolean openConfirmation() {
        if (!resumed || closed) return false;
        String raw = AppUpdates.prefs(activity).getString("update-confirm", ""); if (raw.isEmpty()) return false;
        AppUpdates.prefs(activity).edit().remove("update-confirm").apply();
        try { activity.startActivity(Intent.parseUri(raw, 0)); return true; } catch (Exception ignored) { return false; }
    }
    static void refreshVisible() { UpdateController controller = visible.get(); if (controller != null) controller.handler.post(controller::openConfirmation); }
    void close() { closed = true; onPause(); worker.shutdownNow(); handler.removeCallbacksAndMessages(null); }
}
