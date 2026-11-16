package com.steady.scheduler;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class AlarmSound {
    private AlarmSound() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("alarm_sound", 0);
    }

    public static Uri uri(Context c) {
        String s = prefs(c).getString("uri", "");
        return s.isEmpty() ? builtIn(c) : Uri.parse(s);
    }

    public static Uri builtIn(Context c) {
        return Uri.parse("android.resource://" + c.getPackageName() + "/" + R.raw.steady_alarm);
    }

    public static String name(Context c) {
        return prefs(c).getString("name", "Steady · built-in alarm");
    }

    public static void choose(Context c, Uri uri, String name) {
        prefs(c).edit().remove("last_error").putString("uri", uri == null ? "" : uri.toString()).putString("name", name).apply();
    }

    public static AudioAttributes attributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    }

    public static void importFile(Context c, Uri source) throws IOException {
        File file=File.createTempFile("alarm-", ".audio", c.getFilesDir());boolean saved=false;
        try {
            try(InputStream in=c.getContentResolver().openInputStream(source);OutputStream out=new FileOutputStream(file)) {
                if(in==null)throw new IOException("No audio");byte[] buffer=new byte[8192];long total=0;int n;
                while((n=in.read(buffer))!=-1){total+=n;if(total>20971520)throw new IOException("Choose a file under 20 MB");out.write(buffer,0,n);}
            }
            MediaPlayer probe=new MediaPlayer();
            try {probe.setDataSource(file.getPath());probe.prepare();if(probe.getDuration()<1)throw new IOException("No playable audio");}
            catch(RuntimeException ex){throw new IOException("Unsupported audio",ex);}finally{probe.release();}
            String title="Custom audio";
            try(Cursor cur=c.getContentResolver().query(source,new String[]{"_display_name"},null,null,null)){if(cur!=null&&cur.moveToFirst())title=cur.getString(0);}catch(Exception ignored){}
            Uri old=uri(c);choose(c,Uri.fromFile(file),title);saved=true;
            if("file".equals(old.getScheme()) && old.getPath()!=null){File previous=new File(old.getPath()).getCanonicalFile();if(c.getFilesDir().getCanonicalFile().equals(previous.getParentFile()))previous.delete();}
        }finally{if(!saved)file.delete();}
    }
}
