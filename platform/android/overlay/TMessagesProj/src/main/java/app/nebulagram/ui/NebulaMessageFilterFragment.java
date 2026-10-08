package app.nebulagram.ui;
import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import org.telegram.ui.ActionBar.BaseFragment;

public final class NebulaMessageFilterFragment extends BaseFragment {
    private SharedPreferences prefs;
    private long user;
    @Override public View createView(Context c){
        user=NebulaTasks.user(currentAccount);prefs=NebulaMessageFilter.prefs(user);
        NebulaFormUi.bar(this,actionBar,c,text("Фильтр сообщений","Message filter"));
        LinearLayout body=NebulaFormUi.column(c);
        body.addView(new NebulaSettingsHero(c,"🔎",text("Фильтр сообщений","Message filter"),text("Меньше лишнего в ваших переписках","Less clutter in your conversations")));
        NebulaCard card=new NebulaCard(c);
        card.add(toggle(c,"enabled",true,"Фильтровать сообщения","Filter messages"));
        card.add(new NebulaRow(c).title(text("Слова и фразы","Words and phrases")).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->NebulaMessageFilter.editWords(this)));
        card.add(toggle(c,"translit",false,"Распознавать транслитерацию","Recognize transliteration"));
        card.add(toggle(c,"whole",false,"Только целые слова","Whole words only"));
        card.add(toggle(c,"blocked",false,"Сообщения заблокированных людей","Messages from blocked users"));
        NebulaFormUi.group(body,text("Правила","Rules"),card);
        card=new NebulaCard(c);
        card.add(new NebulaRow(c).title(text("Исключения чатов","Chat exceptions")).subtitle(text("Выберите переписки без фильтрации","Choose conversations without filtering"),false).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->exceptions()));
        NebulaFormUi.group(body,text("Исключения","Exceptions"),card);
        body.addView(NebulaFormUi.note(c,text("Сообщения скрываются только на этом устройстве. Нажмите на скрытое сообщение, чтобы прочитать его. Свои сообщения и реклама не фильтруются.","Messages are masked only on this device. Tap a masked message to read it. Your own messages and sponsored posts are excluded.")));
        return fragmentView=NebulaSettingsLayout.wrap(c,actionBar,NebulaFormUi.scroll(c,body),-27);
    }
    private void exceptions(){
        if(user!=NebulaTasks.user(currentAccount))return;
        java.util.LinkedHashSet<Long> peers=new java.util.LinkedHashSet<>();
        for(String raw:prefs.getString("exceptions","").split("[,\\s]+"))try{peers.add(Long.parseLong(raw));}catch(NumberFormatException ignored){}
        java.util.ArrayList<Long> ids=new java.util.ArrayList<>(peers);
        java.util.ArrayList<CharSequence> labels=new java.util.ArrayList<>();
        labels.add(text("Добавить чат","Add chat"));
        for(long id:ids)labels.add(text("Убрать: ","Remove: ")+NebulaPeerSelections.name(currentAccount,id));
        showDialog(new NebulaDialog.Builder(getContext(),getResourceProvider()).setTitle(text("Исключения чатов","Chat exceptions"))
            .setItems(labels.toArray(new CharSequence[0]),(d,index)->{
                if(user!=NebulaTasks.user(currentAccount))return;
                if(index==0)NebulaPeerSelections.choose(this,false,peer->{peers.add(peer);store(peers);});
                else{peers.remove(ids.get(index-1));store(peers);}
            }).create());
    }
    private void store(java.util.Set<Long> peers){
        if(user!=NebulaTasks.user(currentAccount))return;
        StringBuilder value=new StringBuilder();for(long peer:peers){if(value.length()>0)value.append(',');value.append(peer);}
        prefs.edit().putString("exceptions",value.toString()).apply();NebulaMessageFilter.invalidate(user);
    }
    private NebulaRow toggle(Context c,String key,boolean fallback,String ru,String en){
        return new NebulaRow(c).title(text(ru,en)).trailing(NebulaRow.TRAIL_SWITCH).checked(prefs.getBoolean(key,fallback)).withClick(v->{prefs.edit().putBoolean(key,((NebulaRow)v).toggleChecked()).apply();NebulaMessageFilter.invalidate(user);});
    }
}
