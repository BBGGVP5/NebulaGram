package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

/**
 * Живое превью элемента интерфейса прямо над его настройками.
 *
 * <p>Переключатель "панель в стиле iOS" сам по себе ничего не сообщает: чтобы
 * увидеть разницу, приходилось выходить в чат, смотреть, возвращаться. Превью
 * показывает результат на месте и перерисовывается по нажатию.
 *
 * <p>Рисуем фигурами, а не настоящими виджетами: настоящий {@code
 * ChatActivityEnterView} тянет за собой пол-Telegram, живёт только внутри чата
 * и в списке настроек вёл бы себя непредсказуемо. Здесь нужен образ, а не
 * работающая копия.
 */
public class NebulaPreview extends View {

    /** Нижняя панель вкладок. */
    public static final int KIND_TABS = 0;
    /** Шапка чата. */
    public static final int KIND_HEADER = 1;
    /** Поле ввода сообщения. */
    public static final int KIND_COMPOSER = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final int kind;
    private NebulaTheme theme;

    /**
     * Настоящие аватарка и имя аккаунта. Серые плашки вместо них требовали
     * догадываться, что именно окажется в шапке; со своим лицом и своим
     * именем превью читается как снимок будущего экрана, а не как схема.
     */
    private final org.telegram.messenger.ImageReceiver avatar =
            new org.telegram.messenger.ImageReceiver(this);
    private String accountName = "";
    private String accountStatus = "";

    public NebulaPreview(@NonNull Context context, int kind) {
        super(context);
        this.kind = kind;
        theme = NebulaTheme.of(context);
        loadAccount();
    }

