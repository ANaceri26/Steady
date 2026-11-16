package com.steady.scheduler;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

public final class GymRules {
    private GymRules() {
    }

    public static LocalDate monday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public static boolean validSet(int reps, double weight) {
        return reps > 0 && reps <= 10000 && Double.isFinite(weight) && weight >= 0.0d && weight <= 10000.0d;
    }

    public static String number(double value) {
        return value == ((double) ((long) value)) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", Double.valueOf(value)).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
