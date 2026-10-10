package app.nebulagram.ui;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Streaming mono PCM16 at 16 kHz. State persists across codec output buffers. */
public final class NebulaPcmResampler {
    private final int rate, channels, sampleBytes;
    private final boolean floating;
    private final OutputStream out;
    private final byte[] pending, output = new byte[4096];
    private final ByteBuffer frameBuffer;
    private int pendingCount, outputCount;
    private long frames, nextTime, samples;
    private int previous;
    private boolean finished;
    public NebulaPcmResampler(int rate, int channels, boolean floating, OutputStream out) throws IOException {
        if (rate < 8000 || rate > 192000 || channels < 1 || channels > 8) throw new IOException("Unsupported decoded audio format");
        this.rate=rate;this.channels=channels;this.floating=floating;this.sampleBytes=floating?4:2;this.out=out;pending=new byte[channels*sampleBytes];frameBuffer=ByteBuffer.wrap(pending).order(ByteOrder.LITTLE_ENDIAN);
    }
    public void accept(ByteBuffer input) throws IOException {
        if (finished) throw new IOException("PCM stream already complete");
        while (input.hasRemaining()) {
            int size=Math.min(input.remaining(),pending.length-pendingCount);input.get(pending,pendingCount,size);pendingCount+=size;
            if(pendingCount==pending.length){frame();pendingCount=0;}
        }
    }
    private void frame() throws IOException {
        if(frames >= (long)rate * NebulaLocalAudioPolicy.MAX_SECONDS)throw new IOException("LOCAL_AUDIO_DURATION_LIMIT");
        ByteBuffer buffer=frameBuffer;buffer.position(0);long mix=0;
        for(int channel=0;channel<channels;channel++){
            if(floating){float value=buffer.getFloat();if(!Float.isFinite(value))throw new IOException("Invalid PCM float");mix+=Math.round(Math.max(-1,Math.min(1,value))*32767);}
            else mix+=buffer.getShort();
        }
        int current=(int)(mix/channels);long currentTime=frames*NebulaLocalAudioPolicy.SAMPLE_RATE;
        while(nextTime<=currentTime){
            long fraction=frames==0?0:nextTime-(frames-1)*NebulaLocalAudioPolicy.SAMPLE_RATE;
            int value=frames==0?current:(int)(previous+(current-previous)*fraction/(long)NebulaLocalAudioPolicy.SAMPLE_RATE);
            write(value);nextTime+=rate;
        }
        previous=current;frames++;
    }
    private void write(int sample) throws IOException {
        if(samples>= (long)NebulaLocalAudioPolicy.SAMPLE_RATE*NebulaLocalAudioPolicy.MAX_SECONDS)throw new IOException("LOCAL_AUDIO_DURATION_LIMIT");
        output[outputCount++]=(byte)sample;output[outputCount++]=(byte)(sample>>8);samples++;
        if(outputCount==output.length){out.write(output,0,outputCount);outputCount=0;}
    }
    public void finish() throws IOException {
        if(finished)return;
        if(pendingCount!=0)throw new IOException("Truncated PCM frame");
        while(nextTime<frames*NebulaLocalAudioPolicy.SAMPLE_RATE){write(previous);nextTime+=rate;}
        if(outputCount>0){out.write(output,0,outputCount);outputCount=0;}finished=true;
        if(samples==0)throw new IOException("No decoded audio");
    }
    public long samples(){return samples;}
}
