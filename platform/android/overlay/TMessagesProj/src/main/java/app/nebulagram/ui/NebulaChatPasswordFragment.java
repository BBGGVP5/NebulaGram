package app.nebulagram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.ui.ActionBar.AlertDialog;
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
    private boolean pin;
    private NebulaRow modeRow;
    public NebulaChatPasswordFragment(int account,long dialog){currentAccount=account;this.dialog=dialog;}
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this,actionBar,c,text("Пароль чата","Chat password"));
        LinearLayout content=NebulaFormUi.column(c);boolean existing=NebulaChatLocks.protectedChat(currentAccount,dialog);
        long owner=NebulaChatLocks.user(currentAccount);pin=existing&&NebulaChatLocks.store().isPin(owner,dialog);
        content.addView(NebulaFormUi.note(c,NebulaLockedChatsFragment.name(currentAccount,dialog)));
        if(existing){oldPassword=field(c,text("Текущий пароль","Current password"));NebulaFormUi.group(content,text("Подтверждение","Verification"),oldPassword);}
        NebulaFormUi.group(content,text("Тип защиты","Lock type"),modeRow=new NebulaRow(c).icon(R.drawable.msg_secret).title(modeTitle()).subtitle(modeDescription(),false).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->chooseMode()));
        password=field(c,passwordHint());NebulaFormUi.group(content,text("Новый пароль","New password"),password);
        repeat=field(c,text("Повтори пароль","Repeat password"));NebulaFormUi.group(content,text("Ещё раз","Confirm"),repeat);updateInputModes();
        content.addView(NebulaFormUi.note(c,text("Пароль или PIN-код защищает чат только на этом устройстве. После выхода чат блокируется снова. Код не синхронизируется и не восстанавливается. На других клиентах Telegram чат доступен как обычно.","A password or PIN protects this chat on this device only. It locks again when you leave the app. The code does not sync and cannot be recovered. The chat remains accessible in other Telegram clients.")));
        status=NebulaFormUi.note(c,"");content.addView(status);
        save=NebulaFormUi.primary(c,text("Сохранить пароль","Save password"),v->change(false));content.addView(save);
        if(existing){remove=new NebulaButton(c,NebulaButton.STYLE_TEXT);remove.setText(text("Убрать пароль","Remove password"));remove.setOnClickListener(v->change(true));content.addView(remove);}
        return fragmentView=NebulaSettingsLayout.wrap(c,actionBar,NebulaFormUi.scroll(c,content));
    }
    private String modeTitle(){return pin?text("PIN-код","PIN code"):text("Пароль","Password");}
    private String modeDescription(){return pin?text("4–12 цифр","4–12 digits"):text("Буквы, цифры и символы","Letters, numbers and symbols");}
    private String passwordHint(){return pin?text("От 4 до 12 цифр","4 to 12 digits"):text("Не менее 6 символов","At least 6 characters");}
    private void chooseMode(){new AlertDialog.Builder(getContext()).setTitle(text("Тип защиты","Lock type")).setItems(new CharSequence[]{text("Пароль","Password"),text("PIN-код","PIN code")},(d,which)->{pin=which==1;modeRow.title(modeTitle()).subtitle(modeDescription(),false);password.setHint(passwordHint());updateInputModes();}).show();}
    private void updateInputModes(){int type=pin?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD;password.setInputType(type);password.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(pin?12:128)});repeat.setInputType(type);repeat.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(pin?12:128)});}
    private EditTextBoldCursor field(Context c,String hint){EditTextBoldCursor e=NebulaFormUi.field(c,hint,1,128);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
    private void change(boolean deleting) {
        boolean formatOk=pin?password.length()>=4&&password.length()<=12&&password.getText().toString().matches("[0-9]+"):password.length()>=6;
        if(!deleting && (!formatOk || !password.getText().toString().equals(repeat.getText().toString()))) {
            status.setText(pin?text("PIN-код должен содержать от 4 до 12 цифр; значения должны совпадать.","PIN must contain 4 to 12 digits and both values must match."):text("Пароль должен содержать не менее 6 символов; значения должны совпадать.","Password must contain at least 6 characters and both values must match."));return;
        }
        final char[] old=oldPassword==null?new char[0]:oldPassword.getText().toString().toCharArray();
        final char[] next=password.getText().toString().toCharArray();final long owner=NebulaChatLocks.user(currentAccount);
        save.setEnabled(false);if(remove!=null)remove.setEnabled(false);status.setText(text("Сохранение…","Saving…"));
        NebulaChatLocks.worker.execute(()->{
            boolean ok=false;
            try{if(deleting)NebulaChatLocks.store().remove(owner,dialog,old);else NebulaChatLocks.store().set(owner,dialog,old,next,pin);ok=true;}
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
