package app.nebulagram.ui;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;

/**
 * Иллюстрации обзора: живые миниатюры экранов в духе Telegram.
 *
 * <p>Синяя шапка списка чатов, пузыри на обоях, аватарки с инициалами и
 * синий экран код-пароля — всё узнаётся сразу. Каждая сцена проигрывает
 * короткую петлю: собеседник печатает, меняется акцент, вводится код,
 * отвечает ИИ, пульсирует подключение. Без анимации показан готовый кадр.
 * Геометрия задана на холсте 360x360 и масштабируется под свободное место.
 */
public final class NebulaIntroArt extends View {
    private static final float CANVAS = 360f;
    /** Длина петли каждой сцены, мс. */
    private static final long[] LOOP = {5200, 9600, 4600, 5600, 2400};
    /** Кадр для режима без анимации: сцена в законченном виде. */
    private static final long[] STILL = {3400, 0, 1000, 4000, 600};
    private static final int GREEN = 0xff4fbf62;
    private static final int[][] AVATARS = {
            {0xff5caffa, 0xff408acf}, {0xffb694f9, 0xff6c61df}, {0xffff8aac, 0xffd95574}};
    private static final int[] SWATCHES = {0, 0xff7f72e8, 0xff3fb47a, 0xffe46b8e, 0xfff09a37, 0xff3cb5c6};

    private final Paint p = new Paint(3);
    private final RectF r = new RectF();
    private final Path path = new Path();
    private final Path clip = new Path();
    private final java.util.ArrayList<Shade> gradients = new java.util.ArrayList<>();
    private static final class Shade {
        final float l,t,r,b; final int first,last; final LinearGradient shader;
        Shade(float l,float t,float r,float b,int first,int last) {
            this.l=l;this.t=t;this.r=r;this.b=b;this.first=first;this.last=last;
            shader=new LinearGradient(l,t,r,b,first,last,Shader.TileMode.CLAMP);
        }
    }
    /** Градиенты кэшируются, поэтому сюда идут только постоянные цвета. */
    private LinearGradient shade(float l,float t,float r,float b,int first,int last) {
        for(int i=0;i<gradients.size();i++) {
            Shade value=gradients.get(i);
            if(value.l==l&&value.t==t&&value.r==r&&value.b==b&&value.first==first&&value.last==last) return value.shader;
        }
        Shade value=new Shade(l,t,r,b,first,last);gradients.add(value);return value.shader;
    }

    private int page;
    private long start = SystemClock.uptimeMillis();
    private boolean animated = true;
    private final boolean dark;
    private final int ink, muted, accent, card, raised, edge, tint, header, onHeader;
    private final int inBubble, outBubble, outText, outCheck, wallTop, wallBottom, listCheck;

    private static int blend(int first, int last, float amount) {
        int red = Math.round(Color.red(first) * (1 - amount) + Color.red(last) * amount);
        int green = Math.round(Color.green(first) * (1 - amount) + Color.green(last) * amount);
        int blue = Math.round(Color.blue(first) * (1 - amount) + Color.blue(last) * amount);
        return Color.rgb(red, green, blue);
    }
    private static int alpha(int color, float amount) {
        int value = Math.round((color >>> 24) * Math.max(0, Math.min(1, amount)));
        return (color & 0x00ffffff) | (value << 24);
    }
    private static boolean light(int color) {
        return Color.red(color) * .299f + Color.green(color) * .587f + Color.blue(color) * .114f > 150;
    }

