package com.oinky.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import com.oinky.app.ui.theme.DarkScheme
import com.oinky.app.ui.theme.LightScheme
import com.oinky.core.MoodFace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.oinky.app.MainActivity
import com.oinky.app.OinkyApp
import com.oinky.app.data.TripEntity
import com.oinky.app.work.Notifications
import com.oinky.core.Countries
import com.oinky.core.Currencies
import com.oinky.core.Mood
import com.oinky.core.TxnType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Everything the widget shows about today. */
data class WidgetData(
    val date: LocalDate,
    val mood: Mood? = null,
    val spent: BigDecimal = BigDecimal.ZERO,
    val entries: Int = 0,
    val mainCurrency: String = "SGD",
    val trip: TripEntity? = null,
    val tripFlag: String? = null,
    /** The user's mood faces, best first; sticker faces carry a small decoded bitmap. */
    val faces: List<WidgetFace> = Mood.entries.map { WidgetFace(MoodFace.default(it), null) },
)

data class WidgetFace(val face: MoodFace, val bitmap: Bitmap?)

/** DESIGN.md colours for the widget (brand palette in light and dark). */
private val WidgetColors = ColorProviders(light = LightScheme, dark = DarkScheme)

/**
 * Home-screen widget: today's spending and mood at a glance, a pill that opens the floating
 * quick-entry dialog, and one-tap mood buttons.
 */
class OinkyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val data = todayFlow(context, today)
        provideContent {
            val state by remember { data }.collectAsState(initial = WidgetData(today))
            GlanceTheme(colors = WidgetColors) { Content(state) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun todayFlow(context: Context, today: LocalDate): Flow<WidgetData> {
        val c = (context.applicationContext as OinkyApp).container
        val day = today.toEpochDay()
        val tripWithFlag: Flow<Pair<TripEntity?, String?>> = c.trips.observeTripOn(day).flatMapLatest { trip ->
            if (trip == null) flowOf(null to null)
            else c.trips.observePlaces(trip.id).map { places ->
                trip to (places.firstOrNull()?.let { Countries.flag(it.countryCode) } ?: trip.emoji)
            }
        }
        val faces = c.settings.moodFaces.map { list ->
            list.sortedByDescending { it.score }.map { f -> WidgetFace(f, f.stickerPath?.let { decodeSmall(it) }) }
        }
        return combine(
            c.ledger.observeDay(day),
            c.ledger.observeTxns(day),
            c.settings.mainCurrency,
            tripWithFlag,
            faces,
        ) { entry, txns, main, (trip, flag), widgetFaces ->
            WidgetData(
                date = today,
                mood = entry?.mood,
                spent = txns.filter { it.type == TxnType.EXPENSE }.fold(BigDecimal.ZERO) { a, t -> a + t.baseAmount },
                entries = txns.size,
                mainCurrency = main,
                trip = trip,
                tripFlag = flag,
                faces = widgetFaces,
            )
        }
    }

    @Composable
    private fun Content(d: WidgetData) {
        val colors = GlanceTheme.colors
        Column(
            modifier = GlanceModifier.fillMaxSize().background(colors.widgetBackground).cornerRadius(24.dp).padding(12.dp),
        ) {
            // Header → today's diary page.
            Row(
                modifier = GlanceModifier.fillMaxWidth().clickable(openToday()),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        "🐷 Today · ${d.date.format(DateTimeFormatter.ofPattern("EEE d MMM"))}",
                        style = TextStyle(color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    Text(
                        if (d.entries == 0) "Nothing logged yet" else
                            "Spent ${Currencies.format(d.spent, d.mainCurrency)} · ${d.entries} entr${if (d.entries == 1) "y" else "ies"}",
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
                d.trip?.let { trip ->
                    val dayNo = trip.range.dayNumber(d.date)
                    Text(
                        "${d.tripFlag ?: trip.emoji} Day $dayNo",
                        style = TextStyle(color = colors.onSecondaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        modifier = GlanceModifier.background(colors.secondaryContainer).cornerRadius(12.dp)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            // Pill → floating quick-entry dialog.
            val hint = d.trip?.localCurrency?.takeIf { it != d.mainCurrency }?.let { "1500 ramen ($it)…" } ?: "rm135 on dinner…"
            Box(
                modifier = GlanceModifier.fillMaxWidth().background(colors.primaryContainer).cornerRadius(20.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .clickable(actionStartActivity<QuickEntryActivity>()),
            ) {
                Text("✏️  $hint", style = TextStyle(color = colors.onPrimaryContainer, fontSize = 14.sp), maxLines = 1)
            }

            Spacer(GlanceModifier.height(8.dp))

            // One-tap mood for today.
            Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                d.faces.forEach { wf ->
                    val m = Mood.fromScore(wf.face.score) ?: return@forEach
                    val selected = m == d.mood
                    Box(
                        modifier = GlanceModifier.defaultWeight()
                            .then(if (selected) GlanceModifier.background(colors.secondaryContainer).cornerRadius(14.dp) else GlanceModifier)
                            .padding(vertical = 4.dp)
                            .clickable(actionRunCallback<SetMoodAction>(actionParametersOf(SetMoodAction.scoreKey to m.score))),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (wf.bitmap != null) {
                            Image(
                                ImageProvider(wf.bitmap), contentDescription = wf.face.label,
                                modifier = GlanceModifier.size(if (selected) 30.dp else 26.dp),
                            )
                        } else {
                            Text(wf.face.emoji, style = TextStyle(fontSize = if (selected) 24.sp else 20.sp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun openToday() = actionStartActivity(
        Intent(androidx.glance.LocalContext.current, MainActivity::class.java)
            .putExtra(Notifications.EXTRA_DESTINATION, "today")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )
}

/** Widgets travel as RemoteViews bitmaps, so sticker faces are decoded small (~96px). */
private fun decodeSmall(path: String): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 96) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

/** Sets (or clears, when tapped again) today's mood straight from the widget. */
class SetMoodAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val score = parameters[scoreKey] ?: return
        val c = (context.applicationContext as OinkyApp).container
        val day = LocalDate.now().toEpochDay()
        val current = c.db.days().get(day)?.moodScore
        c.ledger.setMood(day, if (current == score) null else score)
    }

    companion object {
        val scoreKey = ActionParameters.Key<Int>("score")
    }
}

class OinkyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OinkyWidget()
}
