package app.nebulagram.ui;

/** Optional short appearance animation; state belongs to the cell, not to its message. */
public final class NebulaTextFade {
    private long dialog;
    private int account = -1, message, edit;
    private long started = -1;
    public int alpha(int account, long dialog, int message, int edit, long now, boolean animate) {
        boolean changed = this.account != account || this.dialog != dialog || this.message != message || this.edit != edit;
        this.account=account;this.dialog=dialog;this.message=message;this.edit=edit;
        if (!animate) { started=-1; return 255; }
        if (changed) started=now;
        if (started<0) return 255;
        float p=Math.max(0f,Math.min(1f,(now-started)/160f));
        if(p>=1f){started=-1;return 255;}
        return Math.round(255f*(1f-(1f-p)*(1f-p)));
    }
}
