package app.nebulagram.ui;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ActionBar.BaseFragment;

/** Explicit provider choice; no fallback that sends media to another service. */
public final class NebulaTranscription {
    private NebulaTranscription() { }
    public static boolean selected(){return NebulaMessagePreferences.enabled("ai_transcription",false);}
    public static boolean open(MessageObject message){
        if(!selected()||message==null||message.messageOwner==null)return false;
        BaseFragment host=LaunchActivity.getLastFragment();
        if(!(host instanceof ChatActivity)||host.getCurrentAccount()!=message.currentAccount
                ||((ChatActivity)host).getDialogId()!=message.getDialogId())return true;
        NebulaMessageToolsFragment.show(host,new NebulaMessageToolsFragment(message).transcribeOnOpen());
        return true;
    }
}
