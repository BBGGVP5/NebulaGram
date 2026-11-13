package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.ui.ActionBar.*;
import static app.nebulagram.ui.NebulaText.text;

/**
 * Боты аккаунта: шапка раздела и одна карточка, где у каждого бота аватарка,
 * имя и @юзернейм — как в списке аккаунтов. Нажатие открывает его настройки.
 */
public final class NebulaBotsFragment extends BaseFragment {
    private int request;
    private boolean destroyed;
    public NebulaBotsFragment(int account) { currentAccount=account; }
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, text("Мои боты", "My bots"));
        LinearLayout body=NebulaFormUi.column(c);ScrollView scroll=new ScrollView(c);scroll.addView(body);
        NebulaSettingsHero hero=new NebulaSettingsHero(c, R.drawable.msg_bots, text("Мои боты", "My bots"),
                text("Профиль, описание, переводы и управление ботами этого аккаунта.",
                        "Profile, description, translations and management of this account's bots."));
        body.addView(hero);
        LinearLayout list=new LinearLayout(c);list.setOrientation(LinearLayout.VERTICAL);body.addView(list);
        TLRPC.User self=UserConfig.getInstance(currentAccount).getCurrentUser();final long owner=self==null?0:self.id;
        if (self!=null&&self.bot) {
            hero.setStatus(text("Это бот-аккаунт","Bot account"), true);
            list.addView(card(c, java.util.Collections.singletonList(self)));
        } else {
            hero.setStatus(text("Загрузка…","Loading…"));
            request=getConnectionsManager().sendRequest(new TL_bots.getAdminedBots(),(response,error)->AndroidUtilities.runOnUIThread(()->{
                if(destroyed||owner!=UserConfig.getInstance(currentAccount).getClientUserId())return;
                request=0;
                if(!(response instanceof Vector)){hero.setStatus(text("Не удалось загрузить ботов","Could not load bots"));return;}
                java.util.ArrayList<TLRPC.User> bots=new java.util.ArrayList<>();
                for(Object value:((Vector<?>)response).objects)if(value instanceof TLRPC.User&&((TLRPC.User)value).bot)bots.add((TLRPC.User)value);
                getMessagesController().putUsers(bots,false);
                if(bots.isEmpty()){
                    hero.setStatus(text("Ботов нет","No bots"));
                    list.addView(NebulaFormUi.note(c,text("У этого аккаунта нет управляемых ботов. Создайте бота в @BotFather — он появится здесь.",
                            "This account has no managed bots. Create one with @BotFather and it will appear here.")));
                    return;
                }
                hero.setStatus(text("Ботов: ","Bots: ")+bots.size(), true);
                list.addView(NebulaCard.header(c,text("Выберите бота","Choose a bot")));
                list.addView(card(c,bots));
            }));
        }
        return fragmentView=NebulaSettingsLayout.wrap(c,actionBar,scroll);
    }

    private NebulaCard card(Context c, java.util.List<TLRPC.User> bots) {
        NebulaCard card=new NebulaCard(c);
        for(TLRPC.User bot:bots){
            String username=UserObject.getPublicUsername(bot);
            NebulaAccountRow row=new NebulaAccountRow(c,currentAccount,bot,UserObject.getUserName(bot),
                    username==null||username.isEmpty()?"":"@"+username,false,false);
            row.setOnClickListener(v->presentFragment(new NebulaBotSettingsFragment(currentAccount,bot.id)));
            card.add(row);
        }
        return card;
    }

    @Override public void onFragmentDestroy(){destroyed=true;if(request!=0)getConnectionsManager().cancelRequest(request,true);super.onFragmentDestroy();}
}
