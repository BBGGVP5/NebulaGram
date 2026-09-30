package app.nebulagram.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Global factors are relative to the main camera; native factors are local to each module. */
public final class NebulaZoomCapabilities {
    public static final class Module {
        public final String id;
        public final float base, min, max;
        public final boolean logical;
        public Module(String id, float base, float min, float max, boolean logical) {
            this.id = id; this.base = base; this.min = min; this.max = max; this.logical = logical;
        }
        public float lower() { return base * min; }
        public float upper() { return base * max; }
        public boolean covers(float factor) { return factor >= lower() - .001f && factor <= upper() + .001f; }
        public float local(float factor) { return Math.max(min, Math.min(max, factor / base)); }
    }

    public final List<Module> modules;
    public final Module primary;
    public final float minimum, maximum;
    public final float[] stops;

    public NebulaZoomCapabilities(List<Module> modules, Module primary, List<Float> opticalStops) {
        this.modules = new ArrayList<>(modules);
        this.primary = primary;
        float low = primary.lower(), high = primary.upper();
        TreeSet<Float> values = new TreeSet<>();
        for (Module module : modules) {
            low = Math.min(low, module.lower()); high = Math.max(high, module.upper());
            values.add(module.base);
        }
        // A vendor may crop the ultra-wide stream: its supported stop is the ratio-range floor.
        for (float stop : opticalStops) if (stop > 0f && stop <= high) values.add(Math.max(low, stop));
        Float previous = null;
        java.util.Iterator<Float> iterator = values.iterator();
        while (iterator.hasNext()) {
            float value = iterator.next();
            if (previous != null && Math.abs(value / previous - 1f) < .06f) iterator.remove();
            else previous = value;
        }
        minimum = low; maximum = high;
        stops = new float[values.size()];
        int i = 0; for (float value : values) stops[i++] = value;
    }

    public Module find(String id) {
        for (Module module : modules) if (module.id.equals(id)) return module;
        return primary;
    }

    public boolean needsReopen() {
        return !primary.logical && modules.size() > 1 || !primary.covers(minimum) || !primary.covers(maximum);
    }

    public Module select(float factor) {
        // A logical camera switches its own physical sensors without interrupting the preview.
        if (primary.logical && primary.covers(factor)) return primary;
        Module best = null;
        for (Module module : modules) if (module.covers(factor)) {
            if (best == null || module.base > best.base) best = module;
        }
        if (best != null) return best;
        // Some devices expose disjoint ranges. Snap to the nearest usable endpoint.
        best = primary;
        float distance = Float.MAX_VALUE;
        for (Module module : modules) {
            float clamped = module.base * module.local(factor);
            float delta = Math.abs((float) Math.log(clamped / factor));
            if (delta < distance) { distance = delta; best = module; }
        }
        return best;
    }

}
