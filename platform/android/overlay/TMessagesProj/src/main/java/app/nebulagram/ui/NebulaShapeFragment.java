package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Форма: единые скругления, форма фото профиля, хвосты у сообщений.
 *
 * <p>Превью стоит над настройками и перерисовывается на каждом движении
 * ползунка: иначе выбирать скругление приходилось бы выходом с экрана и
 * возвратом обратно.
 */
public final class NebulaShapeFragment extends BaseFragment {
    private LinearLayout content;
    private NebulaShapePreview preview;

    @Override
    public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text("Форма", "Shape"));
        actionBar.setBackgroundColor(theme.surface());
        actionBar.setTitleColor(theme.onSurface());
        actionBar.setItemsColor(theme.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(theme.surface());
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(12), 0, dp(28));
        scroll.addView(content);
        build(context);
        // Без номера раздела: экран ещё не заведён в поиске настроек, а чужой
        // номер увёл бы длинное нажатие на ссылку не туда.
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, scroll);
    }

    private int dp(float value) { return AndroidUtilities.dp(value); }

    private void build(Context context) {
        content.removeAllViews();
        preview = new NebulaShapePreview(context);
        content.addView(preview, new LinearLayout.LayoutParams(-1, -2));

        content.addView(NebulaCard.header(context, text("Единые скругления", "Uniform corners")));
        NebulaCard corners = new NebulaCard(context);
        NebulaExpand details = new NebulaExpand(context, NebulaShape.uniform());
        corners.add(NebulaExtras.toggle(context, R.drawable.msg_customize,
                text("Единые скругления", "Uniform corners"),
                text("Одна форма у карточек, полей и кнопок", "One shape for cards, fields and buttons"),
                NebulaShape.uniform(), enabled -> {
                    NebulaShape.setUniform(enabled);
                    details.expand(enabled);
                    details.invalidate();
                    preview.invalidate();
                }));
        corners.add(details);

        TextView value = new TextView(context);
        value.setTextSize(15);
        value.setTextColor(NebulaTheme.of(context).primary());
        value.setPadding(dp(20), dp(14), dp(20), dp(2));
        details.addView(value);

        SeekBar slider = new SeekBar(context);
        slider.setMax(100);
        slider.setProgress(NebulaShape.roundness());
        slider.setPadding(dp(20), dp(8), dp(20), dp(4));
        details.addView(slider);
        details.addView(stops(context));

        Runnable refresh = () -> {
            value.setText(NebulaShape.roundnessTitle() + "  ·  " + NebulaShape.roundness() + "%");
            preview.invalidate();
        };
        refresh.run();
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) { NebulaShape.setRoundness(progress); refresh.run(); }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        content.addView(corners);

        content.addView(NebulaCard.header(context, text("Шрифт", "Font")));
        NebulaCard font = new NebulaCard(context);
        String[] fonts = {"Telegram", text("Системный", "System")};
        NebulaRow fontRow = new NebulaRow(context).icon(R.drawable.msg_edit)
                .title(text("Шрифт", "Font")).subtitle(NebulaFont.title(), true)
                .trailing(NebulaRow.TRAIL_CHEVRON);
        fontRow.withClick(v -> showDialog(new NebulaDialog.Builder(context).setTitle(text("Шрифт", "Font"))
                .setSelectedIndex(NebulaFont.system() ? 1 : 0)
                .setItems(fonts, (dialog, which) -> {
                    NebulaFont.setSystem(which == 1);
                    fontRow.subtitle(NebulaFont.title(), true);
                    // Кэш начертаний держит старый шрифт, а темы — размеры и
                    // краски, посчитанные по нему. Без обоих сбросов смена
                    // видна лишь на том, что ещё не нарисовано.
                    org.telegram.messenger.AndroidUtilities.nebulaClearTypefaces();
                    org.telegram.ui.ActionBar.Theme.reloadAllResources(context);
                    build(context);
                }).create()));
        font.add(fontRow);
        content.addView(font);

        content.addView(NebulaCard.header(context, text("Контейнеры", "Containers")));
        NebulaCard containers = new NebulaCard(context);
        containers.add(NebulaExtras.toggle(context, R.drawable.msg_list,
                text("Своя карточка у каждой строки", "A card for every row"),
                text("Вместо одной карточки на группу", "Instead of one card per group"),
                NebulaShape.separateRows(), enabled -> {
                    NebulaShape.setSeparateRows(enabled);
                    // Карточки создаются при сборке экрана, поэтому его нужно
                    // собрать заново: иначе выбор виден только при следующем входе.
                    build(context);
                }));
        content.addView(containers);

        content.addView(NebulaCard.header(context, text("Плотность", "Density")));
        NebulaCard density = new NebulaCard(context);
        TextView gapValue = new TextView(context);
        gapValue.setTextSize(15);
        gapValue.setTextColor(NebulaTheme.of(context).primary());
        gapValue.setPadding(dp(20), dp(14), dp(20), dp(2));
        density.add(gapValue);
        SeekBar gap = new SeekBar(context);
        gap.setMax(6);
        gap.setProgress(NebulaShape.sectionGap());
        gap.setPadding(dp(20), dp(8), dp(20), dp(14));
        density.add(gap);
        Runnable gapLabel = () -> gapValue.setText(text("Отступы между секциями", "Space between groups")
                + "  ·  " + NebulaShape.sectionGap());
        gapLabel.run();
        gap.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                NebulaShape.setSectionGap(progress);
                gapLabel.run();
                // Отступ меняет вёрстку уже открытого экрана: перестроить его
                // на месте честнее, чем показать результат только при возврате.
                content.requestLayout();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        content.addView(density);

        content.addView(NebulaCard.header(context, text("Фото профиля", "Profile photo")));
        NebulaCard shapes = new NebulaCard(context);
        shapes.add(shapePicker(context));
        shapes.add(new NebulaRow(context).title(text("Хвосты у сообщений", "Message tails"))
                .subtitle(text("Уголок пузыря у последнего сообщения подряд",
                        "The bubble corner on the last message in a row"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaShape.tails())
                .withClick(v -> NebulaShape.setTails(((NebulaRow) v).toggleChecked())));
        content.addView(shapes);

        content.addView(NebulaFormUi.note(context, text(
                "Форма фото профиля задаётся радиусами углов, поэтому в списке нет срезанных углов — их радиусом не нарисовать. Проценты закругления из «Оформления» остаются главными: форма подчиняется им.",
                "Profile photo shapes are corner radii, so cut corners are not offered — a radius cannot draw them. The corner percentage in Appearance stays in charge; the shape follows it.")));
    }

    /** Подписи ступеней под ползунком — тех же, по которым названа текущая. */
    private View stops(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(22), 0, dp(22), dp(12));
        String[] names = {text("Прямые", "Straight"), text("Мягкие", "Soft"),
                text("Сквиркл", "Squircle"), text("Круглые", "Round")};
        for (int i = 0; i < names.length; i++) {
            TextView label = new TextView(context);
            label.setText(names[i]);
            label.setTextSize(11);
            label.setTextColor(NebulaTheme.of(context).onSurfaceVariant());
            label.setGravity(i == 0 ? Gravity.LEFT : i == names.length - 1 ? Gravity.RIGHT : Gravity.CENTER);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        return row;
    }

    private View shapePicker(Context context) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        for (int shape = 0; shape < NebulaShape.shapeCount(); shape++) {
            final int selected = shape;
            View sample = new ShapeSample(context, shape);
            sample.setContentDescription(NebulaShape.shapeTitle(shape)
                    + (NebulaShape.avatarShape() == shape ? text(", выбрано", ", selected") : ""));
            sample.setOnClickListener(v -> {
                NebulaShape.setAvatarShape(selected);
                build(context);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(58), dp(58));
            params.setMarginEnd(dp(8));
            row.addView(sample, params);
        }
        scroll.addView(row);
        return scroll;
    }

    /** Одна плитка выбора: сама форма плюс рамка, когда она выбрана. */
    private final class ShapeSample extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final Path path = new Path();
        private final float[] radii = new float[8];
        private final int shape;

        ShapeSample(Context context, int shape) {
            super(context);
            this.shape = shape;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            NebulaTheme theme = NebulaTheme.of(getContext());
            boolean active = NebulaShape.avatarShape() == shape;
            if (active) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(2));
                paint.setColor(theme.primary());
                bounds.set(dp(1), dp(1), getWidth() - dp(1), getHeight() - dp(1));
                float frame = NebulaShape.radius(16);
                canvas.drawRoundRect(bounds, frame, frame, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            float side = Math.min(getWidth(), getHeight()) - dp(20);
            float[] factors = NebulaShape.cornerFactors(shape);
            for (int i = 0; i < 4; i++) {
                float corner = side * .5f * factors[i];
                radii[i * 2] = corner;
                radii[i * 2 + 1] = corner;
            }
            float left = (getWidth() - side) / 2f, top = (getHeight() - side) / 2f;
            bounds.set(left, top, left + side, top + side);
            path.reset();
            path.addRoundRect(bounds, radii, Path.Direction.CW);
            paint.setColor(active ? theme.primary() : NebulaTheme.stateLayer(theme.onSurface(), .20f));
            canvas.drawPath(path, paint);
        }
    }
}
