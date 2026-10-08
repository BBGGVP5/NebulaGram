package app.nebulagram.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;

/** Section introduction with Telegram's animated emoji and a readable grouped-settings layout. */
public final class NebulaSettingsHero extends LinearLayout {
    private static final int EMOJI_SIZE = 88;
    private static final int COMPACT_EMOJI_SIZE = 72;
    private static final int TITLE_SIZE = 24;
    private static final int DESCRIPTION_SIZE = 15;
    private final TextView status;
    private final TextView heading;

    public NebulaSettingsHero(Context context, int icon, String title, String description) {
        this(context, emoji(icon), title, description);
    }
    public NebulaSettingsHero(Context context, String emoji, String title, String description) {
        super(context);
        NebulaTheme theme = NebulaTheme.of(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setPadding(dp(12), dp(4), dp(12), dp(16));
        int imageSize=context.getResources().getConfiguration().screenHeightDp<550?COMPACT_EMOJI_SIZE:EMOJI_SIZE;
        NebulaAnimatedEmoji image=new NebulaAnimatedEmoji(context,UserConfig.selectedAccount,emoji,imageSize);
        LayoutParams imageParams=new LayoutParams(dp(imageSize),dp(imageSize));imageParams.bottomMargin=dp(12);
        addView(image,imageParams);
        heading=label(title,TITLE_SIZE,theme.onSurface());heading.setGravity(Gravity.CENTER);heading.setTypeface(AndroidUtilities.bold());
        addView(heading,new LayoutParams(-1,-2));
        TextView explanation = label(descriptionText(description), DESCRIPTION_SIZE, theme.onSurfaceVariant());
        explanation.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        // One- and two-line descriptions share a baseline and section start.
        // Longer translations and larger accessibility fonts can still grow.
        explanation.setMinLines(2);
        explanation.setLineSpacing(dp(2), 1f);
        LayoutParams explanationParams=new LayoutParams(-1,-2);explanationParams.topMargin=dp(8);
        addView(explanation, explanationParams);
        status = label("", 12, theme.primary());
        status.setGravity(Gravity.CENTER);
        LayoutParams params = new LayoutParams(-1, -2);
        params.topMargin = dp(6);
        addView(status, params);
        setStatus("");
    }
    /** Scroll position at which the large title reaches the native bar. */
    public int titleScrollAnchor() { return getTop() + heading.getTop(); }
    public static String descriptionText(String value) {
        if(value==null)return "";String text=value.trim();
        while(text.endsWith("."))text=text.substring(0,text.length()-1).trim();
        return text;
    }
    private static String emoji(int icon) {
        if(icon==R.drawable.msg_settings)return "🧰";
        if(icon==R.drawable.msg_secret)return "🔐";
        if(icon==R.drawable.msg_folders||icon==R.drawable.files_folder)return "🗂";
        if(icon==R.drawable.msg_language)return "🌐";
        if(icon==R.drawable.msg_notifications)return "🔔";
        if(icon==R.drawable.msg_emoji_smiles||icon==R.drawable.nebula_ai_spark)return "🤖";
        if(icon==R.drawable.msg_calendar)return "🗓";
        if(icon==R.drawable.msg_customize)return "🎨";
        if(icon==R.drawable.msg_recent)return "💬";
        return "⚙️";
    }

    public void setStatus(String value) { setStatus(value, false); }

    public void setStatus(String value, boolean active) {
        NebulaTheme theme = NebulaTheme.of(getContext());
        status.setTextColor(active ? theme.success() : theme.onSurfaceVariant());
        status.setText(value);
        status.setVisibility(value == null || value.isEmpty() ? GONE : VISIBLE);
    }

    private TextView label(String text, int size, int color) {
        TextView label = new TextView(getContext());
        label.setIncludeFontPadding(false);
        label.setText(text); label.setTextSize(size); label.setTextColor(color);
        return label;
    }
    private static int dp(int value) { return AndroidUtilities.dp(value); }
}
