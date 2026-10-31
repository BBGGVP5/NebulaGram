package app.nebulagram.ui;

/** Pagination state independent of Telegram/UI; only positive remote IDs advance it. */
public final class NebulaHistoryPager {
    private int offset, received;
    private final int limit;
    public NebulaHistoryPager(int limit) { if (limit < 0) throw new IllegalArgumentException(); this.limit = limit; }
    public int offset() { return offset; }
    public int remaining() { return limit == 0 ? 100 : Math.min(100, Math.max(0, limit - received)); }
    public boolean accepts(int id) { return id > 0 && (offset == 0 || id < offset); }
    public boolean advance(int minimumId, int count) {
        if (count <= 0 || !accepts(minimumId)) return false;
        offset = minimumId; received += count;
        return remaining() > 0;
    }
    public int received() { return received; }
}
