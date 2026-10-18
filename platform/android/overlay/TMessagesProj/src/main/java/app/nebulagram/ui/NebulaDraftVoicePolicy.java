package app.nebulagram.ui;

import java.io.*;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** A transcript belongs to one version and trim region of one unsent recording. */
public final class NebulaDraftVoicePolicy {
    private NebulaDraftVoicePolicy() { }
    public static final class Snapshot {
        public final int account, guid;
        public final long owner, dialog, revision, size, modified, leftMs, rightMs;
        public final String path, service;
        public Snapshot(int account, long owner, long dialog, int guid, long revision, File file, long leftMs, long rightMs, String service) {
            this.account=account;this.owner=owner;this.dialog=dialog;this.guid=guid;this.revision=revision;
            path=file.getAbsolutePath();size=file.length();modified=file.lastModified();
            this.leftMs=leftMs;this.rightMs=rightMs;this.service=service;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Snapshot)) return false;
            Snapshot s=(Snapshot)other;
            return account==s.account && owner==s.owner && dialog==s.dialog && guid==s.guid && revision==s.revision
                    && size==s.size && modified==s.modified && leftMs==s.leftMs && rightMs==s.rightMs
                    && path.equals(s.path) && Objects.equals(service,s.service);
        }
        @Override public int hashCode() { return Objects.hash(account,owner,dialog,guid,revision,path,size,modified,leftMs,rightMs,service); }
    }
    public static final class Gate {
        private int generation;
        private Snapshot source;
        private boolean running;
        public int begin(Snapshot source) { this.source=source;running=true;return ++generation; }
        public boolean accepts(int token, Snapshot current) { return running && token==generation && source!=null && source.equals(current); }
        public boolean complete(int token, Snapshot current) { if(!accepts(token,current))return false;running=false;return true; }
        public void cancel() { ++generation;running=false; }
    }
    public static long[] region(long pcmBytes, long leftMs, long rightMs) throws IOException {
        if(pcmBytes<=0 || (pcmBytes&1)!=0)throw new IOException("No decoded voice audio");
        long durationMs=pcmBytes/32;
        long left=Math.max(0,Math.min(durationMs,leftMs));
        long right=rightMs<=0?durationMs:Math.max(0,Math.min(durationMs,rightMs));
        if(right<=left)throw new IOException("Empty voice fragment");
        return new long[]{left*32,Math.min(pcmBytes,right*32)};
    }
    public static void wave(File pcm, File target, long leftMs, long rightMs, int maxBytes, BooleanSupplier cancelled) throws IOException {
        writeRegion(pcm, target, leftMs, rightMs, maxBytes, cancelled, true);
    }
    public static void pcm(File source, File target, long leftMs, long rightMs, int maxBytes, BooleanSupplier cancelled) throws IOException {
        writeRegion(source, target, leftMs, rightMs, maxBytes, cancelled, false);
    }
    private static void writeRegion(File pcm, File target, long leftMs, long rightMs, int maxBytes, BooleanSupplier cancelled, boolean wave) throws IOException {
        long[] range=region(pcm.length(),leftMs,rightMs);
        long length=range[1]-range[0];
        if(length+(wave?44:0)>maxBytes)throw new IOException("DRAFT_VOICE_TOO_LONG");
        try(RandomAccessFile input=new RandomAccessFile(pcm,"r");OutputStream out=new FileOutputStream(target)) {
            if(wave) {
            out.write(new byte[]{'R','I','F','F'});little(out,length+36,4);out.write(new byte[]{'W','A','V','E','f','m','t',' '});
            little(out,16,4);little(out,1,2);little(out,1,2);little(out,16000,4);little(out,32000,4);little(out,2,2);little(out,16,2);
            out.write(new byte[]{'d','a','t','a'});little(out,length,4);
            }
            input.seek(range[0]);
            byte[] buffer=new byte[8192];long remaining=length;
            while(remaining>0) {
                if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");
                int n=input.read(buffer,0,(int)Math.min(remaining,buffer.length));if(n<0)throw new EOFException();
                out.write(buffer,0,n);remaining-=n;
            }
        }
    }
    private static void little(OutputStream out,long value,int size)throws IOException { for(int i=0;i<size;i++)out.write((int)(value>>(8*i))&255); }
}
