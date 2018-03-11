package com.itmg_consulting.photobyebye;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.OnScanCompletedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2BasicFragment extends Fragment
        implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 2;


    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "Camera2BasicFragment";

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    public static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1080;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();

            stopLocationUpdates("onDisconnected");

            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();

            stopLocationUpdates("onError");

            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }
    };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /** This is our Bitmap. */
    private Bitmap mBitmapImage;

    /** Update Location: 2 different Class for getting the location */
    private FusedLocationRequest mFusedLocationRequest;
    private LocationRequest mLocationRequest;
    private LocationManagerPhotoByeBye mLocationManager;

    /** Flash State */
    private String mFlashState;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            if(mState == STATE_PICTURE_TAKEN)
            {
                getActivity().runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            mLocationManager.checkToken();
                        }
                    }
                );

                Image mImage   = reader.acquireLatestImage();
                imageToBitmap(mImage);
                getActivity().runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageView imageViewPhotoTaken = getActivity().findViewById(R.id.imageViewPhotoTaken);
                            imageViewPhotoTaken.setImageBitmap(mBitmapImage);

                            Matrix matrix = imageViewPhotoTaken.getImageMatrix();
                            float imageHeight = imageViewPhotoTaken.getDrawable().getIntrinsicHeight();
                            int screenHeight = getResources().getDisplayMetrics().heightPixels;
                            float scaleRatio = screenHeight / imageHeight;
                            matrix.setScale(scaleRatio, scaleRatio, 0, 0);
                            imageViewPhotoTaken.setImageMatrix(matrix);

                            saveButton();
                        }
                    }
                );
            }
        }

        private void imageToBitmap(Image mImage){
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();

            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            mImage.close();

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            options.inSampleSize = 2;
            Bitmap drawableBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

            Matrix matrix = new Matrix();

            if (drawableBitmap.getWidth() > drawableBitmap.getHeight())
                matrix.setRotate(90);

            if(mBitmapImage != null)
                mBitmapImage.recycle();

            mBitmapImage = Bitmap.createBitmap(drawableBitmap, 0, 0, drawableBitmap.getWidth(),
                    drawableBitmap.getHeight(), matrix, true);

            drawableBitmap.recycle();
        }
    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }

                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);

                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {

                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        //Log.d("PROCESS_CAM_LOCKAE", "aeState = " + aeState);

                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }

                        return;
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

                    //Log.d("PROCESS_CAM", "aeState = " + aeState);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static Camera2BasicFragment newInstance() {
        return new Camera2BasicFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.picture).setOnClickListener(this);
        view.findViewById(R.id.save_picture).setOnClickListener(this);
        view.findViewById(R.id.cancel_picture).setOnClickListener(this);
        view.findViewById(R.id.flash).setOnClickListener(this);
        mTextureView = view.findViewById(R.id.texture);

        // Get state flash
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        mFlashState = sharedPref.getString("mFlashState", "null");

        if(!mFlashState.equals("null"))
            setFlashImage(mFlashState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Create new album if not exist in the gallery
        String pathD = Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_DCIM + "/";
        File mediaStorageDir = new File(pathD, "PhotoByeBye");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("PhotoByeBye", "failed to create directory");
            }
        }

        /*
        // SCAN THE IMAGE TO SHOW IN GALLERY
        MediaScannerConnection.scanFile(
            getActivity().getApplicationContext(),
            new String[]{mediaStorageDir.toString()},
            null,
            new OnScanCompletedListener() {
                @Override
                public void onScanCompleted(final String path, final Uri uri) {
                    getActivity().runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    Log.v("Scan PhotoByeBye",
                                            "file " + path + " was scanned successfully: " + uri);
                                }
                            }
                    );
                }
            });
        */
    }

    @Override
    public void onResume() {
        super.onResume();

        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        // Save state flash
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("mFlashState", mFlashState);
        editor.apply();

        closeCamera();
        stopLocationUpdates("onPause");

        stopBackgroundThread();
        super.onPause();
    }

    /**
     * Set the place find by the WS on the preview
     * @param text chosen from the popup with different places @see {@link LocationManagerPhotoByeBye#popupCallBack(CharSequence)}
     */
    public void setTextPreview(CharSequence text) {
        Log.d("setTextPreview",(String) text);
        TextView myTextView = getActivity().findViewById(R.id.nameLocalisation);
        myTextView.setText(text);
    }

    /** Change the button on the camera display to save or take a picture */
    private void initButton() {
        TextView myTextView = getActivity().findViewById(R.id.nameLocalisation);
        RelativeLayout relativePhotoTaken = getActivity().findViewById(R.id.relativePhotoTaken);
        ImageView myButtonPicture = getActivity().findViewById(R.id.picture);

        relativePhotoTaken.setVisibility(View.GONE);
        myTextView.setText("");
        myButtonPicture.setVisibility(View.VISIBLE);
    }

    /** Change the button on the camera display to save or take a picture */
    private void saveButton() {
        RelativeLayout relativePhotoTaken = getActivity().findViewById(R.id.relativePhotoTaken);
        ImageView myButtonPicture = getActivity().findViewById(R.id.picture);

        relativePhotoTaken.setVisibility(View.VISIBLE);
        myButtonPicture.setVisibility(View.GONE);
    }

    /** Get State Camera */
    public int getState() {
        return mState;
    }

    /**
     * Set the text on the image then save it @see ByteArrayOutputStreamSaver
     * @param text chosen from popup with the different places
     */
    private void setTextOnImage(String text) {
        Log.d("setTextOnImage", text);

        Canvas canvas = new Canvas(mBitmapImage);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);

        float maxTextSize = 130;
        paint.setTextSize(maxTextSize);
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        float desiredTextSize = maxTextSize * (canvas.getWidth()-20) / bounds.width();

        if(desiredTextSize > maxTextSize)
            desiredTextSize = maxTextSize;

        paint.setTextSize(desiredTextSize);

        int xPos = (canvas.getWidth() / 2);
        int yPos = (int) ((canvas.getHeight()) + ((paint.descent() + paint.ascent()))) ;

        canvas.drawBitmap(mBitmapImage, 0, 0, paint);
        canvas.drawText(text, xPos, yPos, paint);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mBitmapImage.compress(Bitmap.CompressFormat.JPEG, 90, out);
        mBitmapImage.recycle();

        mBackgroundHandler.post(new ByteArrayOutputStreamSaver(out));
    }

    /**
     *  request Camera Permission
     *  @see PermissionRequestDialog()
     */
    private void requestCameraPermission() {
        if (FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            PermissionRequestDialog permissionRequestDialog = new PermissionRequestDialog(Manifest.permission.CAMERA, REQUEST_CAMERA_PERMISSION, R.string.request_camera_permission);
            permissionRequestDialog.showConfirmationDialog(getChildFragmentManager());
        }
        else {
            FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    /**
     *  request WRITE_EXTERNAL_STORAGE Permission
     *  @see PermissionRequestDialog()
     */
    private void requestWriteStoragePermission() {
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED )     {
            if (FragmentCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE))
            {
                PermissionRequestDialog permissionRequestDialog = new PermissionRequestDialog(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE, REQUEST_WRITE_EXTERNAL_STORAGE,
                        R.string.request_storage_permission);
                permissionRequestDialog.showConfirmationDialog(getChildFragmentManager());
            }
            else {
                FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                if (isResumed() && !isRemoving()){
                    PermissionRequestDialog permissionRequestDialog = new PermissionRequestDialog();
                    permissionRequestDialog.showErrorDialog(getChildFragmentManager(),
                            getString(R.string.request_camera_permission));
                }
            }
        }
        else if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                if (isResumed() && !isRemoving()){
                    PermissionRequestDialog permissionRequestDialog = new PermissionRequestDialog();
                    permissionRequestDialog.showErrorDialog(getChildFragmentManager(),
                            getString(R.string.request_storage_permission));
                }
            }
        }
        else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (manager != null) {
                for (String cameraId : manager.getCameraIdList()) {
                    CameraCharacteristics characteristics
                            = manager.getCameraCharacteristics(cameraId);

                    // We don't use a front facing camera in this sample.
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        continue;
                    }

                    StreamConfigurationMap map = characteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (map == null) {
                        continue;
                    }

                    // For still image captures, we use the largest available size.
                    Size largest = Collections.max(
                            Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                            new CompareSizesByArea());

                    mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                            ImageFormat.JPEG, /*maxImages*/2);

                    mImageReader.setOnImageAvailableListener(
                            mOnImageAvailableListener, mBackgroundHandler);

                    // Find out if we need to swap dimension to get the preview size relative to sensor
                    // coordinate.
                    int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                    //noinspection ConstantConditions
                    mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    boolean swappedDimensions = false;
                    switch (displayRotation) {
                        case Surface.ROTATION_0:
                        case Surface.ROTATION_180:
                            if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                                swappedDimensions = true;
                            }
                            break;
                        case Surface.ROTATION_90:
                        case Surface.ROTATION_270:
                            if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                                swappedDimensions = true;
                            }
                            break;
                        default:
                            Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                    }

                    Point displaySize = new Point();
                    activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                    int rotatedPreviewWidth = width;
                    int rotatedPreviewHeight = height;
                    int maxPreviewWidth = displaySize.x;
                    int maxPreviewHeight = displaySize.y;

                    if (swappedDimensions) {
                        rotatedPreviewWidth = height;
                        rotatedPreviewHeight = width;
                        maxPreviewWidth = displaySize.y;
                        maxPreviewHeight = displaySize.x;
                    }

                    if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                        maxPreviewWidth = MAX_PREVIEW_WIDTH;
                    }

                    if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                        maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                    }

                    // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                    // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                    // garbage capture data.
                    mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                            rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                            maxPreviewHeight, largest);

                    // We fit the aspect ratio of TextureView to the size of preview we picked.
                    int orientation = getResources().getConfiguration().orientation;
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        mTextureView.setAspectRatio(
                                mPreviewSize.getWidth(), mPreviewSize.getHeight());
                    } else {
                        mTextureView.setAspectRatio(
                                mPreviewSize.getHeight(), mPreviewSize.getWidth());
                    }

                    // Check if the flash is supported.
                    Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    mFlashSupported = available == null ? false : available;

                    mCameraId = cameraId;
                    return;
                }
            }
        } catch (CameraAccessException e) {
            killCameraRestartOnThread();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            PermissionRequestDialog permissionRequestDialog = new PermissionRequestDialog();
            permissionRequestDialog.showErrorDialog(getChildFragmentManager(), getString(R.string.request_camera_permission));
        }
    }

    /**
        By default the brightness is to low, increase the brightness
     */
    private void setBrightness() {
        int brightnessWanted = 70;

        int minCompensationRange = 0;
        int maxCompensationRange = 0;
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = null;
        try {
            characteristics = manager.getCameraCharacteristics(mCameraId);
        } catch (CameraAccessException e) {
            killCameraRestartOnThread();
        }
        Range<Integer> controlAECompensationRange = characteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        if (controlAECompensationRange != null) {
            minCompensationRange = controlAECompensationRange.getLower();
            maxCompensationRange = controlAECompensationRange.getUpper();
        }

        int brightness = (int) (minCompensationRange + (maxCompensationRange - minCompensationRange) * (brightnessWanted / 100f));
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, brightness);
    }

    /**
     * Opens the camera specified by {@link Camera2BasicFragment#mCameraId}.
     */
    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED){
            requestCameraPermission();
            return;
        }
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED){
            requestWriteStoragePermission();
            return;
        }

        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                killCameraRestartOnThread();
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException | InterruptedException e) {
            killCameraRestartOnThread();
        }
    }

    private void killCameraRestartOnThread(){
        getActivity().runOnUiThread(
            new Runnable() {
                @Override
                public void run() {
                    ((MainActivity)getActivity()).killCameraRestart();
                }
            }
        );
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        getLocationRequestInstance();
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * A safe way to get an instance of the LocationRequest object.
     */
    private void getLocationRequestInstance(){
        MainActivity mainActivity = (MainActivity) getActivity();

        stopLocationUpdates("getLocationRequestInstance");

        if(mLocationManager == null)
            mLocationManager = new LocationManagerPhotoByeBye(mainActivity, this);

        if(MainActivity.USE_FUSED_LOCATION){
            if(mFusedLocationRequest == null)
                mFusedLocationRequest = new FusedLocationRequest(mainActivity, mLocationManager);

            mFusedLocationRequest.init();
        }
        else{
            if(mLocationRequest == null)
                mLocationRequest = new LocationRequest(mainActivity, mLocationManager);

            mLocationRequest.init();
        }
    }

    private void stopLocationUpdates(String from){
        if(mLocationRequest != null)
            mLocationRequest.stopLocationUpdates(from);
        else if(mFusedLocationRequest != null)
            mFusedLocationRequest.stopLocationUpdates(from);
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                mPreviewRequestBuilder = setFlash(mPreviewRequestBuilder);

                                setBrightness();

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException | IllegalStateException e) {
                                getActivity().runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            killCameraRestartOnThread();
                                        }
                                    }
                                );
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                            @NonNull CameraCaptureSession cameraCaptureSession) {
                        }
                    }, null
            );
        } catch (CameraAccessException | IllegalStateException e) {
            killCameraRestartOnThread();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }

        mTextureView.setTransform(matrix);
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        lockFocus();
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        mPreviewRequestBuilder = setFlash(mPreviewRequestBuilder);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

        // Tell #mCaptureCallback to wait for the lock.
        mState = STATE_WAITING_LOCK;
        try {
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException | NullPointerException e) {
            killCameraRestartOnThread();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            killCameraRestartOnThread();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }

            CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            // This is the output Surface we need to start preview.
            captureBuilder.addTarget(mImageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            captureBuilder = setFlash(captureBuilder);

            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            killCameraRestartOnThread();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    public void unlockFocus() {
        try {
            initButton();
            mState = STATE_PREVIEW;
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewRequestBuilder = setFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException | NullPointerException e) {
            killCameraRestartOnThread();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.picture: {
                takePicture();
                break;
            }
            case R.id.cancel_picture: {
                unlockFocus();
                break;
            }
            case R.id.save_picture: {
                MainActivity mainAct = (MainActivity) getActivity();
                mainAct.showProgress(true);

                TextView myTextView =  getActivity().findViewById(R.id.nameLocalisation);
                final CharSequence myText = myTextView.getText();
                unlockFocus();

                Thread thread = new Thread() {
                    @Override
                    public void run() {
                        setTextOnImage((String) myText);
                    }
                };
                thread.start();
                break;
            }
            case R.id.flash: {
                setFlashImageClick();
                break;
            }
        }
    }

    /**
     * Adapt the flash depend the image choosen @see {@link #setFlashImageClick()} ()}
     * @param requestBuilder Camera Setting
     */
    private CaptureRequest.Builder setFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            ImageView flashImage = getActivity().findViewById(R.id.flash);
            String tag = flashImage.getTag().toString();

            switch (tag) {
                case "ic_flash_auto": {
                    requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    requestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                    break;
                }
                case "ic_flash_off": {
                    requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                    break;
                }
                case "ic_flash_on": {
                    requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    break;
                }
            }
        }
        return requestBuilder;
    }

    /** Change the flash img after onclick */
    private void setFlashImageClick() {
        if (mFlashSupported) {
            ImageView flashImage = getActivity().findViewById(R.id.flash);
            String tag = flashImage.getTag().toString();

            setFlashImage(tag);
        }
    }

    /** set the flash img */
    private void setFlashImage(String tag) {
        try {
            ImageView flashImage = getActivity().findViewById(R.id.flash);
            mFlashState = tag;

            switch (tag) {
                case "ic_flash_auto": {
                    flashImage.setImageResource(R.drawable.ic_flash_off);
                    flashImage.setTag("ic_flash_off");
                    break;
                }
                case "ic_flash_off": {
                    flashImage.setImageResource(R.drawable.ic_flash_on);
                    flashImage.setTag("ic_flash_on");
                    break;
                }
                case "ic_flash_on": {
                    flashImage.setImageResource(R.drawable.ic_flash_auto);
                    flashImage.setTag("ic_flash_auto");
                    break;
                }
            }
        } catch (NullPointerException e) {
            killCameraRestartOnThread();
        }
    }

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private class ByteArrayOutputStreamSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final ByteArrayOutputStream mBitmap;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        ByteArrayOutputStreamSaver(ByteArrayOutputStream bitmap) {
            mBitmap = bitmap;

            String pathD = Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_DCIM + "/";
            File mediaStorageDir = new File(pathD, "PhotoByeBye");
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            mFile = new File(mediaStorageDir,"pic"+"_"+ timeStamp+".jpg");
        }

        @Override
        public void run() {
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(mBitmap.toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (null != output) {
                    try {
                        output.close();

                        // SCAN THE IMAGE TO SHOW IN GALLERY
                        MediaScannerConnection.scanFile(
                            getActivity().getApplicationContext(),
                            new String[]{mFile.toString()},
                            null,
                            new OnScanCompletedListener() {
                                @Override
                                public void onScanCompleted(final String path, final Uri uri) {
                                    getActivity().runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                Log.d("Scan PhotoByeBye",
                                                        "file " + path + " was scanned successfully: " + uri);
                                            }
                                        }
                                    );

                                }
                            });

                        getActivity().runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    MainActivity mainAct = (MainActivity) getActivity();
                                    mainAct.showProgress(false);
                                }
                            }
                        );
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}

