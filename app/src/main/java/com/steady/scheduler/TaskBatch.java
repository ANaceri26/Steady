package com.steady.scheduler;

import java.util.ArrayList;
import java.util.List;

/** Validates the entire batch before anything is saved. */
public final class TaskBatch {
    public static final int MAX_TASKS = 25;
    public static List<String> parse(String text) {
        if (text == null || text.length() > 30000) throw new IllegalArgumentException("Paste up to 25 tasks (30,000 characters maximum).");
        List<String> names = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String name = line.trim().replaceFirst("^(?:[•*\\-]|\\d+[.)])\\s*", "").trim();
            if (name.isEmpty()) continue;
            if (name.length() > 200) throw new IllegalArgumentException("Keep each task title under 201 characters.");
            names.add(name);
            if (names.size() > MAX_TASKS) throw new IllegalArgumentException("Add up to 25 tasks at a time.");
        }
        if (names.isEmpty()) throw new IllegalArgumentException("Paste at least one task.");
        return names;
    }
    public static void validateTime(int minute) {
        if (minute < 0 || minute >= 1440) throw new IllegalArgumentException("Choose a valid time.");
    }
    private TaskBatch() {}
}
