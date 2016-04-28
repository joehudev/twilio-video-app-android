package com.twilio.conversations.impl;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoCapturerAndroid.CameraEventsHandler;
import org.webrtc.CameraEnumerationAndroid;

import android.content.Context;
import android.hardware.Camera;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.twilio.conversations.CameraCapturer;
import com.twilio.conversations.CapturerErrorListener;
import com.twilio.conversations.CapturerException;
import com.twilio.conversations.CapturerException.ExceptionDomain;
import com.twilio.conversations.R;
import com.twilio.conversations.impl.logging.Logger;


public class CameraCapturerImpl implements CameraCapturer {
    private static String TAG = "CameraCapturerImpl";

    static final Logger logger = Logger.getLogger(CameraCapturerImpl.class);
    private long session;

    enum CapturerState {
        IDLE,
        PREVIEWING,
        BROADCASTING
    }

    private final Context context;
    private CameraSource source;
    private CapturerState lastCapturerState;

    // Preview capturer members
    private ViewGroup previewContainer;
    private Camera camera;
    private int cameraId;
    private CapturerPreview capturerPreview;
    private CapturerState capturerState = CapturerState.IDLE;

    // Conversation capturer members
    private VideoCapturerAndroid videoCapturerAndroid;
    private CapturerErrorListener listener;
    private long nativeVideoCapturerAndroid;
    private boolean broadcastCapturerPaused = false;

    private CameraCapturerImpl(Context context,
                               CameraSource source,
                               CapturerErrorListener listener) {
        if(context == null) {
            throw new NullPointerException("context must not be null");
        }
        if(source == null) {
            throw new NullPointerException("source must not be null");
        }

        this.context = context;
        this.source = source;
        this.listener = listener;
        cameraId = getCameraId();
        if(cameraId < 0 && listener != null) {
            listener.onError(new CapturerException(ExceptionDomain.CAMERA,
                    "Invalid camera source."));
        }
    }

    public static CameraCapturerImpl create(
            Context context,
            CameraSource source,
            CapturerErrorListener listener) {
        return new CameraCapturerImpl(context, source, listener);
    }

    /**
     * Use VideoCapturerAndroid to determine the camera id of the specified source.
     */
    private int getCameraId() {
        String deviceName;

        if(source == CameraSource.CAMERA_SOURCE_BACK_CAMERA) {
            deviceName = CameraEnumerationAndroid.getNameOfBackFacingDevice();
        } else {
            deviceName = CameraEnumerationAndroid.getNameOfFrontFacingDevice();
        }
        if(deviceName == null) {
            cameraId = -1;
        } else {
            String[] deviceNames = CameraEnumerationAndroid.getDeviceNames();
            for(int i = 0; i < deviceNames.length; i++) {
                if(deviceName.equals(deviceNames[i])) {
                    cameraId = i;
                    break;
                }
            }
        }

        return cameraId;
    }

    @Override
    public synchronized void startPreview(ViewGroup previewContainer) {
        if(capturerState.equals(CapturerState.PREVIEWING) ||
                capturerState.equals(CapturerState.BROADCASTING)) {
            logger.w("previewContainer argument ignored. Preview already running.");
            return;
        }
        this.previewContainer = previewContainer;
        startPreviewInternal();
    }

    @Override
    public synchronized void stopPreview() {
        if(capturerState.equals(CapturerState.PREVIEWING)) {
            if (previewContainer != null) {
                previewContainer.removeAllViews();
            }
            capturerPreview = null;
            if(camera != null) {
                camera.release();
                camera = null;
            }
            capturerState = CapturerState.IDLE;
        }
    }

    @Override
    public synchronized boolean isPreviewing() {
        return capturerState.equals(CapturerState.PREVIEWING);
    }

    /*
     * Called internally prior to a session being started to setup
     * the capturer used during a Conversation.
     */
    synchronized void startConversationCapturer(long session) {
        this.session = session;

        if(isPreviewing()) {
            stopPreview();
        }
        if (nativeVideoCapturerAndroid == 0) {
            createVideoCapturerAndroid();
        }
        capturerState = CapturerState.BROADCASTING;
    }

    @Override
    public synchronized boolean switchCamera() {
        if(capturerState.equals(CapturerState.PREVIEWING)) {
            stopPreview();
            cameraId = (cameraId + 1) % Camera.getNumberOfCameras();
            startPreviewInternal();
            return true;
        } else if (capturerState.equals(CapturerState.BROADCASTING) && !broadcastCapturerPaused) {
            // TODO: propagate error
            videoCapturerAndroid.switchCamera(null);
            return true;
        } else {
            return false;
        }
    }

    void pause() {
        lastCapturerState = capturerState;
        if(capturerState.equals(CapturerState.BROADCASTING)) {
            stopVideoSource(session);
            broadcastCapturerPaused = true;
        }
    }

