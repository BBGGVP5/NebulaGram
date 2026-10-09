package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.*;
import android.media.MediaPlayer;
import android.speech.tts.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.BaseFragment;
import java.io.*;
import java.util.*;

/** Explicit generated speech preview. Sharing uses the native chooser, never auto-sends. */
public final class NebulaSpeechFragment extends BaseFragment {
    private final String source;
    private String language;
    private EditText input;
    private TextView status;
    private NebulaRow voiceRow, languageRow;
    private NebulaButton generate, listen, share;
    private TextToSpeech device;
    private boolean ready, destroyed, busy, visible;
    private int generation;
    private NebulaAudioClient client;
    private MediaPlayer player;
    private File audio;
    private String audioText;
    public NebulaSpeechFragment(int account,String source,String language) { this.currentAccount=account;this.source=source;this.language=language==null||language.isEmpty()?LocaleController.getInstance().getCurrentLocale().getLanguage():language; }
    @Override public android.view.View createView(Context c) {
        NebulaFormUi.bar(this,actionBar,c,text("Озвучивание","Speech"));
        LinearLayout body=NebulaFormUi.column(c);
        body.addView(new NebulaSettingsHero(c,"🔊",text("Озвучивание","Speech"),text("Выберите голос и послушайте результат.","Choose a voice and listen to the result.")));
        input=NebulaFormUi.field(c,text("Текст для озвучивания","Text to read aloud"),1,50000);input.setSingleLine(false);input.setMinLines(3);
        input.setText(source);body.addView(input);
        input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){stop();discard();refresh();}public void afterTextChanged(android.text.Editable e){}});
        NebulaCard choices=new NebulaCard(c);
        choices.add(new NebulaRow(c).title(text("Сервис","Service")).subtitle(NebulaAudioPreferences.title(true),true).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->presentFragment(new NebulaAudioSettingsFragment())));
        languageRow=new NebulaRow(c).title(text("Язык системного голоса","Device voice language")).subtitle(language,true).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->chooseLanguage());
        voiceRow=new NebulaRow(c).title(text("Голос на устройстве","Device voice")).subtitle(text("Автоматически · лучший доступный","Automatic · best available"),true).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->chooseVoice());
        if("device".equals(NebulaAudioPreferences.serviceId(true))){choices.add(languageRow);choices.add(voiceRow);}
        NebulaFormUi.group(body,text("Голос","Voice"),choices);
        generate=NebulaFormUi.primary(c,text("Создать озвучивание","Generate speech"),v->start());body.addView(generate);
        listen=new NebulaButton(c,NebulaButton.STYLE_TEXT);listen.setText(text("Прослушать","Listen"));listen.setOnClickListener(v->play());body.addView(listen);
        NebulaButton stop=new NebulaButton(c,NebulaButton.STYLE_TEXT);stop.setText(text("Остановить","Stop"));stop.setOnClickListener(v->{stop();refresh();});body.addView(stop);
        share=new NebulaButton(c,NebulaButton.STYLE_TEXT);share.setText(text("Поделиться аудио","Share audio"));share.setOnClickListener(v->share());body.addView(share);
        status=NebulaFormUi.note(c,"");body.addView(status);
        body.addView(NebulaFormUi.note(c,text("Облачный голос создан ИИ. Текст отправится выбранному сервису только после нажатия. Системные голоса могут требовать установленный языковой пакет или сеть. Аудио отправляется через обычное меню «Поделиться».","Cloud speech is AI-generated. Text is sent to the selected service only after tapping. Device voices may require an installed language pack or network. Audio is shared using the standard share menu.")));
        refresh();return fragmentView=NebulaSettingsLayout.wrap(c,actionBar,NebulaFormUi.scroll(c,body),-12);
    }
    private void refresh(){if(generate!=null){generate.setEnabled(!busy);listen.setVisibility(audio!=null?android.view.View.VISIBLE:android.view.View.GONE);share.setVisibility(audio!=null?android.view.View.VISIBLE:android.view.View.GONE);}}
    private void start() {
        String value=input.getText().toString();if(value.length()>NebulaAudioProtocol.MAX_TEXT){input.setError(text("До 4000 символов за один запрос","Up to 4000 characters per request"));return;}if(value.trim().isEmpty()){input.setError(text("Введите текст","Enter text"));return;}
        stop();discard();
        if("device".equals(NebulaAudioPreferences.serviceId(true))){startDevice(value);return;}
        final NebulaAudioClient.Configuration config;
        try{config=NebulaAudioPreferences.capture(true);}catch(Exception e){status.setText(e.getMessage());return;}
        final int token=++generation;final long owner=UserConfig.getInstance(currentAccount).getClientUserId();
        busy=true;refresh();status.setText(text("Создаём голос ИИ… · ","Generating AI speech… · ")+NebulaAudioPreferences.title(true));
        final NebulaAudioClient request=client=new NebulaAudioClient();
        new Thread(()->{
            File completed=null;
            try{
                byte[] bytes=request.speech(config,value);
                completed=File.createTempFile("nebula-speech-",".wav",ApplicationLoader.applicationContext.getCacheDir());
                try(FileOutputStream out=new FileOutputStream(completed)){out.write(bytes);}
                final File result=completed;
                AndroidUtilities.runOnUIThread(()->{
                    if(destroyed||!visible||token!=generation||owner!=UserConfig.getInstance(currentAccount).getClientUserId()){result.delete();return;}
                    busy=false;client=null;audio=result;audioText=value;refresh();status.setText(text("Готово · голос создан ИИ","Ready · AI-generated voice"));play();
                });
            }catch(Exception e){if(completed!=null)completed.delete();AndroidUtilities.runOnUIThread(()->{if(!destroyed&&token==generation){busy=false;client=null;refresh();status.setText(text("Не удалось создать голос: ","Speech generation failed: ")+e.getMessage());}});}
        },"NebulaSpeech").start();
    }
    private void startDevice(String value) {
        if(device==null){final int token=++generation;busy=true;refresh();device=new TextToSpeech(getContext(),code->AndroidUtilities.runOnUIThread(()->{ready=code==TextToSpeech.SUCCESS;busy=false;refresh();if(!destroyed&&visible&&token==generation){if(ready)startDevice(input.getText().toString());else status.setText(text("Системный движок недоступен","Device speech engine unavailable"));}}));return;}
        if(!ready){status.setText(text("Дождитесь запуска движка","Wait for the speech engine"));return;}
        Locale locale=Locale.forLanguageTag(language);int supported=device.setLanguage(locale);
        if(supported<0){status.setText(text("Установите голос выбранного языка в настройках Android","Install this language's voice in Android settings"));return;}
        ArrayList<Voice> voices=voices();String name=NebulaAudioPreferences.prefs().getString("device_voice_"+language,"");Voice selected=null;
        for(Voice voice:voices)if(voice.getName().equals(name)){selected=voice;break;}
        if(selected==null&&!voices.isEmpty())selected=voices.get(0);
        if(selected!=null){device.setVoice(selected);voiceRow.subtitle(selected.getName()+(selected.isNetworkConnectionRequired()?text(" · сеть"," · online"):text(" · офлайн"," · offline")),true);}
        device.setSpeechRate(NebulaAudioPreferences.prefs().getFloat("speed",1));
        device.speak(value,TextToSpeech.QUEUE_FLUSH,null,"nebula-device-speech");status.setText(text("Читаем голосом устройства","Reading with the device voice"));
    }
    private ArrayList<Voice> voices(){ArrayList<Voice> result=new ArrayList<>();if(device!=null&&ready&&device.getVoices()!=null)for(Voice v:device.getVoices())if(v.getLocale().getLanguage().equals(Locale.forLanguageTag(language).getLanguage()))result.add(v);result.sort((a,b)->{int quality=Integer.compare(b.getQuality(),a.getQuality());return quality!=0?quality:a.getName().compareTo(b.getName());});return result;}
    private void chooseVoice(){
        if(!ready){status.setText(text("Нажмите «Создать озвучивание», чтобы загрузить список голосов","Tap Generate speech to load available voices"));return;}
        ArrayList<Voice> voices=voices();String[] names=new String[voices.size()+1];names[0]=text("Автоматически · лучший доступный","Automatic · best available");int selected=0;
        String current=NebulaAudioPreferences.prefs().getString("device_voice_"+language,"");
        for(int i=0;i<voices.size();i++){Voice v=voices.get(i);names[i+1]=v.getName()+(v.isNetworkConnectionRequired()?text(" · сеть"," · online"):text(" · офлайн"," · offline"));if(current.equals(v.getName()))selected=i+1;}
        showDialog(new NebulaDialog.Builder(getContext(),getResourceProvider()).setTitle(text("Голос","Voice")).setSelectedIndex(selected).setItems(names,(d,i)->{stop();NebulaAudioPreferences.prefs().edit().putString("device_voice_"+language,i==0?"":voices.get(i-1).getName()).apply();voiceRow.subtitle(names[i],true);}).create());
    }
    private void chooseLanguage(){
        ArrayList<TranslateController.Language> languages=TranslateController.getLanguages();String[] names=new String[languages.size()];int selected=-1;
        for(int i=0;i<names.length;i++){names[i]=languages.get(i).displayName;if(languages.get(i).code.equals(language))selected=i;}
        showDialog(new NebulaDialog.Builder(getContext(),getResourceProvider()).setTitle(text("Язык голоса","Voice language")).setSelectedIndex(selected).setItems(names,(d,i)->{stop();language=languages.get(i).code;languageRow.subtitle(names[i],true);voiceRow.subtitle(text("Автоматически","Automatic"),true);}).create());
    }
    private void play(){
        if(audio==null||!Objects.equals(audioText,input.getText().toString()))return;
        if(player!=null){player.release();player=null;}
        try{player=new MediaPlayer();player.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build());player.setDataSource(audio.getAbsolutePath());player.setOnCompletionListener(p->{p.release();if(player==p)player=null;});player.setOnErrorListener((p,w,e)->{p.release();if(player==p)player=null;status.setText(text("Не удалось воспроизвести аудио","Audio playback failed"));return true;});player.prepare();player.start();}
        catch(Exception e){if(player!=null){player.release();player=null;}status.setText(text("Не удалось воспроизвести аудио","Audio playback failed"));}
    }
    private void share(){
        if(audio==null||getParentActivity()==null||!Objects.equals(audioText,input.getText().toString()))return;
        try{
            File directory=new File(ApplicationLoader.applicationContext.getCacheDir(),"nebula-speech-exports");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Export unavailable");
            File[] old=directory.listFiles();if(old!=null)for(File f:old)if(f.isFile()&&System.currentTimeMillis()-f.lastModified()>48*60*60*1000L)f.delete();
            File copy=File.createTempFile("Nebula-AI-voice-",".wav",directory);try(FileInputStream in=new FileInputStream(audio);FileOutputStream out=new FileOutputStream(copy)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
            android.net.Uri uri=FileProvider.getUriForFile(getParentActivity(),ApplicationLoader.getApplicationId()+".provider",copy);
            Intent intent=new Intent(Intent.ACTION_SEND).setType("audio/wav").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("Nebula AI voice",uri));getParentActivity().startActivity(Intent.createChooser(intent,text("Поделиться аудио ИИ","Share AI-generated audio")));
        }catch(Exception e){status.setText(text("Не удалось поделиться аудио","Could not share audio"));}
    }
    private void stop(){generation++;busy=false;if(client!=null){client.cancel();client=null;}if(device!=null)device.stop();if(player!=null){player.release();player=null;}}
    private void discard(){if(audio!=null){audio.delete();audio=null;audioText=null;}}
    @Override public void onResume(){super.onResume();visible=true;refresh();}
    @Override public void onPause(){visible=false;stop();super.onPause();}
    @Override public void onFragmentDestroy(){destroyed=true;stop();discard();if(device!=null){device.shutdown();device=null;}super.onFragmentDestroy();}
}
