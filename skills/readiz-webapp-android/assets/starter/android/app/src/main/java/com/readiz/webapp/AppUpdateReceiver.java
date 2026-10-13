package com.readiz.webapp;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public final class AppUpdateReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!(BuildConfig.APPLICATION_ID + ".INSTALL_RESULT").equals(intent.getAction())) return;
        int id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1);
        if (id == -1 || id != AppUpdates.prefs(context).getInt("update-session", -2)) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_SUCCESS) AppUpdates.cleanup(context);
        else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm == null) return;
            AppUpdates.prefs(context).edit().putString("update-confirm",confirm.toUri(0)).putString("update-status","approval").apply();
        } else AppUpdates.prefs(context).edit().remove("update-session").remove("update-confirm").putString("update-status","failed").apply();
        UpdateController.refreshVisible();
    }
}
