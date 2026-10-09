import app.nebulagram.ui.NebulaSavedTagStore;
import org.json.*;

public final class SavedTagsCheck {
    static int checks;
    static void check(boolean yes,String detail){checks++;if(!yes)throw new AssertionError(detail);}
    interface Work {void run()throws Exception;}
    static void rejects(Work work)throws Exception{try{work.run();throw new AssertionError("Invalid labels accepted");}catch(java.io.IOException|IllegalArgumentException|JSONException e){checks++;}}
    public static void main(String[] args)throws Exception{
        NebulaSavedTagStore store=NebulaSavedTagStore.decode("");String work=store.create(" Работа "),personal=store.create("Личное");
        store.toggle(42,work);store.toggle(42,personal);store.toggle(7,work);
        check(store.messages(work).toString().equals("[42, 7]"),"newest message IDs first");
        check(store.assigned(42,personal),"assignment");
        rejects(()->store.create("работа"));rejects(()->store.create("\n"));rejects(()->store.create("bad\u0000name"));
        String composed=store.create("Cafe\u0301");rejects(()->store.create("Café"));
        store.rename(composed,"Кофе");check(store.labels().get(2).name.equals("Кофе"),"rename normalization");
        NebulaSavedTagStore reopened=NebulaSavedTagStore.decode(store.encode());check(reopened.assigned(42,personal),"round trip");
        reopened.remove(work);check(reopened.messages(work).isEmpty()&&reopened.assigned(42,personal),"remove label only");
        reopened.toggle(42,personal);check(!reopened.assigned(42,personal),"toggle removes link");
        rejects(()->store.toggle(0,work));rejects(()->store.toggle(42,"missing"));rejects(()->store.rename(personal,"Работа"));
        check(!NebulaSavedTagStore.scope(1).equals(NebulaSavedTagStore.scope(2)),"account user ID isolation");rejects(()->NebulaSavedTagStore.scope(0));
        String raw=store.encode();check(!raw.contains("account")&&!raw.contains("text")&&!raw.contains("media"),"metadata only");
        JSONObject damaged=new JSONObject(raw);damaged.getJSONObject("messages").put("-1",new JSONArray().put(work));rejects(()->NebulaSavedTagStore.decode(damaged.toString()));
        rejects(()->NebulaSavedTagStore.decode("not-json"));
        NebulaSavedTagStore limit=NebulaSavedTagStore.decode("");String[] labels=new String[9];for(int i=0;i<9;i++)labels[i]=limit.create("label "+i);for(int i=0;i<8;i++)limit.toggle(1,labels[i]);rejects(()->limit.toggle(1,labels[8]));
        for(int i=9;i<64;i++)limit.create("label "+i);rejects(()->limit.create("one too many"));
        System.out.println("OK: "+checks+" local Saved label assertions");
    }
}
