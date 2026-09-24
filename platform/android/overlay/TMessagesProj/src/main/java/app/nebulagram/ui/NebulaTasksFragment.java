package app.nebulagram.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.view.View;
import android.widget.*;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import java.util.*;

public final class NebulaTasksFragment extends BaseFragment {
    private String initial;
    private LinearLayout content;
    public NebulaTasksFragment(){this("");}
    public NebulaTasksFragment(String initial){this.initial=initial;}
    private String t(String r,String e){return NebulaText.text(r,e);}
    @Override public View createView(Context c){
        actionBar.setTitle(t("Список дел","Tasks"));actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll=new ScrollView(c);scroll.setBackgroundColor(NebulaTheme.of(c).surface());content=new LinearLayout(c);content.setOrientation(1);content.setPadding(dp(16),dp(12),dp(16),dp(24));scroll.addView(content);build();
        if(!initial.isEmpty())content.post(()->{edit(null,initial);initial="";});
        return fragmentView=NebulaSettingsLayout.wrap(c,actionBar,scroll);
    }
    private int dp(int n){return AndroidUtilities.dp(n);}
    private void error(Exception e){Toast.makeText(getContext(),t("Не удалось сохранить или прочитать задачи","Could not save or load tasks"),Toast.LENGTH_LONG).show();}
    private void build(){
        Context c=getContext();if(c==null)c=content.getContext();content.removeAllViews();
        NebulaButton add=new NebulaButton(c,NebulaButton.STYLE_FILLED);add.setText(t("Новая задача","New task"));add.setOnClickListener(v->edit(null,""));content.addView(add);
        try{JSONArray tasks=NebulaTasks.read(NebulaTasks.user(currentAccount));
            if(tasks.length()==0)content.addView(NebulaCard.header(c,t("Здесь появятся ваши задачи","Your tasks will appear here")));
            for(int i=0;i<tasks.length();i++){
                JSONObject task=tasks.getJSONObject(i);NebulaCard card=new NebulaCard(c);
                String subtitle=task.optString("description");if(task.optLong("remind")>0)subtitle+=" · "+java.text.DateFormat.getDateTimeInstance().format(new Date(task.optLong("remind")));
                card.add(new NebulaRow(c).icon(R.drawable.msg_calendar).title(task.optString("title")).subtitle(subtitle,false).trailing(NebulaRow.TRAIL_SWITCH).checked(task.optBoolean("done")).withClick(v->{try{task.put("done",!task.optBoolean("done"));NebulaTasks.put(NebulaTasks.user(currentAccount),task);build();}catch(Exception e){error(e);}}));
                NebulaRow edit=new NebulaRow(c).title(t("Изменить задачу","Edit task")).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->edit(task,""));card.add(edit);content.addView(card);
            }
        }catch(Exception e){error(e);}
    }
    private void edit(JSONObject previous,String text){
        Context c=getContext();if(c==null)return;
        LinearLayout form=new LinearLayout(c);form.setOrientation(1);form.setPadding(dp(20),0,dp(20),0);
        EditText title=new EditText(c);title.setTextColor(NebulaTheme.of(c).onSurface());title.setHint(t("Название","Title"));title.setText(previous==null?text.split("\n",2)[0]:previous.optString("title"));title.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)});form.addView(title);
        EditText desc=new EditText(c);desc.setTextColor(NebulaTheme.of(c).onSurface());desc.setHint(t("Описание","Description"));desc.setText(previous==null?text:previous.optString("description"));desc.setMaxLines(6);desc.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10000)});form.addView(desc);
        long[] remind={previous==null?0:previous.optLong("remind")};
        NebulaButton date=new NebulaButton(c,NebulaButton.STYLE_TEXT);date.setText(t("Выбрать напоминание","Set reminder"));if(remind[0]>0)date.setText(java.text.DateFormat.getDateTimeInstance().format(new Date(remind[0])));date.setOnClickListener(v->{Calendar cal=Calendar.getInstance();new DatePickerDialog(c,(d,y,m,day)->{cal.set(y,m,day);new TimePickerDialog(c,(time,h,min)->{cal.set(Calendar.HOUR_OF_DAY,h);cal.set(Calendar.MINUTE,min);cal.set(Calendar.SECOND,0);remind[0]=cal.getTimeInMillis();date.setText(java.text.DateFormat.getDateTimeInstance().format(cal.getTime()));},cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),true).show();},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();});form.addView(date);
        NebulaButton clear=new NebulaButton(c,NebulaButton.STYLE_TEXT);clear.setText(t("Без напоминания","No reminder"));clear.setOnClickListener(v->{remind[0]=0;date.setText(t("Выбрать напоминание","Set reminder"));});form.addView(clear);
        TextView hint=new TextView(c);hint.setTextColor(NebulaTheme.of(c).onSurfaceVariant());hint.setText(t("Напоминания зависят от разрешения уведомлений и энергосбережения Android.","Reminders depend on notification permission and Android battery settings."));form.addView(hint);
        AlertDialog dialog=new AlertDialog.Builder(c).setTitle(t("Задача","Task")).setView(form).setPositiveButton(t("Сохранить","Save"),(d,w)->{})
            .setNegativeButton(t("Отмена","Cancel"),null).setNeutralButton(previous==null?null:t("Удалить","Delete"),(d,w)->{try{NebulaTasks.delete(NebulaTasks.user(currentAccount),previous.getString("id"));build();}catch(Exception e){error(e);}}).create();
        showDialog(dialog);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(title.getText().toString().trim().isEmpty()){title.setError(t("Введите название","Enter a title"));return;}
            if(remind[0]>0&&remind[0]<=System.currentTimeMillis()){date.setText(t("Выберите время в будущем","Choose a future time"));return;}
            try{JSONObject task=previous==null?new JSONObject().put("id",UUID.randomUUID().toString()).put("done",false):new JSONObject(previous.toString());
                task.put("title",title.getText().toString().trim()).put("description",desc.getText().toString()).put("remind",remind[0]);NebulaTasks.put(NebulaTasks.user(currentAccount),task);dialog.dismiss();build();
            }catch(Exception e){error(e);}
        });
    }
}
