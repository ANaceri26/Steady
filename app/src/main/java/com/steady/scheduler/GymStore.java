package com.steady.scheduler;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class GymStore {
    private final Store store;

    public static class Exercise {
        long id;
        String name;
        String unit;
    }

    public static class Routine {
        long id;
        String name;
    }

    public static class Session {
        String date;
        long id;
        String name;
        String notes;
        long routine;
    }

    public static class SetRow {
        long exercise;
        String name;
        int position;
        int reps;
        String unit;
        double weight;
    }

    public GymStore(Context c) {
        this.store = Store.get(c);
    }

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE gym_routines(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL)");
        db.execSQL("CREATE TABLE gym_exercises(id INTEGER PRIMARY KEY AUTOINCREMENT,routine INTEGER NOT NULL REFERENCES gym_routines(id) ON DELETE CASCADE,name TEXT NOT NULL,unit TEXT NOT NULL DEFAULT 'kg')");
        db.execSQL("CREATE TABLE gym_sessions(id INTEGER PRIMARY KEY AUTOINCREMENT,routine INTEGER REFERENCES gym_routines(id) ON DELETE SET NULL,name TEXT NOT NULL,date TEXT NOT NULL,notes TEXT NOT NULL)");
        db.execSQL("CREATE TABLE gym_sets(id INTEGER PRIMARY KEY AUTOINCREMENT,session INTEGER NOT NULL REFERENCES gym_sessions(id) ON DELETE CASCADE,exercise INTEGER REFERENCES gym_exercises(id) ON DELETE SET NULL,name TEXT NOT NULL,position INTEGER NOT NULL,reps INTEGER NOT NULL CHECK(reps>0),weight REAL NOT NULL CHECK(weight>=0),unit TEXT NOT NULL)");
        db.execSQL("CREATE INDEX gym_session_dates ON gym_sessions(date)");
    }

    public List<Routine> routines() {
        List<Routine> out = new ArrayList<>();
        Cursor c = this.store.getReadableDatabase().rawQuery("SELECT id,name FROM gym_routines ORDER BY id", null);
        while (c.moveToNext()) {
            try {
                Routine r = new Routine();
                r.id = c.getLong(0);
                r.name = c.getString(1);
                out.add(r);
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

    public long routine(long id, String name) {
        ContentValues v = new ContentValues();
        v.put("name", name);
        if (id == 0) {
            return this.store.getWritableDatabase().insertOrThrow("gym_routines", null, v);
        }
        this.store.getWritableDatabase().update("gym_routines", v, "id=?", new String[]{"" + id});
        return id;
    }

    public void deleteRoutine(long id) {
        this.store.getWritableDatabase().delete("gym_routines", "id=?", new String[]{"" + id});
    }

    public List<Exercise> exercises(long routine) {
        List<Exercise> out = new ArrayList<>();
        Cursor c = this.store.getReadableDatabase().rawQuery("SELECT id,name,unit FROM gym_exercises WHERE routine=? ORDER BY id", new String[]{"" + routine});
        while (c.moveToNext()) {
            try {
                Exercise e = new Exercise();
                e.id = c.getLong(0);
                e.name = c.getString(1);
                e.unit = c.getString(2);
                out.add(e);
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

    public void exercise(long id, long routine, String name, String unit) {
        ContentValues v = new ContentValues();
        v.put("routine", Long.valueOf(routine));
        v.put("name", name);
        v.put("unit", unit);
        if (id == 0) {
            this.store.getWritableDatabase().insertOrThrow("gym_exercises", null, v);
        } else {
            this.store.getWritableDatabase().update("gym_exercises", v, "id=?", new String[]{"" + id});
        }
    }

    public void deleteExercise(long id) {
        this.store.getWritableDatabase().delete("gym_exercises", "id=?", new String[]{"" + id});
    }

    public List<Session> sessions(LocalDate from, LocalDate to) {
        List<Session> out = new ArrayList<>();
        Cursor c = this.store.getReadableDatabase().rawQuery("SELECT id,routine,name,date,notes FROM gym_sessions WHERE date>=? AND date<=? ORDER BY date DESC,id DESC", new String[]{from.toString(), to.toString()});
        while (c.moveToNext()) {
            try {
                Session s = new Session();
                s.id = c.getLong(0);
                s.routine = c.getLong(1);
                s.name = c.getString(2);
                s.date = c.getString(3);
                s.notes = c.getString(4);
                out.add(s);
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

    public List<SetRow> sets(long session) {
        List<SetRow> out = new ArrayList<>();
        Cursor c = this.store.getReadableDatabase().rawQuery("SELECT exercise,name,position,reps,weight,unit FROM gym_sets WHERE session=? ORDER BY id", new String[]{"" + session});
        while (c.moveToNext()) {
            try {
                SetRow r = new SetRow();
                r.exercise = c.getLong(0);
                r.name = c.getString(1);
                r.position = c.getInt(2);
                r.reps = c.getInt(3);
                r.weight = c.getDouble(4);
                r.unit = c.getString(5);
                out.add(r);
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

    public long save(Session session, List<SetRow> sets) {
        if (LocalDate.parse(session.date).isAfter(LocalDate.now()) || sets.isEmpty()) {
            throw new IllegalArgumentException("Log at least one set for today or an earlier date.");
        }
        for (SetRow r : sets) {
            if (!GymRules.validSet(r.reps, r.weight) || (!r.unit.equals("kg") && !r.unit.equals("lb"))) {
                throw new IllegalArgumentException("Check reps and weight.");
            }
        }
        SQLiteDatabase db = this.store.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            if (session.routine == 0) {
                v.putNull("routine");
            } else {
                v.put("routine", Long.valueOf(session.routine));
            }
            v.put("name", session.name);
            v.put("date", session.date);
            v.put("notes", session.notes);
            long id = session.id;
            if (id == 0) {
                id = db.insertOrThrow("gym_sessions", null, v);
            } else {
                db.update("gym_sessions", v, "id=?", new String[]{"" + id});
                db.delete("gym_sets", "session=?", new String[]{"" + id});
            }
            for (SetRow r2 : sets) {
                ContentValues s = new ContentValues();
                s.put("session", Long.valueOf(id));
                if (r2.exercise == 0) {
                    s.putNull("exercise");
                } else {
                    s.put("exercise", Long.valueOf(r2.exercise));
                }
                s.put("name", r2.name);
                s.put("position", Integer.valueOf(r2.position));
                s.put("reps", Integer.valueOf(r2.reps));
                s.put("weight", Double.valueOf(r2.weight));
                s.put("unit", r2.unit);
                db.insertOrThrow("gym_sets", null, s);
            }
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public void deleteSession(long id) {
        this.store.getWritableDatabase().delete("gym_sessions", "id=?", new String[]{"" + id});
    }

    public List<String> history(long exercise, LocalDate until) {
        List<String> out = new ArrayList<>();
        Cursor c = this.store.getReadableDatabase().rawQuery("SELECT s.date,s.name,g.reps,g.weight,g.unit,g.position FROM gym_sets g JOIN gym_sessions s ON s.id=g.session WHERE g.exercise=? AND s.date<=? ORDER BY s.date DESC,s.id DESC,g.position LIMIT 100", new String[]{"" + exercise, until.toString()});
        while (c.moveToNext()) {
            try {
                out.add(c.getString(0) + " · " + c.getString(1) + " · Set " + c.getInt(5) + ": " + GymRules.number(c.getDouble(3)) + " " + c.getString(4) + " × " + c.getInt(2));
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
