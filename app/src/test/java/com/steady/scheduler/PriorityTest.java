package com.steady.scheduler;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteConstraintException;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PriorityTest {
    private Store store;
    @Before public void setup() throws Exception {
        Field singleton=Store.class.getDeclaredField("instance");singleton.setAccessible(true);
        Store previous=(Store)singleton.get(null);if(previous!=null)previous.close();singleton.set(null,null);
        RuntimeEnvironment.getApplication().deleteDatabase("steady.db");store=Store.get(RuntimeEnvironment.getApplication());
    }
    @After public void close(){store.close();}
    private Item task(long project,Boolean override){Item item=new Item();item.title="Task";item.project=project;item.signalOverride=override;store.save(item);return item;}
    @Test public void projectChangesUpdateInheritedTasksButKeepOverrides(){
        long project=store.project(0,"Ship Steady","",true);
        Item inherited=task(project,null),noise=task(project,false),signal=task(project,true);
        assertTrue(store.isSignal(store.find(inherited.id)));assertFalse(store.isSignal(store.find(noise.id)));
        store.project(project,"Ship Steady","",false);
        assertFalse(store.isSignal(store.find(inherited.id)));assertFalse(store.isSignal(store.find(noise.id)));assertTrue(store.isSignal(store.find(signal.id)));
        assertNull(store.find(inherited.id).signalOverride);
        signal.signalOverride=null;store.save(signal);assertFalse(store.isSignal(store.find(signal.id)));
        store.project(project,"Renamed","",true);store.project(project,"Renamed again","");assertTrue(store.projectSignal(project));
    }
    @Test public void standaloneAndBulkDefaultsAreNoiseAndPersistExplicitSignal(){
        Item normal=task(0,null),signal=task(0,true);assertFalse(store.isSignal(store.find(normal.id)));assertTrue(store.isSignal(store.find(signal.id)));
        store.close();assertTrue(store.isSignal(store.find(signal.id)));
    }
    @Test public void versionFourMigrationPreservesDataAndHasOnlyBinaryValues(){
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try {
            db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY,title TEXT,notes TEXT)");
            db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY,title TEXT,project INTEGER)");
            db.execSQL("INSERT INTO projects VALUES(1,'Existing project','Keep notes')");db.execSQL("INSERT INTO items VALUES(2,'Existing task',1)");
            store.onUpgrade(db,4,5);
            try(Cursor c=db.rawQuery("SELECT title,signal FROM projects",null)){assertTrue(c.moveToFirst());assertEquals("Existing project",c.getString(0));assertEquals(0,c.getInt(1));}
            try(Cursor c=db.rawQuery("SELECT title,signal_override FROM items",null)){assertTrue(c.moveToFirst());assertEquals("Existing task",c.getString(0));assertTrue(c.isNull(1));}
            assertThrows(SQLiteConstraintException.class,()->db.execSQL("UPDATE projects SET signal=2"));
            assertThrows(SQLiteConstraintException.class,()->db.execSQL("UPDATE items SET signal_override=2"));
        }finally{db.close();}
    }
    @Test public void filtersResolveOverridesAndSignalFirstPreservesTimeOrder() throws Exception {
        long project=store.project(0,"Important project","",true);
        Item noise=task(project,false),signalEarly=task(0,true),signalLate=task(project,null);
        MainActivity a=Robolectric.buildActivity(MainActivity.class).get();Field field=MainActivity.class.getDeclaredField("store");field.setAccessible(true);field.set(a,store);
        Field filter=MainActivity.class.getDeclaredField("priorityFilter");filter.setAccessible(true);Method matches=MainActivity.class.getDeclaredMethod("matchesPriority",Item.class);matches.setAccessible(true);
        filter.setInt(a,1);assertFalse((boolean)matches.invoke(a,noise));assertTrue((boolean)matches.invoke(a,signalLate));
        filter.setInt(a,2);assertTrue((boolean)matches.invoke(a,noise));assertFalse((boolean)matches.invoke(a,signalLate));
        filter.setInt(a,0);assertTrue((boolean)matches.invoke(a,noise));assertTrue((boolean)matches.invoke(a,signalLate));
        Field sort=MainActivity.class.getDeclaredField("signalFirst");sort.setAccessible(true);sort.setBoolean(a,true);
        List<Item> items=new ArrayList<>(Arrays.asList(noise,signalEarly,signalLate));Method order=MainActivity.class.getDeclaredMethod("orderTasks",List.class);order.setAccessible(true);order.invoke(a,items);
        assertEquals(Arrays.asList(signalEarly,signalLate,noise),items);
    }
}
