package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import java.util.ArrayList;

/** One horizontal gesture; vertical movement chooses from the user's ordered actions. */
public final class NebulaSwipeActions {
    public static final int REPLY=0,COPY=1,TOOLS=2,TRANSLATE=3;
    private static volatile int[] actions={REPLY};
    private static SharedPreferences prefs;
    private static final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,key)->{if(key==null||key.equals("swipe_actions"))refresh();};
    private static synchronized void prepare(){if(prefs==null){prefs=ApplicationLoader.applicationContext.getSharedPreferences("nebulagram",0);prefs.registerOnSharedPreferenceChangeListener(listener);refresh();}}
    private static void refresh(){actions=parse(prefs.getString("swipe_actions","0"));}
    public static int[] parse(String value){ArrayList<Integer> parsed=new ArrayList<>();if(value!=null&&value.length()<=40)for(String token:value.split(","))try{int action=Integer.parseInt(token);if(action>=0&&action<=3&&!parsed.contains(action))parsed.add(action);}catch(NumberFormatException ignored){}if(parsed.isEmpty())parsed.add(REPLY);int[] result=new int[parsed.size()];for(int i=0;i<result.length;i++)result[i]=parsed.get(i);return result;}
    public static int[] order(){if(prefs==null)prepare();return actions.clone();}
    public static void set(int[] order){StringBuilder value=new StringBuilder();for(int action:order){if(value.length()>0)value.append(',');value.append(action);}if(prefs==null)prepare();prefs.edit().putString("swipe_actions",value.toString()).apply();refresh();}
    public static int action(float verticalDelta){if(prefs==null)prepare();int[] snapshot=actions;int index=Math.round(verticalDelta/AndroidUtilities.dp(56));return snapshot[Math.max(0,Math.min(snapshot.length-1,index))];}
    public static int icon(int action){return action==COPY?R.drawable.msg_copy:action==TRANSLATE?R.drawable.msg_translate:action==TOOLS?R.drawable.nebula_ai_spark:R.drawable.menu_reply;}
    public static String title(int action){return action==COPY?NebulaText.text("Копировать","Copy"):action==TRANSLATE?NebulaText.text("Перевести","Translate"):action==TOOLS?NebulaText.text("Инструменты Nebula","Nebula tools"):NebulaText.text("Ответить","Reply");}
}
