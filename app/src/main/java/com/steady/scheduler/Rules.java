package com.steady.scheduler;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

public final class Rules {
    public static final int DAILY = 1;
    public static final int MONTHLY = 3;
    public static final int ONCE = 0;
    public static final int WEEKLY = 2;

    private Rules() {
    }

    public static boolean due(LocalDate date, LocalDate start, int repeat) {
        if (date.isBefore(start)) {
            return false;
        }
        switch (repeat) {
            case 1:
                return true;
            case 2:
                return date.getDayOfWeek() == start.getDayOfWeek();
            case 3:
                return date.getDayOfMonth() == Math.min(start.getDayOfMonth(), date.lengthOfMonth());
            default:
                return date.equals(start);
        }
    }

    public static String key(LocalDate date, int repeat, boolean goal) {
        if (goal && repeat == 2) {
            date = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        if (goal && repeat == 3) {
            date = date.withDayOfMonth(1);
        }
        return date.toString();
    }

    public static ZonedDateTime next(LocalDate start, int minute, int repeat, ZonedDateTime now) {
        LocalDate date = now.toLocalDate().isAfter(start) ? now.toLocalDate() : start;
        int i = 0;
        while (i < 370) {
            if (due(date, start, repeat)) {
                ZonedDateTime AtZone = date.atTime(minute / 60, minute % 60).atZone(now.getZone());
                if (AtZone.toInstant().isAfter(now.toInstant())) {
                    return AtZone;
                }
                if (repeat == 0) {
                    return null;
                }
            }
            i++;
            date = date.plusDays(1L);
        }
        return null;
    }

    public static int streak(Set<String> completed, LocalDate today) {
        int count = 0;
        for (LocalDate cursor = completed.contains(today.toString()) ? today : today.minusDays(1L); completed.contains(cursor.toString()); cursor = cursor.minusDays(1L)) {
            count++;
        }
        return count;
    }

    public static String repeatLabel(int repeat) {
        return new String[]{"Once", "Daily", "Weekly", "Monthly"}[repeat];
    }
}
