package com.plane.tracker.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plane.tracker.ui.theme.*

/**
 * Solari split-flap text — each character renders in its own cell
 * with a vertical slide animation when it changes.
 */
@Composable
fun SolariText(
    text: String,
    maxChars: Int = text.length,
    modifier: Modifier = Modifier,
    fontSize: Float = 28f
) {
    val padded = text.padEnd(maxChars).take(maxChars)

    Row(modifier = modifier, horizontalArrangement = Arrangement.Center) {
        padded.forEach { char ->
            SolariCell(char = char, fontSize = fontSize)
            Spacer(modifier = Modifier.width(2.dp))
        }
    }
}

@Composable
private fun SolariCell(char: Char, fontSize: Float) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(SolariSurface)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = char,
            transitionSpec = {
                slideInVertically { -it } + fadeIn() togetherWith
                slideOutVertically { it } + fadeOut()
            },
            label = "solariFlap"
        ) { targetChar ->
            Text(
                text = targetChar.toString(),
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Bold,
                color = SolariTextPrimary,
                fontFamily = SolariMono,
                textAlign = TextAlign.Center
            )
        }
    }
}
