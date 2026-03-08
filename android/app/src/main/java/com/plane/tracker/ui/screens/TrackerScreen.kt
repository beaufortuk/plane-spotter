package com.plane.tracker.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.plane.tracker.domain.TrackedFlight
import com.plane.tracker.ui.components.*
import com.plane.tracker.ui.theme.*
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun TrackerScreen(viewModel: TrackerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Request location permission on first launch
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        if (!state.locationAvailable) {
            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val config = LocalConfiguration.current
    val isWide = config.screenWidthDp >= 640

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SolariBg)
            .systemBarsPadding()
    ) {
        when {
            state.isLoading -> LoadingState()
            state.flights.isEmpty() -> EmptyState()
            else -> {
                val tracked = state.currentFlight ?: return@Box
                if (isWide) {
                    WideLayout(tracked, state, viewModel)
                } else {
                    NarrowLayout(tracked, state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = SolariGold)
            Spacer(Modifier.height(16.dp))
            Text("SCANNING SKY", color = SolariTextSecondary, fontFamily = SolariMono,
                fontSize = 12.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NO FLIGHTS", color = SolariTextSecondary, fontFamily = SolariMono,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            Text("Clear skies overhead", color = SolariTextSecondary.copy(alpha = 0.6f),
                fontFamily = SolariMono, fontSize = 12.sp)
        }
    }
}

// ── Wide layout (tablet / landscape) — two columns ──

@Composable
private fun WideLayout(tracked: TrackedFlight, state: TrackerViewModel.UiState, vm: TrackerViewModel) {
    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Left column (58%) — airline + arc
        Column(
            modifier = Modifier.weight(0.58f).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AirlineHeader(tracked)
            Spacer(Modifier.height(12.dp))
            RouteSection(tracked, vm)
        }

        Spacer(Modifier.width(16.dp))

        // Right column (42%) — stats
        Column(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
            StatsGrid(tracked)
            Spacer(Modifier.weight(1f))
            FlightPager(state)
        }
    }
}

// ── Narrow layout (phone portrait) — vertical stack ──

@Composable
private fun NarrowLayout(tracked: TrackedFlight, state: TrackerViewModel.UiState, vm: TrackerViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AirlineHeader(tracked)
        RouteSection(tracked, vm)
        StatsGrid(tracked)
        Spacer(Modifier.weight(1f))
        FlightPager(state)
    }
}

// ── Shared components ──

@Composable
private fun AirlineHeader(tracked: TrackedFlight) {
    val route = tracked.route
    val flight = tracked.flight
    val classification = tracked.classification

    // Accent bar colour
    val accentColor = when (classification?.tier) {
        com.plane.tracker.domain.Tier.RARE -> SolariRed
        else -> SolariGold
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(52.dp)
                .background(accentColor, RoundedCornerShape(2.dp))
        )

        Spacer(Modifier.width(12.dp))

        // Airline logo
        if (route != null && route.airlineIata.isNotBlank()) {
            AsyncImage(
                model = "https://images.kiwi.com/airlines/64/${route.airlineIata}.png",
                contentDescription = route.airlineName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SolariSurface),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = route?.airlineName?.uppercase() ?: flight.callsign.ifBlank { flight.icao24 },
                    color = SolariTextPrimary,
                    fontFamily = SolariMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (classification != null) {
                    Spacer(Modifier.width(8.dp))
                    TagPill(classification)
                }
            }

            // Subtitle line: callsign · type · reg
            val parts = buildList {
                if (flight.callsign.isNotBlank()) add(flight.callsign)
                if (flight.type.isNotBlank()) add(flight.type)
                if (flight.reg.isNotBlank()) add(flight.reg)
            }
            if (parts.isNotEmpty()) {
                Text(
                    text = parts.joinToString(" · "),
                    color = SolariTextSecondary,
                    fontFamily = SolariMono,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun RouteSection(tracked: TrackedFlight, vm: TrackerViewModel) {
    val route = tracked.route
    if (route != null && route.origin.isNotBlank() && route.dest.isNotBlank()) {
        FlightArc(
            origin = route.origin,
            dest = route.dest,
            originName = route.originName,
            destName = route.destName,
            progress = tracked.progress,
            phase = vm.detectPhase(tracked.flight, tracked.progress)
        )
    }
}

@Composable
private fun StatsGrid(tracked: TrackedFlight) {
    val flight = tracked.flight
    val fmt = NumberFormat.getNumberInstance()

    val vrateStr = if (flight.vrateFpm >= 0) "+${fmt.format(flight.vrateFpm)}"
                   else fmt.format(flight.vrateFpm)
    val vrateColor = when {
        flight.vrateFpm > 200 -> SolariClimb
        flight.vrateFpm < -200 -> SolariDescent
        else -> SolariTextPrimary
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "ALTITUDE",
                value = fmt.format(flight.altFt),
                unit = "FT",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "GND SPEED",
                value = fmt.format(flight.speedKts),
                unit = "KTS",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "V/RATE",
                value = vrateStr,
                unit = "FPM",
                valueColor = vrateColor,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "DISTANCE",
                value = String.format("%.1f", flight.distMi),
                unit = "MI",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FlightPager(state: TrackerViewModel.UiState) {
    if (state.flightCount > 1) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${state.displayIndex} / ${state.flightCount}",
                color = SolariTextSecondary,
                fontFamily = SolariMono,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp
            )
        }
    }
}
