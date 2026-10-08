package app.nebulagram.ui;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/** Choose supported PCM capture before configuring AAC, so channel count and timestamps agree. */
public final class NebulaRecordingAudio {
    private NebulaRecordingAudio() { }
    public static AudioRecord open(int sampleRate,int minimumBuffer,boolean stereo){
        return open(sampleRate,minimumBuffer,stereo,MediaRecorder.AudioSource.DEFAULT);
    }
    public static AudioRecord open(int sampleRate,int minimumBuffer,boolean stereo,int defaultSource){
        RuntimeException last=null;
        for(int channels=stereo?2:1;channels>=1;channels--){
            int mask=channels==2?AudioFormat.CHANNEL_IN_STEREO:AudioFormat.CHANNEL_IN_MONO;
            int min=AudioRecord.getMinBufferSize(sampleRate,mask,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)continue;
            int source=NebulaMessagePreferences.enabled("video_microphones",false)?MediaRecorder.AudioSource.CAMCORDER:defaultSource;
            AudioRecord record=null;
            try {
                record=new AudioRecord(source,sampleRate,mask,AudioFormat.ENCODING_PCM_16BIT,Math.max(min*4,minimumBuffer));
                if(record.getState()==AudioRecord.STATE_INITIALIZED)return record;
            }catch(RuntimeException error){last=error;}
            if(record!=null)record.release();
        }
        throw new IllegalStateException("Unable to initialize microphone",last);
    }
    public static long durationUs(long bytes,int rate,int channels){return NebulaCameraPolicy.audioDurationUs(bytes,rate,channels);}
}
