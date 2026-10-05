package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import java.util.ArrayList;

public final class NebulaSwipeSettingsFragment extends BaseFragment {
    private LinearLayout content;
    @Override public View createView(Context c){NebulaFormUi.bar(this,actionBar,c,NebulaText.text("Действия свайпа","Swipe actions"));content=NebulaFormUi.column(c);rebuild();return fragmentView=NebulaSettingsLayout.wrap(c,actionBar,NebulaFormUi.scroll(c,content),3);}
    private void rebuild(){Context c=content.getContext();content.removeAllViews();
        content.addView(NebulaFormUi.note(c,NebulaText.text("Потяните сообщение влево. Не отпуская, ведите палец вниз, чтобы выбрать следующее действие. Отпустите для выполнения.","Swipe a message left. Keep holding and move down to choose the next action. Release to perform it.")));
        ArrayList<Integer> order=new ArrayList<>();for(int action:NebulaSwipeActions.order())order.add(action);
        NebulaCard card=new NebulaCard(c);
        for(int i=0;i<order.size();i++){final int index=i;int action=order.get(i);
            card.add(new NebulaRow(c).icon(NebulaSwipeActions.icon(action)).title((i+1)+". "+NebulaSwipeActions.title(action)).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v->showDialog(new NebulaDialog.Builder(c).setTitle(NebulaSwipeActions.title(action)).setItems(new CharSequence[]{NebulaText.text("Выше","Move up"),NebulaText.text("Ниже","Move down"),NebulaText.text("Убрать","Remove")},(d,which)->{if(which==2&&order.size()>1)order.remove(index);else if(which==0&&index>0)java.util.Collections.swap(order,index,index-1);else if(which==1&&index<order.size()-1)java.util.Collections.swap(order,index,index+1);save(order);}).create())));}
        NebulaFormUi.group(content,NebulaText.text("Порядок действий","Action order"),card);
        card=new NebulaCard(c);for(int action=0;action<=3;action++)if(!order.contains(action)){final int add=action;card.add(new NebulaRow(c).icon(NebulaSwipeActions.icon(action)).title(NebulaSwipeActions.title(action)).withClick(v->{order.add(add);save(order);}));}
        if(card.getChildCount()>0)NebulaFormUi.group(content,NebulaText.text("Добавить действие","Add action"),card);
    }
    private void save(ArrayList<Integer> order){int[] values=new int[order.size()];for(int i=0;i<values.length;i++)values[i]=order.get(i);NebulaSwipeActions.set(values);rebuild();}
}