    public NebulaIntroArt(Context context) {
        super(context);
        NebulaTheme theme = NebulaTheme.of(context);
        int surface = theme.surface();
        dark = !light(surface);
        ink = theme.onSurface(); muted = theme.onSurfaceVariant(); accent = theme.primary();
        // Цвета Telegram по умолчанию: белые карточки и синяя шапка днём,
        // графитовые карточки и тёмная шапка ночью.
        card = dark ? blend(surface, ink, .09f) : 0xffffffff;
        raised = dark ? blend(card, ink, .07f) : 0xfff1f2f4;
        edge = alpha(ink, dark ? .10f : .08f);
        tint = blend(card, accent, dark ? .22f : .12f);
        header = dark ? blend(card, ink, .05f) : blend(accent, 0xff000000, .08f);
        onHeader = dark ? ink : 0xffffffff;
        inBubble = dark ? blend(card, ink, .06f) : 0xffffffff;
        outBubble = dark ? 0xff3a6ea5 : 0xffeffede;
        outText = dark ? 0xffffffff : ink;
        outCheck = dark ? 0xffcfe3ff : 0xff4fae4e;
        wallTop = dark ? 0xff1e2b26 : 0xffdbddbb;
        wallBottom = dark ? 0xff12191a : 0xff8fbf8f;
        listCheck = dark ? accent : 0xff46aa36;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setPage(int value) {
        if (value != page) start = SystemClock.uptimeMillis();
        page = value; invalidate();
    }
    /** Выключает петли, если пользователь отключил анимации. */
    public void setAnimated(boolean value) {
        if (value && !animated) start = SystemClock.uptimeMillis();
        animated = value; invalidate();
    }

    /**
     * Рисунок занимает всё место, что ему дали, а без ограничения просит
     * квадрат по ширине колонки.
     */
    @Override protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        int preferred = Math.min(width, AndroidUtilities.dp(300 * NebulaLoginStyle.vertical()));
        int height = MeasureSpec.getMode(h) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(h)
                : MeasureSpec.getMode(h) == MeasureSpec.AT_MOST ? Math.min(preferred, MeasureSpec.getSize(h))
                : preferred;
        setMeasuredDimension(width, height);
    }
    private String t(String ru, String en) { return NebulaText.text(ru, en); }
    @Override protected void onDraw(Canvas c) {
        float scale = Math.min(getWidth()/CANVAS, getHeight()/CANVAS);
        // Крупнее этого миниатюра выглядит как настоящий экран, а не картинка.
        scale = Math.min(scale, AndroidUtilities.dp(380) / CANVAS);
        if (scale <= 0) return;
        long time = animated ? (SystemClock.uptimeMillis() - start) % LOOP[page] : STILL[page];
        c.save(); c.translate((getWidth()-CANVAS*scale)/2, (getHeight()-CANVAS*scale)/2); c.scale(scale, scale);
        if(page==0) welcome(c,time); else if(page==1) design(c,time); else if(page==2) privacy(c,time); else if(page==3) ai(c,time); else link(c,time);
        c.restore();
        if (animated) postInvalidateOnAnimation();
    }

    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
    /** Плавный переход от 0 к 1 между моментами from и to. */
    private static float span(long t, long from, long to) { float v = clamp((t - from) / (float) (to - from)); return v * v * (3 - 2 * v); }
    /** Появление с лёгким перелётом, как у счётчиков Telegram. */
    private static float pop(long t, long from) {
        float u = clamp((t - from) / 320f);
        return u <= 0 ? 0 : u * (1 + .28f * (float) Math.sin(Math.PI * u));
    }

