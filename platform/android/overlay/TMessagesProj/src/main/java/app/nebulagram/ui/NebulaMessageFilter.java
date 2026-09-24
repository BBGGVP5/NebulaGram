package app.nebulagram.ui;

import android.content.*;
import android.graphics.*;
import android.view.View;
import android.widget.EditText;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import java.util.*;

/** Reversible local keyword mask. Sponsored messages are deliberately excluded. */
public final class NebulaMessageFilter {
    private static final Map<Long,String[]> rules=new HashMap<>();
    private static final Set<String> revealed=new HashSet<>();
    private static final Paint paint=new Paint(3);
    private static final RectF bounds=new RectF();
    private static SharedPreferences prefs(long user){return ApplicationLoader.applicationContext.getSharedPreferences("nebula_filter_"+user,0);}
    private static String key(MessageObject m){return NebulaTasks.user(m.currentAccount)+":"+m.getDialogId()+":"+m.getId();}
    public static boolean hidden(MessageObject m){
        if(m==null||m.messageOwner==null||m.sponsoredId!=null||m.isOutOwner()||m.messageOwner.message==null)return false;
        long user=NebulaTasks.user(m.currentAccount);String[] words=rules.get(user);
        if(words==null){words=java.util.Arrays.stream(prefs(user).getString("words","").toLowerCase(Locale.ROOT).split("\n")).map(String::trim).filter(v->!v.isEmpty()).toArray(String[]::new);rules.put(user,words);}
        if(words.length==0||revealed.contains(key(m)))return false;
        String text=m.messageOwner.message.toLowerCase(Locale.ROOT);
        for(String word:words)if(!word.trim().isEmpty()&&text.contains(word.trim()))return true;
        return false;
    }
    public static String label(){return NebulaText.text("Скрыто по фильтру · нажмите, чтобы показать","Filtered message · tap to reveal");}
    public static void reveal(MessageObject m){if(revealed.size()>=512)revealed.clear();revealed.add(key(m));}
    public static void draw(Canvas c,View host){
        float inset=AndroidUtilities.dp(12),height=Math.min(AndroidUtilities.dp(56),host.getHeight());
        bounds.set(inset,0,host.getWidth()-inset,height);paint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));c.drawRoundRect(bounds,inset,inset,paint);
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));paint.setTextSize(AndroidUtilities.dp(12));
        String text=label();float w=paint.measureText(text);if(w>bounds.width()-inset*2)paint.setTextSize(paint.getTextSize()*(bounds.width()-inset*2)/w);
        c.drawText(text,bounds.left+inset,height/2+AndroidUtilities.dp(4),paint);
    }
    public static void configure(BaseFragment host){
        long user=NebulaTasks.user(host.getCurrentAccount());EditText input=new EditText(host.getContext());input.setText(prefs(user).getString("words",""));input.setMaxLines(10);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4000)});
        host.showDialog(new AlertDialog.Builder(host.getContext()).setTitle(NebulaText.text("Фильтр сообщений","Message filter"))
            .setMessage(NebulaText.text("По одному слову или фразе в строке. Подходящие входящие сообщения скрываются локально; их можно открыть нажатием. Пустой список отключает фильтр.","One word or phrase per line. Matching incoming messages are masked locally and can be revealed with a tap. Clear the list to disable."))
            .setView(input).setPositiveButton(NebulaText.text("Сохранить","Save"),(d,w)->{prefs(user).edit().putString("words",input.getText().toString()).apply();rules.remove(user);revealed.clear();})
            .setNegativeButton(NebulaText.text("Отмена","Cancel"),null).create());
    }
}
