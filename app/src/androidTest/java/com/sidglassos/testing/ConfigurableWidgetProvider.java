package com.sidglassos.testing;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

/** A real second-package provider, present only in the instrumentation APK. */
public class ConfigurableWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            manager.updateAppWidget(id, new RemoteViews(context.getPackageName(), com.sidglassos.test.R.layout.test_widget));
        }
    }
}
