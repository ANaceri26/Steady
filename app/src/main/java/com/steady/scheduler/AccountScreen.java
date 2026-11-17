package com.steady.scheduler;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.text.InputType;
import android.widget.*;
import java.util.concurrent.*;

final class AccountScreen {
    interface Work { String run() throws Exception; }
    static void add(MainActivity activity, UI ui, LinearLayout body) {
        LinearLayout card=ui.card(body);
        card.addView(ui.bold("Your Steady account",20,UI.TEXT));
        boolean guest=Store.workspace(activity).equals("guest");
        card.addView(ui.text(guest?"Sign in to connect this phone with the web app. Your existing data stays in a separate guest workspace.":AccountCloud.preferences(activity).getString("email","Connected account"),14,UI.MUTED));
        if(!AccountCloud.configured()){card.addView(ui.text("Account connection is not configured in this build.",13,UI.MUTED));return;}
        if(guest){
            ui.action(card,"Sign in",true,()->credentials(activity,ui,"Sign in"));
            ui.action(card,"Create account",false,()->credentials(activity,ui,"Create account"));
        }else{
            ui.action(card,"Sync now",true,()->synchronize(activity,false));
            ui.action(card,"Account preferences",false,()->preferences(activity,ui));
            ui.action(card,"Sign out",false,()->new AlertDialog.Builder(activity).setTitle("Sign out?")
                    .setMessage("Your account workspace stays on this phone for your next sign-in. Guest data is separate. Sync first to send any changes to your other devices.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Sign out",(d,n)->{
                        new AccountCloud(activity).clearSession();Store.activate(activity,"guest");restart(activity);
                    }).show());
            card.addView(ui.text("Use Sync now after editing on either device. Offline changes stay on this phone until you sync. If both devices changed, Steady asks before replacing anything.",13,UI.MUTED));
        }
        ui.action(card,"Reset password",false,()->credentials(activity,ui,"Reset password"));
    }
    private static void credentials(MainActivity a,UI ui,String mode){
        LinearLayout form=ui.col();form.setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(8));
        EditText email=ui.input(form,"Email","",false);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password=mode.equals("Reset password")?null:ui.input(form,"Password (12+ characters for a new account)","",false);
        if(password!=null)password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle(mode).setView(form).setNegativeButton("Cancel",null).setPositiveButton(mode,null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            String address=email.getText().toString().trim(),secret=password==null?"":password.getText().toString();
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()){email.setError("Enter a valid email.");return;}
            if(password!=null&&secret.isEmpty()){password.setError("Enter your password.");return;}
            dialog.dismiss();run(a,()->{
                AccountCloud cloud=new AccountCloud(a);
                if(mode.equals("Reset password")){cloud.resetPassword(address);return "If an account exists, check its email. Finish the reset in the web app.";}
                if(mode.equals("Create account")){cloud.signUp(address,secret);return "Check your email to confirm the account, then sign in.";}
                String owner=cloud.signIn(address,secret).getString("id");
                // Switch on the UI thread after the network request completes.
                return "SIGNED_IN:"+owner+":"+address;
            });
        }));dialog.show();
    }
    private static void preferences(MainActivity a,UI ui){
        LinearLayout form=ui.col();form.setPadding(ui.dp(20),ui.dp(8),ui.dp(20),ui.dp(8));
        android.content.SharedPreferences p=AccountCloud.preferences(a);
        EditText name=ui.input(form,"Display name",p.getString("displayName",""),false);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        android.widget.CheckBox first=new android.widget.CheckBox(a);first.setText("Show Signal tasks first");first.setChecked(p.getBoolean("signalFirst",false));form.addView(first);
        new AlertDialog.Builder(a).setTitle("Account preferences").setView(form).setNegativeButton("Cancel",null)
                .setPositiveButton("Save",(d,n)->{p.edit().putString("displayName",name.getText().toString()).putBoolean("signalFirst",first.isChecked()).apply();a.accountRefreshed();}).show();
    }
    static void synchronize(MainActivity a,boolean replace){run(a,()->new AccountCloud(a).sync(replace));}
    private static void restart(MainActivity a){a.startActivity(new Intent(a,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK));a.finish();}
    private static void run(MainActivity a,Work work){
        int orientation=a.getRequestedOrientation();a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        ProgressDialog progress=new ProgressDialog(a);progress.setMessage("Connecting to your Steady account…");progress.setCancelable(false);progress.show();
        ExecutorService worker=Executors.newSingleThreadExecutor();worker.execute(()->{
            String result;boolean conflict=false;
            try{result=work.run();}catch(AccountCloud.Conflict e){result=e.getMessage();conflict=true;}catch(Exception e){result=e.getMessage()==null?"Connection failed. Your local data is still available.":e.getMessage();}
            final String message=result;final boolean collided=conflict;
            a.runOnUiThread(()->{if(a.isDestroyed())return;progress.dismiss();a.setRequestedOrientation(orientation);
                if(message.startsWith("SIGNED_IN:")){String[] parts=message.split(":",3);Store.activate(a,parts[1]);AccountCloud.preferences(a).edit().putString("email",parts[2]).apply();restart(a);return;}
                a.accountRefreshed();
                if(collided)new AlertDialog.Builder(a).setTitle("Both devices have changes")
                        .setMessage("Nothing was overwritten. Save a full local backup in Settings first. Loading the account copy replaces this phone's workspace.")
                        .setNegativeButton("Keep local data",null).setPositiveButton("Load account copy",(d,n)->new AlertDialog.Builder(a).setTitle("Replace this phone's workspace?").setMessage("Continue only after saving a local backup if you need these changes.")
                                .setNegativeButton("Cancel",null).setPositiveButton("Replace",(x,y)->synchronize(a,true)).show()).show();
                else Toast.makeText(a,message,Toast.LENGTH_LONG).show();
            });
        });worker.shutdown();
    }
}
