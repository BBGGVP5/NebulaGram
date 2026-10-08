package app.nebulagram.ui;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Size;
import java.util.*;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

/** Camera metadata is queried on page/session setup, never while drawing frames. */
public final class NebulaCameraCapabilities {
    public final Set<String> effects = new HashSet<>();
    public final SortedSet<Integer> qualities = new TreeSet<>();
    public boolean available, dual, wide;
    private NebulaCameraCapabilities() { }
    public static boolean contains(int[] values, int value) {
        if (values != null) for (int candidate : values) if (candidate == value) return true;
        return false;
    }
    public static boolean supports(CameraCharacteristics c, String effect) {
        if (c == null) return false;
        switch (effect) {
            case "ois": return contains(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION), 1);
            case "eis": return contains(c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES), 1);
            case "focus": return contains(c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES), CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            case "noise": return contains(c.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES), CaptureRequest.NOISE_REDUCTION_MODE_FAST);
            case "faces": return contains(c.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES), CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE)
                    || contains(c.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES), CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL);
            case "bokeh":
                if (Build.VERSION.SDK_INT >= 30) {
                    android.hardware.camera2.params.Capability[] modes = c.get(CameraCharacteristics.CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_CAPABILITIES);
                    if (modes != null) for (android.hardware.camera2.params.Capability mode : modes)
                        if (mode.getMode() == CaptureRequest.CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS) return true;
                }
        }
        return false;
    }
    public static NebulaCameraCapabilities inspect() {
        NebulaCameraCapabilities result = new NebulaCameraCapabilities();
        try {
            CameraManager manager = (CameraManager) ApplicationLoader.applicationContext.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) return result;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                StreamConfigurationMap streams = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] sizes = streams == null ? null : streams.getOutputSizes(SurfaceTexture.class);
                if (sizes == null || sizes.length == 0) continue;
                result.available = true;
                for (String effect : new String[]{"ois", "eis", "focus", "noise", "faces", "bokeh"}) if (supports(c, effect)) result.effects.add(effect);
                for (Size size : sizes) {
                    int height = Math.min(size.getWidth(), size.getHeight());
                    for (int quality : new int[]{480,720,1080,2160}) if (height >= quality) result.qualities.add(quality);
                }
            }
            if (Build.VERSION.SDK_INT >= 30) for (Set<String> set : manager.getConcurrentCameraIds()) {
                boolean front = false, rear = false;
                for (String id : set) {
                    Integer facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                    front |= facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
                    rear |= facing != null && facing == CameraCharacteristics.LENS_FACING_BACK;
                }
                result.dual |= front && rear;
            }
            NebulaZoomCapabilities zoom = NebulaCameraZoom.discover(false);
            result.wide = zoom != null && zoom.minimum < .99f;
        } catch (Exception error) { FileLog.e(error); }
        return result;
    }
    public static final class Options {
        public final Map<CaptureRequest.Key<?>, Object> values = new LinkedHashMap<>();
        public Options(CameraCharacteristics c) {
            this(c, null);
        }
        public Options(CameraCharacteristics c, Size captureSize) {
            if (c == null || !NebulaCameraSettings.enabled("enhancements")) return;
            List<CaptureRequest.Key<?>> keys = c.getAvailableCaptureRequestKeys();
            if (keys == null) return;
            int stabilization = NebulaCameraPolicy.stabilization(NebulaCameraSettings.effect("ois"), NebulaCameraSettings.effect("eis"), supports(c,"ois"), supports(c,"eis"));
            if (supports(c,"ois")) put(keys,CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,stabilization == 1 ? 1 : 0);
            if (supports(c,"eis")) put(keys,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,stabilization == 2 ? 1 : 0);
            if (supports(c,"focus")) {
                int mode = NebulaCameraSettings.effect("focus") ? CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO : CaptureRequest.CONTROL_AF_MODE_AUTO;
                if (contains(c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES), mode)) put(keys,CaptureRequest.CONTROL_AF_MODE,mode);
            }
            if (supports(c,"noise")) {
                int mode = NebulaCameraSettings.effect("noise") ? CaptureRequest.NOISE_REDUCTION_MODE_FAST : CaptureRequest.NOISE_REDUCTION_MODE_OFF;
                if (contains(c.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES),mode)) put(keys,CaptureRequest.NOISE_REDUCTION_MODE,mode);
            }
            if (supports(c,"faces")) {
                int mode = !NebulaCameraSettings.effect("faces") ? 0 : contains(c.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES),1) ? 1 : 2;
                put(keys,CaptureRequest.STATISTICS_FACE_DETECT_MODE,mode);
            }
            boolean bokehFits = false;
            if (Build.VERSION.SDK_INT >= 30 && captureSize != null) {
                android.hardware.camera2.params.Capability[] modes = c.get(CameraCharacteristics.CONTROL_AVAILABLE_EXTENDED_SCENE_MODE_CAPABILITIES);
                if (modes != null) for (android.hardware.camera2.params.Capability mode : modes) {
                    Size max = mode.getMaxStreamingSize();
                    if (mode.getMode() == CaptureRequest.CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS && max != null
                            && captureSize.getWidth() <= max.getWidth() && captureSize.getHeight() <= max.getHeight()) bokehFits = true;
                }
            }
            if (Build.VERSION.SDK_INT >= 30 && bokehFits) put(keys,CaptureRequest.CONTROL_EXTENDED_SCENE_MODE,
                NebulaCameraSettings.effect("bokeh") ? CaptureRequest.CONTROL_EXTENDED_SCENE_MODE_BOKEH_CONTINUOUS : CaptureRequest.CONTROL_EXTENDED_SCENE_MODE_DISABLED);
        }
        private <T> void put(List<CaptureRequest.Key<?>> keys, CaptureRequest.Key<T> key, T value) { if (keys.contains(key)) values.put(key,value); }
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void apply(CaptureRequest.Builder builder) {
            for (Map.Entry<CaptureRequest.Key<?>, Object> entry : values.entrySet()) {
                try { builder.set((CaptureRequest.Key) entry.getKey(), entry.getValue()); }
                catch (IllegalArgumentException error) { FileLog.e(error); }
            }
        }
    }
}
