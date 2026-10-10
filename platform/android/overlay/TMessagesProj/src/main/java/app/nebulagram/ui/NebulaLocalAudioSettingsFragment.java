package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.widget.*;
import com.google.mlkit.genai.common.FeatureStatus;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import java.util.Locale;

/** Explicit readiness/download for the audio model, independent of text Nano settings. */
public final class NebulaLocalAudioSettingsFragment extends BaseFragment {
    private final boolean advanced;
    private LinearLayout body;
    private TextView statusView;
    private NebulaButton action;
    private NebulaLocalTranscription request;
    private int generation, status=-1;
    private boolean busy, visible, destroyed;
    private String detail="";
    public NebulaLocalAudioSettingsFragment(boolean advanced){this.advanced=advanced;}
    @Override public android.view.View createView(Context c){
        NebulaFormUi.bar(this,actionBar,c,advanced?text("Речь Gemini Nano","Gemini Nano speech"):text("Речь на устройстве","On-device speech"));
        body=NebulaFormUi.column(c);rebuild();return fragmentView=NebulaSettingsLayout.wrap(c,actionBar,NebulaFormUi.scroll(c,body),-12);
    }
    private void rebuild(){
        Context c=body.getContext();body.removeAllViews();
        body.addView(new NebulaSettingsHero(c,"🎙️",advanced?"Gemini Nano":text("На устройстве","On device"),text("Локальная расшифровка голосовых и кружков","Local voice and video-message transcription")));
        NebulaCard card=new NebulaCard(c);
        card.add(new NebulaRow(c).title(text("Язык записи","Recording language")).subtitle(localeTitle(NebulaAudioPreferences.localLocale()),true).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->chooseLanguage()));
        NebulaFormUi.group(body,text("Распознавание","Recognition"),card);
        statusView=NebulaFormUi.note(c,"");body.addView(statusView);
        action=NebulaFormUi.primary(c,"",v->{if(busy){cancel();detail=text("Остановлено","Stopped");refresh();}else check(status==FeatureStatus.DOWNLOADABLE);});body.addView(action);
        body.addView(NebulaFormUi.note(c,advanced?text("Gemini Nano распознаёт запись через AICore на поддерживаемых Pixel 10/11. Наличие текстовой модели Nano не означает доступность аудиомодели. На других телефонах выберите отдельный режим распознавания Android.","Gemini Nano transcribes through AICore on supported Pixel 10/11 devices. Text Nano availability does not imply audio-model support. Choose the separate Android recognition mode on other phones."):text("Это обычное локальное распознавание Android, а не Gemini Nano. Требуются Android 12+ и доступный языковой пакет.","This is standard local Android recognition, not Gemini Nano. It requires Android 12+ and an available language pack.")));
        body.addView(NebulaFormUi.note(c,text("Скачивание модели запускается только кнопкой. Запись не отправляется API-провайдеру; для скачивания модели нужна сеть. Локальная обработка идёт примерно с темпом записи, максимум 10 минут и 14 МБ. Сворачивание или закрытие останавливает операцию.","Model download starts only after tapping. The recording is not sent to an API provider; downloading the model needs network access. Local processing runs at roughly the recording's pace, up to 10 minutes and 14 MB. Leaving or backgrounding stops the operation.")));
        refresh();
    }
    private String localeTitle(String code){Locale locale=Locale.forLanguageTag(code);return locale.getDisplayName(org.telegram.messenger.LocaleController.getInstance().getCurrentLocale())+" · "+code;}
    private void chooseLanguage(){
        String[] codes=NebulaLocalAudioPolicy.LOCALES,names=new String[codes.length];for(int i=0;i<names.length;i++)names[i]=localeTitle(codes[i]);
        showDialog(new NebulaDialog.Builder(getContext(),getResourceProvider()).setTitle(text("Язык записи","Recording language")).setSelectedIndex(java.util.Arrays.asList(codes).indexOf(NebulaAudioPreferences.localLocale())).setItems(names,(d,i)->{cancel();NebulaAudioPreferences.prefs().edit().putString("local_audio_locale",codes[i]).apply();status=-1;detail="";rebuild();check(false);}).create());
    }
    private void refresh(){if(statusView==null)return;String state=status==FeatureStatus.AVAILABLE?text("Модель готова","Model ready"):status==FeatureStatus.DOWNLOADABLE?text("Модель можно скачать","Model can be downloaded"):status==FeatureStatus.DOWNLOADING?text("Модель загружается","Model is downloading"):status==FeatureStatus.UNAVAILABLE?text("Модель недоступна на устройстве для этого языка","Model unavailable on this device for this language"):text("Проверяем аудиомодель…","Checking the audio model…");statusView.setText((detail.isEmpty()?state:detail));action.setText(busy?text("Остановить","Stop"):status==FeatureStatus.DOWNLOADABLE?text("Скачать модель","Download model"):text("Проверить доступность","Check availability"));}
    private void check(boolean download){
        cancel();if(!visible||destroyed)return;final int token=++generation;busy=true;detail=download?text("Скачиваем аудиомодель…","Downloading the audio model…"):text("Проверяем аудиомодель…","Checking the audio model…");refresh();
        final NebulaLocalTranscription task=request=new NebulaLocalTranscription(advanced,NebulaAudioPreferences.localLocale());
        new Thread(()->{
            try{
                int result=download?task.download(new NebulaLocalTranscription.Progress(){public void text(String value){}public void status(String value){if(value.startsWith("DOWNLOAD:")){String size=value.substring(9);AndroidUtilities.runOnUIThread(()->{if(current(token)){detail=NebulaText.text("Скачано байт: ","Downloaded bytes: ")+size;refresh();}});}}}):task.checkStatus();
                AndroidUtilities.runOnUIThread(()->{if(current(token)){request=null;busy=false;status=result;detail=!NebulaLocalTranscription.supported(advanced)?NebulaLocalTranscription.errorText(new java.io.IOException(advanced?"LOCAL_AUDIO_NANO_UNAVAILABLE":"LOCAL_AUDIO_UNAVAILABLE")):"";refresh();}});
            }catch(Exception error){AndroidUtilities.runOnUIThread(()->{if(current(token)){request=null;busy=false;status=-1;detail=NebulaLocalTranscription.errorText(error);refresh();}});}
        },"NebulaLocalSpeechSetup").start();
    }
    private boolean current(int token){return !destroyed&&visible&&generation==token;}
    private void cancel(){generation++;busy=false;if(request!=null){request.cancel();request=null;}}
    @Override public void onResume(){super.onResume();visible=true;check(false);}
    @Override public void onPause(){visible=false;cancel();super.onPause();}
    @Override public void onFragmentDestroy(){destroyed=true;cancel();super.onFragmentDestroy();}
}
