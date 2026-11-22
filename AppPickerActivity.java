package com.escaner.texto;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lista de apps instaladas para el botón «Recortar otra app».
 * Al tocar una: se abre esa app (en el punto donde la dejaste) y encima
 * se inicia la captura, como con el botón de ajustes rápidos.
 */
public class AppPickerActivity extends Activity {

    private static class Item {
        String label, pkg;
        Drawable icon;
    }

    private final List<Item> all = new ArrayList<>();
    private final List<Item> shown = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BaseAdapter adapter;
    private TextView status;
    private float d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(244, 243, 239));
        root.setPadding(px(16), px(28), px(16), px(8));

        TextView title = new TextView(this);
        title.setText("¿Qué app quieres recortar?");
        title.setTextColor(Color.rgb(20, 20, 20));
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Se abrirá donde la dejaste y podrás marcar el recorte.");
        sub.setTextColor(Color.rgb(92, 92, 92));
        sub.setTextSize(14);
        sub.setPadding(0, px(4), 0, px(12));
        root.addView(sub);

        EditText search = new EditText(this);
        search.setHint("Buscar app");
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTextColor(Color.rgb(20, 20, 20));
        search.setHintTextColor(Color.rgb(140, 140, 140));
        search.setPadding(px(14), px(12), px(14), px(12));
        GradientDrawable box = new GradientDrawable();
        box.setColor(Color.WHITE);
        box.setCornerRadius(px(12));
        box.setStroke(px(1), Color.rgb(222, 220, 212));
        search.setBackground(box);
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("Cargando apps…");
        status.setTextColor(Color.rgb(92, 92, 92));
        status.setPadding(0, px(12), 0, px(4));
        root.addView(status);

        ListView list = new ListView(this);
        list.setDivider(null);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) { return row(shown.get(i), v); }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> open(shown.get(pos)));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });

        setContentView(root);
        loadApps();
    }

    private int px(float v) { return (int) (v * d); }

    private View row(Item it, View convert) {
        LinearLayout r;
        ImageView icon;
        TextView label;
        if (convert instanceof LinearLayout) {
            r = (LinearLayout) convert;
            icon = (ImageView) r.getChildAt(0);
            label = (TextView) r.getChildAt(1);
        } else {
            r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(px(4), px(10), px(4), px(10));
            icon = new ImageView(this);
            r.addView(icon, new LinearLayout.LayoutParams(px(40), px(40)));
            label = new TextView(this);
            label.setTextSize(17);
            label.setTextColor(Color.rgb(20, 20, 20));
            label.setPadding(px(14), 0, 0, 0);
            r.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        }
        icon.setImageDrawable(it.icon);
        label.setText(it.label);
        return r;
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
            List<Item> items = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : ris) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                Item it = new Item();
                it.pkg = pkg;
                it.label = String.valueOf(ri.loadLabel(pm));
                it.icon = ri.loadIcon(pm);
                items.add(it);
            }
            Collator col = Collator.getInstance(new Locale("es"));
            java.util.Collections.sort(items, (a, b) -> col.compare(a.label, b.label));
            handler.post(() -> {
                all.clear();
                all.addAll(items);
                filter("");
            });
        }).start();
    }

    private void filter(String q) {
        String needle = q.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Item it : all) if (needle.isEmpty() || it.label.toLowerCase(Locale.ROOT).contains(needle)) shown.add(it);
        status.setText(shown.isEmpty() ? (all.isEmpty() ? "Cargando apps…" : "Ninguna app coincide") : "Toca la app que quieres recortar");
        adapter.notifyDataSetChanged();
    }

    private void open(Item it) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(it.pkg);
        if (launch == null) {
            Toast.makeText(this, "No se pudo abrir esa app", Toast.LENGTH_LONG).show();
            return;
        }
        // Abre la app (vuelve a su última pantalla si ya estaba abierta)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
        // Encima, la captura (invisible) que pide permiso y muestra el recorte
        startActivity(new Intent(this, CaptureActivity.class)
                .putExtra(CaptureActivity.EXTRA_FROM_APP, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
        overridePendingTransition(0, 0);
    }
}
