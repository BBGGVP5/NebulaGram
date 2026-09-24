package app.nebulagram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.DialogsActivity;
import static app.nebulagram.ui.NebulaText.text;

public final class NebulaLockedChatsFragment extends BaseFragment {
    private LinearLayout content;
    public static String name(int account,long dialog) {
        if(DialogObject.isUserDialog(dialog)) {
            TLRPC.User user=MessagesController.getInstance(account).getUser(dialog);
            if(user!=null)return UserObject.getUserName(user);
        }else if(DialogObject.isChatDialog(dialog)) {
            TLRPC.Chat chat=MessagesController.getInstance(account).getChat(-dialog);if(chat!=null)return chat.title;
        }
        return text("Чат ","Chat ")+dialog;
    }
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this,actionBar,c,text("Пароли чатов","Chat passwords"));content=NebulaFormUi.column(c);build(c);
        return fragmentView=NebulaSettingsLayout.wrap(c,actionBar,NebulaFormUi.scroll(c,content),-19);
    }
    private void build(Context c) {
        content.removeAllViews();
        content.addView(NebulaFormUi.note(c,text("У каждого чата свой пароль. Сообщения в уведомлениях скрыты, быстрый ответ недоступен. Защита действует только в этом приложении на этом устройстве.","Each chat has its own password. Notification previews and quick replies are disabled. Protection applies only to this app on this device.")));
        content.addView(NebulaFormUi.primary(c,text("Защитить чат","Protect a chat"),v->choose()));
        try {
            java.util.List<Long> dialogs=NebulaChatLocks.store().dialogs(NebulaChatLocks.user(currentAccount));
            if(dialogs.isEmpty())content.addView(NebulaFormUi.note(c,text("Защищённых чатов пока нет","No protected chats yet")));
            else {
                NebulaCard card=new NebulaCard(c);
                for(long dialog:dialogs)card.add(NebulaFormUi.action(c,R.drawable.msg_secret,name(currentAccount,dialog),text("Изменить или убрать пароль","Change or remove password"),v->presentFragment(new NebulaChatPasswordFragment(currentAccount,dialog))));
                NebulaFormUi.group(content,text("Защищённые чаты","Protected chats"),card);
            }
        }catch(Exception e){content.addView(NebulaFormUi.note(c,text("Не удалось прочитать список защиты","Could not read protected chats")));}
    }
    private void choose() {
        Bundle args=new Bundle();args.putBoolean("onlySelect",true);args.putInt("dialogsType",0);
        DialogsActivity picker=new DialogsActivity(args);picker.setCurrentAccount(currentAccount);
        picker.setDelegate((fragment,dialogs,message,param,notify,scheduleDate,scheduleRepeatPeriod,topicsFragment)->{
            if(dialogs.isEmpty())return true;
            long id=dialogs.get(0).dialogId;
            presentFragment(new NebulaChatPasswordFragment(currentAccount,id),true);return true;
        });
        presentFragment(picker);
    }
    @Override public void onResume(){super.onResume();if(content!=null)build(content.getContext());}
}
