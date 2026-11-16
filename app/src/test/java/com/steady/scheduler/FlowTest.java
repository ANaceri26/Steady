package com.steady.scheduler;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class FlowTest {
    private ActivityController<MainActivity> controller;private MainActivity a;private Store store;
    @Before public void setup() throws Exception {
        Field instance=Store.class.getDeclaredField("instance");instance.setAccessible(true);Store old=(Store)instance.get(null);if(old!=null)old.close();instance.set(null,null);
        RuntimeEnvironment.getApplication().deleteDatabase("steady.db");controller=Robolectric.buildActivity(MainActivity.class).setup();a=controller.get();store=Store.get(a);
    }
    @After public void close(){controller.pause().stop().destroy();store.close();}
    private void collect(View v,List<EditText> out){if(v instanceof EditText)out.add((EditText)v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),out);}}
    private List<EditText> fields(AlertDialog dialog){List<EditText> out=new ArrayList<>();collect(dialog.getWindow().getDecorView(),out);return out;}
    private View findDescription(View v,String description){
        if(description.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=findDescription(g.getChildAt(i),description);if(found!=null)return found;}}
        return null;
    }
    @Test public void batchSavesTheTimeChosenBesideTheTask(){
        a.bulk();AlertDialog paste=ShadowAlertDialog.getLatestAlertDialog();fields(paste).get(0).setText("Morning routine");paste.getButton(-1).performClick();
        AlertDialog review=ShadowAlertDialog.getLatestAlertDialog();findDescription(review.getWindow().getDecorView(),"Time for task 1").performClick();
        TimePickerDialog clock=(TimePickerDialog)ShadowDialog.getLatestDialog();
        int pickerId=a.getResources().getIdentifier("timePicker","id","android");TimePicker picker=clock.findViewById(pickerId);picker.setHour(7);picker.setMinute(15);clock.getButton(-1).performClick();
        review.getButton(-1).performClick();assertEquals(435,store.items().get(0).minute);
    }
    @Test public void goalStartsWithoutReflectionAndCompletesOnlyAfterSave() throws Exception {
        Item goal=new Item();goal.title="Build prototype";goal.goal=true;goal.repeat=1;store.save(goal);
        Method start=MainActivity.class.getDeclaredMethod("startGoal",Item.class,String.class);start.setAccessible(true);start.invoke(a,goal,goal.start);
        assertEquals("started",store.status(goal.id,goal.start));assertTrue(store.journals().isEmpty());assertNull(ShadowAlertDialog.getLatestAlertDialog());
        Method complete=MainActivity.class.getDeclaredMethod("complete",Item.class,String.class);complete.setAccessible(true);complete.invoke(a,goal,goal.start);
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertEquals("started",store.status(goal.id,goal.start));
        dialog.getButton(-1).performClick();assertTrue(store.journals().isEmpty());
        for(EditText f:fields(dialog))f.setText("A detailed observation about the prototype.");dialog.getButton(-1).performClick();
        assertEquals("done",store.status(goal.id,goal.start));assertEquals(1,store.journals().size());
    }
    @Test public void batchReviewDoesNotSaveUntilConfirmedAndRejectsBlankRows(){
        a.bulk();AlertDialog paste=ShadowAlertDialog.getLatestAlertDialog();fields(paste).get(0).setText("Study\nWorkout");paste.getButton(-1).performClick();
        AlertDialog review=ShadowAlertDialog.getLatestAlertDialog();assertTrue(store.items().isEmpty());
        List<EditText> names=fields(review);assertEquals(2,names.size());names.get(1).setText("");review.getButton(-1).performClick();assertTrue(store.items().isEmpty());
        names.get(1).setText("Training");review.getButton(-1).performClick();assertEquals(2,store.items().size());assertEquals(540,store.items().get(0).minute);assertEquals(570,store.items().get(1).minute);
    }
    @Test public void allMainTabsRender() throws Exception {
        Field tab=MainActivity.class.getDeclaredField("tab");tab.setAccessible(true);Method render=MainActivity.class.getDeclaredMethod("render");render.setAccessible(true);
        for(int i=0;i<6;i++){tab.setInt(a,i);render.invoke(a);assertNotNull(a.findViewById(android.R.id.content));}
    }
}
