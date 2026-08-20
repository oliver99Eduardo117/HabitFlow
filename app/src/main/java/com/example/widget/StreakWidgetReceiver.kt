package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        WidgetRepositoryProvider.startObserving(context.applicationContext)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRepositoryProvider.startObserving(context.applicationContext)
    }
}

