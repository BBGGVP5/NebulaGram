package app.nebulagram.ui;
import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

public final class NebulaMessagePreferences {
    private NebulaMessagePreferences() { }
    private static SharedPreferences prefs(){return ApplicationLoader.applicationContext.getSharedPreferences("nebula_message_preferences",0);}
    public static boolean enabled(String key,boolean fallback){return prefs().getBoolean(key,fallback);}
    public static void set(String key,boolean value){prefs().edit().putBoolean(key,value).apply();}
    public static int number(String key,int fallback,int min,int max){return Math.max(min,Math.min(max,prefs().getInt(key,fallback)));}
    public static void set(String key,int value){prefs().edit().putInt(key,value).apply();}
    public static int seekSeconds(){return number("seek",10,5,25);}
    public static int keyboardThreshold(){return number("keyboard",5,0,10);}
}
