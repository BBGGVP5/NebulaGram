"""Exercise real folder-root listener ownership and toolbar theme-cache invalidation."""
from pathlib import Path
import os,shutil,subprocess,tempfile
root=Path(__file__).resolve().parents[1];ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
def method(s,signature):
 start=s.index(signature);i=s.index('{',start);depth=1;end=i+1
 while depth:depth+=(s[end]=='{')-(s[end]=='}');end+=1
 return s[start:end]
folder=(ui/'NebulaFolderTabs.java').read_text(encoding='utf-8')
notify=method(folder,'private static void notifyChanged(');add=method(folder,'public static void addChangeListener(');remove=method(folder,'public static void removeChangeListener(')
source='''class FolderCheck {
 private static final java.util.LinkedHashSet<Runnable> changeListeners=new java.util.LinkedHashSet<>();
 NOTIFY ADD REMOVE
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[]args){
 int[] count={0,0};Runnable first=()->count[0]++;Runnable second=()->count[1]++;
 addChangeListener(first);addChangeListener(second);addChangeListener(second);notifyChanged();check(count[0]==1&&count[1]==1);
 removeChangeListener(first);notifyChanged();check(count[0]==1&&count[1]==2);
 removeChangeListener(second);check(changeListeners.isEmpty());
 for(int i=0;i<1000;i++){Runnable root=()->{};addChangeListener(root);removeChangeListener(root);}check(changeListeners.isEmpty());
 Runnable victim=()->{throw new AssertionError("Destroyed root notified");};
 Runnable remover=()->removeChangeListener(victim);addChangeListener(remover);addChangeListener(victim);notifyChanged();removeChangeListener(remover);
 System.out.println("Folder roots: simultaneous updates, duplicate registration, exact detach and 1000 recreate cycles passed");
 }}'''.replace('NOTIFY',notify).replace('ADD',add).replace('REMOVE',remove)
bar=(ui/'NebulaFormattingToolbar.java').read_text(encoding='utf-8');palette=method(bar,'private void updatePalette(')
palette=palette.replace('android.graphics.drawable.RippleDrawable','RippleDrawable').replace('android.content.res.ColorStateList','ColorStateList')
source+='''
class PaletteCheck {
 static class NebulaTheme {static int surface=1,ink=2,chip=4;static NebulaTheme of(Object c){return new NebulaTheme();}int surfaceContainer(){return surface;}int onSurface(){return ink;}int surface(){return chip;}static int stateLayer(int c,float a){return c;}}
 static class AndroidUtilities {static int dp(float n){return Math.round(n);}}
 static class GradientDrawable {static int created;int color;GradientDrawable(){created++;}void setColor(int n){color=n;}void setCornerRadius(int n){}}
 static class ColorStateList {static Object valueOf(int c){return c;}}
 static class InsetDrawable {GradientDrawable tile;InsetDrawable(GradientDrawable tile,int inset){this.tile=tile;}}
 static class RippleDrawable {GradientDrawable tile;RippleDrawable(Object c,InsetDrawable tile,Object mask){this.tile=tile.tile;}}
 static class View {int color;Object background;void setBackground(Object b){background=b;}}
 static class TextView extends View {void setTextColor(int n){color=n;}}
 static class ImageView extends View {void setColorFilter(int n){color=n;}}
 static class Row {View[]items={new TextView(),new ImageView()};int getChildCount(){return items.length;}View getChildAt(int n){return items[n];}}
 int palette,paletteInk,paletteChip;Object background;Row row=new Row();Object getContext(){return null;}Object getBackground(){return background;}void setBackground(Object b){background=b;}
 static int blend(int base,int ink,float fraction){return NebulaTheme.chip;}
 PALETTE
 public static void main(String[]args){PaletteCheck p=new PaletteCheck();p.updatePalette();
 for(int i=0;i<1000;i++)p.updatePalette();if(GradientDrawable.created!=3)throw new AssertionError("Idle cache rebuilt");
 NebulaTheme.ink=3;p.updatePalette();if(p.row.items[0].color!=3||p.row.items[1].color!=3)throw new AssertionError("Unchanged surface kept stale foreground");
 NebulaTheme.chip=5;p.updatePalette();for(View item:p.row.items)if(((RippleDrawable)item.background).tile.color!=5)throw new AssertionError("Chip-only theme change lost");
 System.out.println("Toolbar palette: 1000 idle layouts retain the cached surface; foreground-only theme changes refresh all controls");}}
'''.replace('PALETTE',palette)
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'))
with tempfile.TemporaryDirectory(prefix='nebula-presentation-life-') as work:
 work=Path(work);file=work/'FolderCheck.java';file.write_text(source,encoding='utf-8')
 subprocess.run([shutil.which('javac') or str(java_home/'bin/javac.exe'),'-encoding','UTF-8','-d',str(work),str(file)],check=True)
 for name in ['FolderCheck','PaletteCheck']:subprocess.run([shutil.which('java') or str(java_home/'bin/java.exe'),'-cp',str(work),name],check=True)
