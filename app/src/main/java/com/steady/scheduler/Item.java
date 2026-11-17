package com.steady.scheduler;

import java.time.LocalDate;

public class Item {
    public long id;
    public long project;
    // Null follows the project default; every task resolves to Signal or Noise.
    public Boolean signalOverride;
    public String title = "";
    public String notes = "";
    public String section = "";
    public String start = LocalDate.now().toString();
    public boolean goal = false;
    public boolean scheduled = true;
    public boolean enabled = true;
    public int repeat = 0;
    public int minute = 540;

    public String key(LocalDate date) {
        if (this.repeat == 0) {
            return this.scheduled ? this.start : "once";
        }
        return Rules.key(date, this.repeat, this.goal);
    }
}
