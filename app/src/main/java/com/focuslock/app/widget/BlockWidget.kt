package com.focuslock.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.focuslock.app.MainActivity
import com.focuslock.app.R
import com.focuslock.app.stats.BlockLog

/** Home-screen widget: "Avoided today: N". Refreshed by the service after every block. */
class BlockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BlockWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, views(context)) }
        }

        private fun views(context: Context): RemoteViews {
            val log = BlockLog.get(context)
            val today = log.todayCount()
            val week = log.events.value.count { it.at > System.currentTimeMillis() - 7 * 86_400_000L }
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return RemoteViews(context.packageName, R.layout.widget_blocks).apply {
                setTextViewText(R.id.widget_count, today.toString())
                setTextViewText(R.id.widget_week, "this week: $week")
                setOnClickPendingIntent(R.id.widget_root, open)
            }
        }
    }
}
