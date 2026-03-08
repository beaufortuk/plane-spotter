package com.planetracker.widget.components

import android.graphics.*
import com.planetracker.data.model.Flight
import com.planetracker.data.model.RouteInfo
import com.planetracker.util.Constants
import com.planetracker.util.GeoMath
import kotlin.math.*

/**
 * Pre-renders the route arc to a Bitmap using Android Canvas API.
 * This is necessary because Glance doesn't support Canvas/Path drawing.
 *
 * Mirrors the arc computation from the HTML page's computeArc() and
 * the macOS widget's ArcShape.swift.
 */
object RouteArcBitmap {

    private const val CLIMB_F = 0.18f
    private const val CRUISE_F = 0.60f
    private const val DESCENT_F = 0.22f

    /**
     * Render the route arc to a Bitmap
     * @param width bitmap width in pixels
     * @param height bitmap height in pixels
     * @param flight current flight data
     * @param route route info (nullable)
     * @param userLat user latitude
     * @param userLon user longitude
     * @param density screen density for scaling
     */
    fun render(
        width: Int,
        height: Int,
        flight: Flight,
        route: RouteInfo?,
        userLat: Double,
        userLon: Double,
        density: Float = 2f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val w = width.toFloat()
        val h = height.toFloat()
        val padX = w * 0.06f
        val xOrig = padX
        val xDest = w - padX
        val yGnd = h - 14f * density / 2f
        val span = xDest - xOrig

        val xTOC = xOrig + span * CLIMB_F
        val xTOD = xDest - span * DESCENT_F

        // Cruise altitude
        val descentW = xDest - xTOD
        val maxDropH = descentW * 2.8f
        val yCrz = max(h * 0.08f, yGnd - maxDropH)

        // Climb bezier control points
        val ccp1x = xOrig + (xTOC - xOrig) * 0.1f
        val ccp1y = yGnd
        val ccp2x = xTOC - (xTOC - xOrig) * 0.1f
        val ccp2y = yCrz

        // Descent bezier control points
        val dcp1x = xTOD + (xDest - xTOD) * 0.1f
        val dcp1y = yCrz
        val dcp2x = xDest - (xDest - xTOD) * 0.1f
        val dcp2y = yGnd - (yGnd - yCrz) * 0.15f

        // Build the full path
        val fullPath = Path().apply {
            moveTo(xOrig, yGnd)
            cubicTo(ccp1x, ccp1y, ccp2x, ccp2y, xTOC, yCrz)
            lineTo(xTOD, yCrz)
            cubicTo(dcp1x, dcp1y, dcp2x, dcp2y, xDest, yGnd)
        }

        // Calculate progress
        val progress = if (route != null && route.originLat != 0.0 && route.destLat != 0.0) {
            val total = GeoMath.haversine(route.originLat, route.originLon, route.destLat, route.destLon)
            if (total > 10) {
                val flown = GeoMath.haversine(route.originLat, route.originLon, flight.lat, flight.lon)
                (flown / total).coerceIn(0.02, 0.98).toFloat()
            } else 0.5f
        } else 0.5f

        // Ground line
        val groundPaint = Paint().apply {
            color = Color.argb(30, 255, 255, 255)
            strokeWidth = 1f * density / 2f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(xOrig, yGnd + 6f, xDest, yGnd + 6f, groundPaint)

        // Full path — dashed gray
        val dashedPaint = Paint().apply {
            color = Color.argb(30, 255, 255, 255)
            strokeWidth = 1.5f * density / 2f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(6f * density / 2f, 10f * density / 2f), 0f)
            isAntiAlias = true
        }
        canvas.drawPath(fullPath, dashedPaint)

        // Flown portion — solid gold with clip
        val planePos = getPlanePosition(
            progress, xOrig, xDest, yGnd, yCrz, xTOC, xTOD,
            ccp1x, ccp1y, ccp2x, ccp2y, dcp1x, dcp1y, dcp2x, dcp2y
        )

        canvas.save()
        canvas.clipRect(0f, 0f, planePos.first + 2f, h + 10f)
        val goldPaint = Paint().apply {
            color = Constants.GOLD_ACCENT_INT
            strokeWidth = 2.5f * density / 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawPath(fullPath, goldPaint)
        canvas.restore()

        // Cruise guide line
        val cruiseGuidePaint = Paint().apply {
            color = Color.argb(50, 212, 168, 71)
            strokeWidth = 1f * density / 2f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(2f * density / 2f, 6f * density / 2f), 0f)
        }
        canvas.drawLine(xTOC, yCrz, xTOD, yCrz, cruiseGuidePaint)

        // Origin dot (pulsing effect simulated with two circles)
        val originDotPaint = Paint().apply {
            color = Constants.GOLD_ACCENT_INT
            isAntiAlias = true
        }
        canvas.drawCircle(xOrig, yGnd, 3.5f * density / 2f, originDotPaint)
        originDotPaint.alpha = 40
        canvas.drawCircle(xOrig, yGnd, 7f * density / 2f, originDotPaint)

        // Destination dot
        val destDotPaint = Paint().apply {
            color = Constants.TEXT_PRIMARY_INT
            alpha = if (route != null) 180 else 80
            isAntiAlias = true
        }
        canvas.drawCircle(xDest, yGnd, 3.5f * density / 2f, destDotPaint)

        // Plane emoji at progress position
        val planePaint = Paint().apply {
            color = Constants.TEXT_PRIMARY_INT
            textSize = max(16f, h * 0.20f)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("✈", planePos.first, planePos.second + planePaint.textSize / 3f, planePaint)

        return bitmap
    }

    /** Calculate plane position along the flight path */
    private fun getPlanePosition(
        progress: Float,
        xOrig: Float, xDest: Float,
        yGnd: Float, yCrz: Float,
        xTOC: Float, xTOD: Float,
        ccp1x: Float, ccp1y: Float,
        ccp2x: Float, ccp2y: Float,
        dcp1x: Float, dcp1y: Float,
        dcp2x: Float, dcp2y: Float
    ): Pair<Float, Float> {
        val tn = progress

        return when {
            tn <= CLIMB_F -> {
                val t = tn / CLIMB_F
                cubicPt(t, xOrig, ccp1x, ccp2x, xTOC) to
                        cubicPt(t, yGnd, ccp1y, ccp2y, yCrz)
            }
            tn <= CLIMB_F + CRUISE_F -> {
                val t = (tn - CLIMB_F) / CRUISE_F
                (xTOC + (xTOD - xTOC) * t) to yCrz
            }
            else -> {
                val t = (tn - CLIMB_F - CRUISE_F) / DESCENT_F
                cubicPt(t, xTOD, dcp1x, dcp2x, xDest) to
                        cubicPt(t, yCrz, dcp1y, dcp2y, yGnd)
            }
        }
    }

    /** Cubic bezier point evaluation */
    private fun cubicPt(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
        val u = 1 - t
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3
    }
}
