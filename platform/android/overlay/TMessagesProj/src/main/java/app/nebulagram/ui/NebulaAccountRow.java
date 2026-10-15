package app.nebulagram.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;

/** Account-owned avatar and a readable name/status hierarchy; no phone-number disclosure. */
public final class NebulaAccountRow extends FrameLayout {
    public NebulaAccountRow(Context context, int account, TLRPC.User user, String title, String detail, boolean current, boolean masked) {
        super(context);
        NebulaTheme theme = NebulaTheme.of(context);
        setMinimumHeight(dp(80));
        setBackgroundColor(current ? NebulaTheme.stateLayer(theme.primary(), .07f) : android.graphics.Color.TRANSPARENT);
        setForeground(new RippleDrawable(ColorStateList.valueOf(NebulaTheme.stateLayer(theme.onSurface(), .08f)), null, null));
        setSelected(current);
        BackupImageView avatar = new BackupImageView(context);
        avatar.setRoundRadius(dp(24));
        avatar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (masked) avatar.setImageResource(R.drawable.msg_secret, theme.onSurfaceVariant());
        else {
            AvatarDrawable placeholder = new AvatarDrawable();
            placeholder.setInfo(account, user);
            avatar.getImageReceiver().setCurrentAccount(account);
            avatar.setForUserOrChat(user, placeholder);
        }
        LayoutParams photo = new LayoutParams(dp(48), dp(48), Gravity.START | Gravity.CENTER_VERTICAL);
        photo.setMarginStart(dp(16)); addView(avatar, photo);
        LinearLayout labels = new LinearLayout(context); labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context); name.setText(title); name.setTextSize(17);
        name.setTypeface(AndroidUtilities.bold()); name.setTextColor(theme.onSurface());
        name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END); name.setGravity(Gravity.START);
        name.setIncludeFontPadding(false); labels.addView(name, new LinearLayout.LayoutParams(-1, -2));
        if (!detail.isEmpty()) {
            TextView status = new TextView(context); status.setText(detail); status.setTextSize(13);
            status.setTextColor(current ? theme.primary() : theme.onSurfaceVariant());
            status.setMaxLines(2); status.setEllipsize(TextUtils.TruncateAt.END); status.setGravity(Gravity.START);
            status.setIncludeFontPadding(false);
            LinearLayout.LayoutParams info = new LinearLayout.LayoutParams(-1, -2); info.topMargin=dp(5); labels.addView(status,info);
        }
        LayoutParams copy = new LayoutParams(-1,-2,Gravity.CENTER_VERTICAL);
        copy.setMarginStart(dp(80)); copy.setMarginEnd(dp(48)); copy.topMargin=copy.bottomMargin=dp(16); addView(labels,copy);
        if (current) {
            ImageView check = new ImageView(context); check.setImageResource(R.drawable.account_check);
            check.setColorFilter(theme.primary(), PorterDuff.Mode.SRC_IN); check.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams mark = new LayoutParams(dp(24),dp(24),Gravity.END|Gravity.CENTER_VERTICAL);mark.setMarginEnd(dp(16));addView(check,mark);
        }
        setLayoutDirection(LocaleController.isRTL ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);
        setContentDescription(title + (detail.isEmpty() ? "" : ", " + detail));
    }
    private static int dp(float value) {return AndroidUtilities.dp(value);}
}
