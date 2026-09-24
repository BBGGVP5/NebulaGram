import app.nebulagram.ui.NebulaChatLockStore;
import app.nebulagram.ui.NebulaSyncDocument;
import java.nio.file.*;
import java.util.*;

public class PrivateFeaturesCheck {
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args) throws Exception {
        String a="11111111-1111-1111-1111-111111111111",b="22222222-2222-2222-2222-222222222222";
        Map<String,Object> all=new HashMap<>();all.put("material_you",true);all.put("ai_api_key","secret");all.put("xray_subscription","secret");
        Map<String,Object> safe=NebulaSyncDocument.portable(all);
        check(safe.size()==1 && safe.containsKey("material_you"),"portable allowlist");
        NebulaSyncDocument first=new NebulaSyncDocument(a,Collections.singletonMap(a,1L),safe);
        NebulaSyncDocument second=NebulaSyncDocument.parse(first.encode());
        check(second.settings.equals(safe),"roundtrip");
        check(!first.encode().contains("secret"),"credentials excluded");
        NebulaSyncDocument concurrent=new NebulaSyncDocument(b,Collections.singletonMap(b,1L),Collections.singletonMap("material_you",false));
        check(NebulaSyncDocument.heads(Arrays.asList(first,concurrent)).size()==2,"concurrent heads");
        Map<String,Long> clock=NebulaSyncDocument.mergedClock(Arrays.asList(first,concurrent));clock.put(a,2L);
        NebulaSyncDocument merged=new NebulaSyncDocument(a,clock,safe);
        check(merged.dominates(first)&&merged.dominates(concurrent)&&NebulaSyncDocument.heads(Arrays.asList(first,concurrent,merged)).size()==1,"merge clock");
        try{new NebulaSyncDocument(a,Collections.singletonMap(a,1L),all);throw new AssertionError("bad key accepted");}catch(IllegalArgumentException expected){}
        Path dir=Files.createTempDirectory("chat-lock-test");
        NebulaChatLockStore store=new NebulaChatLockStore(dir.toFile());
        char[] password="correct horse".toCharArray();
        store.set(100,42,new char[0],password);
        check(store.protectedChat(100,42)&&!store.protectedChat(100,43)&&!store.protectedChat(101,42),"scoped chat");
        check(!Files.readString(dir.resolve("nebula-chat-locks-100.json")).contains("correct horse"),"hashed at rest");
        check(new NebulaChatLockStore(dir.toFile()).verify(100,42,password,1000),"persisted password");
        for(int n=0;n<5;n++)check(!store.verify(100,42,"wrong".toCharArray(),2000+n),"wrong password");
        check(store.remaining(100,42,2005)>0 && !store.verify(100,42,password,2005),"rate limit");
        check(store.verify(100,42,password,32005),"rate limit ends");
        store.remove(100,42,password);
        check(!store.protectedChat(100,42),"remove");
        System.out.println("private feature model checks passed");
    }
}
