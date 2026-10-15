package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import org.telegram.messenger.*;
import java.util.Map;
import java.util.WeakHashMap;

/** Visual chat-row masking only. Not a lock or screen-recording permission. */
public final class NebulaPresentationPrivacy {
    private NebulaPresentationPrivacy() {}
    public static boolean enabled() { return NebulaFeatureSettings.enabled("mask_chat_list"); }
    public static void setEnabled(boolean enabled) {
        NebulaFeatureSettings.set("mask_chat_list", enabled);
        for (int account : UserConfig.getAccountIds()) {
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.storiesUpdated);
        }
    }
    public static String label(boolean archive) { return archive ? NebulaText.text("Архив", "Archive") : NebulaText.text("Чат скрыт", "Chat hidden"); }
    public static String detail() { return NebulaText.text("Предпросмотр скрыт", "Preview hidden"); }
    private static final class Cache {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG), detail = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        int width = -1; float scale, density; String sourceTitle, sourceDetail, fittedTitle, fittedDetail;
    }
    // Values never reference their weak keys. No View/Activity is retained.
    private static final Map<View, Cache> cache = new WeakHashMap<>();
    public static void draw(Canvas canvas, View view, boolean archive) {
        draw(canvas, view, archive, 0);
    }
    public static void draw(Canvas canvas, View view, boolean archive, float corner) {
        Cache value = cache.get(view); if (value == null) { value = new Cache(); cache.put(view, value); }
        NebulaTheme theme = NebulaTheme.of(view.getContext());
        value.fill.setColor(theme.surface() | 0xff000000);
        if (corner > 0) canvas.drawRoundRect(0, 0, view.getWidth(), view.getHeight(), corner, corner, value.fill);
        else canvas.drawRect(0, 0, view.getWidth(), view.getHeight(), value.fill);
        float density = view.getResources().getDisplayMetrics().density, scale = view.getResources().getDisplayMetrics().scaledDensity;
        float avatar = Math.min(26 * density, Math.max(0, view.getHeight() / 2f - 8 * density));
        float center = LocaleController.isRTL ? view.getWidth() - 38 * density : 38 * density;
        value.fill.setColor(theme.onSurfaceVariant() & 0x00ffffff | 0x18000000); canvas.drawCircle(center, view.getHeight() / 2f, avatar, value.fill);
        String title = label(archive), detail = detail();
        float available = Math.max(0, view.getWidth() - 96 * density);
        value.title.setColor(theme.onSurface()); value.detail.setColor(theme.onSurfaceVariant());
        if (value.width != view.getWidth() || value.scale != scale || value.density != density || !title.equals(value.sourceTitle) || !detail.equals(value.sourceDetail)) {
            value.title.setTextSize(Math.min(16 * scale, 24 * density)); value.detail.setTextSize(Math.min(13 * scale, 19 * density));
            value.fittedTitle = TextUtils.ellipsize(title, value.title, available, TextUtils.TruncateAt.END).toString();
            value.fittedDetail = TextUtils.ellipsize(detail, value.detail, available, TextUtils.TruncateAt.END).toString();
            value.width = view.getWidth(); value.scale = scale; value.density = density; value.sourceTitle = title; value.sourceDetail = detail;
        }
        value.title.setTextAlign(LocaleController.isRTL ? Paint.Align.RIGHT : Paint.Align.LEFT);
        value.detail.setTextAlign(LocaleController.isRTL ? Paint.Align.RIGHT : Paint.Align.LEFT);
        float x = LocaleController.isRTL ? view.getWidth() - 78 * density : 78 * density;
        canvas.drawText(value.fittedTitle, x, view.getHeight() / 2f - 2 * density, value.title);
        canvas.drawText(value.fittedDetail, x, view.getHeight() / 2f + 19 * density, value.detail);
    }
}
