package com.escaner.texto;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Actividad transparente: pide a Android permiso para capturar la pantalla,
 * arranca CaptureService y, cuando la captura está lista, abre CropActivity.
 * Se queda abierta (invisible) mientras dura la captura para poder abrir el recorte
 * desde primer plano, como exige Android.
 */
public class CaptureActivity extends Activity {

    private static final int REQ = 7001;
    static CaptureActivity current = null;
    private boolean waiting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = this;
        if (savedInstanceState != null && savedInstanceState.getBoolean("waiting")) {
            waiting = true;       // ya se pidió el permiso; esperamos la captura
            return;
        }
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent ask;
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+: pedir directamente la pantalla completa (no «una sola app»)
            ask = mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
        } else {
            ask = mpm.createScreenCaptureIntent();
        }
        try {
            startActivityForResult(ask, REQ);
        } catch (Exception e) {
            Toast.makeText(this, "Este teléfono no permite capturar la pantalla", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("waiting", waiting);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ) return;
        if (resultCode != RESULT_OK || data == null) {
            finish();
            return;
        }
        waiting = true;
        Intent svc = new Intent(this, CaptureService.class)
                .putExtra(CaptureService.EXTRA_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_DATA, data);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
        else startService(svc);
    }

    /** Lo llama CaptureService cuando terminó (path == null si falló). */
    void onCaptured(String path) {
        if (path != null) {
            startActivity(new Intent(this, CropActivity.class).putExtra(CropActivity.EXTRA_PATH, path));
        } else {
            Toast.makeText(this, "No se pudo capturar la pantalla", Toast.LENGTH_LONG).show();
        }
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        super.onDestroy();
    }
}
