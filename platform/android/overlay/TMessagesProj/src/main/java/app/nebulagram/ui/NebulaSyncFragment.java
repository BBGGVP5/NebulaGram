package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import java.text.DateFormat;
import java.util.Date;
import static app.nebulagram.ui.NebulaText.text;

public final class NebulaSyncFragment extends BaseFragment {
    private LinearLayout content;
    private final Runnable refresh=()->{if(content!=null)build(content.getContext());};
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this,actionBar,c,text("Синхронизация настроек","Settings sync"));
        content=NebulaFormUi.column(c);build(c);
        return fragmentView=NebulaSettingsLayout.wrap(c,actionBar,NebulaFormUi.scroll(c,content),-18);
    }
    private void build(Context c) {
        content.removeAllViews();NebulaSettingsSync sync=NebulaSettingsSync.get();boolean enabled=sync.enabled(currentAccount);
        NebulaCard control=new NebulaCard(c);
        control.add(new NebulaRow(c).icon(R.drawable.msg_saved).title(text("Через «Избранное»","Via Saved Messages"))
                .subtitle(text("Для текущего аккаунта Telegram","For the current Telegram account"),false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(enabled).withClick(v->sync.enable(currentAccount,!enabled)));
        NebulaFormUi.group(content,text("Облако Telegram","Telegram cloud"),control);
        content.addView(NebulaFormUi.note(c,text("Темы, папки и оформление синхронизируются, пока приложение открыто. В «Избранном» хранится одна служебная запись для каждого устройства.","Themes, folders and appearance sync while the app is open. Saved Messages stores one service entry per device.")));
        if(enabled) {
            NebulaCard state=new NebulaCard(c);
            long time=sync.lastTime(currentAccount);
            state.add(new NebulaRow(c).icon(R.drawable.msg_info).title(sync.status().isEmpty()?text("Готово к синхронизации","Ready to sync"):sync.status())
                    .subtitle(time==0?text("Первая синхронизация ещё не завершена","First sync has not completed yet"):DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(time)),false));
            NebulaRow now=NebulaFormUi.action(c,R.drawable.msg_retry,text("Синхронизировать сейчас","Sync now"),null,v->sync.sync(-1));
            now.setEnabled(!sync.busy());now.setAlpha(sync.busy()?.5f:1f);state.add(now);
            NebulaFormUi.group(content,text("Состояние","Status"),state);
            if(!sync.choices().isEmpty()) {
                NebulaCard choices=new NebulaCard(c);
                choices.add(NebulaFormUi.action(c,R.drawable.msg_settings,text("Использовать настройки этого устройства","Use this device's settings"),null,v->sync.sync(0)));
                int index=0;
                for(NebulaSyncDocument d:sync.choices()) {
                    final int choice=++index;
                    choices.add(NebulaFormUi.action(c,R.drawable.msg_download,text("Версия из облака ","Cloud version ")+index,
                            text("Устройство ","Device ")+d.device.substring(0,8)+" · "+d.settings.size()+text(" настроек"," settings"),v->sync.sync(choice)));
                }
                NebulaFormUi.group(content,text("Выбери версию","Choose a version"),choices);
            }
            NebulaCard recover=new NebulaCard(c);
            recover.add(NebulaFormUi.action(c,R.drawable.msg_saved,text("Сохранить настройки заново","Save settings again"),
                    text("Если служебная копия была удалена","If the service copy was deleted"),v->sync.sync(0)));
            NebulaFormUi.group(content,text("Восстановление","Recovery"),recover);
        }
        content.addView(NebulaFormUi.note(c,text("Ключи ИИ, подписки, пароли чатов и локальные задачи остаются на устройстве. Выключение синхронизации сохраняет копии в «Избранном» — их можно удалить вручную.","AI keys, subscriptions, chat passwords and local tasks stay on the device. Turning sync off keeps the Saved Messages copies; you can delete them manually.")));
    }
    @Override public void onResume(){super.onResume();NebulaSettingsSync.get().start(currentAccount);NebulaSettingsSync.get().observe(refresh);refresh.run();}
    @Override public void onPause(){NebulaSettingsSync.get().unobserve(refresh);super.onPause();}
    @Override public void onFragmentDestroy(){NebulaSettingsSync.get().unobserve(refresh);super.onFragmentDestroy();}
}
