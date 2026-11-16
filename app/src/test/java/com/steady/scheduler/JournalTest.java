package com.steady.scheduler;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteConstraintException;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class JournalTest {
    private Store store;private JournalRepository repo;
    @Before public void setup() throws Exception {
        Field instance=Store.class.getDeclaredField("instance");instance.setAccessible(true);Store previous=(Store)instance.get(null);if(previous!=null)previous.close();instance.set(null,null);
        Context context=RuntimeEnvironment.getApplication();context.deleteDatabase("steady.db");store=Store.get(context);repo=new JournalRepository(store);
    }
    @After public void close(){store.close();}
    private JournalRepository.Entry entry(String title){JournalRepository.Entry e=new JournalRepository.Entry();e.title=title;e.progress="What I learned";return e;}
    @Test public void movingAndDeletingGroupsPreservesEntriesAndPhotos(){
        long group=repo.saveGroup(0,"Engineering");JournalRepository.Entry e=entry("Motor driver");repo.save(e);store.journalPhoto(e.id,"photos/kept.jpg");repo.move(e.id,group);
        assertEquals(1,repo.entries(group,"").size());assertTrue(repo.entries(0,"").isEmpty());
        repo.deleteGroup(group);assertEquals(1,repo.entries(0,"").size());assertEquals("photos/kept.jpg",store.journalPhotos(e.id).get(0));
    }
    @Test public void namesAreUniqueAndSearchTreatsSqlAndWildcardsAsText(){
        repo.saveGroup(0,"Engineering");assertThrows(SQLiteConstraintException.class,()->repo.saveGroup(0,"engineering"));
        repo.save(entry("100% progress"));repo.save(entry("Ordinary entry"));
        assertEquals(1,repo.entries(-1,"%").size());assertTrue(repo.entries(-1,"' OR 1=1 --").isEmpty());
        JournalRepository.Entry quote=entry("O'Reilly's notes");repo.save(quote);assertEquals(1,repo.entries(-1,"O'Reilly").size());
    }
    @Test public void fiveHundredEntriesRemainSearchableAndGrouped(){
        long group=repo.saveGroup(0,"Engineering");SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
        try{for(int i=0;i<500;i++){JournalRepository.Entry e=entry("Experiment "+i);e.groupId=i%2==0?group:0;repo.save(e);}db.setTransactionSuccessful();}finally{db.endTransaction();}
        assertEquals(500,repo.entries(-1,"").size());assertEquals(250,repo.entries(group,"").size());assertEquals(1,repo.entries(-1,"Experiment 499").size());
    }
    @Test public void editingRetainsDatePhotosAndCompletionLink(){
        Item item=new Item();item.title="Goal";item.goal=true;store.save(item);store.journal(item,item.start,"Built a prototype","Learned current limits","Measure the power usage");
        JournalRepository.Entry e=repo.entries(-1,"").get(0);long created=e.created;store.journalPhoto(e.id,"photos/a.jpg");e.title="Updated";e.project="Solar car";repo.save(e);
        assertEquals(created,repo.entries(-1,"").get(0).created);assertEquals("done",store.status(item.id,item.start));assertEquals("photos/a.jpg",store.journalPhotos(e.id).get(0));assertEquals("Solar car",store.journals().get(0)[9]);
    }
    @Test public void versionThreeMigrationPreservesExistingRecords(){
        SQLiteDatabase db=SQLiteDatabase.create(null);db.setForeignKeyConstraintsEnabled(true);
        try{
            db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY,title TEXT)");db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY,project INTEGER)");
            db.execSQL("CREATE TABLE journal(id INTEGER PRIMARY KEY,item INTEGER,title TEXT,period TEXT,progress TEXT,obstacle TEXT,next TEXT,created INTEGER)");
            db.execSQL("INSERT INTO projects VALUES(1,'Solar car')");db.execSQL("INSERT INTO items VALUES(2,1)");db.execSQL("INSERT INTO journal VALUES(3,2,'Original','2026-11-01','Progress','Lesson','Next',123)");
            store.onUpgrade(db,3,4);
            try(Cursor c=db.rawQuery("SELECT title,created,group_id,kind,project_title FROM journal",null)){assertTrue(c.moveToFirst());assertEquals("Original",c.getString(0));assertEquals(123,c.getLong(1));assertTrue(c.isNull(2));assertEquals("Journal",c.getString(3));assertEquals("Solar car",c.getString(4));}
        }finally{db.close();}
    }
}
