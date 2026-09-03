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

    public static void update(DialogsActivity host, boolean menuVisible) {
        if (host.getActionBar() == null || !host.isMainDialogList()
                || host.isArchive() || host.isCommunity()) {
            return;
        }
        ActionBarMenu menu = host.getActionBar().createMenu();
        ActionBarMenuItem item = menu.getItem(MENU_ID);
        boolean visible = NebulaBottomBar.sidebarEnabled() && menuVisible;
        if (item == null && visible) {
            item = menu.addItem(MENU_ID, R.drawable.menu_intro);
            item.setContentDescription(LocaleController.getString(R.string.NebulaSidePanelTitle));
            item.setOnClickListener(v -> show(host));
        }
        if (item != null) {
            item.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

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
        String name = UserObject.getUserName(MessagesController.getInstance(account).getUser(userId));
        content.addView(new NebulaRow(context).icon(R.drawable.msg_openprofile)
                .title(name).subtitle(LocaleController.getString(R.string.NebulaTabProfile), false)
                .withClick(v -> open(dialog, host, new ProfileActivity(profileArgs))));
        content.addView(NebulaRow.divider(context));
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
        if (host.showDialog(dialog) != null) {
            int width = Math.min(AndroidUtilities.dp(320), context.getResources().getDisplayMetrics().widthPixels - AndroidUtilities.dp(32));
            window.setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT);
        }
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
