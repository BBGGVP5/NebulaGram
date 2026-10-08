package app.nebulagram.ui;

/** Reversible substitution; appending/prepending keeps the source-language draft. */
public final class NebulaDraftOriginal {
    private CharSequence original, displayed;
    private String mappedSource;
    public boolean hasOriginal() { return original != null; }
    public boolean isDisplayed(CharSequence value) { return displayed != null && displayed.toString().contentEquals(value); }
    public CharSequence source(CharSequence current) {
        CharSequence result;
        if (displayed == null) result = current;
        else if (current.toString().startsWith(displayed.toString())) result = concat(original, current.subSequence(displayed.length(), current.length()));
        else if (current.toString().endsWith(displayed.toString())) result = concat(current.subSequence(0, current.length() - displayed.length()), original);
        else { mappedSource = null; return current; }
        mappedSource = result.toString();
        return result;
    }
    private static CharSequence concat(CharSequence first, CharSequence second) {
        return new android.text.SpannableStringBuilder(first).append(second);
    }
    public CharSequence restore(CharSequence current) {
        if (displayed == null) return current;
        if (current.toString().startsWith(displayed.toString()) || current.toString().endsWith(displayed.toString())) return source(current);
        return original;
    }
    public void replaced(CharSequence source, CharSequence translated) {
        if (original == null || source.toString().equals(mappedSource)) original = NebulaRichText.snapshot(source);
        displayed = NebulaRichText.snapshot(translated);
        mappedSource = null;
    }
    public void clear() { original = displayed = null; mappedSource = null; }
}
