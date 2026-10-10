package app.nebulagram.ui;

import java.io.*;
import java.util.function.BooleanSupplier;
import java.util.concurrent.locks.LockSupport;

/** Pace PCM on completion of each write; blocked pipes never cause a catch-up burst. */
public final class NebulaPcmFeeder {
    public interface Clock { long now(); void pause(long nanos) throws InterruptedIOException; }
    private NebulaPcmFeeder(){ }
    public static void write(InputStream input, OutputStream output, BooleanSupplier cancelled) throws IOException {
        write(input,output,cancelled,new Clock(){public long now(){return System.nanoTime();}public void pause(long nanos){LockSupport.parkNanos(Math.min(10_000_000L,nanos));}});
    }
    public static void write(InputStream input, OutputStream output, BooleanSupplier cancelled, Clock clock) throws IOException {
        byte[] chunk=new byte[640];long last=clock.now(),total=0;
        while(true){
            check(cancelled);int size=0;
            while(size<chunk.length){int n=input.read(chunk,size,chunk.length-size);if(n<0)break;if(n==0)continue;size+=n;check(cancelled);}
            if(size==0)return;
            total+=size;if(size%2!=0||total>(long)NebulaLocalAudioPolicy.BYTES_PER_SECOND*NebulaLocalAudioPolicy.MAX_SECONDS)throw new IOException("Invalid PCM pipe stream");
            long target=last+NebulaLocalAudioPolicy.playbackNanos(size);
            while(clock.now()<target){check(cancelled);clock.pause(target-clock.now());}
            check(cancelled);output.write(chunk,0,size);last=clock.now();
        }
    }
    private static void check(BooleanSupplier cancelled)throws InterruptedIOException{if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");}
}
