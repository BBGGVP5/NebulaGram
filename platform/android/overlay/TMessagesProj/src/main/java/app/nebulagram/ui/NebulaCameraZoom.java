package app.nebulagram.ui;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Range;
import android.util.SizeF;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import java.util.ArrayList;
import java.util.List;

/** Only advertises cameras that Android exposes with a recording-compatible preview stream. */
public final class NebulaCameraZoom {
    private static final class Camera {
        String id;
        CameraCharacteristics info;
        float focal, min = 1f, max = 1f;
        boolean logical, ratio;
    }

    public static NebulaZoomCapabilities discover(boolean front) {
        if (Build.VERSION.SDK_INT < 21) return null;
        CameraManager manager = (CameraManager) ApplicationLoader.applicationContext.getSystemService(Context.CAMERA_SERVICE);
        List<Camera> cameras = new ArrayList<>();
        try {
            for (String id : manager.getCameraIdList()) {
                try {
                    CameraCharacteristics info = manager.getCameraCharacteristics(id);
                    Integer facing = info.get(CameraCharacteristics.LENS_FACING);
                    if (facing == null || facing != (front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK)) continue;
                    StreamConfigurationMap streams = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (streams == null || streams.getOutputSizes(SurfaceTexture.class) == null
                            || streams.getOutputSizes(SurfaceTexture.class).length == 0) continue;
                    if (!streams.isOutputSupportedFor(android.graphics.ImageFormat.JPEG)) continue;
                    Camera camera = new Camera(); camera.id = id; camera.info = info;
                    camera.focal = focal(info);
                    Float digital = info.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                    if (digital != null && digital >= 1f && !Float.isInfinite(digital)) camera.max = digital;
                    if (Build.VERSION.SDK_INT >= 30) {
                        Range<Float> range = info.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                        if (range != null && range.getLower() > 0 && range.getUpper() >= 1 && !Float.isInfinite(range.getUpper())) {
                            camera.min = range.getLower(); camera.max = range.getUpper(); camera.ratio = true;
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 28) {
                        int[] capabilities = info.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                        if (capabilities != null) for (int capability : capabilities)
                            if (capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) camera.logical = true;
                    }
                    cameras.add(camera);
                } catch (Exception error) { FileLog.e(error); }
            }
        } catch (Exception error) { FileLog.e(error); }
        if (cameras.isEmpty()) return null;
        Camera main = cameras.get(0);
        for (Camera camera : cameras) {
            if (camera.focal > 0 && (main.focal <= 0 || Math.abs(camera.focal - 26f) < Math.abs(main.focal - 26f))) main = camera;
        }
        Camera preferred = main;
        for (Camera camera : cameras) {
            if (camera.logical && camera.ratio && (!preferred.logical || !preferred.ratio
                    || camera.max / camera.min > preferred.max / preferred.min)) preferred = camera;
        }
        // Zoom-ratio 1 on the logical camera is the vendor's main-lens baseline.
        float baseline = main.focal;
        if (Build.VERSION.SDK_INT >= 28 && preferred.logical && preferred.ratio) {
            for (String physicalId : preferred.info.getPhysicalCameraIds()) {
                try {
                    float value = focal(manager.getCameraCharacteristics(physicalId));
                    if (value > 0 && (baseline <= 0 || Math.abs(value - 26f) < Math.abs(baseline - 26f))) baseline = value;
                } catch (Exception error) { FileLog.e(error); }
            }
        }
        List<NebulaZoomCapabilities.Module> modules = new ArrayList<>();
        List<Float> stops = new ArrayList<>();
        NebulaZoomCapabilities.Module primary = null;
        for (Camera camera : cameras) {
            if (camera != preferred && (front || camera.focal <= 0 || baseline <= 0)) continue;
            float base = front || camera.focal <= 0 || baseline <= 0 ? 1f : camera.focal / baseline;
            if (camera == preferred) base = 1f;
            NebulaZoomCapabilities.Module module = new NebulaZoomCapabilities.Module(camera.id, base, camera.min, camera.max, camera.logical && camera.ratio);
            // Don't reopen a concurrently used logical camera just to gain unverified digital reach.
            if (preferred.logical && preferred.ratio && camera != preferred) continue;
            modules.add(module);
            if (camera == preferred) primary = module;
        }
        if (Build.VERSION.SDK_INT >= 28 && preferred.logical && preferred.ratio && baseline > 0) {
            for (String physicalId : preferred.info.getPhysicalCameraIds()) {
                try {
                    float value = focal(manager.getCameraCharacteristics(physicalId));
                    if (value > 0) stops.add(value / baseline);
                } catch (Exception error) { FileLog.e(error); }
            }
        }
        return primary == null ? null : new NebulaZoomCapabilities(modules, primary, stops);
    }

    private static float focal(CameraCharacteristics info) {
        float[] lengths = info.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        SizeF sensor = info.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        float value = lengths == null || lengths.length == 0 || sensor == null || sensor.getWidth() <= 0 ? 0f : 36f * lengths[0] / sensor.getWidth();
        return value > 0 && !Float.isInfinite(value) ? value : 0f;
    }
    private NebulaCameraZoom() {}
}
