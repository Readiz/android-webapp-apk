package com.readiz.webapp;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.Signature;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class AppUpdatesTest {
 Context c;
 @Before public void setup(){c=RuntimeEnvironment.getApplication();AppUpdates.prefs(c).edit().clear().commit();AppUpdates.cleanup(c);}
 JSONObject release()throws Exception{return new JSONObject().put("packageName",c.getPackageName()).put("versionCode",999).put("versionName","9.9.9").put("size",4).put("sha256","a".repeat(64));}
 @Test public void identityVersionAndBounds()throws Exception{JSONObject r=release();assertTrue(AppUpdates.newer(c,r));r.put("versionCode",AppUpdates.installed(c));assertFalse(AppUpdates.newer(c,r));r=release();r.put("packageName","other");assertFalse(AppUpdates.valid(r));r=release();r.put("size",17*1024*1024);assertFalse(AppUpdates.valid(r));r=release();r.put("sha256","bad");assertFalse(AppUpdates.valid(r));r=release();r.put("versionName","../file");assertFalse(AppUpdates.valid(r));}
 @Test public void samePackageAndSignersRequired(){PackageInfo a=new PackageInfo(),b=new PackageInfo();a.packageName=b.packageName=c.getPackageName();a.signatures=b.signatures=new Signature[]{new Signature("abcd")};assertTrue(AppUpdates.sameSigners(a,b));a.signatures=new Signature[]{new Signature("1234")};assertFalse(AppUpdates.sameSigners(a,b));a.signatures=b.signatures;a.packageName="other";assertFalse(AppUpdates.sameSigners(a,b));assertFalse(AppUpdates.sameSigners(null,b));}
 @Test public void corruptedDownloadNeverStages()throws Exception{JSONObject r=release();File f=AppUpdates.file(c,r);try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[]{1,2,3,4});}assertFalse(AppUpdates.verified(c,r,f));assertEquals(0,c.getPackageManager().getPackageInstaller().getMySessions().size());AppUpdates.cleanup(c);assertFalse(f.exists());}
 @Test @Config(sdk=26) public void verifiedArchiveRequiresPermissionAndExactVersion()throws Exception{
  JSONObject r=release();File f=AppUpdates.file(c,r);try(FileOutputStream out=new FileOutputStream(f)){out.write(new byte[]{1,2,3,4});}r.put("sha256",AppUpdates.sha256(f));
  var pm=org.robolectric.Shadows.shadowOf(c.getPackageManager());PackageInfo installed=pm.getInternalMutablePackageInfo(c.getPackageName());installed.signatures=new Signature[]{new Signature("abcd")};
  PackageInfo next=new PackageInfo();next.packageName=c.getPackageName();next.versionCode=999;next.versionName="9.9.9";next.signatures=installed.signatures;pm.setPackageArchiveInfo(f.getAbsolutePath(),next);
  assertTrue(AppUpdates.verified(c,r,f));next.versionName="9.9.8";assertFalse(AppUpdates.verified(c,r,f));next.versionName="9.9.9";
  try{AppUpdates.install(c,r);fail();}catch(java.io.IOException expected){assertEquals("install-permission",expected.getMessage());}
  pm.setCanRequestPackageInstalls(true);AppUpdates.install(c,r);assertTrue(AppUpdates.prefs(c).getInt("update-session",-1)>=0);assertEquals("installing",AppUpdates.prefs(c).getString("update-status",""));
 }
 @Test public void installationAlwaysRequestsSystemUserConfirmation(){var p=AppUpdates.parameters(c,4);assertEquals(PackageInstaller.SessionParams.USER_ACTION_REQUIRED,(int)org.robolectric.util.ReflectionHelpers.getField(p,"requireUserAction"));}
 @Test @Config(sdk=23) public void olderAndroidUsesConfirmationDefault(){var p=AppUpdates.parameters(c,4);assertEquals(c.getPackageName(),org.robolectric.util.ReflectionHelpers.getField(p,"appPackageName"));}
 @Test public void receiverIgnoresForeignSessionsAndDefersUserAction(){AppUpdates.prefs(c).edit().putInt("update-session",7).commit();var receiver=new AppUpdateReceiver();Intent intent=new Intent(BuildConfig.APPLICATION_ID + ".INSTALL_RESULT").putExtra(PackageInstaller.EXTRA_SESSION_ID,8).putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_PENDING_USER_ACTION).putExtra(Intent.EXTRA_INTENT,new Intent("android.intent.action.VIEW"));receiver.onReceive(c,intent);assertFalse(AppUpdates.prefs(c).contains("update-confirm"));intent.putExtra(PackageInstaller.EXTRA_SESSION_ID,7);receiver.onReceive(c,intent);assertTrue(AppUpdates.prefs(c).contains("update-confirm"));intent.putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE_ABORTED);receiver.onReceive(c,intent);assertFalse(AppUpdates.prefs(c).contains("update-confirm"));assertFalse(AppUpdates.prefs(c).contains("update-session"));}
 @Test public void checkingIsBoundedAndDeferralAvoidsCancelLoops(){assertTrue(UpdateController.due(100,0,0));assertFalse(UpdateController.due(100,0,200));assertFalse(UpdateController.due(100,50,0));assertTrue(UpdateController.due(1_000_000,1,0));assertTrue(UpdateController.due(1,100,0));}
}
