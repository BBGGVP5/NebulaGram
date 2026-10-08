"""Check real menu filtering against empty/disabled/native-action combinations."""
from pathlib import Path
import subprocess, tempfile

root = Path(__file__).resolve().parents[1]
source = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaMessageMenuSettings.java'
files = {
    'android/content/SharedPreferences.java': '''package android.content;
public interface SharedPreferences {
 boolean getBoolean(String key,boolean fallback); int getInt(String key,int fallback); Editor edit();
 interface Editor {Editor putBoolean(String key,boolean value);Editor putInt(String key,int value);void apply();}
}''',
    'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;
public class ApplicationLoader {
 public static Store applicationContext=new Store();
 public static class Store implements android.content.SharedPreferences,android.content.SharedPreferences.Editor {
  java.util.Map<String,Object> values=new java.util.HashMap<>();
  public Store getSharedPreferences(String key,int mode){return this;}
  public boolean getBoolean(String key,boolean fallback){return (Boolean)values.getOrDefault(key,fallback);}
  public int getInt(String key,int fallback){return (Integer)values.getOrDefault(key,fallback);}
  public Store edit(){return this;}public Store putBoolean(String key,boolean value){values.put(key,value);return this;}
  public Store putInt(String key,int value){values.put(key,value);return this;}public void apply(){}
 }
}''',
    'app/nebulagram/ui/MenuCheck.java': '''package app.nebulagram.ui;
import java.util.*;
public class MenuCheck {
 static void verify(Integer... input){
  ArrayList<Integer> actions=new ArrayList<>(Arrays.asList(input)),icons=new ArrayList<>();
  ArrayList<CharSequence> labels=new ArrayList<>();
  for(int id:input){icons.add(id+1000);labels.add("action-"+id);}
  NebulaMessageMenuSettings.filter(labels,icons,actions);
  if(input.length>0&&actions.isEmpty())throw new AssertionError("empty popup");
  if(labels.size()!=actions.size()||icons.size()!=actions.size())throw new AssertionError("parallel arrays");
  for(int i=0;i<actions.size();i++)if(icons.get(i)!=actions.get(i)+1000||!labels.get(i).equals("action-"+actions.get(i)))throw new AssertionError("action/label mismatch");
  for(int id:input)if((id==1||id==12)&&!actions.contains(id))throw new AssertionError("edit/delete hidden");
 }
 public static void main(String[] args){
  for(int id:new int[]{1,2,3,4,6,7,8,10,12,22,23,29,30,110})NebulaMessageMenuSettings.set("action_"+id,false);
  verify();verify(3);verify(2,3,8,29);verify(1,12);verify(2,1,3,12,8);verify(30,29,110,10);
  NebulaMessageMenuSettings.heightPercent(-100);if(NebulaMessageMenuSettings.heightPercent()!=35)throw new AssertionError("negative height");
  NebulaMessageMenuSettings.heightPercent(10000);if(NebulaMessageMenuSettings.heightPercent()!=80)throw new AssertionError("oversized height");
  System.out.println("Menu stays usable, preserves edit/delete and keeps labels/icons/actions aligned");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='nebula-menu-policy-') as folder:
    out = Path(folder)
    for name, content in files.items():
        path = out / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', folder, str(source), *map(str, out.rglob('*.java'))], check=True)
    subprocess.run(['java', '-cp', folder, 'app.nebulagram.ui.MenuCheck'], check=True)
