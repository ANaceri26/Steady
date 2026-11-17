package com.steady.scheduler;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.*;

/** Versioned JSON and media, independent of Android database files. */
public final class PortableBackup {
    static final String[] TABLES = {"projects", "items", "entries", "journal_groups", "journal",
            "photos", "journal_photos", "gym_routines", "gym_exercises", "gym_sessions", "gym_sets", "snoozes"};
    static final long MAX_BYTES = 256L * 1024 * 1024;
    static final int MAX_JSON = 16 * 1024 * 1024, MAX_FILES = 10000;
    private PortableBackup() {}

    public static void write(Store store, File files, OutputStream output) throws Exception {
        JSONObject document = new JSONObject().put("format", "steady-backup").put("version", 1);
        JSONObject tables = new JSONObject();
        Map<String, File> media = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        SQLiteDatabase db = store.getWritableDatabase();
        db.beginTransaction();
        try {
            for (String table : TABLES) {
                JSONArray rows = new JSONArray();
                try (Cursor c = db.rawQuery("SELECT * FROM " + table, null)) {
                    while (c.moveToNext()) {
                        JSONObject row = new JSONObject();
                        for (int i = 0; i < c.getColumnCount(); i++) {
                            String key = c.getColumnName(i);
                            Object value = c.isNull(i) ? JSONObject.NULL : c.getType(i) == Cursor.FIELD_TYPE_INTEGER
                                    ? c.getLong(i) : c.getType(i) == Cursor.FIELD_TYPE_FLOAT ? c.getDouble(i) : c.getString(i);
                            if (key.equals("path")) {
                                String path = c.getString(i);
                                if (!names.containsKey(path)) {
                                    File file = new File(files, path).getCanonicalFile();
                                    if (!file.toPath().startsWith(files.getCanonicalFile().toPath()) || !file.isFile())
                                        throw new IOException("A photo is missing or unreadable.");
                                    String name = "media/" + (names.size() + 1) + ".jpg";
                                    names.put(path, name); media.put(name, file);
                                }
                                value = names.get(path);
                            }
                            row.put(key, value);
                        }
                        rows.put(row);
                    }
                }
                tables.put(table, rows);
            }
            document.put("data", tables);
            byte[] json = document.toString().getBytes(StandardCharsets.UTF_8);
            long total = json.length;
            for (File photo : media.values()) total += photo.length();
            if (json.length > MAX_JSON || total > MAX_BYTES || media.size() >= MAX_FILES)
                throw new IOException("Backup exceeds the supported size.");
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                ZipEntry manifest = new ZipEntry("steady.json"); manifest.setTime(0); zip.putNextEntry(manifest); zip.write(json); zip.closeEntry();
                for (Map.Entry<String, File> photo : media.entrySet()) {
                    ZipEntry entry = new ZipEntry(photo.getKey()); entry.setTime(0); zip.putNextEntry(entry);
                    Files.copy(photo.getValue().toPath(), zip); zip.closeEntry();
                }
            }
        } finally { db.endTransaction(); }
    }

    public static void restore(Store store, File files, InputStream input) throws Exception {
        File stage = new File(files, "transfer-" + UUID.randomUUID());
        if (!stage.mkdir()) throw new IOException("Cannot prepare backup.");
        boolean committed = false;
        try {
            Set<String> names = new HashSet<>();
            byte[] json = null, buffer = new byte[8192];
            long total = 0;
            try (ZipInputStream zip = new ZipInputStream(input)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if ((!name.equals("steady.json") && !name.matches("media/[1-9][0-9]*\\.jpg"))
                            || entry.isDirectory() || !names.add(name) || names.size() > MAX_FILES)
                        throw new IOException("Invalid backup file names.");
                    ByteArrayOutputStream text = name.equals("steady.json") ? new ByteArrayOutputStream() : null;
                    File destination = new File(stage, name.substring(name.lastIndexOf('/') + 1));
                    try (OutputStream out = text != null ? text : new FileOutputStream(destination)) {
                        int n; long size = 0;
                        while ((n = zip.read(buffer)) != -1) {
                            total += n; size += n;
                            if (total > MAX_BYTES || (text != null && size > MAX_JSON)) throw new IOException("Backup is too large.");
                            out.write(buffer, 0, n);
                        }
                    }
                    if (text != null) json = text.toByteArray();
                    zip.closeEntry();
                }
            }
            if (json == null) throw new IOException("This is not a Steady backup.");
            JSONObject document = new JSONObject(new String(json, StandardCharsets.UTF_8));
            if (!"steady-backup".equals(document.getString("format")) || !Integer.valueOf(1).equals(document.get("version")))
                throw new IOException("Unsupported backup version.");
            JSONObject data = document.getJSONObject("data");
            if (data.length() != TABLES.length) throw new IOException("Incomplete backup.");
            // Validate in an isolated database before touching any existing data.
            try (SQLiteDatabase validation = SQLiteDatabase.create(null)) {
                validation.setForeignKeyConstraintsEnabled(true);
                store.onCreate(validation);
                insert(validation, data, stage, names);
                try (Cursor c = validation.rawQuery("PRAGMA foreign_key_check", null)) {
                    if (c.moveToFirst()) throw new IOException("Broken backup references.");
                }
                try (Cursor c = validation.rawQuery("SELECT start,minute,repeat,goal,scheduled,enabled FROM items", null)) {
                    while (c.moveToNext()) {
                        LocalDate.parse(c.getString(0));
                        if (c.getInt(1) < 0 || c.getInt(1) > 1439 || c.getInt(2) < 0 || c.getInt(2) > 3)
                            throw new IOException("Invalid task schedule.");
                        for (int i = 3; i < 6; i++) if (c.getInt(i) < 0 || c.getInt(i) > 1) throw new IOException("Invalid task state.");
                    }
                }
                try (Cursor c = validation.rawQuery("SELECT date FROM gym_sessions", null)) {
                    while (c.moveToNext()) LocalDate.parse(c.getString(0));
                }
            }
            SQLiteDatabase db = store.getWritableDatabase();
            db.beginTransaction();
            try {
                for (int i = TABLES.length - 1; i >= 0; i--) db.delete(TABLES[i], null, null);
                insert(db, data, stage, names);
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
            committed = true;
        } finally {
            if (!committed) {
                File[] children = stage.listFiles();
                if (children != null) for (File child : children) child.delete();
                stage.delete();
            }
        }
    }

    private static void insert(SQLiteDatabase db, JSONObject data, File stage, Set<String> names) throws Exception {
        for (String table : TABLES) {
            Map<String, String> columns = new LinkedHashMap<>();
            try (Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
                while (c.moveToNext()) columns.put(c.getString(1), c.getString(2));
            }
            JSONArray rows = data.getJSONArray(table);
            if (rows.length() > 100000) throw new IOException("Too many records.");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (row.length() != columns.size()) throw new IOException("Invalid record fields.");
                ContentValues values = new ContentValues();
                for (Map.Entry<String, String> column : columns.entrySet()) {
                    String key = column.getKey(); Object value = row.get(key);
                    if (value == JSONObject.NULL) values.putNull(key);
                    else if (column.getValue().equals("INTEGER") && (value instanceof Integer || value instanceof Long)) values.put(key, ((Number)value).longValue());
                    else if (column.getValue().equals("REAL") && value instanceof Number) values.put(key, ((Number)value).doubleValue());
                    else if (column.getValue().equals("TEXT") && value instanceof String) {
                        String text = (String)value;
                        if (key.equals("path")) {
                            if (!text.startsWith("media/") || !names.contains(text)) throw new IOException("Missing backup photo.");
                            text = stage.getName() + "/" + text.substring(6);
                        }
                        values.put(key, text);
                    } else throw new IOException("Invalid record value.");
                }
                db.insertOrThrow(table, null, values);
            }
        }
    }
}
