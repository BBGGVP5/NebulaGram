package app.nebulagram.ui;

import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.BaseFragment;

public final class NebulaSavedTags {
    private NebulaSavedTags() { }
    public static NebulaSavedTagStore read(int account) throws Exception {
        long user=UserConfig.getInstance(account).getClientUserId();
        return NebulaSavedTagStore.decode(ApplicationLoader.applicationContext.getSharedPreferences(NebulaSavedTagStore.scope(user),0).getString("metadata",""));
    }
    public static void write(int account,long expectedUser,NebulaSavedTagStore store) throws Exception {
        if(expectedUser!=UserConfig.getInstance(account).getClientUserId())throw new IllegalStateException("Account changed");
        ApplicationLoader.applicationContext.getSharedPreferences(NebulaSavedTagStore.scope(expectedUser),0).edit().putString("metadata",store.encode()).apply();
    }
    public static boolean eligible(MessageObject message){return message!=null&&message.messageOwner!=null&&message.getId()>0&&!message.deleted&&message.getDialogId()==UserConfig.getInstance(message.currentAccount).getClientUserId();}
    public static void open(BaseFragment host,MessageObject message){if(eligible(message)&&host.getCurrentAccount()==message.currentAccount)host.presentFragment(new NebulaSavedTagsFragment(message.currentAccount,message.getId(),null));}
}
