package com.steady.scheduler;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Passwords go directly to the auth provider. Only encrypted session tokens are retained. */
final class AccountCloud {
    private final Context context;
    private static final String ALIAS = "steady-account-session";
    AccountCloud(Context c) { context = c.getApplicationContext(); }
    static boolean configured() { return BuildConfig.SUPABASE_URL.startsWith("https://") && !BuildConfig.SUPABASE_KEY.isEmpty(); }
    static SharedPreferences preferences(Context c) { return c.getSharedPreferences("account-" + Store.workspace(c), 0); }
    static JSONObject settings(Context c) throws JSONException {
        SharedPreferences p = preferences(c);
        return new JSONObject().put("displayName", p.getString("displayName", "")).put("signalFirst", p.getBoolean("signalFirst", false));
    }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            return generator.generateKey();
        }
        return (SecretKey)store.getKey(ALIAS, null);
    }
    private void saveSession(JSONObject session) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        String encrypted = Base64.encodeToString(cipher.doFinal(session.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        boolean saved=context.getSharedPreferences("steady-session",0).edit().putString("token", encrypted)
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).commit();
        if (!saved) throw new IOException("Could not save your session.");
    }
    private JSONObject session() throws Exception {
        SharedPreferences p = context.getSharedPreferences("steady-session",0);
        if (!p.contains("token")) throw new IOException("Sign in to connect your account.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(p.getString("iv",""), Base64.NO_WRAP)));
        return new JSONObject(new String(cipher.doFinal(Base64.decode(p.getString("token",""), Base64.NO_WRAP)), StandardCharsets.UTF_8));
    }
    void clearSession() { context.getSharedPreferences("steady-session",0).edit().clear().commit(); }
    JSONObject signIn(String email, String password) throws Exception {
        JSONObject response=json("POST","/auth/v1/token?grant_type=password",new JSONObject().put("email",email).put("password",password),null);
        saveSession(response); return response.getJSONObject("user");
    }
    void signUp(String email,String password) throws Exception {
        if(password.length()<12)throw new IOException("Use at least 12 characters for your password.");
        json("POST","/auth/v1/signup",new JSONObject().put("email",email).put("password",password),null);
    }
    void resetPassword(String email) throws Exception {
        String path="/auth/v1/recover";
        if(!BuildConfig.WEB_URL.isEmpty())path+="?redirect_to="+URLEncoder.encode(BuildConfig.WEB_URL,"UTF-8");
        json("POST",path,new JSONObject().put("email",email),null);
    }
    private String token() throws Exception {
        JSONObject old=session();
        String uid=old.getJSONObject("user").getString("id");
        if(!uid.equals(Store.workspace(context)))throw new IOException("Sign in again to this workspace.");
        JSONObject fresh=json("POST","/auth/v1/token?grant_type=refresh_token",new JSONObject().put("refresh_token",old.getString("refresh_token")),null);
        if(!uid.equals(fresh.getJSONObject("user").getString("id")))throw new IOException("Account mismatch.");
        saveSession(fresh); return fresh.getString("access_token");
    }
    private JSONObject json(String method,String path,JSONObject body,String token) throws Exception {
        byte[] bytes=request(method,path,body==null?null:body.toString().getBytes(StandardCharsets.UTF_8),"application/json",token,1024*1024);
        return bytes.length==0?new JSONObject():new JSONObject(new String(bytes,StandardCharsets.UTF_8));
    }
    private byte[] request(String method,String path,byte[] body,String contentType,String token,int limit) throws Exception {
        if(!configured())throw new IOException("Account connection is not configured in this build.");
        HttpURLConnection connection=(HttpURLConnection)new URL(BuildConfig.SUPABASE_URL+path).openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(30000);
        connection.setRequestMethod(method);connection.setRequestProperty("apikey",BuildConfig.SUPABASE_KEY);
        if(token!=null)connection.setRequestProperty("Authorization","Bearer "+token);
        try {
            if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type",contentType);connection.setFixedLengthStreamingMode(body.length);try(OutputStream out=connection.getOutputStream()){out.write(body);}}
            int code=connection.getResponseCode();InputStream source=code>=200&&code<300?connection.getInputStream():connection.getErrorStream();
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            if(source!=null)try(InputStream in=source){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("Server response exceeds supported size.");out.write(buffer,0,n);}}
            byte[] bytes=out.toByteArray();
            if(code<200||code>=300){String raw=new String(bytes,StandardCharsets.UTF_8);if(raw.contains("SYNC_CONFLICT"))throw new Conflict();
                String message="Account request failed ("+code+").";try{JSONObject error=new JSONObject(raw);message=error.optString("msg",error.optString("message",error.optString("error_description",message)));}catch(JSONException ignored){}
                throw new IOException(message);
            }
            return bytes;
        } finally { connection.disconnect(); }
    }
    static final class Conflict extends IOException { Conflict(){super("Both devices have changes. Export a local backup before loading the account copy.");} }
    static String fingerprint(byte[] bytes,JSONObject settings) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(bytes);digest.update(settings.toString().getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(digest.digest(),Base64.NO_WRAP);
    }
    private byte[] backup() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        OutputStream limited=new OutputStream(){
            public void write(int value) throws IOException { if(bytes.size()>=20*1024*1024)throw new IOException("Use an offline backup for workspaces over 20 MB.");bytes.write(value); }
            public void write(byte[] data,int offset,int length) throws IOException { if(bytes.size()+length>20*1024*1024)throw new IOException("Use an offline backup for workspaces over 20 MB.");bytes.write(data,offset,length); }
        };
        PortableBackup.write(Store.get(context),context.getFilesDir(),limited);return bytes.toByteArray();
    }
    synchronized String sync(boolean replace) throws Exception {
        String token=token(),owner=Store.workspace(context);SharedPreferences p=preferences(context);
        byte[] response=request("GET","/rest/v1/steady_workspaces?select=*&user_id=eq."+owner,null,"application/json",token,1024*1024);
        JSONArray rows=new JSONArray(new String(response,StandardCharsets.UTF_8));JSONObject remote=rows.length()==0?null:rows.getJSONObject(0);
        byte[] current=backup();JSONObject settings=settings(context);String hash=fingerprint(current,settings);
        boolean empty=true;for(String table:PortableBackup.TABLES)try(android.database.Cursor c=Store.get(context).getReadableDatabase().rawQuery("SELECT 1 FROM "+table+" LIMIT 1",null)){if(c.moveToFirst())empty=false;}
        boolean dirty=p.contains("hash")?!hash.equals(p.getString("hash","")):!empty||!settings.getString("displayName").isEmpty()||settings.getBoolean("signalFirst");
        long revision=p.getLong("revision",0);
        if(remote!=null&&(replace||remote.getLong("revision")!=revision)){
            if(dirty&&!replace)throw new Conflict();
            String path=remote.getString("object_path");if(!path.matches(java.util.regex.Pattern.quote(owner)+"/[0-9a-f-]+\\.zip"))throw new IOException("Invalid account file path.");
            byte[] bytes=request("GET","/storage/v1/object/authenticated/steady-backups/"+path,null,"application/zip",token,20*1024*1024);
            Store store=Store.get(context);List<Item> old=store.items();List<String[]> snoozes=store.snoozes();
            JSONObject prefs=remote.getJSONObject("settings");String name=prefs.getString("displayName");boolean first=prefs.getBoolean("signalFirst");if(name.length()>80)throw new IOException("Invalid account preferences.");
            PortableBackup.restore(store,context.getFilesDir(),new ByteArrayInputStream(bytes));
            p.edit().putString("displayName",name).putBoolean("signalFirst",first).commit();
            p.edit().putLong("revision",remote.getLong("revision")).putString("hash",fingerprint(backup(),settings(context))).commit();
            Alarms.cancelTransferred(context,old,snoozes);Alarms.restore(context);return "Account copy loaded. Check this phone's reminder permissions.";
        }
        if(replace&&remote==null)throw new IOException("This account has no saved workspace yet.");
        if(!dirty&&remote!=null)return "Up to date across devices.";
        if(current.length>20*1024*1024)throw new IOException("Cloud transfers support 20 MB. Use an offline backup for larger workspaces.");
        String path=owner+"/"+UUID.randomUUID()+".zip";
        request("POST","/storage/v1/object/steady-backups/"+path,current,"application/zip",token,1024*1024);
        JSONArray result;
        try{result=new JSONArray(new String(request("POST","/rest/v1/rpc/steady_commit",new JSONObject().put("expected_revision",revision).put("new_path",path).put("new_settings",settings).toString().getBytes(StandardCharsets.UTF_8),"application/json",token,1024*1024),StandardCharsets.UTF_8));}
        catch(Exception error){removeObject(path,token);throw error;}
        p.edit().putLong("revision",result.getJSONObject(0).getLong("revision")).putString("hash",hash).commit();
        if(remote!=null)removeObject(remote.getString("object_path"),token);
        return "Workspace and preferences saved to your account.";
    }
    private void removeObject(String path,String token){try{json("DELETE","/storage/v1/object/steady-backups",new JSONObject().put("prefixes",new JSONArray().put(path)),token);}catch(Exception ignored){}}
}
