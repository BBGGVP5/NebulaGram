#!/usr/bin/env python3
"""Exercise the real palette/accent controller with in-memory Android preferences."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaTheme.java'

def main():
    with tempfile.TemporaryDirectory(prefix='nebula-theme-') as temp:
        tree = Path(temp)
        def put(name, text):
            path = tree / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding='utf-8')
        put('app/nebulagram/ui/NebulaTheme.java', SOURCE.read_text(encoding='utf-8'))
        colors = sorted(set(re.findall(r'android.R.color.(\w+)', SOURCE.read_text(encoding='utf-8'))))
        put('android/R.java', 'package android; public class R { public static class color { '
            + ' '.join(f'public static final int {color} = {i};' for i, color in enumerate(colors)) + ' }}')
        put('android/os/Build.java', '''package android.os; public class Build {
            public static class VERSION { public static int SDK_INT=36; }
            public static class VERSION_CODES { public static final int S=31; }
        }''')
        put('android/os/SystemClock.java', '''package android.os; public class SystemClock {
            public static long now=1000; public static long elapsedRealtime() { return now; }
        }''')
        put('android/content/res/Configuration.java', '''package android.content.res;
            public class Configuration { public static final int UI_MODE_NIGHT_MASK=48, UI_MODE_NIGHT_YES=32;
            public int uiMode=32; }''')
        put('android/content/res/Resources.java', '''package android.content.res;
            public class Resources { public Configuration config=new Configuration();
            public Configuration getConfiguration() { return config; } }''')
        put('android/content/SharedPreferences.java', '''package android.content;
            public interface SharedPreferences {
                boolean contains(String key); int getInt(String key,int fallback);
                boolean getBoolean(String key,boolean fallback); Editor edit();
                interface Editor { Editor putInt(String key,int value); Editor putBoolean(String key,boolean value);
                    Editor remove(String key); void apply(); }
            }''')
        put('android/content/Context.java', '''package android.content;
            public class Context { public final android.content.res.Resources resources=new android.content.res.Resources();
                public SharedPreferences prefs; public android.content.res.Resources getResources(){return resources;}
                public SharedPreferences getSharedPreferences(String name,int mode){return prefs;} }''')
        put('androidx/core/content/ContextCompat.java', '''package androidx.core.content;
            public class ContextCompat { public static int wallpaper=0xffeeaa88;
                public static int getColor(android.content.Context c,int id){ return wallpaper; } }''')
        put('org/telegram/messenger/ApplicationLoader.java', '''package org.telegram.messenger;
            public class ApplicationLoader { public static android.content.Context applicationContext; }''')
        put('org/telegram/messenger/AndroidUtilities.java', '''package org.telegram.messenger;
            public class AndroidUtilities { public static int dp(int value){return value;} }''')
        put('org/telegram/messenger/FileLog.java', '''package org.telegram.messenger;
            public class FileLog { public static void e(Throwable e){throw new AssertionError(e);} }''')
        put('org/telegram/ui/ActionBar/Theme.java', '''package org.telegram.ui.ActionBar;
            public class Theme {
                public static final int key_windowBackgroundWhiteBlueText=1,key_windowBackgroundGray=2,key_windowBackgroundWhite=3,key_windowBackgroundWhiteBlackText=4,key_windowBackgroundWhiteGrayText=5,key_divider=6;
                public static boolean isCurrentThemeDark(){return true;}
                public static int getColor(int key){return active.getAccent(false).accentColor;}

                public static java.util.ArrayList<ThemeInfo> themes=new java.util.ArrayList<>();
                public static ThemeInfo active;
                public static class ThemeAccent { public int accentColor,id;
                    public ThemeAccent(int id,int color){this.id=id;accentColor=color;} }
                public static class ThemeInfo { public String key; public int index;
                    public java.util.ArrayList<ThemeAccent> themeAccents=new java.util.ArrayList<>();
                    public ThemeInfo(String key,int... colors){this.key=key; for(int c:colors) themeAccents.add(new ThemeAccent(themeAccents.size(),c));}
                    public String getKey(){return key;} public ThemeAccent getAccent(boolean create){return themeAccents.get(index);} }
                public static ThemeInfo getActiveTheme(){return active;}
                public static void saveThemeAccents(ThemeInfo info,boolean a,boolean b,boolean c,boolean d){}
                public static void refreshThemeColors(){}
            }''')
        put('CheckTheme.java', '''import android.content.*;
            import app.nebulagram.ui.NebulaTheme;
            import org.telegram.messenger.ApplicationLoader;
            import org.telegram.ui.ActionBar.Theme;
            import androidx.core.content.ContextCompat;
            public class CheckTheme {
                static class Prefs implements SharedPreferences, SharedPreferences.Editor {
                    java.util.Map<String,Object> values=new java.util.HashMap<>();
                    public boolean contains(String k){return values.containsKey(k);}
                    public int getInt(String k,int d){return (int)values.getOrDefault(k,d);}
                    public boolean getBoolean(String k,boolean d){return (boolean)values.getOrDefault(k,d);}
                    public Editor edit(){return this;} public Editor putInt(String k,int v){values.put(k,v);return this;}
                    public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}
                    public Editor remove(String k){values.remove(k);return this;} public void apply(){}
                }
                static int checks;
                static void eq(int actual,int expected){checks++;if(actual!=expected)throw new AssertionError(actual+" != "+expected);}
                static void apply(Context c){android.os.SystemClock.now+=300;NebulaTheme.applyMaterialYou(c);}
                public static void main(String[] args){
                    Context c=new Context(); Prefs p=new Prefs(); c.prefs=p; ApplicationLoader.applicationContext=c;
                    Theme.ThemeInfo day=new Theme.ThemeInfo("day",0xff1144aa,0xffbb2233);
                    Theme.ThemeInfo night=new Theme.ThemeInfo("night",0xff447788);
                    Theme.themes.add(day); Theme.themes.add(night); Theme.active=day;
                    for(int cycle=0;cycle<3;cycle++){
                        NebulaTheme.setMaterialYouEnabled(true); apply(c);
                        eq(day.getAccent(false).accentColor,ContextCompat.wallpaper);
                        NebulaTheme snapshot=NebulaTheme.of(c);
                        day.index=1; apply(c); Theme.active=night; apply(c);
                        // An app restart reconstructs theme objects; only preferences retain backups.
                        day=new Theme.ThemeInfo("day",ContextCompat.wallpaper,ContextCompat.wallpaper);
                        night=new Theme.ThemeInfo("night",ContextCompat.wallpaper);
                        Theme.themes.clear();Theme.themes.add(day);Theme.themes.add(night);Theme.active=night;
                        NebulaTheme.setMaterialYouEnabled(false);
                        eq(day.themeAccents.get(0).accentColor,0xff1144aa);
                        eq(day.themeAccents.get(1).accentColor,0xffbb2233);
                        eq(night.getAccent(false).accentColor,0xff447788);
                        eq(snapshot.primary(),Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                        eq(NebulaTheme.of(c).primary(),Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                        eq((int)p.values.keySet().stream().filter(k->k.startsWith("accent_before_material_you")).count(),0);
                        Theme.active=day;
                    }
                    p.putInt("accent_before_material_you",0xff229955); day.getAccent(false).accentColor=ContextCompat.wallpaper;
                    NebulaTheme.setMaterialYouEnabled(false); eq(day.getAccent(false).accentColor,0xff229955);
                    // Rapid explicit toggles must bypass the automatic notification debounce.
                    NebulaTheme.setMaterialYouEnabled(true); NebulaTheme.applyMaterialYou(c);
                    eq(day.getAccent(false).accentColor,ContextCompat.wallpaper);
                    NebulaTheme.setMaterialYouEnabled(false); eq(day.getAccent(false).accentColor,0xff229955);
                    c.resources.config.uiMode=0; eq(NebulaTheme.of(c).primary(),Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
                    android.os.Build.VERSION.SDK_INT=30; NebulaTheme.setMaterialYouEnabled(true); apply(c);
                    eq(day.getAccent(false).accentColor,0xff229955);
                    System.out.println(checks+" palette/accent restoration checks passed");
                }
            }''')
        javac = shutil.which('javac')
        java = shutil.which('java')
        if not javac or not java:
            raise SystemExit('A JDK is required for the theme regression check')
        sources = [str(path) for path in tree.rglob('*.java')]
        subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(tree / 'classes'), *sources], check=True)
        subprocess.run([java, '-cp', str(tree / 'classes'), 'CheckTheme'], check=True)

if __name__ == '__main__':
    main()
