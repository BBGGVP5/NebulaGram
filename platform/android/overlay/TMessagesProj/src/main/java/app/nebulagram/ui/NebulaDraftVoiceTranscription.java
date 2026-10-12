package app.nebulagram.ui;

import android.content.Context;
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
    private final NebulaDraftVoicePolicy.Snapshot source;
    private final Supplier<NebulaDraftVoicePolicy.Snapshot> current;
    private final Consumer<String> useText;
    private final BiConsumer<NebulaDraftVoicePolicy.Snapshot,String> remember;
    private final NebulaDraftVoicePolicy.Gate gate=new NebulaDraftVoicePolicy.Gate();
    private final BaseFragment host;
    private BottomSheet sheet;
    private EditText transcript;
    private TextView status;
    private NebulaButton retry, apply;
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
        this.host=host;this.source=source;this.current=current;this.useText=useText;this.remember=remember;
        Context context=host.getParentActivity();
        LinearLayout body=NebulaFormUi.column(context);
        TextView title=NebulaFormUi.note(context,NebulaText.text("Голосовое в текст","Voice to text"));
        title.setTextSize(22);body.addView(title);
        body.addView(new NebulaRow(context).title(NebulaText.text("Сервис распознавания","Recognition service"))
                .subtitle(NebulaAudioPreferences.title(false),true).withClick(v->{cancel();host.presentFragment(new NebulaAudioSettingsFragment());}));
        status=NebulaFormUi.note(context,changed?NebulaText.text("Запись или фрагмент изменились. Расшифруйте заново.","The recording or fragment changed. Transcribe it again."):"");body.addView(status);
        transcript=NebulaFormUi.field(context,NebulaText.text("Здесь появится текст записи","The transcript appears here"),1,50000);
        transcript.setSingleLine(false);transcript.setMinLines(3);transcript.setMaxLines(8);transcript.setText(cached==null?"":cached);
        body.addView(transcript,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        ready=cached!=null&&!cached.trim().isEmpty();
        retry=NebulaFormUi.primary(context,NebulaText.text("Расшифровать","Transcribe"),v->start());body.addView(retry);
        apply=NebulaFormUi.primary(context,NebulaText.text("Использовать текст","Use text"),v->{
            String value=transcript.getText().toString().trim();
            if(!ready||busy||value.isEmpty()||!source.equals(current.get()))return;
            cancel();useText.accept(value);
        });body.addView(apply);
        NebulaButton keep=new NebulaButton(context,NebulaButton.STYLE_TEXT);keep.setText(NebulaText.text("Оставить голосовое","Keep voice message"));keep.setOnClickListener(v->cancel());body.addView(keep);
        body.addView(NebulaFormUi.note(context,NebulaText.text("Текст заменит голосовое в поле сообщения. Перед отправкой его можно проверить и отредактировать.","The text replaces the voice draft in the composer. You can review and edit it before sending.")));
        sheet=new BottomSheet.Builder(context,false,host.getResourceProvider()).setCustomView(NebulaFormUi.scroll(context,body)).create();
        sheet.setOnDismissListener(d->{closed=true;stop();});
        refresh();host.showDialog(sheet);
        AndroidUtilities.runOnUIThread(scopeCheck,250);
        if(!ready&&!changed)start();
    }
    private void refresh(){retry.setEnabled(!busy);apply.setEnabled(ready&&!busy);transcript.setEnabled(!busy);}
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
                wave=File.createTempFile("nebula-draft-",".wav",ApplicationLoader.applicationContext.getCacheDir());
                NebulaLocalAudioDecoder.decode(new File(source.path),pcm,()->cancelled);
                NebulaDraftVoicePolicy.wave(pcm,wave,source.leftMs,source.rightMs,NebulaAudioProtocol.MAX_INPUT,()->cancelled);
                if(cancelled)throw new InterruptedIOException("Cancelled");
                String answer;
                if(localMode)answer=localRequest.transcribe(wave,new NebulaLocalTranscription.Progress(){
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
    public void cancel(){if(closed)return;closed=true;stop();if(sheet!=null)sheet.dismiss();}
}
