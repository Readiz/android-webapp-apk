package com.readiz.webapp;
import static org.junit.Assert.*;
import android.webkit.WebSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35, shadows=MainActivityTest.NoNetworkUpdates.class)
public class MainActivityTest {
 @org.robolectric.annotation.Implements(value=UpdateController.class, isInAndroidSdk=false)
 public static class NoNetworkUpdates {
  @org.robolectric.annotation.Implementation protected void execute(Runnable task) { }
 }
 @Test public void configuredHomeKeepsStorageAndRestrictedSettings() {
  try(var c=Robolectric.buildActivity(MainActivity.class).setup()) {
   var a=c.get(); assertEquals(BuildConfig.HOME_URL,a.web.getUrl());
   assertEquals(BuildConfig.APPLICATION_ID,a.getPackageName());
   assertTrue(a.web.getSettings().getUserAgentString().contains(BuildConfig.APPLICATION_ID+"/"+BuildConfig.VERSION_CODE));
   assertTrue(a.web.getSettings().getDomStorageEnabled()); assertTrue(a.web.getSettings().getJavaScriptEnabled());
   assertFalse(a.web.getSettings().getAllowFileAccess()); assertFalse(a.web.getSettings().getAllowContentAccess());
   assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW,a.web.getSettings().getMixedContentMode());
   c.pause().resume(); assertEquals(NavigationPolicy.HOME,a.web.getUrl());
  }
 }
 @Test public void onlyConfiguredHttpsBoundariesStayInside() {
  assertTrue(NavigationPolicy.internal(BuildConfig.HOME_URL));
  for(String prefix:BuildConfig.INTERNAL_URLS) {
   assertTrue(NavigationPolicy.internal(prefix));
   String p=prefix.endsWith("/")?prefix:prefix+"/";
   assertTrue(NavigationPolicy.internal(p+"page?q=1"));
   for(String suffix:new String[]{"../x","%2e%2e/x","%252e%252e/x","%2fx","app.apk"}) assertFalse(NavigationPolicy.internal(p+suffix));
  }
  for(String bad:new String[]{null,"http://example.test/","https://evil.example/","file:///tmp/test","javascript:alert(1)","intent://example","https://user@example.test/","https://example.test:444/"}) assertFalse(NavigationPolicy.internal(bad));
  assertTrue(NavigationPolicy.external("https://elsewhere.test/page")); assertFalse(NavigationPolicy.external("https://user:pass@example.test/"));
 }
}
