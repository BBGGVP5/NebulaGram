package app.nebulagram.ui;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ActionBar.BaseFragment;

/** Explicit provider choice; no fallback that sends media to another service. */
public final class NebulaTranscription {
    private NebulaTranscription() { }
    public static boolean selected(){return NebulaMessagePreferences.enabled("ai_transcription",false);}
    public static void setEnabled(boolean enabled) {
        NebulaMessagePreferences.set("ai_transcription", enabled);
        BaseFragment host = LaunchActivity.getLastFragment();
        if (host instanceof ChatActivity) ((ChatActivity)host).updateVisibleRows();
    }

    public static boolean eligible(MessageObject message) {
        if(message==null||message.messageOwner==null||message.getId()<=0||message.deleted||message.messageOwner.noforwards||message.messageOwner.ttl>0||message.isSecretMedia()
                ||message.isRestrictedMessage||message.isSponsored()||org.telegram.messenger.DialogObject.isEncryptedDialog(message.getDialogId())
                ||!(message.isVoice()||message.isRoundVideo()||message.isVideo())) return false;
        org.telegram.messenger.MessagesController controller=org.telegram.messenger.MessagesController.getInstance(message.currentAccount);
        long peer=message.getDialogId();
        if(peer<0){org.telegram.tgnet.TLRPC.Chat chat=controller.getChat(-peer);return chat!=null&&!chat.noforwards;}
        org.telegram.tgnet.TLRPC.UserFull full=controller.getUserFull(peer);
        return full==null||!(full.noforwards_my_enabled||full.noforwards_peer_enabled);
    }
    public static void openTools(BaseFragment host, MessageObject message) {
        if (!eligible(message) || host == null || host.getCurrentAccount() != message.currentAccount) return;
        if (!selected() || !NebulaAudioPreferences.hasTranscriptionService()) { host.presentFragment(new NebulaAudioSettingsFragment()); return; }
        NebulaMessageToolsFragment.show(host,new NebulaMessageToolsFragment(message).transcribeOnOpen());
    }
    public static boolean open(MessageObject message){
        if(!selected()||message==null||message.messageOwner==null)return false;
        if(!eligible(message))return true;
        BaseFragment host=LaunchActivity.getLastFragment();
        if(!(host instanceof ChatActivity)||host.getCurrentAccount()!=message.currentAccount
                ||((ChatActivity)host).getDialogId()!=message.getDialogId())return true;
        openTools(host, message);
        return true;
    }
}