    private void loadAccount() {
        try {
            int account = org.telegram.messenger.UserConfig.selectedAccount;
            org.telegram.tgnet.TLRPC.User self = org.telegram.messenger.MessagesController
                    .getInstance(account)
                    .getUser(org.telegram.messenger.UserConfig.getInstance(account).clientUserId);
            if (self == null) {
                return;
            }
            accountName = org.telegram.messenger.UserObject.getUserName(self);
            accountStatus = LocaleController.getString(R.string.Online);
            avatar.setForUserOrChat(self,
                    new org.telegram.ui.Components.AvatarDrawable(self));
        } catch (Throwable e) {
            // Превью — украшение: без аватарки оно всё равно показывает форму.
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatar.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        avatar.onDetachedFromWindow();
        super.onDetachedFromWindow();
    }

    /** Рисует текст, обрезая по ширине: длинное имя не должно лезть за край. */
    private void drawLabel(Canvas canvas, String text, float x, float y,
                           float maxWidth, Paint.Align align, boolean bold, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        paint.setTypeface(bold ? AndroidUtilities.bold() : null);
        paint.setTextAlign(align);
        paint.setColor(color);
        String shown = android.text.TextUtils.ellipsize(text, new android.text.TextPaint(paint),
                maxWidth, android.text.TextUtils.TruncateAt.END).toString();
        canvas.drawText(shown, x, y, paint);
        paint.setTypeface(null);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** Перерисовать после изменения настройки. */
    public void refresh() {
        theme = NebulaTheme.of(getContext());
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int height = AndroidUtilities.dp(kind == KIND_TABS ? 104 : kind == KIND_HEADER ? 92 : 100);
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Превью должно быть тем же фрагментом чата, а не карточкой со
        // случайными геометрическими фигурами. Так сразу видно, что именно
        // включит переключатель поверх реальных обоев.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.surface());
        rect.set(0, 0, getWidth(), getHeight());
        float radius = NebulaTheme.cornerMedium();
        canvas.drawRoundRect(rect, radius, radius, paint);
        if (kind != KIND_TABS) drawWallpaper(canvas);

        switch (kind) {
            case KIND_TABS:
                drawTabs(canvas);
                break;
            case KIND_HEADER:
                drawHeader(canvas);
                break;
            case KIND_COMPOSER:
                drawComposer(canvas);
                break;
            default:
                break;
        }
    }

    // --- нижняя панель ------------------------------------------------------

    private void drawTabs(Canvas canvas) {
        float margin = AndroidUtilities.dp(12);
        float height = AndroidUtilities.dp(56);
        float top = getHeight() - height - AndroidUtilities.dp(10);

        if (!NebulaBottomBar.enabled()) {
            drawSidePanelPreview(canvas);
            return;
        }

        // Рисуем кусочек настоящего списка чатов, чтобы нижняя панель не
        // висела в пустоте и было понятно, что это именно навигация.
        float contentLeft = AndroidUtilities.dp(18);
        paint.setTypeface(AndroidUtilities.bold());
        paint.setTextSize(AndroidUtilities.dp(13));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(theme.onSurface());
        canvas.drawText(LocaleController.getString(R.string.MainTabsChats), contentLeft,
                AndroidUtilities.dp(24), paint);
        paint.setTypeface(null);

        drawPreviewChatRow(canvas, contentLeft, AndroidUtilities.dp(28),
                AndroidUtilities.dp(24), true);

        // Та же плашка и те же подписи, что в реальной нижней панели. На
        // маленьком экране это читается намного лучше, чем отдельные линии.
        drawGlassSurface(canvas, new RectF(margin, top, getWidth() - margin, top + height),
                height / 2f);
        rect.set(margin, top, getWidth() - margin, top + height);

        boolean[] shown = {
                true,
                NebulaBottomBar.tabEnabled(NebulaBottomBar.TAB_CONTACTS),
                NebulaBottomBar.tabEnabled(NebulaBottomBar.TAB_SETTINGS),
                NebulaBottomBar.tabEnabled(NebulaBottomBar.TAB_PROFILE),
        };
        int count = 0;
        for (boolean visible : shown) {
            if (visible) {
                count++;
            }
        }
        float step = (rect.width()) / count;
        float iconY = top + AndroidUtilities.dp(20);
        float labelY = top + AndroidUtilities.dp(44);
        String[] labels = {
                LocaleController.getString(R.string.MainTabsChats),
                LocaleController.getString(R.string.MainTabsContacts),
                LocaleController.getString(R.string.Settings),
                LocaleController.getString(R.string.MainTabsProfile),
        };
        int index = 0;
        for (int i = 0; i < shown.length; i++) {
            if (!shown[i]) {
                continue;
            }
            float centerX = rect.left + step * index + step / 2f;
            // Первая вкладка выбрана: так видно, чем выбранная отличается,
            // а включённые тумблеры ниже сразу меняют набор подписей.
            boolean active = index == 0;
            boolean showLabels = NebulaBottomBar.tabLabels();
            if (active) {
                float pillWidth = Math.min(step - AndroidUtilities.dp(10), AndroidUtilities.dp(56));
                paint.setColor(NebulaTheme.stateLayer(theme.primary(), .22f));
                // Без подписей плашка выделения тоже переезжает к центру:
                // иначе значок стоял бы по центру, а подсветка — над ним.
                float pillTop = showLabels ? top + AndroidUtilities.dp(6)
                        : top + height / 2f - AndroidUtilities.dp(14);
                rect.set(centerX - pillWidth / 2f, pillTop,
                        centerX + pillWidth / 2f, pillTop + AndroidUtilities.dp(28));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(15), AndroidUtilities.dp(15), paint);
            }
            paint.setColor(active ? theme.primary() : theme.onSurfaceVariant());
            // Без подписей значок встаёт по центру плашки — как и в панели.
            drawTabGlyph(canvas, i, centerX, showLabels ? iconY : top + height / 2f);
            if (!showLabels) {
                index++;
                continue;
            }
            paint.setTextSize(AndroidUtilities.dp(8));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(active ? AndroidUtilities.bold() : null);
            paint.setColor(active ? theme.primary()
                    : NebulaTheme.stateLayer(theme.onSurfaceVariant(), .8f));
            canvas.drawText(labels[i], centerX, labelY, paint);
            index++;
        }
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(null);
    }

    private void drawPreviewChatRow(Canvas canvas, float left, float top, float height, boolean unread) {
        float avatar = height / 2f;
        paint.setColor(unread ? NebulaTheme.stateLayer(theme.primary(), .45f)
                : NebulaTheme.stateLayer(theme.onSurfaceVariant(), .34f));
        canvas.drawCircle(left + avatar, top + avatar, avatar, paint);
        float lineLeft = left + height + AndroidUtilities.dp(9);
        paint.setColor(NebulaTheme.stateLayer(theme.onSurface(), .82f));
        rect.set(lineLeft, top + AndroidUtilities.dp(7),
                lineLeft + AndroidUtilities.dp(unread ? 102 : 76), top + AndroidUtilities.dp(12));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), paint);
        paint.setColor(NebulaTheme.stateLayer(theme.onSurfaceVariant(), .55f));
        rect.set(lineLeft, top + AndroidUtilities.dp(19), lineLeft + AndroidUtilities.dp(132),
                top + AndroidUtilities.dp(23));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), paint);
        if (unread) {
            paint.setColor(theme.primary());
            canvas.drawCircle(getWidth() - AndroidUtilities.dp(31), top + avatar,
                    AndroidUtilities.dp(5), paint);
        }
    }

    private void drawSidePanelPreview(Canvas canvas) {
        float left = AndroidUtilities.dp(16);
        float top = AndroidUtilities.dp(14);
        paint.setColor(theme.surfaceContainer());
        rect.set(left, top, getWidth() - left, getHeight() - top);
        canvas.drawRoundRect(rect, AndroidUtilities.dp(18), AndroidUtilities.dp(18), paint);
        paint.setColor(theme.primary());
        paint.setStrokeWidth(AndroidUtilities.dp(2));
        for (int i = 0; i < 3; i++) {
            float y = top + AndroidUtilities.dp(24 + i * 18);
            canvas.drawRoundRect(new RectF(left + AndroidUtilities.dp(16), y,
                    left + AndroidUtilities.dp(34), y + AndroidUtilities.dp(2)),
                    AndroidUtilities.dp(1), AndroidUtilities.dp(1), paint);
        }
        paint.setColor(theme.onSurface());
        paint.setTextSize(AndroidUtilities.dp(12));
        canvas.drawText(LocaleController.getString(R.string.NebulaSidePanelTitle),
                left + AndroidUtilities.dp(48), top + AndroidUtilities.dp(30), paint);
        paint.setColor(theme.onSurfaceVariant());
        paint.setTextSize(AndroidUtilities.dp(9));
        canvas.drawText(LocaleController.getString(R.string.NebulaSidePanelSub),
                left + AndroidUtilities.dp(48), top + AndroidUtilities.dp(48), paint);
    }

    /** Значки вкладок: узнаваемая форма важнее точного повторения иконки. */
    private void drawTabGlyph(Canvas canvas, int tab, float cx, float cy) {
        float size = AndroidUtilities.dp(9);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.6f));
        switch (tab) {
            case 0: // чаты — облачко
                rect.set(cx - size, cy - size * 0.8f, cx + size, cy + size * 0.5f);
                canvas.drawRoundRect(rect, size * 0.5f, size * 0.5f, paint);
                path.reset();
                path.moveTo(cx - size * 0.4f, cy + size * 0.5f);
                path.lineTo(cx - size * 0.15f, cy + size);
                path.lineTo(cx + size * 0.15f, cy + size * 0.5f);
                canvas.drawPath(path, paint);
                break;
            case 1: // контакты — голова и плечи
                canvas.drawCircle(cx, cy - size * 0.35f, size * 0.42f, paint);
                rect.set(cx - size * 0.75f, cy + size * 0.1f, cx + size * 0.75f, cy + size * 1.2f);
                canvas.drawArc(rect, 200, 140, false, paint);
                break;
            case 2: // настройки — шестерёнка, показанная кольцом с зубцами
                canvas.drawCircle(cx, cy, size * 0.45f, paint);
                for (int i = 0; i < 6; i++) {
                    double angle = Math.PI * i / 3;
                    canvas.drawLine(
                            cx + (float) Math.cos(angle) * size * 0.6f,
                            cy + (float) Math.sin(angle) * size * 0.6f,
                            cx + (float) Math.cos(angle) * size * 0.95f,
                            cy + (float) Math.sin(angle) * size * 0.95f, paint);
                }
                break;
            default: // профиль — своя аватарка, как и в настоящей панели
                paint.setStyle(Paint.Style.FILL);
                float r = size * 0.85f;
                avatar.setRoundRadius((int) r);
                avatar.setImageCoords(cx - r, cy - r, r * 2, r * 2);
                avatar.draw(canvas);
                break;
        }
        paint.setStyle(Paint.Style.FILL);
    }

    // --- шапка чата ---------------------------------------------------------

    private void drawHeader(Canvas canvas) {
        boolean styled = NebulaAppearance.chatHeader();
        float margin = AndroidUtilities.dp(14);
        float height = AndroidUtilities.dp(48);
        float top = AndroidUtilities.dp(20);
        float centerY = top + height / 2f;

        if (!styled) {
            paint.setColor(theme.surfaceContainer());
            rect.set(margin, top, getWidth() - margin, top + height);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), paint);
        }

        // Стрелка назад — она есть в обоих вариантах.
        paint.setColor(theme.onSurfaceVariant());
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.8f));
        float arrowX = styled ? margin + height / 2f : margin + AndroidUtilities.dp(18);
        if (styled) drawGlassCircle(canvas, arrowX, centerY, height / 2f);
        paint.setColor(theme.onSurface());
        // Стрелка целиком: остриё плюс древко. Без древка «галочка» читалась
        // как знак «меньше», а не как кнопка возврата.
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        float arrowSize = AndroidUtilities.dp(5);
        path.reset();
        path.moveTo(arrowX + arrowSize * 0.8f, centerY - arrowSize);
        path.lineTo(arrowX - arrowSize * 0.4f, centerY);
        path.lineTo(arrowX + arrowSize * 0.8f, centerY + arrowSize);
        canvas.drawPath(path, paint);
        canvas.drawLine(arrowX - arrowSize * 0.4f, centerY,
                arrowX + arrowSize * 1.7f, centerY, paint);
        paint.setStyle(Paint.Style.FILL);

        // В Liquid Glass имя по центру и аватар отдельно справа. Поэтому
        // видна ключевая разница, которую даёт переключатель в реальном чате.
        float avatarX = styled ? getWidth() - margin - height / 2f : margin + AndroidUtilities.dp(48);
        if (styled) drawGlassCircle(canvas, avatarX, centerY, height / 2f);
        float avatarRadius = AndroidUtilities.dp(styled ? 15 : 14);
        avatar.setRoundRadius((int) avatarRadius);
        avatar.setImageCoords(avatarX - avatarRadius, centerY - avatarRadius,
                avatarRadius * 2, avatarRadius * 2);
        avatar.draw(canvas);

        float textLeft = avatarX + AndroidUtilities.dp(22);
        float titleWidth = AndroidUtilities.dp(84);
        float titleX = styled ? (getWidth() - titleWidth) / 2f : textLeft;
        if (styled) {
            rect.set(titleX - AndroidUtilities.dp(18), top + AndroidUtilities.dp(4),
                    titleX + titleWidth + AndroidUtilities.dp(18), top + height - AndroidUtilities.dp(4));
            drawGlassSurface(canvas, rect, AndroidUtilities.dp(20));
        }
        paint.setTextSize(AndroidUtilities.dp(13));
        drawLabel(canvas, accountName,
                styled ? getWidth() / 2f : textLeft, centerY - AndroidUtilities.dp(2),
                titleWidth + AndroidUtilities.dp(36),
                styled ? Paint.Align.CENTER : Paint.Align.LEFT, true, theme.onSurface());

        paint.setTextSize(AndroidUtilities.dp(10));
        drawLabel(canvas, accountStatus,
                styled ? getWidth() / 2f : textLeft, centerY + AndroidUtilities.dp(10),
                titleWidth + AndroidUtilities.dp(36),
                styled ? Paint.Align.CENTER : Paint.Align.LEFT, false,
                NebulaTheme.stateLayer(theme.onSurfaceVariant(), 0.85f));
    }

    // --- поле ввода ---------------------------------------------------------

    private void drawComposer(Canvas canvas) {
        boolean ios = NebulaAppearance.iosComposer();
        float margin = AndroidUtilities.dp(14);
        float height = AndroidUtilities.dp(44);
        float centerY = getHeight() - AndroidUtilities.dp(34);
        float top = centerY - height / 2f;
        float circle = height / 2f;

        float fieldLeft, fieldRight;
        if (ios) {
            // Кнопки отдельными кружками, поле — самостоятельная пилюля.
            drawGlassCircle(canvas, margin + circle, centerY, circle);
            drawGlassCircle(canvas, getWidth() - margin - circle, centerY, circle);

            fieldLeft = margin + height + AndroidUtilities.dp(12);
            fieldRight = getWidth() - margin - height - AndroidUtilities.dp(12);
            rect.set(fieldLeft, top, fieldRight, top + height);
            drawGlassSurface(canvas, rect, circle);

            paint.setColor(theme.onSurfaceVariant());
            drawClip(canvas, margin + circle, centerY);
            paint.setColor(theme.onSurface());
            drawMic(canvas, getWidth() - margin - circle, centerY);
        } else {
            // Стоковый вид: всё в одной полосе во всю ширину, углы почти прямые.
            paint.setColor(theme.surfaceContainer());
            rect.set(margin, top, getWidth() - margin, top + height);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
            fieldLeft = margin;
            fieldRight = getWidth() - margin;

            paint.setColor(theme.onSurfaceVariant());
            drawClip(canvas, fieldLeft + AndroidUtilities.dp(22), centerY);
            drawMic(canvas, fieldRight - AndroidUtilities.dp(22), centerY);
        }

        // Смайл у правого края поля — он есть в обоих вариантах и помогает
        // узнать в фигуре именно строку сообщения.
        paint.setColor(theme.onSurfaceVariant());
        drawSmile(canvas, fieldRight - AndroidUtilities.dp(ios ? 20 : 48), centerY);

        // Строка подсказки "Сообщение".
        float textLeft = fieldLeft + AndroidUtilities.dp(ios ? 18 : 44);
        paint.setColor(NebulaTheme.stateLayer(theme.onSurfaceVariant(), 0.5f));
        rect.set(textLeft, centerY - AndroidUtilities.dp(3),
                textLeft + AndroidUtilities.dp(58), centerY + AndroidUtilities.dp(3));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), paint);
    }

    private void drawWallpaper(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1));
        paint.setColor(NebulaTheme.stateLayer(theme.primary(), .16f));
        for (int i = 0; i < 6; i++) {
            float x = AndroidUtilities.dp(18 + i * 54);
            float y = AndroidUtilities.dp(16 + (i % 3) * 25);
            rect.set(x, y, x + AndroidUtilities.dp(26), y + AndroidUtilities.dp(18));
            canvas.drawArc(rect, 20 + i * 23, 210, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawGlassCircle(Canvas canvas, float cx, float cy, float radius) {
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        drawGlassSurface(canvas, rect, radius);
    }

    private void drawGlassSurface(Canvas canvas, RectF bounds, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.blendARGB(theme.surfaceContainer(), theme.primary(), .08f));
        canvas.drawRoundRect(bounds, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dpf2(.75f));
        paint.setColor(NebulaTheme.stateLayer(theme.onSurface(), .18f));
        canvas.drawRoundRect(bounds, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** Скрепка: вытянутая петля под наклоном — форма узнаётся и в 14dp. */
    private void drawClip(Canvas canvas, float cx, float cy) {
        float h = AndroidUtilities.dp(7);
        float w = AndroidUtilities.dp(3.4f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.save();
        canvas.rotate(35, cx, cy);
        path.reset();
        path.moveTo(cx + w, cy - h);
        path.lineTo(cx + w, cy + h * 0.45f);
        path.addArc(cx - w, cy + h * 0.05f, cx + w, cy + h * 0.85f, 0, 180);
        path.moveTo(cx - w, cy + h * 0.45f);
        path.lineTo(cx - w, cy - h * 0.55f);
        path.addArc(cx - w, cy - h * 1.1f, cx + w * 0.2f, cy - h * 0.25f, 180, 180);
        canvas.drawPath(path, paint);
        canvas.restore();
        paint.setStyle(Paint.Style.FILL);
    }

    /** Микрофон: капсула, дужка и ножка — без ножки читался как силуэт человека. */
    private void drawMic(Canvas canvas, float cx, float cy) {
        float size = AndroidUtilities.dp(4.6f);
        rect.set(cx - size * 0.62f, cy - size * 1.7f, cx + size * 0.62f, cy + size * 0.25f);
        canvas.drawRoundRect(rect, size * 0.62f, size * 0.62f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.4f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        rect.set(cx - size, cy - size * 0.55f, cx + size, cy + size * 0.9f);
        canvas.drawArc(rect, 15, 150, false, paint);
        canvas.drawLine(cx, cy + size * 0.9f, cx, cy + size * 1.7f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** Смайл: кружок с точками и дугой. */
    private void drawSmile(Canvas canvas, float cx, float cy) {
        float r = AndroidUtilities.dp(6.5f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.4f));
        canvas.drawCircle(cx, cy, r, paint);
        rect.set(cx - r * 0.55f, cy - r * 0.25f, cx + r * 0.55f, cy + r * 0.55f);
        canvas.drawArc(rect, 25, 130, false, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx - r * 0.35f, cy - r * 0.28f, AndroidUtilities.dp(0.9f), paint);
        canvas.drawCircle(cx + r * 0.35f, cy - r * 0.28f, AndroidUtilities.dp(0.9f), paint);
    }

    /** Когда панель выключена, показываем это словами формы, а не пустотой. */
    private void drawCentredNote(Canvas canvas) {
        paint.setColor(NebulaTheme.stateLayer(theme.onSurfaceVariant(), 0.35f));
        float width = AndroidUtilities.dp(120);
        rect.set((getWidth() - width) / 2f, getHeight() / 2f - AndroidUtilities.dp(4),
                (getWidth() + width) / 2f, getHeight() / 2f + AndroidUtilities.dp(4));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
    }
}
