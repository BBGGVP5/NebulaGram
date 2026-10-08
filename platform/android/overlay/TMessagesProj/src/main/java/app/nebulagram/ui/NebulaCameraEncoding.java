package app.nebulagram.ui;

import android.media.MediaCodecInfo;
import android.util.Size;

/** Capture capability does not imply encoder capability. Preserve framing when scaling down. */
public final class NebulaCameraEncoding {
    private NebulaCameraEncoding() { }
    public static Size size(MediaCodecInfo.VideoCapabilities caps,int width,int height,int fps) {
        if(caps==null)return new Size(width,height);
        int ax=Math.max(1,caps.getWidthAlignment()),ay=Math.max(1,caps.getHeightAlignment());
        for(double scale=1;scale>.02;scale*=.9){
            int w=Math.max(ax,(int)(width*scale)/ax*ax),h=Math.max(ay,(int)(height*scale)/ay*ay);
            if(caps.areSizeAndRateSupported(w,h,fps))return new Size(w,h);
        }
        throw new IllegalStateException("No supported camera encoder resolution");
    }
    public static int bitrate(MediaCodecInfo.VideoCapabilities caps,int width,int height,int fallback){
        int requested=(int)Math.max(fallback,Math.min(24_000_000L,(long)width*height*3));
        return caps==null?requested:caps.getBitrateRange().clamp(requested);
    }
}
