package com.escaner.texto;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Pantalla de recorte: muestra la captura congelada, dejas marcar la zona y eliges
 *  - «Copiar texto»: lee el texto del recorte (ML Kit, sin internet) y lo copia al portapapeles.
 *  - «Copiar imagen»: copia el recorte como imagen al portapapeles.
 * En ambos casos vuelves a la app donde estabas, listo para pegar.
 */
public class CropActivity extends Activity {

    static final String EXTRA_PATH = "path";
    private static final int YELLOW = Color.rgb(255, 214, 10);
    private static final int INK = Color.rgb(20, 20, 20);

    private Bitmap shot;
    private CropView crop;
    private TextView hint;
    private LinearLayout bar;
    private boolean working = false;

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
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(15);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding((int) (16 * d), (int) (10 * d), (int) (16 * d), (int) (10 * d));
        hint.setBackground(round(Color.argb(220, 20, 20, 20), 12 * d));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hp.topMargin = (int) (36 * d);
        hp.setMargins((int) (16 * d), (int) (36 * d), (int) (16 * d), 0);
        root.addView(hint, hp);
        setHint(false);

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding((int) (8 * d), (int) (10 * d), (int) (8 * d), (int) (10 * d));
        bar.setBackground(round(Color.argb(235, 20, 20, 20), 18 * d));
        bar.addView(button("Cancelar", Color.argb(0, 0, 0, 0), Color.WHITE, v -> finish()), weight(0.8f));
        bar.addView(button("Copiar imagen", Color.rgb(244, 243, 239), INK, v -> copyImage()), weight(1.1f));
        bar.addView(button("Copiar texto", YELLOW, INK, v -> copyText()), weight(1.1f));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bp.setMargins((int) (12 * d), 0, (int) (12 * d), (int) (28 * d));
        root.addView(bar, bp);

        crop.setListener(this::setHint);
        setContentView(root);
        hideSystemBars();
    }

    private void setHint(boolean hasSelection) {
        hint.setText(hasSelection
                ? "Ajusta el recuadro y elige qué copiar"
                : "Marca con el dedo la zona que quieres copiar");
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

    private LinearLayout.LayoutParams weight(float w) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, (int) (52 * d), w);
        int m = (int) (4 * d);
        p.setMargins(m, 0, m, 0);
        return p;
    }

    private Button button(String text, int bg, int fg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(fg);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(round(bg, 12 * getResources().getDisplayMetrics().density));
        b.setOnClickListener(l);
        return b;
    }

    private Bitmap cropped() {
        Rect r = crop.getCropRect();
        return Bitmap.createBitmap(shot, r.left, r.top, r.width(), r.height());
    }

    private void setWorking(boolean w, String msg) {
        working = w;
        bar.setAlpha(w ? 0.5f : 1f);
        for (int i = 0; i < bar.getChildCount(); i++) bar.getChildAt(i).setEnabled(!w);
        if (msg != null) hint.setText(msg);
    }

    /** Copiar texto: lee el recorte con ML Kit y lo deja en el portapapeles. */
    private void copyText() {
        if (working) return;
        setWorking(true, "Leyendo texto…");
        final Bitmap b;
        try {
            b = cropped();
        } catch (Exception e) {
            setWorking(false, "No se pudo preparar el recorte");
            return;
        }
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(InputImage.fromBitmap(b, 0))
                .addOnSuccessListener(result -> {
                    String text = result.getText() == null ? "" : result.getText().trim();
                    recognizer.close();
                    if (text.isEmpty()) {
                        setWorking(false, "No se encontró texto. Prueba con otro recorte.");
                        return;
                    }
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("Texto escaneado", text));
                    Toast.makeText(this, "Texto copiado, listo para pegar", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    recognizer.close();
                    setWorking(false, "No se pudo leer el texto. Inténtalo de nuevo.");
                });
    }

    /** Copiar imagen: guarda el recorte y lo deja en el portapapeles como imagen. */
    private void copyImage() {
        if (working) return;
        setWorking(true, "Copiando imagen…");
        try {
            Bitmap b = cropped();
            File dir = new File(getCacheDir(), "recortes");
            if (!dir.exists()) dir.mkdirs();
            // Borra recortes viejos para no llenar la memoria
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) f.delete();
            File out = new File(dir, "recorte_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", out);
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newUri(getContentResolver(), "Recorte", uri));
            Toast.makeText(this, "Imagen copiada, lista para pegar", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            setWorking(false, "No se pudo copiar la imagen. Inténtalo de nuevo.");
        }
    }
}
