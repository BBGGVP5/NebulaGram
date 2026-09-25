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
    private final Path leader = new Path();
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
        int maxWidth = MeasureSpec.getSize(w);
        int maxHeight = AndroidUtilities.dp(340 * NebulaLoginStyle.vertical());
        // Measure the view to the drawing canvas, instead of forcing a narrow
        // canvas into the full text column and shrinking the art inside it.
        int width = Math.min(maxWidth, Math.round(maxHeight * (360f / 340f)));
        int height = Math.round(width * (340f / 360f));
        setMeasuredDimension(resolveSize(width, w), resolveSize(height, h));
    }
    private String t(String ru, String en) { return NebulaText.text(ru, en); }
    @Override protected void onDraw(Canvas c) {
        float scale = Math.min(getWidth()/360f, getHeight()/340f);
        c.save(); c.translate((getWidth()-360*scale)/2, (getHeight()-340*scale)/2); c.scale(scale, scale);
        p.setShader(new RadialGradient(180,195,180,0x334d60db,0x004d60db,Shader.TileMode.CLAMP));
        c.drawCircle(180,195,180,p); p.setShader(null);
        if(page==0) welcome(c); else if(page==1) design(c); else if(page==2) privacy(c); else if(page==3) ai(c); else link(c);
        c.restore();
    }
    private void welcome(Canvas c) {
        annotation(c,70,5,290,39,"✦",t("Твои разговоры","Your conversations"),178,55);
        card(c,38,54,322,324,24);
        text(c,"NebulaGram",58,84,18,ink,true);
        pill(c,56,98,110,122,t("Все","All"),cyan);
        text(c,t("Личные","Personal"),128,115,11,muted,false);
        text(c,t("Работа","Work"),190,115,11,muted,false);
        String[] names={t("Избранное","Saved Messages"),t("Команда Nebula","Nebula team"),t("Аня","Anna")};
        String[] messages={t("Всё важное — здесь","Keep what matters"),t("Новая идея","A new idea"),t("Увидимся вечером?","See you tonight?")};
        String[] times={"16:40","12:30","11:58"};
        for(int i=0;i<3;i++) {
            float y=157+i*47;
            drawChatAvatar(c,75,y,i);
            fitText(c,names[i],101,y-3,12,153,ink,true);
            rightText(c,times[i],304,y-3,9,muted,false);
            fitText(c,messages[i],101,y+15,10,190,muted,false);
            if(i<2) divider(c,101,y+23,304,y+23);
        }
        sticker(c,278,274,320,322);
    }
    private void design(Canvas c) {
        annotation(c,52,5,308,39,"✦",t("Настрой под себя","Make it yours"),180,58);
        int[] colors={0xff4fc7c9,0xff7565ee,0xffdd91c4,0xff438adf,0xff365172,0xff758acf};
        String[] icons={"✦","◈","Aa","☷","N","▣"};
        for(int i=0;i<6;i++) {
            int x=50+(i%3)*91,y=61+(i/3)*84;
            gradient(c,x,y,x+78,y+74,22,colors[i],0xff19253b);
            centeredText(c,icons[i],x+39,y+48,i==2?27:35,ink,true);
        }
        annotation(c,42,247,318,287,"Aa",t("Темы, папки и иконки","Themes, folders, icons"),180,224);
    }
    private void privacy(Canvas c) {
        annotation(c,72,5,288,39,"●",t("Код и биометрия","Passcode and biometrics"),252,75);
        gradient(c,64,48,296,324,28,0xff435678,0xff202a3b);
        centeredText(c,t("Код доступа","Passcode"),180,91,16,ink,true);
        centeredText(c,"••••",180,134,28,ink,true);
        for(int i=0;i<9;i++) {
            int x=124+(i%3)*56,y=176+(i/3)*41;
            circle(c,x,y,17,0x203cd4d4);centeredText(c,""+(i+1),x,y+6,15,ink,false);
        }
        circle(c,180,299,17,0x203cd4d4);centeredText(c,"0",180,304,15,ink,false);
        circle(c,270,77,21,0xff496ddb);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(ink);
        r.set(264,64,276,78);c.drawRoundRect(r,6,6,p);p.setStyle(Paint.Style.FILL);
        r.set(260,74,280,88);c.drawRoundRect(r,4,4,p);
    }
    private void ai(Canvas c) {
        text(c,"Nebula AI",35,44,20,ink,true);
        sticker(c,238,3,345,116);
        gradient(c,62,105,325,171,19,0xff7465dc,0xff435fca);
        fitText(c,t("Объясни это проще","Make this easier to understand"),78,133,12,231,ink,true);
        text(c,t("И выдели главное","And highlight what matters"),78,154,11,ink,false);
        arrow(c,284,174,304,190,cyan);
        card(c,26,193,318,304,21);text(c,"Nebula AI",44,220,13,cyan,true);
        fitText(c,t("Конечно. Вот краткий ответ:","Of course. Here is a short answer:"),44,245,11,256,ink,false);
        text(c,t("1. Самая важная мысль","1. The key idea"),44,268,11,ink,false);
        text(c,t("2. Что можно сделать дальше","2. What you can do next"),44,288,11,ink,false);
    }
    private void link(Canvas c) {
        card(c,49,16,311,320,24);text(c,"NebulaLink",70,52,20,ink,true);
        circle(c,180,111,32,0xff304f62);
        // Draw the power symbol ourselves so it is independent of emoji fonts.
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(cyan);
        r.set(164,95,196,127);c.drawArc(r,-50,280,false,p);c.drawLine(180,89,180,111,p);
        p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.BUTT);
        centeredText(c,t("Подключено","Connected"),180,159,14,cyan,true);
        arrow(c,180,168,180,188,cyan);
        card(c,66,190,294,257,14);
        drawFinlandFlag(c,94,223);
        fitText(c,t("Финляндия","Finland"),112,228,13,98,ink,true);
        pingBadge(c,211,204,282,242,t("89 мс","89 ms"));
        centeredText(c,t("Сервер можно сменить","Change servers anytime"),180,286,11,muted,false);
    }
    private void sticker(Canvas c,float l,float t,float rr,float b){if(astronaut!=null){p.setColor(-1);float scale=Math.min((rr-l)/astronaut.getWidth(),(b-t)/astronaut.getHeight());float w=astronaut.getWidth()*scale,h=astronaut.getHeight()*scale;r.set((l+rr-w)/2,(t+b-h)/2,(l+rr+w)/2,(t+b+h)/2);c.drawBitmap(astronaut,null,r,p);}}
    private void card(Canvas c,float l,float t,float rr,float b,float rad){gradient(c,l,t,rr,b,rad,0xff2c384b,0xff192332);}
    private void gradient(Canvas c,float l,float t,float rr,float b,float rad,int a,int z){r.set(l,t,rr,b);p.setShader(new LinearGradient(l,t,rr,b,a,z,Shader.TileMode.CLAMP));p.setStyle(Paint.Style.FILL);c.drawRoundRect(r,rad,rad,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.8f);p.setColor(0x30ffffff);c.drawRoundRect(r,rad,rad,p);p.setStyle(Paint.Style.FILL);}
    private void ribbon(Canvas c,float l,float t,float rr,float b,String icon,String label){card(c,l,t,rr,b,19);circle(c,l+24,(t+b)/2,14,0xff496ddb);centeredText(c,icon,l+24,(t+b)/2+5,15,ink,true);fitText(c,label,l+48,(t+b)/2+5,13,rr-l-62,ink,true);}
    private void fitText(Canvas c,String s,float x,float y,float size,float available,int color,boolean bold){p.setTypeface(bold?AndroidUtilities.bold():Typeface.DEFAULT);p.setTextSize(size);float measured=p.measureText(s);text(c,s,x,y,measured>available?size*available/measured:size,color,bold);}
    private void pill(Canvas c,float l,float t,float rr,float b,String text,int color){gradient(c,l,t,rr,b,12,0xff385b6b,0xff2e4256);centeredText(c,text,(l+rr)*.5f,b-7,10,color,true);}
    private void circle(Canvas c,float x,float y,float radius,int color){p.setColor(color);c.drawCircle(x,y,radius,p);}
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){p.setColor(color);p.setTextSize(size);p.setTypeface(bold?AndroidUtilities.bold():Typeface.DEFAULT);c.drawText(s,x,y,p);}
    private void rightText(Canvas c,String s,float right,float baseline,float size,int color,boolean bold){p.setTypeface(bold?AndroidUtilities.bold():Typeface.DEFAULT);p.setTextSize(size);text(c,s,right-p.measureText(s),baseline,size,color,bold);}
    private void divider(Canvas c,float sx,float sy,float ex,float ey){p.setColor(0x273a4a60);p.setStrokeWidth(.8f);p.setStyle(Paint.Style.STROKE);c.drawLine(sx,sy,ex,ey,p);p.setStyle(Paint.Style.FILL);}
    private void centeredText(Canvas c,String s,float centerX,float baseline,float size,int color,boolean bold){p.setTypeface(bold?AndroidUtilities.bold():Typeface.DEFAULT);p.setTextSize(size);text(c,s,centerX-p.measureText(s)*.5f,baseline,size,color,bold);}
    private void drawChatAvatar(Canvas c,float x,float y,int type) {
        int save=c.save();Path clip=new Path();clip.addCircle(x,y,15,Path.Direction.CW);c.clipPath(clip);
        int bg=type==0?0xff5d70df:type==1?0xff27afb3:0xffd48ea9; p.setColor(bg);c.drawCircle(x,y,15,p);
        if(type==0) {
            p.setColor(0xffffd66e);c.drawCircle(x,y-3,5,p);r.set(x-8,y+2,x+8,y+15);c.drawRoundRect(r,7,7,p);
            p.setColor(0xfff5f3ff);p.setStrokeWidth(1.5f);p.setStyle(Paint.Style.STROKE);c.drawLine(x-5,y+1,x+5,y+1,p);p.setStyle(Paint.Style.FILL);
        } else if(type==1) {
            p.setColor(0xffd9fbf5);c.drawCircle(x-5,y-2,4,p);c.drawCircle(x+5,y-2,4,p);
            r.set(x-12,y+3,x+1,y+13);c.drawRoundRect(r,5,5,p);r.set(x-1,y+3,x+12,y+13);c.drawRoundRect(r,5,5,p);
            p.setColor(0xff7885f1);c.drawCircle(x,y-7,3,p);
        } else {
            p.setColor(0xffffe0bf);c.drawCircle(x,y-2,6,p);r.set(x-9,y+4,x+9,y+17);c.drawRoundRect(r,8,8,p);
            p.setColor(0xff744c67);r.set(x-7,y-9,x+7,y-1);c.drawOval(r,p);c.drawCircle(x-6,y-3,3,p);c.drawCircle(x+6,y-3,3,p);
        }
        c.restoreToCount(save);
    }
    private void drawFinlandFlag(Canvas c,float x,float y) {
        r.set(x-11,y-9,x+11,y+9);p.setColor(0xfff1f5fb);c.drawRoundRect(r,4,4,p);
        p.setColor(0xff3976c7);r.set(x-5,y-9,x-1,y+9);c.drawRect(r,p);r.set(x-11,y-2,x+11,y+2);c.drawRect(r,p);
    }
    private void pingBadge(Canvas c,float l,float top,float rr,float bottom,String label) {
        gradient(c,l,top,rr,bottom,11,0xff304b62,0xff26394f);
        p.setTypeface(AndroidUtilities.bold());p.setTextSize(10);
        Paint.FontMetrics metrics=p.getFontMetrics();
        float baseline=(top+bottom)*.5f-(metrics.ascent+metrics.descent)*.5f;
        float labelWidth=p.measureText(label), barsWidth=14, gap=5;
        float groupWidth=barsWidth+gap+labelWidth;
        float start=(l+rr-groupWidth)*.5f;
        float x=start+1;
        p.setColor(cyan);p.setStrokeWidth(2.2f);p.setStrokeCap(Paint.Cap.ROUND);
        float[] heights={4,7,10,13};for(int i=0;i<heights.length;i++)c.drawLine(x+i*4,baseline+1,x+i*4,baseline+1-heights[i],p);
        p.setStrokeCap(Paint.Cap.BUTT);text(c,label,start+barsWidth+gap,baseline,10,cyan,true);
    }
    private void annotation(Canvas c,float l,float top,float rr,float bottom,String icon,String label,float targetX,float targetY) {
        arrow(c,(l+rr)*.5f,bottom,targetX,targetY,cyan);
        gradient(c,l,top,rr,bottom,(bottom-top)*.48f,0xff29384f,0xff202c40);
        circle(c,l+19,(top+bottom)*.5f,12,0xff496ddb);
        centeredText(c,icon,l+19,(top+bottom)*.5f+4,12,ink,true);
        fitText(c,label,l+39,(top+bottom)*.5f+4,12,rr-l-50,ink,true);
    }
    private void arrow(Canvas c,float sx,float sy,float ex,float ey,int color) {
        float dx=ex-sx,dy=ey-sy;
        float cx=sx+dx*.48f-dy*.12f,cy=sy+dy*.48f+dx*.12f;
        leader.reset();leader.moveTo(sx,sy);
        if(Math.abs(dx)<6) leader.lineTo(ex,ey); else leader.quadTo(cx,cy,ex,ey);
        p.setColor(color);p.setAlpha(210);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);c.drawPath(leader,p);
        float angle=(float)Math.atan2(dy,dx),size=6;
        leader.reset();leader.moveTo(ex,ey);
        leader.lineTo(ex-size*(float)Math.cos(angle-.48f),ey-size*(float)Math.sin(angle-.48f));
        leader.lineTo(ex-size*(float)Math.cos(angle+.48f),ey-size*(float)Math.sin(angle+.48f));
        leader.close();p.setStyle(Paint.Style.FILL);p.setAlpha(220);c.drawPath(leader,p);p.setAlpha(255);p.setStrokeCap(Paint.Cap.BUTT);
    }
}
