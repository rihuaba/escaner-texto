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
    static final String EXTRA_FROM_APP = "fromApp";
    private boolean fromApp = false;
    static CaptureActivity current = null;
    private boolean waiting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Botón «Recortar otra app» dentro de la app (enlace escaner://capture):
        // se relanza en su propia tarea y la app principal pasa a segundo plano,
        // así queda visible la app que se estaba usando antes.
        Intent in = getIntent();
        boolean fromAppButton = in != null && Intent.ACTION_VIEW.equals(in.getAction());
        if (fromAppButton) fromApp = true;
        // Android 13 o anterior: no hay selector de apps; se muestra la app anterior y se captura la pantalla
        if (savedInstanceState == null && fromAppButton && Build.VERSION.SDK_INT < 34) {
            startActivity(new Intent(this, CaptureActivity.class)
                    .putExtra(EXTRA_FROM_APP, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            MainActivity main = MainActivity.current;
            finish();
            overridePendingTransition(0, 0);
            if (main != null) main.moveTaskToBack(true);
            return;
        }
        current = this;
        if (in != null && in.getBooleanExtra(EXTRA_FROM_APP, false)) fromApp = true;
        if (savedInstanceState != null) fromApp = savedInstanceState.getBoolean("fromApp", fromApp);
        if (savedInstanceState != null && savedInstanceState.getBoolean("waiting")) {
            waiting = true;       // ya se pidió el permiso; esperamos la captura
            return;
        }
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent ask;
        if (Build.VERSION.SDK_INT >= 34 && fromApp) {
            // Botón de la app (Android 14+): Android deja elegir «Una sola app» y muestra
            // la lista de apps abiertas para escoger cuál recortar
            ask = mpm.createScreenCaptureIntent();
        } else if (Build.VERSION.SDK_INT >= 34) {
            // Botón de ajustes rápidos: pantalla completa (ya estás en la app que quieres)
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
        out.putBoolean("fromApp", fromApp);
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
                .putExtra(CaptureService.EXTRA_DATA, data)
                // Si viene del botón de la app, espera un poco más a que se vea la otra app
                .putExtra(CaptureService.EXTRA_DELAY, fromApp ? 1000 : 450);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
        else startService(svc);
    }

    /** Lo llama CaptureService cuando terminó (path == null si falló). */
    void onCaptured(String path) {
        if (path != null) {
            // En su propia tarea: al terminar vuelves a la app recortada, lista para pegar
            startActivity(new Intent(this, CropActivity.class)
                    .putExtra(CropActivity.EXTRA_PATH, path)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else {
            Toast.makeText(this, CaptureService.lastWasBlack
                    ? "Esa app no permite capturas de pantalla (está protegida). Prueba con otra."
                    : "No se pudo capturar la pantalla", Toast.LENGTH_LONG).show();
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
