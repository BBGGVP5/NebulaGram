package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import java.io.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Explicit Aa preview: recognizes a paused snapshot, never sends or deletes the voice. */
public final class NebulaDraftVoiceTranscription {
    public interface InlineHost { void show(View content); void hide(); }
    private final NebulaDraftVoicePolicy.Snapshot source;
    private final Supplier<NebulaDraftVoicePolicy.Snapshot> current;
    private final Consumer<String> useText;
    private final BiConsumer<NebulaDraftVoicePolicy.Snapshot,String> remember;
    private final NebulaDraftVoicePolicy.Gate gate=new NebulaDraftVoicePolicy.Gate();
    private final BaseFragment host;
    private BottomSheet sheet;
    private EditText transcript;
    private TextView status;
    private TextView retry, apply;
    private NebulaAiPanelUi.Glyph glyph;
    private final InlineHost inline;
    private boolean textSelected = true;
    private volatile boolean cancelled;
    private boolean closed, busy, ready;
    private NebulaAudioClient cloud;
    private NebulaLocalTranscription local;
    private Thread worker;
    private final Runnable scopeCheck = new Runnable() {
        @Override public void run() {
            if(closed)return;
            if(!source.equals(current.get())) { cancel();return; }
            AndroidUtilities.runOnUIThread(this,250);
        }
    };

