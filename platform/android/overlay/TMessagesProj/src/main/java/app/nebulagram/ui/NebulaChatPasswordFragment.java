package app.nebulagram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EditTextBoldCursor;
import java.util.Arrays;
import static app.nebulagram.ui.NebulaText.text;

public final class NebulaChatPasswordFragment extends BaseFragment {
    private final long dialog;
    private EditTextBoldCursor oldPassword,password,repeat;
    private TextView status;
    private NebulaButton save,remove;
    private boolean destroyed;
    public NebulaChatPasswordFragment(int account,long dialog){currentAccount=account;this.dialog=dialog;}
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this,actionBar,c,text("Пароль чата","Chat password"));
        LinearLayout content=NebulaFormUi.column(c);boolean existing=NebulaChatLocks.protectedChat(currentAccount,dialog);
        content.addView(NebulaFormUi.note(c,NebulaLockedChatsFragment.name(currentAccount,dialog)));
        if(existing){oldPassword=field(c,text("Текущий пароль","Current password"));NebulaFormUi.group(content,text("Подтверждение","Verification"),oldPassword);}
        password=field(c,text("Не менее 6 символов","At least 6 characters"));NebulaFormUi.group(content,text("Новый пароль","New password"),password);
        repeat=field(c,text("Повтори пароль","Repeat password"));NebulaFormUi.group(content,text("Ещё раз","Confirm"),repeat);
        content.addView(NebulaFormUi.note(c,text("Пароль закрывает доступ к чату на этом устройстве. После выхода из приложения чат блокируется снова. Пароль не синхронизируется; восстановления забытого пароля нет. На других клиентах Telegram этот чат доступен как обычно.","The password protects this chat on this device. It locks again when you leave the app. Passwords do not sync and cannot be recovered. The chat remains accessible in other Telegram clients.")));
        status=NebulaFormUi.note(c,"");content.addView(status);
        save=NebulaFormUi.primary(c,text("Сохранить пароль","Save password"),v->change(false));content.addView(save);
        if(existing){remove=new NebulaButton(c,NebulaButton.STYLE_TEXT);remove.setText(text("Убрать пароль","Remove password"));remove.setOnClickListener(v->change(true));content.addView(remove);}
        return fragmentView=NebulaSettingsLayout.wrap(c,actionBar,NebulaFormUi.scroll(c,content));
    }
    private EditTextBoldCursor field(Context c,String hint){EditTextBoldCursor e=NebulaFormUi.field(c,hint,1,128);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
    private void change(boolean deleting) {
        if(!deleting && (password.length()<6 || !password.getText().toString().equals(repeat.getText().toString()))) {
            status.setText(text("Пароли должны совпадать и содержать не менее 6 символов.","Passwords must match and contain at least 6 characters."));return;
        }
        final char[] old=oldPassword==null?new char[0]:oldPassword.getText().toString().toCharArray();
        final char[] next=password.getText().toString().toCharArray();final long owner=NebulaChatLocks.user(currentAccount);
        save.setEnabled(false);if(remove!=null)remove.setEnabled(false);status.setText(text("Сохранение…","Saving…"));
        NebulaChatLocks.worker.execute(()->{
            boolean ok=false;
            try{if(deleting)NebulaChatLocks.store().remove(owner,dialog,old);else NebulaChatLocks.store().set(owner,dialog,old,next);ok=true;}
            catch(Exception ignored){}finally{Arrays.fill(old,'\0');Arrays.fill(next,'\0');}
            final boolean success=ok;
            AndroidUtilities.runOnUIThread(()->{
                if(success){
                    NebulaChatLocks.changed();
                    NotificationsController.getInstance(currentAccount).removeNotificationsForDialog(dialog);
                }
                if(destroyed || owner!=NebulaChatLocks.user(currentAccount))return;
                save.setEnabled(true);if(remove!=null)remove.setEnabled(true);
                if(success){AndroidUtilities.hideKeyboard(password);finishFragment();}
                else status.setText(text("Не удалось сохранить. Проверь текущий пароль; после 5 ошибок нужно подождать 30 секунд.","Could not save. Check the current password; after 5 errors, wait 30 seconds."));
            });
        });
    }
    @Override public void onPause(){super.onPause();if(password!=null){password.setText("");repeat.setText("");if(oldPassword!=null)oldPassword.setText("");}}
    @Override public void onFragmentDestroy(){destroyed=true;super.onFragmentDestroy();}
}
