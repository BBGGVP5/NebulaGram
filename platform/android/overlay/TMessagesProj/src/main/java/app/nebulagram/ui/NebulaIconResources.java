package app.nebulagram.ui;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

/** Resolves interface icons at the resource boundary, including native Telegram screens. */
public final class NebulaIconResources extends Resources {
    private final Resources original;

    public NebulaIconResources(Resources original) {
        super(original.getAssets(), original.getDisplayMetrics(), original.getConfiguration());
        this.original = original;
    }

    public static Resources wrap(Resources original, Resources previous) {
        if (previous instanceof NebulaIconResources && ((NebulaIconResources) previous).original == original) {
            if (!previous.getConfiguration().equals(original.getConfiguration())) {
                previous.updateConfiguration(original.getConfiguration(), original.getDisplayMetrics());
            }
            return previous;
        }
        return new NebulaIconResources(original);
    }

    @Override public Drawable getDrawable(int id) throws NotFoundException {
        return original.getDrawable(NebulaIcons.resource(id));
    }
    @Override public Drawable getDrawable(int id, Theme theme) throws NotFoundException {
        return original.getDrawable(NebulaIcons.resource(id), theme);
    }
    @Override public Drawable getDrawableForDensity(int id, int density) throws NotFoundException {
        return original.getDrawableForDensity(NebulaIcons.resource(id), density);
    }
    @Override public Drawable getDrawableForDensity(int id, int density, Theme theme) throws NotFoundException {
        return original.getDrawableForDensity(NebulaIcons.resource(id), density, theme);
    }
    @Override public void getValue(int id, TypedValue out, boolean resolveRefs) throws NotFoundException {
        original.getValue(NebulaIcons.resource(id), out, resolveRefs);
    }
    @Override public void getValueForDensity(int id, int density, TypedValue out, boolean resolveRefs) throws NotFoundException {
        original.getValueForDensity(NebulaIcons.resource(id), density, out, resolveRefs);
    }
}
