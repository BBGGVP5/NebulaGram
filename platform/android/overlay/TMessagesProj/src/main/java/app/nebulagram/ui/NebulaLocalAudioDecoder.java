package app.nebulagram.ui;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.function.BooleanSupplier;

/** Decode the existing recording track only; never records microphone audio. */
public final class NebulaLocalAudioDecoder {
    private NebulaLocalAudioDecoder(){ }
    public static void decode(File source, File pcm, BooleanSupplier cancelled) throws Exception {
        if(source==null||!source.isFile()||source.length()==0||source.length()>NebulaAudioProtocol.MAX_INPUT)throw new IOException("Maximum recording size: 14 MB");
        MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
        try(FileOutputStream output=new FileOutputStream(pcm)){
            extractor.setDataSource(source.getAbsolutePath());MediaFormat selected=null;
            for(int i=0;i<extractor.getTrackCount();i++){MediaFormat format=extractor.getTrackFormat(i);String mime=format.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/")){extractor.selectTrack(i);selected=format;break;}}
            if(selected==null)throw new IOException("No audio track");
            if(selected.containsKey(MediaFormat.KEY_DURATION)&&selected.getLong(MediaFormat.KEY_DURATION)>NebulaLocalAudioPolicy.MAX_SECONDS*1_000_000L)throw new IOException("LOCAL_AUDIO_DURATION_LIMIT");
            codec=MediaCodec.createDecoderByType(selected.getString(MediaFormat.KEY_MIME));codec.configure(selected,null,null,0);codec.start();
            boolean inputDone=false,outputDone=false;NebulaPcmResampler resampler=null;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            long deadline=System.nanoTime()+120_000_000_000L;
            while(!outputDone){
                check(cancelled);if(System.nanoTime()>deadline)throw new IOException("LOCAL_AUDIO_TIMEOUT");
                if(!inputDone){int index=codec.dequeueInputBuffer(10000);if(index>=0){ByteBuffer input=codec.getInputBuffer(index);if(input==null)throw new IOException("Audio decoder input unavailable");input.clear();int count=extractor.readSampleData(input,0);if(count<0){codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}else{codec.queueInputBuffer(index,0,count,Math.max(0,extractor.getSampleTime()),0);extractor.advance();}}}
                int index=codec.dequeueOutputBuffer(info,10000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    if(resampler!=null&&resampler.samples()>0)throw new IOException("Audio format changed during decode");
                    resampler=create(codec.getOutputFormat(),output);
                }else if(index>=0){
                    try{
                        if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
                            if(resampler==null)resampler=create(codec.getOutputFormat(),output);
                            ByteBuffer decoded=codec.getOutputBuffer(index);if(decoded==null)throw new IOException("Audio decoder output unavailable");decoded.position(info.offset);decoded.limit(info.offset+info.size);resampler.accept(decoded);
                        }
                        outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    }finally{codec.releaseOutputBuffer(index,false);}
                }
            }
            if(resampler==null)throw new IOException("No decoded audio");resampler.finish();check(cancelled);
        }finally{if(codec!=null){try{codec.stop();}catch(RuntimeException ignored){}codec.release();}extractor.release();}
    }
    private static NebulaPcmResampler create(MediaFormat format,OutputStream output)throws IOException{
        int encoding=format.containsKey(MediaFormat.KEY_PCM_ENCODING)?format.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
        if(encoding!=AudioFormat.ENCODING_PCM_16BIT&&encoding!=AudioFormat.ENCODING_PCM_FLOAT)throw new IOException("Unsupported PCM encoding");
        return new NebulaPcmResampler(format.getInteger(MediaFormat.KEY_SAMPLE_RATE),format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),encoding==AudioFormat.ENCODING_PCM_FLOAT,output);
    }
    private static void check(BooleanSupplier cancelled)throws InterruptedIOException{if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");}
}
