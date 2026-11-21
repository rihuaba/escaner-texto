package com.escaner.texto;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Recibe imágenes compartidas desde otras apps (por ejemplo una captura de pantalla)
 * y se las pasa a la página como data URL llamando a window.__escShare(dataUrl).
 */
public class MainActivity extends BridgeActivity {

    private static final int MAX_SIDE = 4096;          // limita imágenes enormes
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String pending = null;
    private int tries = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent);
    }

    private void handleShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String type = intent.getType();
        if (type == null || !type.startsWith("image/")) return;

        Uri uri;
        if (Build.VERSION.SDK_INT >= 33) uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        else uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) return;

        // Evita reprocesar la misma imagen si la actividad se recrea (p. ej. al girar)
        intent.setAction(Intent.ACTION_MAIN);

        final Uri src = uri;
        new Thread(() -> {
            String dataUrl = toDataUrl(src);
            handler.post(() -> {
                pending = dataUrl != null ? dataUrl : "error";
                tries = 0;
                deliver();
            });
        }).start();
    }

    /** Lee la imagen y la devuelve como PNG en base64 (reducida si es gigante). */
    private String toDataUrl(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            bmp.recycle();
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /** Entrega la imagen a la página; reintenta hasta que la página esté lista. */
    private void deliver() {
        if (pending == null) return;
        WebView web = getBridge() != null ? getBridge().getWebView() : null;
        if (web == null) { retry(); return; }
        String arg = "error".equals(pending) ? "null" : "'" + pending + "'";
        String js = "(function(){if(window.__escShare){window.__escShare(" + arg + ");return 'ok'}return 'wait'})()";
        web.evaluateJavascript(js, result -> {
            if (result != null && result.contains("ok")) pending = null;
            else retry();
        });
    }

    private void retry() {
        if (++tries > 60) { pending = null; return; }   // ~30 s como máximo
        handler.postDelayed(this::deliver, 500);
    }
}
