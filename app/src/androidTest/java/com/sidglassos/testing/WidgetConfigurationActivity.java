package com.sidglassos.testing;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

/** Platform-only Java lets this fixture run in its own process without the target APK's Kotlin runtime. */
public class WidgetConfigurationActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return; }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(padding + bars.left, padding + bars.top,
                    padding + bars.right, padding + bars.bottom);
            } else {
                view.setPadding(padding + insets.getSystemWindowInsetLeft(), padding + insets.getSystemWindowInsetTop(),
                    padding + insets.getSystemWindowInsetRight(), padding + insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        TextView title = new TextView(this);
        title.setText("Configure test widget");
        title.setTextSize(24);
        layout.addView(title);
        Button save = new Button(this);
        save.setAllCaps(false);
        save.setText("Save test widget");
        save.setOnClickListener(view -> {
            AppWidgetManager.getInstance(this).updateAppWidget(id,
                new RemoteViews(getPackageName(), com.sidglassos.test.R.layout.test_widget));
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
            finish();
        });
        layout.addView(save);
        Button cancel = new Button(this);
        cancel.setAllCaps(false);
        cancel.setText("Cancel test widget");
        cancel.setOnClickListener(view -> finish());
        layout.addView(cancel);
        setContentView(layout);
        layout.requestApplyInsets();
    }
}
