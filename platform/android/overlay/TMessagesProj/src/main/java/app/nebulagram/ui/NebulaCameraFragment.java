package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.InstantCameraViewBase;
import org.telegram.utils.camera.roundvideo.RoundVideoSession;
import org.telegram.utils.settings.SharedSettings;
import java.util.ArrayList;
import java.util.function.IntConsumer;

/** One camera destination; the illustration stays attached while controls update. */
public final class NebulaCameraFragment extends BaseFragment {
    private LinearLayout content, body;
    private NebulaCameraCapabilities capabilities;
    private NebulaCameraPreview preview;
    private boolean expanded;
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, text("Камера", "Camera"));
        content = NebulaFormUi.column(c);
        content.addView(new NebulaSettingsHero(c,"🎬",text("Камера", "Camera"),text("Фото, видео и кружки в вашем стиле", "Photos, videos and round messages your way")));
        preview = new NebulaCameraPreview(c);
        content.addView(preview);
        body = new LinearLayout(c); body.setOrientation(LinearLayout.VERTICAL); content.addView(body);
        capabilities = NebulaCameraCapabilities.inspect(); rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(c,actionBar,NebulaFormUi.scroll(c,content),-26);
    }
    private void group(String title, NebulaCard card) { NebulaFormUi.group(body,title,card); }
    private NebulaRow choice(String title, String value, Runnable click) {
        return new NebulaRow(body.getContext()).title(title).subtitle(value,true).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> click.run());
    }
    private NebulaRow toggle(String key,String ru,String en,String detailRu,String detailEn) {
        return new NebulaRow(body.getContext()).title(text(ru,en)).subtitle(text(detailRu,detailEn),false)
            .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaCameraSettings.enabled(key)).withClick(v -> {
                NebulaCameraSettings.set(key,((NebulaRow)v).toggleChecked());
                preview.refresh();
                if (key.equals("enhancements") || key.equals("ois") || key.equals("eis") || key.equals("focus") || key.equals("noise") || key.equals("faces") || key.equals("bokeh")) rebuild();
            });
    }
    private void choose(String title,String[] labels,int selected,IntConsumer apply) {
        showDialog(new NebulaDialog.Builder(getContext(),getResourceProvider()).setTitle(title).setSelectedIndex(selected)
            .setItems(labels,(dialog,index) -> { apply.accept(index); rebuild(); }).setNegativeButton(text("Отмена","Cancel"),null).create());
    }
    private void rebuild() {
        Context c = body.getContext(); body.removeAllViews();
        preview.refresh();
        String[] engines = {text("Автоматически · Telegram","Automatic · Telegram"),text("Telegram · совместимый","Telegram · compatibility"),"Camera2","CameraX",text("Системная камера","System camera")};
        NebulaCard capture = new NebulaCard(c);
        capture.add(choice(text("Тип камеры","Camera type"),engines[NebulaCameraSettings.backend()],() -> choose(text("Тип камеры","Camera type"),engines,NebulaCameraSettings.backend(),i -> {
            NebulaCameraSettings.set("backend",i);
            if (i == NebulaCameraSettings.LEGACY || i == NebulaCameraSettings.CAMERAX) InstantCameraViewBase.setUseCamera2Implementation(false);
        })));
        String[] aspects = {text("По умолчанию","Default"),"4:3","16:9","1:1"};
        capture.add(choice(text("Соотношение сторон","Aspect ratio"),aspects[NebulaCameraSettings.aspect()],() -> choose(text("Соотношение сторон","Aspect ratio"),aspects,NebulaCameraSettings.aspect(),i -> NebulaCameraSettings.set("aspect",i))));
        ArrayList<Integer> sizes = new ArrayList<>(); sizes.add(0); sizes.addAll(capabilities.qualities);
        String[] labels = new String[sizes.size()]; for(int i=0;i<labels.length;i++) labels[i]=sizes.get(i)==0?text("По умолчанию","Default"):sizes.get(i)+"p";
        int requested = NebulaCameraSettings.quality();
        capture.add(choice(text("Качество камеры","Capture quality"),requested==0?labels[0]:requested+"p",() -> choose(text("Качество камеры","Capture quality"),labels,Math.max(0,sizes.indexOf(requested)),i -> NebulaCameraSettings.set("quality",sizes.get(i)))));
        group(text("Съёмка","Capture"),capture);
        body.addView(NebulaFormUi.note(c,text("Применяется при следующем открытии камеры. Недоступное разрешение заменяется ближайшим поддерживаемым.","Applies the next time the camera opens. Unavailable resolution falls back to a supported size.")));

        NebulaCard round = new NebulaCard(c);
        boolean modern = InstantCameraViewBase.isUsingCamera2Implementation();
        round.add(new NebulaRow(c).title(text("Новый движок кружков","New round-video engine"))
            .subtitle(text("Camera2 · запись, пауза и монтаж Telegram","Camera2 · Telegram recording, pause and trimming"),false)
            .trailing(NebulaRow.TRAIL_SWITCH).checked(modern).withClick(v -> {
                boolean enabled=((NebulaRow)v).toggleChecked(); InstantCameraViewBase.setUseCamera2Implementation(enabled);
                if(enabled && (NebulaCameraSettings.backend()==1 || NebulaCameraSettings.backend()==3)) NebulaCameraSettings.set("backend",2);
                rebuild();
            }));
        NebulaRow dual=toggle("dual","Две камеры одновременно","Concurrent cameras","Быстрее переключать стороны; расходует больше энергии","Faster facing changes; uses more power");
        if(modern || !capabilities.dual) { dual.setEnabled(false); dual.setAlpha(.55f); dual.subtitle(text(modern?"Совместимый движок · при поддержке устройства":"Устройство не сообщает поддержку двух камер",modern?"Compatibility engine · supported devices only":"Device does not report concurrent camera support"),false); }
        round.add(dual);
        String[] facing={text("Как в прошлый раз","Last used"),text("Фронтальная","Front"),text("Основная","Rear"),text("Спрашивать","Ask")};
        round.add(choice(text("Начальная камера кружка","Initial round-video camera"),NebulaRoundCamera.title(),() -> choose(text("Начальная камера кружка","Initial round-video camera"),facing,NebulaRoundCamera.mode(),NebulaRoundCamera::setMode)));
        NebulaRow wide=toggle("ultrawide","Начинать с широкоугольной","Start with ultrawide","Для основной камеры, если объектив доступен","Rear camera, when the lens is available");
        if(!capabilities.wide){wide.setEnabled(false);wide.setAlpha(.55f);} round.add(wide);
        round.add(toggle("switch_blur","Размытие при переключении","Blur camera switches","Плавный переход сохраняется в записанном кружке","The smooth transition is retained in the recorded video"));
        String[] output={text("По умолчанию","Default"),"360 × 360","480 × 480"};
        round.add(choice(text("Размер кружка","Round-video size"),output[NebulaCameraSettings.number("round_size",0,0,2)],() -> choose(text("Размер кружка","Round-video size"),output,NebulaCameraSettings.number("round_size",0,0,2),i -> {
            NebulaCameraSettings.set("round_size",i);
            SharedSettings.roundVideoOutputResolution.set(i==1?RoundVideoSession.OutputResolution.P360:RoundVideoSession.OutputResolution.P480);
        })));
        if(modern) round.add(choice(text("Частота кадров","Frame rate"),SharedSettings.roundVideoFrameRate.get().getValue()+" fps",() -> choose(text("Частота кадров","Frame rate"),new String[]{"30 fps","60 fps"},SharedSettings.roundVideoFrameRate.get()==RoundVideoSession.FrameRate.FPS_60?1:0,i -> SharedSettings.roundVideoFrameRate.set(i==1?RoundVideoSession.FrameRate.FPS_60:RoundVideoSession.FrameRate.FPS_30))));
        group(text("Видеосообщения","Round messages"),round);

        NebulaCard effects=new NebulaCard(c);
        effects.add(toggle("enhancements","Улучшения камеры","Camera enhancements","Выбранные параметры вместо настроек движка","Use the selected parameters instead of engine defaults"));
        int count=0; for(String key:new String[]{"ois","eis","focus","noise","faces","bokeh"}) if(NebulaCameraSettings.enabled(key))count++;
        effects.add(choice(text("Выбрать улучшения","Choose enhancements"),count+" / 6",() -> { expanded=!expanded;rebuild(); }));
        if(expanded){
            String[][] names={{"ois","Оптическая стабилизация · OIS","Optical stabilization · OIS"},{"eis","Видеостабилизация · EIS","Video stabilization · EIS"},{"focus","Непрерывный автофокус","Continuous autofocus"},{"noise","Шумоподавление","Noise reduction"},{"faces","Обнаружение лиц","Face detection"},{"bokeh","Размытие фона","Background blur"}};
            for(String[] name:names){
                boolean supported=capabilities.effects.contains(name[0]);
                boolean legacy=NebulaCameraSettings.backend()==NebulaCameraSettings.LEGACY;
                if(legacy && (name[0].equals("ois")||name[0].equals("noise")||name[0].equals("faces")||name[0].equals("bokeh")))supported=false;
                NebulaRow row=toggle(name[0],name[1],name[2],supported?"На поддерживаемых объективах":"Недоступно для этой камеры",supported?"On supported lenses":"Unavailable for this camera");
                if(!supported){row.setEnabled(false);row.setAlpha(.55f);} effects.add(row);
            }
        }
        group(text("Обработка","Processing"),effects);
        body.addView(NebulaFormUi.note(c,text("Поддержка зависит от объектива и режима съёмки. Если выбраны OIS и EIS, используется OIS. Размытие фона доступно только при аппаратной поддержке видеорежима.","Support depends on the lens and capture mode. When OIS and EIS are selected, OIS takes priority. Background blur requires hardware support for video.")));
        NebulaCard controls=new NebulaCard(c);
        String[] positions={text("Скрыт","Hidden"),text("Снизу","Bottom"),text("Слева","Left"),text("Справа","Right")};
        controls.add(choice(text("Ползунок экспозиции","Exposure slider"),positions[NebulaCameraSettings.exposure()],() -> choose(text("Ползунок экспозиции","Exposure slider"),positions,NebulaCameraSettings.exposure(),i -> NebulaCameraSettings.set("exposure",i))));
        controls.add(toggle("center_controls","Центрировать управление","Center camera controls","Расположение переключения камер и вспышки","Position of the facing and flash controls"));
        group(text("Управление","Controls"),controls);
    }
}
