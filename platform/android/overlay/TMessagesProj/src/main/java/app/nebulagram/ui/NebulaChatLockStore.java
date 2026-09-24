package app.nebulagram.ui;

import java.util.*;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.json.JSONObject;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Local access-lock records. No plaintext passwords, exported settings or account slot IDs. */
public final class NebulaChatLockStore {
    private final File directory;
    private final Map<Long,JSONObject> cache=new HashMap<>();
    public NebulaChatLockStore(File directory){this.directory=directory;}
    private AtomicFile file(long user){return new AtomicFile(new File(directory,"nebula-chat-locks-"+user+".json"));}
    private JSONObject data(long user) throws Exception {
        if(user<=0)throw new IllegalArgumentException("account");
        JSONObject d=cache.get(user);
        if(d==null){AtomicFile f=file(user);d=f.getBaseFile().exists()?new JSONObject(new String(f.readFully(),StandardCharsets.UTF_8)):new JSONObject();cache.put(user,d);}
        return d;
    }
    private void save(long user,JSONObject next) throws Exception {
        AtomicFile f=file(user);FileOutputStream out=null;
        try{out=f.startWrite();out.write(next.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(out);cache.put(user,next);}
        catch(Exception e){if(out!=null)f.failWrite(out);throw e;}
    }
    public synchronized boolean protectedChat(long user,long dialog) {
        if(user<=0 || dialog==0)return false;
        try{return data(user).has(Long.toString(dialog));}catch(Exception e){return true;}
    }
    public synchronized List<Long> dialogs(long user) throws Exception {
        ArrayList<Long> result=new ArrayList<>();Iterator<String> it=data(user).keys();while(it.hasNext())result.add(Long.parseLong(it.next()));return result;
    }
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    private static byte[] bytes(String hex) {
        if(hex.length()%2!=0 || !hex.matches("[a-f0-9]+"))throw new IllegalArgumentException("hash");
        byte[] b=new byte[hex.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);return b;
    }
    private static byte[] derive(char[] password,byte[] salt) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(password,salt,210000,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
    }
    public synchronized long remaining(long user,long dialog,long now) throws Exception {
        JSONObject d=data(user).optJSONObject(Long.toString(dialog));if(d==null)return 0;
        return Math.max(0,Math.min(30000,d.optLong("until",0)-now));
    }
    public boolean verify(long user,long dialog,char[] password,long now) throws Exception {
        JSONObject d;
        synchronized(this){JSONObject stored=data(user).optJSONObject(Long.toString(dialog));
            if(stored==null || remaining(user,dialog,now)>0)return false;
            d=new JSONObject(stored.toString());}
        if(d.getInt("version")!=1)throw new IOException("version");
        byte[] salt=bytes(d.getString("salt")),expected=bytes(d.getString("hash"));
        if(salt.length!=32 || expected.length!=32)throw new IOException("record");
        boolean ok=MessageDigest.isEqual(expected,derive(password,salt));
        synchronized(this) {
            JSONObject next=new JSONObject(data(user).toString());JSONObject record=next.optJSONObject(Long.toString(dialog));
            if(record==null || !d.getString("hash").equals(record.optString("hash")))return false;
            int attempts=ok?0:record.optInt("attempts",0)+1;
            record.put("attempts",attempts>=5?0:attempts).put("until",attempts>=5?now+30000:0);
            save(user,next);return ok;
        }
    }
    public void set(long user,long dialog,char[] oldPassword,char[] password) throws Exception {
        if(dialog==0 || password.length<6 || password.length>128)throw new IllegalArgumentException("password length");
        if(protectedChat(user,dialog) && !verify(user,dialog,oldPassword,System.currentTimeMillis()))throw new SecurityException("password");
        byte[] salt=new byte[32];new SecureRandom().nextBytes(salt);
        JSONObject record=new JSONObject().put("version",1).put("salt",hex(salt)).put("hash",hex(derive(password,salt)));
        synchronized(this){JSONObject next=new JSONObject(data(user).toString());next.put(Long.toString(dialog),record);save(user,next);}
    }
    public void remove(long user,long dialog,char[] password) throws Exception {
        if(!verify(user,dialog,password,System.currentTimeMillis()))throw new SecurityException("password");
        synchronized(this){JSONObject next=new JSONObject(data(user).toString());next.remove(Long.toString(dialog));save(user,next);}
    }
}
