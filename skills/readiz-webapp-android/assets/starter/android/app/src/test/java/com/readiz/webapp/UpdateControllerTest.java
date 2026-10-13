package com.readiz.webapp;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class UpdateControllerTest {
 static class Fake extends UpdateController {
  int checks,downloads,installs; boolean permission=true,offline=false; java.util.List<Runnable> tasks=new java.util.ArrayList<>();
  Fake(Activity a){super(a);AppUpdates.prefs(a).edit().clear().commit();}
  @Override void execute(Runnable r){tasks.add(r);}
  @Override JSONObject checkRelease()throws Exception{checks++;if(offline)throw new java.io.IOException();return new JSONObject().put("packageName",activity.getPackageName()).put("versionCode",999).put("versionName","9.9.9").put("size",4).put("sha256","a".repeat(64));}
  @Override void download(JSONObject r){downloads++;}
  @Override void stageInstall(JSONObject r){installs++;}
  @Override boolean canInstall(){return permission;}
  void runTask(){tasks.remove(0).run();Shadows.shadowOf(Looper.getMainLooper()).idle();}
 }
 @Test public void automaticallyChecksAndDownloadsThenWaitsForForeground(){try(var c=Robolectric.buildActivity(MainActivity.class).setup()){Fake f=new Fake(c.get());f.onResume();assertTrue(f.tasks.isEmpty());f.onPageFinished();assertEquals(1,f.tasks.size());f.onPause();f.runTask();assertEquals(1,f.checks);assertEquals(1,f.downloads);assertEquals(0,f.installs);assertTrue(f.tasks.isEmpty());f.onResume();f.runTask();assertEquals(1,f.installs);f.onPause();f.onResume();f.onPageFinished();assertTrue(f.tasks.isEmpty());f.close();}}
 @Test public void permissionRefusalDefersWithoutARepeatDialog(){try(var c=Robolectric.buildActivity(MainActivity.class).setup()){Fake f=new Fake(c.get());f.permission=false;f.onResume();f.onPageFinished();f.runTask();assertNotNull(f.dialog);f.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull(f.ready);f.onPause();f.onResume();assertNull(f.dialog);assertTrue(f.tasks.isEmpty());assertEquals(0,f.installs);f.close();}}
 @Test public void grantingPermissionResumesTheReadyUpdate(){try(var c=Robolectric.buildActivity(MainActivity.class).setup()){Fake f=new Fake(c.get());f.permission=false;f.onResume();f.onPageFinished();f.runTask();f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();var intent=Shadows.shadowOf(c.get()).getNextStartedActivity();assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,intent.getAction());assertEquals("package:"+c.get().getPackageName(),intent.getDataString());f.onPause();f.permission=true;f.onResume();f.runTask();assertEquals(1,f.installs);f.close();}}
 @Test public void offlineCheckLeavesTheAppUsable(){try(var c=Robolectric.buildActivity(MainActivity.class).setup()){Fake f=new Fake(c.get());f.offline=true;f.onResume();f.onPageFinished();f.runTask();assertFalse(f.busy);assertNull(f.dialog);assertNull(f.ready);assertEquals(0,f.installs);assertEquals(NavigationPolicy.HOME,c.get().web.getUrl());f.close();}}
}
