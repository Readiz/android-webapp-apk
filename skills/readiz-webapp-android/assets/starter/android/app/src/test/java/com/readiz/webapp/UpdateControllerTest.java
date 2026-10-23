package com.readiz.webapp;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Looper;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class UpdateControllerTest {
 @Before public void reset(){AppUpdates.prefs(RuntimeEnvironment.getApplication()).edit().clear().commit();}
 static class Fake extends UpdateController {
  int checks,downloads,installs; long latest=999;
  boolean permission=true,offline=false,installFailure=false;
  java.util.List<Runnable> tasks=new java.util.ArrayList<>();
  Fake(Activity a){super(a);}
  @Override void execute(Runnable r){tasks.add(r);}
  @Override JSONObject checkRelease()throws Exception{
   checks++;if(offline)throw new java.io.IOException();
   return new JSONObject().put("packageName",activity.getPackageName()).put("versionCode",latest)
    .put("versionName","9.9."+latest).put("size",4).put("sha256","a".repeat(64));
  }
  @Override void download(JSONObject r){downloads++;}
  @Override void stageInstall(JSONObject r)throws Exception{installs++;if(installFailure)throw new java.io.IOException();}
  @Override boolean canInstall(){return permission;}
  void runTask(){tasks.remove(0).run();Shadows.shadowOf(Looper.getMainLooper()).idle();}
 }
 @Test public void checksBeforeAnyWebPageAndDownloadsButOnlyInstallsInForeground(){
  try(var c=Robolectric.buildActivity(Activity.class).setup()){
   Fake f=new Fake(c.get());f.onResume();assertEquals(1,f.tasks.size());
   f.onResume();f.check();assertEquals("Only one in-flight request",1,f.tasks.size());
   f.onPause();f.runTask();assertEquals(1,f.checks);assertEquals(1,f.downloads);
   assertEquals(0,f.installs);assertTrue(f.tasks.isEmpty());
   f.onResume();f.runTask();assertEquals(1,f.installs);
   f.onPause();f.onResume();f.runTask();
   assertEquals("Still discovers releases after installation was offered",2,f.checks);
   assertEquals(1,f.downloads);assertEquals(1,f.installs);assertTrue(f.tasks.isEmpty());f.close();
  }
 }
 @Test public void nextResumeImmediatelyFindsAReleasePublishedAfterThePreviousCheck(){
  try(var c=Robolectric.buildActivity(Activity.class).setup()){
   Fake f=new Fake(c.get());f.latest=AppUpdates.installed(c.get());
   f.onResume();f.runTask();assertEquals(0,f.downloads);
   f.latest=999;f.onPause();f.onResume();f.runTask();
   assertEquals(2,f.checks);assertEquals(1,f.downloads);f.runTask();assertEquals(1,f.installs);f.close();
  }
 }
 @Test public void permissionRefusalDefersOnlyThisVersionAcrossRestart(){
  try(var c=Robolectric.buildActivity(Activity.class).setup()){
   Fake f=new Fake(c.get());f.permission=false;f.onResume();f.runTask();assertNotNull(f.dialog);
   f.dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
   assertNull(f.ready);f.close();
   Fake next=new Fake(c.get());next.permission=false;next.onResume();next.runTask();
   assertEquals(1,next.checks);assertEquals(0,next.downloads);assertNull(next.dialog);
   next.latest=1000;next.onPause();next.onResume();next.runTask();
   assertEquals(2,next.checks);assertEquals(1,next.downloads);assertNotNull(next.dialog);
   assertEquals(1000,next.ready.optLong("versionCode"));next.close();
  }
 }
 @Test public void grantingPermissionResumesTheReadyUpdate(){
  try(var c=Robolectric.buildActivity(Activity.class).setup()){
   Fake f=new Fake(c.get());f.permission=false;f.onResume();f.runTask();
   f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
   var intent=Shadows.shadowOf(c.get()).getNextStartedActivity();
   assertEquals(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,intent.getAction());
   assertEquals("package:"+c.get().getPackageName(),intent.getDataString());
   f.onPause();f.permission=true;f.onResume();f.runTask();assertEquals(1,f.installs);f.close();
  }
 }
 @Test public void returningWithoutPermissionDoesNotRepeatTheDialog(){
  try(var c=Robolectric.buildActivity(Activity.class).setup()){
   Fake f=new Fake(c.get());f.permission=false;f.onResume();f.runTask();
   f.dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
   f.onPause();f.onResume();assertNull(f.dialog);assertNull(f.ready);
   f.onPause();f.onResume();f.runTask();assertNull(f.dialog);assertEquals(0,f.installs);f.close();
  }
 }
 @Test public void offlineFailureRetriesOnTheVeryNextResume(){
  try(var c=Robolectric.buildActivity(Activity.class).setup()){
   Fake f=new Fake(c.get());f.offline=true;f.onResume();f.runTask();
   assertFalse(f.busy);assertNull(f.dialog);assertNull(f.ready);assertEquals(0,f.installs);
   f.offline=false;f.onPause();f.onResume();f.runTask();f.runTask();
   assertEquals(2,f.checks);assertEquals(1,f.installs);f.close();
  }
 }
 @Test public void installationPreparationFailureDoesNotDeferTheRetry(){
  try(var c=Robolectric.buildActivity(Activity.class).setup()){
   Fake f=new Fake(c.get());f.installFailure=true;f.onResume();f.runTask();f.runTask();
   assertFalse(f.busy);assertFalse(AppUpdates.prefs(c.get()).contains("deferred-version"));
   f.installFailure=false;f.onPause();f.onResume();f.runTask();f.runTask();
   assertEquals(2,f.checks);assertEquals(2,f.installs);f.close();
  }
 }
 @Test public void oldGlobalCooldownsDoNotBlockTheFirstCheckAndExpiredVersionDefersEnd(){
  try(var c=Robolectric.buildActivity(Activity.class).setup()){
   AppUpdates.prefs(c.get()).edit().putLong("installed",AppUpdates.installed(c.get()))
    .putLong("checked",System.currentTimeMillis()).putLong("deferred",Long.MAX_VALUE)
    .putLong("deferred-version",999).putLong("deferred-until",System.currentTimeMillis()-1).commit();
   Fake f=new Fake(c.get());f.onResume();f.runTask();f.runTask();
   assertEquals(1,f.installs);assertFalse(AppUpdates.prefs(c.get()).contains("deferred"));f.close();
  }
 }
 @Test public void closedActivityNeverOpensInstallerAfterDownload(){
  try(var c=Robolectric.buildActivity(Activity.class).setup()){
   Fake f=new Fake(c.get());f.onResume();f.close();f.runTask();
   assertEquals(0,f.installs);assertNull(f.dialog);assertNull(f.ready);f.onResume();assertTrue(f.tasks.isEmpty());
  }
 }
}
