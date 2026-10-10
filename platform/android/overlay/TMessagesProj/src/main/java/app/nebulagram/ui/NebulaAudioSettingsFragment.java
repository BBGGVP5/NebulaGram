package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.widget.*;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import java.util.ArrayList;

public final class NebulaAudioSettingsFragment extends BaseFragment {
    private LinearLayout body;
    @Override public android.view.View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, text("Аудио и голоса", "Audio & voices"));
        body = NebulaFormUi.column(c); rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(c,actionBar,NebulaFormUi.scroll(c,body),-12);
    }
    @Override public void onResume() { super.onResume(); if (body != null) rebuild(); }
    private NebulaRow row(String title,String value,Runnable action) {
        return new NebulaRow(body.getContext()).title(title).subtitle(value,true).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->action.run());
    }
    private void rebuild() {
        Context c=body.getContext();body.removeAllViews();
        body.addView(new NebulaSettingsHero(c,"🎙️",text("Аудио и голоса","Audio & voices"),text("Распознавайте, переводите и озвучивайте сообщения.","Transcribe, translate and read messages aloud.")));
        NebulaCard card=new NebulaCard(c);
        card.add(new NebulaRow(c).title(text("Расшифровка Nebula", "Nebula transcription"))
                .subtitle(text("Голосовые и кружки локально или через ваш ИИ-сервис · без Telegram Premium", "Voice and video messages locally or through your AI service · no Telegram Premium required"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaTranscription.selected()).withClick(v -> {
                    if (NebulaTranscription.selected()) { NebulaTranscription.setEnabled(false); rebuild(); }
                    else if (!NebulaAudioPreferences.hasTranscriptionService()) chooseService(false, true);
                    else { NebulaTranscription.setEnabled(true); rebuild(); }
                }));
        card.add(row(text("Сервис расшифровки","Transcription service"),NebulaAudioPreferences.title(false),()->chooseService(false)));
        if (NebulaAudioPreferences.localTranscription()) {
            card.add(row(text("Локальная модель", "Local model"),NebulaAudioPreferences.localLocale(),()->presentFragment(new NebulaLocalAudioSettingsFragment(NebulaLocalAudioPolicy.NANO.equals(NebulaAudioPreferences.serviceId(false))))));
        }
        addModel(card,false);NebulaFormUi.group(body,text("Распознавание речи","Speech recognition"),card);
        body.addView(NebulaFormUi.note(c,text("Включите тумблер, выберите локальное распознавание или добавьте OpenAI/Gemini, затем нажмите кнопку расшифровки у голосового или кружка. Также доступно: удержание сообщения → «Расшифровать Nebula». Для локального распознавания выберите Gemini Nano или Android и проверьте аудиомодель.", "Enable the switch, choose local recognition or add OpenAI/Gemini, then tap the transcription button on a voice or video message. You can also hold the message and choose Nebula transcription. For local transcription choose Gemini Nano or Android and check the audio model.")));

        card=new NebulaCard(c);
        card.add(row(text("Озвучивание","Speech"),NebulaAudioPreferences.title(true),()->chooseService(true)));
        addModel(card,true);
        NebulaAiServices.Service service=NebulaAiServices.find(NebulaAudioPreferences.serviceId(true));
        if(service!=null && NebulaAudioPreferences.supported(service)) {
            card.add(row(text("Голос","Voice"),NebulaAudioPreferences.voice(service.provider),()->{
                String[] voices=service.provider==0?NebulaAudioProtocol.OPENAI_VOICES:NebulaAudioProtocol.GEMINI_VOICES;
                int selected=java.util.Arrays.asList(voices).indexOf(NebulaAudioPreferences.voice(service.provider));
                showDialog(new NebulaDialog.Builder(c,getResourceProvider()).setTitle(text("Голос ИИ","AI voice")).setSelectedIndex(selected).setItems(voices,(d,i)->{NebulaAudioPreferences.prefs().edit().putString("voice_"+service.provider,voices[i]).apply();rebuild();}).create());
            }));
        }
        String[] styles={"neutral","warm","calm","lively"},names={text("Естественно","Natural"),text("Дружелюбно","Friendly"),text("Спокойно","Calm"),text("Живо","Expressive")};
        int style=java.util.Arrays.asList(styles).indexOf(NebulaAudioPreferences.prefs().getString("style","neutral"));
        if(service!=null && NebulaAudioPreferences.supported(service)) card.add(row(text("Манера речи ИИ","AI speaking style"),names[Math.max(0,style)],()->showDialog(new NebulaDialog.Builder(c,getResourceProvider()).setTitle(text("Манера речи","Speaking style")).setSelectedIndex(style).setItems(names,(d,i)->{NebulaAudioPreferences.prefs().edit().putString("style",styles[i]).apply();rebuild();}).create())));
        float speed=NebulaAudioPreferences.prefs().getFloat("speed",1);
        card.add(row(text("Темп речи","Speaking pace"),speed+"×",()->showDialog(new NebulaDialog.Builder(c,getResourceProvider()).setTitle(text("Темп речи","Speaking pace")).setSelectedIndex(speed<1?0:speed>1?2:1).setItems(new String[]{"0.8×","1×","1.2×"},(d,i)->{NebulaAudioPreferences.prefs().edit().putFloat("speed",new float[]{.8f,1f,1.2f}[i]).apply();rebuild();}).create())));
        NebulaFormUi.group(body,text("Озвучивание текста","Text to speech"),card);
        body.addView(NebulaFormUi.note(c,text("Облачный голос создаётся ИИ. По нажатию текст или запись отправляется указанному сервису с вашим ключом; доступ и стоимость зависят от сервиса. Голос на устройстве выбирается в окне озвучивания. Модели аудио настраиваются отдельно от текстового чата. Записи: до 14 МБ, озвучивание: до 4000 символов.","Cloud speech is AI-generated. Tapping sends text or a recording to the named service using your key; availability and cost depend on that service. Choose an installed device voice in the speech screen. Audio models are separate from text chat. Recordings: up to 14 MB; speech: up to 4000 characters.")));
        body.addView(NebulaFormUi.primary(c,text("Настроить сервисы","Configure services"),v->presentFragment(new NebulaAiServicesFragment())));
    }
    private void addModel(NebulaCard card,boolean speech) {
        NebulaAiServices.Service service=NebulaAiServices.find(NebulaAudioPreferences.serviceId(speech));
        if(service==null || !NebulaAudioPreferences.supported(service)) return;
        card.add(row(text("Модель","Model"),NebulaAudioPreferences.model(speech,service),()->{
            EditText field=NebulaFormUi.field(body.getContext(),text("ID модели","Model ID"),1,256);field.setText(NebulaAudioPreferences.model(speech,service));
            showDialog(new NebulaDialog.Builder(body.getContext(),getResourceProvider()).setTitle(text("Модель аудио","Audio model")).setView(field).setPositiveButton(text("Сохранить","Save"),(d,w)->{
                try {String model=NebulaAudioProtocol.model(field.getText().toString().trim());NebulaAudioPreferences.prefs().edit().putString((speech?"speech_model_":"transcription_model_")+service.provider,model).apply();rebuild();}
                catch(Exception error){field.setError(text("Проверьте ID модели","Check the model ID"));}
            }).setNegativeButton(text("Отмена","Cancel"),null).create());
        }));
    }
    private void chooseService(boolean speech) { chooseService(speech, false); }
    private void chooseService(boolean speech, boolean enableAfterSelection) {
        ArrayList<String> ids=new ArrayList<>(),titles=new ArrayList<>();
        if(speech){ids.add("device");titles.add(text("На устройстве","On device"));}
        else {ids.add("");titles.add(text("Не выбран","Not selected"));ids.add(NebulaLocalAudioPolicy.NANO);titles.add(text("Gemini Nano · на устройстве","Gemini Nano · on device"));ids.add(NebulaLocalAudioPolicy.BASIC);titles.add(text("Android · на устройстве","Android · on device"));}
        for(NebulaAiServices.Service service:NebulaAiServices.list()) if(NebulaAudioPreferences.supported(service)){ids.add(service.id);titles.add(service.name+" · "+NebulaAiServices.providerName(service.provider));}
        int createOpenAI=ids.size();ids.add("create-openai");titles.add(text("Добавить OpenAI","Add OpenAI"));
        int createGemini=ids.size();ids.add("create-gemini");titles.add(text("Добавить Gemini","Add Gemini"));
        showDialog(new NebulaDialog.Builder(body.getContext(),getResourceProvider()).setTitle(text("Сервис аудио","Audio service")).setSelectedIndex(ids.indexOf(NebulaAudioPreferences.serviceId(speech))).setItems(titles.toArray(new String[0]),(d,i)->{
            if(i==createOpenAI || i==createGemini) {
                presentFragment(NebulaAiServiceEditorFragment.forAudio(i==createOpenAI?NebulaAiClient.OPENAI:NebulaAiClient.GEMINI,speech,service->{
                    NebulaAudioPreferences.prefs().edit().putString(speech?"speech_service":"transcription_service",service.id).putString((speech?"speech_model_":"transcription_model_")+service.provider,service.model).apply();
                    if(!speech&&enableAfterSelection)NebulaTranscription.setEnabled(true);
                }));return;
            }
            NebulaAudioPreferences.prefs().edit().putString(speech?"speech_service":"transcription_service",ids.get(i)).apply();
            if(!speech){if(ids.get(i).isEmpty())NebulaTranscription.setEnabled(false);else if(enableAfterSelection)NebulaTranscription.setEnabled(true);}
            rebuild();
            if (!speech && NebulaLocalAudioPolicy.local(ids.get(i))) presentFragment(new NebulaLocalAudioSettingsFragment(NebulaLocalAudioPolicy.NANO.equals(ids.get(i))));
        }).create());
    }
}
