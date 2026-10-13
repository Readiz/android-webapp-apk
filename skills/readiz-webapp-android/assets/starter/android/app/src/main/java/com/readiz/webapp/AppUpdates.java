package com.readiz.webapp;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;

// Only this app's signed APK is accepted. Every install requires Android user confirmation.
final class AppUpdates {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("app-updates", Context.MODE_PRIVATE); }
    static long installed(Context c) {
        try { PackageInfo info = c.getPackageManager().getPackageInfo(c.getPackageName(), 0); return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }
        catch (Exception e) { return Long.MAX_VALUE; }
    }
    static boolean integer(JSONObject o, String key) { Object v=o.opt(key); return v instanceof Integer || v instanceof Long; }
    static boolean valid(JSONObject release) {
        return integer(release,"versionCode") && integer(release,"size") && BuildConfig.APPLICATION_ID.equals(release.optString("packageName")) && release.optLong("versionCode") > 0 && release.optLong("versionCode") <= Integer.MAX_VALUE
            && release.optString("versionName").matches("[0-9]{1,5}\\.[0-9]{1,5}\\.[0-9]{1,5}") && release.optString("sha256").matches("[a-f0-9]{64}")
            && release.optLong("size") > 0 && release.optLong("size") <= 16 * 1024 * 1024;
    }
    static boolean newer(Context c, JSONObject release) { return valid(release) && release.optLong("versionCode") > installed(c); }
    static HttpURLConnection connection(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(BuildConfig.UPDATE_BASE_URL + path).openConnection();
        connection.setUseCaches(false); connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(4000); connection.setReadTimeout(8000); return connection;
    }
    static JSONObject check(Context c) throws Exception {
        HttpURLConnection connection = connection("app-version");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("release-unavailable");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[1024]; int size;
                long deadline = System.nanoTime() + 20_000_000_000L;
                while ((size = input.read(buffer)) != -1) { if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline || bytes.size() + size > 4096) throw new IOException("release-too-large"); bytes.write(buffer, 0, size); }
            }
            JSONObject release = new JSONObject(bytes.toString("UTF-8"));
            if (!valid(release)) throw new IOException("invalid-release");
            if (!newer(c, release)) cleanup(c);
            return release;
        } finally { connection.disconnect(); }
    }
    static File directory(Context c) { File dir = new File(c.getCacheDir(), "app-updates"); dir.mkdirs(); return dir; }
    static File file(Context c, JSONObject release) { return new File(directory(c), "update-" + release.optLong("versionCode") + ".apk"); }
    static boolean sameSigners(PackageInfo candidate, PackageInfo current) {
        if (candidate == null || current == null || !BuildConfig.APPLICATION_ID.equals(candidate.packageName)) return false;
        Signature[] a = Build.VERSION.SDK_INT >= 28 && candidate.signingInfo != null ? candidate.signingInfo.getApkContentsSigners() : candidate.signatures;
        Signature[] b = Build.VERSION.SDK_INT >= 28 && current.signingInfo != null ? current.signingInfo.getApkContentsSigners() : current.signatures;
        return a != null && b != null && a.length > 0 && b.length > 0 && new HashSet<>(Arrays.asList(a)).equals(new HashSet<>(Arrays.asList(b)));
    }
    static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) { byte[] bytes = new byte[16384]; int size; while ((size = input.read(bytes)) != -1) digest.update(bytes, 0, size); }
        StringBuilder value = new StringBuilder(); for (byte b : digest.digest()) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return value.toString();
    }
    static boolean verified(Context c, JSONObject release, File file) throws Exception {
        if (!newer(c, release) || !file.isFile() || file.length() != release.optLong("size") || !sha256(file).equals(release.optString("sha256"))) return false;
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo apk = c.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), flags);
        if (!sameSigners(apk, c.getPackageManager().getPackageInfo(c.getPackageName(), flags))) return false;
        return (Build.VERSION.SDK_INT >= 28 ? apk.getLongVersionCode() : apk.versionCode) == release.optLong("versionCode")
            && release.optString("versionName").equals(apk.versionName);
    }
    static synchronized File download(Context c, JSONObject release) throws Exception {
        if (!newer(c, release)) throw new IOException("not-newer");
        File result = file(c, release);
        File[] old = directory(c).listFiles(); if (old != null) for (File f : old) if (!f.equals(result)) f.delete();
        if (verified(c, release, result)) return result;
        HttpURLConnection connection = connection("app.apk?version=" + release.optString("versionName") + "&sha256=" + release.optString("sha256"));
        File temporary = new File(directory(c), "download.tmp");
        try {
            if (connection.getResponseCode() != 200) throw new IOException(connection.getResponseCode() == 401 ? "login-required" : "download-failed");
            long received = 0, deadline = System.nanoTime() + 60_000_000_000L;
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] bytes = new byte[16384]; int count;
                while ((count = input.read(bytes)) != -1) {
                    if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline || (received += count) > release.optLong("size")) throw new IOException("download-size");
                    output.write(bytes, 0, count);
                }
            }
            if (!verified(c, release, temporary)) throw new IOException("verification-failed");
            if (!temporary.renameTo(result)) throw new IOException("save-failed");
            return result;
        } finally { temporary.delete(); connection.disconnect(); }
    }
    static PackageInstaller.SessionParams parameters(Context c, long size) {
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName()); params.setSize(size);
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        return params;
    }
    // Persist the session on the worker before handing control to the installer.
    @android.annotation.SuppressLint("ApplySharedPref")
    static synchronized void install(Context c, JSONObject release) throws Exception {
        if (Build.VERSION.SDK_INT >= 26 && !c.getPackageManager().canRequestPackageInstalls()) throw new IOException("install-permission");
        File apk = file(c, release); if (!verified(c, release, apk)) throw new IOException("verification-failed");
        PackageInstaller installer = c.getPackageManager().getPackageInstaller();
        int previous = prefs(c).getInt("update-session", -1);
        if (previous != -1 && installer.getSessionInfo(previous) != null) return;
        int id = installer.createSession(parameters(c, apk.length()));
        try (PackageInstaller.Session session = installer.openSession(id); InputStream input = new FileInputStream(apk)) {
            try (OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[16384]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count); session.fsync(output);
            }
            if (!prefs(c).edit().putInt("update-session", id).remove("update-confirm").putString("update-status", "installing").commit()) throw new IOException("session-save-failed");
            Intent callback = new Intent(c, AppUpdateReceiver.class).setAction(BuildConfig.APPLICATION_ID + ".INSTALL_RESULT").setData(android.net.Uri.parse(BuildConfig.APPLICATION_ID + "://update-session/" + id));
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            session.commit(PendingIntent.getBroadcast(c, id, callback, flags).getIntentSender());
        } catch (Exception error) {
            installer.abandonSession(id); prefs(c).edit().remove("update-session").putString("update-status", "failed").apply(); throw error;
        }
    }
    static void cleanup(Context c) {
        File[] files = directory(c).listFiles(); if (files != null) for (File file : files) file.delete();
        prefs(c).edit().remove("update-session").remove("update-confirm").remove("update-notified").putString("update-status", "current").apply();
    }
}
