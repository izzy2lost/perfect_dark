package com.perfectdark.port;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Fullscreen editor for the touch layout. Shows the same overlay the game
 * uses, but in edit mode: tap + drag moves an element, toggling "resize"
 * instead stretches its radius. "Save" writes to SharedPreferences.
 */
public class LayoutEditorActivity extends AppCompatActivity {

    private TouchOverlayView overlay;
    private boolean resizing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        hideSystemUI();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF101014);
        setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        overlay = new TouchOverlayView(this);
        overlay.setInternalHudVisible(false);
        overlay.setEditMode(true);
        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        root.addView(buildHud(this), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        Toast.makeText(this,
                "Toca un control para arrastrarlo. Activa RESIZE para cambiar su tamaño.",
                Toast.LENGTH_LONG).show();
    }

    private View buildHud(Context ctx) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(16, 16, 16, 16);
        bar.setBackgroundColor(0x88000000);

        TextView title = new TextView(ctx);
        title.setText("Touch Layout");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(title, titleLp);

        final Button resize = new Button(ctx);
        resize.setText("RESIZE OFF");
        resize.setOnClickListener(v -> {
            resizing = !resizing;
            overlay.setResizeMode(resizing);
            resize.setText(resizing ? "RESIZE ON" : "RESIZE OFF");
        });
        bar.addView(resize);

        Button reset = new Button(ctx);
        reset.setText("RESET");
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Restablecer el layout predeterminado?")
                .setPositiveButton("Sí", (d, w) -> {
                    TouchLayout.resetDefaults(this);
                    overlay.reloadLayout();
                })
                .setNegativeButton("No", null)
                .show());
        bar.addView(reset);

        Button save = new Button(ctx);
        save.setText("GUARDAR");
        save.setOnClickListener(v -> {
            overlay.getLayout().save(this);
            Toast.makeText(this, "Layout guardado", Toast.LENGTH_SHORT).show();
            finish();
        });
        bar.addView(save);

        Button back = new Button(ctx);
        back.setText("CANCELAR");
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        return bar;
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
}
