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

public final class NebulaBotsFragment extends BaseFragment {
    private int request;
    private boolean destroyed;
    public NebulaBotsFragment(int account) { currentAccount=account; }
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, text("Мои боты", "My bots"));
        LinearLayout body=NebulaFormUi.column(c);ScrollView scroll=new ScrollView(c);scroll.addView(body);
        TextView status=new TextView(c);status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));body.addView(status);
        TLRPC.User self=UserConfig.getInstance(currentAccount).getCurrentUser();final long owner=self==null?0:self.id;
        if (self!=null&&self.bot) {
            body.addView(new NebulaRow(c).title(UserObject.getUserName(self)).trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v->presentFragment(new NebulaBotSettingsFragment(currentAccount,owner))));
        } else {
            status.setText(text("Загрузка…","Loading…"));
            request=getConnectionsManager().sendRequest(new TL_bots.getAdminedBots(),(response,error)->AndroidUtilities.runOnUIThread(()->{
                if(destroyed||owner!=UserConfig.getInstance(currentAccount).getClientUserId())return;
                request=0;
                if(!(response instanceof Vector)){status.setText(text("Не удалось загрузить ботов","Could not load bots"));return;}
                java.util.ArrayList<TLRPC.User> bots=new java.util.ArrayList<>();
                for(Object value:((Vector<?>)response).objects)if(value instanceof TLRPC.User&&((TLRPC.User)value).bot)bots.add((TLRPC.User)value);
                getMessagesController().putUsers(bots,false);
                status.setText(bots.isEmpty()?text("У этого аккаунта нет управляемых ботов","This account has no managed bots"):text("Выберите бота","Choose a bot"));
                for(TLRPC.User bot:bots)body.addView(new NebulaRow(c).title(UserObject.getUserName(bot)).trailing(NebulaRow.TRAIL_CHEVRON)
                    .withClick(v->presentFragment(new NebulaBotSettingsFragment(currentAccount,bot.id))));
            }));
        }
        return fragmentView=NebulaSettingsLayout.wrap(c,actionBar,scroll);
    }
    @Override public void onFragmentDestroy(){destroyed=true;if(request!=0)getConnectionsManager().cancelRequest(request,true);super.onFragmentDestroy();}
}
