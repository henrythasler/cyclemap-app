package com.henrythasler.cyclemap

import android.location.Location
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.exp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Composable
fun SpeedDisplay(currentSpeed: Double, padding: PaddingValues) {
    val radius = 8.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(padding),
            shape = RoundedCornerShape(radius),
            color = colorResource(R.color.distanceMeasurementBadgeBackground),
        ) {
            Text(
                text = "${DecimalFormat("0.0").format(currentSpeed)} km/h",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    start = radius,
                    end = radius,
                    top = radius / 2,
                    bottom = radius / 2
                )
            )
        }
    }
}

@Composable
fun RouteStatistics(distance: Double, waypointCount: Int, padding: PaddingValues) {
    val radius = 8.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(padding)
                .padding(start = radius),
            shape = RoundedCornerShape(radius),
            color = colorResource(R.color.distanceMeasurementBadgeBackground),
        ) {
            Column(
                modifier = Modifier.padding(
                    start = radius,
                    end = radius,
                    top = radius / 2,
                    bottom = radius / 2
                )
            ) {
                Text(
                    text = "$waypointCount Wpts",
                )
                Text(
                    text = getFormattedDistance(distance),
                )
            }
        }
    }
}

@Composable
fun TrackStatistics(locations: List<Location>, trackRecording: Boolean, padding: PaddingValues) {
    val radius = 8.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        var expanded by remember { mutableStateOf(false) }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(padding)
                .padding(end = radius),
            onClick = {
                expanded = !expanded
            },
            shape = RoundedCornerShape(radius),
            color = colorResource(R.color.distanceMeasurementBadgeBackground),
        ) {
            val distance = measureDistance(locationToPoints(locations))
            val tripDuration: Duration = if (locations.isNotEmpty())
                ((if (trackRecording) System.currentTimeMillis() else locations.last().time) - locations.first().time).milliseconds else Duration.ZERO
            Column(
                modifier = Modifier.padding(
                    start = radius,
                    end = radius,
                    top = radius / 2,
                    bottom = radius / 2
                ),
                horizontalAlignment = Alignment.End
            ) {
                if(expanded) {
                    val avgSpeed = if(tripDuration.inWholeSeconds > 0) distance / tripDuration.inWholeSeconds * 3.6 else 0.0
                    Text("Dist: ${getFormattedDistance(distance)}")
                    Text("Time: ${DateUtils.formatElapsedTime(tripDuration.inWholeSeconds)}")
                    Text("Avg: ${DecimalFormat("0.0").format(avgSpeed)} km/h")
                    if(locations.isNotEmpty()) {
                        val formattedTime = remember(locations.first().time) {
                            val instant = Instant.ofEpochMilli(locations.first().time)
                            val formatter = DateTimeFormatter.ofPattern("HH:mm")
                                .withZone(ZoneId.systemDefault())
                            formatter.format(instant)
                        }
                        Text("Start: $formattedTime")
                    }
                }
                else {
                    Text(getFormattedDistance(distance))
                    Text(DateUtils.formatElapsedTime(tripDuration.inWholeSeconds))
                }
            }
        }
    }
}

@Composable
fun DistanceBadge(distance: Double) {
    Text(
        modifier = Modifier.padding(3.dp),
        fontSize = 18.sp,
        fontStyle = FontStyle.Italic,
        text = getFormattedDistance(distance)
    )
}

@Composable
fun MainMenu(
    showMainMenu: Boolean,
    onDismissRequest: () -> Unit,
    onSelectMapStyle: () -> Unit,
    onLoadGpx: () -> Unit,
    onClearRoute: () -> Unit,
    onSaveGpx: () -> Unit,
    onDeleteTrack: () -> Unit,
    onAbout: () -> Unit,
    onScreenshot: () -> Unit,
) {
    DropdownMenu(
        expanded = showMainMenu,
        offset = DpOffset(48.dp, 0.dp), // FIXME: avoid hard-coded values
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuItem(
            text = {
                Text(text = stringResource(R.string.menu_map_style))
            },
            onClick = onSelectMapStyle,
            leadingIcon = {
                Icon(
                    painterResource(id = R.drawable.baseline_map_24),
                    stringResource(R.string.menu_map_style)
                )
            }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(text = stringResource(R.string.menu_gpx_load))
            },
            onClick = onLoadGpx,
            leadingIcon = {
                Icon(
                    painterResource(id = R.drawable.baseline_directions_24),
                    stringResource(R.string.menu_gpx_load)
                )
            }
        )
        DropdownMenuItem(
            text = {
                Text(text = stringResource(R.string.menu_delete_route))
            },
            onClick = onClearRoute,
            leadingIcon = {
                Icon(
                    painterResource(id = R.drawable.baseline_directions_off_24),
                    stringResource(R.string.menu_delete_route)
                )
            }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(text = stringResource(R.string.menu_gpx_save))
            },
            onClick = onSaveGpx,
            leadingIcon = {
                Icon(
                    painterResource(id = R.drawable.baseline_save_alt_24),
                    stringResource(R.string.menu_gpx_save)
                )
            }
        )
        DropdownMenuItem(
            text = {
                Text(text = stringResource(R.string.menu_delete_track))
            },
            onClick = onDeleteTrack,
            leadingIcon = {
                Icon(
                    painterResource(id = R.drawable.baseline_delete_forever_24),
                    stringResource(R.string.menu_delete_track)
                )
            }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(text = stringResource(R.string.menu_about))
            },
            onClick = onAbout,
            leadingIcon = {
                Icon(
                    painterResource(id = R.drawable.baseline_info_24),
                    stringResource(R.string.menu_about)
                )
            }
        )
        DropdownMenuItem(
            text = {
                Text(text = stringResource(R.string.menu_screenshot))
            },
            onClick = onScreenshot,
            leadingIcon = {
                Icon(
                    painterResource(id = R.drawable.baseline_my_location_24),
                    stringResource(R.string.menu_screenshot)
                )
            }
        )
    }
}