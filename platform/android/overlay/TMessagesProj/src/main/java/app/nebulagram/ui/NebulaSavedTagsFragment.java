package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.os.Bundle;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ActionBar.BaseFragment;

public final class NebulaSavedTagsFragment extends BaseFragment {
    private final int messageId;
    private final String label;
    private final long user;
    private LinearLayout body, rows;
    private String query="";
    private NebulaSavedTagStore store;
    private int visibleMessages=100;
    public NebulaSavedTagsFragment(int account,int messageId,String label){currentAccount=account;this.messageId=messageId;this.label=label;user=UserConfig.getInstance(account).getClientUserId();}
    @Override public android.view.View createView(Context c){
        NebulaFormUi.bar(this,actionBar,c,text("Метки в Избранном","Saved Messages labels"));body=NebulaFormUi.column(c);
        body.addView(new NebulaSettingsHero(c,"🏷️",text("Метки","Labels"),messageId>0?text("Нажмите на метку, чтобы назначить или снять её.","Tap a label to assign or remove it."):text("Находите сохранённые сообщения по своим меткам.","Find saved messages using your own labels.")));
        if(label==null){EditText search=NebulaFormUi.field(c,text("Поиск меток","Search labels"),1,64);body.addView(search);search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();rebuild();}public void afterTextChanged(android.text.Editable e){}});}
        rows=new LinearLayout(c);rows.setOrientation(LinearLayout.VERTICAL);body.addView(rows);rebuild();
        return fragmentView=NebulaSettingsLayout.wrap(c,actionBar,NebulaFormUi.scroll(c,body),-12);
    }
    @Override public void onResume(){super.onResume();if(body!=null)rebuild();}
    private void rebuild(){
        if(rows==null)return;Context c=body.getContext();rows.removeAllViews();
        try{if(user<=0||user!=UserConfig.getInstance(currentAccount).getClientUserId())throw new IllegalStateException("Account changed");store=NebulaSavedTags.read(currentAccount);}
        catch(Exception error){rows.addView(NebulaFormUi.note(c,text("Не удалось прочитать метки. Данные не перезаписаны.","Could not read labels. Existing data has not been overwritten.")));return;}
        NebulaCard card=new NebulaCard(c);
        if(label!=null){
            for(int id:store.messages(label).subList(0,Math.min(visibleMessages,store.messages(label).size()))){NebulaRow row=new NebulaRow(c).title(text("Сообщение № ","Message #")+id).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->{if(user!=UserConfig.getInstance(currentAccount).getClientUserId()){rebuild();return;}Bundle args=new Bundle();args.putLong("user_id",user);args.putInt("message_id",id);ChatActivity chat=new ChatActivity(args);chat.setCurrentAccount(currentAccount);presentFragment(chat);});row.setOnLongClickListener(v->{confirmRemove(id);return true;});card.add(row);}
        }else{
            for(NebulaSavedTagStore.Label tag:store.labels()){
                if(!query.isEmpty()&&!tag.name.toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT)))continue;
                boolean assigned=store.assigned(messageId,tag.id);
                NebulaRow row=new NebulaRow(c).title(tag.name).subtitle(messageId>0?assigned?text("Назначена","Assigned"):text("Нажмите, чтобы назначить","Tap to assign"):store.messages(tag.id).size()+text(" сообщений"," messages"),false).withClick(v->{
                    if(messageId>0){try{store.toggle(messageId,tag.id);save();}catch(Exception e){report(e);}}
                    else presentFragment(new NebulaSavedTagsFragment(currentAccount,0,tag.id));
                });
                if(messageId>0&&assigned)row.setBackgroundColor(NebulaTheme.stateLayer(NebulaTheme.of(c).primary(),.14f));
                row.setOnLongClickListener(v->{edit(tag);return true;});card.add(row);
            }
        }
        if(card.getChildCount()>0)rows.addView(card);else rows.addView(NebulaFormUi.note(c,text("Пока нет сообщений с метками","No labelled messages yet")));
        if(label!=null&&store.messages(label).size()>visibleMessages)rows.addView(NebulaFormUi.primary(c,text("Показать ещё 100","Show 100 more"),v->{visibleMessages+=100;rebuild();}));
        if(label==null)rows.addView(NebulaFormUi.primary(c,text("Создать метку","Create label"),v->edit(null)));
        rows.addView(NebulaFormUi.note(c,text("Метки хранятся только на этом устройстве, отдельно для каждого аккаунта. Текст и медиа не копируются. Для переименования, удаления метки или снятия связи удерживайте строку.","Labels stay on this device and are separate for each account. Text and media are not copied. Hold a row to rename/delete a label or remove a message link.")));
    }
    private void save()throws Exception{NebulaSavedTags.write(currentAccount,user,store);rebuild();}
    private void edit(NebulaSavedTagStore.Label label){
        EditText field=NebulaFormUi.field(body.getContext(),text("Название метки","Label name"),1,64);if(label!=null)field.setText(label.name);
        NebulaDialog.Builder dialog=new NebulaDialog.Builder(body.getContext(),getResourceProvider()).setTitle(label==null?text("Новая метка","New label"):text("Изменить метку","Edit label")).setView(field).setPositiveButton(text("Сохранить","Save"),(d,w)->{try{if(label==null){String id=store.create(field.getText().toString());if(messageId>0)store.toggle(messageId,id);}else store.rename(label.id,field.getText().toString());save();}catch(Exception e){report(e);}}).setNegativeButton(text("Отмена","Cancel"),null);
        if(label!=null)dialog.setNeutralButton(text("Удалить метку","Delete label"),(d,w)->showDialog(new NebulaDialog.Builder(body.getContext(),getResourceProvider()).setTitle(text("Удалить метку?","Delete label?")).setMessage(text("Сообщения останутся в Избранном.","Messages stay in Saved Messages.")).setPositiveButton(text("Удалить","Delete"),(dd,ww)->{try{store.remove(label.id);save();}catch(Exception e){report(e);}}).setNegativeButton(text("Отмена","Cancel"),null).create()));
        showDialog(dialog.create());
    }
    private void confirmRemove(int id){showDialog(new NebulaDialog.Builder(body.getContext(),getResourceProvider()).setTitle(text("Снять метку с сообщения?","Remove this message label?")).setPositiveButton(text("Снять","Remove"),(d,w)->{try{store.toggle(id,label);save();}catch(Exception e){report(e);}}).setNegativeButton(text("Отмена","Cancel"),null).create());}
    private void report(Exception e){rebuild();Toast.makeText(body.getContext(),text("Не удалось сохранить метку: ","Could not save label: ")+e.getMessage(),Toast.LENGTH_LONG).show();}
}
