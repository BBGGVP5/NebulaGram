package app.nebulagram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

/**
 * Sheet opened from the badge beside a profile name: large artwork, what the
 * badge means, how it is earned, the other NebulaGram badges, and a way to
 * support the project when the badge comes from donating.
 */
public final class NebulaBadgeSheet {
    private NebulaBadgeSheet() { }

    public static void show(BaseFragment fragment, String kind) {
        Context context = fragment.getParentActivity();
        if (context == null || NebulaBadges.title(kind) == null) return;
        Theme.ResourcesProvider resources = fragment.getResourceProvider();
        int text = Theme.getColor(Theme.key_dialogTextBlack, resources);
        int secondary = Theme.getColor(Theme.key_dialogTextGray3, resources);
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resources);
        int accentText = Theme.getColor(Theme.key_featuredStickers_buttonText, resources);
        int card = Theme.multAlpha(text, .06f);

        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resources);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), dp(16));

        FrameLayout halo = new FrameLayout(context);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Theme.multAlpha(accent, .12f));
        halo.setBackground(circle);
        ImageView artwork = new ImageView(context);
        artwork.setImageResource(icon(kind));
        artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
        halo.addView(artwork, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        LinearLayout.LayoutParams haloParams = new LinearLayout.LayoutParams(dp(104), dp(104));
        haloParams.gravity = Gravity.CENTER_HORIZONTAL;
        haloParams.topMargin = dp(8);
        root.addView(halo, haloParams);
        artwork.setScaleX(.6f);
        artwork.setScaleY(.6f);
        artwork.animate().scaleX(1f).scaleY(1f).setDuration(320)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.6f)).start();

        TextView title = label(context, NebulaBadges.title(kind), 20, text, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, margins(dp(14), 0));
        TextView description = label(context, NebulaBadges.description(kind), 15, secondary, false);
        description.setGravity(Gravity.CENTER);
        root.addView(description, margins(dp(6), 0));

        LinearLayout how = new LinearLayout(context);
        how.setOrientation(LinearLayout.VERTICAL);
        how.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable howBackground = new GradientDrawable();
        howBackground.setColor(card);
        howBackground.setCornerRadius(dp(16));
        how.setBackground(howBackground);
        how.addView(label(context, NebulaText.text("Как получить", "How to get it"), 13, accent, true));
        how.addView(label(context, NebulaBadges.howToGet(kind), 14, text, false), margins(dp(2), 0));
        root.addView(how, margins(dp(18), 0));

        root.addView(label(context, NebulaText.text("Все значки NebulaGram", "All NebulaGram badges"), 13, secondary, true), margins(dp(18), 0));
        LinearLayout all = new LinearLayout(context);
        all.setOrientation(LinearLayout.HORIZONTAL);
        all.setGravity(Gravity.CENTER);
        for (String other : NebulaBadges.kinds()) {
            ImageView item = new ImageView(context);
            item.setImageResource(icon(other));
            item.setScaleType(ImageView.ScaleType.FIT_CENTER);
            item.setPadding(dp(5), dp(5), dp(5), dp(5));
            item.setAlpha(other.equals(kind) ? 1f : .55f);
            item.setContentDescription(NebulaBadges.title(other));
            item.setOnClickListener(v -> {
                NebulaHaptics.tick(v);
                android.widget.Toast.makeText(context, NebulaBadges.title(other), android.widget.Toast.LENGTH_SHORT).show();
            });
            all.addView(item, new LinearLayout.LayoutParams(0, dp(40), 1f));
        }
        root.addView(all, margins(dp(6), 0));

        BottomSheet[] sheet = new BottomSheet[1];
        if (NebulaBadges.donation(kind)) {
            TextView support = button(context, NebulaText.text("Поддержать проект", "Support the project"), accent, accentText);
            support.setOnClickListener(v -> {
                if (sheet[0] != null) sheet[0].dismiss();
                fragment.presentFragment(new NebulaSupportFragment());
            });
            root.addView(support, margins(dp(18), dp(48)));
        }
        TextView close = button(context, NebulaText.text("Понятно", "Got it"),
                NebulaBadges.donation(kind) ? card : accent, NebulaBadges.donation(kind) ? text : accentText);
        close.setOnClickListener(v -> { if (sheet[0] != null) sheet[0].dismiss(); });
        root.addView(close, margins(dp(NebulaBadges.donation(kind) ? 8 : 18), dp(48)));

        builder.setCustomView(root);
        sheet[0] = builder.create();
        fragment.showDialog(sheet[0]);
    }

    private static int icon(String kind) {
        int resource = NebulaBadges.iconResource(kind);
        return resource == 0 ? R.drawable.nebula_badge_star : resource;
    }

    private static TextView label(Context context, CharSequence value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(2), 1f);
        if (bold) view.setTypeface(AndroidUtilities.bold());
        return view;
    }

    private static TextView button(Context context, CharSequence value, int background, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(15);
        view.setTextColor(color);
        view.setTypeface(AndroidUtilities.bold());
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(14));
        view.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Theme.multAlpha(color, .15f)), shape, null));
        return view;
    }

    private static LinearLayout.LayoutParams margins(int top, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height == 0 ? LinearLayout.LayoutParams.WRAP_CONTENT : height);
        params.topMargin = top;
        return params;
    }

}
