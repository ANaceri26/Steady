package com.steady.scheduler;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Journal organization is independent of task completion and preserves existing entries. */
public final class JournalRepository {
    private final SQLiteDatabase db;
    public JournalRepository(Store store) { db = store.getWritableDatabase(); }
    public static void migrate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE journal_groups(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL COLLATE NOCASE UNIQUE CHECK(length(trim(name)) BETWEEN 1 AND 40))");
        db.execSQL("ALTER TABLE journal ADD COLUMN group_id INTEGER REFERENCES journal_groups(id) ON DELETE SET NULL");
        db.execSQL("ALTER TABLE journal ADD COLUMN kind TEXT NOT NULL DEFAULT 'Journal' CHECK(kind IN ('Journal','Advice','Project log'))");
        db.execSQL("ALTER TABLE journal ADD COLUMN project_title TEXT NOT NULL DEFAULT ''");
        db.execSQL("CREATE INDEX journal_group_created ON journal(group_id,created)");
        db.execSQL("UPDATE journal SET project_title=COALESCE((SELECT projects.title FROM projects JOIN items ON items.project=projects.id WHERE items.id=journal.item),'')");
    }
    public static final class Group {
        public final long id; public final String name;
        Group(long id, String name) { this.id=id; this.name=name; }
        @Override public String toString() { return name; }
    }
    public static final class Entry {
        public long id, groupId, created;
        public String title="", date=LocalDate.now().toString(), progress="", lessons="", next="", kind="Journal", project="";
    }
    public List<Group> groups() {
        List<Group> out = new ArrayList<>();
        try (Cursor c=db.rawQuery("SELECT id,name FROM journal_groups ORDER BY name COLLATE NOCASE",null)) {
            while(c.moveToNext()) out.add(new Group(c.getLong(0),c.getString(1)));
        }
        return out;
    }
    public long saveGroup(long id,String name) {
        name=name.trim();
        if(name.isEmpty() || name.length()>40) throw new IllegalArgumentException("Use 1–40 characters for the group name.");
        ContentValues v=new ContentValues();v.put("name",name);
        if(id==0) return db.insertOrThrow("journal_groups",null,v);
        db.update("journal_groups",v,"id=?",new String[]{String.valueOf(id)});return id;
    }
    public void deleteGroup(long id) { db.delete("journal_groups","id=?",new String[]{String.valueOf(id)}); }
    public void move(long id,long group) {
        ContentValues v=new ContentValues();if(group==0)v.putNull("group_id");else v.put("group_id",group);
        db.update("journal",v,"id=?",new String[]{String.valueOf(id)});
    }
    public List<Entry> entries(long group,String search) {
        // -1 = All; 0 = Ungrouped. Search uses bound parameters and literal LIKE escapes.
        String where=group<0?"1=1":group==0?"group_id IS NULL":"group_id=?";
        List<String> args=new ArrayList<>();if(group>0)args.add(String.valueOf(group));
        if(search!=null && !search.trim().isEmpty()) {
            where+=" AND (title || ' ' || progress || ' ' || obstacle || ' ' || next || ' ' || project_title) LIKE ? ESCAPE '\\'";
            args.add("%"+search.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%");
        }
        List<Entry> out=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT id,title,period,progress,obstacle,next,group_id,kind,project_title,created FROM journal WHERE "+where+" ORDER BY created DESC,id DESC",args.toArray(new String[0]))) {
            while(c.moveToNext()) {
                Entry e=new Entry();e.id=c.getLong(0);e.title=c.getString(1);e.date=c.getString(2);e.progress=c.getString(3);e.lessons=c.getString(4);e.next=c.getString(5);e.groupId=c.getLong(6);e.kind=c.getString(7);e.project=c.getString(8);e.created=c.getLong(9);out.add(e);
            }
        }
        return out;
    }
    public long save(Entry e) {
        if(e.title.trim().isEmpty() || e.title.length()>200)throw new IllegalArgumentException("Add a title of 1–200 characters.");
        if(e.progress.trim().isEmpty())throw new IllegalArgumentException("Add a note, advice, or progress before saving.");
        if(e.progress.length()>20000 || e.lessons.length()>20000 || e.next.length()>20000 || e.project.length()>200)throw new IllegalArgumentException("Keep each note under 20,001 characters and project names under 201.");
        ContentValues v=new ContentValues();v.put("title",e.title.trim());v.put("period",e.date);v.put("progress",e.progress.trim());v.put("obstacle",e.lessons.trim());v.put("next",e.next.trim());v.put("kind",e.kind);v.put("project_title",e.project.trim());
        if(e.groupId==0)v.putNull("group_id");else v.put("group_id",e.groupId);
        if(e.id==0) {v.put("created",System.currentTimeMillis());e.id=db.insertOrThrow("journal",null,v);}
        else db.update("journal",v,"id=?",new String[]{String.valueOf(e.id)});
        return e.id;
    }
}
