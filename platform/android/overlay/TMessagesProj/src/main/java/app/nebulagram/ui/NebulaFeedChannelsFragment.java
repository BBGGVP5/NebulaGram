package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.ArrayList;

import static app.nebulagram.ui.NebulaText.text;

/**
 * Выбор каналов: для всей ленты (категория 0) или для одной категории.
 * Канал, добавленный в категорию, попадает и в ленту; убранный из ленты
 * пропадает из всех категорий.
 */
public final class NebulaFeedChannelsFragment extends BaseFragment {
    private final long categoryId;

    public NebulaFeedChannelsFragment(int account, long categoryId) {
        currentAccount = account;
        this.categoryId = categoryId;
    }

    @Override
    public View createView(Context context) {
        NebulaFeedModel model = NebulaFeedStore.load(currentAccount);
        NebulaFeedModel.Category category = categoryId == 0 ? null : model.find(categoryId);
        String title = category == null ? text("Каналы ленты", "Feed channels")
                : (category.emoji.isEmpty() ? "" : category.emoji + " ") + category.name;
        NebulaFormUi.bar(this, actionBar, context, title);
        LinearLayout body = NebulaFormUi.column(context);
        ScrollView scroll = NebulaFormUi.scroll(context, body);
        ArrayList<TLRPC.Chat> channels = NebulaFeedStore.subscribedChannels(currentAccount);
        if (channels.isEmpty()) {
            body.addView(NebulaFormUi.note(context, text("Вы пока не подписаны ни на один канал.", "You are not subscribed to any channels yet.")));
            return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, scroll);
        }
        body.addView(NebulaFormUi.note(context, category == null
                ? text("Посты отмеченных каналов собираются в ленту, новые сверху.", "Posts from the channels you check are collected in the feed, newest first.")
                : text("Каналы этой вкладки. Отмеченный канал добавится и в ленту.", "Channels for this tab. A checked channel is added to the feed as well.")));
        NebulaCard card = new NebulaCard(context);
        for (TLRPC.Chat chat : channels) {
            long id = chat.id;
            boolean checked = category == null ? model.channels.contains(id) : category.channels.contains(id);
            String username = ChatObject.getPublicUsername(chat);
            NebulaRow row = new NebulaRow(context).icon(R.drawable.msg_channel).title(chat.title)
                    .subtitle(username == null || username.isEmpty() ? "" : "@" + username, false)
                    .trailing(NebulaRow.TRAIL_SWITCH).checked(checked);
            row.withClick(v -> {
                boolean value = row.toggleChecked();
                // Перечитываем: соседние экраны могли сохранить своё.
                NebulaFeedModel stored = NebulaFeedStore.load(currentAccount);
                NebulaFeedModel.Category target = categoryId == 0 ? null : stored.find(categoryId);
                if (target == null) {
                    stored.setChannel(id, value);
                } else if (value) {
                    target.channels.add(id);
                    stored.setChannel(id, true);
                } else {
                    target.channels.remove(id);
                }
                NebulaFeedStore.save(currentAccount, stored);
            });
            card.add(row);
        }
        body.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, scroll);
    }
}
