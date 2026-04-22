package com.perfectdark.port;

import org.libsdl.app.SDLActivity;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.io.File;

public class MainActivity extends SDLActivity {
    static {
        System.loadLibrary("SDL2");
        System.loadLibrary("pd");
    }

    private TouchOverlayView overlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        hideSystemUI();
        initializeGame();
        attachTouchOverlay();
    }

    /**
     * SDLActivity builds its own view hierarchy inside {@code mLayout}. We add
     * our overlay as the last child so it sits on top and captures touches
     * before they reach the SDL surface.
     */
    private void attachTouchOverlay() {
        overlay = new TouchOverlayView(this);
        // mLayout is an SDL-owned RelativeLayout; a plain ViewGroup.LayoutParams
        // with MATCH_PARENT on both axes is enough since we want it to fill.
        mLayout.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void initializeGame() {
        File dataDir = new File(getExternalFilesDir(null), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        nativeInit(dataDir.getAbsolutePath());
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (overlay != null) overlay.reloadLayout();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        nativeDestroy();
    }

    public native void nativeInit(String dataPath);
    public native void nativeStartGame();
    public native void nativeDestroy();
}
