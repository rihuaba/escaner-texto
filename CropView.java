package com.escaner.texto;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Muestra la captura a pantalla completa y deja marcar un recorte con el dedo:
 * arrastrar en zona libre = recorte nuevo; arrastrar dentro = mover; esquinas = ajustar.
 */
public class CropView extends View {

    interface Listener { void onSelectionChanged(boolean hasSelection); }

    private static final int YELLOW = Color.rgb(255, 214, 10);
    private final Bitmap bitmap;
    private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint dim = new Paint();
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();     // dónde se dibuja la imagen en la vista
    private RectF sel = null;                  // recorte en coordenadas de la vista
    private final float density;
    private Listener listener;

    private int mode = 0;                      // 0 nada, 1 nuevo, 2 mover, 3..6 esquinas
    private float sx, sy;
    private RectF startSel;

    public CropView(Context ctx, Bitmap bitmap) {
        super(ctx);
        this.bitmap = bitmap;
        density = ctx.getResources().getDisplayMetrics().density;
        dim.setColor(Color.argb(115, 0, 0, 0));
        border.setColor(YELLOW);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(2.5f * density);
        handle.setColor(YELLOW);
        handleEdge.setColor(Color.rgb(20, 20, 20));
        handleEdge.setStyle(Paint.Style.STROKE);
        handleEdge.setStrokeWidth(2 * density);
    }

    void setListener(Listener l) { listener = l; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float s = Math.min(w / (float) bitmap.getWidth(), h / (float) bitmap.getHeight());
        float bw = bitmap.getWidth() * s, bh = bitmap.getHeight() * s;
        dst.set((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f);
        sel = null;
        notifySel();
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(Color.BLACK);
        c.drawBitmap(bitmap, null, dst, img);
        if (sel == null) {
            c.drawRect(dst, dim);
            return;
        }
        Path p = new Path();
        p.setFillType(Path.FillType.EVEN_ODD);
        p.addRect(dst, Path.Direction.CW);
        p.addRect(sel, Path.Direction.CW);
        c.drawPath(p, dim);
        c.drawRect(sel, border);
        float r = 11 * density;
        for (float[] pt : corners()) {
            c.drawCircle(pt[0], pt[1], r, handle);
            c.drawCircle(pt[0], pt[1], r, handleEdge);
        }
    }

    private float[][] corners() {
        return new float[][]{{sel.left, sel.top}, {sel.right, sel.top}, {sel.left, sel.bottom}, {sel.right, sel.bottom}};
    }

    private float clampX(float x) { return Math.max(dst.left, Math.min(dst.right, x)); }
    private float clampY(float y) { return Math.max(dst.top, Math.min(dst.bottom, y)); }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = clampX(e.getX()), y = clampY(e.getY());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                sx = x; sy = y; mode = 1;
                if (sel != null) {
                    float[][] cs = corners();
                    float hit = 28 * density;
                    for (int i = 0; i < 4; i++) {
                        if (Math.abs(e.getX() - cs[i][0]) < hit && Math.abs(e.getY() - cs[i][1]) < hit) { mode = 3 + i; break; }
                    }
                    if (mode == 1 && sel.contains(e.getX(), e.getY())) mode = 2;
                    startSel = new RectF(sel);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mode == 1) {
                    sel = new RectF(Math.min(sx, x), Math.min(sy, y), Math.max(sx, x), Math.max(sy, y));
                } else if (mode == 2) {
                    float dx = x - sx, dy = y - sy;
                    float l = Math.max(dst.left, Math.min(dst.right - startSel.width(), startSel.left + dx));
                    float t = Math.max(dst.top, Math.min(dst.bottom - startSel.height(), startSel.top + dy));
                    sel = new RectF(l, t, l + startSel.width(), t + startSel.height());
                } else if (mode >= 3) {
                    float l = startSel.left, t = startSel.top, r = startSel.right, b = startSel.bottom;
                    if (mode == 3 || mode == 5) l = x; else r = x;
                    if (mode == 3 || mode == 4) t = y; else b = y;
                    sel = new RectF(Math.min(l, r), Math.min(t, b), Math.max(l, r), Math.max(t, b));
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (sel != null && (sel.width() < 12 * density || sel.height() < 12 * density)) sel = null;
                mode = 0;
                invalidate();
                notifySel();
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void notifySel() { if (listener != null) listener.onSelectionChanged(sel != null); }

    /** Recorte en píxeles reales de la captura (toda la imagen si no hay selección). */
    Rect getCropRect() {
        if (sel == null) return new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        float k = bitmap.getWidth() / dst.width();
        int l = Math.round((sel.left - dst.left) * k), t = Math.round((sel.top - dst.top) * k);
        int r = Math.round((sel.right - dst.left) * k), b = Math.round((sel.bottom - dst.top) * k);
        l = Math.max(0, l); t = Math.max(0, t);
        r = Math.min(bitmap.getWidth(), Math.max(l + 1, r));
        b = Math.min(bitmap.getHeight(), Math.max(t + 1, b));
        return new Rect(l, t, r, b);
    }
}
