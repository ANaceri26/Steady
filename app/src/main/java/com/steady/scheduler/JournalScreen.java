package com.steady.scheduler;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.BitmapFactory;
import android.widget.*;
import android.text.Editable;
import android.text.TextWatcher;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

public final class JournalScreen {
    private final Activity activity;
    private final UI ui;
    private final Store store;
    private final JournalRepository repo;
    private final Runnable refresh, export;
    private final LongConsumer photo;
    public long selectedGroup=-1;
    public String search="";
    private int visibleCount=30;

    public JournalScreen(Activity a, UI ui, Store store, Runnable refresh, Runnable export, LongConsumer photo) {
        this.activity=a;this.ui=ui;this.store=store;this.repo=new JournalRepository(store);this.refresh=refresh;this.export=export;this.photo=photo;
    }
    public void render(LinearLayout body) {
        ui.eyebrow(body,"YOUR PERSONAL KNOWLEDGE LIBRARY");
        body.addView(ui.bold("Keep what you learn.",30,UI.TEXT));
        body.addView(ui.text("Capture advice, reflect on your day, and collect evidence for your next portfolio project.",15,UI.MUTED));
        ui.space(body,16);
        LinearLayout actions=ui.row();body.addView(actions);
        ui.weight(actions,ui.button("+ Write",true,()->edit(new JournalRepository.Entry())));
        ui.weight(actions,ui.button("Export all",false,export));
        ui.space(body,12);
        HorizontalScrollView scroller=new HorizontalScrollView(activity);scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout bubbles=ui.row();scroller.addView(bubbles);body.addView(scroller);
        bubble(bubbles,"All",-1);bubble(bubbles,"Ungrouped",0);
        for(JournalRepository.Group g:repo.groups())bubble(bubbles,g.name,g.id);
        bubbles.addView(ui.button("+ Group",false,()->groupEditor(0,"")));
        if(selectedGroup>0) {
            for(JournalRepository.Group g:repo.groups())if(g.id==selectedGroup)ui.action(body,"Manage “"+g.name+"”",false,()->groupEditor(g.id,g.name));
        }
        EditText query=ui.input(body,"Search titles, notes, lessons, or projects",search,false);
        query.setContentDescription("Search journal entries");
        LinearLayout list=ui.col();body.addView(list);ui.space(body,8);
        renderEntries(list);
        query.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){search=s.toString();visibleCount=30;renderEntries(list);}
            public void afterTextChanged(Editable e){}
        });
    }
    private void bubble(LinearLayout row,String name,long id) {
        Button b=ui.button(name,selectedGroup==id,()->{selectedGroup=id;visibleCount=30;refresh.run();});
        b.setSelected(selectedGroup==id);b.setContentDescription(name+(selectedGroup==id?", selected":""));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=ui.dp(8);row.addView(b,lp);
    }
    private void renderEntries(LinearLayout list) {
        list.removeAllViews();ui.space(list,16);
        List<JournalRepository.Entry> entries=repo.entries(selectedGroup,search);
        list.addView(ui.text(entries.size()+" entries",12,UI.MUTED));ui.space(list,12);
        if(entries.isEmpty())ui.empty(list,3,"Room for your next idea",search.isEmpty()?"Write a journal, save advice, or document a project. Existing entries are in All and Ungrouped.":"No matches. Try another search or group.",false);
        for(JournalRepository.Entry e:entries.subList(0,Math.min(visibleCount,entries.size()))) {
            LinearLayout card=ui.card(list);
            card.addView(ui.text(e.kind+" · "+e.date,12,UI.GREEN));
            card.addView(ui.bold(e.title,21,UI.TEXT));
            if(!e.project.isEmpty())card.addView(ui.text("Project: "+e.project,13,UI.GREEN));
            section(card,e.kind.equals("Advice")?"ADVICE / TAKEAWAY":"PROGRESS / REFLECTION",e.progress);
            section(card,"LESSONS & OBSTACLES",e.lessons);section(card,"NEXT STEP",e.next);
            for(String path:store.journalPhotos(e.id)) {
                ImageView image=new ImageView(activity);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;
                image.setImageBitmap(BitmapFactory.decodeFile(new File(activity.getFilesDir(),path).getPath(),options));image.setAdjustViewBounds(true);image.setContentDescription("Photo for "+e.title);
                card.addView(image,new LinearLayout.LayoutParams(-1,ui.dp(160)));
            }
            LinearLayout buttons=ui.row();card.addView(buttons);
            ui.weight(buttons,ui.button("Edit",false,()->edit(e)));
            ui.weight(buttons,ui.button("Move",false,()->move(e)));
            ui.action(card,"+ Photo",false,()->photo.accept(e.id));
        }
        if(entries.size()>visibleCount)ui.action(list,"Show more entries",false,()->{visibleCount+=30;renderEntries(list);});
    }
    private void section(LinearLayout card,String label,String text) {
        if(text.isEmpty())return;ui.space(card,12);card.addView(ui.bold(label,11,UI.GREEN));TextView t=ui.text(text,15,UI.TEXT);t.setTextIsSelectable(true);card.addView(t);
    }
    private LinearLayout form(){LinearLayout f=ui.col();f.setPadding(ui.dp(20),ui.dp(12),ui.dp(20),ui.dp(12));return f;}
    private AlertDialog dialog(String title,LinearLayout form,String action) {
        ScrollView scroll=new ScrollView(activity);scroll.addView(form);
        return new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton(action,null).show();
    }
    private Spinner spinner(LinearLayout f,String label,List<?> options,int selected) {
        f.addView(ui.text(label,13,UI.MUTED));Spinner s=new Spinner(activity);s.setContentDescription(label);
        ArrayAdapter<Object> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,new ArrayList<Object>(options));adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(adapter);s.setSelection(selected);f.addView(s);return s;
    }
    public void edit(JournalRepository.Entry entry) {
        LinearLayout f=form();
        EditText title=ui.input(f,"Title",entry.title,false);
        List<String> types=java.util.Arrays.asList("Journal","Advice","Project log");
        Spinner kind=spinner(f,"Entry type",types,Math.max(0,types.indexOf(entry.kind)));
        List<JournalRepository.Group> groups=new ArrayList<>();groups.add(new JournalRepository.Group(0,"Ungrouped"));groups.addAll(repo.groups());
        long chosen=entry.id==0?Math.max(0,selectedGroup):entry.groupId;int index=0;for(int i=0;i<groups.size();i++)if(groups.get(i).id==chosen)index=i;
        Spinner group=spinner(f,"Group",groups,index);
        EditText project=ui.input(f,"Project name (optional, kept for your portfolio)",entry.project,false);
        List<Store.Project> projects=store.projects();
        if(!projects.isEmpty())ui.action(f,"Choose an existing project",false,()->{
            String[] names=new String[projects.size()];for(int i=0;i<names.length;i++)names[i]=projects.get(i).title;
            new AlertDialog.Builder(activity).setTitle("Project").setItems(names,(d,n)->project.setText(names[n])).show();
        });
        EditText progress=ui.input(f,"What happened, what did you build, or what advice will you keep?",entry.progress,true);
        EditText lessons=ui.input(f,"Lessons, decisions, tools used, or obstacles (optional)",entry.lessons,true);
        EditText next=ui.input(f,"Next step or how to apply this (optional)",entry.next,true);
        AlertDialog d=dialog(entry.id==0?"Capture something useful":"Edit entry",f,"Save");
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            JournalRepository.Entry draft=new JournalRepository.Entry();draft.id=entry.id;draft.date=entry.date;draft.title=title.getText().toString();draft.kind=types.get(kind.getSelectedItemPosition());draft.groupId=groups.get(group.getSelectedItemPosition()).id;draft.project=project.getText().toString();draft.progress=progress.getText().toString();draft.lessons=lessons.getText().toString();draft.next=next.getText().toString();
            try {repo.save(draft);d.dismiss();refresh.run();}catch(IllegalArgumentException ex){progress.setError(ex.getMessage());}
        });
    }
    private void move(JournalRepository.Entry e) {
        List<JournalRepository.Group> groups=new ArrayList<>();groups.add(new JournalRepository.Group(0,"Ungrouped"));groups.addAll(repo.groups());
        String[] names=new String[groups.size()];for(int i=0;i<names.length;i++)names[i]=groups.get(i).name;
        new AlertDialog.Builder(activity).setTitle("Move entry").setItems(names,(d,n)->{repo.move(e.id,groups.get(n).id);refresh.run();}).setNegativeButton("Cancel",null).show();
    }
    private void groupEditor(long id,String current) {
        LinearLayout f=form();EditText name=ui.input(f,"Group name",current,false);AlertDialog d=dialog(id==0?"New group":"Manage group",f,"Save");
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {selectedGroup=repo.saveGroup(id,name.getText().toString());d.dismiss();refresh.run();}
            catch(SQLiteConstraintException ex){name.setError("A group with that name already exists.");}
            catch(IllegalArgumentException ex){name.setError(ex.getMessage());}
        });
        if(id>0)ui.action(f,"Delete group",false,()->new AlertDialog.Builder(activity).setTitle("Delete this group?").setMessage("Your entries will be kept in Ungrouped.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(x,n)->{repo.deleteGroup(id);selectedGroup=-1;d.dismiss();refresh.run();}).show());
    }
}
