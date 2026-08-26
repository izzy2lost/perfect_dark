package com.perfectdark.port;

import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends SDLActivity {

    private static final String TAG = "PerfectDark";

    static {
        System.loadLibrary("SDL2");
        System.loadLibrary("pd");
    }

    private TouchControls touchControls;
    private InputManager inputManager;

    private final InputManager.InputDeviceListener deviceListener = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int deviceId) { refreshPads(); }
        @Override public void onInputDeviceRemoved(int deviceId) { refreshPads(); }
        @Override public void onInputDeviceChanged(int deviceId) { refreshPads(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.util.Log.i(TAG, "MainActivity onCreate start");

        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // display cutouts only exist from Android 9; the field is absent before that
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        hideSystemUI();
        initializeGame();
        addTouchControls();

        inputManager = (InputManager) getSystemService(INPUT_SERVICE);
        if (inputManager != null) {
            inputManager.registerInputDeviceListener(deviceListener, null);
        }

        android.util.Log.i(TAG, "MainActivity onCreate complete");
    }

    /**
     * Command line handed to pd_main(). fsInit() resolves --moddir against the base data
     * directory, so the mod tree is overlaid on top of the ROM's files.
     */
    @Override
    protected String[] getArguments() {
        List<String> args = new ArrayList<>();

        String modPath = ModManager.activeModPath(this);
        if (modPath != null) {
            args.add("--moddir");
            args.add(modPath);
            android.util.Log.i(TAG, "launching with mod: " + modPath);
        }

        return args.toArray(new String[0]);
    }

    private void addTouchControls() {
        View content = SDLActivity.getContentView();
        if (!(content instanceof ViewGroup)) {
            android.util.Log.w(TAG, "no SDL content view; on-screen controls disabled");
            return;
        }

        ViewGroup root = (ViewGroup) content;
        // pointers that miss a control have to reach SDLSurface underneath
        root.setMotionEventSplittingEnabled(true);

        touchControls = new TouchControls(this, new TouchLayout(this));
        root.addView(touchControls, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        touchControls.bringToFront();
    }

    private void refreshPads() {
        if (touchControls != null) {
            touchControls.refreshGamepadState();
        }
    }

    /** In the layout editor, Back means "done" rather than "quit". */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (touchControls != null && touchControls.isEditMode()
                && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                touchControls.setEditMode(false);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void initializeGame() {
        File dataDir = new File(getExternalFilesDir(null), "data");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            android.util.Log.e(TAG, "could not create " + dataDir.getAbsolutePath());
        }
        android.util.Log.i(TAG, "data dir: " + dataDir.getAbsolutePath());
        nativeInit(dataDir.getAbsolutePath());
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        refreshPads();
    }

    @Override
    protected void onDestroy() {
        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(deviceListener);
        }
        super.onDestroy();
        nativeDestroy();
    }

    public native void nativeInit(String dataPath);
    public native void nativeDestroy();
}
