package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.LayoutHelper;

/** Real bundled packs and a live avatar radius sample. */
public final class NebulaDesignFragment extends BaseFragment {
    private final boolean avatars;
    private LinearLayout content;
    public NebulaDesignFragment(boolean avatars) { this.avatars = avatars; }
    @Override public View createView(Context c) {
        NebulaTheme t = NebulaTheme.of(c);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(avatars ? text("Закругление аватарок", "Avatar corners") : text("Наборы иконок", "Icon packs"));
        actionBar.setBackgroundColor(t.surface()); actionBar.setTitleColor(t.onSurface()); actionBar.setItemsColor(t.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(c); scroll.setBackgroundColor(t.surface());
        content = new LinearLayout(c); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(24)); scroll.addView(content);
        if (avatars) buildAvatars(c); else buildPacks(c);
        return fragmentView = scroll;
    }
    private int dp(float v) { return AndroidUtilities.dp(v); }
    private void buildPacks(Context c) {
        content.removeAllViews();
        content.addView(NebulaCard.header(c, text("Базовые наборы", "Base packs")));
        NebulaCard card = new NebulaCard(c);
        String[] names = {text("По умолчанию", "Default"), "iOS Outline", "Solar Icon Set"};
        String[] authors = {"Telegram", "NebulaGram", "Design480 · CC BY 4.0"};
        int[] icons = {R.drawable.msg_discussion, R.drawable.msg_link, R.drawable.menu_reply, R.drawable.msg_gallery};
        for (int pack = 0; pack < names.length; pack++) {
            final int selected = pack;
            FrameLayout row = new FrameLayout(c); row.setMinimumHeight(dp(96));
            LinearLayout labels = new LinearLayout(c); labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(label(c, names[pack], 17, NebulaTheme.of(c).onSurface()));
            labels.addView(label(c, authors[pack], 13, NebulaTheme.of(c).onSurfaceVariant()));
            row.addView(labels, LayoutHelper.createFrame(-1, -2, Gravity.CENTER_VERTICAL, 82, 12, 50, 12));
            for (int i = 0; i < 4; i++) {
                ImageView image = new ImageView(c); image.setImageResource(NebulaIcons.previewResource(icons[i], pack));
                image.setColorFilter(NebulaTheme.of(c).onSurfaceVariant(), PorterDuff.Mode.SRC_IN);
                row.addView(image, LayoutHelper.createFrame(22, 22, Gravity.TOP | Gravity.LEFT, 16 + i % 2 * 28, 22 + i / 2 * 28, 0, 0));
            }
            RadioButton radio = new RadioButton(c); radio.setChecked(NebulaIcons.pack() == pack); radio.setClickable(false);
            radio.setButtonTintList(android.content.res.ColorStateList.valueOf(NebulaTheme.of(c).primary()));
            row.addView(radio, LayoutHelper.createFrame(40, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
            row.setOnClickListener(v -> { NebulaIcons.setPack(selected); Theme.reloadAllResources(c); buildPacks(c); });
            card.add(row);
        }
        content.addView(card);
        content.addView(NebulaMenuFragment.placeholder(c, text("Набор заменяет поддерживаемые значки. Эмодзи, анимации статуса файлов и стикеры сохраняют собственный вид.", "A pack replaces supported icons. Emoji, file status animations and stickers retain their own appearance.")));
    }
    private TextView label(Context c, String value, int size, int color) {
        TextView v = new TextView(c); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private void buildAvatars(Context c) {
        NebulaCard card = new NebulaCard(c);
        TextView value = label(c, "", 16, NebulaTheme.of(c).primary());
        value.setPadding(dp(16), dp(16), dp(16), dp(8)); card.add(value);
        SeekBar slider = new SeekBar(c); slider.setMax(100); slider.setProgress(NebulaAppearance.avatarRound());
        slider.setPadding(dp(20), dp(12), dp(20), dp(12)); card.add(slider);
        View sample = new View(c) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); final RectF rect = new RectF();
            @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), dp(120)); }
            @Override protected void onDraw(Canvas canvas) {
                paint.setColor(NebulaTheme.of(c).onSurfaceVariant()); paint.setAlpha(80);
                rect.set(dp(20), dp(20), dp(96), dp(96)); float r = dp(38) * NebulaAppearance.avatarRound() / 100f;
                canvas.drawRoundRect(rect, r, r, paint);
                for (int i = 0; i < 3; i++) { rect.set(dp(116), dp(28 + i * 23), getWidth() - dp(i == 1 ? 22 : 66), dp(36 + i * 23)); canvas.drawRoundRect(rect, dp(4), dp(4), paint); }
                paint.setAlpha(255); paint.setColor(NebulaTheme.of(c).primary()); canvas.drawCircle(dp(89), dp(87), dp(7), paint);
            }
        };
        Runnable update = () -> { value.setText(text("Квадрат", "Square") + "  ·  " + NebulaAppearance.avatarRound() + "%  ·  " + text("Круг", "Circle")); sample.invalidate(); };
        update.run(); card.add(sample);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int n, boolean user) { if (user) { NebulaAppearance.setAvatarRound(n); update.run(); } }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        card.add(NebulaExtras.toggle(c, R.drawable.nebula_cupertino_person, text("Единое закругление", "Uniform corners"),
                text("Форумы имеют ту же форму, что и чаты", "Forums use the same shape as chats"), NebulaAppearance.uniformAvatars(), NebulaAppearance::setUniformAvatars));
        content.addView(card);
    }
}
