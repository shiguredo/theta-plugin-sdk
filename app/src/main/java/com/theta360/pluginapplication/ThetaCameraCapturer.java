package com.theta360.pluginapplication;


import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import org.webrtc.CapturerObserver;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.ThreadUtils;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.io.IOException;

public class ThetaCameraCapturer implements VideoCapturer, VideoSink {

    private static final String TAG = ThetaCameraCapturer.class.getSimpleName();

    private SurfaceTextureHelper surfaceTextureHelper;
    private CapturerObserver capturerObserver;
    private boolean              isDisposed;
    private int                  width;
    private int                  height;
    @SuppressWarnings("deprecation")
    private Camera               camera;
    private String               shootingMode;
    private String               stitching;

    public enum ShootingMode {
        Live3840, Live1920, Live1024, Live640,

        // こっちは絵が出なかった
        Recording3840
    }

    public ThetaCameraCapturer(ShootingMode shootingMode) {
        switch (shootingMode) {
            case Live3840:
                this.width = 3840;
                this.height = 1920;
                this.shootingMode = "RicMoviePreview3840";
                break;
            case Live1920:
                this.width = 1920;
                this.height = 960;
                this.shootingMode = "RicMoviePreview1920";
                break;
            case Live1024:
                this.width = 1024;
                this.height = 512;
                this.shootingMode = "RicMoviePreview1024";
                break;
            case Live640:
                this.width = 640;
                this.height = 320;
                this.shootingMode = "RicMoviePreview640";
                break;
            case Recording3840:
                this.width = 3840;
                this.height = 1920;
                this.shootingMode = "RicMovieRecording4kEqui";
                break;
        }

        //noinspection deprecation
        this.camera     = Camera.open();
        this.isDisposed = false;
    }

    private void checkNotDisposed() {
        if (isDisposed) {
            throw new RuntimeException("capturer is disposed.");
        }
    }

    @Override
    public void onFrame(VideoFrame videoFrame) {
        capturerObserver.onFrameCaptured(videoFrame);
    }

    @Override
    public void initialize(final SurfaceTextureHelper surfaceTextureHelper,
                           final Context              context,
                           final CapturerObserver     capturerObserver) {

        Log.d(TAG, "initialize");
        checkNotDisposed();

        if (surfaceTextureHelper == null) {
            throw new RuntimeException("surfaceTextureHelper not set.");
        }
        this.surfaceTextureHelper = surfaceTextureHelper;

        if (capturerObserver == null) {
            throw new RuntimeException("capturerObserver not set.");
        }
        this.capturerObserver = capturerObserver;
    }

    @Override
    public void startCapture(final int width,
                             final int height,
                             final int ignoredFrameRate) {
        // 引数は無視
        Log.d(TAG, "startCapture");
        checkNotDisposed();

        if (setupCameraTexture(width, height)) {
            capturerObserver.onCapturerStarted(true);
            surfaceTextureHelper.startListening(ThetaCameraCapturer.this);
        }
    }

    private boolean setupCameraTexture(final int width, final int height) {
        Log.d(TAG, "setupCameraTexture");

        surfaceTextureHelper.getSurfaceTexture().setDefaultBufferSize(this.width, this.height);
        try {
            camera.setPreviewTexture(surfaceTextureHelper.getSurfaceTexture());
        } catch (IOException e) {
            e.printStackTrace();
            camera.release();
            return false;
        }
        @SuppressWarnings("deprecation") Camera.Parameters params = camera.getParameters();
        params.set("RIC_SHOOTING_MODE", this.shootingMode);
        params.set("RIC_PROC_STITCHING", "RicStaticStitching");
        //params.set("RicMicSelect", "RicMicSelectExternal");
        // params.set("RicMicSelect", "RicMicSelectInternal");
        //params.set("RIC_MIC_SELECT", "RicMicSelectExternal");
        // params.set("RicMicSurrondVolumeLevel", "RicMicSurroundVolumeLevelNormal");
        // params.set("RicMicSurrondVolumeLevel", "RicMicSurroundVolumeLevelLarge");


        camera.setParameters(params);
        camera.startPreview();
        return true;
    }

    @Override
    public void stopCapture() throws InterruptedException {
        Log.d(TAG, "stopCapture");
        checkNotDisposed();
        ThreadUtils.invokeAtFrontUninterruptibly(surfaceTextureHelper.getHandler(), new Runnable() {
            @Override
            public void run() {
                camera.stopPreview();
                surfaceTextureHelper.stopListening();
                capturerObserver.onCapturerStopped();
            }
        });
    }

    @Override
    public void changeCaptureFormat(final int width,
                                    final int height,
                                    final int ignoredFrameRate) {
        Log.d(TAG, "changeCaptureFormat");
        checkNotDisposed();
        ThreadUtils.invokeAtFrontUninterruptibly(surfaceTextureHelper.getHandler(), new Runnable() {
            @Override
            public void run() {
                camera.stopPreview();
                setupCameraTexture(width, height);
                surfaceTextureHelper.stopListening();
                capturerObserver.onCapturerStopped();
            }
        });
    }

    @Override
    public void dispose() {
        Log.d(TAG, "dispose");
        isDisposed = true;
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    @Override
    public boolean isScreencast() {
        return true;
    }
}
