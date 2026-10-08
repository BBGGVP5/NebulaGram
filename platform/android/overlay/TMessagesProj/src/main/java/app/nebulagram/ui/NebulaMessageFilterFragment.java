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
        card.add(new NebulaRow(c).title(text("Исключения чатов","Chat exceptions")).subtitle(text("ID чатов, где фильтр не применяется","Chat IDs where filtering is disabled"),false).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->{
            EditText field=new EditText(c);field.setText(prefs.getString("exceptions",""));field.setHint(text("ID через пробел или запятую","IDs separated by spaces or commas"));field.setMaxLines(6);
            field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4000)});
            showDialog(new NebulaDialog.Builder(c,getResourceProvider()).setTitle(text("Исключения","Exceptions")).setView(field)
                .setPositiveButton(text("Сохранить","Save"),(d,w)->{prefs.edit().putString("exceptions",field.getText().toString()).apply();NebulaMessageFilter.invalidate(user);})
                .setNegativeButton(text("Отмена","Cancel"),null).create());
        }));
        NebulaFormUi.group(body,text("Исключения","Exceptions"),card);
        body.addView(NebulaFormUi.note(c,text("Сообщения скрываются только на этом устройстве. Нажмите на скрытое сообщение, чтобы прочитать его. Свои сообщения и реклама не фильтруются.","Messages are masked only on this device. Tap a masked message to read it. Your own messages and sponsored posts are excluded.")));
        return fragmentView=NebulaSettingsLayout.wrap(c,actionBar,NebulaFormUi.scroll(c,body),-27);
    }
    private NebulaRow toggle(Context c,String key,boolean fallback,String ru,String en){
        return new NebulaRow(c).title(text(ru,en)).trailing(NebulaRow.TRAIL_SWITCH).checked(prefs.getBoolean(key,fallback)).withClick(v->{prefs.edit().putBoolean(key,((NebulaRow)v).toggleChecked()).apply();NebulaMessageFilter.invalidate(user);});
    }
}
