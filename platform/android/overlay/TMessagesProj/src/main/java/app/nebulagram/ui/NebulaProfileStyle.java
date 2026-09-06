package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.AboutLinkCell;
import org.telegram.ui.Cells.CollapseTextCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Business.ProfileHoursCell;
import org.telegram.ui.Business.ProfileLocationCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.ProfileActionsView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SectionsScrollView;
import org.telegram.ui.FiltersSetupActivity;

import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Shared geometry for profile information, members and chat settings.
 *
 * <p>The native section renderer still draws the surfaces with its own
 * resources provider, clips pressed states, and tracks animated rows. No
 * adapter, avatar, role label or permission-controlled action is replaced.
 */
public final class NebulaProfileStyle {
    private NebulaProfileStyle() { }
    private static final WeakHashMap<RecyclerListView, Boolean> styledLists = new WeakHashMap<>();

    public static void sections(RecyclerListView list, Theme.ResourcesProvider provider) {
        if (list == null) return;
        final boolean enabled = NebulaAppearance.profileStyle();
        if (!enabled) {
            list.setSections();
            return;
        }
        final NebulaProfileArt.Surface surface = new NebulaProfileArt.Surface(list.getContext(), provider);
        // These are the native section boundaries. Special rows and shared
        // media retain the tags and grouping chosen by their own adapters.
        list.setSections(view -> !(view instanceof TextInfoPrivacyCell ||
                        view instanceof ShadowSectionCell ||
                        view instanceof FiltersSetupActivity.HintInnerCell ||
                        view instanceof GraySectionCell || view instanceof CollapseTextCell) &&
                        !Objects.equals(view.getTag(), RecyclerListView.TAG_NOT_SECTION),
                AndroidUtilities.dp(16), AndroidUtilities.dp(24),
                surface::draw, false);
        if (!styledLists.containsKey(list)) {
            styledLists.put(list, true);
            list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
                @Override
                public void onChildViewAttachedToWindow(View view) {
                    if (view instanceof HeaderCell) header((HeaderCell) view, provider);
                    // Часы работы и адрес рисуют собственный непрозрачный фон
                    // поверх нашей карточки — отсюда тёмная полоса ровно на
                    // этом блоке. Секция под ними уже нарисована, свой фон им
                    // не нужен.
                    if (view instanceof ProfileHoursCell || view instanceof ProfileLocationCell) {
                        view.setBackground(null);
                    }
                    if (view instanceof AboutLinkCell) {
                        ((AboutLinkCell) view).setNebulaSectionSurface(true);
                        hideBioShadow((ViewGroup) view);
                    }
                }
                @Override
                public void onChildViewDetachedFromWindow(View view) { }
            });
        }
    }

    /**
     * Убирает полоску-затенение под текстом «О себе».
     *
     * <p>Она нужна, чтобы длинный текст истаивал у нижнего края, и залита
     * цветом строки. У Telegram строка того же цвета, и полоски не видно, а на
     * нашей карточке — другой оттенок, и между «О себе» и именем пользователя
     * проступала тёмная полоса в двенадцать точек. Прозрачности у полоски нет:
     * она рисуется всегда, даже когда текст помещается целиком.
     */
    private static void hideBioShadow(ViewGroup cell) {
        final int height = AndroidUtilities.dp(12);
        for (int i = 0; i < cell.getChildCount(); i++) {
            View child = cell.getChildAt(i);
            ViewGroup.LayoutParams params = child.getLayoutParams();
            if (child instanceof android.widget.FrameLayout && params != null
                    && params.height == height) {
                child.setBackground(null);
            }
        }
    }

    /** All drawing, hit targets, accessibility and avatar blur remain native. */
    public static ProfileActionsView actions(Context context, int height, Theme.ResourcesProvider provider) {
        return NebulaAppearance.profileStyle()
                ? new NebulaProfileArt.Actions(context, height, provider)
                : new ProfileActionsView(context, height);
    }

    public static float titleScale(float progress, boolean landscape) {
        return 1f + (NebulaAppearance.profileStyle() && !landscape ? .42f : .12f) * progress;
    }

    public static int titleSpacing(boolean landscape) {
        return NebulaAppearance.profileStyle() && !landscape ? AndroidUtilities.dp(8) : 0;
    }

    /**
     * A separate identity card above the existing settings groups. The gap is
     * a native section boundary; it does not wrap or reparent editable views.
     * Styling is chosen when the screen is created, as with the upstream theme.
     */
    public static void editor(SectionsScrollView scroll, LinearLayout content,
                              View identity, TextView name, TextView description,
                              Theme.ResourcesProvider provider) {
        if (!NebulaAppearance.profileStyle() || scroll == null || content == null) return;
        scroll.setSectionRadius(AndroidUtilities.dp(24));
        scroll.setSectionBackground(new NebulaProfileArt.Surface(content.getContext(), provider)::draw);
        editorInsets(content, 0, 0,
                Math.max(0, content.getPaddingBottom() - AndroidUtilities.dp(12)));

        final int identityPosition = content.indexOfChild(identity);
        if (identityPosition >= 0) {
            identity.setBackground(new NebulaProfileArt.IdentityBackground(content.getContext(), provider));
            identity.setPadding(identity.getPaddingLeft(), AndroidUtilities.dp(12),
                    identity.getPaddingRight(), AndroidUtilities.dp(12));
            View gap = new View(content.getContext());
            gap.setTag(RecyclerListView.TAG_NOT_SECTION);
            gap.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            content.addView(gap, identityPosition + 1,
                    new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(12)));
        }
        if (name != null) {
            // The native name field already wraps to four lines. SP keeps the
            // same field usable when the system font size is increased.
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            name.setTypeface(AndroidUtilities.bold());
        }
        if (description != null) {
            description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            description.setLineSpacing(AndroidUtilities.dp(2), 1f);
        }
        editorHeaders(content, provider, 0);
    }

    private static void editorHeaders(ViewGroup group, Theme.ResourcesProvider provider, int depth) {
        if (depth > 3) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof HeaderCell) header((HeaderCell) child, provider);
            else if (child instanceof ViewGroup) editorHeaders((ViewGroup) child, provider, depth + 1);
        }
    }

    private static void header(HeaderCell cell, Theme.ResourcesProvider provider) {
        TextView text = cell.getTextView();
        if (text == null) return;
        cell.setHeight(48);
        cell.setTopMargin(8);
        cell.setBottomMargin(8);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        text.setTypeface(AndroidUtilities.bold());
        text.setLetterSpacing(.04f);
        text.setAllCaps(true);
        text.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        text.setBackground(new NebulaProfileArt.LabelBackground(cell.getContext(), provider));
    }

    public static void editorInsets(LinearLayout content, int left, int right, int bottom) {
        if (content == null) return;
        final boolean enabled = NebulaAppearance.profileStyle();
        final int inset = AndroidUtilities.dp(enabled ? 16 : 12);
        content.setPadding(inset + (enabled ? left : 0),
                AndroidUtilities.dp(enabled ? 10 : 4),
                inset + (enabled ? right : 0), inset + bottom);
    }
}
