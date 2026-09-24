package app.nebulagram.ui;

import android.content.*;
import android.util.AtomicFile;
import org.telegram.messenger.*;
import org.telegram.tgnet.*;
import org.telegram.ui.ActionBar.Theme;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One message per installation in Saved Messages; never edits another device's snapshot. */
public final class NebulaSettingsSync {
    private static final NebulaSettingsSync INSTANCE = new NebulaSettingsSync();
    public static NebulaSettingsSync get() { return INSTANCE; }
    private final Set<Runnable> observers = new HashSet<>();
    private final List<NebulaSyncDocument> documents = new ArrayList<>();
    private final List<NebulaSyncDocument> choices = new ArrayList<>();
    private int account=-1, generation, ownMessage;
    private long user;
    private boolean foreground, busy, applying;
    private String status="", device;
    private SortedMap<String,Object> captured;
    private final Runnable scheduled = () -> sync(-1);
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (p,key) -> {
        if(!applying && key!=null && NebulaSettingsSchema.types.containsKey(key)) schedule(3000);
    };
    private boolean listening;
    private SharedPreferences prefs() { return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram",0); }
    private SharedPreferences state() { return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram_cloud_sync",0); }
    public boolean enabled(int a) {
        long id=UserConfig.getInstance(a).getClientUserId();
        if(id==0 || state().getLong("owner",0)!=id)return false;
        try{return device().equals(state().getString("device",""));}catch(Exception e){return false;}
    }
    public String status() { return status; }
    public boolean busy() { return busy; }
    public List<NebulaSyncDocument> choices() { return new ArrayList<>(choices); }
    public long lastTime(int a) { return state().getLong("time_"+UserConfig.getInstance(a).getClientUserId(),0); }
    public void observe(Runnable r) { observers.add(r); }
    public void unobserve(Runnable r) { observers.remove(r); }
    private void changed(String text) { status=text;for(Runnable r:new ArrayList<>(observers))r.run(); }
    private String t(String ru,String en) { return NebulaText.text(ru,en); }
    public void start(int a) {
        foreground=true;
        long id=UserConfig.getInstance(a).getClientUserId();
        if(account!=a || user!=id) { generation++;busy=false;account=a;user=id;choices.clear();documents.clear();status=""; }
        if(!listening) { prefs().registerOnSharedPreferenceChangeListener(listener); listening=true; }
        schedule(1000);
    }
    public void pause() { foreground=false;generation++;busy=false;AndroidUtilities.cancelRunOnUIThread(scheduled); }
    public void enable(int a, boolean value) {
        generation++;busy=false;choices.clear();
        if(value){
            try {
                String id=device();
                if(!id.equals(state().getString("device","")))state().edit().clear().putString("device",id).commit();
            }catch(Exception e){changed(t("Не удалось подготовить синхронизацию","Could not prepare sync"));return;}
        }
        state().edit().putLong("owner",value?UserConfig.getInstance(a).getClientUserId():0).apply();
        start(a);
        changed(value?t("Проверяем «Избранное»…","Checking Saved Messages…"):t("Синхронизация выключена","Sync is off"));
        if(value)sync(-1);
    }
    private void schedule(long delay) {
        AndroidUtilities.cancelRunOnUIThread(scheduled);
        if(foreground && account>=0 && enabled(account))AndroidUtilities.runOnUIThread(scheduled,delay);
    }
    private String device() throws Exception {
        if(device!=null)return device;
        AtomicFile file=new AtomicFile(new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(),"nebula-sync-device"));
        if(file.getBaseFile().exists())device=new String(file.readFully(),StandardCharsets.UTF_8);
        else {
            device=UUID.randomUUID().toString(); FileOutputStream out=null;
            try { out=file.startWrite();out.write(device.getBytes(StandardCharsets.UTF_8));file.finishWrite(out); }
            catch(Exception e){if(out!=null)file.failWrite(out);device=null;throw e;}
        }
        if(!device.matches("[a-f0-9-]{36}"))throw new IOException("device");
        return device;
    }
    private String fingerprint(Map<String,Object> values) throws Exception {
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(new TreeMap<>(values).toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
    }
    /** -1 automatic; 0 keep this device; positive selects a displayed cloud version. */
    public void sync(int choice) {
        if(busy || account<0 || !enabled(account))return;
        NebulaSyncDocument selected=choice>0 && choice<=choices.size()?choices.get(choice-1):null;
        if(choice>0 && selected==null)return;
        busy=true; captured=NebulaSyncDocument.portable(prefs().getAll());
        documents.clear();ownMessage=0;
        changed(t("Синхронизация…","Syncing…"));
        try{device();read(0,0,generation,choice,selected);}catch(Exception e){fail();}
    }
    private boolean valid(int token) { return foreground && token==generation && account>=0 && enabled(account) && UserConfig.getInstance(account).getClientUserId()==user; }
    private void read(int offset,int count,int token,int choice,NebulaSyncDocument selected) {
        TLRPC.TL_messages_search req=new TLRPC.TL_messages_search();
        req.peer=new TLRPC.TL_inputPeerSelf();req.q="#NebulaGramSettingsV1";req.filter=new TLRPC.TL_inputMessagesFilterEmpty();req.saved_reaction=null;req.limit=50;req.offset_id=offset;
        ConnectionsManager.getInstance(account).sendRequest(req,(response,error)->AndroidUtilities.runOnUIThread(()->{
            if(!valid(token))return;
            if(error!=null || !(response instanceof TLRPC.messages_Messages)){fail();return;}
            TLRPC.messages_Messages result=(TLRPC.messages_Messages)response;
            int next=offset;
            try {
                for(TLRPC.Message m:result.messages) {
                    next=m.id;
                    if(!m.out || m.peer_id==null || m.peer_id.user_id!=user ||
                            m.from_id!=null && m.from_id.user_id!=user || m.fwd_from!=null ||
                            m.message==null || !m.message.startsWith(NebulaSyncDocument.MARKER))continue;
                    // Invalid or newer snapshots stop automatic writes; never silently overwrite unknown data.
                    NebulaSyncDocument d=NebulaSyncDocument.parse(m.message);documents.add(d);
                    if(d.device.equals(device))ownMessage=Math.max(ownMessage,m.id);
                }
                if(result.messages.size()==50) {
                    if(count+50>=200 || next==offset)throw new IOException("snapshot limit");
                    read(next,count+50,token,choice,selected);return;
                }
                if(ownMessage!=0)state().edit().remove("pending_"+user).remove("pending_payload_"+user).apply();
                reconcile(token,choice,selected);
            } catch(Exception e){fail();}
        }));
    }
    private void reconcile(int token,int choice,NebulaSyncDocument selected) throws Exception {
        if(!captured.equals(NebulaSyncDocument.portable(prefs().getAll()))) {busy=false;schedule(3000);return;}
        List<NebulaSyncDocument> heads=NebulaSyncDocument.heads(documents);
        choices.clear();
        for(NebulaSyncDocument h:heads) {
            boolean same=false;for(NebulaSyncDocument c:choices)if(c.settings.equals(h.settings)){same=true;break;}
            if(!same)choices.add(h);
        }
        if(choice>=0) {
            if(selected!=null) {
                // A choice is tied to exactly the reviewed snapshot. New heads require a new decision.
                boolean present=false;for(NebulaSyncDocument h:heads)if(h.clock.equals(selected.clock)&&h.settings.equals(selected.settings))present=true;
                if(!present){conflict();return;}
            }
            publish(token,selected==null?captured:selected.settings,selected!=null);return;
        }
        if(heads.isEmpty()) {
            if(!state().getString("base_"+user,"").isEmpty()) {
                busy=false;changed(t("Копия удалена из «Избранного». Нажми «Сохранить настройки заново».","The cloud copy was deleted. Choose Save settings again."));return;
            }
            publish(token,captured,false);return;
        }
        if(choices.size()>1){conflict();return;}
        NebulaSyncDocument remote=heads.get(0);
        String localHash=fingerprint(captured), remoteHash=fingerprint(remote.settings), base=state().getString("base_"+user,"");
        if(localHash.equals(remoteHash)){done(captured);return;}
        if(base.equals(localHash)) {apply(remote.settings);done(remote.settings);return;}
        if(base.equals(remoteHash)){publish(token,captured,false);return;}
        conflict();
    }
    private void conflict(){busy=false;changed(t("Настройки отличаются. Выбери версию для всех устройств.","Settings differ. Choose the version to use on your devices."));}
    private void publish(int token,SortedMap<String,Object> values,boolean applyAfter) throws Exception {
        Map<String,Long> clock=NebulaSyncDocument.mergedClock(documents);
        clock.put(device,(clock.containsKey(device)?clock.get(device):0)+1);
        String message=new NebulaSyncDocument(device,clock,values).encode();
        String pendingPayload=state().getString("pending_payload_"+user,null);
        SortedMap<String,Object> published=values;
        if(ownMessage==0 && pendingPayload!=null) {
            NebulaSyncDocument previous=NebulaSyncDocument.parse(pendingPayload);
            if(!device.equals(previous.device))throw new IOException("pending device");
            message=pendingPayload;published=previous.settings;applyAfter=false;
        }
        TLObject request;
        if(ownMessage!=0) {
            TLRPC.TL_messages_editMessage edit=new TLRPC.TL_messages_editMessage();edit.peer=new TLRPC.TL_inputPeerSelf();edit.id=ownMessage;edit.message=message;edit.flags=2048;edit.no_webpage=true;request=edit;
        }else {
            TLRPC.TL_messages_sendMessage send=new TLRPC.TL_messages_sendMessage();send.peer=new TLRPC.TL_inputPeerSelf();send.message=message;send.no_webpage=true;send.silent=true;
            // Persist the random ID before sending, so a lost reply cannot create a duplicate on retry.
            long random=state().getLong("pending_"+user,0);if(random==0){random=Utilities.random.nextLong();if(random==0)random=1;
                if(!state().edit().putLong("pending_"+user,random).putString("pending_payload_"+user,message).commit())throw new IOException("pending");}
            send.random_id=random;request=send;
        }
        final int requestAccount=account;
        final SortedMap<String,Object> finalPublished=published;
        final boolean finalApplyAfter=applyAfter;
        ConnectionsManager.getInstance(account).sendRequest(request,(response,error)->AndroidUtilities.runOnUIThread(()->{
            if(response instanceof TLRPC.Updates)MessagesController.getInstance(requestAccount).processUpdates((TLRPC.Updates)response,false);
            if(!valid(token))return;
            if(error!=null && !"MESSAGE_NOT_MODIFIED".equals(error.text)){fail();return;}
            try {
                state().edit().remove("pending_"+user).remove("pending_payload_"+user).apply();
                if(finalApplyAfter && captured.equals(NebulaSyncDocument.portable(prefs().getAll())))apply(finalPublished);
                done(finalPublished);
            }catch(Exception e){fail();}
        }));
    }
    private void apply(Map<String,Object> values) throws Exception {
        applying=true;
        try {
            SharedPreferences.Editor edit=prefs().edit();
            for(String key:NebulaSettingsSchema.types.keySet())edit.remove(key);
            for(Map.Entry<String,Object> e:values.entrySet()) {
                Object v=e.getValue();if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);
                else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);else edit.putString(e.getKey(),(String)v);
            }
            if(!edit.commit())throw new IOException("preferences");
            NebulaIcons.setPack(NebulaIcons.pack());NebulaTheme.setMaterialYouEnabled(prefs().getBoolean("material_you",true));
            NebulaTheme.applyMaterialYou(ApplicationLoader.applicationContext);Theme.reloadAllResources(ApplicationLoader.applicationContext);
        }finally{applying=false;}
    }
    private void done(Map<String,Object> values) throws Exception {
        state().edit().putString("base_"+user,fingerprint(values)).putLong("time_"+user,System.currentTimeMillis()).apply();
        busy=false;choices.clear();changed(t("Настройки синхронизированы","Settings are up to date"));
        schedule(values.equals(NebulaSyncDocument.portable(prefs().getAll()))?300000:3000);
    }
    private void fail(){busy=false;changed(t("Не удалось синхронизировать. Проверь соединение и формат копии в «Избранном».","Could not sync. Check your connection and the Saved Messages copy."));schedule(300000);}
}
