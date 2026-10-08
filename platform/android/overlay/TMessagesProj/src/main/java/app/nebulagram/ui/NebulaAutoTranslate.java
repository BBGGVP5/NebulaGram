package app.nebulagram.ui;

import android.content.*;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in translation of visible text; one bounded queue and cancellable requests. */
public final class NebulaAutoTranslate {
    private static final ThreadPoolExecutor worker=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(20));
    private static final Map<String,Job> jobs=new HashMap<>();
    private static final LinkedHashMap<String,TLRPC.TL_textWithEntities> cache=new LinkedHashMap<String,TLRPC.TL_textWithEntities>(256,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,TLRPC.TL_textWithEntities> e){return size()>256;}};
    private static final Map<String,Long> failed=new HashMap<>();
    private static long lastError;
    private static final Map<TLRPC.Message,String> applied = new WeakHashMap<>();
    private static final Map<String,String> errors = new HashMap<>();
    private static final Map<String, ArrayList<MessageObject>> visibleSnapshots = new HashMap<>();
    private static String scope(int a, long d) { return NebulaTasks.user(a) + ":" + d; }
    public static String providerName() {
        int provider = NebulaTranslationSettings.global().getInt("provider", 0);
        return new String[]{"OpenAI", "Claude", "Gemini", NebulaText.text("ИИ", "AI"), "Gemini Nano"}[Math.max(0, Math.min(4, provider))];
    }
    public static String status(int a, long d) {
        String provider = "Nebula AI";
        for (Job job : jobs.values()) if (job.account == a && job.dialog == d && job.visible && !job.cancelled)
            return provider + " · " + (job.phase != null ? job.phase : NebulaText.text("переводим…", "translating…"));
        if (errors.containsKey(scope(a,d))) return provider + " · " + NebulaText.text("нужна проверка", "check required");
        return provider + " · " + NebulaText.text("перевод на ", "translate to ") + NebulaTranslationSettings.label(language(a,d));
    }
    public static void retry(int account, long dialog) {
        stop(account, dialog); failed.clear(); errors.remove(scope(account,dialog));
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogTranslate, dialog, enabled(account,dialog));
    }
    public static void showStatus(BaseFragment host, long dialog) {
        int account = host.getCurrentAccount();
        String detail = errors.get(scope(account,dialog));
        host.showDialog(new NebulaDialog.Builder(host.getContext(), host.getResourceProvider())
            .setTitle(NebulaText.text("Nebula AI · перевод", "Nebula AI · translation"))
            .setMessage((NebulaTranslationSettings.local() ? NebulaText.text("Быстрый перевод на устройстве", "Fast on-device translation") : providerName()) + "\n\n" + (detail != null ? detail : NebulaText.text("Видимые сообщения переводит выбранный движок Nebula AI. Язык, модель и перевод при наборе доступны в настройках.", "Your selected Nebula AI engine translates visible messages. Language, model and typing translation are available in settings.")))
            .setPositiveButton(NebulaText.text("Повторить", "Retry"), (d,w) -> retry(account,dialog))
            .setNeutralButton(NebulaText.text("Настройки", "Settings"), (d,w) -> configure(host,dialog))
            .setNegativeButton(NebulaText.text("Выключить", "Turn off"), (d,w) -> disableAll(account,dialog)).create());
    }
    private static String failure(Exception error) {
        return NebulaTranslationClient.errorText(error);
    }
    private static SharedPreferences prefs(int a){return NebulaTranslationSettings.prefs(a);}
    public static boolean enabled(int a,long d){return d!=0&&!DialogObject.isEncryptedDialog(d)&&(NebulaTranslationSettings.local()||NebulaAiAvailability.enabled())&&NebulaTasks.user(a)>0&&(prefs(a).getBoolean("on_"+d,false)||NebulaTranslationSettings.outgoing(a,d));}
    public static boolean enabledForMessage(int a, MessageObject message) {
        return enabled(a,message.getDialogId()) && (message.isOutOwner()
            ? NebulaTranslationSettings.outgoing(a,message.getDialogId()) : prefs(a).getBoolean("on_"+message.getDialogId(),false));
    }
    public static void setOutgoing(int a,long d,boolean on) {
        stop(a,d); prefs(a).edit().putBoolean("outgoing_"+d,on).apply();
        NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogTranslate,d,enabled(a,d));
    }
    public static void retainVisible(int a,long d,Collection<MessageObject> visible) {
        ArrayList<MessageObject> snapshot = new ArrayList<>(visible);
        snapshot.removeIf(m -> m == null || m.messageOwner == null);
        snapshot.sort(Comparator.comparingInt(m -> m.messageOwner.message == null ? 0 : m.messageOwner.message.length()));
        visibleSnapshots.put(scope(a,d), snapshot);
        Iterator<Map.Entry<String,Job>> iterator=jobs.entrySet().iterator();
        while(iterator.hasNext()) {
            Job job=iterator.next().getValue(); if(job.account!=a||job.dialog!=d)continue;
            boolean keep=false;
            boolean valid = job.user == NebulaTasks.user(a) && job.connectionIdentity.equals(NebulaTranslationSettings.translationIdentity()) && enabledForMessage(a,job.message)
                && job.matches(job.message) && job.lang.equals(language(a,d));
            if(valid)
                for(MessageObject message:visible) if(message.getId()==job.message.getId() && job.matches(message)){keep=true;break;}
            job.visible = keep;
            // Do not repeatedly restart an inference that already began during a scroll.
            if(!valid || !keep && !job.started){job.cancelled=true;job.client.cancel();worker.remove(job);iterator.remove();invalidateProgress(a,job.message);}
        }
        refill(a,d);
    }
    private static void refill(int account, long dialog) {
        ArrayList<MessageObject> snapshot = visibleSnapshots.get(scope(account,dialog));
        if (snapshot != null) for (MessageObject message : snapshot) request(account,message);
    }
    public static boolean isTranslating(int account, MessageObject message) {
        if (message == null || message.messageOwner == null || !enabledForMessage(account, message)) return false;
        for (Job job : jobs.values()) {
            if (job.account == account && job.dialog == message.getDialogId() && job.message.getId() == message.getId()
                && !job.cancelled && job.visible && job.matches(message)) return true;
        }
        return false;
    }
    private static void invalidateProgress(int account, MessageObject message) {
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.messageTranslating, message);
    }
    public static void disableAll(int account,long dialog) {
        prefs(account).edit().putBoolean("outgoing_"+dialog,false).apply(); disable(account,dialog);
    }
    public static void disable(int account,long dialog) {
        stop(account,dialog);errors.remove(scope(account,dialog));prefs(account).edit().putBoolean("on_"+dialog,false).apply();
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogTranslate,dialog,enabled(account,dialog));
    }
    public static void setLanguage(int account,long dialog,String language) {
        stop(account,dialog);errors.remove(scope(account,dialog));prefs(account).edit().putString("language_"+dialog,language).apply();
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogTranslate,dialog,enabled(account,dialog));
    }
    public static String language(int a,long d){return prefs(a).getString("language_"+d,"ru");}
    public static String tag(int a,long d){return language(a,d);}
    public static void configure(BaseFragment host,long dialog){
        host.presentFragment(new NebulaTranslationFragment(host.getCurrentAccount(), dialog));
    }

    public static void request(int account,MessageObject message){
        if (message == null || message.messageOwner == null) return;
        long dialog=message.getDialogId();String text=message.messageOwner.message;
        if(!enabledForMessage(account,message)||text==null||text.trim().isEmpty()||text.length()>12000||message.messageOwner.noforwards
                ||message.messageOwner.ttl>0||message.isSecretMedia()||message.getId()<=0
                ||message.isRestrictedMessage||message.isSponsored()||(!message.isOutOwner()&&!TranslateController.isTranslatable(message)))return;
        TLRPC.Chat chat=dialog<0?MessagesController.getInstance(account).getChat(-dialog):null;
        if(chat!=null&&chat.noforwards)return;
        TLRPC.TL_textWithEntities richSource = NebulaRichText.copy(text, message.messageOwner.entities);
        String sourceIdentity = NebulaRichText.key(richSource);
        String lang=language(account,dialog), key=NebulaTranslationSettings.translationIdentity()+":"+NebulaTasks.user(account)+":"+dialog+":"+message.getId()+":"+lang+":"+sourceIdentity;
        if (key.equals(applied.get(message.messageOwner)) && lang.equals(message.messageOwner.translatedToLanguage) && NebulaRichText.same(cache.get(key), message.messageOwner.translatedText)) return;
        if(cache.containsKey(key)){applied.put(message.messageOwner,key);apply(account,message,lang,cache.get(key));return;}
        Long retry=failed.get(key);if(jobs.containsKey(key)||retry!=null&&retry>System.currentTimeMillis())return;
        // Never reuse a Telegram/provider-old result or allow its pending request to win the race.
        MessagesController.getInstance(account).getTranslateController().cancelTranslations(dialog);
        if (message.messageOwner.translatedText != null) {
            message.messageOwner.translatedText = null; message.messageOwner.translatedToLanguage = null;
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.messageTranslated,message);
        }
        Job job=new Job(account,dialog,message,lang,text,key,richSource,sourceIdentity);jobs.put(key,job);
        try{worker.execute(job);invalidateProgress(account,message);}catch(RejectedExecutionException e){jobs.remove(key);}
    }
    private static void apply(int account,MessageObject message,String lang,TLRPC.TL_textWithEntities result){
        if(!enabledForMessage(account,message)||!lang.equals(language(account,message.getDialogId())))return;
        if (message.messageOwner.translatedText != null && !result.text.equals(message.messageOwner.translatedText.text)) {
            message.messageOwner.translatedText = null;
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.messageTranslated,message);
        }
        TLRPC.TL_textWithEntities translated=NebulaRichText.copy(result.text, result.entities);
        message.messageOwner.translatedText=translated;message.messageOwner.translatedToLanguage=lang;
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.messageTranslated,message);
    }
    public static void stop(int account,long dialog){
        visibleSnapshots.remove(scope(account,dialog));
        Iterator<Map.Entry<String,Job>> iterator=jobs.entrySet().iterator();while(iterator.hasNext()){Job j=iterator.next().getValue();if(j.account==account&&j.dialog==dialog){j.cancelled=true;j.client.cancel();worker.remove(j);iterator.remove();invalidateProgress(account,j.message);}}
    }
    private static final class Job implements Runnable {
        final long user;final String connectionIdentity = NebulaTranslationSettings.translationIdentity();final int account;final long dialog;final MessageObject message;final String lang,text,key;final NebulaTranslationClient client=new NebulaTranslationClient();volatile boolean cancelled, started;boolean visible=true;String phase;
        final TLRPC.TL_textWithEntities source; final String sourceIdentity;
        Job(int a,long d,MessageObject m,String l,String t,String k,TLRPC.TL_textWithEntities source,String identity){account=a;user=NebulaTasks.user(a);dialog=d;message=m;lang=l;text=t;key=k;this.source=source;sourceIdentity=identity;}
        boolean matches(MessageObject m) { return text.equals(m.messageOwner.message) && sourceIdentity.equals(NebulaRichText.key(NebulaRichText.copy(m.messageOwner.message, m.messageOwner.entities))); }
        public void run(){started=true;TLRPC.TL_textWithEntities result=null;String error=null;try{if(cancelled)return;if(user!=NebulaTasks.user(account)||!connectionIdentity.equals(NebulaTranslationSettings.translationIdentity())||!enabledForMessage(account,message))throw new java.io.InterruptedIOException();result=NebulaRichText.translate(client,source,lang,false,value -> AndroidUtilities.runOnUIThread(() -> {if(jobs.get(key)==this && !cancelled){phase=value;invalidateProgress(account,message);}}));}catch(Exception e){error=failure(e);}final TLRPC.TL_textWithEntities translated=result;final String problem=error;
            AndroidUtilities.runOnUIThread(()->{if(jobs.get(key)!=this)return;jobs.remove(key);invalidateProgress(account,message);if(cancelled||user!=NebulaTasks.user(account)||!connectionIdentity.equals(NebulaTranslationSettings.translationIdentity())||!enabledForMessage(account,message)||!matches(message)||!lang.equals(language(account,dialog))){refill(account,dialog);return;}if(translated==null||translated.text.trim().isEmpty()){
                if(failed.size()>256)failed.clear();failed.put(key,System.currentTimeMillis()+30000);
                if(errors.size()>256)errors.clear();errors.put(scope(account,dialog),problem != null ? problem : NebulaText.text("Модель вернула пустой перевод", "The model returned an empty translation"));
                invalidateProgress(account,message);
                if (System.currentTimeMillis()-lastError>30000) { lastError=System.currentTimeMillis(); Toast.makeText(ApplicationLoader.applicationContext,errors.get(scope(account,dialog)),Toast.LENGTH_SHORT).show(); }
            }else{errors.remove(scope(account,dialog));cache.put(key,translated);if(visible){applied.put(message.messageOwner,key);apply(account,message,lang,translated);}}refill(account,dialog);});
        }
    }
}