    private void welcome(Canvas c, long t) {
        int save = clipCard(c,30,26,330,334,24,card,card);
        r.set(30,26,330,112); p.setColor(header); c.drawRect(r,p);
        text(c,"NebulaGram",52,62,18,onHeader,true);
        search(c,304,56,onHeader);
        String[] tabs={t("Все","All"),t("Личные","Personal"),t("Работа","Work")};
        float x=52;
        for(int i=0;i<tabs.length;i++) {
            p.setTypeface(AndroidUtilities.bold()); p.setTextSize(13);
            float width=p.measureText(tabs[i]);
            text(c,tabs[i],x,97,13,i==0?onHeader:alpha(onHeader,.6f),true);
            if(i==0) { r.set(x-3,108,x+width+3,112); p.setColor(onHeader); c.drawRoundRect(r,2,2,p); }
            x+=width+26;
        }
        String[] names={t("Избранное","Saved Messages"),t("Команда Nebula","Nebula team"),t("Аня","Anna")};
        String[] initials={"","КН",t("А","A")};
        // Новое сообщение приходит в 2.6 с и тает в конце петли.
        float arrived = span(t,2600,2800) * (1 - span(t,4900,5200));
        for(int i=0;i<3;i++) {
            float y=150+i*60;
            avatar(c,62,y,i,initials[i]);
            fitText(c,names[i],96,y-4,14,150,ink,true);
            if(i==0) { checks(c,268,y-8,listCheck,.8f); rightText(c,"16:40",314,y-4,11,muted,false); }
            if(i==1) {
                rightText(c,"12:30",314,y-4,11,muted,false);
                p.setTypeface(AndroidUtilities.bold()); p.setTextSize(12);
                String sender=t("Макс: ","Max: ");
                float senderWidth=p.measureText(sender);
                text(c,sender,96,y+16,12,accent,true);
                fitText(c,t("Новая идея готова","The new idea is ready"),96+senderWidth,y+16,12,170-senderWidth,muted,false);
                badge(c,304,y+12,"3",1);
            }
            if(i==0) fitText(c,t("Фото, ссылки и заметки","Photos, links and notes"),96,y+16,12,200,muted,false);
            if(i==2) {
                rightText(c,arrived>.5f?"16:42":"11:58",314,y-4,11,muted,false);
                if(arrived<1) typing(c,96,y+16,t,1-arrived);
                if(arrived>0) fitText(c,t("Увидимся вечером?","See you tonight?"),96,y+16,12,170,alpha(ink,arrived),false);
                float s=pop(t,2600)*(1-span(t,4900,5200));
                if(s>0) badge(c,304,y+12,"1",s);
            }
            if(i<2) divider(c,96,y+30,330,y+30);
        }
        c.restoreToCount(save);
        outline(c,30,26,330,334,24);
    }