    void resume() {
        if(lastCapturerState != null) {
            if(lastCapturerState.equals(CapturerState.BROADCASTING)) {
                restartVideoSource(session);
            }
            lastCapturerState = null;
            broadcastCapturerPaused = false;
        }
    }

    long getNativeVideoCapturer()  {
        return nativeVideoCapturerAndroid;
    }

    CapturerState getCapturerState() {
        return capturerState;
    }

    /*
     * We can go idle and switch to preview at this point. Disposal will happen
     * when the conversation is disposed
     */
    void resetNativeVideoCapturer() {
        capturerState = CapturerState.IDLE;
    }

    /*
     * We own the last remnants of the capturer so when the conversation is disposed we dispose
     * of the capturer.
     */
    void dispose() {
        disposeCapturer(nativeVideoCapturerAndroid);
        nativeVideoCapturerAndroid = 0;
    }

    private synchronized void startPreviewInternal() {
        if(capturerState.equals(CapturerState.PREVIEWING) ||
                capturerState.equals(CapturerState.BROADCASTING)) {
            return;
        }

        if (previewContainer == null && listener != null) {
            listener.onError(new CapturerException(ExceptionDomain.CAMERA,
                    "Cannot start preview without a preview container"));
            return;
        }

        if (camera == null) {
            try {
                camera = Camera.open(cameraId);
            } catch (Exception e) {
                if(listener != null) {
                    listener.onError(new CapturerException(ExceptionDomain.CAMERA,
                            "Unable to open camera " +
                                    CameraEnumerationAndroid.getDeviceName(cameraId) + ":" +
                                    e.getMessage()));
                }
                return;
            }

            if (camera == null && listener != null) {
                listener.onError(new CapturerException(ExceptionDomain.CAMERA,
                        "Unable to open camera " +
                                CameraEnumerationAndroid.getDeviceName(cameraId)));
                return;
            }
        }

        // Set camera to continually auto-focus
        Camera.Parameters params = camera.getParameters();
        if (params.getSupportedFocusModes()
                .contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        camera.setParameters(params);

        capturerPreview = new CapturerPreview(context, camera, listener);
        previewContainer.removeAllViews();
        previewContainer.addView(capturerPreview);

        capturerState = CapturerState.PREVIEWING;
    }

    private long retrieveNativeVideoCapturerAndroid(VideoCapturerAndroid videoCapturerAndroid) {
        // Use reflection to retrieve the native video capturer handle
        long nativeHandle = 0;

        try {
            Method takeNativeVideoCapturerMethod = videoCapturerAndroid.getClass()
                    .getSuperclass().getDeclaredMethod("takeNativeVideoCapturer");
            takeNativeVideoCapturerMethod.setAccessible(true);
            nativeHandle = (long) takeNativeVideoCapturerMethod.invoke(videoCapturerAndroid);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to access nativeVideoCapturer field: " +
                    e.getMessage());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Unable to retrieve nativeVideoCapturer field: " +
                    e.getMessage());
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Unable to request nativeVideoCapturer field: " +
                    e.getMessage());
        }

        return nativeHandle;
    }

    private void createVideoCapturerAndroid() {
        String deviceName = CameraEnumerationAndroid.getDeviceName(cameraId);
        if (deviceName == null && listener != null) {
            listener.onError(new CapturerException(ExceptionDomain.CAMERA,
                    "Camera device not found"));
            return;
        }
        videoCapturerAndroid = VideoCapturerAndroid.create(deviceName, cameraEventsHandler);
        nativeVideoCapturerAndroid = retrieveNativeVideoCapturerAndroid(videoCapturerAndroid);
    }

    private final CameraEventsHandler cameraEventsHandler = new CameraEventsHandler() {
        @Override
        public void onCameraError(String errorMsg) {
            if(CameraCapturerImpl.this.listener != null) {
                CameraCapturerImpl.this.listener
                        .onError(new CapturerException(ExceptionDomain.CAMERA, errorMsg));
            }
        }

        @Override
        public void onCameraOpening(int cameraId) {

        }

        @Override
        public void onFirstFrameAvailable() {

        }

        @Override
        public void onCameraClosed() {

        }
    };

    private class CapturerPreview extends ViewGroup implements SurfaceHolder.Callback {
        private Context context;
        private SurfaceView surfaceView;
        private SurfaceHolder holder;
        private Camera camera;
        private List<Camera.Size> supportedPreviewSizes;
        private Camera.Size previewSize;
        private CapturerErrorListener listener;
        private OrientationEventListener orientationEventListener;

        public CapturerPreview(Context context, Camera camera, CapturerErrorListener listener) {
            super(context);
            this.context = context;
            this.camera = camera;
            this.listener = listener;
            this.surfaceView = new SurfaceView(context);
            this.supportedPreviewSizes = camera.getParameters().getSupportedPreviewSizes();

            addView(surfaceView);
            holder = surfaceView.getHolder();
            holder.addCallback(this);
            orientationEventListener = new OrientationEventListener(context) {
                @Override
                public void onOrientationChanged(int orientation) {
                    updatePreview();
                }
            };
            setContentDescription(context.getString(R.string.capturer_preview_content_description));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
            final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);

            setMeasuredDimension(width, height);

            // Now that we know the size of the view we calculate the optimal preview size
            previewSize = getOptimalPreviewSize(supportedPreviewSizes, width, height);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            if (changed && getChildCount() > 0) {
                final View child = getChildAt(0);
                final int width = r - l;
                final int height = b - t;
                int previewWidth = width;
                int previewHeight = height;
                int displayOrientation = calculateDisplayOrientation();
                boolean verticalOrientation = displayOrientation == 90 ||
                        displayOrientation == 270;

                if (previewSize != null) {
                    if (verticalOrientation) {
                        previewWidth = previewSize.height;
                        previewHeight = previewSize.width;
                    } else {
                        previewWidth = previewSize.width;
                        previewHeight = previewSize.height;
                    }
                }
                if (verticalOrientation) {
                    final int scaledChildHeight = previewHeight * width / previewWidth;
                    child.layout(0, (height - scaledChildHeight) / 2,
                            width, (height + scaledChildHeight) / 2);
                } else {
                    final int scaledChildWidth = previewWidth * height / previewHeight;
                    child.layout((width - scaledChildWidth) / 2, 0,
                            (width + scaledChildWidth) / 2, height);
                }
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                if (camera != null) {
                    camera.setPreviewDisplay(holder);
                    camera.startPreview();
                    orientationEventListener.enable();
                }

            } catch (IOException e) {
                if(listener != null) {
                    listener.onError(new CapturerException(ExceptionDomain.CAMERA,
                            "Unable to start preview: " + e.getMessage()));
                }
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if(camera != null) {
                orientationEventListener.disable();
                camera.stopPreview();
                try {
                    camera.setPreviewDisplay(null);
                } catch(IOException e) {
                    if(listener != null) {
                        listener.onError(new CapturerException(ExceptionDomain.CAMERA,
                                "Unable to reset preview: " + e.getMessage()));
                    }
                }
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            if (this.holder.getSurface() == null) {
                return;
            }

            if(camera != null) {
                try {
                    camera.stopPreview();
                    camera.setPreviewDisplay(this.holder);
                    updatePreview();
                    camera.startPreview();
                } catch (Exception e) {
                    if(listener != null) {
                        listener.onError(new CapturerException(ExceptionDomain.CAMERA,
                                "Unable to restart preview: " + e.getMessage()));
                    }
                }
            }
        }

        /*
         * Calculates the optimal preview size based on supported preview sizes
         * and provided dimensions
         */
        private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int width, int height) {
            final double ASPECT_TOLERANCE = 0.1;
            double targetRatio = (double) width / height;
            if (sizes == null) return null;
            Camera.Size optimalSize = null;
            double minDiff = Double.MAX_VALUE;
            int targetHeight = height;

            // Try to find an size match aspect ratio and size
            for (Camera.Size size : sizes) {
                double ratio = (double) size.width / size.height;
                if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }

            // Cannot find the one match the aspect ratio, ignore the requirement
            if (optimalSize == null) {
                minDiff = Double.MAX_VALUE;
                for (Camera.Size size : sizes) {
                    if (Math.abs(size.height - targetHeight) < minDiff) {
                        optimalSize = size;
                        minDiff = Math.abs(size.height - targetHeight);
                    }
                }
            }

            return optimalSize;
        }

        private void updatePreview() {
            updatePreviewOrientation();
            updatePreviewSize();
            requestLayout();
        }

        private void updatePreviewOrientation() {
            camera.setDisplayOrientation(calculateDisplayOrientation());
        }

        private int calculateDisplayOrientation() {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, info);
            int degrees = getDeviceOrientation();
            int displayOrientation;

            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                displayOrientation = (info.orientation + degrees) % 360;
                displayOrientation = (360 - displayOrientation) % 360;
            } else {
                displayOrientation = (info.orientation - degrees + 360) % 360;
            }

            return displayOrientation;
        }

        private void updatePreviewSize() {
            Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewSize(previewSize.width, previewSize.height);
        }

        private int getDeviceOrientation() {
            int orientation = 0;

            WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
            switch(wm.getDefaultDisplay().getRotation()) {
                case Surface.ROTATION_90:
                    orientation = 90;
                    break;
                case Surface.ROTATION_180:
                    orientation = 180;
                    break;
                case Surface.ROTATION_270:
                    orientation = 270;
                    break;
                case Surface.ROTATION_0:
                default:
                    orientation = 0;
                    break;
            }
            return orientation;
        }
    }

    private native void stopVideoSource(long nativeSession);
    private native void restartVideoSource(long nativeSession);
    private native void disposeCapturer(long nativeVideoCapturerAndroid);
}
