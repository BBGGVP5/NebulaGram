package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.widget.EditText;

/**
 * The gray inline result after "=". Space right after "=" inserts it; Backspace right after
 * an insertion puts the expression back. Its own watcher is attached on the first draw.
 */
public final class NebulaMathHint {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String result = "";
    private String prefix = "";
    private EditText input;
    private CharSequence beforeInsertion;
    private String afterInsertion;
    private boolean applying;
    private final TextWatcher watcher = new TextWatcher() {
        private int start, before, count;
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { this.start = start; this.before = before; this.count = count; }
        @Override public void afterTextChanged(Editable s) {
            if (applying) return;
            CharSequence restore = beforeInsertion; String inserted = afterInsertion;
            beforeInsertion = null; afterInsertion = null;
            if (!NebulaInlineSettings.enabled()) return;
            String text = s.toString();
            if (inserted != null && before == 1 && count == 0 && start == text.length() && inserted.length() == text.length() + 1 && inserted.startsWith(text)) {
                replace(s, 0, s.length(), restore);
                return;
            }
            if (before != 0 || count != 1 || start != text.length() - 1 || text.charAt(start) != ' ') return;
            String head = text.substring(0, start);
            if (!head.endsWith("=")) return;
            String value = NebulaInlineResults.compute(head, NebulaInlineSettings.options());
            if (value.isEmpty()) return;
            CharSequence original = new SpannableStringBuilder(s, 0, start);
            replace(s, start, start + 1, spacing(head) + value + " ");
            beforeInsertion = original; afterInsertion = s.toString();
        }
    };
    private void replace(Editable s, int from, int to, CharSequence value) {
        applying = true;
        try { s.replace(from, to, value); } finally { applying = false; }
        if (input != null) input.setSelection(s.length());
    }
    /** "2 + 2 =" reads as "2 + 2 = 4"; "2+2=" as "2+2=4". */
    private static String spacing(String head) {
        return head.length() > 1 && Character.isWhitespace(head.charAt(head.length() - 2)) ? " " : "";
    }
    public void update(CharSequence source) {
        if (!NebulaInlineSettings.enabled() || source == null) { result = ""; return; }
        NebulaInlineResults.Options options = NebulaInlineSettings.options();
        result = NebulaInlineResults.compute(source, options);
        String text = source.toString();
        prefix = text.endsWith("=") ? spacing(text) : " = ";
        if (options.ratesWanted && options.rates == null) NebulaInlineSettings.load(() -> {
            EditText field = input;
            if (field != null) { update(field.getText()); field.invalidate(); }
        });
    }
    public void draw(EditText input, Canvas canvas) {
        if (this.input != input) {
            if (this.input != null) this.input.removeTextChangedListener(watcher);
            this.input = input; input.addTextChangedListener(watcher);
        }
        if (result.isEmpty() || !NebulaInlineSettings.enabled()) return;
        Layout layout = input.getLayout(); if (layout == null) return;
        int last = layout.getLineCount() - 1; if (last < 0) return;
        paint.set(input.getPaint()); paint.setColor(input.getCurrentHintTextColor());
        String value = prefix + result;
        float x = input.getCompoundPaddingLeft() + layout.getLineRight(last) - input.getScrollX();
        float y = input.getExtendedPaddingTop() + layout.getLineBaseline(last) - input.getScrollY();
        if (x + paint.measureText(value) > input.getWidth() - input.getCompoundPaddingRight() || y > input.getHeight() - input.getPaddingBottom()) return;
        canvas.drawText(value, x, y, paint);
    }
}
