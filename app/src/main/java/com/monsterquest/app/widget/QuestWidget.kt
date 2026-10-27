package com.monsterquest.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.monsterquest.app.MainActivity
import com.monsterquest.app.data.SaveRepository
import com.monsterquest.core.GameState
import com.monsterquest.core.HomeWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home-screen widget: today's reminders, upcoming calendar quests and the
 * to-do list. It follows Material 3 (and the wallpaper's colors on Android
 * 12+), resizes freely, and opens the game when tapped.
 */
class QuestWidget : GlanceAppWidget() {
    /** Small: the next few things. Wider or taller: every section. */
    override val sizeMode = SizeMode.Responsive(
        setOf(SMALL, WIDE, LARGE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = withContext(Dispatchers.IO) { SaveRepository(context).load() }
        provideContent {
            GlanceTheme {
                WidgetBody(state)
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)

        /** Redraws every placed widget (after the save changes). */
        suspend fun refresh(context: Context) = QuestWidget().updateAll(context)
    }
}

class QuestWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuestWidget()
}

private sealed interface Entry {
    data class Header(val text: String) : Entry
    data class Item(val line: HomeWidget.Line) : Entry
}

@Composable
private fun WidgetBody(state: GameState) {
    val size = LocalSize.current
    val summary = HomeWidget.summary(state, System.currentTimeMillis())
    val compact = size.height < 180.dp || size.width < 200.dp
    val colors = GlanceTheme.colors
    // Small widgets get one combined list; bigger ones get a section per kind.
    val rows: List<Entry> = if (compact) {
        (summary.reminders.filter { !it.done } + summary.calendar + summary.todos).take(4).map { Entry.Item(it) }
    } else {
        buildList {
            if (summary.reminders.isNotEmpty()) {
                add(Entry.Header("Reminders"))
                summary.reminders.forEach { add(Entry.Item(it)) }
            }
            if (summary.calendar.isNotEmpty()) {
                add(Entry.Header("Calendar"))
                summary.calendar.forEach { add(Entry.Item(it)) }
            }
            if (summary.todos.isNotEmpty()) {
                add(Entry.Header("To-do"))
                summary.todos.forEach { add(Entry.Item(it)) }
            }
        }
    }
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        androidx.glance.layout.Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Monster Quest",
                style = TextStyle(color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text("Lv ${summary.heroLevel}", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp))
        }
        Spacer(GlanceModifier.height(6.dp))
        if (summary.isEmpty) {
            Text("All clear! Nothing due.", style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp))
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(rows) { row ->
                    when (row) {
                        is Entry.Header -> Text(
                            row.text,
                            style = TextStyle(color = colors.secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                        is Entry.Item -> LineRow(row.line)
                    }
                }
            }
        }
    }
}

@Composable
private fun LineRow(line: HomeWidget.Line) {
    val colors = GlanceTheme.colors
    androidx.glance.layout.Row(
        GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(if (line.urgent) colors.secondaryContainer else colors.widgetBackground)
            .cornerRadius(8.dp)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        line.time?.let {
            Text(it, style = TextStyle(color = colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.width(8.dp))
        }
        Text(
            (if (line.done) "✓ " else "") + line.title,
            style = TextStyle(
                color = if (line.done) colors.onSurfaceVariant else if (line.urgent) colors.onSecondaryContainer else colors.onSurface,
                fontSize = 13.sp,
            ),
            maxLines = 1,
        )
    }
}
