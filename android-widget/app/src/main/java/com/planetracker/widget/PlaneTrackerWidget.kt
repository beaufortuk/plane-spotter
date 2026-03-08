package com.planetracker.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.state.GlanceStateDefinition
import java.io.File

/** Main Glance widget with responsive sizing */
class PlaneTrackerWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<WidgetState> =
        PlaneTrackerStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(
            SMALL_SIZE,
            MEDIUM_SIZE,
            LARGE_SIZE
        )
    )

    @Composable
    override fun Content() {
        val state = currentState<WidgetState>()
        val size = LocalSize.current

        when {
            size.width >= LARGE_SIZE.width && size.height >= LARGE_SIZE.height ->
                LargeWidgetContent(state)
            size.width >= MEDIUM_SIZE.width ->
                MediumWidgetContent(state)
            else ->
                SmallWidgetContent(state)
        }
    }

    companion object {
        private val SMALL_SIZE = DpSize(110.dp, 110.dp)
        private val MEDIUM_SIZE = DpSize(250.dp, 110.dp)
        private val LARGE_SIZE = DpSize(250.dp, 250.dp)
    }
}

/** Widget receiver — manages lifecycle and triggers WorkManager */
class PlaneTrackerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaneTrackerWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Start periodic background updates
        PlaneTrackerWorker.schedule(context)
        // Trigger an immediate first update
        PlaneTrackerWorker.refreshNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Cancel background work when no widgets remain
        PlaneTrackerWorker.cancel(context)
    }
}
