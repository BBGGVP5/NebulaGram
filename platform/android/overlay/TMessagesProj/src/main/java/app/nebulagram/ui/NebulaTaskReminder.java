package app.nebulagram.ui;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.ui.LaunchActivity;

public final class NebulaTaskReminder extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent){
        ApplicationLoader.postInitApplication();
        try {
            if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){
                for(int a=0;a<UserConfig.MAX_ACCOUNT_COUNT;a++)if(UserConfig.getInstance(a).isClientActivated()){
                    long user=NebulaTasks.user(a);JSONArray all=NebulaTasks.read(user);for(int i=0;i<all.length();i++)NebulaTasks.schedule(user,all.getJSONObject(i));
                }
                return;
            }
            long user=intent.getLongExtra("user",0);int account=-1;
            for(int a=0;a<UserConfig.MAX_ACCOUNT_COUNT;a++)if(UserConfig.getInstance(a).isClientActivated()&&NebulaTasks.user(a)==user)account=a;
            if(account<0)return;
            JSONArray all=NebulaTasks.read(user);
            for(int i=0;i<all.length();i++){
                JSONObject task=all.getJSONObject(i);
                if(!task.optString("id").equals(intent.getStringExtra("task"))||task.optBoolean("done")||task.optLong("remind")==0)continue;
                if(task.optLong("remind")>System.currentTimeMillis()+1000){NebulaTasks.schedule(user,task);return;}
                NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
                String channel="nebula_tasks";
                if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(channel,NebulaText.text("Задачи","Tasks"),NotificationManager.IMPORTANCE_DEFAULT));
                Intent open=new Intent(c,LaunchActivity.class).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse("tg://settings/nebula?section=-16")).putExtra("currentAccount",account);
                PendingIntent click=PendingIntent.getActivity(c,account,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                android.app.Notification.Builder n=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(c,channel):new android.app.Notification.Builder(c);
                // Task contents are private on the lock screen and do not bypass the app passcode.
                n.setSmallIcon(R.drawable.notification).setContentTitle("NebulaGram").setContentText(NebulaText.text("Напоминание о задаче","Task reminder"))
                    .setContentIntent(click).setAutoCancel(true).setVisibility(android.app.Notification.VISIBILITY_PRIVATE);
                nm.notify("nebula-task-"+user+"-"+task.getString("id"),0,n.build());
                task.put("remind",0);NebulaTasks.put(user,task);return;
            }
        }catch(Exception e){FileLog.e("Nebula task reminder failed");}
    }
}
