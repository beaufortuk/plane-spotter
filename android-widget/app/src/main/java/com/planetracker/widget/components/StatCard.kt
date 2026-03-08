package com.planetracker.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.planetracker.util.Constants

/** Stat card for the stats grid */
@Composable
fun StatCard(
    label: String,
    value: String,
    unit: String,
    accentColor: Color = Constants.GoldAccent,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .background(ColorProvider(Constants.FlapBg)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(Constants.TextDim),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        )

        Spacer(GlanceModifier.height(2.dp))

        // Value + unit row
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = TextStyle(
                    color = ColorProvider(Constants.TextPrimary),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
            Spacer(GlanceModifier.width(3.dp))
            Text(
                text = unit,
                style = TextStyle(
                    color = ColorProvider(Constants.TextDim),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

/** Vertical speed stat card with color coding */
@Composable
fun VerticalSpeedCard(
    vertRateFpm: Int,
    modifier: GlanceModifier = GlanceModifier
) {
    val (text, _) = when {
        vertRateFpm > 200 -> "↑ Climbing" to Constants.ClimbGreen
        vertRateFpm < -200 -> "↓ Descending" to Constants.DescentRed
        else -> "→ Level" to Constants.LevelBlue
    }

    val statusColor = when {
        vertRateFpm > 200 -> Constants.ClimbGreen
        vertRateFpm < -200 -> Constants.DescentRed
        else -> Constants.LevelBlue
    }

    val absFpm = kotlin.math.abs(vertRateFpm)
    val fpmText = when {
        vertRateFpm > 200 -> "+${"%,d".format(absFpm)} fpm"
        vertRateFpm < -200 -> "−${"%,d".format(absFpm)} fpm"
        else -> ""
    }

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .background(ColorProvider(Constants.FlapBg)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = ColorProvider(statusColor),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        )

        if (fpmText.isNotEmpty()) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = fpmText,
                style = TextStyle(
                    color = ColorProvider(Constants.TextDim),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = "CLIMB RATE",
            style = TextStyle(
                color = ColorProvider(Constants.TextDim),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}