    private void design(Canvas c, long t) {
        int step=(int)(t/1600)%SWATCHES.length, previous=(step+SWATCHES.length-1)%SWATCHES.length;
        float k = t < 1600 ? 1 : span(t%1600,0,360);
        int current=blend(swatch(previous),swatch(step),k);
        int save=clipCard(c,34,18,326,176,22,wallTop,wallBottom);
        round(c,52,36,226,76,16,inBubble);
        fitText(c,t("Новая тема готова?","New theme ready?"),66,61,13,118,ink,false);
        rightText(c,"16:39",216,70,9,muted,false);
        int bubble = dark ? blend(0xff1f2123,current,.62f) : blend(0xffffffff,current,.24f);
        round(c,118,84,312,124,16,bubble);
        fitText(c,t("Да, с моим акцентом","Yes, with my accent"),132,109,13,124,outText,false);
        rightText(c,"16:40",288,118,9,alpha(outText,.6f),false);
        checks(c,292,110,dark?alpha(0xffffffff,.85f):current,.65f);
        round(c,52,132,120,162,16,inBubble);
        for(int i=0;i<3;i++) circle(c,72+i*14,147,3.5f,alpha(muted,dots(t,i)));
        c.restoreToCount(save);
        outline(c,34,18,326,176,22);
        text(c,t("Цвет акцента","Accent color"),42,208,12,muted,true);
        float from=58+previous*49, to=58+step*49;
        for(int i=0;i<SWATCHES.length;i++) circle(c,58+i*49,238,15,swatch(i));
        ring(c,from+(to-from)*k,238,20,ink);
        text(c,t("Иконка приложения","App icon"),42,286,12,muted,true);
        for(int i=0;i<5;i++) {
            float x=42+i*56;
            int color=swatch(i);
            if(i==0) gradientRound(c,x,300,x+44,344,13,blend(accent,0xffffffff,.25f),accent);
            else gradientRound(c,x,300,x+44,344,13,blend(color,0xffffffff,.25f),color);
            centeredText(c,"N",x+22,331,22,0xffffffff,true);
        }
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.4f);p.setColor(ink);r.set(37,295,91,349);c.drawRoundRect(r,17,17,p);p.setStyle(Paint.Style.FILL);
    }
    private int swatch(int i) { return i==0 ? accent : SWATCHES[i]; }

    private void privacy(Canvas c, long t) {
        // Синий экран код-пароля Telegram: белые клавиши на цвете шапки.
        int bottom=blend(header,0xff000000,dark?.25f:.18f);
        int save=clipCard(c,66,14,294,346,30,header,bottom);
        long[] presses={300,800,1300,1800};
        int[] keys={1,4,7,10};
        int typed=0;for(long press:presses) if(t>=press) typed++;
        float unlocked=span(t,2200,2400)*(1-span(t,3700,3950));
        if(t>=3950) typed=0;
        circle(c,180,62,24,alpha(onHeader,.16f));
        lock(c,180,62,onHeader,unlocked);
        centeredText(c,t("Код-пароль","Passcode"),180,110,16,onHeader,true);
        fitCentered(c,t("Введите код, чтобы открыть","Enter it to unlock"),180,129,11,200,alpha(onHeader,.72f));
        for(int i=0;i<4;i++) {
            float x=150+i*20;
            if(i<typed) circle(c,x,154,5.5f,blend(onHeader,GREEN,unlocked));
            else { p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);p.setColor(alpha(onHeader,.6f));c.drawCircle(x,154,5,p);p.setStyle(Paint.Style.FILL); }
        }
        for(int i=0;i<12;i++) {
            float x=124+(i%3)*56, y=196+(i/3)*41;
            if(i==9) { fingerprint(c,x,y,onHeader); continue; }
            if(i==11) { backspace(c,x,y,onHeader); continue; }
            float pressed=0;
            for(int j=0;j<keys.length;j++) if(keys[j]==i && t>=presses[j]) pressed=Math.max(pressed,1-clamp((t-presses[j])/260f));
            circle(c,x,y,17,alpha(onHeader,.14f+.3f*pressed));
            centeredText(c,i==10?"0":String.valueOf(i+1),x,y+5.5f,16,onHeader,false);
        }
        c.restoreToCount(save);
    }

    private void ai(Canvas c, long t) {
        gradientCircle(c,58,40,20,AVATARS[1][0],AVATARS[1][1]);
        sparkle(c,58,40,10*(1+.08f*(float)Math.sin(t/260.0)),0xffffffff);
        text(c,"Nebula AI",88,37,17,ink,true);
        boolean typing = t>=500 && t<1600;
        fitText(c,typing?t("печатает…","typing…"):t("бот","bot"),88,56,12,200,typing?accent:muted,false);
        float out=1-span(t,5200,5600);
        int save=clipCard(c,30,72,330,344,22,wallTop,wallBottom);
        round(c,124,88,316,146,16,alpha(outBubble,out));
        fitText(c,t("Объясни это проще","Make this easier to read"),138,110,13,162,alpha(outText,out),false);
        fitText(c,t("и выдели главное","and highlight the key points"),138,128,13,124,alpha(outText,out),false);
        rightText(c,"16:41",292,139,9,alpha(outText,.6f*out),false);
        checks(c,296,131,alpha(outCheck,out),.65f);
        if(typing) {
            round(c,44,158,110,188,15,inBubble);
            for(int i=0;i<3;i++) circle(c,63+i*14,173,3.5f,alpha(muted,dots(t,i)));
        }
        float reply=span(t,1600,1850)*out;
        if(reply>0) {
            float lift=(1-reply)*10;
            round(c,44,158+lift,292,272+lift,16,alpha(inBubble,reply));
            sparkle(c,62,177+lift,6,alpha(accent,reply));
            fitText(c,t("Конечно, вот коротко:","Sure, here's the short version:"),74,182+lift,13,206,alpha(ink,reply),true);
            String[] points={t("Самая важная мысль","The key idea"),t("Что можно сделать дальше","What to do next")};
            for(int i=0;i<2;i++) {
                float shown=span(t,2000+i*400,2250+i*400)*out;
                float y=212+i*28+lift+(1-shown)*6;
                circle(c,64,y-4,3.5f,alpha(accent,shown));
                fitText(c,points[i],76,y,12,200,alpha(ink,shown),false);
            }
            rightText(c,"16:41",282,266+lift,9,alpha(muted,reply),false);
        }
        // Кнопки под ответом бота, как встроенная клавиатура Telegram.
        float keys=span(t,2800,3050)*out;
        if(keys>0) {
            String[] labels={t("Перевести","Translate"),t("Кратко","Summary"),t("Ответить","Reply")};
            for(int i=0;i<3;i++) {
                float l=44+i*84, y=282+(1-keys)*8;
                round(c,l,y,l+80,y+30,10,alpha(dark?0x66ffffff:0x40000000,keys));
                fitCentered(c,labels[i],l+40,y+19.5f,11,70,alpha(0xffffffff,keys));
            }
        }
        c.restoreToCount(save);
    }

    private void link(Canvas c, long t) {
        int save=clipCard(c,46,14,314,340,28,card,card);
        text(c,"NebulaLink",70,52,18,ink,true);
        // Расходящиеся волны: соединение живое.
        for(int i=0;i<2;i++) {
            float wave=((t+i*1200)%2400)/2400f;
            circle(c,180,128,42+wave*30,alpha(accent,.22f*(1-wave)));
        }
        circle(c,180,128,42,tint);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(accent);
        r.set(162,110,198,146);c.drawArc(r,-55,290,false,p);c.drawLine(180,102,180,126,p);
        p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.BUTT);
        p.setTypeface(AndroidUtilities.bold());p.setTextSize(14);
        String status=t("Подключено","Connected");
        float statusWidth=p.measureText(status)+20;
        float blink=.55f+.45f*(float)Math.cos(t/2400.0*Math.PI*2);
        circle(c,180-statusWidth/2+4,197,4.5f,alpha(GREEN,blink));
        text(c,status,180-statusWidth/2+16,202,14,GREEN,true);
        round(c,66,226,294,284,18,raised);
        flag(c,94,255);
        fitText(c,t("Финляндия","Finland"),116,253,13,92,ink,true);
        fitText(c,"Helsinki · VLESS",116,270,10,92,muted,false);
        ping(c,216,241,280,269,t("89 мс","89 ms"),t);
        String change=t("Сменить сервер","Change server");
        p.setTypeface(AndroidUtilities.bold());p.setTextSize(12);
        float width=p.measureText(change);
        text(c,change,180-(width+12)/2,316,12,accent,true);
        chevron(c,180+(width+12)/2-3,312,accent);
        c.restoreToCount(save);
        outline(c,46,14,314,340,28);
    }

    /** Яркость точки «печатает» с волной слева направо. */
    private static float dots(long t, int i) { return .35f + .65f * (.5f + .5f * (float) Math.sin(t / 170.0 - i * .9)); }
    private void typing(Canvas c,float x,float baseline,long t,float alpha){
        String label=t("печатает","typing");
        fitText(c,label,x,baseline,12,120,alpha(accent,alpha),false);
        p.setTextSize(12);p.setTypeface(Typeface.DEFAULT);
        float end=x+p.measureText(label)+5;
        for(int i=0;i<3;i++) circle(c,end+i*6,baseline-3.5f,1.8f,alpha(accent,alpha*dots(t,i)));
    }
    private int clipCard(Canvas c,float l,float top,float rr,float b,float rad,int first,int last){
        int save=c.save();clip.reset();r.set(l,top,rr,b);clip.addRoundRect(r,rad,rad,Path.Direction.CW);c.clipPath(clip);
        p.setStyle(Paint.Style.FILL);p.setShader(shade(l,top,rr,b,first,last));c.drawRect(r,p);p.setShader(null);
        return save;
    }
    private void outline(Canvas c,float l,float top,float rr,float b,float rad){r.set(l,top,rr,b);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.8f);p.setColor(edge);c.drawRoundRect(r,rad,rad,p);p.setStyle(Paint.Style.FILL);}
    private void round(Canvas c,float l,float top,float rr,float b,float rad,int color){r.set(l,top,rr,b);p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawRoundRect(r,rad,rad,p);}
    private void gradientRound(Canvas c,float l,float top,float rr,float b,float rad,int first,int last){r.set(l,top,rr,b);p.setStyle(Paint.Style.FILL);p.setShader(shade(l,top,rr,b,first,last));c.drawRoundRect(r,rad,rad,p);p.setShader(null);}
    private void gradientCircle(Canvas c,float x,float y,float radius,int first,int last){p.setStyle(Paint.Style.FILL);p.setShader(shade(x,y-radius,x,y+radius,first,last));c.drawCircle(x,y,radius,p);p.setShader(null);}
    private void badge(Canvas c,float x,float y,String value,float scale){
        circle(c,x,y,10*scale,accent);
        if(scale>.6f) centeredText(c,value,x,y+3.8f*scale,11*scale,0xffffffff,true);
    }
    private void checks(Canvas c,float x,float y,int color,float size){
        stroke(color,1.6f);
        for(int i=0;i<2;i++){float o=i*5*size;c.drawLine(x+o,y+4*size,x+o+3*size,y+7*size,p);c.drawLine(x+o+3*size,y+7*size,x+o+9*size,y,p);}
        fill();
    }
    private void ring(Canvas c,float x,float y,float radius,int color){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.4f);p.setColor(color);c.drawCircle(x,y,radius,p);p.setStyle(Paint.Style.FILL);}
    private void fitText(Canvas c,String s,float x,float y,float size,float available,int color,boolean bold){p.setTypeface(bold?AndroidUtilities.bold():Typeface.DEFAULT);p.setTextSize(size);float measured=p.measureText(s);text(c,s,x,y,measured>available?size*available/measured:size,color,bold);}
    private void fitCentered(Canvas c,String s,float centerX,float y,float size,float available,int color){p.setTypeface(Typeface.DEFAULT);p.setTextSize(size);float measured=p.measureText(s);centeredText(c,s,centerX,y,measured>available?size*available/measured:size,color,false);}
    private void circle(Canvas c,float x,float y,float radius,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawCircle(x,y,radius,p);}
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);p.setTypeface(bold?AndroidUtilities.bold():Typeface.DEFAULT);c.drawText(s,x,y,p);}
    private void rightText(Canvas c,String s,float right,float baseline,float size,int color,boolean bold){p.setTypeface(bold?AndroidUtilities.bold():Typeface.DEFAULT);p.setTextSize(size);text(c,s,right-p.measureText(s),baseline,size,color,bold);}
    private void divider(Canvas c,float sx,float sy,float ex,float ey){p.setColor(edge);p.setStrokeWidth(.8f);p.setStyle(Paint.Style.STROKE);c.drawLine(sx,sy,ex,ey,p);p.setStyle(Paint.Style.FILL);}
    private void centeredText(Canvas c,String s,float centerX,float baseline,float size,int color,boolean bold){p.setTypeface(bold?AndroidUtilities.bold():Typeface.DEFAULT);p.setTextSize(size);text(c,s,centerX-p.measureText(s)*.5f,baseline,size,color,bold);}
    private void stroke(int color,float width){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(color);}
    private void fill(){p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.BUTT);}
    private void search(Canvas c,float x,float y,int color){stroke(color,2);c.drawCircle(x-2,y-2,6,p);c.drawLine(x+2.5f,y+2.5f,x+7,y+7,p);fill();}
    /** Замок; lift от 0 до 1 приподнимает дужку — код принят. */
    private void lock(Canvas c,float x,float y,int color,float lift){
        float up=lift*6;
        stroke(color,3);r.set(x-6.5f,y-13-up,x+6.5f,y+1-up);c.drawArc(r,180,180,false,p);
        c.drawLine(x-6.5f,y-6-up,x-6.5f,y-2-up*(1-lift),p);c.drawLine(x+6.5f,y-6-up,x+6.5f,y-2,p);
        fill();p.setColor(color);r.set(x-10,y-3,x+10,y+12);c.drawRoundRect(r,4,4,p);
    }
    /**
     * Отпечаток пальца: вложенные гребни-арки с «ножками» разной длины и
     * короткой линией в центре — как значок сканера в Android.
     */
    private void fingerprint(Canvas c,float x,float y,int color){
        stroke(color,1.6f);
        float cy=y-2;
        float[] radius={3.2f,6.4f,9.6f};
        float[] left={7,9,6};
        float[] right={10,6,9};
        c.drawLine(x,cy+1,x,cy+7,p);
        for(int i=0;i<radius.length;i++){
            float rad=radius[i];
            r.set(x-rad,cy-rad,x+rad,cy+rad);c.drawArc(r,180,180,false,p);
            c.drawLine(x-rad,cy,x-rad,cy+left[i],p);
            c.drawLine(x+rad,cy,x+rad,cy+right[i],p);
        }
        r.set(x-12.8f,cy-12.8f,x+12.8f,cy+12.8f);c.drawArc(r,205,130,false,p);
        fill();
    }
    private void backspace(Canvas c,float x,float y,int color){
        stroke(color,1.8f);path.reset();path.moveTo(x-11,y);path.lineTo(x-5,y-7);path.lineTo(x+10,y-7);path.lineTo(x+10,y+7);path.lineTo(x-5,y+7);path.close();c.drawPath(path,p);
        c.drawLine(x-1,y-3,x+5,y+3,p);c.drawLine(x+5,y-3,x-1,y+3,p);fill();
    }
    private void sparkle(Canvas c,float x,float y,float size,int color){
        float waist=size*.28f;path.reset();path.moveTo(x,y-size);path.quadTo(x+waist,y-waist,x+size,y);path.quadTo(x+waist,y+waist,x,y+size);path.quadTo(x-waist,y+waist,x-size,y);path.quadTo(x-waist,y-waist,x,y-size);path.close();
        fill();p.setColor(color);c.drawPath(path,p);
    }
    private void chevron(Canvas c,float x,float y,int color){stroke(color,2);c.drawLine(x-3,y-5,x+2,y,p);c.drawLine(x+2,y,x-3,y+5,p);fill();}
    /** Аватарка Telegram: градиентный круг с инициалами, у «Избранного» — закладка. */
    private void avatar(Canvas c,float x,float y,int type,String letters) {
        gradientCircle(c,x,y,22,AVATARS[type][0],AVATARS[type][1]);
        if(type==0) {
            p.setColor(0xffffffff);path.reset();path.moveTo(x-7,y-10);path.lineTo(x+7,y-10);path.lineTo(x+7,y+10);path.lineTo(x,y+4.5f);path.lineTo(x-7,y+10);path.close();c.drawPath(path,p);
        } else {
            centeredText(c,letters,x,y+5.5f,15,0xffffffff,true);
        }
    }
    private void flag(Canvas c,float x,float y) {
        int save=c.save();clip.reset();clip.addCircle(x,y,12,Path.Direction.CW);c.clipPath(clip);
        p.setColor(0xfff1f5fb);c.drawCircle(x,y,12,p);
        p.setColor(0xff3976c7);r.set(x-5,y-12,x-1,y+12);c.drawRect(r,p);r.set(x-12,y-2,x+12,y+2);c.drawRect(r,p);
        c.restoreToCount(save);
    }
    private void ping(Canvas c,float l,float top,float rr,float bottom,String label,long t) {
        r.set(l,top,rr,bottom);p.setColor(tint);c.drawRoundRect(r,(bottom-top)/2,(bottom-top)/2,p);
        p.setTypeface(AndroidUtilities.bold());p.setTextSize(10);
        Paint.FontMetrics metrics=p.getFontMetrics();
        float baseline=(top+bottom)*.5f-(metrics.ascent+metrics.descent)*.5f;
        float labelWidth=p.measureText(label), barsWidth=12, gap=5;
        float start=(l+rr-(barsWidth+gap+labelWidth))*.5f;
        float[] heights={3,6,9,12};
        for(int i=0;i<heights.length;i++){
            // Полоски по очереди вспыхивают, как замер задержки.
            float glow=.55f+.45f*clamp(1-Math.abs(((t%2400)/2400f)*5-1-i));
            stroke(alpha(accent,glow),2.2f);
            c.drawLine(start+1+i*3.6f,baseline+1,start+1+i*3.6f,baseline+1-heights[i],p);
        }
        fill();text(c,label,start+barsWidth+gap,baseline,10,accent,true);
    }
}
