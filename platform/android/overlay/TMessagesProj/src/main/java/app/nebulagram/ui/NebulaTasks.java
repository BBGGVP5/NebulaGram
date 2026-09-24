package app.nebulagram.ui;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.*;
import org.telegram.messenger.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Private local tasks keyed by Telegram user, never by reusable account slot. */
public final class NebulaTasks {
    private static AtomicFile file(long user) {
        if(user<=0)throw new IllegalStateException("Account required");
        return new AtomicFile(new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(),"nebula-tasks-"+user+".json"));
    }
    public static long user(int account){return UserConfig.getInstance(account).getClientUserId();}
    public static synchronized JSONArray read(long user)throws Exception {
        AtomicFile f=file(user);return f.getBaseFile().exists()?new JSONArray(new String(f.readFully(),StandardCharsets.UTF_8)):new JSONArray();
    }
    public static synchronized void put(long user, JSONObject task)throws Exception {
        JSONArray all=read(user),next=new JSONArray();boolean replaced=false;
        for(int i=0;i<all.length();i++){JSONObject row=all.getJSONObject(i);if(row.getString("id").equals(task.getString("id"))){next.put(task);replaced=true;}else next.put(row);}
        if(!replaced)next.put(task);write(user,next);schedule(user,task);
    }
    public static synchronized void delete(long user,String id)throws Exception {
        JSONArray all=read(user),next=new JSONArray();for(int i=0;i<all.length();i++)if(!all.getJSONObject(i).getString("id").equals(id))next.put(all.getJSONObject(i));write(user,next);
        alarms().cancel(pending(user,id));
    }
    private static void write(long user,JSONArray value)throws Exception {
        AtomicFile f=file(user);FileOutputStream out=f.startWrite();try{out.write(value.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(out);}catch(Exception e){f.failWrite(out);throw e;}
    }
    private static AlarmManager alarms(){return (AlarmManager)ApplicationLoader.applicationContext.getSystemService(Context.ALARM_SERVICE);}
    private static PendingIntent pending(long user,String id){
        Context c=ApplicationLoader.applicationContext;
        Intent intent=new Intent(c,NebulaTaskReminder.class).setData(Uri.parse("nebula-task://"+user+"/"+id)).putExtra("user",user).putExtra("task",id);
        return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void schedule(long user,JSONObject task){
        PendingIntent p=pending(user,task.optString("id"));alarms().cancel(p);
        long at=task.optLong("remind");
        if(!task.optBoolean("done")&&at>0) {
            at=Math.max(at,System.currentTimeMillis()+1000);
            if(android.os.Build.VERSION.SDK_INT>=23)alarms().setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);
            else alarms().set(AlarmManager.RTC_WAKEUP,at,p);
        }
    }
}
