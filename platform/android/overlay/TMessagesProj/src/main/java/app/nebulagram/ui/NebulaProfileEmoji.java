package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;
import android.view.View;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;

/** Native Telegram emoji artwork at its intended small size, without monochrome tint. */
final class NebulaProfileEmoji implements NotificationCenter.NotificationCenterDelegate {
    private static final String[] GLYPHS={"💬","🔔","💬","🎁","🔗","📞","📹","➕","❗","🚪","🎙️","📺","📖","✋","📷","👤","📝","⚙️"};
    private final SparseArray<Drawable> icons=new SparseArray<>();
    private final View owner;
    NebulaProfileEmoji(View owner){this.owner=owner;}
    void attach(){NotificationCenter.getGlobalInstance().addObserver(this,NotificationCenter.emojiLoaded);}
    void detach(){NotificationCenter.getGlobalInstance().removeObserver(this,NotificationCenter.emojiLoaded);icons.clear();}
    boolean draw(Canvas canvas,Rect bounds,int key,float alpha){
        if(key<0||key>=GLYPHS.length)return false;
        Drawable emoji=icons.get(key);
        if(emoji==null){emoji=Emoji.getEmojiBigDrawable(GLYPHS[key]);if(emoji==null)return false;
            if(emoji instanceof Emoji.EmojiDrawable){((Emoji.EmojiDrawable)emoji).fullSize=false;((Emoji.EmojiDrawable)emoji).preload();}icons.put(key,emoji);}
        emoji.setBounds(bounds);emoji.setAlpha(Math.round(255*alpha));
        try{emoji.draw(canvas);}finally{emoji.setAlpha(255);}return true;
    }
    @Override public void didReceivedNotification(int id,int account,Object...args){owner.invalidate();}
}
