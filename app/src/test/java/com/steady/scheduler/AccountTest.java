package com.steady.scheduler;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.file.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class AccountTest {
 private BackupTest fixture;
 @Before public void setup() throws Exception {fixture=new BackupTest();fixture.setup();}
 @After public void close() throws Exception {Store.activate(RuntimeEnvironment.getApplication(),"guest");fixture.store=Store.get(RuntimeEnvironment.getApplication());fixture.close();}
 @Test public void accountSwitchingKeepsGuestAndOtherAccountsSeparate(){
  android.content.Context c=RuntimeEnvironment.getApplication();Item guest=new Item();guest.title="Guest task";fixture.store.save(guest);
  Store.activate(c,"00000000-0000-4000-8000-000000000001");assertTrue(Store.get(c).items().isEmpty());Item own=new Item();own.title="Account one";Store.get(c).save(own);
  Store.activate(c,"00000000-0000-4000-8000-000000000002");assertTrue(Store.get(c).items().isEmpty());
  Store.activate(c,"00000000-0000-4000-8000-000000000001");assertEquals("Account one",Store.get(c).items().get(0).title);
  Store.activate(c,"guest");assertEquals("Guest task",Store.get(c).items().get(0).title);
 }
 @Test public void fingerprintChangesWithPreferences() throws Exception {
  byte[] bytes={1,2};org.json.JSONObject settings=new org.json.JSONObject().put("displayName","A").put("signalFirst",false);
  String first=AccountCloud.fingerprint(bytes,settings);settings.put("signalFirst",true);assertNotEquals(first,AccountCloud.fingerprint(bytes,settings));
 }
}
