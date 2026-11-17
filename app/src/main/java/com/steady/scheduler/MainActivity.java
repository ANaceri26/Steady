package com.steady.scheduler;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import androidx.exifinterface.media.ExifInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class MainActivity extends Activity {
    private static final int ALARM_FILE = 55;
    private static final int ALARM_TONE = 54;
    private static final int EXPORT = 51;
    private static final int EXPORT_PHOTOS = 52;
    private static final int JOURNAL_PHOTO = 53;
    private static final int PHOTO = 50;
    private LinearLayout body;
    private GymScreen gym;
    private JournalScreen journalScreen;
    private LinearLayout root;
    private ScrollView contentScroll;
    private int renderedTab=-1;
    private boolean animateNext;
    private Store store;
    private UI ui;
    private int tab = 0;
    private int priorityFilter = 0; // All, Signal, Noise
    private boolean signalFirst;
    private LocalDate day = LocalDate.now();
    private long projectId = 0;
    private long photoItem = 0;
    private long journalPhotoId = 0;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override // java.lang.Runnable
        public void run() {
            if (MainActivity.this.tab == 0) {
                MainActivity.this.render();
            }
            MainActivity.this.ticker.postDelayed(this, 60000L);
        }
    };

    interface DateResult {
        void result(LocalDate localDate);
    }

    @Override // android.app.Activity
    public void onCreate(Bundle state) {
        super.onCreate(state);
        this.ui = new UI(this);
        this.store = Store.get(this);
        signalFirst = AccountCloud.preferences(this).getBoolean("signalFirst", false);
        this.gym = new GymScreen(this, this.ui, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.render();
            }
        });
        if (state != null) {
            this.tab = state.getInt("tab");
            priorityFilter = state.getInt("priorityFilter", 0);
            signalFirst = state.getBoolean("signalFirst", false);
            this.projectId = state.getLong("project");
            this.photoItem = state.getLong("photo");
            this.journalPhotoId = state.getLong("journalPhoto");
            this.day = LocalDate.parse(state.getString("day", LocalDate.now().toString()));
        }
        if (state != null) {
            this.gym.week = LocalDate.parse(state.getString("gymWeek", GymRules.monday(LocalDate.now()).toString()));
        }
        journalScreen = new JournalScreen(this, ui, store, this::render, this::exportJournalPhotos, this::lambda$journal$26);
        if (state != null) { journalScreen.selectedGroup = state.getLong("journalGroup", -1); journalScreen.search = state.getString("journalSearch", ""); }
        Alarms.channel(this);
        render();
        consumeIntent();
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        Alarms.restore(this);
        render();
        this.ticker.postDelayed(this.refresh, 60000L);
    }

    @Override // android.app.Activity
    protected void onPause() {
        super.onPause();
        this.ticker.removeCallbacks(this.refresh);
    }

    @Override // android.app.Activity
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeIntent();
    }

    @Override // android.app.Activity
    protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putInt("tab", this.tab);
        b.putInt("priorityFilter", priorityFilter);
        b.putBoolean("signalFirst", signalFirst);
        b.putLong("journalGroup", journalScreen.selectedGroup);
        b.putString("journalSearch", journalScreen.search);
        b.putLong("project", this.projectId);
        b.putLong("photo", this.photoItem);
        b.putLong("journalPhoto", this.journalPhotoId);
        b.putString("day", this.day.toString());
        b.putString("gymWeek", this.gym.week.toString());
    }

    private void consumeIntent() {
        long id = getIntent().getLongExtra("open_item", 0L);
        if (id != 0) {
            Item i = this.store.find(id);
            String p = getIntent().getStringExtra("period");
            getIntent().removeExtra("open_item");
            if (i != null) {
                details(i, p == null ? i.key(LocalDate.now()) : p);
            }
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private String time(int minute) {
        return String.format(Locale.getDefault(), "%02d:%02d", Integer.valueOf(minute / 60), Integer.valueOf(minute % 60));
    }

    private void heading(String eyebrow, String title, String desc) {
        this.ui.eyebrow(this.body, eyebrow);
        TextView pageTitle=ui.bold(title,BuildConfig.REDESIGN?32:28,UI.TEXT);
        if(BuildConfig.REDESIGN)pageTitle.setTypeface(android.graphics.Typeface.create("serif",android.graphics.Typeface.NORMAL));
        body.addView(pageTitle);
        if (!desc.isEmpty()) {
            this.ui.space(this.body, 6);
            this.body.addView(this.ui.text(desc, 15, UI.MUTED));
        }
        this.ui.space(this.body, 16);
    }

    public void render() {
        int previousScroll=contentScroll!=null && renderedTab==tab ? contentScroll.getScrollY() : 0;
        renderedTab=tab;
        root=ui.col();root.setBackgroundColor(UI.BG);ui.insets(root);setContentView(root);
        LinearLayout top=ui.row();top.setPadding(ui.dp(20),ui.dp(BuildConfig.REDESIGN?4:8),ui.dp(12),ui.dp(4));root.addView(top);
        top.addView(new NavIcon(this,6,UI.GREEN),new LinearLayout.LayoutParams(ui.dp(23),ui.dp(23)));
        TextView brand=ui.bold("steady",BuildConfig.REDESIGN?20:24,UI.TEXT);brand.setPadding(ui.dp(10),0,0,0);ui.weight(top,brand);
        FrameLayout settings=new FrameLayout(this);settings.setContentDescription("Setup and settings");
        settings.addView(new NavIcon(this,5,UI.MUTED),new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),android.view.Gravity.CENTER));
        top.addView(settings,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));settings.setOnClickListener(v->navigateTo(5));
        if(!BuildConfig.REDESIGN){View divider=new View(this);divider.setBackgroundColor(UI.LINE);root.addView(divider,new LinearLayout.LayoutParams(-1,ui.dp(1)));}
        contentScroll=new ScrollView(this);contentScroll.setFillViewport(true);contentScroll.setClipToPadding(false);
        root.addView(contentScroll,new LinearLayout.LayoutParams(-1,0,1));body=ui.col();body.setPadding(ui.dp(20),ui.dp(16),ui.dp(20),ui.dp(24));contentScroll.addView(body);
        if(tab==0)today();else if(tab==1)projects();else if(tab==2)goals();else if(tab==3)journal();else if(tab==4)gym.render(body);else setup();
        if(BuildConfig.REDESIGN && tab==0){LinearLayout dock=ui.row();dock.setPadding(ui.dp(16),ui.dp(8),ui.dp(16),ui.dp(8));dock.setBackgroundColor(UI.BG);root.addView(dock);creationActions(dock);}
        LinearLayout nav=ui.row();nav.setPadding(ui.dp(8),ui.dp(6),ui.dp(8),ui.dp(8));nav.setBackgroundColor(UI.CARD);root.addView(nav);
        String[] labels={"Today","Projects","Goals","Journal","Gym"};
        for(int n=0;n<labels.length;n++){
            final int selected=n;LinearLayout cell=ui.col();cell.setGravity(android.view.Gravity.CENTER);cell.setMinimumHeight(ui.dp(56));cell.setPadding(0,ui.dp(6),0,ui.dp(6));
            if(tab==n)cell.setBackground(ui.bg(UI.TINT,14));
            cell.addView(new NavIcon(this,n,tab==n?UI.GREEN:UI.MUTED),new LinearLayout.LayoutParams(ui.dp(22),ui.dp(22)));
            TextView label=ui.text(labels[n],11,tab==n?UI.GREEN:UI.MUTED);label.setGravity(android.view.Gravity.CENTER);label.setMaxLines(1);cell.addView(label);
            cell.setContentDescription(labels[n]);cell.setSelected(tab==n);cell.setOnClickListener(v->navigateTo(selected));ui.weight(nav,cell);
        }
        contentScroll.post(()->contentScroll.scrollTo(0,previousScroll));
        if(animateNext && android.animation.ValueAnimator.areAnimatorsEnabled()){
            body.setAlpha(0);body.setTranslationY(ui.dp(8));body.animate().alpha(1).translationY(0).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
        animateNext=false;
    }

    private void navigateTo(int selected){animateNext=tab!=selected;tab=selected;projectId=0;render();}
    private void creationActions(LinearLayout row){
        ui.weight(row,ui.button("+ Add task",true,()->edit(newTask(0L),false)));
        ui.weight(row,ui.button("Paste list",false,this::bulk));
    }

    public /* synthetic */ void renderAction0(View v) {
        navigateTo(5);
    }

    public /* synthetic */ void renderAction1(int selected, View v) {
        navigateTo(selected);
    }

    private boolean onDay(Item i, LocalDate d) {
        return i.enabled && i.scheduled && Rules.due(d, LocalDate.parse(i.start), i.repeat);
    }

    private boolean done(Item i, String key) {
        return this.store.status(i.id, key).equals("done");
    }

    private boolean matchesPriority(Item item) {
        return priorityFilter == 0 || store.isSignal(item) == (priorityFilter == 1);
    }

    private void priorityBadge(LinearLayout card, boolean signal) {
        if (signal) {
            android.graphics.drawable.GradientDrawable background = ui.bg(UI.CARD, 18);
            background.setStroke(ui.dp(1), UI.GOLD);
            card.setBackground(background);
        }
        TextView badge = ui.bold(signal ? "◆ SIGNAL" : "NOISE", 11, signal ? UI.GOLD : UI.MUTED);
        badge.setContentDescription(signal ? "Signal" : "Noise");
        card.addView(badge);ui.space(card,6);
    }

    private void priorityControls() {
        android.widget.HorizontalScrollView scroll=new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filters=ui.row();scroll.addView(filters);body.addView(scroll);
        String[] labels={"All","Signal","Noise"};
        for(int n=0;n<labels.length;n++) {
            final int choice=n;
            Button button=ui.chip(labels[n],priorityFilter==n,()->{priorityFilter=choice;animateNext=true;render();});
            button.setContentDescription("Filter: "+labels[n]);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,ui.dp(48));lp.rightMargin=ui.dp(6);filters.addView(button,lp);
        }
        Button order=ui.chip(signalFirst ? "Signal first ↓" : "Time ↓",false,()->{signalFirst=!signalFirst;AccountCloud.preferences(this).edit().putBoolean("signalFirst",signalFirst).apply();render();});
        order.setContentDescription(signalFirst ? "Sort: Signal first. Tap for time order." : "Sort: Time. Tap for Signal first.");
        filters.addView(order,new LinearLayout.LayoutParams(-2,ui.dp(48)));
        ui.space(body,12);
    }

    private void orderTasks(List<Item> tasks) {
        if(signalFirst) tasks.sort(java.util.Comparator.comparing((Item item)->!store.isSignal(item)));
    }

    private void today() {
        if(BuildConfig.REDESIGN){todayStudio();return;}
        heading("MAKE ROOM FOR WHAT MATTERS", "Your day, in focus.", "");
        priorityControls();
        List<Item> items = new ArrayList<>();
        int completed = 0;
        int best = 0;
        for (Item i : this.store.items()) {
            if (onDay(i, this.day) && matchesPriority(i)) {
                items.add(i);
                if (done(i, i.key(this.day))) {
                    completed++;
                }
            }
            if (!i.goal && i.repeat == 1) {
                best = Math.max(best, Rules.streak(this.store.completed(i.id), LocalDate.now()));
            }
        }
        LinearLayout summary = this.ui.card(this.body);
        summary.setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14));
        LinearLayout counts = this.ui.row();
        summary.addView(counts);
        LinearLayout score = this.ui.col();
        score.addView(this.ui.bold(priorityFilter==0 ? "DAILY PROGRESS" : priorityFilter==1 ? "SIGNAL PROGRESS" : "NOISE PROGRESS", 11, UI.MUTED));
        score.addView(this.ui.bold(completed + " / " + items.size(), 28, UI.TEXT));
        score.addView(this.ui.text((completed != items.size() || completed <= 0) ? "One task at a time." : "Everything you planned, done.", 12, UI.MUTED));
        this.ui.weight(counts, score);
        counts.addView(new ProgressRing(this, completed, items.size()), new LinearLayout.LayoutParams(this.ui.dp(64.0f), this.ui.dp(64.0f)));
        this.ui.space(this.body, 8);
        this.body.addView(this.ui.bold("Your schedule", 21, UI.TEXT));
        this.ui.space(this.body, 4);
        LinearLayout dateRow = this.ui.row();
        this.body.addView(dateRow);
        dateRow.addView(this.ui.link("‹", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.todayAction3();
            }
        }), new LinearLayout.LayoutParams(this.ui.dp(48.0f), this.ui.dp(48.0f)));
        this.ui.weight(dateRow, this.ui.link(this.day.format(DateTimeFormatter.ofPattern("EEE, d MMM")), new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.todayAction5();
            }
        }));
        dateRow.addView(this.ui.link("›", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.todayAction6();
            }
        }), new LinearLayout.LayoutParams(this.ui.dp(48.0f), this.ui.dp(48.0f)));
        this.ui.space(this.body, 8);
        if (items.isEmpty()) {
            LinearLayout c = this.ui.empty(this.body, 0, priorityFilter==0 ? "A little space to begin." : "No matching tasks today.", priorityFilter==0 ? "Add a task or paste your routine below." : "Try All, or change a task’s Signal / Noise setting.", true);
        }
        orderTasks(items);
        for (Item i2 : items) {
            itemCard(this.body, i2, i2.key(this.day), true);
        }
        List<Item> overdue = new ArrayList<>();
        for (Item i3 : this.store.items()) {
            if (!i3.goal && i3.enabled && i3.scheduled && i3.repeat == 0 && LocalDate.parse(i3.start).isBefore(this.day) && !done(i3, i3.key(this.day)) && !this.store.status(i3.id, i3.key(this.day)).equals("skipped")) {
                if(matchesPriority(i3)) overdue.add(i3);
            }
        }
        orderTasks(overdue);
        if (!overdue.isEmpty()) {
            this.ui.space(this.body, 16);
            this.body.addView(this.ui.bold("UNFINISHED · EARLIER", 12, UI.RED));
            this.ui.space(this.body, 12);
            for (Item i4 : overdue) {
                itemCard(this.body, i4, i4.key(this.day), true);
            }
        }
        List<Item> parked = new ArrayList<>();
        for (Item i5 : this.store.items()) {
            if (!i5.goal && i5.project == 0 && (!i5.scheduled || !i5.enabled)) {
                if(matchesPriority(i5)) parked.add(i5);
            }
        }
        orderTasks(parked);
        if (!parked.isEmpty()) {
            this.ui.space(this.body, 16);
            this.body.addView(this.ui.bold("UNSCHEDULED & PAUSED", 12, UI.MUTED));
            this.ui.space(this.body, 12);
            for (Item i6 : parked) {
                itemCard(this.body, i6, i6.key(LocalDate.now()), false);
            }
        }
        ui.space(body,8);LinearLayout creation=ui.row();body.addView(creation);creationActions(creation);
        this.ui.space(this.body, 22);
        this.body.addView(this.ui.text("↗ " + best + " day best current streak. Finish a daily task to keep its streak going.", 13, UI.MUTED));
        if (!Alarms.exact(this) || !Alarms.notifications(this) || !Alarms.fullScreen(this)) {
            this.body.addView(this.ui.link("Finish alarm setup →", new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.todayAction8();
                }
            }));
        }
    }

    private void todayStudio(){
        ui.eyebrow(body,"A LITTLE PROGRESS, EVERY DAY");
        TextView title=ui.bold(day.equals(LocalDate.now())?"Make today count.":"Make room for it.",32,UI.TEXT);
        title.setTypeface(android.graphics.Typeface.create("serif",android.graphics.Typeface.NORMAL));body.addView(title);ui.space(body,8);
        body.addView(ui.text("Less noise. More of what matters.",15,UI.MUTED));ui.space(body,20);
        List<Item> scheduled=new ArrayList<>(),earlier=new ArrayList<>(),parked=new ArrayList<>();int completed=0,signals=0;
        for(Item item:store.items()){
            if(!matchesPriority(item))continue;
            if(onDay(item,day)){scheduled.add(item);if(done(item,item.key(day)))completed++;if(store.isSignal(item))signals++;}
            else if(!item.goal && item.enabled && item.scheduled && item.repeat==0 && LocalDate.parse(item.start).isBefore(day) && !done(item,item.key(day)) && !"skipped".equals(store.status(item.id,item.key(day))))earlier.add(item);
            else if(!item.goal && item.project==0 && (!item.scheduled||!item.enabled))parked.add(item);
        }
        LinearLayout progress=ui.card(body);progress.setBackground(ui.bg(UI.TINT,20));
        LinearLayout stats=ui.row();progress.addView(stats);ui.weight(stats,ui.bold(completed+" of "+scheduled.size()+" complete",18,UI.TEXT));
        stats.addView(ui.text(signals+" signal",13,UI.GREEN));ui.space(progress,12);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(1,scheduled.size()));bar.setProgress(completed);bar.setProgressTintList(ColorStateList.valueOf(UI.GREEN));bar.setProgressBackgroundTintList(ColorStateList.valueOf(UI.LINE));progress.addView(bar,new LinearLayout.LayoutParams(-1,ui.dp(6)));
        ui.space(body,8);priorityControls();
        LinearLayout dateRow=ui.row();body.addView(dateRow);
        dateRow.addView(ui.link("‹",()->{day=day.minusDays(1);render();}),new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        ui.weight(dateRow,ui.link(day.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),this::todayAction5));
        dateRow.addView(ui.link("›",()->{day=day.plusDays(1);render();}),new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        ui.space(body,12);
        if(scheduled.isEmpty())ui.empty(body,0,priorityFilter==0?"Your day starts here.":"Nothing in this view.",priorityFilter==0?"Choose one thing worth making time for. Add it below.":"Switch to All or change a task’s priority.",true);
        studioSection("THE PLAN",scheduled,day);
        studioSection("STILL ON YOUR MIND",earlier,day);
        studioSection("ON PAUSE",parked,LocalDate.now());
        if(!Alarms.exact(this)||!Alarms.notifications(this)||!Alarms.fullScreen(this)){ui.space(body,12);ui.action(body,"Set up reminders →",false,()->navigateTo(5));}
    }

    private void studioSection(String label,List<Item> items,LocalDate date){
        if(items.isEmpty())return;orderTasks(items);ui.eyebrow(body,label);
        for(Item item:items)itemCard(body,item,item.key(date),true);ui.space(body,8);
    }

    private void studioTaskCard(LinearLayout parent,Item item,String key){
        boolean finished=done(item,key),signal=store.isSignal(item);LinearLayout card=ui.card(parent);
        LinearLayout row=ui.row();card.addView(row);
        Button check=ui.chip(finished?"✓":"○",finished,()->{if(finished)details(item,key);else if(item.goal&&!"started".equals(store.status(item.id,key)))startGoal(item,key);else complete(item,key);});
        check.setContentDescription(finished?"Completed "+item.title:(item.goal&&!"started".equals(store.status(item.id,key))?"Start ":"Complete ")+item.title);row.addView(check,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        LinearLayout copy=ui.col();copy.setPadding(ui.dp(10),0,0,0);ui.weight(row,copy);
        TextView name=ui.bold(item.title,18,finished?UI.MUTED:UI.TEXT);if(finished)name.setPaintFlags(name.getPaintFlags()|16);copy.addView(name);
        copy.addView(ui.text(item.scheduled?time(item.minute)+" · "+Rules.repeatLabel(item.repeat):"Unscheduled",13,UI.MUTED));
        copy.setOnClickListener(v->details(item,key));copy.setContentDescription("Open "+item.title);
        ui.space(card,10);LinearLayout meta=ui.row();card.addView(meta);TextView badge=ui.bold(signal?"◆ SIGNAL":"NOISE",11,signal?UI.GOLD:UI.MUTED);meta.addView(badge);
        if(item.project>0){TextView project=ui.text("  /  "+store.projectName(item.project),12,UI.MUTED);project.setMaxLines(1);project.setEllipsize(android.text.TextUtils.TruncateAt.END);ui.weight(meta,project);}
        if(!"planned".equals(store.status(item.id,key))&&!finished)card.addView(ui.text(store.status(item.id,key),12,UI.GREEN));
        if(!item.notes.isEmpty()){ui.space(card,8);TextView note=ui.text(item.notes,14,UI.MUTED);note.setMaxLines(2);note.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(note);}
        card.setOnClickListener(v->details(item,key));
    }

    public /* synthetic */ void todayAction2() {
        edit(newTask(0L), false);
    }

    public /* synthetic */ void todayAction3() {
        this.day = this.day.minusDays(1L);
        render();
    }

    public /* synthetic */ void todayAction4(LocalDate d) {
        this.day = d;
        render();
    }

    public /* synthetic */ void todayAction5() {
        pickDate(this.day, new DateResult() {
            @Override // com.steady.scheduler.MainActivity.DateResult
            public final void result(LocalDate localDate) {
                MainActivity.this.todayAction4(localDate);
            }
        });
    }

    public /* synthetic */ void todayAction6() {
        this.day = this.day.plusDays(1L);
        render();
    }

    public /* synthetic */ void todayAction7() {
        edit(newTask(0L), false);
    }

    public /* synthetic */ void todayAction8() {
        this.tab = 5;
        render();
    }

    private Item newTask(long project) {
        Item i = new Item();
        i.start = (this.day.isBefore(LocalDate.now()) ? LocalDate.now() : this.day).toString();
        i.project = project;
        i.scheduled = project == 0;
        ZonedDateTime next = ZonedDateTime.now().plusHours(1L);
        i.minute = (next.getHour() * 60) + next.getMinute();
        if (project == 0) {
            i.start = next.toLocalDate().toString();
        }
        return i;
    }

    private void empty(String title, String subtitle) {
        LinearLayout c = this.ui.card(this.body);
        c.addView(this.ui.bold(title, 21, UI.TEXT));
        this.ui.space(c, 8);
        c.addView(this.ui.text(subtitle, 15, UI.MUTED));
    }

    private void itemCard(LinearLayout parent, final Item i, final String key, boolean showTime) {
        if(BuildConfig.REDESIGN){studioTaskCard(parent,i,key);return;}
        StringBuilder sbAppend;
        List<String> photos;
        LinearLayout linearLayoutCard = this.ui.card(parent);
        priorityBadge(linearLayoutCard, store.isSignal(i));
        String status = this.store.status(i.id, key);
        final boolean finished = status.equals("done");
        LinearLayout row = this.ui.row();
        linearLayoutCard.addView(row);
        TextView check = this.ui.bold(finished ? "✓" : "○", 26, finished ? UI.GREEN : UI.MUTED);
        check.setGravity(17);
        check.setContentDescription(finished ? "Completed" : (i.goal && !"started".equals(status) ? "Start " : "Complete ") + i.title);
        row.addView(check, new LinearLayout.LayoutParams(this.ui.dp(44.0f), this.ui.dp(48.0f)));
        check.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$itemCard$9(finished, i, key, view);
            }
        });
        LinearLayout copy = this.ui.col();
        this.ui.weight(row, copy);
        TextView title = this.ui.bold(i.title, 17, finished ? UI.MUTED : UI.TEXT);
        if (finished) {
            title.setPaintFlags(title.getPaintFlags() | 16);
        }
        copy.addView(title);
        String meta = i.scheduled ? time(i.minute) + " · " + Rules.repeatLabel(i.repeat) : "Not scheduled";
        if (i.project > 0) {
            meta = meta + " · " + this.store.projectName(i.project);
        }
        if (!i.enabled) meta += " · Paused";
        else if (!status.equals("planned") && !finished) meta += " · " + status;
        copy.addView(this.ui.text(meta, 13, UI.MUTED));
        if (!i.notes.isEmpty()) {
            TextView note2 = this.ui.text(i.notes, 14, UI.MUTED);
            note2.setMaxLines(2);
            copy.addView(note2);
        }
        if (i.goal && i.repeat == 1) copy.addView(ui.text("↗ " + Rules.streak(store.completed(i.id), LocalDate.now()) + " day streak",13,UI.GREEN));
        copy.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$itemCard$10(i, key, view);
            }
        });
        photos = this.store.photos(i.id);
        if (!photos.isEmpty()) {
            ImageView image2 = new ImageView(this);
            BitmapFactory.Options options2 = new BitmapFactory.Options();
            options2.inSampleSize = 8;
            image2.setImageBitmap(BitmapFactory.decodeFile(new File(getFilesDir(), photos.get(0)).getPath(), options2));
            image2.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image2.setContentDescription("Open task photos");
            row.addView(image2, new LinearLayout.LayoutParams(this.ui.dp(42.0f), this.ui.dp(42.0f)));
            image2.setOnClickListener(new View.OnClickListener() {
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$itemCard$11(i, key, view);
                }
            });
        }
        TextView more2 = this.ui.text("›", 25, UI.GREEN);
        more2.setGravity(17);
        row.addView(more2, new LinearLayout.LayoutParams(this.ui.dp(32.0f), this.ui.dp(48.0f)));
        more2.setContentDescription("Open " + i.title);
        more2.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$itemCard$12(i, key, view);
            }
        });
    }

    public /* synthetic */ void lambda$itemCard$9(boolean finished, Item i, String key, View v) {
        if (finished) {
            details(i, key);
        } else {
            if (i.goal && !"started".equals(store.status(i.id, key))) startGoal(i, key);
            else complete(i, key);
        }
    }

    public /* synthetic */ void lambda$itemCard$10(Item i, String key, View v) {
        details(i, key);
    }

    public /* synthetic */ void lambda$itemCard$11(Item i, String key, View v) {
        details(i, key);
    }

    public /* synthetic */ void lambda$itemCard$12(Item i, String key, View v) {
        details(i, key);
    }

    private void projects() {
        if (this.projectId != 0) {
            projectDetail();
            return;
        }
        heading("BIG IDEAS, SMALL STEPS", "Your projects.", "");
        this.ui.primary(this.body, "+  New project", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$projects$13();
            }
        });
        this.ui.space(this.body, 20);
        if (this.store.projects().isEmpty()) {
            this.ui.primary(this.ui.empty(this.body, 1, "Start with the big picture.", "Create a project, group its subtasks into sections, then give them a place in your day.", false), "Create a project", new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$projects$14();
                }
            });
        }
        for (final Store.Project p : this.store.projects()) {
            int total = 0;
            int complete = 0;
            for (Item i : this.store.items()) {
                if (i.project == p.id) {
                    total++;
                    if (done(i, i.key(LocalDate.now()))) {
                        complete++;
                    }
                }
            }
            LinearLayout c = this.ui.card(this.body);
            priorityBadge(c,p.signal);
            c.addView(this.ui.bold(p.title, 23, UI.TEXT));
            if (!p.notes.isEmpty()) {
                c.addView(this.ui.text(p.notes, 14, UI.MUTED));
            }
            this.ui.space(c, 12);
            c.addView(this.ui.text(complete + " / " + total + " subtasks complete", 13, UI.GREEN));
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(Math.max(1, total));
            bar.setProgress(complete);
            bar.setProgressTintList(ColorStateList.valueOf(UI.GREEN));
            this.ui.space(c, 12);
            c.addView(bar);
            this.ui.action(c, "Open project →", false, new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$projects$15(p);
                }
            });
        }
    }

    public /* synthetic */ void lambda$projects$13() {
        lambda$projectDetail$18(null);
    }

    public /* synthetic */ void lambda$projects$14() {
        lambda$projectDetail$18(null);
    }

    public /* synthetic */ void lambda$projects$15(Store.Project p) {
        this.projectId = p.id;
        render();
    }

    private void projectDetail() {
        Store.Project found = null;
        for (Store.Project p : this.store.projects()) {
            if (p.id == this.projectId) {
                found = p;
            }
        }
        if (found == null) {
            this.projectId = 0L;
            projects();
            return;
        }
        final Store.Project project = found;
        this.ui.action(this.body, "‹ All projects", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$projectDetail$16();
            }
        });
        this.ui.space(this.body, 20);
        heading(project.signal ? "◆ SIGNAL PROJECT" : "NOISE PROJECT", project.title, project.notes);
        body.addView(ui.text("Tasks follow this project unless you override them.",13,UI.MUTED));
        LinearLayout buttons = this.ui.row();
        this.body.addView(buttons);
        this.ui.weight(buttons, this.ui.button("+ Subtask", true, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$projectDetail$17(project);
            }
        }));
        this.ui.weight(buttons, this.ui.button("Edit project", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$projectDetail$18(project);
            }
        }));
        this.ui.space(this.body, 22);
        Map<String, List<Item>> groups = new LinkedHashMap<>();
        for (Item i : this.store.items()) {
            if (i.project == project.id) {
                groups.computeIfAbsent(i.section.isEmpty() ? "General" : i.section, new Function() {
                    @Override // java.util.function.Function
                    public final Object apply(Object obj) {
                        return MainActivity.lambda$projectDetail$19((String) obj);
                    }
                }).add(i);
            }
        }
        if (groups.isEmpty()) {
            empty("What is the first step?", "Create a subtask and give it a section, like Research or Assembly.");
        }
        for (final Map.Entry<String, List<Item>> group : groups.entrySet()) {
            LinearLayout sectionHeader = this.ui.row();
            this.body.addView(sectionHeader);
            this.ui.weight(sectionHeader, this.ui.bold(group.getKey().toUpperCase(Locale.getDefault()) + " · " + group.getValue().size(), 13, UI.GREEN));
            Button addSection = this.ui.button("+ Add", false, new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$projectDetail$20(project, group);
                }
            });
            sectionHeader.addView(addSection);
            this.ui.space(this.body, 12);
            for (Item i2 : group.getValue()) {
                itemCard(this.body, i2, i2.key(LocalDate.now()), true);
            }
            this.ui.space(this.body, 12);
        }
    }

    public /* synthetic */ void lambda$projectDetail$16() {
        this.projectId = 0L;
        render();
    }

    public /* synthetic */ void lambda$projectDetail$17(Store.Project project) {
        edit(newTask(project.id), false);
    }

    static /* synthetic */ List lambda$projectDetail$19(String k) {
        return new ArrayList();
    }

    public /* synthetic */ void lambda$projectDetail$20(Store.Project project, Map.Entry group) {
        Item next = newTask(project.id);
        next.section = (String) group.getKey();
        edit(next, false);
    }

    public void lambda$projectDetail$18(final Store.Project p) {
        LinearLayout form = form();
        final EditText title = this.ui.input(form, "Project name", p == null ? "" : p.title, false);
        final EditText notes = this.ui.input(form, "What are you building?", p != null ? p.notes : "", true);
        final Switch signal = new Switch(this);signal.setText("Signal project");signal.setChecked(p != null && p.signal);form.addView(signal);
        form.addView(ui.text("Off = Noise. Tasks follow this setting unless individually overridden.",13,UI.MUTED));
        final AlertDialog dialog = dialog(p == null ? "New project" : "Edit project", form, "Save", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.lambda$editProject$21();
            }
        });
        dialog.getButton(-1).setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$editProject$22(title, p, notes, signal, dialog, view);
            }
        });
        if (p != null) {
            Button delete = this.ui.button("Delete project", false, new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$editProject$24(p, dialog);
                }
            });
            form.addView(delete);
        }
    }

    static /* synthetic */ void lambda$editProject$21() {
    }

    public /* synthetic */ void lambda$editProject$22(EditText title, Store.Project p, EditText notes, Switch signal, AlertDialog dialog, View v) {
        if (title.getText().toString().trim().isEmpty()) {
            title.setError("Add a name");
            return;
        }
        this.projectId = this.store.project(p == null ? 0L : p.id, title.getText().toString().trim(), notes.getText().toString().trim(), signal.isChecked());
        dialog.dismiss();
        render();
    }

    public /* synthetic */ void lambda$editProject$23(Store.Project p, AlertDialog dialog) {
        for (Item i : this.store.items()) {
            if (i.project == p.id) {
                deleteItem(i);
            }
        }
        this.store.deleteProject(p.id);
        this.projectId = 0L;
        dialog.dismiss();
        render();
    }

    public /* synthetic */ void lambda$editProject$24(final Store.Project p, final AlertDialog dialog) {
        confirm("Delete this project and its subtasks? Journal entries will remain.", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$editProject$23(p, dialog);
            }
        });
    }

    private void goals() {
        heading("DIRECTION, NOT JUST MOTION", "Goals with intention.", "");
        this.ui.primary(this.body, "+  Add goal", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$goals$25();
            }
        });
        this.ui.space(this.body, 18);
        this.body.addView(this.ui.text("Reflect on each goal before marking it complete. Your answers become your journal.", 15, UI.MUTED));
        this.ui.space(this.body, 24);
        int[] iArr = {1, 2, 3};
        for (int i = 0; i < 3; i++) {
            int repeat = iArr[i];
            LinearLayout c = this.ui.card(this.body);
            this.ui.eyebrow(c, Rules.repeatLabel(repeat).toUpperCase(Locale.getDefault()));
            boolean any = false;
            for (Item i2 : this.store.items()) {
                if (i2.goal && i2.repeat == repeat) {
                    itemCard(c, i2, i2.key(LocalDate.now()), true);
                    any = true;
                }
            }
            if (!any) {
                c.addView(this.ui.text("No " + Rules.repeatLabel(repeat).toLowerCase(Locale.getDefault()) + " goals yet.", 15, UI.MUTED));
            }
        }
    }

    public /* synthetic */ void lambda$goals$25() {
        Item i = newTask(0L);
        i.goal = true;
        i.repeat = 1;
        i.minute = 1230;
        i.start = LocalDate.now().toString();
        edit(i, true);
    }

    private void journal() { journalScreen.render(body); }

    public /* synthetic */ void lambda$journal$26(long journalId) {
        this.journalPhotoId = journalId;
        startActivityForResult(new Intent("android.intent.action.OPEN_DOCUMENT").setType("image/*").addCategory("android.intent.category.OPENABLE"), JOURNAL_PHOTO);
    }

    public void writeJournal() { journalScreen.edit(new JournalRepository.Entry()); }

    private void setup() {
        heading("MAKE IT YOURS", "Ready when you are.", "A few phone settings make your reminders work as intended.");
        setting("1. Notifications", Alarms.notifications(this) ? "Allowed" : "Action needed", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$setup$29();
            }
        });
        setting("2. Alarms & reminders", Alarms.exact(this) ? "Allowed" : "Action needed", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$setup$30();
            }
        });
        setting("3. Full-screen reminders", Alarms.fullScreen(this) ? "Allowed" : "Action needed", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$setup$31();
            }
        });
        alarmSoundSettings();
        LinearLayout volume = this.ui.card(this.body);
        volume.addView(this.ui.bold("Alarm volume", 18, UI.TEXT));
        final AudioManager audio = (AudioManager) getSystemService(AudioManager.class);
        SeekBar slider = new SeekBar(this);
        slider.setContentDescription("Alarm volume");
        slider.setMax(audio.getStreamMaxVolume(4));
        slider.setProgress(audio.getStreamVolume(4));
        volume.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar s, int value, boolean user) {
                if (user) {
                    audio.setStreamVolume(4, value, 0);
                }
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        volume.addView(this.ui.text("This controls your phone’s alarm volume. Zero is silent.", 13, UI.MUTED));
        this.ui.action(this.body, "Preview the swipe screen (silent)", true, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$setup$32();
            }
        });
        this.ui.action(this.body, "Test a ringing alarm in 1 minute", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.testAlarm();
            }
        });
        this.ui.space(this.body, 20);
        LinearLayout info = this.ui.card(this.body);
        info.addView(this.ui.bold("A little good to know", 18, UI.TEXT));
        this.ui.space(info, 8);
        info.addView(this.ui.text("Reminders loop using your alarm volume until you accept, skip or snooze, with a 10-minute automatic stop. When your phone is unlocked, Android may show a banner; tap it to open the swipe screen. Do Not Disturb and phone settings can affect sound. Test with your screen locked.", 14, UI.MUTED));
        this.ui.space(info, 12);
        info.addView(this.ui.text("Your data stays on this phone. Photos are copied into the app. Guest data stays local. Signed-in accounts can transfer data with Sync now. Uninstalling or clearing app storage deletes your data. Journal exports include photos in a ZIP; text-only export is also available. Use a full backup below to transfer your data offline.", 14, UI.MUTED));
        this.ui.space(info, 12);
        info.addView(this.ui.text("Streaks count completed daily tasks, not accepted reminders. Yesterday's streak remains alive while today is still open. Past days can be completed manually; future days cannot.", 14, UI.MUTED));
        this.ui.space(this.body, 16);
        LinearLayout exports = this.ui.card(this.body);
        exports.addView(this.ui.bold("Journal exports", 20, UI.TEXT));
        this.ui.action(exports, "Export journal + photos (ZIP)", true, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.exportJournalPhotos();
            }
        });
        this.ui.action(exports, "Export text only", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.exportJournal();
            }
        });
        this.ui.space(this.body, 14);
        AccountScreen.add(this, this.ui, this.body);
        BackupScreen.add(this, this.ui, this.body);
        this.body.addView(this.ui.text("Steady " + BuildConfig.VERSION_NAME, 12, UI.MUTED));
    }

    public /* synthetic */ void lambda$setup$29() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission("android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            openSettings(new Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra("android.provider.extra.APP_PACKAGE", getPackageName()));
        } else {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 10);
        }
    }

    public /* synthetic */ void lambda$setup$30() {
        if (Build.VERSION.SDK_INT >= 31) {
            openSettings(new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM", Uri.parse("package:" + getPackageName())));
        } else {
            toast("Already supported on this Android version.");
        }
    }

    public /* synthetic */ void lambda$setup$31() {
        if (Build.VERSION.SDK_INT >= 34) {
            openSettings(new Intent("android.settings.MANAGE_APP_USE_FULL_SCREEN_INTENT", Uri.parse("package:" + getPackageName())));
        } else {
            toast("No separate permission needed on this Android version.");
        }
    }

    public /* synthetic */ void lambda$setup$32() {
        startActivity(new Intent(this, (Class<?>) AlarmActivity.class).putExtra("demo", true));
    }

    private void alarmSoundSettings() {
        LinearLayout c = this.ui.card(this.body);
        c.addView(this.ui.bold("4. Alarm sound", 18, UI.TEXT));
        c.addView(this.ui.text(AlarmSound.name(this), 14, UI.GREEN));
        this.ui.action(c, "Choose a phone alarm tone", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$alarmSoundSettings$33();
            }
        });
        this.ui.action(c, "Choose an audio file", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$alarmSoundSettings$34();
            }
        });
        this.ui.action(c, "Use Steady built-in alarm", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$alarmSoundSettings$35();
            }
        });
        String error = getSharedPreferences("alarm_sound", 0).getString("last_error", "");
        if (!error.isEmpty()) {
            c.addView(this.ui.text(error, 13, UI.RED));
        }
    }

    public /* synthetic */ void lambda$alarmSoundSettings$33() {
        Intent i = new Intent("android.intent.action.RINGTONE_PICKER").putExtra("android.intent.extra.ringtone.TYPE", 4).putExtra("android.intent.extra.ringtone.SHOW_SILENT", false).putExtra("android.intent.extra.ringtone.DEFAULT_URI", RingtoneManager.getDefaultUri(4)).putExtra("android.intent.extra.ringtone.EXISTING_URI", AlarmSound.uri(this));
        try {
            startActivityForResult(i, ALARM_TONE);
        } catch (ActivityNotFoundException e) {
            toast("This phone has no tone picker. Choose an audio file or use the built-in alarm.");
        }
    }

    public /* synthetic */ void lambda$alarmSoundSettings$34() {
        startActivityForResult(new Intent("android.intent.action.OPEN_DOCUMENT").setType("audio/*").addCategory("android.intent.category.OPENABLE"), ALARM_FILE);
    }

    public /* synthetic */ void lambda$alarmSoundSettings$35() {
        AlarmSound.choose(this, null, "Steady · built-in alarm");
        render();
    }

    private void setting(String title, String state, Runnable action) {
        LinearLayout c = this.ui.card(this.body);
        c.addView(this.ui.bold(title, 17, UI.TEXT));
        c.addView(this.ui.text(state, 13, UI.GREEN));
        this.ui.action(c, "Open setting", false, action);
    }

    private void openSettings(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + getPackageName())));
        }
    }

    public void testAlarm() {
        if (!Alarms.exact(this) || !Alarms.notifications(this)) {
            toast("Allow notifications and Alarms & reminders first.");
            return;
        }
        Item i = new Item();
        ZonedDateTime n = ZonedDateTime.now().plusMinutes(1L);
        i.title = "Test reminder · swipe to accept";
        i.notes = "A test task. Delete it when you are finished.";
        i.start = n.toLocalDate().toString();
        i.minute = (n.getHour() * 60) + n.getMinute();
        this.store.save(i);
        Alarms.schedule(this, i);
        toast("Test alarm set for " + time(i.minute) + ". Lock your screen to try it.");
    }

    private LinearLayout form() {
        LinearLayout f = this.ui.col();
        f.setPadding(this.ui.dp(20.0f), this.ui.dp(12.0f), this.ui.dp(20.0f), this.ui.dp(12.0f));
        return f;
    }

    private AlertDialog dialog(String title, LinearLayout form, String positive, final Runnable save) {
        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog d = new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).setPositiveButton(positive, new DialogInterface.OnClickListener() {
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                save.run();
            }
        }).create();
        d.show();
        return d;
    }

    private void pickDate(LocalDate date, final DateResult result) {
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override // android.app.DatePickerDialog.OnDateSetListener
            public final void onDateSet(DatePicker datePicker, int i, int i2, int i3) {
                result.result(LocalDate.of(i, i2 + 1, i3));
            }
        }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()).show();
    }

    private void edit(final Item item, final boolean goal) {
        String str;
        LinearLayout f = form();
        final EditText title = this.ui.input(f, goal ? "Goal" : "Task name", item.title, false);
        final EditText notes = this.ui.input(f, goal ? "What does success look like?" : "Notes", item.notes, true);
        final Switch followProject = new Switch(this);
        followProject.setText("Follow project priority");followProject.setChecked(item.project>0 && item.signalOverride==null);
        final Switch signal = new Switch(this);signal.setText("Signal task");signal.setChecked(store.isSignal(item));
        if(item.project>0) f.addView(followProject);
        f.addView(signal);signal.setEnabled(!followProject.isChecked());
        followProject.setOnCheckedChangeListener((button,checked)->{signal.setEnabled(!checked);if(checked)signal.setChecked(store.projectSignal(item.project));});
        f.addView(ui.text("Off = Noise. Signal highlights work that moves your goals forward.",13,UI.MUTED));
        final EditText section = item.project > 0 ? this.ui.input(f, "Project section (e.g. Research / 3D printing)", item.section, false) : null;
        final Switch scheduled = new Switch(this);
        scheduled.setText(R.string.schedule_toggle);
        scheduled.setChecked(item.scheduled);
        if (!goal) {
            f.addView(scheduled);
        }
        this.ui.space(f, 12);
        f.addView(this.ui.text(goal ? "How often should we check in?" : "Repeat", 13, UI.MUTED));
        final Spinner repeat = new Spinner(this);
        String[] options = goal ? new String[]{"Daily", "Weekly", "Monthly"} : new String[]{"Once", "Daily", "Weekly", "Monthly"};
        repeat.setAdapter((SpinnerAdapter) new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options));
        int i = item.repeat;
        if (goal) {
            i--;
        }
        repeat.setSelection(i);
        f.addView(repeat);
        final LocalDate[] date = {LocalDate.parse(item.start)};
        final int[] minute = {item.minute};
        final boolean[] timeChosen = {item.id != 0 || item.project == 0};
        this.ui.space(f, 14);
        f.addView(this.ui.text(goal ? "First review date (sets weekday / day of month)" : "First date · sets weekly weekday / monthly day", 13, UI.MUTED));
        final Button dateButton = this.ui.button(date[0].toString(), false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.lambda$edit$38();
            }
        });
        dateButton.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$edit$40(date, dateButton, view);
            }
        });
        f.addView(dateButton);
        this.ui.space(f, 12);
        f.addView(this.ui.text("Reminder time · local phone time", 13, UI.MUTED));
        final Button timeButton = this.ui.button(timeChosen[0] ? time(minute[0]) : "Choose reminder time", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.lambda$edit$41();
            }
        });
        timeButton.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$edit$43(minute, timeChosen, scheduled, timeButton, view);
            }
        });
        f.addView(timeButton);
        final Switch enabled = new Switch(this);
        enabled.setText(R.string.active_toggle);
        enabled.setChecked(item.enabled);
        this.ui.space(f, 16);
        f.addView(enabled);
        this.ui.space(f, 12);
        f.addView(this.ui.text(goal ? "A completed check-in needs a detailed reflection. Weekly periods start Monday. Monthly dates such as the 31st use the last day in shorter months." : "Weekly repeats use the selected weekday. Monthly repeats use the selected day, or the last day of shorter months. Reminders follow your phone’s local time.", 13, UI.MUTED));
        if (item.id == 0) {
            str = goal ? "New goal" : "New task";
        } else {
            str = "Edit";
        }
        final AlertDialog d = dialog(str, f, "Save", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.lambda$edit$44();
            }
        });
        d.getButton(-1).setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$edit$45(title, repeat, goal, scheduled, timeChosen, date, minute, item, notes, enabled, section, followProject, signal, d, view);
            }
        });
    }

    static /* synthetic */ void lambda$edit$38() {
    }

    static /* synthetic */ void lambda$edit$39(LocalDate[] date, Button dateButton, LocalDate d) {
        date[0] = d;
        dateButton.setText(d.toString());
    }

    public /* synthetic */ void lambda$edit$40(final LocalDate[] date, final Button dateButton, View v) {
        pickDate(date[0], new DateResult() {
            @Override // com.steady.scheduler.MainActivity.DateResult
            public final void result(LocalDate localDate) {
                MainActivity.lambda$edit$39(date, dateButton, localDate);
            }
        });
    }

    static /* synthetic */ void lambda$edit$41() {
    }

    public /* synthetic */ void lambda$edit$42(int[] minute, boolean[] timeChosen, Switch scheduled, Button timeButton, TimePicker picker, int h, int m) {
        minute[0] = (h * 60) + m;
        timeChosen[0] = true;
        scheduled.setChecked(true);
        timeButton.setText(time(minute[0]));
    }

    public /* synthetic */ void lambda$edit$43(final int[] minute, final boolean[] timeChosen, final Switch scheduled, final Button timeButton, View v) {
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override // android.app.TimePickerDialog.OnTimeSetListener
            public final void onTimeSet(TimePicker timePicker, int i, int i2) {
                MainActivity.this.lambda$edit$42(minute, timeChosen, scheduled, timeButton, timePicker, i, i2);
            }
        }, minute[0] / 60, minute[0] % 60, true).show();
    }

    static /* synthetic */ void lambda$edit$44() {
    }

    public /* synthetic */ void lambda$edit$45(EditText editText, Spinner spinner, boolean z, Switch r14, boolean[] zArr, LocalDate[] localDateArr, int[] iArr, Item item, EditText editText2, Switch r20, EditText editText3, Switch followProject, Switch signal, AlertDialog alertDialog, View view) {
        String strTrim = editText.getText().toString().trim();
        if (strTrim.isEmpty()) {
            editText.setError("Add a title");
            return;
        }
        int selectedItemPosition = spinner.getSelectedItemPosition() + (z ? 1 : 0);
        boolean z2 = z || r14.isChecked();
        if (z2 && !zArr[0]) {
            toast("Choose a reminder time for this subtask.");
            return;
        }
        if (z2 && selectedItemPosition == 0 && localDateArr[0].atTime(iArr[0] / 60, iArr[0] % 60).atZone(ZoneId.systemDefault()).toInstant().isBefore(Instant.now())) {
            toast("Choose a future time for a one-off reminder, or turn off Add to scheduler.");
            return;
        }
        boolean z3 = item.id != 0 && done(item, item.key(LocalDate.now()));
        if (item.id != 0) {
            Alarms.cancel(this, item);
        }
        item.signalOverride = item.project>0 && followProject.isChecked() ? null : Boolean.valueOf(signal.isChecked());
        item.title = strTrim;
        item.notes = editText2.getText().toString().trim();
        item.goal = z;
        item.repeat = selectedItemPosition;
        item.minute = iArr[0];
        item.start = localDateArr[0].toString();
        item.scheduled = z2;
        item.enabled = r20.isChecked();
        if (editText3 != null) {
            item.section = editText3.getText().toString().trim();
        }
        this.store.save(item);
        if (z3 && item.repeat == 0) {
            this.store.status(item.id, item.key(LocalDate.now()), "done");
        }
        Alarms.schedule(this, item);
        alertDialog.dismiss();
        render();
        if (!z2 || Alarms.exact(this)) {
            return;
        }
        toast("Saved. Enable Alarms & reminders in Setup to activate ringing.");
    }

    private boolean future(Item item, String key) {
        if (!item.scheduled) {
            return false;
        }
        try {
            return LocalDate.parse(key).isAfter(LocalDate.now()) || LocalDate.parse(item.start).isAfter(LocalDate.now());
        } catch (Exception e) {
            return false;
        }
    }

    private void startGoal(Item item, String key) {
        if (future(item,key) || (tab==0 && day.isAfter(LocalDate.now()))) {toast("You can start this when its day arrives.");return;}
        Alarms.respond(this,item,key,"accept");render();toast("Goal started. Reflect when you complete it.");
    }

    private void complete(Item item, String key) {
        if (future(item, key) || (this.tab == 0 && this.day.isAfter(LocalDate.now()))) {
            toast("You can complete this when its day arrives.");
            return;
        }
        if (item.goal) {
            reflect(item, key);
            return;
        }
        this.store.status(item.id, key, "done");
        Alarms.quiet(this, item.id, key);
        Alarms.schedule(this, item);
        render();
    }

    private void details(final Item item, final String key) {
        LinearLayout f = form();
        f.addView(this.ui.text((item.project > 0 ? this.store.projectName(item.project) + " · " + item.section + "\n" : "") + (item.scheduled ? Rules.repeatLabel(item.repeat) + " · " + time(item.minute) + " · from " + item.start : "Not scheduled") + (item.enabled ? "" : " · Paused"), 13, UI.GREEN));
        this.ui.space(f, 12);
        if (!item.notes.isEmpty()) {
            f.addView(this.ui.text(item.notes, 16, UI.TEXT));
        }
        this.ui.space(f, 12);
        f.addView(this.ui.text("Status: " + this.store.status(item.id, key) + " · " + key, 13, UI.MUTED));
        final AlertDialog d = dialog(item.title, f, "Close", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.lambda$details$46();
            }
        });
        if (!done(item, key)) {
            this.ui.action(f, item.goal ? ("started".equals(store.status(item.id,key)) ? "Complete & reflect" : "Start goal") : "Mark complete", true, new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$details$47(d, item, key);
                }
            });
        }
        if (!item.goal) {
            this.ui.action(f, "Write a reflection & complete", false, new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$details$48(item, key, d);
                }
            });
        }
        this.ui.action(f, "Edit task / schedule", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$details$49(d, item);
            }
        });
        if (!done(item, key)) {
            this.ui.action(f, "Skip this occurrence", false, new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$details$50(item, key, d);
                }
            });
        }
        if (done(item, key) && !item.goal) {
            this.ui.action(f, "Undo completion", false, new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$details$51(item, key, d);
                }
            });
        }
        this.ui.space(f, 18);
        f.addView(this.ui.bold("PHOTOS", 12, UI.GREEN));
        for (final String path : this.store.photos(item.id)) {
            ImageView image = new ImageView(this);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            image.setImageBitmap(BitmapFactory.decodeFile(new File(getFilesDir(), path).getPath(), options));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setContentDescription("Photo attached to " + item.title);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, this.ui.dp(180.0f));
            lp.topMargin = this.ui.dp(10.0f);
            f.addView(image, lp);
            image.setOnClickListener(new View.OnClickListener() {
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.lambda$details$53(path, d, item, key, view);
                }
            });
        }
        this.ui.action(f, "+ Attach photo", false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$details$54(item, d);
            }
        });
        this.ui.space(f, 20);
        this.ui.action(f, "Delete " + (item.goal ? "goal" : "task"), false, new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$details$56(item, d);
            }
        });
    }

    static /* synthetic */ void lambda$details$46() {
    }

    public /* synthetic */ void lambda$details$47(AlertDialog d, Item item, String key) {
        d.dismiss();
        if (item.goal && !"started".equals(store.status(item.id,key))) startGoal(item,key);
        else complete(item, key);
    }

    public /* synthetic */ void lambda$details$48(Item item, String key, AlertDialog d) {
        if (future(item, key)) {
            toast("You cannot journal for a future task yet.");
        } else {
            d.dismiss();
            reflect(item, key);
        }
    }

    public /* synthetic */ void lambda$details$49(AlertDialog d, Item item) {
        d.dismiss();
        edit(item, item.goal);
    }

    public /* synthetic */ void lambda$details$50(Item item, String key, AlertDialog d) {
        Alarms.respond(this, item, key, "skip");
        d.dismiss();
        render();
    }

    public /* synthetic */ void lambda$details$51(Item item, String key, AlertDialog d) {
        this.store.status(item.id, key, "planned");
        Alarms.schedule(this, item);
        d.dismiss();
        render();
    }

    public /* synthetic */ void lambda$details$52(String path, AlertDialog d, Item item, String key, DialogInterface x, int y) {
        this.store.removePhoto(path);
        if (!this.store.photoRetained(path)) {
            new File(getFilesDir(), path).delete();
        }
        d.dismiss();
        details(item, key);
    }

    public /* synthetic */ void lambda$details$53(final String path, final AlertDialog d, final Item item, final String key, View v) {
        ImageView full = new ImageView(this);
        full.setAdjustViewBounds(true);
        full.setImageBitmap(BitmapFactory.decodeFile(new File(getFilesDir(), path).getPath()));
        new AlertDialog.Builder(this).setView(full).setPositiveButton("Close", (DialogInterface.OnClickListener) null).setNegativeButton("Remove", new DialogInterface.OnClickListener() {
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.lambda$details$52(path, d, item, key, dialogInterface, i);
            }
        }).show();
    }

    public /* synthetic */ void lambda$details$54(Item item, AlertDialog d) {
        this.photoItem = item.id;
        d.dismiss();
        Intent picker = new Intent("android.intent.action.OPEN_DOCUMENT").setType("image/*").addCategory("android.intent.category.OPENABLE");
        startActivityForResult(picker, PHOTO);
    }

    public /* synthetic */ void lambda$details$55(Item item, AlertDialog d) {
        deleteItem(item);
        d.dismiss();
        render();
    }

    public /* synthetic */ void lambda$details$56(final Item item, final AlertDialog d) {
        confirm("Delete this item and its photos? Journal entries will remain.", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$details$55(item, d);
            }
        });
    }

    private void reflect(final Item item, final String key) {
        if (future(item, key)) {
            toast("This review period has not started yet.");
            return;
        }
        LinearLayout f = form();
        f.addView(this.ui.text(item.title + " · " + key, 14, UI.GREEN));
        this.ui.space(f, 12);
        final EditText progress = this.ui.input(f, "What did you do? Be specific.", "", true);
        final EditText obstacle = this.ui.input(f, "What was difficult, or what did you learn?", "", true);
        final EditText next = this.ui.input(f, "What will you do next?", "", true);
        f.addView(this.ui.text("Write at least 20 characters in each answer. A check-in records reflection, even when the goal is still in progress.", 12, UI.MUTED));
        final AlertDialog d = dialog("Reflect & check in", f, "Save check-in", new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.lambda$reflect$57();
            }
        });
        d.getButton(-1).setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.lambda$reflect$58(progress, obstacle, next, item, key, d, view);
            }
        });
    }

    static /* synthetic */ void lambda$reflect$57() {
    }

    public /* synthetic */ void lambda$reflect$58(EditText progress, EditText obstacle, EditText next, Item item, String key, AlertDialog d, View v) {
        EditText[] editTextArr = {progress, obstacle, next};
        boolean valid = true;
        for (int i = 0; i < 3; i++) {
            EditText e = editTextArr[i];
            if (e.getText().toString().trim().length() < 20) {
                e.setError("Please add detail (at least 20 characters)");
                valid = false;
            }
        }
        if (valid) {
            this.store.journal(item, key, progress.getText().toString().trim(), obstacle.getText().toString().trim(), next.getText().toString().trim());
            Alarms.quiet(this, item.id, key);
            Alarms.schedule(this, item);
            d.dismiss();
            render();
            toast("Reflection saved.");
        }
    }

    private void deleteItem(Item item) {
        Alarms.cancel(this, item);
        for (String path : this.store.photos(item.id)) {
            if (!this.store.photoRetained(path)) {
                new File(getFilesDir(), path).delete();
            }
        }
        this.store.delete(item.id);
    }

    private void confirm(String message, final Runnable action) {
        new AlertDialog.Builder(this).setMessage(message).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).setPositiveButton("Delete", new DialogInterface.OnClickListener() {
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                action.run();
            }
        }).show();
    }

    public void bulk() {
        new BatchTaskDialog(this, ui, store, () -> {day=LocalDate.now();render();}).show();
    }

    private void sample() {
        long p = this.store.project(0L, "Solar car · example", "A sample you can edit or delete. Break the build into small, testable steps.");
        String[][] tasks = {new String[]{"Research", "Compare motor and battery requirements"}, new String[]{"Research", "Choose compatible solar charging parts"}, new String[]{"3D printing", "Measure the chassis and sketch component mounts"}, new String[]{"3D printing", "Print and test-fit the battery holder"}, new String[]{"Assembly", "Wire and test each motor independently"}, new String[]{"Testing", "Record charging and driving performance"}};
        for (String[] task : tasks) {
            Item i = new Item();
            i.project = p;
            i.section = task[0];
            i.title = task[1];
            i.scheduled = false;
            this.store.save(i);
        }
        this.projectId = p;
        render();
    }

    void accountRefreshed() {
        signalFirst = AccountCloud.preferences(this).getBoolean("signalFirst", false);
        afterBackupImport();
    }

    private void afterBackupImport() {
        projectId = 0;
        journalScreen = new JournalScreen(this, ui, store, this::render, this::exportJournalPhotos, this::lambda$journal$26);
        gym = new GymScreen(this, ui, this::render);
        render();
    }

    public void exportJournalPhotos() {
        startActivityForResult(new Intent("android.intent.action.CREATE_DOCUMENT").addCategory("android.intent.category.OPENABLE").setType("application/zip").putExtra("android.intent.extra.TITLE", "Steady-Journal-" + LocalDate.now() + ".zip"), EXPORT_PHOTOS);
    }

    public void exportJournal() {
        Intent i = new Intent("android.intent.action.CREATE_DOCUMENT").addCategory("android.intent.category.OPENABLE").setType("text/plain").putExtra("android.intent.extra.TITLE", "Steady-Journal-" + LocalDate.now() + ".txt");
        startActivityForResult(i, EXPORT);
    }

    @Override // android.app.Activity
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != -1 || data == null) {
            return;
        }
        if (request == ALARM_TONE) {
            Uri chosen = (Uri) (Build.VERSION.SDK_INT >= 33 ? data.getParcelableExtra("android.intent.extra.ringtone.PICKED_URI", Uri.class) : data.getParcelableExtra("android.intent.extra.ringtone.PICKED_URI"));
            if (chosen != null) {
                Ringtone ringtone = RingtoneManager.getRingtone(this, chosen);
                AlarmSound.choose(this, chosen, ringtone == null ? "Phone alarm tone" : ringtone.getTitle(this));
                render();
                return;
            }
            return;
        }
        if (data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        if (request == BackupScreen.SAVE || request == BackupScreen.OPEN) {
            BackupScreen.handle(this, store, request, uri, this::afterBackupImport);
            return;
        }
        if (request == ALARM_FILE) {
            toast("Importing alarm sound…");
            ExecutorService worker = Executors.newSingleThreadExecutor();
            worker.execute(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onActivityResult$64(uri);
                }
            });
            worker.shutdown();
            return;
        }
        if (request == EXPORT_PHOTOS) {
            toast("Exporting journal and photos…");
            ExecutorService worker2 = Executors.newSingleThreadExecutor();
            worker2.execute(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onActivityResult$68(uri);
                }
            });
            worker2.shutdown();
        }
        if (request == JOURNAL_PHOTO) {
            final long id = this.journalPhotoId;
            ExecutorService worker3 = Executors.newSingleThreadExecutor();
            worker3.execute(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onActivityResult$71(uri, id);
                }
            });
            worker3.shutdown();
        }
        if (request == EXPORT) {
            try {
                OutputStream out = getContentResolver().openOutputStream(uri);
                try {
                    StringBuilder s = new StringBuilder("STEADY JOURNAL\n\n");
                    for (String[] j : this.store.journals()) {
                        s.append(j[0]).append(" | Period ").append(j[1]).append("\nRecorded: ").append(Instant.ofEpochMilli(Long.parseLong(j[5]))).append("\nProgress: ").append(j[2]).append("\nObstacles / lessons: ").append(j[3]).append("\nNext step: ").append(j[4]).append("\n\n");
                    }
                    if (out == null) {
                        throw new IOException();
                    }
                    out.write(s.toString().getBytes(StandardCharsets.UTF_8));
                    toast("Journal exported.");
                    if (out != null) {
                        out.close();
                    }
                } catch (Throwable th) {
                    if (out != null) {
                        try {
                            out.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            } catch (Exception e) {
                toast("Could not export. Please choose another location.");
            }
        }
        if (request == PHOTO) {
            final long id2 = this.photoItem;
            toast("Adding photo…");
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onActivityResult$74(uri, id2);
                }
            });
            executor.shutdown();
        }
    }

    public /* synthetic */ void lambda$onActivityResult$62() {
        render();
        toast("Alarm sound saved. Use the one-minute test to try it.");
    }

    public /* synthetic */ void lambda$onActivityResult$63() {
        toast("Could not read this audio. Try an MP3, WAV or OGG under 20 MB.");
    }

    public /* synthetic */ void lambda$onActivityResult$64(Uri uri) {
        try {
            AlarmSound.importFile(this, uri);
            runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onActivityResult$62();
                }
            });
        } catch (Exception e) {
            runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onActivityResult$63();
                }
            });
        }
    }

    public /* synthetic */ List lambda$onActivityResult$65(long id) {
        return this.store.journalPhotos(id);
    }

    public /* synthetic */ void lambda$onActivityResult$66() {
        toast("Journal ZIP saved. Extract it and open journal.html.");
    }

    public /* synthetic */ void lambda$onActivityResult$67() {
        toast("Export failed. Check storage and try another location.");
    }

    public /* synthetic */ void lambda$onActivityResult$68(Uri uri) {
        try {
            OutputStream out = getContentResolver().openOutputStream(uri);
            try {
                if (out == null) {
                    throw new IOException();
                }
                JournalExport.write(out, this.store.journals(), new JournalExport.Photos() {
                    @Override // com.steady.scheduler.JournalExport.Photos
                    public final List get(long j) {
                        return MainActivity.this.lambda$onActivityResult$65(j);
                    }
                }, getFilesDir());
                runOnUiThread(new Runnable() {
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.lambda$onActivityResult$66();
                    }
                });
                if (out != null) {
                    out.close();
                }
            } catch (Throwable th) {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Throwable th2) {
                        th.addSuppressed(th2);
                    }
                }
                throw th;
            }
        } catch (Exception e) {
            runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onActivityResult$67();
                }
            });
        }
    }

    public /* synthetic */ void lambda$onActivityResult$69() {
        render();
        toast("Journal photo saved.");
    }

    public /* synthetic */ void lambda$onActivityResult$70() {
        toast("Could not save this photo.");
    }

    public /* synthetic */ void lambda$onActivityResult$71(Uri uri, long id) {
        try {
            String path = copyPhoto(uri);
            this.store.journalPhoto(id, path);
            runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onActivityResult$69();
                }
            });
        } catch (Exception e) {
            runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onActivityResult$70();
                }
            });
        }
    }

    public /* synthetic */ void lambda$onActivityResult$72(long id, String path) {
        Item item = this.store.find(id);
        if (item == null) {
            new File(getFilesDir(), path).delete();
            return;
        }
        this.store.photo(id, path);
        render();
        details(item, item.key(LocalDate.now()));
    }

    public /* synthetic */ void lambda$onActivityResult$73() {
        toast("Could not add this photo. Try a JPG or PNG under 30 MB.");
    }

    public /* synthetic */ void lambda$onActivityResult$74(Uri uri, final long id) {
        try {
            final String path = copyPhoto(uri);
            runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onActivityResult$72(id, path);
                }
            });
        } catch (Exception e) {
            runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onActivityResult$73();
                }
            });
        }
    }

    private String copyPhoto(Uri uri) throws IOException {
        File temp=File.createTempFile("photo-", ".input",getCacheDir());
        Bitmap decoded=null,rotated=null,scaled=null;File destination=null;boolean saved=false;
        try {
            try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)) {
                if(in==null)throw new IOException("Missing image");byte[] buffer=new byte[8192];long count=0;int n;
                while((n=in.read(buffer))!=-1){count+=n;if(count>31457280)throw new IOException("Photo too large");out.write(buffer,0,n);}
            }
            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeFile(temp.getPath(),opts);
            if(opts.outWidth<1||opts.outHeight<1)throw new IOException("Unsupported image");
            opts.inSampleSize=1;while(Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>2000)opts.inSampleSize*=2;
            opts.inJustDecodeBounds=false;decoded=BitmapFactory.decodeFile(temp.getPath(),opts);if(decoded==null)throw new IOException("Unsupported image");
            int orientation=new ExifInterface(temp.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);Matrix m=new Matrix();
            switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(-90);m.postScale(-1,1);break;case 8:m.setRotate(-90);break;}
            rotated=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),m,true);
            float factor=Math.min(1f,1600f/Math.max(rotated.getWidth(),rotated.getHeight()));
            scaled=Bitmap.createScaledBitmap(rotated,Math.max(1,(int)(rotated.getWidth()*factor)),Math.max(1,(int)(rotated.getHeight()*factor)),true);
            File dir=new File(getFilesDir(),"photos");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create photo directory");
            String path="photos/"+UUID.randomUUID()+".jpg";destination=new File(getFilesDir(),path);
            try(OutputStream out=new FileOutputStream(destination)){if(!scaled.compress(Bitmap.CompressFormat.JPEG,88,out))throw new IOException("Cannot save photo");}
            saved=true;return path;
        }finally{
            temp.delete();if(!saved&&destination!=null)destination.delete();
            if(scaled!=null)scaled.recycle();if(rotated!=null&&rotated!=scaled)rotated.recycle();if(decoded!=null&&decoded!=rotated&&decoded!=scaled)decoded.recycle();
        }
    }
}
