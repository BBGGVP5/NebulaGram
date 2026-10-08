package app.nebulagram.ui;

import android.hardware.Camera;
import android.util.Size;
import java.util.List;

/** Requested framing is applied only to ordinary capture, never square round messages or stories. */
public final class NebulaCameraCapture {
    private NebulaCameraCapture() { }
    public static float aspect(float fallback) {
        switch (NebulaCameraSettings.aspect()) { case 1: return 4f/3; case 2: return 16f/9; case 3: return 1f; default: return fallback; }
    }
    public static Size size(Size[] supported, Size fallback, boolean framing) {
        if (supported == null || supported.length == 0) return fallback;
        int requested = NebulaCameraSettings.quality();
        if (requested == 0 && (!framing || NebulaCameraSettings.aspect() == 0)) return fallback;
        float aspect = framing ? aspect((float)fallback.getWidth()/fallback.getHeight()) : (float)fallback.getWidth()/fallback.getHeight();
        int[] heights = new int[supported.length];
        for (int i=0;i<supported.length;i++) heights[i]=Math.min(supported[i].getWidth(),supported[i].getHeight());
        int height=NebulaCameraPolicy.quality(requested==0?Math.min(fallback.getWidth(),fallback.getHeight()):requested,heights);
        Size best=fallback; double score=Double.MAX_VALUE;
        for(Size candidate:supported){
            int w=Math.max(candidate.getWidth(),candidate.getHeight()),h=Math.min(candidate.getWidth(),candidate.getHeight());
            // Never exceed the requested short side if a lower size is available.
            double value=Math.abs((double)w/h-aspect)*10000+Math.abs(h-height)+(h>height?100000:0);
            if(value<score){score=value;best=candidate;}
        }
        return best;
    }
    public static void legacy(Camera.Parameters p, float exposure) {
        if (NebulaCameraSettings.enabled("enhancements")) {
            if(p.isVideoStabilizationSupported())p.setVideoStabilization(NebulaCameraSettings.effect("eis"));
            String mode=NebulaCameraSettings.effect("focus")?Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO:Camera.Parameters.FOCUS_MODE_AUTO;
            List<String> modes=p.getSupportedFocusModes(); if(modes!=null && modes.contains(mode))p.setFocusMode(mode);
        }
        if (NebulaCameraSettings.exposure()!=0 && Float.isFinite(exposure))
            p.setExposureCompensation(Math.round(exposure*(exposure<0?-p.getMinExposureCompensation():p.getMaxExposureCompensation())));
    }
}
