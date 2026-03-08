package com.plane.tracker.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plane.tracker.domain.FlightPhase
import com.plane.tracker.ui.theme.*
import kotlin.math.*

/**
 * Flight arc — cubic bezier with CLIMB/CRUISE/DESCENT phases.
 * Direct port of the web app's renderArc() algorithm.
 */
@Composable
fun FlightArc(
    origin: String,
    dest: String,
    originName: String,
    destName: String,
    progress: Float,   // 0..1
    phase: FlightPhase,
    modifier: Modifier = Modifier
) {
    val hasRoute = origin.isNotBlank() && dest.isNotBlank()

    // Pulsing animation for origin dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseAlpha"
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (hasRoute) {
            // IATA codes above arc
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(origin, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        color = SolariTextPrimary, fontFamily = SolariMono, letterSpacing = 2.sp)
                    Text(originName, fontSize = 11.sp, color = SolariTextSecondary,
                        fontFamily = SolariMono)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(dest, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        color = SolariTextPrimary, fontFamily = SolariMono, letterSpacing = 2.sp)
                    Text(destName, fontSize = 11.sp, color = SolariTextSecondary,
                        fontFamily = SolariMono)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Arc canvas
            Canvas(
                modifier = Modifier.fillMaxWidth().height(100.dp)
            ) {
                drawFlightArc(progress, phase, pulseAlpha)
            }
        }
    }
}

private fun DrawScope.drawFlightArc(progress: Float, phase: FlightPhase, pulseAlpha: Float) {
    val w = size.width
    val h = size.height
    val pad = 20f
    val baseline = h - 12f
    val peakY = 20f

    // Phase fractions
    val climbF = 0.18f
    val cruiseF = 0.60f
    // descentF = 0.22f

    val climbEnd = pad + (w - 2 * pad) * climbF
    val cruiseEnd = pad + (w - 2 * pad) * (climbF + cruiseF)
    val endX = w - pad

    // Control points for climb bezier
    val p0 = Offset(pad, baseline)
    val p1 = Offset(pad + (climbEnd - pad) * 0.3f, baseline)
    val p2 = Offset(climbEnd - (climbEnd - pad) * 0.3f, peakY)
    val p3 = Offset(climbEnd, peakY)

    // Cruise is a straight line at peakY
    val c0 = Offset(climbEnd, peakY)
    val c1 = Offset(cruiseEnd, peakY)

    // Descent bezier
    val d0 = Offset(cruiseEnd, peakY)
    val d1 = Offset(cruiseEnd + (endX - cruiseEnd) * 0.3f, peakY)
    val d2 = Offset(endX - (endX - cruiseEnd) * 0.3f, baseline)
    val d3 = Offset(endX, baseline)

    // Build full path
    val arcPath = Path().apply {
        moveTo(p0.x, p0.y)
        cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
        lineTo(c1.x, c1.y)
        cubicTo(d1.x, d1.y, d2.x, d2.y, d3.x, d3.y)
    }

    // Draw arc with gradient
    val arcBrush = Brush.horizontalGradient(
        colors = listOf(
            SolariGold.copy(alpha = 0.3f),
            SolariGold.copy(alpha = 0.6f),
            SolariGold.copy(alpha = 0.3f)
        )
    )
    drawPath(arcPath, arcBrush, style = Stroke(width = 2.5f))

    // Traversed portion — brighter
    if (progress > 0.01f) {
        val traversedPath = buildTraversedPath(progress, p0, p1, p2, p3, c0, c1, d0, d1, d2, d3, climbF, cruiseF)
        drawPath(traversedPath, SolariGold.copy(alpha = 0.9f), style = Stroke(width = 3f))
    }

    // Origin dot (pulsing)
    drawCircle(SolariGold.copy(alpha = pulseAlpha), radius = 5f, center = p0)

    // Destination dot
    drawCircle(SolariTextSecondary.copy(alpha = 0.6f), radius = 4f, center = d3)

    // Plane position dot
    if (progress in 0.01f..0.99f) {
        val planePos = arcPosition(progress, p0, p1, p2, p3, c0, c1, d0, d1, d2, d3, climbF, cruiseF)
        // Glow
        drawCircle(SolariGold.copy(alpha = 0.3f), radius = 10f, center = planePos)
        drawCircle(SolariGold, radius = 5f, center = planePos)
    }
}

private fun buildTraversedPath(
    progress: Float,
    p0: Offset, p1: Offset, p2: Offset, p3: Offset,
    c0: Offset, c1: Offset,
    d0: Offset, d1: Offset, d2: Offset, d3: Offset,
    climbF: Float, cruiseF: Float
): Path {
    val path = Path()
    path.moveTo(p0.x, p0.y)

    if (progress <= climbF) {
        val t = progress / climbF
        val steps = 20
        for (i in 1..steps) {
            val ti = t * i / steps
            val pt = cubicPt(ti, p0, p1, p2, p3)
            path.lineTo(pt.x, pt.y)
        }
    } else {
        // Full climb
        path.cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)

        if (progress <= climbF + cruiseF) {
            val t = (progress - climbF) / cruiseF
            val cruiseX = c0.x + (c1.x - c0.x) * t
            path.lineTo(cruiseX, c0.y)
        } else {
            // Full cruise
            path.lineTo(c1.x, c1.y)
            // Partial descent
            val t = (progress - climbF - cruiseF) / (1f - climbF - cruiseF)
            val steps = 20
            for (i in 1..steps) {
                val ti = t * i / steps
                val pt = cubicPt(ti, d0, d1, d2, d3)
                path.lineTo(pt.x, pt.y)
            }
        }
    }
    return path
}

private fun arcPosition(
    progress: Float,
    p0: Offset, p1: Offset, p2: Offset, p3: Offset,
    c0: Offset, c1: Offset,
    d0: Offset, d1: Offset, d2: Offset, d3: Offset,
    climbF: Float, cruiseF: Float
): Offset {
    return when {
        progress <= climbF -> {
            cubicPt(progress / climbF, p0, p1, p2, p3)
        }
        progress <= climbF + cruiseF -> {
            val t = (progress - climbF) / cruiseF
            Offset(c0.x + (c1.x - c0.x) * t, c0.y)
        }
        else -> {
            val t = (progress - climbF - cruiseF) / (1f - climbF - cruiseF)
            cubicPt(t.coerceIn(0f, 1f), d0, d1, d2, d3)
        }
    }
}

/** Evaluate cubic bezier at parameter t */
private fun cubicPt(t: Float, p0: Offset, p1: Offset, p2: Offset, p3: Offset): Offset {
    val u = 1 - t
    return Offset(
        u * u * u * p0.x + 3 * u * u * t * p1.x + 3 * u * t * t * p2.x + t * t * t * p3.x,
        u * u * u * p0.y + 3 * u * u * t * p1.y + 3 * u * t * t * p2.y + t * t * t * p3.y
    )
}
