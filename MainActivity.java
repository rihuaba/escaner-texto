package com.escaner.texto;

import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * - Recibe imágenes compartidas desde otras apps (Compartir → Escáner de texto).
 * - Recibe recortes de CropActivity con la acción ACTION_SCAN (escanear y copiar al momento).
 * La imagen llega a la página como data URL llamando a window.__escShare(dataUrl, auto).
 * - En Android 13+ ofrece una vez agregar el botón «Escanear» a los ajustes rápidos.
 */
public class MainActivity extends BridgeActivity {

    static final String ACTION_SCAN = "com.escaner.texto.ESCANEAR";
    static MainActivity current = null;
    private static final int MAX_SIDE = 4096;          // limita imágenes enormes
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pending = null;
    private boolean pendingAuto = false;
    private int tries = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = this;
        handleIntent(getIntent());
        handler.postDelayed(this::offerQuickTile, 2500);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        final String action = intent.getAction();

        if (ACTION_SCAN.equals(action)) {
            final String path = intent.getStringExtra(CropActivity.EXTRA_PATH);
            intent.setAction(Intent.ACTION_MAIN);
            if (path == null) return;
            load(() -> toDataUrlFromFile(new File(path)), true);
            return;
        }

        if (!Intent.ACTION_SEND.equals(action)) return;
        String type = intent.getType();
        if (type == null || !type.startsWith("image/")) return;
        Uri uri;
        if (Build.VERSION.SDK_INT >= 33) uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        else uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) return;
        // Evita reprocesar la misma imagen si la actividad se recrea (p. ej. al girar)
        intent.setAction(Intent.ACTION_MAIN);
        final Uri src = uri;
        load(() -> toDataUrlFromUri(src), false);
    }

    private interface Loader { String get(); }

    private void load(Loader loader, boolean auto) {
        new Thread(() -> {
            String dataUrl = loader.get();
            handler.post(() -> {
                pending = dataUrl != null ? dataUrl : "error";
                pendingAuto = auto;
                tries = 0;
                deliver();
            });
        }).start();
    }

    private String toDataUrlFromUri(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleFor(bounds);
            Bitmap bmp;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            return encode(bmp);
        } catch (Exception e) {
            return null;
        }
    }

    private String toDataUrlFromFile(File f) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = new FileInputStream(f)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleFor(bounds);
            Bitmap bmp;
            try (InputStream in = new FileInputStream(f)) {
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            return encode(bmp);
        } catch (Exception e) {
            return null;
        }
    }

    private int sampleFor(BitmapFactory.Options b) {
        int sample = 1;
        while (Math.max(b.outWidth, b.outHeight) / sample > MAX_SIDE) sample *= 2;
        return sample;
    }

    private String encode(Bitmap bmp) {
        if (bmp == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        bmp.recycle();
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    /** Entrega la imagen a la página; reintenta hasta que la página esté lista. */
    private void deliver() {
        if (pending == null) return;
        WebView web = getBridge() != null ? getBridge().getWebView() : null;
        if (web == null) { retry(); return; }
        String arg = "error".equals(pending) ? "null" : "'" + pending + "'";
        String js = "(function(){if(window.__escShare){window.__escShare(" + arg + "," + pendingAuto + ");return 'ok'}return 'wait'})()";
        web.evaluateJavascript(js, result -> {
            if (result != null && result.contains("ok")) pending = null;
            else retry();
        });
    }

    private void retry() {
        if (++tries > 60) { pending = null; return; }   // ~30 s como máximo
        handler.postDelayed(this::deliver, 500);
    }

    @Override
    public void onResume() {
        super.onResume();
        current = this;
    }

    @Override
    public void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }

    /** Android 13+: ofrece una sola vez agregar el botón a los ajustes rápidos. */
    private void offerQuickTile() {
        if (Build.VERSION.SDK_INT < 33 || isFinishing()) return;
        SharedPreferences prefs = getSharedPreferences("escaner", MODE_PRIVATE);
        if (prefs.getBoolean("tileOffered", false)) return;
        prefs.edit().putBoolean("tileOffered", true).apply();
        try {
            StatusBarManager sbm = getSystemService(StatusBarManager.class);
            sbm.requestAddTileService(
                    new ComponentName(this, CaptureTileService.class),
                    "Escanear",
                    Icon.createWithResource(this, R.drawable.ic_tile),
                    getMainExecutor(),
                    result -> { });
        } catch (Exception ignored) { }
    }
}
