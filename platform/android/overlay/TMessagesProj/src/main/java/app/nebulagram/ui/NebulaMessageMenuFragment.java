package app.nebulagram.ui;
import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import org.telegram.ui.ActionBar.BaseFragment;

public final class NebulaMessageMenuFragment extends BaseFragment {
    private LinearLayout content;
    @Override public View createView(Context c){
        NebulaFormUi.bar(this,actionBar,c,text("Меню сообщения","Message menu"));content=NebulaFormUi.column(c);
        content.addView(new NebulaSettingsHero(c,"💬",text("Меню сообщения","Message menu"),text("Нужные действия всегда под рукой","Keep the actions you use within reach")));
        content.addView(new NebulaControlsPreview(c,NebulaControlsPreview.MESSAGE));
        NebulaCard layout=new NebulaCard(c);
        layout.add(toggle(c,"compact",false,"Компактный вид","Compact appearance"));
        layout.add(new NebulaRow(c).title(text("Размытие фона","Background blur")).trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaAppearance.messageMenuBlur()).withClick(v->NebulaAppearance.setMessageMenuBlur(((NebulaRow)v).toggleChecked())));
        layout.add(new NebulaRow(c).title(text("Меню под сообщением","Menu below the message")).trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaAppearance.messageMenuBelow()).withClick(v->NebulaAppearance.setMessageMenuBelow(((NebulaRow)v).toggleChecked())));
        layout.add(new NebulaRow(c).title(text("Высота меню","Menu height")).subtitle(NebulaMessageMenuSettings.heightPercent()+"%",true).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->{
            String[] labels={"35%","45%","55%","65%","80%"};int[] values={35,45,55,65,80};
            showDialog(new NebulaDialog.Builder(c,getResourceProvider()).setTitle(text("Высота меню","Menu height")).setItems(labels,(d,i)->{NebulaMessageMenuSettings.heightPercent(values[i]);((NebulaRow)v).subtitle(labels[i],true);}).create());
        }));
        NebulaFormUi.group(content,text("Оформление","Appearance"),layout);
        NebulaCard items=new NebulaCard(c);
        int[] ids={8,2,3,4,7,10,6,22,23,29,30,110};
        String[] ru={"Ответить","Переслать","Копировать","Сохранить в галерею","Сохранить медиа","Сохранить в загрузки","Поделиться","Копировать ссылку","Пожаловаться","Перевести","Расшифровать","Добавить в список дел"};
        String[] en={"Reply","Forward","Copy","Save to gallery","Save media","Save to downloads","Share","Copy link","Report","Translate","Transcribe","Add to checklist"};
        for(int i=0;i<ids.length;i++)items.add(toggle(c,"action_"+ids[i],true,ru[i],en[i]));
        items.add(toggle(c,"sound",true,"Сохранить звук","Save sound"));
        items.add(toggle(c,"tools",true,"Инструменты NebulaGram","NebulaGram tools"));
        NebulaFormUi.group(content,text("Действия","Actions"),items);
        content.addView(NebulaFormUi.note(c,text("Появляются только действия, доступные для выбранного сообщения. Редактирование и удаление остаются в меню. Настройки применяются при следующем открытии меню.","Only actions available for the selected message are shown. Edit and delete remain in the menu. Changes apply the next time you open it.")));
        return fragmentView=NebulaSettingsLayout.wrap(c,actionBar,NebulaFormUi.scroll(c,content),-28);
    }
    private NebulaRow toggle(Context c,String key,boolean fallback,String ru,String en){return new NebulaRow(c).title(text(ru,en)).trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaMessageMenuSettings.enabled(key,fallback)).withClick(v->NebulaMessageMenuSettings.set(key,((NebulaRow)v).toggleChecked()));}
}
