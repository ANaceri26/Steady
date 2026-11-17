package com.steady.scheduler;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class DemoLayoutTest {
    private Store store;private MainActivity a;private ActivityController<MainActivity> controller;
    @Before public void open() throws Exception {
        Field singleton=Store.class.getDeclaredField("instance");singleton.setAccessible(true);
        Store old=(Store)singleton.get(null);if(old!=null)old.close();singleton.set(null,null);
        RuntimeEnvironment.getApplication().deleteDatabase("steady.db");
        controller=Robolectric.buildActivity(MainActivity.class).setup();a=controller.get();store=Store.get(a);
    }
    @After public void close(){controller.pause().stop().destroy();store.close();}
    private List<View> descendants(View v){List<View> out=new ArrayList<>();out.add(v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)out.addAll(descendants(g.getChildAt(i)));}return out;}
    private View layout(int tab,int width,int height) throws Exception {
        Field current=MainActivity.class.getDeclaredField("tab");current.setAccessible(true);current.setInt(a,tab);a.render();
        View root=((ViewGroup)a.findViewById(android.R.id.content)).getChildAt(0);
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
        for(View view:descendants(root)){
            if(view instanceof Button){Button b=(Button)view;assertTrue("48dp touch target: "+b.getText(),b.getHeight()>=48);
                if(b.getMaxLines()==1)assertTrue("Clipped button: "+b.getText(),b.getWidth()-b.getPaddingLeft()-b.getPaddingRight()+1>=b.getPaint().measureText(b.getText().toString()));
            }
            if(view instanceof EditText)assertTrue("Visible text field",view.getHeight()>=48);
        }
        return root;
    }
    private void capture(View root,String name) throws Exception {
        File dir=new File("build/previews/"+BuildConfig.FLAVOR);assertTrue(dir.isDirectory()||dir.mkdirs());
        Bitmap bitmap=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    }
    private void seed(){
        long project=store.project(0,"Build a solar rover","A small build. Real lessons.",true);
        String[] titles={"Prototype the motor driver","Read for twenty minutes","Write up the bench test"};
        for(int n=0;n<3;n++){Item item=new Item();item.title=titles[n];item.repeat=1;item.minute=540+n*120;item.project=n==1?0:project;item.notes=n==0?"Compare power draw before choosing the battery.":"";store.save(item);if(n==1)store.status(item.id,item.start,"done");}
        Item goal=new Item();goal.title="Make something every week";goal.goal=true;goal.repeat=2;goal.signalOverride=true;store.save(goal);
        JournalRepository repo=new JournalRepository(store);long engineering=repo.saveGroup(0,"Engineering");repo.saveGroup(0,"Morning");repo.saveGroup(0,"Investing");
        JournalRepository.Entry entry=new JournalRepository.Entry();entry.title="Measure before you optimize";entry.kind="Advice";entry.groupId=engineering;entry.project="Solar rover";entry.progress="The first bench test showed where the power actually went.";entry.lessons="A quick measurement beat an afternoon of guessing.";entry.next="Log current draw with the new motor driver.";repo.save(entry);
    }
    @Test public void renderAllPagesAndCheckControlsAtPhoneWidths() throws Exception {
        String[] pages={"today","projects","goals","journal","gym","settings"};
        for(int tab=0;tab<6;tab++)capture(layout(tab,360,800),pages[tab]+"-empty");
        seed();for(int tab=0;tab<6;tab++)capture(layout(tab,360,800),pages[tab]+"-filled");
    }
    @Test public void narrowPhoneWithLargerTextKeepsControlsReadable() throws Exception {
        RuntimeEnvironment.setFontScale(1.3f);seed();
        for(int tab:new int[]{0,3})capture(layout(tab,320,760),(tab==0?"today":"journal")+"-large-text");
    }
}
