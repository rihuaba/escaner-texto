package com.escaner.texto;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Pantalla de recorte: muestra la captura congelada, dejas marcar la zona y eliges
 *  - «Texto»: abre la app con el recorte, lo escanea y copia el texto.
 *  - «Imagen»: guarda el recorte en la Galería (Imágenes › EscanerDeTexto).
 */
public class CropActivity extends Activity {

    static final String EXTRA_PATH = "path";
    private static final int YELLOW = Color.rgb(255, 214, 10);
    private static final int INK = Color.rgb(20, 20, 20);

    private Bitmap shot;
    private CropView crop;
    private TextView hint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = getIntent().getStringExtra(EXTRA_PATH);
        shot = path == null ? null : BitmapFactory.decodeFile(path);
        if (shot == null) {
            Toast.makeText(this, "No se pudo abrir la captura", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        float d = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        crop = new CropView(this, shot);
        root.addView(crop, new FrameLayout.LayoutParams(-1, -1));

        hint = new TextView(this);
        hint.setText("Marca con el dedo la zona que quieres");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(15);
        hint.setPadding((int) (16 * d), (int) (10 * d), (int) (16 * d), (int) (10 * d));
        hint.setBackground(round(Color.argb(220, 20, 20, 20), 12 * d));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hp.topMargin = (int) (36 * d);
        root.addView(hint, hp);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding((int) (10 * d), (int) (10 * d), (int) (10 * d), (int) (10 * d));
        bar.setBackground(round(Color.argb(235, 20, 20, 20), 18 * d));
        bar.addView(button("Cancelar", Color.argb(0, 0, 0, 0), Color.WHITE, v -> finish()), weight());
        bar.addView(button("Imagen", Color.rgb(244, 243, 239), INK, v -> saveImage()), weight());
        bar.addView(button("Texto", YELLOW, INK, v -> scanText()), weight());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bp.setMargins((int) (14 * d), 0, (int) (14 * d), (int) (28 * d));
        root.addView(bar, bp);

        crop.setListener(has -> hint.setText(has
                ? "Ajusta el recuadro o elige Texto / Imagen"
                : "Marca con el dedo la zona que quieres (sin recorte = pantalla completa)"));

        setContentView(root);
        hideSystemBars();
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, (int) (50 * getResources().getDisplayMetrics().density), 1);
        int m = (int) (5 * getResources().getDisplayMetrics().density);
        p.setMargins(m, 0, m, 0);
        return p;
    }

    private Button button(String text, int bg, int fg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(fg);
        b.setBackground(round(bg, 12 * getResources().getDisplayMetrics().density));
        b.setOnClickListener(l);
        return b;
    }

    private Bitmap cropped() {
        Rect r = crop.getCropRect();
        return Bitmap.createBitmap(shot, r.left, r.top, r.width(), r.height());
    }

    /** Texto: pasa el recorte a la app, que lo escanea y copia. */
    private void scanText() {
        try {
            Bitmap b = cropped();
            File out = new File(getCacheDir(), "recorte.png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            Intent i = new Intent(this, MainActivity.class)
                    .setAction(MainActivity.ACTION_SCAN)
                    .putExtra(EXTRA_PATH, out.getAbsolutePath())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo preparar el recorte", Toast.LENGTH_LONG).show();
        }
    }

    /** Imagen: guarda el recorte en la Galería. */
    private void saveImage() {
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, "Guardar imágenes requiere Android 10 o superior", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Bitmap b = cropped();
            String name = "Recorte_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EscanerDeTexto");
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
            ContentResolver cr = getContentResolver();
            Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new Exception("sin uri");
            try (OutputStream os = cr.openOutputStream(uri)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            v.clear();
            v.put(MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(uri, v, null, null);
            Toast.makeText(this, "Imagen guardada en Galería › EscanerDeTexto", Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo guardar la imagen", Toast.LENGTH_LONG).show();
        }
    }

}
