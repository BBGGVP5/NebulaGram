package app.nebulagram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.ProfileActivity;

/**
 * Нижняя панель со вкладками: чаты, контакты, настройки, профиль.
 *
 * <p>В Telegram такой панели нет — навигация живёт в боковой шторке, и до неё
 * тянуться через весь экран. Панель целиком наша: это не переделка чужого
 * экрана, а отдельный вид, который вставляется одной строкой. Поэтому
 * обновление Telegram её не ломает — ломаться там просто нечему.
 *
 * <p>Вкладки можно выключать по одной, а панель — целиком: тот, кому она не
 * нужна, возвращает прежний вид одним переключателем.
 */
public final class NebulaBottomBar {

    private static final String PREFS = "nebulagram";
    private static final String KEY_ENABLED = "bottom_bar";
    private static final String KEY_PREFIX = "bottom_bar_";

    /** Вкладки, которые можно скрыть. Чаты остаются всегда: это сам экран. */
    public static final String TAB_CONTACTS = "contacts";
    public static final String TAB_SETTINGS = "settings";
    public static final String TAB_PROFILE = "profile";

    private NebulaBottomBar() {
    }

    // --- настройки ----------------------------------------------------------

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }

    public static boolean enabled() {
        try {
            return prefs().getBoolean(KEY_ENABLED, true);
        } catch (Throwable e) {
            return false;
        }
    }

    public static void setEnabled(boolean value) {
        prefs().edit().putBoolean(KEY_ENABLED, value).apply();
    }

    public static boolean tabEnabled(String tab) {
        try {
            return prefs().getBoolean(KEY_PREFIX + tab, true);
        } catch (Throwable e) {
            return true;
        }
    }

    public static void setTabEnabled(String tab, boolean value) {
        prefs().edit().putBoolean(KEY_PREFIX + tab, value).apply();
    }

    // --- построение ---------------------------------------------------------

    /**
     * Вставляет панель в экран чатов.
     *
     * @param floatingButton кнопка «написать»: она стоит в том же углу и без
     *                       сдвига оказалась бы под панелью.
     */
    public static void attach(BaseFragment fragment, FrameLayout root, View floatingButton) {
        if (!enabled() || root == null || fragment == null) {
            return;
        }
        Context context = root.getContext();
        View bar = build(context, fragment);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(62));
        params.gravity = Gravity.BOTTOM;
        params.leftMargin = AndroidUtilities.dp(10);
        params.rightMargin = AndroidUtilities.dp(10);
        params.bottomMargin = AndroidUtilities.dp(10);
        root.addView(bar, params);

        liftFloatingButton(floatingButton);
    }

    /** Поднимает кнопку «написать» над панелью, не трогая её собственную анимацию. */
    private static void liftFloatingButton(View button) {
        if (button == null || !(button.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) button.getLayoutParams();
        params.bottomMargin += AndroidUtilities.dp(72);
        button.setLayoutParams(params);
    }

    private static View build(Context context, BaseFragment fragment) {
        NebulaTheme theme = NebulaTheme.of(context);

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(AndroidUtilities.dp(31));
        background.setColor(theme.surfaceContainer());
        bar.setBackground(background);
        bar.setElevation(AndroidUtilities.dp(6));
        // Тень должна повторять скруглённую форму, иначе под панелью видно
        // прямоугольник — на тёмных обоях это особенно заметно.
        bar.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        bar.setClipToOutline(true);

        bar.addView(tab(context, fragment, theme, R.drawable.msg_discussion,
                LocaleController.getString(R.string.NebulaTabChats), true, unread(), null));

        if (tabEnabled(TAB_CONTACTS)) {
            bar.addView(tab(context, fragment, theme, R.drawable.msg_contacts,
                    LocaleController.getString(R.string.NebulaTabContacts), false, 0,
                    () -> fragment.presentFragment(new ContactsActivity(null))));
        }
        if (tabEnabled(TAB_SETTINGS)) {
            bar.addView(tab(context, fragment, theme, R.drawable.msg_settings,
                    LocaleController.getString(R.string.NebulaTabSettings), false, 0,
                    () -> fragment.presentFragment(new ProfileActivity(selfArgs(false)))));
        }
        if (tabEnabled(TAB_PROFILE)) {
            bar.addView(tab(context, fragment, theme, 0,
                    LocaleController.getString(R.string.NebulaTabProfile), false, 0,
                    () -> fragment.presentFragment(new ProfileActivity(selfArgs(true)))));
        }
        return bar;
    }

    /** Настройки и свой профиль — один и тот же экран, отличается только флагом. */
    private static Bundle selfArgs(boolean myProfile) {
        Bundle args = new Bundle();
        args.putLong("user_id", UserConfig.getInstance(UserConfig.selectedAccount).clientUserId);
        if (myProfile) {
            args.putBoolean("my_profile", true);
        }
        return args;
    }

    private static int unread() {
        try {
            return NotificationsController.getInstance(UserConfig.selectedAccount).getTotalUnreadCount();
        } catch (Throwable e) {
            return 0;
        }
    }

    /**
     * Одна вкладка: значок над подписью. Значок нулевой означает аватарку —
     * вкладка профиля показывает лицо, а не силуэт, иначе её не отличить от
     * настроек.
     */
    private static View tab(Context context, BaseFragment fragment, NebulaTheme theme,
                            int icon, String label, boolean selected, int badge,
                            Runnable action) {
        LinearLayout tab = new LinearLayout(context);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        tab.setForeground(new RippleDrawable(
                ColorStateList.valueOf(NebulaTheme.stateLayer(theme.onSurface(), 0.10f)), null, null));
        if (action != null) {
            tab.setOnClickListener(v -> action.run());
        }

        int tint = selected ? theme.primary() : theme.onSurfaceVariant();

        FrameLayout iconHolder = new FrameLayout(context);
        iconHolder.addView(icon == 0 ? avatar(context) : icon(context, icon, tint),
                new FrameLayout.LayoutParams(AndroidUtilities.dp(26), AndroidUtilities.dp(26)));
        if (badge > 0) {
            iconHolder.addView(badge(context, theme, badge));
        }
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        tab.addView(iconHolder, iconParams);

        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        text.setTextColor(tint);
        text.setText(label);
        text.setSingleLine();
        if (selected) {
            text.setTypeface(AndroidUtilities.bold());
        }
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.gravity = Gravity.CENTER_HORIZONTAL;
        textParams.topMargin = AndroidUtilities.dp(3);
        tab.addView(text, textParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tab.setLayoutParams(params);
        return tab;
    }

    private static View icon(Context context, int resource, int tint) {
        ImageView view = new ImageView(context);
        view.setImageResource(resource);
        view.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        view.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return view;
    }

    private static View avatar(Context context) {
        BackupImageView view = new BackupImageView(context);
        view.setRoundRadius(AndroidUtilities.dp(13));
        try {
            int account = UserConfig.selectedAccount;
            TLRPC.User self = MessagesController.getInstance(account)
                    .getUser(UserConfig.getInstance(account).clientUserId);
            if (self != null) {
                view.setForUserOrChat(self, new AvatarDrawable(self));
            }
        } catch (Throwable e) {
            // Аватарка — украшение: без неё вкладка всё равно работает.
        }
        return view;
    }

    private static View badge(Context context, NebulaTheme theme, int count) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        view.setTextColor(theme.onPrimary());
        view.setTypeface(AndroidUtilities.bold());
        view.setGravity(Gravity.CENTER);
        view.setText(count > 99 ? "99+" : String.valueOf(count));
        view.setPadding(AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5), 0);

        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(AndroidUtilities.dp(9));
        pill.setColor(theme.primary());
        view.setBackground(pill);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(18));
        params.gravity = Gravity.TOP | Gravity.END;
        params.topMargin = -AndroidUtilities.dp(4);
        params.rightMargin = -AndroidUtilities.dp(10);
        view.setLayoutParams(params);
        return view;
    }
}
