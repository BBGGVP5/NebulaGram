package app.nebulagram.ui;

public final class NebulaSlowModePolicy {
    private NebulaSlowModePolicy() { }
    public static int next(int now, int serverNext, int last, int interval) {
        if (interval <= 0 || interval > 86400 || now <= 0) throw new IllegalArgumentException();
        long next = Math.max((long) now + 10, (long) serverNext + 1);
        if (last > now) next = Math.max(next, (long) last + interval);
        if (next >= Integer.MAX_VALUE - 1L) throw new IllegalArgumentException("Schedule overflow");
        return (int) next;
    }
}
