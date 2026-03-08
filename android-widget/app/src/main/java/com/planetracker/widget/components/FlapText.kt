package com.planetracker.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.planetracker.util.Constants

/** Single split-flap character tile */
@Composable
fun FlapCharacter(ch: Char, isLarge: Boolean = false) {
    if (ch == ' ') {
        Spacer(GlanceModifier.width(if (isLarge) 6.dp else 4.dp))
        return
    }

    Box(
        modifier = GlanceModifier
            .size(
                width = if (isLarge) 28.dp else 14.dp,
                height = if (isLarge) 36.dp else 20.dp
            )
            .background(ColorProvider(Constants.FlapBg)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = ch.toString(),
            style = TextStyle(
                color = ColorProvider(Constants.TextPrimary),
                fontSize = if (isLarge) 22.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        )
    }
}

/** Row of split-flap characters */
@Composable
fun FlapText(text: String, isLarge: Boolean = false) {
    Row(
        modifier = GlanceModifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        text.forEachIndexed { index, ch ->
            if (index > 0) {
                Spacer(GlanceModifier.width(if (isLarge) 3.dp else 1.dp))
            }
            FlapCharacter(ch, isLarge)
        }
    }
}
