import app.nebulagram.ui.*;
import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LocalAudioCheck {
    static int checks;
    static void check(boolean yes,String detail){checks++;if(!yes)throw new AssertionError(detail);}
    interface Work {void run()throws Exception;}
    static void rejects(Work work)throws Exception{try{work.run();throw new AssertionError("Invalid audio accepted");}catch(IOException|IllegalArgumentException expected){checks++;}}
    static byte[] pcm(int rate,int channels,boolean floating, int frames){ByteBuffer b=ByteBuffer.allocate(frames*channels*(floating?4:2)).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<frames;i++)for(int j=0;j<channels;j++){float v=(float)Math.sin(2*Math.PI*440*i/rate);if(floating)b.putFloat(v*.5f);else b.putShort((short)(v*16000));}return b.array();}
    static byte[] convert(byte[] pcm,int rate,int channels,boolean floating,int chunk)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();NebulaPcmResampler r=new NebulaPcmResampler(rate,channels,floating,out);for(int i=0;i<pcm.length;i+=chunk)r.accept(ByteBuffer.wrap(pcm,i,Math.min(chunk,pcm.length-i)));r.finish();return out.toByteArray();}
    static class Clock implements NebulaPcmFeeder.Clock {long time;public long now(){return time;}public void pause(long nanos){time+=nanos;}}
    public static void main(String[] args)throws Exception{
        for(int rate:new int[]{8000,16000,44100,48000})for(int channels:new int[]{1,2})for(boolean floating:new boolean[]{false,true}){
            byte[] data=pcm(rate,channels,floating,rate/10);byte[] one=convert(data,rate,channels,floating,data.length);
            for(int chunk:new int[]{1,3,127,8192})check(Arrays.equals(one,convert(data,rate,channels,floating,chunk)),"Resampler preserves chunk boundaries");
            check(one.length==3200,"100 ms produces 1600 mono PCM16 samples");
            check(!(one[0]=='R'&&one[1]=='I'&&one[2]=='F'&&one[3]=='F'),"Headerless input");
        }
        ByteBuffer stereo=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);stereo.putShort((short)16000).putShort((short)-8000).putShort((short)12000).putShort((short)4000);
        byte[] mono=convert(stereo.array(),16000,2,false,3);check(ByteBuffer.wrap(mono).order(ByteOrder.LITTLE_ENDIAN).getShort()==4000,"Stereo mixed, no one-channel discard");
        rejects(()->convert(new byte[]{0},16000,1,false,1));rejects(()->new NebulaPcmResampler(1,1,false,new ByteArrayOutputStream()));
        rejects(()->convert(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(Float.NaN).array(),16000,1,true,4));
        NebulaPcmResampler capped=new NebulaPcmResampler(8000,1,false,OutputStream.nullOutputStream());byte[] second=new byte[16000];for(int i=0;i<600;i++)capped.accept(ByteBuffer.wrap(second));rejects(()->capped.accept(ByteBuffer.wrap(new byte[2])));
        byte[] bytes=pcm(16000,1,false,2000);Clock clock=new Clock();ByteArrayOutputStream output=new ByteArrayOutputStream();ArrayList<Long> writes=new ArrayList<>();
        OutputStream paced=new OutputStream(){public void write(int b){}public void write(byte[] b,int start,int size){writes.add(clock.time);output.write(b,start,size);if(writes.size()==1)clock.time+=700_000_000L;}};
        NebulaPcmFeeder.write(new ByteArrayInputStream(bytes),paced,()->false,clock);
        check(Arrays.equals(bytes,output.toByteArray()),"Paced input byte-for-byte identical");check(writes.get(0)==20_000_000L,"First 20 ms of audio paced");check(writes.get(1)==740_000_000L,"No catch-up burst after blocked writer");
        check(clock.time==825_000_000L,"125 ms PCM plus writer delay, including short final packet");
        Clock cancelledClock=new Clock();AtomicBoolean cancelled=new AtomicBoolean();ByteArrayOutputStream cancelledOut=new ByteArrayOutputStream();NebulaPcmFeeder.Clock stop=new NebulaPcmFeeder.Clock(){public long now(){return cancelledClock.time;}public void pause(long nanos){cancelledClock.time+=nanos;cancelled.set(true);}};
        rejects(()->NebulaPcmFeeder.write(new ByteArrayInputStream(bytes),cancelledOut,cancelled::get,stop));check(cancelledOut.size()==0,"Cancelled data not emitted");
        rejects(()->NebulaPcmFeeder.write(new ByteArrayInputStream(new byte[]{1}),output,()->false,clock));
        NebulaLocalTranscript transcript=new NebulaLocalTranscript();check(transcript.partial("hel").equals("hel"),"Partial output");check(transcript.partial("hello").equals("hello"),"Partial replaced rather than duplicated");check(transcript.segment("hello").equals("hello"),"Final segment committed");transcript.partial("wor");check(transcript.result().equals("hello"),"Partial never enters final result");transcript.segment("world");check(transcript.result().equals("hello\nworld"),"Final segments retained");rejects(()->new NebulaLocalTranscript().result());
        check(NebulaLocalAudioPolicy.advancedDevice("Google","Pixel 10 Pro XL",36),"Supported Nano audio device");check(NebulaLocalAudioPolicy.advancedDevice("Google","Pixel 11",37),"Supported next generation");check(!NebulaLocalAudioPolicy.advancedDevice("Google","Pixel 9 Pro",36),"Text Nano support is not audio support");check(!NebulaLocalAudioPolicy.advancedDevice("Samsung","Pixel 10",36),"No model-name-only spoofing");check(!NebulaLocalAudioPolicy.advancedDevice("Google","Pixel 10a",36),"No undocumented model family");
        check(NebulaLocalAudioPolicy.locale("ru").equals("ru-RU"),"Russian recognition locale");check(NebulaLocalAudioPolicy.locale("pt_PT").equals("pt-BR"),"Supported regional default");check(NebulaLocalAudioPolicy.playbackNanos(32000)==1_000_000_000L,"Real-time rate");
        System.out.println("OK: "+checks+" local PCM, pacing, cancellation, transcript and mode assertions");
    }
}