    public static boolean enabled() { return NebulaAudioPreferences.prefs().getBoolean("draft_transcription",true); }
    public static String serviceKey() {
        String id=NebulaAudioPreferences.serviceId(false);
        NebulaAiServices.Service service=NebulaAiServices.find(id);
        return id+"|"+NebulaAudioPreferences.localLocale()+"|"+(service==null?"":NebulaAudioPreferences.model(false,service));
    }
    public NebulaDraftVoiceTranscription(BaseFragment host,NebulaDraftVoicePolicy.Snapshot source,
            Supplier<NebulaDraftVoicePolicy.Snapshot> current,String cached,boolean changed,
            Consumer<String> useText,BiConsumer<NebulaDraftVoicePolicy.Snapshot,String> remember) {
        this(host,source,current,cached,changed,useText,remember,null);
    }
    public NebulaDraftVoiceTranscription(BaseFragment host,NebulaDraftVoicePolicy.Snapshot source,
            Supplier<NebulaDraftVoicePolicy.Snapshot> current,String cached,boolean changed,
            Consumer<String> useText,BiConsumer<NebulaDraftVoicePolicy.Snapshot,String> remember,InlineHost inline) {
        this.host=host;this.source=source;this.current=current;this.useText=useText;this.remember=remember;
        this.inline=inline;
        Context context=host.getParentActivity();
        LinearLayout body=NebulaAiPanelUi.column(context,inline!=null);
        LinearLayout header=NebulaAiPanelUi.header(context,NebulaAiPanelUi.VOICE,
                NebulaText.text("Голосовое в текст","Voice to text"),NebulaAudioPreferences.title(false),this::cancel);
        glyph=(NebulaAiPanelUi.Glyph)header.getChildAt(0);
        LinearLayout labels=(LinearLayout)header.getChildAt(1);
        ((TextView)labels.getChildAt(0)).setTextSize(inline==null?20:17);
        labels.setOnClickListener(v->{cancel();host.presentFragment(new NebulaAudioSettingsFragment());});
        labels.setContentDescription(NebulaText.text("Выбрать сервис распознавания","Choose recognition service"));
        body.addView(header);
        TextView voice=NebulaAiPanelUi.button(context,NebulaText.text("Голосовое","Voice"),false,v->{textSelected=false;cancel();});
        TextView text=NebulaAiPanelUi.button(context,NebulaText.text("Текст","Text"),true,v->{textSelected=true;transcript.requestFocus();});
        text.setSelected(true);
        LinearLayout choices=NebulaAiPanelUi.actions(context,voice,text);
        NebulaAiPanelUi.add(body,choices,10);
        transcript=NebulaAiPanelUi.editor(context,NebulaText.text("Здесь появится текст записи","The transcript appears here"),3);
        transcript.setMaxLines(inline==null?8:4);transcript.setText(cached==null?"":cached);
        if(inline!=null) { transcript.setHeight(NebulaAiPanelUi.dp(96));transcript.setVerticalScrollBarEnabled(true); }
        NebulaAiPanelUi.add(body,transcript,10);
        status=NebulaAiPanelUi.caption(context,changed?NebulaText.text("Запись или сервис изменились. Распознайте заново.","The recording or service changed. Transcribe again."):cached!=null?NebulaText.text("Проверьте текст перед отправкой","Review the text before sending"):"");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        retry=NebulaAiPanelUi.button(context,NebulaText.text("Распознать","Transcribe"),false,v->start());
        retry.setTextSize(13);
        LinearLayout footer=new LinearLayout(context);footer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        footer.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams retryParams=new LinearLayout.LayoutParams(-2,-2);retryParams.leftMargin=NebulaAiPanelUi.dp(10);
        footer.addView(retry,retryParams);NebulaAiPanelUi.add(body,footer,8);
        ready=cached!=null&&!changed;
        transcript.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                if(!busy&&transcript.hasFocus()&&source.equals(current.get())) {
                    ready=true;remember.accept(source,s.toString());refresh();
                }
            }
            public void afterTextChanged(android.text.Editable e){}
        });
        if(inline!=null) {
            ScrollView content=NebulaAiPanelUi.scroll(context,body);
            if(inline instanceof NebulaDraftVoiceInlineHost) {
                NebulaDraftVoiceInlineHost recorder=(NebulaDraftVoiceInlineHost)inline;
                recorder.onDismiss(this::cancel);
                java.util.function.IntConsumer viewport=height->{
                    if(closed)return;
                    boolean compact=height<NebulaAiPanelUi.dp(240)&&transcript.hasFocus();
                    boolean modeChanged=(header.getVisibility()==View.GONE)!=compact;
                    header.setVisibility(compact?View.GONE:View.VISIBLE);footer.setVisibility(compact?View.GONE:View.VISIBLE);
                    body.setPadding(NebulaAiPanelUi.dp(14),NebulaAiPanelUi.dp(compact?8:12),NebulaAiPanelUi.dp(14),NebulaAiPanelUi.dp(compact?8:12));
                    LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)choices.getLayoutParams();
                    int margin=NebulaAiPanelUi.dp(compact?0:10);if(params.topMargin!=margin){params.topMargin=margin;choices.setLayoutParams(params);}
                    transcript.setHeight(compact?Math.min(NebulaAiPanelUi.dp(96),Math.max(NebulaAiPanelUi.dp(48),height-NebulaAiPanelUi.dp(76))):NebulaAiPanelUi.dp(96));
                    if(modeChanged)content.scrollTo(0,0);
                };
                recorder.onHeightChanged(viewport);
                transcript.setOnFocusChangeListener((v,focused)->viewport.accept(recorder.availableHeight()));
            }
            inline.show(content);
        }
        else {
            apply=NebulaAiPanelUi.button(context,NebulaText.text("Использовать текст","Use text"),true,v->{
                String value=textForSend();if(value==null)return;cancel();useText.accept(value);
            });NebulaAiPanelUi.add(body,apply,12);
            sheet=new BottomSheet.Builder(context,false,host.getResourceProvider()).setCustomView(NebulaAiPanelUi.scroll(context,body)).create();
            sheet.setOnDismissListener(d->{closed=true;stop();});host.showDialog(sheet);
        }
        refresh();
        AndroidUtilities.runOnUIThread(scopeCheck,250);
        if(!ready&&!changed)start();
    }
    public boolean isTextSelected(){return !closed&&textSelected;}
    public boolean isShowing(){return !closed;}
    public String textForSend(){
        if(!isTextSelected()||!ready||busy||!source.equals(current.get()))return null;
        String value=transcript.getText().toString().trim();return value.isEmpty()?null:value;
    }
    private void refresh(){
        NebulaAiPanelUi.enabled(retry,!busy);
        retry.setText(ready?NebulaText.text("Повторить","Retry"):NebulaText.text("Распознать","Transcribe"));
        if(apply!=null)NebulaAiPanelUi.enabled(apply,ready&&!busy);
        transcript.setEnabled(!busy);glyph.setBusy(busy);
    }
    private void start() {
        if(closed||busy||!source.equals(current.get()))return;
        stop();cancelled=false;ready=false;busy=true;refresh();
        boolean localMode=NebulaAudioPreferences.localTranscription();
        if(org.telegram.messenger.DialogObject.isEncryptedDialog(source.dialog)&&!localMode) {
            busy=false;status.setText(NebulaText.text("Для секретного чата выберите распознавание на устройстве.","Choose on-device recognition for a secret chat."));refresh();return;
        }
        final NebulaAudioClient.Configuration configuration;
        try{configuration=localMode?null:NebulaAudioPreferences.capture(false);}catch(Exception failure){busy=false;status.setText(failure.getMessage());refresh();return;}
        final NebulaAudioClient request=cloud=localMode?null:new NebulaAudioClient();
        final NebulaLocalTranscription localRequest=local=localMode?new NebulaLocalTranscription(NebulaLocalAudioPolicy.NANO.equals(NebulaAudioPreferences.serviceId(false)),NebulaAudioPreferences.localLocale()):null;
        final int token=gate.begin(source);
        status.setText(NebulaText.text("Распознаём выбранный фрагмент… · ","Transcribing selected fragment… · ")+NebulaAudioPreferences.title(false));
        worker=new Thread(()->{
            File pcm=null,wave=null;
            try {
                pcm=File.createTempFile("nebula-draft-",".pcm",ApplicationLoader.applicationContext.getCacheDir());
                wave=File.createTempFile("nebula-draft-",localMode?".pcm":".wav",ApplicationLoader.applicationContext.getCacheDir());
                NebulaLocalAudioDecoder.decode(new File(source.path),pcm,()->cancelled);
                if(localMode)NebulaDraftVoicePolicy.pcm(pcm,wave,source.leftMs,source.rightMs,NebulaAudioProtocol.MAX_INPUT,()->cancelled);
                else NebulaDraftVoicePolicy.wave(pcm,wave,source.leftMs,source.rightMs,NebulaAudioProtocol.MAX_INPUT,()->cancelled);
                if(cancelled)throw new InterruptedIOException("Cancelled");
                String answer;
                if(localMode)answer=localRequest.transcribePcm(wave,new NebulaLocalTranscription.Progress(){
                    public void text(String value){AndroidUtilities.runOnUIThread(()->{if(!closed&&gate.accepts(token,current.get()))transcript.setText(value);});}
                    public void status(String value){ }
                });
                else answer=request.transcribe(configuration,wave,"audio/wav");
                final String result=answer;
                AndroidUtilities.runOnUIThread(()->{
                    if(closed||!gate.complete(token,current.get()))return;
                    busy=false;cloud=null;local=null;worker=null;ready=!result.trim().isEmpty();transcript.setText(result);
                    status.setText(ready?NebulaText.text("Проверьте текст перед отправкой","Review the text before sending"):NebulaText.text("Речь не распознана","No speech recognized"));
                    if(ready)remember.accept(source,result);refresh();
                });
            } catch(Exception failure) {
                AndroidUtilities.runOnUIThread(()->{
                    if(closed||!gate.complete(token,current.get()))return;
                    busy=false;cloud=null;local=null;worker=null;ready=false;
                    status.setText("DRAFT_VOICE_TOO_LONG".equals(failure.getMessage())?NebulaText.text("Выбранный фрагмент слишком большой. Обрежьте запись и повторите.","The selected fragment is too large. Trim it and retry."):localMode?NebulaLocalTranscription.errorText(failure):NebulaText.text("Не удалось распознать. Проверьте сервис и повторите. Голосовое сохранено.","Transcription failed. Check the service and retry. The voice draft is kept."));refresh();
                });
            } finally { if(pcm!=null)pcm.delete();if(wave!=null)wave.delete(); }
        },"NebulaDraftVoice");worker.start();
    }
    private void stop(){cancelled=true;gate.cancel();if(cloud!=null)cloud.cancel();if(local!=null)local.cancel();if(worker!=null)worker.interrupt();cloud=null;local=null;worker=null;busy=false;if(closed)AndroidUtilities.cancelRunOnUIThread(scopeCheck);}
    public void cancel(){
        if(closed)return;
        if(ready&&!busy&&source.equals(current.get()))remember.accept(source,transcript.getText().toString());
        closed=true;stop();glyph.setBusy(false);if(sheet!=null)sheet.dismiss();if(inline!=null)inline.hide();
    }
}
