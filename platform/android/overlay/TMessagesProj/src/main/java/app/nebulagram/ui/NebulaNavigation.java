package app.nebulagram.ui;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.LoginActivity;

/** Shared navigation actions for the settings page and tab menus. */
public final class NebulaNavigation {
    private NebulaNavigation() { }

    public static void addAccount(BaseFragment host) {
        if (host.getContext() == null) return;
        if (UserConfig.canAddAccount()) {
            host.presentFragment(new LoginActivity(UserConfig.allocateAccount()));
        }
    }

    /** SpoilersTextView reveals only the phone on tap; the username stays readable. */
    public static CharSequence privateSubtitle(TLRPC.User user) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (user != null && !TextUtils.isEmpty(user.phone)) {
            text.append(PhoneFormat.getInstance().format("+" + user.phone));
            TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
            run.flags = TextStyleSpan.FLAG_STYLE_SPOILER;
            run.start = 0;
            run.end = text.length();
            text.setSpan(new TextStyleSpan(run), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        String username = UserObject.getPublicUsername(user);
        if (!TextUtils.isEmpty(username)) {
            if (text.length() > 0) text.append(" • ");
            text.append("@").append(username);
        }
        return text;
    }
}
