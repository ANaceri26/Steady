package com.steady.scheduler;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.database.sqlite.SQLiteDatabase;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BatchTaskDialog {
    private final Activity a;private final UI ui;private final Store store;private final Runnable refresh;
    public BatchTaskDialog(Activity a,UI ui,Store store,Runnable refresh){this.a=a;this.ui=ui;this.store=store;this.refresh=refresh;}
    private LinearLayout form(){LinearLayout f=ui.col();f.setPadding(ui.dp(20),ui.dp(12),ui.dp(20),ui.dp(12));return f;}
    private AlertDialog dialog(String title,LinearLayout f,String positive){ScrollView scroll=new ScrollView(a);scroll.addView(f);return new AlertDialog.Builder(a).setTitle(title).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton(positive,null).show();}
    private String time(int minute){return String.format(Locale.getDefault(),"%02d:%02d",minute/60,minute%60);}
    public void show(){
        LinearLayout f=form();f.addView(ui.text("Paste one daily task per line. Choose each time before saving.",14,UI.MUTED));
        EditText lines=ui.input(f,"Your daily tasks","",true);lines.setMinLines(6);
        AlertDialog d=dialog("Paste tasks",f,"Review times");
        d.getButton(-1).setOnClickListener(v->{try{List<String> names=TaskBatch.parse(lines.getText().toString());review(names);d.dismiss();}catch(IllegalArgumentException e){lines.setError(e.getMessage());}});
    }
    private void review(List<String> names){
        LinearLayout f=form();f.addView(ui.text("Daily reminders · tap a time to change it. Nothing is saved until you tap Add tasks.",14,UI.MUTED));
        List<EditText> titles=new ArrayList<>();int[] minutes=new int[names.size()];
        for(int n=0;n<names.size();n++){
            final int index=n;minutes[n]=540+n*30;
            LinearLayout row=ui.row();f.addView(row);EditText name=new EditText(a);name.setText(names.get(n));name.setTextColor(UI.TEXT);name.setSingleLine(true);name.setContentDescription("Task "+(n+1)+" name");ui.weight(row,name);titles.add(name);
            Button clock=ui.button(time(minutes[n]),false,()->{});clock.setContentDescription("Time for task "+(n+1));row.addView(clock);
            clock.setOnClickListener(v->new TimePickerDialog(a,(picker,h,m)->{minutes[index]=h*60+m;clock.setText(time(minutes[index]));},minutes[index]/60,minutes[index]%60,android.text.format.DateFormat.is24HourFormat(a)).show());
        }
        AlertDialog d=dialog("Review daily tasks",f,"Add tasks");
        d.getButton(-1).setOnClickListener(v->{
            List<Item> items=new ArrayList<>();
            for(int n=0;n<titles.size();n++){
                EditText title=titles.get(n);String name=title.getText().toString().trim();
                if(name.isEmpty()||name.length()>200){title.setError("Use a title of 1–200 characters");return;}
                TaskBatch.validateTime(minutes[n]);Item item=new Item();item.title=name;item.repeat=1;item.minute=minutes[n];items.add(item);
            }
            SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
            try{for(Item item:items)store.save(item);db.setTransactionSuccessful();}finally{db.endTransaction();}
            for(Item item:items)Alarms.schedule(a,item);
            d.dismiss();refresh.run();Toast.makeText(a,"Daily tasks saved with your chosen times.",Toast.LENGTH_SHORT).show();
        });
    }
}
