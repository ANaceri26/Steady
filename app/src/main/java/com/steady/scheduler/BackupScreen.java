package com.steady.scheduler;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.io.*;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

/** System document picker supports local files and USB without an account or connection. */
final class BackupScreen {
    static final int SAVE = 60, OPEN = 61;
    static void add(Activity activity, UI ui, LinearLayout body) {
        LinearLayout card = ui.card(body);
        card.addView(ui.bold("Offline backup & transfer", 20, UI.TEXT));
        card.addView(ui.text("Save tasks, goals, projects, journals, photos and workouts in one portable file. Copy it by USB or local file sharing, then import it on another device. Web and iOS support will be added with those apps. This is a manual transfer, not automatic sync.", 14, UI.MUTED));
        ui.action(card, "Save full backup", true, () -> activity.startActivityForResult(
                new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/zip").putExtra(Intent.EXTRA_TITLE, "Steady-Backup-" + LocalDate.now() + ".zip"), SAVE));
        ui.action(card, "Import backup", false, () -> activity.startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), OPEN));
        card.addView(ui.text("Backup files contain your private notes and photos and are not encrypted. Alarm sounds and phone permissions stay on each device. A journal export is for reading; use a full backup to transfer data.", 13, UI.MUTED));
    }

    static void handle(Activity activity, Store store, int request, Uri uri, Runnable refreshed) {
        if (request == OPEN) {
            new AlertDialog.Builder(activity).setTitle("Replace this device’s data?")
                    .setMessage("Importing replaces all tasks, goals, projects, journals and workouts in this Steady app. It does not merge them. Save a full backup of this device first if you want to keep its data. The selected file is checked before replacement.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Replace and import", (dialog, which) -> run(activity, store, request, uri, refreshed)).show();
        } else run(activity, store, request, uri, refreshed);
    }

    private static void run(Activity activity, Store store, int request, Uri uri, Runnable refreshed) {
        ProgressDialog progress = new ProgressDialog(activity);
        progress.setMessage(request == SAVE ? "Saving full backup…" : "Checking and importing backup…");
        progress.setCancelable(false); progress.show();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            String message;
            boolean restored = false;
            try {
                if (request == SAVE) {
                    try (OutputStream out = activity.getContentResolver().openOutputStream(uri, "wt")) {
                        if (out == null) throw new IOException();
                        PortableBackup.write(store, activity.getFilesDir(), out);
                    }
                    message = "Backup saved. Copy this file to your other device.";
                } else {
                    List<Item> oldItems = store.items();
                    List<String[]> oldSnoozes = store.snoozes();
                    try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                        if (in == null) throw new IOException();
                        PortableBackup.restore(store, activity.getFilesDir(), in);
                    }
                    restored = true;
                    message = "Backup imported. Check reminder permissions on this device.";
                    try {
                        Alarms.cancelTransferred(activity, oldItems, oldSnoozes);
                        Alarms.restore(activity);
                    } catch (Exception alarmError) {
                        message = "Data imported. Reopen Steady and check reminder setup.";
                    }
                }
            } catch (Exception error) {
                message = request == SAVE ? "Backup could not be saved. Check storage space and photo availability."
                        : "Import failed. Your existing data was kept. Choose a valid Steady full backup (up to 256 MB).";
            }
            final String result = message;
            final boolean changed = restored;
            activity.runOnUiThread(() -> {
                if (activity.isDestroyed()) return;
                progress.dismiss();
                if (changed) refreshed.run();
                Toast.makeText(activity, result, Toast.LENGTH_LONG).show();
            });
        });
        worker.shutdown();
    }
}
