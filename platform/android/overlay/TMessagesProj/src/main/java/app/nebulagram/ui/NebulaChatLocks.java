package app.nebulagram.ui;

import android.graphics.Canvas;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EditTextBoldCursor;
import java.util.*;
import java.util.concurrent.*;
import static app.nebulagram.ui.NebulaText.text;

/** UI access protection on this installation; Telegram's message database is unchanged. */
public final class NebulaChatLocks {
    public static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static NebulaChatLockStore store;
    private static final Set<String> unlocked=ConcurrentHashMap.newKeySet();
    private static final Map<ViewGroup,java.lang.ref.WeakReference<Gate>> gates=new WeakHashMap<>();
    private static int generation;
    public static synchronized NebulaChatLockStore store(){if(store==null)store=new NebulaChatLockStore(ApplicationLoader.applicationContext.getNoBackupFilesDir());return store;}
    public static long user(int account){return UserConfig.getInstance(account).getClientUserId();}
    private static String key(int account,long dialog){return user(account)+":"+dialog;}
    public static boolean protectedChat(int account,long dialog){return store().protectedChat(user(account),dialog);}
    public static boolean locked(int account,long dialog){return protectedChat(account,dialog)&&!unlocked.contains(key(account,dialog));}
    public static boolean locked(MessageObject m){return m!=null && locked(m.currentAccount,m.getDialogId());}
    public static long dialog(BaseFragment host) {
        if(host instanceof org.telegram.ui.ChatActivity)return ((org.telegram.ui.ChatActivity)host).getDialogId();
        if(host instanceof org.telegram.ui.ProfileActivity)return ((org.telegram.ui.ProfileActivity)host).getDialogId();
        if(host instanceof org.telegram.ui.Components.MediaActivity)return ((org.telegram.ui.Components.MediaActivity)host).getDialogId();
        return 0;
    }
    public static void install(BaseFragment host) {
        if(!(host.getFragmentView() instanceof ViewGroup) || dialog(host)==0)return;
        if(!protectedChat(host.getCurrentAccount(),dialog(host)))return;
        ViewGroup root=(ViewGroup)host.getFragmentView();Gate gate=find(root);
        if(gate==null) {gate=new Gate(host);gates.put(root,new java.lang.ref.WeakReference<>(gate));root.addView(gate,new android.widget.FrameLayout.LayoutParams(-1,-1));}
        gate.refresh();
    }
    public static void resume(BaseFragment host){install(host);}
    public static void lockAll() {
        generation++;unlocked.clear();
        for(java.lang.ref.WeakReference<Gate> reference:new ArrayList<>(gates.values())){Gate gate=reference.get();if(gate!=null)gate.refresh();}
        if(org.telegram.ui.PhotoViewer.hasInstance()) {
            org.telegram.ui.PhotoViewer viewer=org.telegram.ui.PhotoViewer.getInstance();
            if(viewer.isNebulaLocked())viewer.closePhoto(false,false);
        }
        org.telegram.messenger.MediaController media=org.telegram.messenger.MediaController.getInstance();
        if(locked(media.getPlayingMessageObject()))media.cleanupPlayer(true,true);
    }
    public static void changed(){lockAll();}
    public static boolean draw(Canvas c,ViewGroup root) {
        Gate gate=find(root);if(gate==null || !locked(gate.account,gate.dialog))return false;
        gate.setVisibility(View.VISIBLE);
        if(gate.getWidth()!=root.getWidth() || gate.getHeight()!=root.getHeight()) {
            gate.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(root.getHeight(),View.MeasureSpec.EXACTLY));
            gate.layout(0,0,root.getWidth(),root.getHeight());
        }
        c.drawColor(NebulaTheme.of(root.getContext()).surface());gate.draw(c);return true;
    }
    public static boolean intercept(ViewGroup root,android.view.MotionEvent event) {
        Gate gate=find(root);if(gate==null || !locked(gate.account,gate.dialog))return false;
        gate.dispatchTouchEvent(event);return true;
    }
    public static void mask(Canvas c,View host) {
        android.graphics.Paint p=new android.graphics.Paint(3);p.setColor(NebulaTheme.of(host.getContext()).surface());c.drawRect(0,0,host.getWidth(),host.getHeight(),p);
        p.setColor(NebulaTheme.of(host.getContext()).onSurfaceVariant());p.setTextSize(AndroidUtilities.dp(14));
        String label=text("Чат защищён паролем","Password-protected chat");
        float max=host.getWidth()-AndroidUtilities.dp(32);if(p.measureText(label)>max)p.setTextSize(p.getTextSize()*max/p.measureText(label));
        c.drawText(label,AndroidUtilities.dp(16),host.getHeight()/2f+AndroidUtilities.dp(5),p);
    }
    private static Gate find(ViewGroup root){java.lang.ref.WeakReference<Gate> value=gates.get(root);return value==null?null:value.get();}
    private static final class Gate extends FrameLayout {
        final BaseFragment host;final int account;final long dialog;
        final EditTextBoldCursor password;final TextView status;final NebulaButton unlock;
        Gate(BaseFragment host) {
            super(host.getContext());this.host=host;account=host.getCurrentAccount();dialog=dialog(host);
            setBackgroundColor(NebulaTheme.of(getContext()).surface());setClickable(true);setFocusable(true);
            LinearLayout form=NebulaFormUi.column(getContext());form.setGravity(Gravity.CENTER_VERTICAL);
            TextView title=NebulaIntroFragment.text(getContext(),24,NebulaTheme.of(getContext()).onSurface(),true);title.setText(text("Чат под паролем","This chat is locked"));form.addView(title);
            boolean pin=store().isPin(user(account),dialog);
            status=NebulaFormUi.note(getContext(),pin?text("Введи PIN-код этого чата","Enter this chat's PIN"):text("Введи пароль этого чата","Enter this chat's password"));form.addView(status);
            password=NebulaFormUi.field(getContext(),pin?text("PIN-код","PIN code"):text("Пароль","Password"),1,pin?12:128);password.setInputType(pin?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);form.addView(password);
            unlock=NebulaFormUi.primary(getContext(),text("Открыть чат","Unlock chat"),v->verify());form.addView(unlock);
            NebulaButton back=new NebulaButton(getContext(),NebulaButton.STYLE_TEXT);back.setText(text("Назад","Back"));back.setOnClickListener(v->host.finishFragment());form.addView(back);
            addView(NebulaFormUi.scroll(getContext(),form),new FrameLayout.LayoutParams(-1,-1));
        }
        void refresh() {
            boolean locked=locked(account,dialog);setVisibility(locked?View.VISIBLE:View.GONE);
            if(locked){password.setText("");unlock.setEnabled(true);}
            ViewGroup root=(ViewGroup)getParent();if(root!=null) {
                for(int i=0;i<root.getChildCount();i++){View v=root.getChildAt(i);if(v!=this)v.setImportantForAccessibility(locked?View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);}
                bringToFront();root.invalidate();
            }
        }
        void verify() {
            final char[] value=password.getText().toString().toCharArray();password.setText("");unlock.setEnabled(false);
            final int token=generation;final long owner=user(account);
            worker.execute(()->{
                boolean ok=false;long wait=0;
                try{ok=store().verify(owner,dialog,value,System.currentTimeMillis());wait=store().remaining(owner,dialog,System.currentTimeMillis());}catch(Exception ignored){}
                finally{Arrays.fill(value,'\0');}
                final boolean success=ok;final long delay=wait;
                AndroidUtilities.runOnUIThread(()->{
                    unlock.setEnabled(true);if(token!=generation || owner!=user(account) || !isAttachedToWindow())return;
                    if(success){unlocked.add(key(account,dialog));AndroidUtilities.hideKeyboard(password);refresh();}
                    else status.setText(delay>0?text("Слишком много попыток. Подожди 30 секунд.","Too many attempts. Wait 30 seconds."):text("Неверный пароль","Incorrect password"));
                });
            });
        }
    }
}
