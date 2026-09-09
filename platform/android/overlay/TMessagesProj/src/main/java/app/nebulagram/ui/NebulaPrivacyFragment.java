package app.nebulagram.ui;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

public final class NebulaPrivacyFragment extends BaseFragment {
    private LinearLayout content;
    private long owner;
    private int generation;
    private String text(String ru,String en) {return NebulaText.text(ru,en);}
    @Override public android.view.View createView(Context context) {
        owner=NebulaDeletedArchive.owner(currentAccount);
        actionBar.setTitle(text("Конфиденциальность","Privacy"));
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll=new ScrollView(context); content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16),AndroidUtilities.dp(12),AndroidUtilities.dp(16),AndroidUtilities.dp(24));
        scroll.setBackgroundColor(NebulaTheme.of(context).surface());scroll.addView(content);
        fragmentView=scroll; rebuild();return scroll;
    }
    private NebulaRow row(String title) {return new NebulaRow(content.getContext()).title(title);}
    private void note(String text) {
        TextView label=new TextView(content.getContext());label.setText(text);label.setTextSize(14);
        label.setTextColor(NebulaTheme.of(content.getContext()).onSurfaceVariant());
        label.setPadding(12,16,12,16);content.addView(label);
    }
    private void rebuild() {
        if(content==null)return; int version=++generation;content.removeAllViews();
        content.addView(row(text("Сохранять удалённые сообщения","Save deleted messages")).trailing(NebulaRow.TRAIL_SWITCH)
            .checked(NebulaDeletedArchive.enabled(owner)).withClick(v->{
                if(NebulaDeletedArchive.enabled(owner)){NebulaDeletedArchive.setEnabled(owner,false);rebuild();}
                else showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text("Включить локальный архив?","Enable local archive?"))
                    .setMessage(text("Полученные сообщения останутся на своём месте в чате с отметкой удаления. Их содержимое и вложения остаются в обычном локальном кэше Telegram; на сервер ничего не отправляется. Секретные и исчезающие сообщения требуют отдельных переключателей. Защита от копирования сохраняется.","Received messages stay in place with a deletion marker. Content and attachments remain in Telegram’s normal local cache; nothing is sent to the server. Secret and expiring messages require separate switches. Copy protection is respected."))
                    .setNegativeButton(text("Отмена","Cancel"),null).setPositiveButton(text("Включить","Enable"),(d,w)->{NebulaDeletedArchive.setEnabled(owner,true);rebuild();}).create());
            }));
        extraToggle(true);
        extraToggle(false);
        content.addView(row(text("Значок удалённого сообщения: ","Deleted message icon: ")+NebulaDeletedArchive.icon()).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->icons()));
        content.addView(row(text("Очистить кэш удалённых сообщений","Clear retained-message cache")).withClick(v->confirmClear(this,currentAccount,0,this::rebuild)));
        note(text("До 500 сообщений, срок показа — 7 дней; просроченное очищается при следующем обращении к архиву. Текст и уже полученные вложения. Медиа может удаляться стандартной очисткой кэша; недоступные файлы восстановить нельзя. Фоновое сохранение возможно лишь при получении приложением события удаления. Выключение не удаляет существующий архив. Клиент собеседника не определяется.","Up to 500 messages, shown for 7 days; expired entries are removed on the next archive access. Text and received attachments. Standard cache eviction can remove media; unavailable files cannot be recovered. Background capture requires the app to receive the deletion update. Disabling does not erase existing entries. Peer clients are not detected."));
        Utilities.globalQueue.postRunnable(()->{
            try {JSONArray entries=NebulaDeletedArchive.entries(owner);AndroidUtilities.runOnUIThread(()->{
                if(content==null||version!=generation)return;
                if(NebulaDeletedArchive.hasError(owner))note(text("Часть сообщений не удалось сохранить. Архив не сброшен.","Some messages could not be saved. The archive was not reset."));
                if(entries.length()==0)note(text("Архив пока пуст","The archive is empty"));
                for(int i=0;i<Math.min(50,entries.length());i++) {JSONObject entry=entries.optJSONObject(i);if(entry==null)continue;
                    TextView label=new TextView(content.getContext());
                    label.setText(NebulaDeletedArchive.icon()+" "+text("Чат ","Chat ")+entry.optLong("peer")+"\n"+
                        java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(entry.optLong("deletedAt")))+"\n\n"+entry.optString("text"));
                    label.setTextSize(16);label.setTextColor(NebulaTheme.of(content.getContext()).onSurfaceVariant());
                    label.setTextIsSelectable(true);label.setPadding(20,20,20,20);
                    android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
                    background.setColor(NebulaTheme.stateLayer(NebulaTheme.of(content.getContext()).onSurface(),0.06f));background.setCornerRadius(AndroidUtilities.dp(16));label.setBackground(background);
                    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=AndroidUtilities.dp(8);content.addView(label,params);
                }
            });}catch(Exception e){error();}
        });
    }
    private void extraToggle(boolean secret) {
        boolean selected=secret?NebulaDeletedArchive.saveSecret(owner):NebulaDeletedArchive.saveExpiring(owner);
        content.addView(row(secret?text("Сохранять в секретных чатах","Retain in secret chats"):text("Сохранять исчезающие сообщения","Retain expiring messages")).trailing(NebulaRow.TRAIL_SWITCH).checked(selected).withClick(v->{
            if(selected){NebulaDeletedArchive.setExtra(owner,secret,false);rebuild();return;}
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text("Оставлять локальную копию?","Keep a local copy?"))
                .setMessage(text("При включённом сохранении копия останется после удаления или таймера. Это меняет ожидаемое поведение секретных и исчезающих сообщений. Работает только с уже полученным содержимым на этом устройстве.","When retention is enabled, a local copy remains after deletion or expiry. This changes the expected behavior of secret and expiring messages. Only content already received on this device can be kept."))
                .setNegativeButton(text("Отмена","Cancel"),null).setPositiveButton(text("Включить","Enable"),(d,w)->{NebulaDeletedArchive.setExtra(owner,secret,true);rebuild();}).create());
        }));
    }
    public static void confirmClear(BaseFragment fragment, int account, long peer, Runnable done) {
        if(fragment.getParentActivity()==null)return;
        fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity())
            .setTitle(NebulaText.text(peer==0?"Очистить все сохранённые удалённые сообщения?":"Очистить удалённые сообщения этого чата?",peer==0?"Clear all retained messages?":"Clear retained messages in this chat?"))
            .setMessage(NebulaText.text("Только локальные сохранённые копии. Обычная переписка и общий медиакэш не удаляются.","Only locally retained copies. Ordinary history and shared media cache are not deleted."))
            .setNegativeButton(NebulaText.text("Отмена","Cancel"),null)
            .setPositiveButton(NebulaText.text("Очистить","Clear"),(d,w)->NebulaDeletedArchive.clearAsync(account,peer,()->{if(done!=null)done.run();},()->{
                if(fragment.getParentActivity()!=null)fragment.showDialog(new AlertDialog.Builder(fragment.getParentActivity()).setMessage(NebulaText.text("Не удалось очистить сохранённые сообщения.","Could not clear retained messages.")).setPositiveButton("OK",null).create());
            })).create());
    }
    private void icons() {
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text("Значок удалённого сообщения","Deleted message icon"))
            .setItems(new CharSequence[]{"🗑","✕","◌",text("Свой символ / эмодзи","Custom symbol / emoji")},(dialog,which)->{
                if(which<3){NebulaDeletedArchive.setIcon(new String[]{"🗑","✕","◌"}[which]);rebuild();return;}
                EditText input=new EditText(getParentActivity());input.setSingleLine(true);input.setText(NebulaDeletedArchive.icon());input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text("Свой значок","Custom icon")).setMessage(text("До четырёх символов или эмодзи. Значок показывается вместо слова «Удалено».","Up to four symbols or emoji. The icon replaces the word Deleted.")).setView(input)
                    .setNegativeButton(text("Отмена","Cancel"),null).setPositiveButton(text("Сохранить","Save"),(d,w)->{NebulaDeletedArchive.setIcon(input.getText().toString());rebuild();}).create());
            }).create());
    }
    private void error(){AndroidUtilities.runOnUIThread(()->{if(content!=null)note(text("Не удалось прочитать или изменить архив. Данные не сброшены.","Could not read or change the archive. Data was not reset."));});}
    @Override public void onFragmentDestroy(){generation++;content=null;super.onFragmentDestroy();}
}
