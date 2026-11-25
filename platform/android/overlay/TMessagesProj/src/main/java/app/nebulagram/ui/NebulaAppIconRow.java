package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LauncherIconController;

/**
 * Строка «Иконка приложения» в «Настройках чатов» вместо нативного выбора
 * иконок Telegram ({@code AppIconsSelectorCell}).
 *
 * <p>Слева — текущая иконка лаунчера NebulaGram, справа — её название и
 * шеврон. Нажатие открывает {@link NebulaIconPickerFragment}; сама строка
 * ничего не переключает. Список и текущая иконка — из
 * {@link LauncherIconController}, как в карусели.
 */
public final class NebulaAppIconRow extends FrameLayout {
    private final NebulaIconArt art;
    private final TextView title, value;
    private final ImageView chevron;
    private final Theme.ResourcesProvider provider;
    private LauncherIconController.LauncherIcon shown;

    public NebulaAppIconRow(Context context, Theme.ResourcesProvider provider) {
        super(context);
        this.provider = provider;
        final boolean rtl = LocaleController.isRTL;

        art = new NebulaIconArt(context);
        art.setCornerFraction(0.28f);
        addView(art, new LayoutParams(AndroidUtilities.dp(36), AndroidUtilities.dp(36),
                (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
        ((LayoutParams) art.getLayoutParams()).leftMargin = rtl ? 0 : AndroidUtilities.dp(18);
        ((LayoutParams) art.getLayoutParams()).rightMargin = rtl ? AndroidUtilities.dp(18) : 0;

        LinearLayout line = new LinearLayout(context);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams lineParams = new LayoutParams(-1, -1);
        lineParams.leftMargin = AndroidUtilities.dp(rtl ? 16 : 70);
        lineParams.rightMargin = AndroidUtilities.dp(rtl ? 70 : 16);
        addView(line, lineParams);

        title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setText(text("Иконка приложения", "App icon"));
        line.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        value = new TextView(context);
        value.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(-2, -2);
        valueParams.leftMargin = AndroidUtilities.dp(8);
        line.addView(value, valueParams);

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.msg_arrowright);
        chevron.setScaleType(ImageView.ScaleType.CENTER);
        chevron.setRotation(rtl ? 180 : 0);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(20), AndroidUtilities.dp(20));
        chevronParams.leftMargin = AndroidUtilities.dp(4);
        line.addView(chevron, chevronParams);
        this.chevron = chevron;

        setBackground(Theme.getSelectorDrawable(false));
        setMinimumHeight(AndroidUtilities.dp(56));
        setContentDescription(title.getText());
        update();
    }

    /** Перечитывает текущую иконку: после карусели строка показывает выбранную. */
    public void update() {
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider));
        value.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText, provider));
        chevron.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, provider));
        LauncherIconController.LauncherIcon current = NebulaBrandPalette.current();
        if (current == shown) return;
        shown = current;
        art.setIcon(current);
        value.setText(NebulaLauncherIconLabels.title(getContext(), current));
        setContentDescription(title.getText() + ", " + value.getText());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(56), MeasureSpec.EXACTLY));
    }
}
