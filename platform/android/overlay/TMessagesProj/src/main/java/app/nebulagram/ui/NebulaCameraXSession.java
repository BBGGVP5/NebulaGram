package app.nebulagram.ui;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CaptureRequest;
import android.util.Size;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.camera2.interop.Camera2CameraControl;
import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import com.google.common.util.concurrent.ListenableFuture;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.camera.Camera2Session;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executor;

/** CameraX owns the capture device; Telegram continues to own its GL texture and encoder. */
@androidx.annotation.OptIn(markerClass = androidx.camera.camera2.interop.ExperimentalCamera2Interop.class)
public final class NebulaCameraXSession extends Camera2Session implements LifecycleOwner {
    private final Executor main = command -> AndroidUtilities.runOnUIThread(command);
    private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);
    private final boolean front;
    private final Size requested;
    private volatile boolean closed, failed, ready, recording, flashing;
    private volatile int width, height;
    private volatile float zoom = 1f, minZoom = 1f, maxZoom = 1f, exposure;
    private Runnable done;
    private ProcessCameraProvider provider;
    private Preview preview;
    private ImageCapture photos;
    private Camera camera;
    private SurfaceTexture texture;
    private boolean scanning, night;
    private int[] supportedScenes;
    private final Runnable openingTimeout = () -> {
        if (!closed && !ready) fail(new IllegalStateException("CameraX did not open in time"));
    };

    public NebulaCameraXSession(boolean front, String id, Size size) {
        super(front, id, size); this.front = front; requested = size;
        width = size.getWidth(); height = size.getHeight();
    }
    @NonNull @Override public Lifecycle getLifecycle() { return lifecycle; }
    @Override public void open(SurfaceTexture input) {
        main.execute(() -> {
            if (closed || input == null || texture != null) return;
            texture = input;
            AndroidUtilities.runOnUIThread(openingTimeout, 15000);
            ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(ApplicationLoader.applicationContext);
            future.addListener(() -> {
                if (closed) return;
                try { provider = future.get(); bind(); }
                catch (Exception error) { fail(error); }
            }, main);
        });
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void bind() throws Exception {
        if (closed || texture == null) return;
        lifecycle.setCurrentState(Lifecycle.State.CREATED);
        CameraSelector selector = new CameraSelector.Builder().requireLensFacing(front ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
            .addCameraFilter(infos -> {
                ArrayList<androidx.camera.core.CameraInfo> matching = new ArrayList<>();
                for (androidx.camera.core.CameraInfo info : infos)
                    if (cameraId.equals(Camera2CameraInfo.from(info).getCameraId())) matching.add(info);
                return matching;
            }).build();
        Preview.Builder builder = new Preview.Builder().setTargetResolution(requested);
        android.hardware.camera2.CameraManager manager = (android.hardware.camera2.CameraManager) ApplicationLoader.applicationContext.getSystemService(android.content.Context.CAMERA_SERVICE);
        Camera2Interop.Extender<Preview> interop = new Camera2Interop.Extender<>(builder);
        supportedScenes = manager.getCameraCharacteristics(cameraId).get(android.hardware.camera2.CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES);
        for (Map.Entry<CaptureRequest.Key<?>, Object> value : new NebulaCameraCapabilities.Options(manager.getCameraCharacteristics(cameraId), requested).values.entrySet())
            interop.setCaptureRequestOption((CaptureRequest.Key) value.getKey(), value.getValue());
        preview = builder.build();
        preview.setSurfaceProvider(main, request -> {
            if (closed || texture == null) { request.willNotProvideSurface(); return; }
            try {
            width = request.getResolution().getWidth(); height = request.getResolution().getHeight();
            texture.setDefaultBufferSize(width, height);
            Surface surface = new Surface(texture);
            // The texture belongs to Telegram. Release only our Surface, after CameraX stops using it.
            request.provideSurface(surface, main, result -> surface.release());
            ready = true;
            AndroidUtilities.cancelRunOnUIThread(openingTimeout);
            if (done != null) { Runnable callback = done; done = null; callback.run(); }
            } catch (RuntimeException error) { request.willNotProvideSurface(); fail(error); }
        });
        if (!recording) {
            photos = new ImageCapture.Builder().setTargetResolution(requested).setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
            camera = provider.bindToLifecycle(this, selector, preview, photos);
        } else camera = provider.bindToLifecycle(this, selector, preview);
        camera.getCameraInfo().getCameraState().observe(this, state -> {
            if (!closed && state.getError() != null) { failed = true; ready = false; }
        });
        camera.getCameraInfo().getZoomState().observe(this, state -> {
            if (state != null) { minZoom = state.getMinZoomRatio(); maxZoom = state.getMaxZoomRatio(); }
        });
        lifecycle.setCurrentState(Lifecycle.State.RESUMED);
        ZoomState state = camera.getCameraInfo().getZoomState().getValue();
        if (state != null) { minZoom = state.getMinZoomRatio(); maxZoom = state.getMaxZoomRatio(); }
        setZoom(zoom); setFlash(flashing); setExposure(exposure); applyCaptureMode();
    }
    private void fail(Exception error) { failed = true; ready = false; FileLog.e(error); destroy(true); }
    @Override public void whenDone(Runnable callback) { main.execute(() -> { if (!closed) { if (ready) callback.run(); else done = callback; } }); }
    @Override public boolean hasFailed() { return failed || closed; }
    @Override public boolean isInitiated() { return ready && !closed && !failed; }
    @Override public int getPreviewWidth() { return width; }
    @Override public int getPreviewHeight() { return height; }
    @Override public void setZoom(float value) {
        if (!Float.isFinite(value)) return;
        zoom = Math.max(minZoom, Math.min(maxZoom, value));
        main.execute(() -> { if (!closed && camera != null) camera.getCameraControl().setZoomRatio(zoom); });
    }
    @Override public float getZoom() { return zoom; }
    @Override public float getMinZoom() { return minZoom; }
    @Override public float getMaxZoom() { return maxZoom; }
    @Override public void setFlash(boolean value) {
        flashing = value;
        main.execute(() -> { if (!closed && camera != null && camera.getCameraInfo().hasFlashUnit()) camera.getCameraControl().enableTorch(flashing); });
    }
    @Override public boolean getFlash() { return flashing; }
    @Override public void setExposure(float value) {
        if (!Float.isFinite(value)) return;
        exposure = Math.max(-1f, Math.min(1f, value));
        main.execute(() -> {
            if (closed || camera == null) return;
            androidx.camera.core.ExposureState state = camera.getCameraInfo().getExposureState();
            if (state.isExposureCompensationSupported()) camera.getCameraControl().setExposureCompensationIndex(
                Math.round(exposure * (exposure < 0 ? -state.getExposureCompensationRange().getLower() : state.getExposureCompensationRange().getUpper())));
        });
    }
    @Override public void setRecordingVideo(boolean value) { recording = value; main.execute(this::applyCaptureMode); }
    @Override public void focusToRect(android.graphics.Rect focus, android.graphics.Rect metering) {
        if (focus == null) return;
        final float x = Math.max(0f, Math.min(1f, (focus.exactCenterX() + 1000f) / 2000f));
        final float y = Math.max(0f, Math.min(1f, (focus.exactCenterY() + 1000f) / 2000f));
        main.execute(() -> {
            if (closed || camera == null) return;
            androidx.camera.core.MeteringPoint point = new androidx.camera.core.SurfaceOrientedMeteringPointFactory(1f,1f).createPoint(x,y);
            androidx.camera.core.FocusMeteringAction action = new androidx.camera.core.FocusMeteringAction.Builder(point)
                .setAutoCancelDuration(5,java.util.concurrent.TimeUnit.SECONDS).build();
            if (camera.getCameraInfo().isFocusMeteringSupported(action)) camera.getCameraControl().startFocusAndMetering(action);
        });
    }
    @Override public void setScanningBarcode(boolean value) { main.execute(() -> { scanning = value; applyCaptureMode(); }); }
    @Override public void setNightMode(boolean value) { main.execute(() -> { night = value; applyCaptureMode(); }); }
    private void applyCaptureMode() {
        if (closed || camera == null) return;
        int requestedScene = scanning ? CaptureRequest.CONTROL_SCENE_MODE_BARCODE
            : night ? (front ? CaptureRequest.CONTROL_SCENE_MODE_NIGHT_PORTRAIT : CaptureRequest.CONTROL_SCENE_MODE_NIGHT)
            : CaptureRequest.CONTROL_SCENE_MODE_DISABLED;
        int scene = CaptureRequest.CONTROL_SCENE_MODE_DISABLED;
        if (supportedScenes != null) for (int value : supportedScenes) if (value == requestedScene) scene = value;
        CaptureRequestOptions options = new CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_MODE, scene == CaptureRequest.CONTROL_SCENE_MODE_DISABLED
                ? CaptureRequest.CONTROL_MODE_AUTO : CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
            .setCaptureRequestOption(CaptureRequest.CONTROL_SCENE_MODE, scene)
            .setCaptureRequestOption(CaptureRequest.CONTROL_CAPTURE_INTENT, recording
                ? CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD : CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW).build();
        Camera2CameraControl.from(camera.getCameraControl()).addCaptureRequestOptions(options);
    }
    @Override public boolean takePicture(File file, Utilities.Callback<Integer> callback) {
        if (!isInitiated() || photos == null) return false;
        main.execute(() -> {
            if (closed || photos == null) return;
            android.view.WindowManager window=(android.view.WindowManager)ApplicationLoader.applicationContext.getSystemService(android.content.Context.WINDOW_SERVICE);
            if(window!=null)photos.setTargetRotation(window.getDefaultDisplay().getRotation());
            photos.takePicture(new ImageCapture.OutputFileOptions.Builder(file).build(), main, new ImageCapture.OnImageSavedCallback() {
                @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) { if (!closed && callback != null) callback.run(AndroidUtilities.getImageOrientation(file).first); }
                @Override public void onError(@NonNull ImageCaptureException error) { FileLog.e(error); if (!closed && callback != null) callback.run(-1); }
            });
        });
        return true;
    }
    @Override public void destroy(boolean async) { destroy(async, null); }
    @Override public void destroy(boolean async, Runnable after) {
        closed = true; ready = false;
        main.execute(() -> {
            AndroidUtilities.cancelRunOnUIThread(openingTimeout);
            done = null;
            if (lifecycle.getCurrentState() != Lifecycle.State.INITIALIZED) lifecycle.setCurrentState(Lifecycle.State.DESTROYED);
            if (provider != null) {
                if (photos != null && preview != null) provider.unbind(preview, photos);
                else if (preview != null) provider.unbind(preview);
            }
            camera = null; photos = null; preview = null; texture = null;
            if (after != null) after.run();
        });
    }
}
