package com.steady.scheduler;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

public final class GymScreen {
    private final Activity a;
    private final Runnable redraw;
    private final GymStore store;
    private final UI ui;
    public LocalDate week = GymRules.monday(LocalDate.now());

    public GymScreen(Activity a, UI ui, Runnable redraw) {
        this.a = a;
        this.ui = ui;
        this.store = new GymStore(a);
        this.redraw = redraw;
    }

    public void toast(String s) {
        Toast.makeText(this.a, s, Toast.LENGTH_LONG).show();
    }

    private LinearLayout form() {
        LinearLayout f = this.ui.col();
        f.setPadding(this.ui.dp(20.0f), this.ui.dp(12.0f), this.ui.dp(20.0f), this.ui.dp(16.0f));
        return f;
    }

    private AlertDialog dialog(String title, LinearLayout f, String button) {
        ScrollView scroll = new ScrollView(this.a);
        scroll.addView(f);
        AlertDialog d = new AlertDialog.Builder(this.a).setTitle(title).setView(scroll).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).setPositiveButton(button, (DialogInterface.OnClickListener) null).create();
        d.show();
        return d;
    }

    private void confirm(String message, final Runnable r) {
        new AlertDialog.Builder(this.a).setMessage(message).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).setPositiveButton("Delete", new DialogInterface.OnClickListener() {
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                r.run();
            }
        }).show();
    }

    public void render(LinearLayout body) {
        int i;
        this.ui.eyebrow(body, "SHOW UP. BUILD ON IT.");
        body.addView(this.ui.bold("A little stronger.", 32, UI.TEXT));
        this.ui.space(body, 16);
        this.ui.primary(body, "+ Log workout", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                GymScreen.this.lambda$render$1();
            }
        });
        this.ui.space(body, 24);
        LinearLayout header = this.ui.row();
        body.addView(header);
        this.ui.weight(header, this.ui.link("‹", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                GymScreen.this.lambda$render$2();
            }
        }));
        TextView dates = this.ui.bold(this.week.format(DateTimeFormatter.ofPattern("MMM d")) + " – " + this.week.plusDays(6L).format(DateTimeFormatter.ofPattern("MMM d, yyyy")), 15, UI.TEXT);
        dates.setGravity(17);
        header.addView(dates);
        this.ui.weight(header, this.ui.link("›", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                GymScreen.this.lambda$render$3();
            }
        }));
        List<GymStore.Session> sessions = this.store.sessions(this.week, this.week.plusDays(6L));
        Set<String> days = new HashSet<>();
        Iterator<GymStore.Session> it = sessions.iterator();
        while (it.hasNext()) {
            days.add(it.next().date);
        }
        LinearLayout linearLayoutCard = this.ui.card(body);
        linearLayoutCard.addView(this.ui.bold(days.size() + " / 7 days · " + sessions.size() + (sessions.size() == 1 ? " workout" : " workouts"), 22, UI.TEXT));
        int i2 = 14;
        this.ui.space(linearLayoutCard, 14);
        int n = 0;
        while (n < 7) {
            TextView dates2 = dates;
            final LocalDate date = this.week.plusDays(n);
            List<String> names = new ArrayList<>();
            for (GymStore.Session s : sessions) {
                if (s.date.equals(date.toString())) {
                    names.add(s.name);
                }
            }
            LinearLayout row = this.ui.row();
            row.setMinimumHeight(this.ui.dp(48.0f));
            linearLayoutCard.addView(row);
            TextView label = this.ui.bold(date.format(DateTimeFormatter.ofPattern("EEE d")), 13, date.equals(LocalDate.now()) ? UI.GREEN : UI.MUTED);
            row.addView(label, new LinearLayout.LayoutParams(this.ui.dp(64.0f), -2));
            this.ui.weight(row, this.ui.text(names.isEmpty() ? date.isAfter(LocalDate.now()) ? "Upcoming" : "No workout logged" : String.join(" + ", names), i2, names.isEmpty() ? UI.MUTED : UI.TEXT));
            if (!date.isAfter(LocalDate.now())) {
                Button plus = this.ui.link("+", new Runnable() {
                    @Override // java.lang.Runnable
                    public final void run() {
                        GymScreen.this.lambda$render$4(date);
                    }
                });
                plus.setContentDescription("Log workout on " + date);
                row.addView(plus, new LinearLayout.LayoutParams(this.ui.dp(48.0f), this.ui.dp(48.0f)));
            }
            n++;
            dates = dates2;
            i2 = 14;
        }
        body.addView(this.ui.link("Back to this week", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                GymScreen.this.lambda$render$5();
            }
        }));
        this.ui.space(body, 18);
        LinearLayout heading = this.ui.row();
        body.addView(heading);
        this.ui.weight(heading, this.ui.bold("Your workout days", 22, UI.TEXT));
        heading.addView(this.ui.link("+ New", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                GymScreen.this.lambda$render$6();
            }
        }));
        if (this.store.routines().isEmpty()) {
            LinearLayout c = this.ui.card(body);
            c.addView(this.ui.text("Create your own days, like Push, Pull and Legs. Add exercises, then record the weight and reps for every set.", 15, UI.MUTED));
            this.ui.action(c, "Create Push / Pull / Legs", true, new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    GymScreen.this.lambda$render$7();
                }
            });
        }
        Iterator<GymStore.Routine> it2 = this.store.routines().iterator();
        while (true) {
            i = 20;
            if (!it2.hasNext()) {
                break;
            }
            final GymStore.Routine r = it2.next();
            LinearLayout c2 = this.ui.card(body);
            c2.addView(this.ui.bold(r.name, 20, UI.TEXT));
            c2.addView(this.ui.text(this.store.exercises(r.id).size() + " exercises", 13, UI.MUTED));
            LinearLayout row2 = this.ui.row();
            c2.addView(row2);
            this.ui.weight(row2, this.ui.link("Exercises & progress", new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    GymScreen.this.lambda$render$8(r);
                }
            }));
            this.ui.weight(row2, this.ui.link("Log", new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    GymScreen.this.lambda$render$9(r);
                }
            }));
        }
        this.ui.space(body, 16);
        body.addView(this.ui.bold("This week’s sessions", 22, UI.TEXT));
        this.ui.space(body, 12);
        if (sessions.isEmpty()) {
            body.addView(this.ui.text("Your logged workouts will appear here.", 15, UI.MUTED));
        }
        for (final GymStore.Session s2 : sessions) {
            LinearLayout c3 = this.ui.card(body);
            c3.addView(this.ui.bold(s2.name, i, UI.TEXT));
            c3.addView(this.ui.text(s2.date + " · " + this.store.sets(s2.id).size() + " sets", 13, UI.GREEN));
            this.ui.action(c3, "View workout", false, new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    GymScreen.this.lambda$render$10(s2);
                }
            });
            days = days;
            i = 20;
        }
    }

    public /* synthetic */ void lambda$render$1() {
        lambda$render$4(LocalDate.now());
    }

    public /* synthetic */ void lambda$render$2() {
        this.week = this.week.minusWeeks(1L);
        this.redraw.run();
    }

    public /* synthetic */ void lambda$render$3() {
        this.week = this.week.plusWeeks(1L);
        this.redraw.run();
    }

    public /* synthetic */ void lambda$render$5() {
        this.week = GymRules.monday(LocalDate.now());
        this.redraw.run();
    }

    public /* synthetic */ void lambda$render$6() {
        editRoutine(null);
    }

    public /* synthetic */ void lambda$render$7() {
        String[] strArr = {"Push", "Pull", "Legs"};
        for (int i = 0; i < 3; i++) {
            String name = strArr[i];
            this.store.routine(0L, name);
        }
        this.redraw.run();
    }

    public /* synthetic */ void lambda$render$9(GymStore.Routine r) {
        log(r, LocalDate.now(), null);
    }

    public void lambda$render$4(final LocalDate date) {
        final List<GymStore.Routine> routines = this.store.routines();
        if (routines.isEmpty()) {
            editRoutine(null);
        } else {
            String[] names = (String[]) routines.stream().map(new Function() {
                @Override // java.util.function.Function
                public final Object apply(Object obj) {
                    return ((GymStore.Routine) obj).name;
                }
            }).toArray(new IntFunction() {
                @Override // java.util.function.IntFunction
                public final Object apply(int i) {
                    return GymScreen.lambda$chooseWorkout$12(i);
                }
            });
            new AlertDialog.Builder(this.a).setTitle("Workout · " + date).setItems(names, new DialogInterface.OnClickListener() {
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    GymScreen.this.lambda$chooseWorkout$13(routines, date, dialogInterface, i);
                }
            }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
        }
    }

    static /* synthetic */ String[] lambda$chooseWorkout$12(int x$0) {
        return new String[x$0];
    }

    public /* synthetic */ void lambda$chooseWorkout$13(List routines, LocalDate date, DialogInterface d, int w) {
        log((GymStore.Routine) routines.get(w), date, null);
    }

    private void editRoutine(final GymStore.Routine routine) {
        LinearLayout f = form();
        final EditText name = this.ui.input(f, "Day name · e.g. Push", routine == null ? "" : routine.name, false);
        final AlertDialog d = dialog(routine == null ? "New workout day" : "Rename workout day", f, "Save");
        d.getButton(-1).setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                GymScreen.this.lambda$editRoutine$14(name, routine, d, view);
            }
        });
    }

    public /* synthetic */ void lambda$editRoutine$14(EditText name, GymStore.Routine routine, AlertDialog d, View v) {
        String value = name.getText().toString().trim();
        if (value.isEmpty()) {
            name.setError("Add a name");
            return;
        }
        long id = this.store.routine(routine == null ? 0L : routine.id, value);
        d.dismiss();
        this.redraw.run();
        if (routine == null) {
            GymStore.Routine r = new GymStore.Routine();
            r.id = id;
            r.name = value;
            lambda$render$8(r);
        }
    }

    public void lambda$render$8(final GymStore.Routine routine) {
        LinearLayout f = form();
        f.addView(this.ui.text("Keep this template for future workouts. Previous sessions keep their original names and recorded sets.", 14, UI.MUTED));
        final AlertDialog d = dialog(routine.name, f, "Close");
        d.getButton(-1).setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                d.dismiss();
            }
        });
        for (final GymStore.Exercise e : this.store.exercises(routine.id)) {
            LinearLayout c = this.ui.card(f);
            c.addView(this.ui.bold(e.name, 18, UI.TEXT));
            List<String> history = this.store.history(e.id, LocalDate.now());
            c.addView(this.ui.text(history.isEmpty() ? "No sets logged yet" : history.get(0), 13, UI.MUTED));
            this.ui.action(c, "History & progress", false, new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    GymScreen.this.lambda$routine$16(e);
                }
            });
            this.ui.action(c, "Edit exercise", false, new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    GymScreen.this.lambda$routine$17(d, routine, e);
                }
            });
        }
        this.ui.action(f, "+ Add exercise", true, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                GymScreen.this.lambda$routine$18(d, routine);
            }
        });
        this.ui.action(f, "Rename workout day", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                GymScreen.this.lambda$routine$19(d, routine);
            }
        });
        this.ui.action(f, "Delete workout day", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                GymScreen.this.lambda$routine$21(routine, d);
            }
        });
    }

    public /* synthetic */ void lambda$routine$17(AlertDialog d, GymStore.Routine routine, GymStore.Exercise e) {
        d.dismiss();
        exercise(routine, e);
    }

    public /* synthetic */ void lambda$routine$18(AlertDialog d, GymStore.Routine routine) {
        d.dismiss();
        exercise(routine, null);
    }

    public /* synthetic */ void lambda$routine$19(AlertDialog d, GymStore.Routine routine) {
        d.dismiss();
        editRoutine(routine);
    }

    public /* synthetic */ void lambda$routine$20(GymStore.Routine routine, AlertDialog d) {
        this.store.deleteRoutine(routine.id);
        d.dismiss();
        this.redraw.run();
    }

    public /* synthetic */ void lambda$routine$21(final GymStore.Routine routine, final AlertDialog d) {
        confirm("Delete this template? Logged workouts stay in your history.", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                GymScreen.this.lambda$routine$20(routine, d);
            }
        });
    }

    private void exercise(final GymStore.Routine routine, final GymStore.Exercise exercise) {
        LinearLayout f = form();
        final EditText name = this.ui.input(f, "Exercise", exercise == null ? "" : exercise.name, false);
        f.addView(this.ui.text("Weight unit", 13, UI.MUTED));
        final Spinner unit = unitSpinner(exercise == null ? "kg" : exercise.unit);
        f.addView(unit);
        f.addView(this.ui.text("Use 0 for unweighted/bodyweight sets. Record the same weight convention each workout (for example, per dumbbell).", 13, UI.MUTED));
        final AlertDialog d = dialog(exercise == null ? "Add exercise" : "Edit exercise", f, "Save");
        d.getButton(-1).setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                GymScreen.this.lambda$exercise$22(name, exercise, routine, unit, d, view);
            }
        });
        if (exercise != null) {
            this.ui.action(f, "Delete exercise", false, new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    GymScreen.this.lambda$exercise$24(exercise, d, routine);
                }
            });
        }
    }

    public /* synthetic */ void lambda$exercise$22(EditText name, GymStore.Exercise exercise, GymStore.Routine routine, Spinner unit, AlertDialog d, View v) {
        String value = name.getText().toString().trim();
        if (value.isEmpty()) {
            name.setError("Add an exercise name");
            return;
        }
        this.store.exercise(exercise == null ? 0L : exercise.id, routine.id, value, unit.getSelectedItem().toString());
        d.dismiss();
        this.redraw.run();
        lambda$render$8(routine);
    }

    public /* synthetic */ void lambda$exercise$23(GymStore.Exercise exercise, AlertDialog d, GymStore.Routine routine) {
        this.store.deleteExercise(exercise.id);
        d.dismiss();
        this.redraw.run();
        lambda$render$8(routine);
    }

    public /* synthetic */ void lambda$exercise$24(final GymStore.Exercise exercise, final AlertDialog d, final GymStore.Routine routine) {
        confirm("Remove this exercise from the template? Logged sets are kept.", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                GymScreen.this.lambda$exercise$23(exercise, d, routine);
            }
        });
    }

    public Spinner unitSpinner(String str) {
        Spinner spinner = new Spinner(this.a);
        spinner.setAdapter((SpinnerAdapter) new ArrayAdapter(this.a, android.R.layout.simple_spinner_dropdown_item, new String[]{"kg", "lb"}));
        spinner.setSelection(str.equals("lb") ? 1 : 0);
        return spinner;
    }

    static class SetInput {
        EditText reps;
        LinearLayout row;
        EditText weight;

        private SetInput() {
        }
    }

    public void lambda$routine$16(GymStore.Exercise e) {
        LinearLayout f = form();
        f.addView(this.ui.text("Most recent first · compare reps and weight using the same unit. All sessions remain available by week.", 14, UI.MUTED));
        this.ui.space(f, 12);
        List<String> history = this.store.history(e.id, LocalDate.now());
        if (history.isEmpty()) {
            f.addView(this.ui.text("Log this exercise to start your history.", 15, UI.MUTED));
        }
        for (String row : history) {
            f.addView(this.ui.text(row, 14, UI.TEXT));
            this.ui.space(f, 10);
        }
        final AlertDialog d = dialog(e.name + " · last 100 sets", f, "Close");
        d.getButton(-1).setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                d.dismiss();
            }
        });
    }

    class ExerciseInput {
        LinearLayout card;
        long id;
        String name;
        LinearLayout rows;
        final List<SetInput> sets = new ArrayList();
        Spinner unit;

        ExerciseInput(LinearLayout parent, long id, String name, String unitName, LocalDate date) {
            this.id = id;
            this.name = name;
            this.card = GymScreen.this.ui.card(parent);
            this.card.addView(GymScreen.this.ui.bold(name, 18, UI.TEXT));
            List<String> previous = id == 0 ? Collections.emptyList() : GymScreen.this.store.history(id, date);
            if (!previous.isEmpty()) {
                GymScreen.this.ui.space(this.card, 6);
                this.card.addView(GymScreen.this.ui.text("Latest: " + previous.get(0), 12, UI.GREEN));
            }
            this.unit = GymScreen.this.unitSpinner(unitName);
            this.card.addView(this.unit);
            this.card.addView(GymScreen.this.ui.text("Reps                         Weight", 12, UI.MUTED));
            this.rows = GymScreen.this.ui.col();
            this.card.addView(this.rows);
            GymScreen.this.ui.action(this.card, "+ Add set", false, new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    GymScreen.ExerciseInput.this.lambda$new$0();
                }
            });
        }

        public /* synthetic */ void lambda$new$0() {
            add(null);
        }

        public /* synthetic */ void lambda$add$1(SetInput s) {
            this.sets.remove(s);
            this.rows.removeView(s.row);
        }

        void add(GymStore.SetRow old) {
            if (this.sets.size() >= 50) {
                GymScreen.this.toast("Up to 50 sets per exercise.");
                return;
            }
            final SetInput s = new SetInput();
            s.row = GymScreen.this.ui.row();
            s.row.setPadding(0, GymScreen.this.ui.dp(5.0f), 0, GymScreen.this.ui.dp(5.0f));
            this.rows.addView(s.row);
            s.reps = new EditText(GymScreen.this.a);
            s.reps.setHint("Reps");
            s.reps.setInputType(2);
            s.reps.setTextSize(16.0f);
            s.reps.setContentDescription(this.name + " set reps");
            s.weight = new EditText(GymScreen.this.a);
            s.weight.setHint("Weight");
            s.weight.setInputType(8194);
            s.weight.setTextSize(16.0f);
            s.weight.setContentDescription(this.name + " set weight");
            if (old != null) {
                s.reps.setText("" + old.reps);
                s.weight.setText(GymRules.number(old.weight));
            }
            GymScreen.this.ui.weight(s.row, s.reps);
            GymScreen.this.ui.weight(s.row, s.weight);
            Button remove = GymScreen.this.ui.link("×", new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    GymScreen.ExerciseInput.this.lambda$add$1(s);
                }
            });
            remove.setContentDescription("Remove set");
            s.row.addView(remove, new LinearLayout.LayoutParams(GymScreen.this.ui.dp(48.0f), GymScreen.this.ui.dp(48.0f)));
            this.sets.add(s);
        }
    }

    private void log(final GymStore.Routine routine, LocalDate date, final GymStore.Session existing) {
        LinearLayout f;
        ExerciseInput in;
        List<GymStore.Exercise> exercises = this.store.exercises(routine.id);
        if (existing == null && exercises.isEmpty()) {
            toast("Add exercises to this day first.");
            lambda$render$8(routine);
            return;
        }
        LinearLayout f2 = form();
        final LocalDate[] chosen = {date};
        final Button dateButton = this.ui.button(date.toString(), false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                GymScreen.lambda$log$26();
            }
        });
        f2.addView(dateButton);
        dateButton.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                GymScreen.this.lambda$log$28(chosen, dateButton, view);
            }
        });
        this.ui.space(f2, 12);
        f2.addView(this.ui.text("Enter actual reps and weight for each set. Leave both fields blank to skip a set. Zero weight means unweighted.", 14, UI.MUTED));
        this.ui.space(f2, 16);
        final List<ExerciseInput> inputs = new ArrayList<>();
        if (existing == null) {
            for (GymStore.Exercise e : exercises) {
                ExerciseInput exerciseInput = new ExerciseInput(f2, e.id, e.name, e.unit, date);
                exerciseInput.add(null);
                inputs.add(exerciseInput);
            }
            f = f2;
        } else {
            Map<String, ExerciseInput> groups = new LinkedHashMap<>();
            for (GymStore.SetRow row : this.store.sets(existing.id)) {
                String key = row.exercise + "/" + row.name + "/" + row.unit;
                ExerciseInput in2 = groups.get(key);
                if (in2 == null) {
                    in = new ExerciseInput(f2, row.exercise, row.name, row.unit, date);
                    groups.put(key, in);
                } else {
                    in = in2;
                }
                in.add(row);
                f2 = f2;
                exercises = exercises;
            }
            f = f2;
            inputs.addAll(groups.values());
        }
        final EditText notes = this.ui.input(f, "Workout notes (optional)", existing == null ? "" : existing.notes, true);
        final AlertDialog d = dialog(existing == null ? "Log " + routine.name : "Edit workout", f, "Save workout");
        d.getButton(-1).setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                GymScreen.this.lambda$log$29(inputs, existing, routine, chosen, notes, d, view);
            }
        });
    }

    static /* synthetic */ void lambda$log$26() {
    }

    public /* synthetic */ void lambda$log$27(LocalDate[] chosen, Button dateButton, DatePicker dp, int y, int m, int d) {
        LocalDate selected = LocalDate.of(y, m + 1, d);
        if (selected.isAfter(LocalDate.now())) {
            toast("Choose today or an earlier date.");
        } else {
            chosen[0] = selected;
            dateButton.setText(selected.toString());
        }
    }

    public /* synthetic */ void lambda$log$28(final LocalDate[] chosen, final Button dateButton, View v) {
        DatePickerDialog picker = new DatePickerDialog(this.a, new DatePickerDialog.OnDateSetListener() {
            @Override // android.app.DatePickerDialog.OnDateSetListener
            public final void onDateSet(DatePicker datePicker, int i, int i2, int i3) {
                GymScreen.this.lambda$log$27(chosen, dateButton, datePicker, i, i2, i3);
            }
        }, chosen[0].getYear(), chosen[0].getMonthValue() - 1, chosen[0].getDayOfMonth());
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.show();
    }

    public /* synthetic */ void lambda$log$29(List inputs, GymStore.Session existing, GymStore.Routine routine, LocalDate[] chosen, EditText notes, AlertDialog d, View v) {
        List<GymStore.SetRow> rows = new ArrayList<>();
        Iterator it = inputs.iterator();
        boolean valid = true;
        while (valid) {
            ExerciseInput in = (ExerciseInput) it.next();
            boolean valid2 = valid;
            int position = 0;
            for (SetInput input : in.sets) {
                String reps = input.reps.getText().toString().trim();
                String weight = input.weight.getText().toString().trim();
                if (!reps.isEmpty() || !weight.isEmpty()) {
                    try {
                        GymStore.SetRow row = new GymStore.SetRow();
                        row.exercise = in.id;
                        row.name = in.name;
                        row.unit = in.unit.getSelectedItem().toString();
                        row.reps = Integer.parseInt(reps);
                        row.weight = Double.parseDouble(weight.replace(',', '.'));
                        position++;
                        row.position = position;
                        if (!GymRules.validSet(row.reps, row.weight)) {
                            throw new NumberFormatException();
                        }
                        rows.add(row);
                    } catch (NumberFormatException e) {
                        input.reps.setError("Enter reps > 0 and weight ≥ 0");
                        valid2 = false;
                    }
                }
            }
            valid = valid2;
        }
        if (valid) {
            if (rows.isEmpty()) {
                toast("Record at least one set.");
                return;
            }
            GymStore.Session session = new GymStore.Session();
            session.id = existing == null ? 0L : existing.id;
            session.routine = routine.id;
            session.name = existing == null ? routine.name : existing.name;
            session.date = chosen[0].toString();
            session.notes = notes.getText().toString().trim();
            try {
                this.store.save(session, rows);
                this.week = GymRules.monday(chosen[0]);
                d.dismiss();
                this.redraw.run();
                toast("Workout saved.");
            } catch (IllegalArgumentException e2) {
                toast(e2.getMessage());
            }
        }
    }

    public void lambda$render$10(final GymStore.Session session) {
        LinearLayout f = form();
        f.addView(this.ui.text(session.date, 14, UI.GREEN));
        String last = "";
        for (GymStore.SetRow set : this.store.sets(session.id)) {
            if (!last.equals(set.name)) {
                this.ui.space(f, 16);
                f.addView(this.ui.bold(set.name, 18, UI.TEXT));
                last = set.name;
            }
            f.addView(this.ui.text("Set " + set.position + " · " + GymRules.number(set.weight) + " " + set.unit + " × " + set.reps + " reps", 15, UI.TEXT));
        }
        if (!session.notes.isEmpty()) {
            this.ui.space(f, 16);
            f.addView(this.ui.text(session.notes, 15, UI.MUTED));
        }
        final AlertDialog d = dialog(session.name, f, "Close");
        d.getButton(-1).setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                d.dismiss();
            }
        });
        this.ui.action(f, "Edit recorded sets", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                GymScreen.this.lambda$session$31(session, d);
            }
        });
        this.ui.action(f, "Delete workout", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                GymScreen.this.lambda$session$33(session, d);
            }
        });
    }

    public /* synthetic */ void lambda$session$31(GymStore.Session session, AlertDialog d) {
        GymStore.Routine r = new GymStore.Routine();
        r.id = session.routine;
        r.name = session.name;
        d.dismiss();
        log(r, LocalDate.parse(session.date), session);
    }

    public /* synthetic */ void lambda$session$32(GymStore.Session session, AlertDialog d) {
        this.store.deleteSession(session.id);
        d.dismiss();
        this.redraw.run();
    }

    public /* synthetic */ void lambda$session$33(final GymStore.Session session, final AlertDialog d) {
        confirm("Delete this workout and its recorded sets?", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                GymScreen.this.lambda$session$32(session, d);
            }
        });
    }
}
