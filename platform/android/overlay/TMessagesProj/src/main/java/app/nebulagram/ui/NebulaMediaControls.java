package app.nebulagram.ui;
import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.widget.LinearLayout;
import org.telegram.ui.ActionBar.BaseFragment;

public final class NebulaMediaControls {
    private NebulaMediaControls() { }
    public static NebulaRow toggle(Context c,String key,boolean fallback,String ru,String en){return new NebulaRow(c).title(text(ru,en)).trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaMessagePreferences.enabled(key,fallback)).withClick(v->NebulaMessagePreferences.set(key,((NebulaRow)v).toggleChecked()));}
    public static void add(BaseFragment host,LinearLayout body){
        Context c=body.getContext();NebulaCard card=new NebulaCard(c);
        card.add(toggle(c,"voice_autoplay",true,"Следующее голосовое автоматически","Autoplay next voice message"));
        card.add(toggle(c,"volume_play",false,"Запускать видео кнопкой громкости","Play video with a volume key"));
        card.add(toggle(c,"pause_background",false,"Пауза видео при сворачивании","Pause video in the background"));
        card.add(toggle(c,"forward_date",false,"Показывать дату оригинала пересылки","Show forwarded message's original date"));
        card.add(toggle(c,"edited_pencil",false,"Карандаш вместо «изменено»","Pencil instead of “edited”"));
        card.add(toggle(c,"direct_share",true,"Кнопка быстрой пересылки","Quick share button"));
        card.add(toggle(c,"premium_effects",true,"Эффекты Premium-стикеров","Premium sticker effects"));
        card.add(toggle(c,"reaction_effects",true,"Анимация реакций","Reaction animations"));
        NebulaFormUi.group(body,text("Медиа и сообщения","Media and messages"),card);
        card=new NebulaCard(c);
        card.add(new NebulaRow(c).title(text("Скрывать клавиатуру при прокрутке","Hide keyboard while scrolling")).subtitle(NebulaMessagePreferences.keyboardThreshold()==0?text("Выключено","Off"):Integer.toString(NebulaMessagePreferences.keyboardThreshold()),true).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->{
            String[] labels=new String[11];labels[0]=text("Выключено","Off");for(int i=1;i<=10;i++)labels[i]=Integer.toString(i);
            host.showDialog(new NebulaDialog.Builder(c,host.getResourceProvider()).setTitle(text("Порог прокрутки","Scroll threshold")).setSelectedIndex(NebulaMessagePreferences.keyboardThreshold()).setItems(labels,(d,i)->{NebulaMessagePreferences.set("keyboard",i);((NebulaRow)v).subtitle(labels[i],true);}).create());
        }));
        card.add(new NebulaRow(c).title(text("Шаг перемотки видео","Video seek step")).subtitle(NebulaMessagePreferences.seekSeconds()+text(" с"," s"),true).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->{
            host.showDialog(new NebulaDialog.Builder(c,host.getResourceProvider()).setTitle(text("Шаг перемотки","Seek step")).setItems(new String[]{"5","10","15","20","25"},(d,i)->{NebulaMessagePreferences.set("seek",(i+1)*5);((NebulaRow)v).subtitle((i+1)*5+text(" с"," s"),true);}).create());
        }));
        NebulaFormUi.group(body,text("Жесты и управление","Gestures and controls"),card);
        card=new NebulaCard(c);
        card.add(toggle(c,"video_microphones",false,"Микрофоны в режиме видеокамеры","Camcorder microphone processing"));
        card.add(toggle(c,"stereo_round",false,"Стереозвук в кружках","Stereo audio in round videos"));
        card.add(toggle(c,"record_exclusive",false,"Приглушать другие звуки при записи","Suppress other audio while recording"));
        NebulaFormUi.group(body,text("Запись звука","Audio recording"),card);
        body.addView(NebulaFormUi.note(c,text("Стерео используется, если устройство поддерживает два канала; иначе запись остаётся моно. Обработка микрофонов зависит от устройства. Приглушение использует системный аудиофокус и не меняет громкость будильников.","Stereo is used when two-channel capture is supported; otherwise recording stays mono. Microphone processing depends on the device. Audio suppression uses system audio focus and does not change alarm volume.")));
    }
}
