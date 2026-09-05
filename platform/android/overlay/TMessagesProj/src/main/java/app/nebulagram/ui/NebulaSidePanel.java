package app.nebulagram.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;

/** Optional side navigation; the pinned upstream no longer contains a drawer. */
public final class NebulaSidePanel {
    private static final int MENU_ID = 0x4e42;

    private NebulaSidePanel() { }

    /**
     * Панель открывается жестом, а не кнопкой.
     *
     * <p>Кнопка занимала место в шапке ради того, что и так делается движением
     * пальца: смахнуть слева направо на первой вкладке. Прежняя кнопка при
     * обновлении убирается, чтобы не осталась висеть у тех, кто уже её видел.
     */
    public static void update(DialogsActivity host, boolean menuVisible) {
        if (host.getActionBar() == null || !host.isMainDialogList()
                || host.isArchive() || host.isCommunity()) {
            return;
        }
        ActionBarMenuItem item = host.getActionBar().createMenu().getItem(MENU_ID);
        if (item != null) {
            item.setVisibility(View.GONE);
        }
    }

    /**
     * Открывает панель по свайпу слева направо с первой вкладки.
     *
     * @return true, если панель открыта и жест дальше передавать не нужно
     */
    public static boolean openOnSwipe(BaseFragment host) {
        if (host == null || !NebulaBottomBar.sidebarEnabled() || host.getParentActivity() == null) {
            return false;
        }
        if (showing) {
            return true;
        }
        show(host);
        return true;
    }

    private static boolean showing;

    private static void show(BaseFragment host) {
        Context context = host.getParentActivity();
        if (context == null) {
            return;
        }
        NebulaTheme theme = NebulaTheme.of(context);
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(16),
                AndroidUtilities.dp(8), AndroidUtilities.dp(24));
        content.setBackgroundColor(theme.surfaceContainer());

        int account = host.getCurrentAccount();
        long userId = UserConfig.getInstance(account).getClientUserId();
        Bundle profileArgs = new Bundle();
        profileArgs.putLong("user_id", userId);
        profileArgs.putBoolean("my_profile", true);
        org.telegram.tgnet.TLRPC.User self =
                MessagesController.getInstance(account).getUser(userId);
        content.addView(header(context, theme, self,
                v -> open(dialog, host, new ProfileActivity(profileArgs))));
        add(content, dialog, host, R.drawable.msg_discussion, R.string.NebulaTabChats, null);
        add(content, dialog, host, R.drawable.msg_contacts, R.string.NebulaTabContacts,
                new ContactsActivity(null));
        Bundle savedArgs = new Bundle();
        savedArgs.putLong("user_id", userId);
        add(content, dialog, host, R.drawable.msg_saved, R.string.SavedMessages,
                new ChatActivity(savedArgs));
        add(content, dialog, host, R.drawable.msg_settings, R.string.NebulaTabSettings,
                new SettingsActivity(new Bundle()));
        add(content, dialog, host, R.drawable.msg_secret, R.string.NebulaLinkName,
                new NebulaMenuFragment());
        add(content, dialog, host, R.drawable.msg_customize, R.string.NebulaSettings,
                new NebulaSettingsFragment());

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(content);
        dialog.setContentView(scroll);
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(theme.surfaceContainer()));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.45f);
        window.setGravity(Gravity.START | Gravity.TOP);
        window.setWindowAnimations(R.style.NebulaDrawerAnimation);
        showing = true;
        dialog.setOnDismissListener(d -> showing = false);
        if (host.showDialog(dialog) != null) {
            int width = Math.min(AndroidUtilities.dp(320), context.getResources().getDisplayMetrics().widthPixels - AndroidUtilities.dp(32));
            window.setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT);
        } else {
            showing = false;
        }
    }

    /**
     * Шапка со своей аватаркой, именем и номером — то, с чего начиналась
     * шторка в старом Telegram. Без неё панель читается как безымянный список
     * ссылок: непонятно, к какому аккаунту он относится.
     */
    private static View header(Context context, NebulaTheme theme,
                               org.telegram.tgnet.TLRPC.User self, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(18),
                AndroidUtilities.dp(16), AndroidUtilities.dp(18));
        android.graphics.drawable.GradientDrawable background =
                new android.graphics.drawable.GradientDrawable();
        background.setCornerRadius(NebulaTheme.cornerMedium());
        background.setColor(NebulaTheme.stateLayer(theme.primary(), 0.16f));
        box.setBackground(background);
        box.setOnClickListener(click);

        org.telegram.ui.Components.BackupImageView avatar =
                new org.telegram.ui.Components.BackupImageView(context);
        avatar.setRoundRadius(AndroidUtilities.dp(30));
        if (self != null) {
            avatar.setForUserOrChat(self, new org.telegram.ui.Components.AvatarDrawable(self));
        }
        box.addView(avatar, new LinearLayout.LayoutParams(
                AndroidUtilities.dp(60), AndroidUtilities.dp(60)));

        android.widget.TextView name = new android.widget.TextView(context);
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(theme.onSurface());
        name.setText(self == null ? "" : UserObject.getUserName(self));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = AndroidUtilities.dp(12);
        box.addView(name, nameParams);

        android.widget.TextView phone = new org.telegram.ui.Components.spoilers.SpoilersTextView(context);
        phone.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
        phone.setTextColor(theme.onSurfaceVariant());
        phone.setText(NebulaNavigation.privateSubtitle(self));
        LinearLayout.LayoutParams phoneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        phoneParams.topMargin = AndroidUtilities.dp(2);
        phoneParams.bottomMargin = AndroidUtilities.dp(2);
        box.addView(phone, phoneParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = AndroidUtilities.dp(8);
        box.setLayoutParams(params);
        return box;
    }

    private static void add(LinearLayout content, Dialog dialog, BaseFragment host,
                            int icon, int title, BaseFragment destination) {
        content.addView(new NebulaRow(content.getContext()).icon(icon)
                .title(LocaleController.getString(title))
                .withClick(v -> open(dialog, host, destination)));
    }

    private static void open(Dialog dialog, BaseFragment host, BaseFragment destination) {
        dialog.dismiss();
        if (destination != null) {
            destination.setCurrentAccount(host.getCurrentAccount());
            host.presentFragment(destination);
        }
    }
}
