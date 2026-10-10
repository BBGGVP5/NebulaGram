package app.nebulagram.ui;

import java.io.IOException;

/** Replace partial revisions; append stable segments; publish only final text at completion. */
public final class NebulaLocalTranscript {
    private final StringBuilder committed = new StringBuilder();
    private String partial = "";
    public synchronized String partial(String value) throws IOException { check(value);partial=value;return preview(); }
    public synchronized String segment(String value) throws IOException {
        check(value);partial="";
        if(!value.trim().isEmpty()){if(committed.length()>0)committed.append('\n');committed.append(value.trim());}
        if(committed.length()>100000)throw new IOException("Transcript too long");return preview();
    }
    private void check(String value)throws IOException{if(value==null||value.length()>100000||committed.length()+value.length()>100000)throw new IOException("Invalid transcript length");}
    public synchronized String preview(){return committed.toString()+(partial.isEmpty()?"":(committed.length()>0?"\n":"")+partial);}
    public synchronized String result() throws IOException {String result=committed.toString().trim();if(result.isEmpty())throw new IOException("LOCAL_AUDIO_NO_SPEECH");return result;}
}
