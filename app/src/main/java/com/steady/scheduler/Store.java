package com.steady.scheduler;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Store extends SQLiteOpenHelper {
    private static Store instance;

    public static class Project {
        public long id;
        public String notes;
        public String title;
    }

    public static synchronized Store get(Context c) {
        if (instance == null) {
            instance = new Store(c.getApplicationContext());
        }
        return instance;
    }

    private Store(Context c) {
        super(c, "steady.db", (SQLiteDatabase.CursorFactory) null, 4);
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,notes TEXT NOT NULL)");
        db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,notes TEXT NOT NULL,section TEXT NOT NULL,project INTEGER REFERENCES projects(id) ON DELETE CASCADE,goal INTEGER NOT NULL,scheduled INTEGER NOT NULL,enabled INTEGER NOT NULL,repeat INTEGER NOT NULL,minute INTEGER NOT NULL,start TEXT NOT NULL)");
        db.execSQL("CREATE TABLE entries(item INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,period TEXT NOT NULL,status TEXT NOT NULL,updated INTEGER NOT NULL,PRIMARY KEY(item,period))");
        db.execSQL("CREATE TABLE journal(id INTEGER PRIMARY KEY AUTOINCREMENT,item INTEGER REFERENCES items(id) ON DELETE SET NULL,title TEXT NOT NULL,period TEXT NOT NULL,progress TEXT NOT NULL,obstacle TEXT NOT NULL,next TEXT NOT NULL,created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE photos(id INTEGER PRIMARY KEY AUTOINCREMENT,item INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,path TEXT NOT NULL)");
        journalPhotosTable(db);
        GymStore.create(db);
        JournalRepository.migrate(db);
        db.execSQL("CREATE TABLE snoozes(item INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,period TEXT NOT NULL,at INTEGER NOT NULL,PRIMARY KEY(item,period))");
    }

    private void journalPhotosTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS journal_photos(id INTEGER PRIMARY KEY AUTOINCREMENT,journal INTEGER NOT NULL REFERENCES journal(id) ON DELETE CASCADE,path TEXT NOT NULL)");
    }

    @Override // android.database.sqlite.SQLiteOpenHelper
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 4) {
            JournalRepository.migrate(db);
        }
        if (oldVersion < 3) {
            GymStore.create(db);
        }
        if (oldVersion < 2) {
            journalPhotosTable(db);
            db.execSQL("INSERT INTO journal_photos(journal,path) SELECT journal.id,photos.path FROM journal JOIN photos ON journal.item=photos.item");
        }
    }

    public List<String> journalPhotos(long id) {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT path FROM journal_photos WHERE journal=? ORDER BY id", new String[]{"" + id});
        while (c.moveToNext()) {
            try {
                out.add(c.getString(0));
            } catch (Throwable th) {
                if (c != null) {
                    try {
                        c.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
        if (c != null) {
            c.close();
        }
        return out;
    }

    public void journalPhoto(long id, String path) {
        ContentValues v = new ContentValues();
        v.put("journal", Long.valueOf(id));
        v.put("path", path);
        getWritableDatabase().insertOrThrow("journal_photos", null, v);
    }

    public boolean photoRetained(String path) {
        Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM journal_photos WHERE path=? LIMIT 1", new String[]{path});
        try {
            boolean zMoveToFirst = c.moveToFirst();
            if (c != null) {
                c.close();
            }
            return zMoveToFirst;
        } catch (Throwable th) {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    public long save(Item item) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("title", item.title);
        contentValues.put("notes", item.notes);
        contentValues.put("section", item.section);
        if (item.project == 0) {
            contentValues.putNull("project");
        } else {
            contentValues.put("project", Long.valueOf(item.project));
        }
        contentValues.put("goal", Integer.valueOf(item.goal ? 1 : 0));
        contentValues.put("scheduled", Integer.valueOf(item.scheduled ? 1 : 0));
        contentValues.put("enabled", Integer.valueOf(item.enabled ? 1 : 0));
        contentValues.put("repeat", Integer.valueOf(item.repeat));
        contentValues.put("minute", Integer.valueOf(item.minute));
        contentValues.put("start", item.start);
        if (item.id == 0) {
            item.id = getWritableDatabase().insertOrThrow("items", null, contentValues);
        } else {
            getWritableDatabase().update("items", contentValues, "id=?", new String[]{"" + item.id});
        }
        return item.id;
    }

    private Item item(Cursor c) {
        Item i = new Item();
        i.id = c.getLong(0);
        i.title = c.getString(1);
        i.notes = c.getString(2);
        i.section = c.getString(3);
        i.project = c.getLong(4);
        i.goal = c.getInt(5) == 1;
        i.scheduled = c.getInt(6) == 1;
        i.enabled = c.getInt(7) == 1;
        i.repeat = c.getInt(8);
        i.minute = c.getInt(9);
        i.start = c.getString(10);
        return i;
    }

    public List<Item> items() {
        List<Item> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM items ORDER BY minute,title", null);
        while (c.moveToNext()) {
            try {
                out.add(item(c));
            } catch (Throwable th) {
                if (c != null) {
                    try {
                        c.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
        if (c != null) {
            c.close();
        }
        return out;
    }

    public Item find(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM items WHERE id=?", new String[]{"" + id});
        try {
            Item item = c.moveToFirst() ? item(c) : null;
            if (c != null) {
                c.close();
            }
            return item;
        } catch (Throwable th) {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    public void delete(long id) {
        getWritableDatabase().delete("items", "id=?", new String[]{"" + id});
    }

    public String status(long id, String period) {
        Cursor c = getReadableDatabase().rawQuery("SELECT status FROM entries WHERE item=? AND period=?", new String[]{"" + id, period});
        try {
            String string = c.moveToFirst() ? c.getString(0) : "planned";
            if (c != null) {
                c.close();
            }
            return string;
        } catch (Throwable th) {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    public long updated(long id, String period) {
        Cursor c = getReadableDatabase().rawQuery("SELECT updated FROM entries WHERE item=? AND period=?", new String[]{"" + id, period});
        try {
            long j = c.moveToFirst() ? c.getLong(0) : 0L;
            if (c != null) {
                c.close();
            }
            return j;
        } catch (Throwable th) {
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
            }
            throw th;
        }
    }

    public void status(long id, String period, String status) {
        if (!"done".equals(status(id, period)) || "planned".equals(status)) {
            ContentValues v = new ContentValues();
            v.put("item", Long.valueOf(id));
            v.put("period", period);
            v.put("status", status);
            v.put("updated", Long.valueOf(System.currentTimeMillis()));
            getWritableDatabase().insertWithOnConflict("entries", null, v, 5);
        }
    }

    public Set<String> completed(long id) {
        Set<String> result = new HashSet<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT period FROM entries WHERE item=? AND status='done'", new String[]{"" + id});
        while (c.moveToNext()) {
            try {
                result.add(c.getString(0));
            } catch (Throwable th) {
                if (c != null) {
                    try {
                        c.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
        if (c != null) {
            c.close();
        }
        return result;
    }

    public List<Project> projects() {
        List<Project> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM projects ORDER BY id", null);
        while (c.moveToNext()) {
            try {
                Project p = new Project();
                p.id = c.getLong(0);
                p.title = c.getString(1);
                p.notes = c.getString(2);
                out.add(p);
            } catch (Throwable th) {
                if (c != null) {
                    try {
                        c.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
        if (c != null) {
            c.close();
        }
        return out;
    }

    public long project(long id, String title, String notes) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("notes", notes);
        if (id == 0) {
            return getWritableDatabase().insertOrThrow("projects", null, v);
        }
        getWritableDatabase().update("projects", v, "id=?", new String[]{"" + id});
        return id;
    }

    public void deleteProject(long id) {
        getWritableDatabase().delete("projects", "id=?", new String[]{"" + id});
    }

    public String projectName(long id) {
        for (Project p : projects()) {
            if (p.id == id) {
                return p.title;
            }
        }
        return "Personal";
    }

    public void journal(Item item, String period, String progress, String obstacle, String next) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("item", Long.valueOf(item.id));
            v.put("title", item.title);
            v.put("project_title", item.project > 0 ? projectName(item.project) : "");
            v.put("period", period);
            v.put("progress", progress);
            v.put("obstacle", obstacle);
            v.put("next", next);
            v.put("created", Long.valueOf(System.currentTimeMillis()));
            long journalId = db.insertOrThrow("journal", null, v);
            for (String path : photos(item.id)) {
                journalPhoto(journalId, path);
            }
            status(item.id, period, "done");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public long writeJournal(String title, String date, String text) {
        ContentValues v = new ContentValues();
        v.putNull("item");
        v.put("title", title);
        v.put("period", date);
        v.put("progress", text);
        v.put("obstacle", "");
        v.put("next", "");
        v.put("created", Long.valueOf(System.currentTimeMillis()));
        return getWritableDatabase().insertOrThrow("journal", null, v);
    }

    public List<String[]> journals() {
        List<String[]> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT title,period,progress,obstacle,next,created,id,item,kind,project_title,COALESCE((SELECT name FROM journal_groups WHERE id=journal.group_id),'Ungrouped') FROM journal ORDER BY created DESC,id DESC", null);
        while (c.moveToNext()) {
            try {
                String[] row = new String[11];
                for (int i = 0; i < 11; i++) {
                    row[i] = c.getString(i);
                }
                out.add(row);
            } catch (Throwable th) {
                if (c != null) {
                    try {
                        c.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
        if (c != null) {
            c.close();
        }
        return out;
    }

    public List<String> photos(long id) {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT path FROM photos WHERE item=? ORDER BY id", new String[]{"" + id});
        while (c.moveToNext()) {
            try {
                out.add(c.getString(0));
            } catch (Throwable th) {
                if (c != null) {
                    try {
                        c.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
        if (c != null) {
            c.close();
        }
        return out;
    }

    public void photo(long id, String path) {
        ContentValues v = new ContentValues();
        v.put("item", Long.valueOf(id));
        v.put("path", path);
        getWritableDatabase().insertOrThrow("photos", null, v);
    }

    public void removePhoto(String path) {
        getWritableDatabase().delete("photos", "path=?", new String[]{path});
    }

    public void snooze(long id, String period, long at) {
        ContentValues v = new ContentValues();
        v.put("item", Long.valueOf(id));
        v.put("period", period);
        v.put("at", Long.valueOf(at));
        getWritableDatabase().insertWithOnConflict("snoozes", null, v, 5);
    }

    public void clearSnooze(long id, String period) {
        getWritableDatabase().delete("snoozes", "item=? AND period=?", new String[]{"" + id, period});
    }

    public List<String[]> snoozes() {
        List<String[]> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT item,period,at FROM snoozes", null);
        while (c.moveToNext()) {
            try {
                out.add(new String[]{c.getString(0), c.getString(1), c.getString(2)});
            } catch (Throwable th) {
                if (c != null) {
                    try {
                        c.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        }
        if (c != null) {
            c.close();
        }
        return out;
    }
}
