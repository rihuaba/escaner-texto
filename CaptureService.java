package com.escaner.texto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/** Servicio en primer plano que toma UNA captura de la pantalla con MediaProjection. */
public class CaptureService extends Service {

    static final String EXTRA_CODE = "code";
    static final String EXTRA_DATA = "data";
    private static final String CHANNEL = "captura";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private boolean done = false;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        if (intent == null) { finish(null); return START_NOT_STICKY; }
        final int code = intent.getIntExtra(EXTRA_CODE, 0);
        final Intent data = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(EXTRA_DATA, Intent.class)
                : intent.getParcelableExtra(EXTRA_DATA);
        // Espera breve para que desaparezca el aviso de permiso antes de capturar
        handler.postDelayed(() -> start(code, data), 450);
        // Si algo se queda trabado, se cancela a los 8 s
        handler.postDelayed(() -> { if (!done) finish(null); }, 8000);
        return START_NOT_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Captura de pantalla", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Notification n = b.setSmallIcon(R.drawable.ic_tile)
                .setContentTitle("Escáner de texto")
                .setContentText("Capturando la pantalla…")
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(1, n);
    }

    private void start(int code, Intent data) {
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
            if (projection == null) { finish(null); return; }
            // Android 14 exige registrar el callback antes de crear la pantalla virtual
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { if (!done) finish(null); }
            }, handler);

            int w, h, dpi;
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (Build.VERSION.SDK_INT >= 30) {
                Rect r = wm.getMaximumWindowMetrics().getBounds();
                w = r.width(); h = r.height();
            } else {
                DisplayMetrics m = new DisplayMetrics();
                wm.getDefaultDisplay().getRealMetrics(m);
                w = m.widthPixels; h = m.heightPixels;
            }
            dpi = getResources().getDisplayMetrics().densityDpi;

            final int fw = w, fh = h;
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
            reader.setOnImageAvailableListener(r -> {
                Image img = null;
                try {
                    img = r.acquireLatestImage();
                    if (img == null || done) return;
                    Image.Plane p = img.getPlanes()[0];
                    ByteBuffer buf = p.getBuffer();
                    int pixelStride = p.getPixelStride(), rowStride = p.getRowStride();
                    int padding = rowStride - pixelStride * fw;
                    Bitmap full = Bitmap.createBitmap(fw + padding / pixelStride, fh, Bitmap.Config.ARGB_8888);
                    full.copyPixelsFromBuffer(buf);
                    img.close();
                    img = null;
                    Bitmap bmp = Bitmap.createBitmap(full, 0, 0, fw, fh);
                    if (bmp != full) full.recycle();
                    File out = new File(getCacheDir(), "captura.png");
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    }
                    bmp.recycle();
                    finish(out.getAbsolutePath());
                } catch (Exception e) {
                    finish(null);
                } finally {
                    if (img != null) img.close();
                }
            }, handler);
            display = projection.createVirtualDisplay("escaner", w, h, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
        } catch (Exception e) {
            finish(null);
        }
    }

    private void finish(String path) {
        if (done) return;
        done = true;
        try { if (display != null) display.release(); } catch (Exception ignored) {}
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        try { if (projection != null) projection.stop(); } catch (Exception ignored) {}
        CaptureActivity a = CaptureActivity.current;
        if (a != null) {
            a.onCaptured(path);
        } else if (path != null) {
            Intent i = new Intent(this, CropActivity.class)
                    .putExtra(CropActivity.EXTRA_PATH, path)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (Exception ignored) {}
        }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(Service.STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }
}
