package app.nebulagram.ui;

/** Native-position control: edit mode does not depend on the multiline compose threshold. */
public final class NebulaComposerAi {
    private NebulaComposerAi(){}
    public static boolean visible(boolean requested,boolean editing,boolean hasText,boolean rich,boolean allowed,boolean blocked) {
        return allowed&&!blocked&&(rich||hasText&&(requested||editing));
    }
    /** Opening the editor from tools must keep live translation paused after tools close. */
    public static final class Modals {
        private int count;
        public void opened(){count++;}
        public boolean closed(){if(count>0)count--;return count>0;}
        public boolean active(){return count>0;}
    }
}
