package app.nebulagram.ui;
import android.content.SharedPreferences;
import java.util.List;
import org.telegram.messenger.ApplicationLoader;

public final class NebulaMessageMenuSettings {
    private NebulaMessageMenuSettings() { }
    static SharedPreferences prefs(){return ApplicationLoader.applicationContext.getSharedPreferences("nebula_message_menu",0);}
    public static boolean enabled(String key,boolean fallback){return prefs().getBoolean(key,fallback);}
    public static void set(String key,boolean value){prefs().edit().putBoolean(key,value).apply();}
    public static boolean visible(int action){return enabled("action_"+action,true);}
    public static int heightPercent(){return Math.max(35,Math.min(80,prefs().getInt("height",55)));}
    public static void heightPercent(int value){prefs().edit().putInt("height",Math.max(35,Math.min(80,value))).apply();}
    public static void filter(List<CharSequence> labels,List<Integer> icons,List<Integer> actions){
        // Safety and editing actions remain reachable even if every optional entry is disabled.
        for(int i=actions.size()-1;i>=0;i--){int action=actions.get(i);if(action!=1&&action!=12&&!visible(action)){actions.remove(i);icons.remove(i);labels.remove(i);}}
    }
}
