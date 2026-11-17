package com.steady.scheduler;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Field;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class BackupTest {
    Store store; File files;
    @Before public void setup() throws Exception {
        Field singleton=Store.class.getDeclaredField("instance"); singleton.setAccessible(true);
        Store previous=(Store)singleton.get(null); if(previous!=null)previous.close(); singleton.set(null,null);
        Context context=RuntimeEnvironment.getApplication();context.deleteDatabase("steady.db");
        store=Store.get(context); files=Files.createTempDirectory("steady-transfer").toFile();
    }
    @After public void close() throws Exception {
        store.close();
        try(java.util.stream.Stream<java.nio.file.Path> paths=Files.walk(files.toPath())) {
            for(java.nio.file.Path path:(Iterable<java.nio.file.Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);
        }
    }
    void seed() throws Exception {
        SQLiteDatabase db=store.getWritableDatabase();
        db.execSQL("INSERT INTO projects(id,title,notes,signal) VALUES(1,'Solar rover','Design documentation',1)");
        Item task=new Item();task.title="Build prototype";task.project=1;task.minute=630;store.save(task);
        Item goal=new Item();goal.title="Ship rover";goal.goal=true;goal.project=1;goal.signalOverride=false;store.save(goal);
        store.journal(goal,goal.start,"Motor tested","Current limits","Tune control loop");
        JournalRepository repo=new JournalRepository(store);
        repo.move(repo.entries(-1,"").get(0).id,repo.saveGroup(0,"Engineering"));
        File photo=new File(files,"rover.jpg");Files.write(photo.toPath(),new byte[]{1,2,3,4});
        store.journalPhoto(repo.entries(-1,"").get(0).id,"rover.jpg");
        db.execSQL("INSERT INTO photos(item,path) VALUES(1,'rover.jpg')");
        db.execSQL("INSERT INTO gym_routines VALUES(1,'Strength')");
        db.execSQL("INSERT INTO gym_exercises VALUES(1,1,'Squat','kg')");
        db.execSQL("INSERT INTO gym_sessions VALUES(1,1,'Strength','2026-11-17','Good session')");
        db.execSQL("INSERT INTO gym_sets VALUES(1,1,1,'Squat',0,5,60.5,'kg')");
        store.snooze(task.id,task.start,1999999999999L);
    }
    byte[] save() throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();PortableBackup.write(store,files,out);return out.toByteArray();}
    Map<String,byte[]> unpack(byte[] bytes) throws Exception {
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry e;while((e=zip.getNextEntry())!=null)entries.put(e.getName(),zip.readAllBytes());}
        return entries;
    }
    byte[] pack(Map<String,byte[]> entries) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){
            for(Map.Entry<String,byte[]> e:entries.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}
        }return out.toByteArray();
    }
    @Test public void roundTripPreservesEveryTablePhotosAndPortfolioExport() throws Exception {
        seed();byte[] backup=save();
        Item extra=new Item();extra.title="Destination-only task";store.save(extra);
        PortableBackup.restore(store,files,new ByteArrayInputStream(backup));
        assertEquals(2,store.items().size());assertTrue(store.items().stream().anyMatch(i->i.goal));
        Map<String,byte[]> before=unpack(backup),after=unpack(save());
        assertEquals(before.keySet(),after.keySet());for(String key:before.keySet())assertArrayEquals(key,before.get(key),after.get(key));
        ByteArrayOutputStream portfolio=new ByteArrayOutputStream();
        JournalExport.write(portfolio,store.journals(),store::journalPhotos,files);
        Map<String,byte[]> exported=unpack(portfolio.toByteArray());
        String html=new String(exported.get("journal.html"),StandardCharsets.UTF_8);
        assertTrue(html.contains("Solar rover"));assertTrue(html.contains("Motor tested"));assertTrue(html.contains("Engineering"));
        assertArrayEquals(new byte[]{1,2,3,4},exported.get("photos/photo-1.jpg"));
    }
    @Test public void malformedReferencesDoNotReplaceExistingData() throws Exception {
        seed();byte[] original=save();Map<String,byte[]> archive=unpack(original);
        JSONObject json=new JSONObject(new String(archive.get("steady.json"),StandardCharsets.UTF_8));
        json.getJSONObject("data").getJSONArray("items").getJSONObject(0).put("project",999);
        archive.put("steady.json",json.toString().getBytes(StandardCharsets.UTF_8));
        assertThrows(Exception.class,()->PortableBackup.restore(store,files,new ByteArrayInputStream(pack(archive))));
        assertArrayEquals(unpack(original).get("steady.json"),unpack(save()).get("steady.json"));
        assertEquals(1,files.list().length);
    }
    @Test public void traversalMissingPhotosAndFutureVersionsAreRejected() throws Exception {
        seed();Map<String,byte[]> original=unpack(save());
        Map<String,byte[]> traversal=new LinkedHashMap<>(original);traversal.put("../outside.txt",new byte[]{1});
        assertThrows(IOException.class,()->PortableBackup.restore(store,files,new ByteArrayInputStream(pack(traversal))));
        Map<String,byte[]> missing=new LinkedHashMap<>(original);missing.remove("media/1.jpg");
        assertThrows(IOException.class,()->PortableBackup.restore(store,files,new ByteArrayInputStream(pack(missing))));
        JSONObject json=new JSONObject(new String(original.get("steady.json"),StandardCharsets.UTF_8));json.put("version",99);
        original.put("steady.json",json.toString().getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class,()->PortableBackup.restore(store,files,new ByteArrayInputStream(pack(original))));
        assertEquals(2,store.items().size());assertEquals(1,files.list().length);
    }
    @Test public void invalidScheduleAndOversizedManifestAreRejected() throws Exception {
        seed();Map<String,byte[]> archive=unpack(save());
        JSONObject json=new JSONObject(new String(archive.get("steady.json"),StandardCharsets.UTF_8));
        json.getJSONObject("data").getJSONArray("items").getJSONObject(0).put("start","not-a-date");
        archive.put("steady.json",json.toString().getBytes(StandardCharsets.UTF_8));
        assertThrows(Exception.class,()->PortableBackup.restore(store,files,new ByteArrayInputStream(pack(archive))));
        archive.put("steady.json",new byte[PortableBackup.MAX_JSON+1]);
        assertThrows(IOException.class,()->PortableBackup.restore(store,files,new ByteArrayInputStream(pack(archive))));
        assertEquals(2,store.items().size());
    }
}
