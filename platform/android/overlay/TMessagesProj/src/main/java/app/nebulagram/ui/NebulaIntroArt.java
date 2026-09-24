package app.nebulagram.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

/** Localized product previews with an original illustrated Nebula mascot. */
public final class NebulaIntroArt extends View {
    private final Paint p = new Paint(3);
    private final RectF r = new RectF();
    private final Bitmap astronaut;
    private int page;
    private final int ink = 0xfff2f5ff, muted = 0xffa9b8cd, cyan = 0xff57dfe0;
    public NebulaIntroArt(Context context) {
        super(context);
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 2;
        astronaut = BitmapFactory.decodeResource(getResources(), R.drawable.nebula_intro_astronaut, options);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void setPage(int value) { page = value; invalidate(); }
    @Override protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        int height = Math.min(Math.round(width * 1.06f), AndroidUtilities.dp(360 * NebulaLoginStyle.vertical()));
        setMeasuredDimension(width, height);
    }
    private String t(String ru, String en) { return NebulaText.text(ru, en); }
    @Override protected void onDraw(Canvas c) {
        float scale = Math.min(getWidth()/360f, getHeight()/380f);
        c.save(); c.translate((getWidth()-360*scale)/2, (getHeight()-380*scale)/2); c.scale(scale, scale);
        p.setShader(new RadialGradient(180,195,180,0x334d60db,0x004d60db,Shader.TileMode.CLAMP));
        c.drawCircle(180,195,180,p); p.setShader(null);
        if(page==0) welcome(c); else if(page==1) design(c); else if(page==2) privacy(c); else if(page==3) ai(c); else link(c);
        c.restore();
    }
    private void welcome(Canvas c) {
        c.save(); c.rotate(-5,180,190);
        card(c,67,44,296,338,24);
        text(c,"NebulaGram",85,74,17,ink,true);
        pill(c,81,87,133,109,t("Все","All"),cyan);
        text(c,t("Личные   Работа","Personal   Work"),143,102,10,muted,false);
        String[] names={t("Избранное","Saved Messages"),t("Команда Nebula","Nebula team"),t("Аня","Anna"),t("Планы на выходные","Weekend plans")};
        String[] messages={t("Всё важное — здесь","Keep what matters"),t("Новая идея ✨","A new idea ✨"),t("Увидимся вечером?","See you tonight?"),t("Фото и хорошие новости","Photos and good news")};
        for(int i=0;i<4;i++) { float y=140+i*46; circle(c,95,y,13,i%2==0?0xff667ced:0xff32b7bd);text(c,names[i],117,y-3,10,ink,true);text(c,messages[i],117,y+12,8,muted,false); }
        card(c,91,302,271,329,14); text(c,"●                 ✦                 ◉",115,321,12,cyan,false);
        c.restore();
        sticker(c,190,154,361,374);
        ribbon(c,15,20,325,64,-3,"✦",t("Добро пожаловать домой","Welcome to your space"));
    }
    private void design(Canvas c) {
        int[] colors={0xff4fc7c9,0xff7565ee,0xffdd91c4,0xff438adf,0xff365172,0xff758acf};
        for(int i=0;i<6;i++) { int x=37+(i%3)*100,y=86+(i/3)*106;c.save();c.rotate((i%2==0?-9:8),x+40,y+40); gradient(c,x,y,x+78,y+78,22,colors[i],0xff19253b);text(c,i==4?"N":"✦",x+23,y+54,40,ink,true);c.restore(); }
        ribbon(c,18,30,301,76,-4,"✦",t("Твой стиль общения","Your style of chatting"));
        ribbon(c,29,290,343,333,4,"◉",t("Папки, темы и иконки","Folders, themes, icons"));
        ribbon(c,53,340,303,375,-3,"Aa",t("Всё под рукой","Make it yours"));
    }
    private void privacy(Canvas c) {
        gradient(c,70,47,294,350,28,0xff435678,0xff202a3b);
        text(c,"••••",145,117,30,ink,true);
        for(int i=0;i<9;i++){int x=114+(i%3)*65,y=174+(i/3)*48;circle(c,x,y,19,0x203cd4d4);text(c,""+(i+1),x-5,y+6,17,ink,false);}
        ribbon(c,13,18,298,61,-4,"◇",t("Личное остаётся личным","Your space stays yours"));
        ribbon(c,32,132,328,176,5,"●",t("Код и биометрия","Passcode and biometrics"));
        ribbon(c,17,303,312,349,-5,"✦",t("Контроль приватности","Privacy controls"));
    }
    private void ai(Canvas c) {
        sticker(c,215,10,356,193);
        ribbon(c,18,25,255,69,-4,"✦","Nebula AI");
        gradient(c,46,116,317,182,19,0xff7465dc,0xff435fca);
        text(c,t("Объясни это проще","Make this easier to understand"),62,143,12,ink,true);
        text(c,t("И выдели главное","And highlight what matters"),62,164,11,ink,false);
        card(c,21,202,315,302,21);text(c,"Nebula AI",39,228,13,cyan,true);
        text(c,t("Конечно. Вот краткий ответ:","Of course. Here is a short answer:"),39,251,11,ink,false);
        text(c,t("1. Самая важная мысль","1. The key idea"),39,272,11,ink,false);
        text(c,t("2. Что можно сделать дальше","2. What you can do next"),39,289,11,ink,false);
        ribbon(c,38,327,336,371,3,"✦",t("Твоя модель. Твой выбор.","Your model. Your choice."));
    }
    private void link(Canvas c) {
        card(c,46,76,317,330,24);text(c,"NebulaLink",66,107,20,ink,true);
        circle(c,181,170,36,0xff304f62);text(c,"⏻",157,184,42,cyan,false);
        text(c,t("Подключено","Connected"),135,229,14,cyan,true);
        card(c,65,245,299,283,13);text(c,t("Финляндия","Finland"),79,269,12,ink,true);text(c,"89 ms",245,269,11,cyan,false);
        ribbon(c,14,22,329,67,-4,"◇",t("Подключение внутри","Connection built in"));
        ribbon(c,20,318,342,363,3,"↗",t("Выбирай свой сервер","Choose your server"));
    }
    private void sticker(Canvas c,float l,float t,float rr,float b){if(astronaut!=null){p.setColor(-1);float scale=Math.min((rr-l)/astronaut.getWidth(),(b-t)/astronaut.getHeight());float w=astronaut.getWidth()*scale,h=astronaut.getHeight()*scale;r.set((l+rr-w)/2,(t+b-h)/2,(l+rr+w)/2,(t+b+h)/2);c.drawBitmap(astronaut,null,r,p);}}
    private void card(Canvas c,float l,float t,float rr,float b,float rad){gradient(c,l,t,rr,b,rad,0xff2c384b,0xff192332);}
    private void gradient(Canvas c,float l,float t,float rr,float b,float rad,int a,int z){r.set(l,t,rr,b);p.setShader(new LinearGradient(l,t,rr,b,a,z,Shader.TileMode.CLAMP));p.setStyle(Paint.Style.FILL);c.drawRoundRect(r,rad,rad,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.8f);p.setColor(0x30ffffff);c.drawRoundRect(r,rad,rad,p);p.setStyle(Paint.Style.FILL);}
    private void ribbon(Canvas c,float l,float t,float rr,float b,float angle,String icon,String label){c.save();c.rotate(angle,(l+rr)/2,(t+b)/2);card(c,l,t,rr,b,19);circle(c,l+22,(t+b)/2,13,0xff496ddb);text(c,icon,l+14,(t+b)/2+5,15,ink,true);text(c,label,l+43,(t+b)/2+5,13,ink,true);c.restore();}
    private void pill(Canvas c,float l,float t,float rr,float b,String text,int color){gradient(c,l,t,rr,b,12,0xff385b6b,0xff2e4256);text(c,text,l+12,b-6,10,color,true);}
    private void circle(Canvas c,float x,float y,float radius,int color){p.setColor(color);c.drawCircle(x,y,radius,p);}
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){p.setColor(color);p.setTextSize(size);p.setTypeface(bold?AndroidUtilities.bold():Typeface.DEFAULT);c.drawText(s,x,y,p);}
}
