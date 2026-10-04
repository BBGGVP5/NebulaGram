package app.nebulagram.ui;

/** Reversible substitution; appending/prepending keeps the source-language draft. */
public final class NebulaDraftOriginal {
    private String original, displayed;
    private String mappedSource;
    public boolean hasOriginal() { return original != null; }
    public boolean isDisplayed(String value) { return displayed != null && displayed.equals(value); }
    public String source(String current) {
        if (displayed == null) return mappedSource = current;
        if (current.startsWith(displayed)) return mappedSource = original + current.substring(displayed.length());
        if (current.endsWith(displayed)) return mappedSource = current.substring(0, current.length() - displayed.length()) + original;
        mappedSource = null;
        return current;
    }
    public String restore(String current) {
        if (displayed == null) return current;
        if (current.startsWith(displayed) || current.endsWith(displayed)) return source(current);
        return original;
    }
    public void replaced(String source, String translated) {
        if (original == null || source.equals(mappedSource)) original = source;
        displayed = translated; mappedSource = null;
    }
    public void clear() { original = displayed = mappedSource = null; }
}
