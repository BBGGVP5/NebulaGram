package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;

import java.util.HashSet;
import java.util.List;

import static app.nebulagram.ui.NebulaText.text;

/**
 * «Отложенные истории»: что, куда и когда опубликует телефон. Нажатие на строку снимает
 * историю с публикации, и она возвращается в обычные черновики.
 */
public final class NebulaScheduledStoriesFragment extends BaseFragment {
    private LinearLayout list;
    private NebulaSettingsHero hero;
    private Context context;

    @Override
    public View createView(Context c) {
        context = c;
        NebulaFormUi.bar(this, actionBar, c, text("Отложенные истории", "Scheduled stories"));
        LinearLayout body = NebulaFormUi.column(c);
        ScrollView scroll = new ScrollView(c);
        scroll.addView(body);
        hero = new NebulaSettingsHero(c, R.drawable.msg_recent, text("Отложенные истории", "Scheduled stories"),
                text("Telegram не умеет откладывать истории на сервере, поэтому их публикует этот телефон. "
                                + "В назначенное время он должен быть включён и в сети; если он был выключен, "
                                + "история выйдет сразу после включения или запуска NebulaGram.",
                        "Telegram cannot schedule stories on its servers, so this phone publishes them. "
                                + "It must be on and online at the chosen time; if it was off, "
                                + "the story goes out as soon as it is back on or NebulaGram starts."));
        body.addView(hero);
        if (!NebulaStorySchedule.exactAlarms()) {
            body.addView(NebulaFormUi.note(c, text(
                    "Точные будильники для приложения выключены в настройках Android, поэтому публикация может сдвинуться на несколько минут.",
                    "Exact alarms are off for this app in Android settings, so publishing may drift by a few minutes.")));
        }
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);
        NebulaStorySchedule.setListener(this::build);
        HashSet<Integer> accounts = new HashSet<>();
        for (NebulaStorySchedulePlan.Item item : NebulaStorySchedule.items()) {
            accounts.add(item.account);
        }
        for (int account : accounts) {
            NebulaStorySchedule.loadDrafts(account, null);
        }
        build();
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, scroll);
    }

    private void build() {
        if (list == null || context == null) {
            return;
        }
        list.removeAllViews();
        List<NebulaStorySchedulePlan.Item> items = NebulaStorySchedule.items();
        if (items.isEmpty()) {
            hero.setStatus(text("Ничего не запланировано", "Nothing scheduled"));
            list.addView(NebulaFormUi.note(context, text(
                    "Чтобы отложить историю, в окне «Опубликовать историю» нажмите «Запланировать публикацию».",
                    "To schedule a story, tap “Schedule for later” in the “Post story” sheet.")));
            return;
        }
        hero.setStatus(text("Ждут публикации: ", "Waiting: ") + items.size(), true);
        list.addView(NebulaCard.header(context, text("Нажмите, чтобы отменить", "Tap to cancel")));
        NebulaCard card = new NebulaCard(context);
        for (NebulaStorySchedulePlan.Item item : items) {
            String subtitle;
            if (item.state == 2) {
                subtitle = text("Не удалось опубликовать: черновик пропал", "Could not publish: the draft is gone");
            } else if (item.publishAt <= System.currentTimeMillis()) {
                subtitle = text("Публикуется…", "Publishing…");
            } else {
                subtitle = NebulaStorySchedule.when(item.publishAt);
            }
            NebulaRow row = new NebulaRow(context).icon(R.drawable.msg_recent).title(target(item))
                    .subtitle(subtitle, false).withClick(v -> confirmCancel(item));
            card.add(row);
        }
        list.addView(card);
    }

    private String target(NebulaStorySchedulePlan.Item item) {
        if (!UserConfig.isValidAccount(item.account)) {
            return text("История", "Story");
        }
        if (item.dialogId == 0 || item.dialogId == UserConfig.getInstance(item.account).getClientUserId()) {
            return text("Мой профиль", "My profile");
        }
        if (item.dialogId < 0) {
            TLRPC.Chat chat = MessagesController.getInstance(item.account).getChat(-item.dialogId);
            if (chat != null && chat.title != null) {
                return chat.title;
            }
        }
        return text("История", "Story");
    }

    private void confirmCancel(NebulaStorySchedulePlan.Item item) {
        showDialog(new NebulaDialog.Builder(context)
                .setMessage(text("Не публиковать эту историю? Она останется в черновиках.",
                        "Don't publish this story? It stays in drafts."))
                .setNegativeButton(text("Оставить", "Keep"), null)
                .setPositiveButton(text("Отменить публикацию", "Cancel publishing"), (d, which) -> NebulaStorySchedule.cancel(item))
                .create());
    }

    @Override
    public void onFragmentDestroy() {
        NebulaStorySchedule.setListener(null);
        super.onFragmentDestroy();
    }
}
