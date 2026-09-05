package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatActivityEnterViewAnimatedIconView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;

/** The real composer's resources and dimensions, without chat/keyboard side effects. */
public final class NebulaComposerPreview extends FrameLayout {
    private final ImageView attachment, microphone;
    private final ChatActivityEnterViewAnimatedIconView emoji;
    private final TextView hint;
    private final BlurredBackgroundDrawable[] surfaces = new BlurredBackgroundDrawable[3];
    private Drawable wallpaper;
    private boolean separate;
    private int fieldLeft, fieldRight, top;

    public NebulaComposerPreview(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        attachment = icon(context, R.drawable.msg_input_attach2);
        microphone = icon(context, R.drawable.input_mic);
        emoji = new ChatActivityEnterViewAnimatedIconView(context);
        emoji.setState(ChatActivityEnterViewAnimatedIconView.State.SMILE, false);
        int inset = AndroidUtilities.dp(7.5f);
        emoji.setPadding(inset, inset, inset, inset);
        addView(emoji);
        hint = new TextView(context);
        hint.setText(LocaleController.getString(R.string.TypeMessage));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setIncludeFontPadding(false);
        hint.setSingleLine();
        hint.setEllipsize(TextUtils.TruncateAt.END);
        addView(hint);

        BlurredBackgroundSourceColor source = new BlurredBackgroundSourceColor();
        source.setColor(Theme.getColor(Theme.key_chat_wallpaper));
        BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(source);
        BlurredBackgroundColorProviderThemed colors =
                new BlurredBackgroundColorProviderThemed(null, Theme.key_chat_messagePanelBackground);
        for (int i = 0; i < surfaces.length; i++) {
            surfaces[i] = factory.create(this, colors);
            surfaces[i].setRadius(AndroidUtilities.dp(22));
            surfaces[i].setPadding(AndroidUtilities.dp(7));
        }
        Drawable cached = Theme.getCachedWallpaperNonBlocking();
        if (cached != null && cached.getConstantState() != null) {
            wallpaper = cached.getConstantState().newDrawable().mutate();
        }
        refresh();
    }

    private ImageView icon(Context context, int resource) {
        ImageView view = new ImageView(context);
        view.setImageResource(resource);
        view.setScaleType(ImageView.ScaleType.CENTER);
        addView(view);
        return view;
    }

    public void refresh() {
        separate = NebulaAppearance.iosComposer();
        int background = Theme.getColor(Theme.key_chat_messagePanelBackground);
        int color = NebulaChatColors.foreground(Theme.getColor(Theme.key_glass_defaultIcon), background);
        attachment.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        microphone.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        emoji.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        hint.setTextColor(Theme.getColor(Theme.key_chat_messagePanelHint));
        requestLayout();
        invalidate();
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width, AndroidUtilities.dp(100));
        int diameter = AndroidUtilities.dp(44), margin = AndroidUtilities.dp(8);
        fieldLeft = margin + (separate ? diameter + AndroidUtilities.dp(6) : 0);
        fieldRight = width - fieldLeft;
        int textLeft = fieldLeft + AndroidUtilities.dp(separate ? 14 : 50);
        int textRight = fieldRight - AndroidUtilities.dp(separate ? 44 : 88);
        int exact = MeasureSpec.EXACTLY;
        for (View view : new View[] {attachment, microphone, emoji}) {
            view.measure(MeasureSpec.makeMeasureSpec(diameter, exact), MeasureSpec.makeMeasureSpec(diameter, exact));
        }
        hint.measure(MeasureSpec.makeMeasureSpec(Math.max(0, textRight - textLeft), exact),
                MeasureSpec.makeMeasureSpec(diameter, exact));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int diameter = AndroidUtilities.dp(44), margin = AndroidUtilities.dp(8);
        top = (getHeight() - diameter) / 2;
        int attachmentLeft = separate ? margin : getWidth() - margin - diameter * 2;
        attachment.layout(attachmentLeft, top, attachmentLeft + diameter, top + diameter);
        microphone.layout(getWidth() - margin - diameter, top, getWidth() - margin, top + diameter);
        int emojiRight = separate ? fieldRight : margin + AndroidUtilities.dp(2) + diameter;
        emoji.layout(emojiRight - diameter, top, emojiRight, top + diameter);
        int textLeft = fieldLeft + AndroidUtilities.dp(separate ? 14 : 50);
        hint.layout(textLeft, top, textLeft + hint.getMeasuredWidth(), top + diameter);
        bounds(surfaces[0], margin, margin + diameter, diameter);
        bounds(surfaces[1], fieldLeft, fieldRight, diameter);
        bounds(surfaces[2], getWidth() - margin - diameter, getWidth() - margin, diameter);
        if (wallpaper != null) {
            float scale = Math.max(getWidth() / (float) Math.max(1, wallpaper.getIntrinsicWidth()),
                    getHeight() / (float) Math.max(1, wallpaper.getIntrinsicHeight()));
            int w = Math.round(wallpaper.getIntrinsicWidth() * scale);
            int h = Math.round(wallpaper.getIntrinsicHeight() * scale);
            wallpaper.setBounds((getWidth() - w) / 2, (getHeight() - h) / 2,
                    (getWidth() + w) / 2, (getHeight() + h) / 2);
        }
    }

    private void bounds(BlurredBackgroundDrawable drawable, int left, int right, int height) {
        int padding = AndroidUtilities.dp(7);
        drawable.setBounds(left - padding, top - padding, right + padding, top + height + padding);
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Theme.getColor(Theme.key_chat_wallpaper));
        if (wallpaper != null) wallpaper.draw(canvas);
        if (separate) { surfaces[0].draw(canvas); surfaces[2].draw(canvas); }
        surfaces[1].draw(canvas);
    }
}
